package ru.spb.reshenie.chekerstatus.sync;

import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
