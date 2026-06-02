package ru.spb.reshenie.chekerstatus.sync;

public class DashboardSummary {

    private final String currentStatus;
    private final NsiSyncRun lastRun;
    private final NsiSyncRun lastSuccessfulRun;
    private final String dictionaryIdentifier;
    private final String dictionaryVersion;
    private final long totalRecords;
    private final long totalGitLinks;
    private final long gitLinksWithErrors;
    private final long totalCommits;
    private final long totalFiles;

    public DashboardSummary(String currentStatus,
                            NsiSyncRun lastRun,
                            NsiSyncRun lastSuccessfulRun,
                            String dictionaryIdentifier,
                            String dictionaryVersion,
                            long totalRecords,
                            long totalGitLinks,
                            long gitLinksWithErrors,
                            long totalCommits,
                            long totalFiles) {
        this.currentStatus = currentStatus;
        this.lastRun = lastRun;
        this.lastSuccessfulRun = lastSuccessfulRun;
        this.dictionaryIdentifier = dictionaryIdentifier;
        this.dictionaryVersion = dictionaryVersion;
        this.totalRecords = totalRecords;
        this.totalGitLinks = totalGitLinks;
        this.gitLinksWithErrors = gitLinksWithErrors;
        this.totalCommits = totalCommits;
        this.totalFiles = totalFiles;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public NsiSyncRun getLastRun() {
        return lastRun;
    }

    public NsiSyncRun getLastSuccessfulRun() {
        return lastSuccessfulRun;
    }

    public String getDictionaryIdentifier() {
        return dictionaryIdentifier;
    }

    public String getDictionaryVersion() {
        return dictionaryVersion;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public long getTotalGitLinks() {
        return totalGitLinks;
    }

    public long getGitLinksWithErrors() {
        return gitLinksWithErrors;
    }

    public long getTotalCommits() {
        return totalCommits;
    }

    public long getTotalFiles() {
        return totalFiles;
    }
}
