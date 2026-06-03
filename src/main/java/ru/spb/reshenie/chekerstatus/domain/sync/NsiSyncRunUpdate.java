package ru.spb.reshenie.chekerstatus.domain.sync;

public class NsiSyncRunUpdate {

    private String dictionaryVersion;
    private int nsiRowsTotal;
    private int nsiRowsInserted;
    private int nsiRowsUpdated;
    private int nsiRowsDeactivated;
    private int gitLinksFound;
    private int gitProjectsProcessed;
    private int gitCommitsProcessed;
    private int gitFilesProcessed;
    private int progressPercent;
    private int errorCount;
    private String errorMessage;

    public NsiSyncRunUpdate copy() {
        NsiSyncRunUpdate copy = new NsiSyncRunUpdate();
        copy.setDictionaryVersion(dictionaryVersion);
        copy.setNsiRowsTotal(nsiRowsTotal);
        copy.setNsiRowsInserted(nsiRowsInserted);
        copy.setNsiRowsUpdated(nsiRowsUpdated);
        copy.setNsiRowsDeactivated(nsiRowsDeactivated);
        copy.setGitLinksFound(gitLinksFound);
        copy.setGitProjectsProcessed(gitProjectsProcessed);
        copy.setGitCommitsProcessed(gitCommitsProcessed);
        copy.setGitFilesProcessed(gitFilesProcessed);
        copy.setProgressPercent(progressPercent);
        copy.setErrorCount(errorCount);
        copy.setErrorMessage(errorMessage);
        return copy;
    }

    public String getDictionaryVersion() {
        return dictionaryVersion;
    }

    public void setDictionaryVersion(String dictionaryVersion) {
        this.dictionaryVersion = dictionaryVersion;
    }

    public int getNsiRowsTotal() {
        return nsiRowsTotal;
    }

    public void setNsiRowsTotal(int nsiRowsTotal) {
        this.nsiRowsTotal = nsiRowsTotal;
    }

    public int getNsiRowsInserted() {
        return nsiRowsInserted;
    }

    public void setNsiRowsInserted(int nsiRowsInserted) {
        this.nsiRowsInserted = nsiRowsInserted;
    }

    public int getNsiRowsUpdated() {
        return nsiRowsUpdated;
    }

    public void setNsiRowsUpdated(int nsiRowsUpdated) {
        this.nsiRowsUpdated = nsiRowsUpdated;
    }

    public int getNsiRowsDeactivated() {
        return nsiRowsDeactivated;
    }

    public void setNsiRowsDeactivated(int nsiRowsDeactivated) {
        this.nsiRowsDeactivated = nsiRowsDeactivated;
    }

    public int getGitLinksFound() {
        return gitLinksFound;
    }

    public void setGitLinksFound(int gitLinksFound) {
        this.gitLinksFound = gitLinksFound;
    }

    public int getGitProjectsProcessed() {
        return gitProjectsProcessed;
    }

    public void setGitProjectsProcessed(int gitProjectsProcessed) {
        this.gitProjectsProcessed = gitProjectsProcessed;
    }

    public int getGitCommitsProcessed() {
        return gitCommitsProcessed;
    }

    public void setGitCommitsProcessed(int gitCommitsProcessed) {
        this.gitCommitsProcessed = gitCommitsProcessed;
    }

    public int getGitFilesProcessed() {
        return gitFilesProcessed;
    }

    public void setGitFilesProcessed(int gitFilesProcessed) {
        this.gitFilesProcessed = gitFilesProcessed;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public void setProgressPercent(int progressPercent) {
        this.progressPercent = Math.max(0, Math.min(100, progressPercent));
    }

    public int getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(int errorCount) {
        this.errorCount = errorCount;
    }

    public void incrementErrorCount() {
        this.errorCount++;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public SyncRunStatus successStatus() {
        return errorCount > 0 ? SyncRunStatus.PARTIAL : SyncRunStatus.SUCCESS;
    }
}
