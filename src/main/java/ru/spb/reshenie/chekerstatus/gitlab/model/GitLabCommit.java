package ru.spb.reshenie.chekerstatus.gitlab.model;

import com.fasterxml.jackson.databind.JsonNode;
import ru.spb.reshenie.chekerstatus.common.json.JsonNodes;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

public class GitLabCommit {

    private final String id;
    private final String shortId;
    private final String title;
    private final String message;
    private final String authorName;
    private final String authorEmail;
    private final OffsetDateTime authoredDate;
    private final String committerName;
    private final String committerEmail;
    private final OffsetDateTime committedDate;
    private final OffsetDateTime createdAt;
    private final String webUrl;
    private final JsonNode rawJson;

    private GitLabCommit(String id,
                         String shortId,
                         String title,
                         String message,
                         String authorName,
                         String authorEmail,
                         OffsetDateTime authoredDate,
                         String committerName,
                         String committerEmail,
                         OffsetDateTime committedDate,
                         OffsetDateTime createdAt,
                         String webUrl,
                         JsonNode rawJson) {
        this.id = id;
        this.shortId = shortId;
        this.title = title;
        this.message = message;
        this.authorName = authorName;
        this.authorEmail = authorEmail;
        this.authoredDate = authoredDate;
        this.committerName = committerName;
        this.committerEmail = committerEmail;
        this.committedDate = committedDate;
        this.createdAt = createdAt;
        this.webUrl = webUrl;
        this.rawJson = rawJson;
    }

    public static GitLabCommit fromJson(JsonNode json) {
        return new GitLabCommit(
                JsonNodes.text(json, "id"),
                JsonNodes.text(json, "short_id"),
                JsonNodes.text(json, "title"),
                JsonNodes.text(json, "message"),
                JsonNodes.text(json, "author_name"),
                JsonNodes.text(json, "author_email"),
                parseDate(JsonNodes.text(json, "authored_date")),
                JsonNodes.text(json, "committer_name"),
                JsonNodes.text(json, "committer_email"),
                parseDate(JsonNodes.text(json, "committed_date")),
                parseDate(JsonNodes.text(json, "created_at")),
                JsonNodes.text(json, "web_url"),
                json
        );
    }

    private static OffsetDateTime parseDate(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    public String getId() {
        return id;
    }

    public String getShortId() {
        return shortId;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getAuthorName() {
        return authorName;
    }

    public String getAuthorEmail() {
        return authorEmail;
    }

    public OffsetDateTime getAuthoredDate() {
        return authoredDate;
    }

    public String getCommitterName() {
        return committerName;
    }

    public String getCommitterEmail() {
        return committerEmail;
    }

    public OffsetDateTime getCommittedDate() {
        return committedDate;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getWebUrl() {
        return webUrl;
    }

    public JsonNode getRawJson() {
        return rawJson;
    }
}
