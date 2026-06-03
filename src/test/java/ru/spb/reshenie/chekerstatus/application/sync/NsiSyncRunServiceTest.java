package ru.spb.reshenie.chekerstatus.application.sync;

import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;
import ru.spb.reshenie.chekerstatus.infrastructure.persistence.NsiSyncRunRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NsiSyncRunServiceTest {

    private final NsiSyncRunRepository repository = mock(NsiSyncRunRepository.class);
    private final NsiSyncRunService service = new NsiSyncRunService(repository);

    @Test
    void calculatesDurationMs() {
        OffsetDateTime started = OffsetDateTime.of(2026, 6, 2, 10, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime finished = started.plusSeconds(12).plusNanos(345000000);

        long durationMs = NsiSyncRunService.durationMs(started, finished);

        assertThat(durationMs).isEqualTo(12345L);
    }

    @Test
    void finishesRunningRunAsSuccess() {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();

        service.finishCompleted(10L, update);

        verify(repository).finishRun(10L, SyncRunStatus.SUCCESS, update);
    }

    @Test
    void finishesRunningRunAsPartialWhenErrorsWereCollected() {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();
        update.setErrorCount(2);

        service.finishCompleted(11L, update);

        verify(repository).finishRun(11L, SyncRunStatus.PARTIAL, update);
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
}
