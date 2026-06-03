package ru.spb.reshenie.chekerstatus.domain.sync;

import java.time.OffsetDateTime;

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
    private final OffsetDateTime nextAutoRunAt;
    private final Long nextAutoRunDelayMs;
    private final Long autoSyncIntervalMs;

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
        this(currentStatus, lastRun, lastSuccessfulRun, dictionaryIdentifier, dictionaryVersion,
                totalRecords, totalGitLinks, gitLinksWithErrors, totalCommits, totalFiles,
                null, null, null);
    }

    public DashboardSummary(String currentStatus,
                            NsiSyncRun lastRun,
                            NsiSyncRun lastSuccessfulRun,
                            String dictionaryIdentifier,
                            String dictionaryVersion,
                            long totalRecords,
                            long totalGitLinks,
                            long gitLinksWithErrors,
                            long totalCommits,
                            long totalFiles,
                            OffsetDateTime nextAutoRunAt,
                            Long nextAutoRunDelayMs,
                            Long autoSyncIntervalMs) {
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
        this.nextAutoRunAt = nextAutoRunAt;
        this.nextAutoRunDelayMs = nextAutoRunDelayMs;
        this.autoSyncIntervalMs = autoSyncIntervalMs;
    }

    public DashboardSummary withScheduler(OffsetDateTime nextAutoRunAt,
                                          Long nextAutoRunDelayMs,
                                          Long autoSyncIntervalMs) {
        return new DashboardSummary(
                currentStatus,
                lastRun,
                lastSuccessfulRun,
                dictionaryIdentifier,
                dictionaryVersion,
                totalRecords,
                totalGitLinks,
                gitLinksWithErrors,
                totalCommits,
                totalFiles,
                nextAutoRunAt,
                nextAutoRunDelayMs,
                autoSyncIntervalMs
        );
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

    public OffsetDateTime getNextAutoRunAt() {
        return nextAutoRunAt;
    }

    public Long getNextAutoRunDelayMs() {
        return nextAutoRunDelayMs;
    }

    public Long getAutoSyncIntervalMs() {
        return autoSyncIntervalMs;
    }
}
