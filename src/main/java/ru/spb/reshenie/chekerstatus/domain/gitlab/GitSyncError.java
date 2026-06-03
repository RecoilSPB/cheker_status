package ru.spb.reshenie.chekerstatus.domain.gitlab;

import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;

public class GitSyncError {

    private final SyncErrorStage stage;
    private final Long gitLinkId;
    private final String projectPath;
    private final String commitSha;
    private final String filePath;
    private final String message;
    private final Throwable throwable;

    public GitSyncError(SyncErrorStage stage,
                        Long gitLinkId,
                        String projectPath,
                        String commitSha,
                        String filePath,
                        String message,
                        Throwable throwable) {
        this.stage = stage;
        this.gitLinkId = gitLinkId;
        this.projectPath = projectPath;
        this.commitSha = commitSha;
        this.filePath = filePath;
        this.message = message;
        this.throwable = throwable;
    }

    public SyncErrorStage getStage() {
        return stage;
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

    public String getMessage() {
        return message;
    }

    public Throwable getThrowable() {
        return throwable;
    }
}
