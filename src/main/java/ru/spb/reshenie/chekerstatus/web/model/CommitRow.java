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
