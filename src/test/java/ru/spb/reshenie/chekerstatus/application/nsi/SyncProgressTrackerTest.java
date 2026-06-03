package ru.spb.reshenie.chekerstatus.application.nsi;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncProgressTrackerTest {

    @Test
    void runningProgressDoesNotReachOneHundredBeforeFinish() {
        SyncProgressTracker progress = new SyncProgressTracker(100);

        progress.completePassport();
        progress.setNsiRowsTotal(0);

        assertThat(progress.runningPercent()).isEqualTo(99);
    }

    @Test
    void nsiRowsMoveProgressByLoadedRecordCount() {
        SyncProgressTracker progress = new SyncProgressTracker(100);

        progress.setNsiRowsTotal(4);
        progress.completePassport();
        progress.addNsiRowsLoaded(2);

        assertThat(progress.runningPercent()).isEqualTo(60);
    }

    @Test
    void gitLinksUseConfiguredWeight() {
        SyncProgressTracker progress = new SyncProgressTracker(100);

        progress.completePassport();
        progress.setNsiRowsTotal(0);
        progress.setGitLinksTotal(2);

        assertThat(progress.runningPercent()).isZero();

        progress.updateGitLinks(1, 2);
        assertThat(progress.runningPercent()).isEqualTo(50);

        progress.updateGitLinks(2, 2);
        assertThat(progress.runningPercent()).isEqualTo(99);
    }

    @Test
    void totalRefinementDoesNotMoveProgressBackwards() {
        SyncProgressTracker progress = new SyncProgressTracker(100);

        progress.setNsiRowsTotal(100);
        progress.completePassport();
        progress.addNsiRowsLoaded(50);

        assertThat(progress.runningPercent()).isEqualTo(50);

        progress.setGitLinksTotal(10);
        assertThat(progress.runningPercent()).isEqualTo(50);
    }
}
