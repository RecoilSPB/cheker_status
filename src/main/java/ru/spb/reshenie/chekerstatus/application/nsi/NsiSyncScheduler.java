package ru.spb.reshenie.chekerstatus.application.nsi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.spb.reshenie.chekerstatus.application.nsi.port.NsiSyncSettings;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;

import java.util.List;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class NsiSyncScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncScheduler.class);

    private final NsiSyncService syncService;
    private final NsiSyncSettings settings;
    private final TaskExecutor taskExecutor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public NsiSyncScheduler(NsiSyncService syncService, NsiSyncSettings settings, TaskExecutor taskExecutor) {
        this.syncService = syncService;
        this.settings = settings;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (settings.isSyncOnStartup()) {
            runOnce(false);
        }
    }

    @Scheduled(
            fixedDelayString = "${nsi.poll-fixed-delay-ms:3600000}",
            initialDelayString = "${nsi.poll-initial-delay-ms:60000}"
    )
    public void scheduledSynchronization() {
        runOnce(false);
    }

    private void runOnce(boolean forceReload) {
        if (!running.compareAndSet(false, true)) {
            log.info("NSI synchronization is already running");
            return;
        }
        try {
            syncService.synchronizeConfiguredDictionaries(forceReload, SyncRunType.AUTO);
        } finally {
            running.set(false);
        }
    }

    public long startManualSynchronization(String dictionaryIdentifier, boolean forceReload) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("NSI synchronization is already running");
        }

        final String resolvedIdentifier = resolveDictionaryIdentifier(dictionaryIdentifier);
        final long runId;
        try {
            runId = syncService.createSyncRun(resolvedIdentifier, forceReload, SyncRunType.MANUAL);
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }

        taskExecutor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    syncService.synchronizeDictionary(runId, resolvedIdentifier, forceReload);
                } finally {
                    running.set(false);
                }
            }
        });
        return runId;
    }

    public boolean isRunning() {
        return running.get();
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
