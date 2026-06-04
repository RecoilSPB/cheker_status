package ru.spb.reshenie.chekerstatus.web.model;

import java.time.OffsetDateTime;

public class DocumentSummary {
    private final long id;
    private final String documentOid;
    private final String documentName;
    private final String projectPath;
    private final String treeRef;
    private final String status;
    private final String lastSyncError;
    private final OffsetDateTime lastSyncAt;
    private final long commitCount;
    private final long fileChangeCount;
    private final long failedFileCount;

    public DocumentSummary(long id,
                           String documentOid,
                           String documentName,
                           String projectPath,
                           String treeRef,
                           String status,
                           String lastSyncError,
                           OffsetDateTime lastSyncAt,
                           long commitCount,
                           long fileChangeCount,
                           long failedFileCount) {
        this.id = id;
        this.documentOid = documentOid;
        this.documentName = documentName;
        this.projectPath = projectPath;
        this.treeRef = treeRef;
        this.status = status;
        this.lastSyncError = lastSyncError;
        this.lastSyncAt = lastSyncAt;
        this.commitCount = commitCount;
        this.fileChangeCount = fileChangeCount;
        this.failedFileCount = failedFileCount;
    }

    public long getId() {
        return id;
    }

    public String getDocumentOid() {
        return documentOid;
    }

    public String getDocumentName() {
        return documentName;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getTreeRef() {
        return treeRef;
    }

    public String getStatus() {
        return status;
    }

    public String getLastSyncError() {
        return lastSyncError;
    }

    public OffsetDateTime getLastSyncAt() {
        return lastSyncAt;
    }

    public long getCommitCount() {
        return commitCount;
    }

    public long getFileChangeCount() {
        return fileChangeCount;
    }

    public long getFailedFileCount() {
        return failedFileCount;
    }

    public boolean hasError() {
        return "ERROR".equalsIgnoreCase(status) || failedFileCount > 0;
    }

    public String getDisplayStatus() {
        return status == null || status.trim().isEmpty() ? "PENDING" : status;
    }

    public String getStatusCss() {
        if (hasError()) {
            return "status-error";
        }
        if ("OK".equalsIgnoreCase(status)) {
            return "status-ok";
        }
        return "status-pending";
    }
}
