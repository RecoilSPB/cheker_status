package ru.spb.reshenie.chekerstatus.gitlab.model;

import java.time.OffsetDateTime;

public class GitCommitFileChange {

    private final long gitLinkId;
    private final long commitRowId;
    private final String commitSha;
    private final String parentSha;
    private final String oldPath;
    private final String newPath;
    private final String filePath;
    private final String changeType;
    private final boolean renamedFile;
    private final boolean newFile;
    private final boolean deletedFile;
    private final String diff;
    private final boolean diffTooLarge;
    private final GitFileContentSnapshot contentAfter;
    private final GitFileContentSnapshot contentBefore;
    private final String contentFetchStatus;
    private final String contentFetchError;
    private final OffsetDateTime committedDate;

    public GitCommitFileChange(long gitLinkId,
                               long commitRowId,
                               String commitSha,
                               String parentSha,
                               String oldPath,
                               String newPath,
                               String filePath,
                               String changeType,
                               boolean renamedFile,
                               boolean newFile,
                               boolean deletedFile,
                               String diff,
                               boolean diffTooLarge,
                               GitFileContentSnapshot contentAfter,
                               GitFileContentSnapshot contentBefore,
                               String contentFetchStatus,
                               String contentFetchError,
                               OffsetDateTime committedDate) {
        this.gitLinkId = gitLinkId;
        this.commitRowId = commitRowId;
        this.commitSha = commitSha;
        this.parentSha = parentSha;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.filePath = filePath;
        this.changeType = changeType;
        this.renamedFile = renamedFile;
        this.newFile = newFile;
        this.deletedFile = deletedFile;
        this.diff = diff;
        this.diffTooLarge = diffTooLarge;
        this.contentAfter = contentAfter;
        this.contentBefore = contentBefore;
        this.contentFetchStatus = contentFetchStatus;
        this.contentFetchError = contentFetchError;
        this.committedDate = committedDate;
    }

    public long getGitLinkId() {
        return gitLinkId;
    }

    public long getCommitRowId() {
        return commitRowId;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getParentSha() {
        return parentSha;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getChangeType() {
        return changeType;
    }

    public boolean isRenamedFile() {
        return renamedFile;
    }

    public boolean isNewFile() {
        return newFile;
    }

    public boolean isDeletedFile() {
        return deletedFile;
    }

    public String getDiff() {
        return diff;
    }

    public boolean isDiffTooLarge() {
        return diffTooLarge;
    }

    public GitFileContentSnapshot getContentAfter() {
        return contentAfter;
    }

    public GitFileContentSnapshot getContentBefore() {
        return contentBefore;
    }

    public String getContentFetchStatus() {
        return contentFetchStatus;
    }

    public String getContentFetchError() {
        return contentFetchError;
    }

    public OffsetDateTime getCommittedDate() {
        return committedDate;
    }
}
