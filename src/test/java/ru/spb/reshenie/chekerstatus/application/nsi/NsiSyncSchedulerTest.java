package ru.spb.reshenie.chekerstatus.application.nsi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.TaskScheduler;
import ru.spb.reshenie.chekerstatus.application.live.LiveUpdatePublisher;
import ru.spb.reshenie.chekerstatus.application.sync.port.SyncRunHistoryPort;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;
import ru.spb.reshenie.chekerstatus.infrastructure.config.NsiProperties;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NsiSyncSchedulerTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-03T10:00:00Z"), ZoneOffset.UTC);

    @Test
    void manualSynchronizationReschedulesNextAutoRunAfterCompletion() {
        NsiSyncService syncService = mock(NsiSyncService.class);
        NsiProperties properties = properties();
        TaskScheduler taskScheduler = taskScheduler();
        LiveUpdatePublisher liveUpdatePublisher = mock(LiveUpdatePublisher.class);
        SyncRunHistoryPort syncRunHistoryPort = mock(SyncRunHistoryPort.class);

        when(syncService.createSyncRun("identifier", false, SyncRunType.MANUAL)).thenReturn(42L);

        NsiSyncScheduler scheduler = newScheduler(
                syncService,
                properties,
                syncRunHistoryPort,
                taskScheduler,
                liveUpdatePublisher
        );

        long runId = scheduler.startManualSynchronization("identifier", false);

        assertThat(runId).isEqualTo(42L);
        assertThat(scheduler.getNextAutoRunAt())
                .isEqualTo(OffsetDateTime.of(2026, 6, 3, 10, 1, 0, 0, ZoneOffset.UTC));
        verify(syncService).synchronizeDictionary(42L, "identifier", false);
        verify(liveUpdatePublisher, atLeastOnce()).publish("scheduler.changed");
    }

    @Test
    void manualSynchronizationReschedulesFailedRunAfterThirtySeconds() {
        NsiSyncService syncService = mock(NsiSyncService.class);
        NsiProperties properties = properties();
        TaskScheduler taskScheduler = taskScheduler();
        LiveUpdatePublisher liveUpdatePublisher = mock(LiveUpdatePublisher.class);
        SyncRunHistoryPort syncRunHistoryPort = mock(SyncRunHistoryPort.class);

        when(syncService.createSyncRun("identifier", false, SyncRunType.MANUAL)).thenReturn(42L);
        doThrow(new NsiSyncException("boom"))
                .when(syncService).synchronizeDictionary(42L, "identifier", false);

        NsiSyncScheduler scheduler = newScheduler(
                syncService,
                properties,
                syncRunHistoryPort,
                taskScheduler,
                liveUpdatePublisher
        );

        long runId = scheduler.startManualSynchronization("identifier", false);

        assertThat(runId).isEqualTo(42L);
        assertThat(scheduler.getNextAutoRunAt())
                .isEqualTo(OffsetDateTime.of(2026, 6, 3, 10, 0, 30, 0, ZoneOffset.UTC));
    }

    @Test
    void automaticSynchronizationReschedulesFailedBatchAfterThirtySeconds() {
        NsiSyncService syncService = mock(NsiSyncService.class);
        NsiProperties properties = properties();
        properties.setSyncOnStartup(true);
        TaskScheduler taskScheduler = taskScheduler();
        LiveUpdatePublisher liveUpdatePublisher = mock(LiveUpdatePublisher.class);
        SyncRunHistoryPort syncRunHistoryPort = mock(SyncRunHistoryPort.class);

        when(syncService.synchronizeConfiguredDictionaries(false, SyncRunType.AUTO)).thenReturn(true);

        NsiSyncScheduler scheduler = newScheduler(
                syncService,
                properties,
                syncRunHistoryPort,
                taskScheduler,
                liveUpdatePublisher
        );

        scheduler.run(null);

        assertThat(scheduler.getNextAutoRunAt())
                .isEqualTo(OffsetDateTime.of(2026, 6, 3, 10, 0, 30, 0, ZoneOffset.UTC));
    }

    @Test
    void startupAfterFailedLastRunSchedulesAfterThirtySeconds() {
        NsiSyncService syncService = mock(NsiSyncService.class);
        NsiProperties properties = properties();
        properties.setSyncOnStartup(false);
        TaskScheduler taskScheduler = taskScheduler();
        LiveUpdatePublisher liveUpdatePublisher = mock(LiveUpdatePublisher.class);
        SyncRunHistoryPort syncRunHistoryPort = mock(SyncRunHistoryPort.class);

        when(syncRunHistoryPort.findLatestRunStatus()).thenReturn(SyncRunStatus.FAILED);

        NsiSyncScheduler scheduler = newScheduler(
                syncService,
                properties,
                syncRunHistoryPort,
                taskScheduler,
                liveUpdatePublisher
        );

        scheduler.run(null);

        assertThat(scheduler.getNextAutoRunAt())
                .isEqualTo(OffsetDateTime.of(2026, 6, 3, 10, 0, 30, 0, ZoneOffset.UTC));
    }

    @ParameterizedTest
    @EnumSource(value = SyncRunStatus.class, names = {"SUCCESS", "PARTIAL", "SERVER_STOPPED"})
    void startupAfterNonFailedLastRunUsesInitialDelay(SyncRunStatus status) {
        NsiSyncService syncService = mock(NsiSyncService.class);
        NsiProperties properties = properties();
        properties.setSyncOnStartup(false);
        TaskScheduler taskScheduler = taskScheduler();
        LiveUpdatePublisher liveUpdatePublisher = mock(LiveUpdatePublisher.class);
        SyncRunHistoryPort syncRunHistoryPort = mock(SyncRunHistoryPort.class);

        when(syncRunHistoryPort.findLatestRunStatus()).thenReturn(status);

        NsiSyncScheduler scheduler = newScheduler(
                syncService,
                properties,
                syncRunHistoryPort,
                taskScheduler,
                liveUpdatePublisher
        );

        scheduler.run(null);

        assertThat(scheduler.getNextAutoRunAt())
                .isEqualTo(OffsetDateTime.of(2026, 6, 3, 10, 1, 0, 0, ZoneOffset.UTC));
    }

    private NsiSyncScheduler newScheduler(NsiSyncService syncService,
                                          NsiProperties properties,
                                          SyncRunHistoryPort syncRunHistoryPort,
                                          TaskScheduler taskScheduler,
                                          LiveUpdatePublisher liveUpdatePublisher) {
        TaskExecutor directExecutor = new TaskExecutor() {
            @Override
            public void execute(Runnable task) {
                task.run();
            }
        };
        return new NsiSyncScheduler(
                syncService,
                properties,
                syncRunHistoryPort,
                directExecutor,
                taskScheduler,
                liveUpdatePublisher,
                CLOCK
        );
    }

    private NsiProperties properties() {
        NsiProperties properties = new NsiProperties();
        properties.setPollFixedDelayMs(60_000L);
        properties.setPollInitialDelayMs(60_000L);
        return properties;
    }

    private TaskScheduler taskScheduler() {
        TaskScheduler taskScheduler = mock(TaskScheduler.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        doReturn(future).when(taskScheduler).schedule(any(Runnable.class), any(Instant.class));
        return taskScheduler;
    }
}
