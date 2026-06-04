package ru.spb.reshenie.chekerstatus.web.model;

import java.time.OffsetDateTime;

public class DashboardStats {
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
