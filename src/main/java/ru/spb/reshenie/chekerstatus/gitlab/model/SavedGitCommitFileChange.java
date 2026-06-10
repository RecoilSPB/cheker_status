package ru.spb.reshenie.chekerstatus.gitlab.model;

public class SavedGitCommitFileChange {

    private final long id;
    private final boolean inserted;

    public SavedGitCommitFileChange(long id, boolean inserted) {
        this.id = id;
        this.inserted = inserted;
    }

    public long getId() {
        return id;
    }

    public boolean isInserted() {
        return inserted;
    }
}
