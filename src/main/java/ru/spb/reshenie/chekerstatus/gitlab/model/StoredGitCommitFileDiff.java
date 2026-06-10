package ru.spb.reshenie.chekerstatus.gitlab.model;

public class StoredGitCommitFileDiff {

    private final long fileChangeId;
    private final String status;
    private final String diffType;
    private final String formatFamily;
    private final String summaryJson;
    private final String artifactObjectKey;
    private final String error;

    public StoredGitCommitFileDiff(long fileChangeId,
                                   String status,
                                   String diffType,
                                   String formatFamily,
                                   String summaryJson,
                                   String artifactObjectKey,
                                   String error) {
        this.fileChangeId = fileChangeId;
        this.status = status;
        this.diffType = diffType;
        this.formatFamily = formatFamily;
        this.summaryJson = summaryJson;
        this.artifactObjectKey = artifactObjectKey;
        this.error = error;
    }

    public long getFileChangeId() {
        return fileChangeId;
    }

    public String getStatus() {
        return status;
    }

    public String getDiffType() {
        return diffType;
    }

    public String getFormatFamily() {
        return formatFamily;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public String getArtifactObjectKey() {
        return artifactObjectKey;
    }

    public String getError() {
        return error;
    }
}
