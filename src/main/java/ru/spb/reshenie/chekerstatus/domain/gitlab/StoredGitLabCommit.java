package ru.spb.reshenie.chekerstatus.domain.gitlab;

import java.time.OffsetDateTime;

public class StoredGitLabCommit {

    private final long id;
    private final long documentGitLinkId;
    private final String commitSha;
    private final OffsetDateTime committedDate;

    public StoredGitLabCommit(long id, long documentGitLinkId, String commitSha, OffsetDateTime committedDate) {
        this.id = id;
        this.documentGitLinkId = documentGitLinkId;
        this.commitSha = commitSha;
        this.committedDate = committedDate;
    }

    public long getId() {
        return id;
    }

    public long getDocumentGitLinkId() {
        return documentGitLinkId;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public OffsetDateTime getCommittedDate() {
        return committedDate;
    }
}
