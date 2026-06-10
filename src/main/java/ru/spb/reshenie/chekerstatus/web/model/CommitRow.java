package ru.spb.reshenie.chekerstatus.web.model;

import java.time.OffsetDateTime;

public class CommitRow {
    private final long id;
    private final long documentId;
    private final String documentName;
    private final String commitSha;
    private final String shortId;
    private final String title;
    private final String authorName;
    private final OffsetDateTime committedDate;
    private final long fileCount;
    private final String fileHistoryStatus;
    private final String webUrl;

    public CommitRow(long id,
                     String commitSha,
                     String shortId,
                     String title,
                     String authorName,
                     OffsetDateTime committedDate,
                     long fileCount,
                     String webUrl) {
        this(id, 0, null, commitSha, shortId, title, authorName, committedDate, fileCount, "SUCCESS", webUrl);
    }

    public CommitRow(long id,
                     String commitSha,
                     String shortId,
                     String title,
                     String authorName,
                     OffsetDateTime committedDate,
                     long fileCount,
                     String fileHistoryStatus,
                     String webUrl) {
        this(id, 0, null, commitSha, shortId, title, authorName, committedDate, fileCount, fileHistoryStatus, webUrl);
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
        this(id, 0, null, commitSha, shortId, title, authorName, committedDate, fileCount,
                fileHistoryLoaded ? "SUCCESS" : "PENDING", webUrl);
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
        this(id, documentId, documentName, commitSha, shortId, title, authorName, committedDate, fileCount,
                "SUCCESS", webUrl);
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
        this(id, documentId, documentName, commitSha, shortId, title, authorName, committedDate, fileCount,
                fileHistoryLoaded ? "SUCCESS" : "PENDING", webUrl);
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
                     String fileHistoryStatus,
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
        this.fileHistoryStatus = fileHistoryStatus;
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
        return "SUCCESS".equalsIgnoreCase(fileHistoryStatus);
    }

    public boolean isFileHistoryFailed() {
        return "FAILED".equalsIgnoreCase(fileHistoryStatus);
    }

    public String getFileHistoryStatus() {
        return fileHistoryStatus;
    }

    public String getFileHistoryStatusCss() {
        if (isFileHistoryFailed()) {
            return "status-error";
        }
        if (isFileHistoryLoaded()) {
            return "status-ok";
        }
        return "status-muted";
    }

    public String getFileHistoryDisplayText() {
        if (isFileHistoryLoaded()) {
            return Long.toString(fileCount);
        }
        if (isFileHistoryFailed()) {
            return "error";
        }
        return "not loaded";
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
