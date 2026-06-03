package ru.spb.reshenie.chekerstatus.application.nsi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.task.TaskExecutionAutoConfiguration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;
import ru.spb.reshenie.chekerstatus.application.live.LiveUpdatePublisher;
import ru.spb.reshenie.chekerstatus.application.nsi.port.NsiSyncSettings;
import ru.spb.reshenie.chekerstatus.application.sync.port.SyncRunHistoryPort;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NsiSyncScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncScheduler.class);
    private static final long FAILED_RETRY_DELAY_MS = 30_000L;

    private final NsiSyncService syncService;
    private final NsiSyncSettings settings;
    private final SyncRunHistoryPort syncRunHistoryPort;
    private final TaskExecutor taskExecutor;
    private final TaskScheduler taskScheduler;
    private final LiveUpdatePublisher liveUpdatePublisher;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final Object scheduleLock = new Object();
    private volatile ScheduledFuture<?> scheduledAutoRun;
    private volatile OffsetDateTime nextAutoRunAt;

    public NsiSyncScheduler(NsiSyncService syncService,
                            NsiSyncSettings settings,
                            SyncRunHistoryPort syncRunHistoryPort,
                            @Qualifier(TaskExecutionAutoConfiguration.APPLICATION_TASK_EXECUTOR_BEAN_NAME)
                            TaskExecutor taskExecutor,
                            TaskScheduler taskScheduler,
                            LiveUpdatePublisher liveUpdatePublisher,
                            Clock clock) {
        this.syncService = syncService;
        this.settings = settings;
        this.syncRunHistoryPort = syncRunHistoryPort;
        this.taskExecutor = taskExecutor;
        this.taskScheduler = taskScheduler;
        this.liveUpdatePublisher = liveUpdatePublisher;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.isSyncOnStartup()) {
            startAutoSynchronization();
        } else {
            scheduleNextAutoRun(Instant.now(clock).plusMillis(initialDelayMs()));
        }
    }

    private void startAutoSynchronization() {
        if (!running.compareAndSet(false, true)) {
            log.info("NSI synchronization start skipped because another run is active");
            return;
        }
        clearScheduledAutoRun();
        taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean failed = false;
                try {
                    failed = syncService.synchronizeConfiguredDictionaries(false, SyncRunType.AUTO);
                } catch (RuntimeException e) {
                    failed = true;
                    log.error("Automatic NSI synchronization failed", e);
                } finally {
                    running.set(false);
                    scheduleAfterCompletion(failed);
                }
            }
        });
    }

    public long startManualSynchronization(String dictionaryIdentifier, boolean forceReload) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("Синхронизация уже выполняется");
        }

        final String resolvedIdentifier = resolveDictionaryIdentifier(dictionaryIdentifier);
        final long runId;
        try {
            runId = syncService.createSyncRun(resolvedIdentifier, forceReload, SyncRunType.MANUAL);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        clearScheduledAutoRun();
        taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                boolean failed = false;
                try {
                    syncService.synchronizeDictionary(runId, resolvedIdentifier, forceReload);
                } catch (RuntimeException e) {
                    failed = true;
                    log.error("Manual NSI synchronization failed: runId={}", Long.valueOf(runId), e);
                } finally {
                    running.set(false);
                    scheduleAfterCompletion(failed);
                }
            }
        });
        return runId;
    }

    public boolean isRunning() {
        return running.get();
    }

    public OffsetDateTime getNextAutoRunAt() {
        if (isRunning()) {
            return null;
        }
        return nextAutoRunAt;
    }

    public Long getNextAutoRunDelayMs() {
        OffsetDateTime nextRun = getNextAutoRunAt();
        if (nextRun == null) {
            return null;
        }
        long delay = Duration.between(Instant.now(clock), nextRun.toInstant()).toMillis();
        return Long.valueOf(Math.max(0L, delay));
    }

    public long getAutoSyncIntervalMs() {
        return fixedDelayMs();
    }

    private void scheduleAfterCompletion(boolean failed) {
        long delayMs = failed ? FAILED_RETRY_DELAY_MS : fixedDelayMs();
        scheduleNextAutoRun(Instant.now(clock).plusMillis(delayMs));
    }

    private void scheduleNextAutoRun(Instant runAt) {
        synchronized (scheduleLock) {
            if (scheduledAutoRun != null) {
                scheduledAutoRun.cancel(false);
            }
            nextAutoRunAt = OffsetDateTime.ofInstant(runAt, ZoneOffset.UTC);
            scheduledAutoRun = taskScheduler.schedule(new Runnable() {
                @Override
                public void run() {
                    startAutoSynchronization();
                }
            }, runAt);
        }
        liveUpdatePublisher.publish("scheduler.changed");
        liveUpdatePublisher.publish("dashboard.changed");
    }

    private void clearScheduledAutoRun() {
        synchronized (scheduleLock) {
            if (scheduledAutoRun != null) {
                scheduledAutoRun.cancel(false);
                scheduledAutoRun = null;
            }
            nextAutoRunAt = null;
        }
        liveUpdatePublisher.publish("scheduler.changed");
        liveUpdatePublisher.publish("dashboard.changed");
    }

    private long fixedDelayMs() {
        return Math.max(1_000L, settings.getPollFixedDelayMs());
    }

    private long initialDelayMs() {
        try {
            if (SyncRunStatus.FAILED.equals(syncRunHistoryPort.findLatestRunStatus())) {
                return FAILED_RETRY_DELAY_MS;
            }
        } catch (RuntimeException e) {
            log.warn("Cannot read latest sync run status for scheduler startup: error={}", e.getMessage());
        }
        return Math.max(0L, settings.getPollInitialDelayMs());
    }

    private String resolveDictionaryIdentifier(String dictionaryIdentifier) {
        if (dictionaryIdentifier != null && !dictionaryIdentifier.trim().isEmpty()) {
            return dictionaryIdentifier.trim();
        }
        List<String> identifiers = settings.getEnabledDictionaryIdentifiers();
        if (!identifiers.isEmpty()) {
            return identifiers.get(0);
        }
        throw new IllegalArgumentException("NSI dictionary identifier is not configured");
    }
}
