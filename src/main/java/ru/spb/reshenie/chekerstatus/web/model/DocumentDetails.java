package ru.spb.reshenie.chekerstatus.web.model;

import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffArtifact;

import java.util.Collections;
import java.util.List;

public class DocumentDetails {
    private final DocumentSummary document;
    private final List<CommitRow> commits;
    private final List<FileChangeRow> fileChanges;
    private final FileChangeRow selectedFileChange;
    private final StructuredFileDiffArtifact structuredDiff;

    public DocumentDetails(DocumentSummary document,
                           List<CommitRow> commits,
                           List<FileChangeRow> fileChanges,
                           FileChangeRow selectedFileChange) {
        this(document, commits, fileChanges, selectedFileChange, null);
    }

    public DocumentDetails(DocumentSummary document,
                           List<CommitRow> commits,
                           List<FileChangeRow> fileChanges,
                           FileChangeRow selectedFileChange,
                           StructuredFileDiffArtifact structuredDiff) {
        this.document = document;
        this.commits = Collections.unmodifiableList(commits);
        this.fileChanges = Collections.unmodifiableList(fileChanges);
        this.selectedFileChange = selectedFileChange;
        this.structuredDiff = structuredDiff;
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

    public StructuredFileDiffArtifact getStructuredDiff() {
        return structuredDiff;
    }

    public DocumentDetails withStructuredDiff(StructuredFileDiffArtifact value) {
        return new DocumentDetails(document, commits, fileChanges, selectedFileChange, value);
    }
}
