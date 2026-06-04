package ru.spb.reshenie.chekerstatus.nsi.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyncProgressTrackerTest {

    @Test
    void runningProgressStartsAboveZero() {
        SyncProgressTracker progress = new SyncProgressTracker(100);

        progress.start();

        assertThat(progress.runningPercent()).isEqualTo(1);
    }

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

        assertThat(progress.runningPercent()).isEqualTo(1);

        progress.updateGitLinks(1, 2);
        assertThat(progress.runningPercent()).isEqualTo(51);

        progress.updateGitLinks(2, 2);
        assertThat(progress.runningPercent()).isEqualTo(99);
    }

    @Test
    void totalRefinementDoesNotMoveProgressBackwards() {
        SyncProgressTracker progress = new SyncProgressTracker(100);

        progress.setNsiRowsTotal(100);
        progress.completePassport();
        progress.addNsiRowsLoaded(50);

        assertThat(progress.runningPercent()).isEqualTo(51);

        progress.setGitLinksTotal(10);
        assertThat(progress.runningPercent()).isEqualTo(51);
    }
}
