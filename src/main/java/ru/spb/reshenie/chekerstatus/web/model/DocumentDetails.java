package ru.spb.reshenie.chekerstatus.web.model;

import java.util.Collections;
import java.util.List;

public class DocumentDetails {
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
