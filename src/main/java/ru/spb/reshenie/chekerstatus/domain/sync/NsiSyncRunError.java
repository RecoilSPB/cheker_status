package ru.spb.reshenie.chekerstatus.domain.sync;

import java.time.OffsetDateTime;

public class NsiSyncRunError {

    private final long id;
    private final long syncRunId;
    private final SyncErrorStage errorStage;
    private final String dictionaryIdentifier;
    private final Long gitLinkId;
    private final String projectPath;
    private final String commitSha;
    private final String filePath;
    private final String errorMessage;
    private final String stackTrace;
    private final OffsetDateTime createdAt;

    public NsiSyncRunError(long id,
                           long syncRunId,
                           SyncErrorStage errorStage,
                           String dictionaryIdentifier,
                           Long gitLinkId,
                           String projectPath,
                           String commitSha,
                           String filePath,
                           String errorMessage,
                           String stackTrace,
                           OffsetDateTime createdAt) {
        this.id = id;
        this.syncRunId = syncRunId;
        this.errorStage = errorStage;
        this.dictionaryIdentifier = dictionaryIdentifier;
        this.gitLinkId = gitLinkId;
        this.projectPath = projectPath;
        this.commitSha = commitSha;
        this.filePath = filePath;
        this.errorMessage = errorMessage;
        this.stackTrace = stackTrace;
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getSyncRunId() {
        return syncRunId;
    }

    public SyncErrorStage getErrorStage() {
        return errorStage;
    }

    public String getDictionaryIdentifier() {
        return dictionaryIdentifier;
    }

    public Long getGitLinkId() {
        return gitLinkId;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
