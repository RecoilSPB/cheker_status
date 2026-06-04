package ru.spb.reshenie.chekerstatus.web.model;

import java.time.OffsetDateTime;

public class FileChangeRow {
    private final long id;
    private final long documentId;
    private final String documentName;
    private final String commitSha;
    private final String parentSha;
    private final String changeType;
    private final String oldPath;
    private final String newPath;
    private final String filePath;
    private final String contentFetchStatus;
    private final String contentFetchError;
    private final Long contentBeforeSize;
    private final Long contentAfterSize;
    private final String contentBeforeSha256;
    private final String contentAfterSha256;
    private final String diff;
    private final OffsetDateTime committedDate;

    public FileChangeRow(long id,
                         String commitSha,
                         String parentSha,
                         String changeType,
                         String oldPath,
                         String newPath,
                         String filePath,
                         String contentFetchStatus,
                         String contentFetchError,
                         Long contentBeforeSize,
                         Long contentAfterSize,
                         String contentBeforeSha256,
                         String contentAfterSha256,
                         String diff,
                         OffsetDateTime committedDate) {
        this(id, 0, null, commitSha, parentSha, changeType, oldPath, newPath, filePath, contentFetchStatus,
                contentFetchError, contentBeforeSize, contentAfterSize, contentBeforeSha256, contentAfterSha256,
                diff, committedDate);
    }

    public FileChangeRow(long id,
                         long documentId,
                         String documentName,
                         String commitSha,
                         String parentSha,
                         String changeType,
                         String oldPath,
                         String newPath,
                         String filePath,
                         String contentFetchStatus,
                         String contentFetchError,
                         Long contentBeforeSize,
                         Long contentAfterSize,
                         String contentBeforeSha256,
                         String contentAfterSha256,
                         String diff,
                         OffsetDateTime committedDate) {
        this.id = id;
        this.documentId = documentId;
        this.documentName = documentName;
        this.commitSha = commitSha;
        this.parentSha = parentSha;
        this.changeType = changeType;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.filePath = filePath;
        this.contentFetchStatus = contentFetchStatus;
        this.contentFetchError = contentFetchError;
        this.contentBeforeSize = contentBeforeSize;
        this.contentAfterSize = contentAfterSize;
        this.contentBeforeSha256 = contentBeforeSha256;
        this.contentAfterSha256 = contentAfterSha256;
        this.diff = diff;
        this.committedDate = committedDate;
    }

    public long getId() {
        return id;
    }

    public long getDocumentId() {
        return documentId;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getParentSha() {
        return parentSha;
    }

    public String getChangeType() {
        return changeType;
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

    public String getContentFetchStatus() {
        return contentFetchStatus;
    }

    public String getContentFetchError() {
        return contentFetchError;
    }

    public Long getContentBeforeSize() {
        return contentBeforeSize;
    }

    public Long getContentAfterSize() {
        return contentAfterSize;
    }

    public String getContentBeforeSha256() {
        return contentBeforeSha256;
    }

    public String getContentAfterSha256() {
        return contentAfterSha256;
    }

    public String getDiff() {
        return diff;
    }

    public OffsetDateTime getCommittedDate() {
        return committedDate;
    }

    public String getShortSha() {
        if (commitSha == null) {
            return "";
        }
        return commitSha.length() <= 10 ? commitSha : commitSha.substring(0, 10);
    }

    public String getShortParentSha() {
        if (parentSha == null) {
            return "";
        }
        return parentSha.length() <= 10 ? parentSha : parentSha.substring(0, 10);
    }

    public String getChangeTypeCss() {
        if ("added".equalsIgnoreCase(changeType)) {
            return "change-added";
        }
        if ("deleted".equalsIgnoreCase(changeType)) {
            return "change-deleted";
        }
        if ("renamed".equalsIgnoreCase(changeType)) {
            return "change-renamed";
        }
        return "change-modified";
    }

    public String getStatusCss() {
        if ("SUCCESS".equalsIgnoreCase(contentFetchStatus)) {
            return "status-ok";
        }
        if ("FAILED".equalsIgnoreCase(contentFetchStatus)) {
            return "status-error";
        }
        return "status-muted";
    }
}
