package ru.spb.reshenie.chekerstatus.web;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

public final class UiModels {

    private UiModels() {
    }

    public static class DashboardStats {
        private final long dictionaries;
        private final long documents;
        private final long commits;
        private final long fileChanges;
        private final long errors;
        private final OffsetDateTime lastSyncAt;

        public DashboardStats(long dictionaries,
                              long documents,
                              long commits,
                              long fileChanges,
                              long errors,
                              OffsetDateTime lastSyncAt) {
            this.dictionaries = dictionaries;
            this.documents = documents;
            this.commits = commits;
            this.fileChanges = fileChanges;
            this.errors = errors;
            this.lastSyncAt = lastSyncAt;
        }

        public long getDictionaries() {
            return dictionaries;
        }

        public long getDocuments() {
            return documents;
        }

        public long getCommits() {
            return commits;
        }

        public long getFileChanges() {
            return fileChanges;
        }

        public long getErrors() {
            return errors;
        }

        public OffsetDateTime getLastSyncAt() {
            return lastSyncAt;
        }
    }

    public static class DocumentSummary {
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

    public static class CommitRow {
        private final long id;
        private final long documentId;
        private final String documentName;
        private final String commitSha;
        private final String shortId;
        private final String title;
        private final String authorName;
        private final OffsetDateTime committedDate;
        private final long fileCount;
        private final boolean fileHistoryLoaded;
        private final String webUrl;

        public CommitRow(long id,
                         String commitSha,
                         String shortId,
                         String title,
                         String authorName,
                         OffsetDateTime committedDate,
                         long fileCount,
                         String webUrl) {
            this(id, 0, null, commitSha, shortId, title, authorName, committedDate, fileCount, true, webUrl);
        }

        public CommitRow(long id,
                         String commitSha,
                         String shortId,
                         String title,
                         String authorName,
                         OffsetDateTime committedDate,
                         long fileCount,
                         boolean fileHistoryLoaded,
                         String webUrl) {
            this(id, 0, null, commitSha, shortId, title, authorName, committedDate, fileCount, fileHistoryLoaded,
                    webUrl);
        }

        public CommitRow(long id,
                         long documentId,
                         String documentName,
                         String commitSha,
                         String shortId,
                         String title,
                         String authorName,
                         OffsetDateTime committedDate,
                         long fileCount,
                         String webUrl) {
            this(id, documentId, documentName, commitSha, shortId, title, authorName, committedDate, fileCount, true,
                    webUrl);
        }

        public CommitRow(long id,
                         long documentId,
                         String documentName,
                         String commitSha,
                         String shortId,
                         String title,
                         String authorName,
                         OffsetDateTime committedDate,
                         long fileCount,
                         boolean fileHistoryLoaded,
                         String webUrl) {
            this.id = id;
            this.documentId = documentId;
            this.documentName = documentName;
            this.commitSha = commitSha;
            this.shortId = shortId;
            this.title = title;
            this.authorName = authorName;
            this.committedDate = committedDate;
            this.fileCount = fileCount;
            this.fileHistoryLoaded = fileHistoryLoaded;
            this.webUrl = webUrl;
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

        public String getShortId() {
            return shortId;
        }

        public String getTitle() {
            return title;
        }

        public String getAuthorName() {
            return authorName;
        }

        public OffsetDateTime getCommittedDate() {
            return committedDate;
        }

        public long getFileCount() {
            return fileCount;
        }

        public boolean isFileHistoryLoaded() {
            return fileHistoryLoaded;
        }

        public String getWebUrl() {
            return webUrl;
        }

        public String getShortSha() {
            if (commitSha == null) {
                return "";
            }
            return commitSha.length() <= 10 ? commitSha : commitSha.substring(0, 10);
        }
    }

    public static class FileChangeRow {
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

    public static class DocumentDetails {
        private final DocumentSummary document;
        private final List<CommitRow> commits;
        private final List<FileChangeRow> fileChanges;
        private final FileChangeRow selectedFileChange;

        public DocumentDetails(DocumentSummary document,
                               List<CommitRow> commits,
                               List<FileChangeRow> fileChanges,
                               FileChangeRow selectedFileChange) {
            this.document = document;
            this.commits = Collections.unmodifiableList(commits);
            this.fileChanges = Collections.unmodifiableList(fileChanges);
            this.selectedFileChange = selectedFileChange;
        }

        public DocumentSummary getDocument() {
            return document;
        }

        public List<CommitRow> getCommits() {
            return commits;
        }

        public List<FileChangeRow> getFileChanges() {
            return fileChanges;
        }

        public FileChangeRow getSelectedFileChange() {
            return selectedFileChange;
        }
    }
}
