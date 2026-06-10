package ru.spb.reshenie.chekerstatus.gitlab.model;

import java.time.OffsetDateTime;

public class StoredGitCommitFileChange {

    private final long id;
    private final long gitLinkId;
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
    private final String contentFetchStatus;
    private final String contentFetchError;
    private final GitFileContentSnapshot contentBefore;
    private final GitFileContentSnapshot contentAfter;
    private final OffsetDateTime committedDate;

    public StoredGitCommitFileChange(long id,
                                     long gitLinkId,
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
                                     String contentFetchStatus,
                                     String contentFetchError,
                                     GitFileContentSnapshot contentBefore,
                                     GitFileContentSnapshot contentAfter,
                                     OffsetDateTime committedDate) {
        this.id = id;
        this.gitLinkId = gitLinkId;
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
        this.contentFetchStatus = contentFetchStatus;
        this.contentFetchError = contentFetchError;
        this.contentBefore = contentBefore;
        this.contentAfter = contentAfter;
        this.committedDate = committedDate;
    }

    public long getId() {
        return id;
    }

    public long getGitLinkId() {
        return gitLinkId;
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

    public String getContentFetchStatus() {
        return contentFetchStatus;
    }

    public String getContentFetchError() {
        return contentFetchError;
    }

    public GitFileContentSnapshot getContentBefore() {
        return contentBefore;
    }

    public GitFileContentSnapshot getContentAfter() {
        return contentAfter;
    }

    public OffsetDateTime getCommittedDate() {
        return committedDate;
    }
}
