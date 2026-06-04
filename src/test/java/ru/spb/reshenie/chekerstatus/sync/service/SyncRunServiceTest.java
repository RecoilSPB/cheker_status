package ru.spb.reshenie.chekerstatus.sync.service;

import ru.spb.reshenie.chekerstatus.sync.repository.SyncRunRepository;

import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.sync.model.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunLogLevel;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunType;
import ru.spb.reshenie.chekerstatus.sync.service.LiveUpdatePublisher;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SyncRunServiceTest {

    private final SyncRunRepository repository = mock(SyncRunRepository.class);
    private final LiveUpdatePublisher liveUpdatePublisher = mock(LiveUpdatePublisher.class);
    private final SyncRunService service = new SyncRunService(repository, liveUpdatePublisher);

    @Test
    void calculatesDurationMs() {
        OffsetDateTime started = OffsetDateTime.of(2026, 6, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime finished = started.plusSeconds(12).plusNanos(345000000);

        long durationMs = SyncRunService.durationMs(started, finished);

        assertThat(durationMs).isEqualTo(12345L);
    }

    @Test
    void finishesRunningRunAsSuccess() {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();

        service.finishCompleted(10L, update);

        assertThat(update.getProgressPercent()).isEqualTo(100);
        verify(repository).finishRun(10L, SyncRunStatus.SUCCESS, update);
        verify(liveUpdatePublisher).publish(eq("syncRun.changed"), anyMap());
        verify(liveUpdatePublisher, atLeastOnce()).publish(eq("dashboard.changed"), anyMap());
    }

    @Test
    void finishesRunningRunAsPartialWhenErrorsWereCollected() {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();
        update.setErrorCount(2);

        service.finishCompleted(11L, update);

        verify(repository).finishRun(11L, SyncRunStatus.PARTIAL, update);
        assertThat(update.getProgressPercent()).isEqualTo(100);
    }

    @Test
    void clampsProgressPercent() {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();

        update.setProgressPercent(140);
        assertThat(update.getProgressPercent()).isEqualTo(100);

        update.setProgressPercent(-20);
        assertThat(update.getProgressPercent()).isZero();
    }

    @Test
    void finishesRunningRunAsFailed() {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();
        update.setErrorMessage("NSI unavailable");

        service.finishFailed(12L, update);

        verify(repository).finishRun(12L, SyncRunStatus.FAILED, update);
    }

    @Test
    void marksActiveRunAsServerStoppedOnShutdown() {
        when(repository.createRun(eq(SyncRunType.MANUAL), eq("identifier"), eq(false), anyMap()))
                .thenReturn(42L);
        long runId = service.startRun(SyncRunType.MANUAL, "identifier", false);

        NsiSyncRunUpdate update = new NsiSyncRunUpdate();
        update.setDictionaryVersion("5");
        update.setNsiRowsTotal(100);
        service.updateProgress(runId, SyncErrorStage.NSI_DATA, update);

        service.markActiveRunsStoppedOnShutdown();

        verify(repository).addError(
                eq(42L),
                eq(SyncErrorStage.UNKNOWN),
                eq("identifier"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("Синхронизация остановлена из-за остановки сервера: последний этап NSI_DATA"),
                eq(null)
        );
        verify(repository).stopRunOnServerShutdown(
                eq(42L),
                any(NsiSyncRunUpdate.class),
                eq("Синхронизация остановлена из-за остановки сервера: последний этап NSI_DATA")
        );
    }

    @Test
    void marksStaleRunningRunsAfterPreviousServerStop() {
        service.markRunsInterruptedBeforeStartup();

        verify(repository).stopRunningRunsAfterPreviousServerStop(
                "Синхронизация остановлена: предыдущий запуск сервера завершился до окончания процесса"
        );
    }

    @Test
    void publishesLiveUpdateWhenLogIsSaved() {
        service.safeAddLog(20L, SyncRunLogLevel.INFO, SyncErrorStage.NSI_DATA, "identifier",
                null, null, null, null, "message", null, Collections.<String, Object>emptyMap());

        verify(repository).addLog(
                eq(20L),
                eq(SyncRunLogLevel.INFO),
                eq(SyncErrorStage.NSI_DATA),
                eq("identifier"),
                eq(null),
                eq(null),
                eq(null),
                eq(null),
                eq("message"),
                eq(null),
                anyMap()
        );
        verify(liveUpdatePublisher).publish(eq("syncRun.log.changed"), anyMap());
        verify(liveUpdatePublisher).publish(eq("dashboard.changed"), anyMap());
    }
}
