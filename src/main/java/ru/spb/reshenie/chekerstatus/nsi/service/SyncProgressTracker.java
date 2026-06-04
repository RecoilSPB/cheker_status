package ru.spb.reshenie.chekerstatus.nsi.service;

final class SyncProgressTracker {

    static final int PASSPORT_WEIGHT = 1;
    static final int NSI_RECORD_WEIGHT = 1;
    static final int MAX_RUNNING_PERCENT = 99;

    private final int gitLinkWeight;
    private boolean passportCompleted;
    private int nsiRowsTotal;
    private int nsiRowsLoaded;
    private int gitLinksTotal;
    private int gitLinksCompleted;
    private int lastPercent;

    SyncProgressTracker(int gitLinkWeight) {
        this.gitLinkWeight = Math.max(1, gitLinkWeight);
    }

    void completePassport() {
        passportCompleted = true;
    }

    void setNsiRowsTotal(int nsiRowsTotal) {
        this.nsiRowsTotal = Math.max(0, nsiRowsTotal);
        this.nsiRowsLoaded = Math.min(this.nsiRowsLoaded, this.nsiRowsTotal);
    }

    void addNsiRowsLoaded(int rows) {
        if (rows <= 0) {
            return;
        }
        this.nsiRowsLoaded = Math.min(nsiRowsTotal, this.nsiRowsLoaded + rows);
    }

    void setGitLinksTotal(int gitLinksTotal) {
        this.gitLinksTotal = Math.max(0, gitLinksTotal);
        this.gitLinksCompleted = Math.min(this.gitLinksCompleted, this.gitLinksTotal);
    }

    void updateGitLinks(int completed, int total) {
        setGitLinksTotal(total);
        this.gitLinksCompleted = Math.max(0, Math.min(completed, this.gitLinksTotal));
    }

    int runningPercent() {
        long totalUnits = totalUnits();
        if (totalUnits <= 0) {
            return lastPercent;
        }
        long completedUnits = completedUnits();
        int calculated = (int) Math.floor((completedUnits * 100.0d) / totalUnits);
        calculated = Math.min(MAX_RUNNING_PERCENT, Math.max(0, calculated));
        lastPercent = Math.max(lastPercent, calculated);
        return lastPercent;
    }

    private long totalUnits() {
        return PASSPORT_WEIGHT
                + (long) nsiRowsTotal * NSI_RECORD_WEIGHT
                + (long) gitLinksTotal * gitLinkWeight;
    }

    private long completedUnits() {
        return (passportCompleted ? PASSPORT_WEIGHT : 0L)
                + (long) Math.min(nsiRowsLoaded, nsiRowsTotal) * NSI_RECORD_WEIGHT
                + (long) Math.min(gitLinksCompleted, gitLinksTotal) * gitLinkWeight;
    }
}
