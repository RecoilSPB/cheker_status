package ru.spb.reshenie.chekerstatus.gitlab.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitFileHistorySyncResult {

    private final int processedCommits;
    private final int processedFiles;
    private final int failedCommits;
    private final int failedFiles;
    private final String firstError;
    private final List<GitSyncError> errors;

    public GitFileHistorySyncResult(int processedCommits,
                                    int processedFiles,
                                    int failedCommits,
                                    int failedFiles,
                                    String firstError) {
        this(processedCommits, processedFiles, failedCommits, failedFiles, firstError,
                new ArrayList<GitSyncError>());
    }

    public GitFileHistorySyncResult(int processedCommits,
                                    int processedFiles,
                                    int failedCommits,
                                    int failedFiles,
                                    String firstError,
                                    List<GitSyncError> errors) {
        this.processedCommits = processedCommits;
        this.processedFiles = processedFiles;
        this.failedCommits = failedCommits;
        this.failedFiles = failedFiles;
        this.firstError = firstError;
        this.errors = Collections.unmodifiableList(new ArrayList<GitSyncError>(errors));
    }

    public static GitFileHistorySyncResult disabled() {
        return new GitFileHistorySyncResult(0, 0, 0, 0, null);
    }

    public int getProcessedCommits() {
        return processedCommits;
    }

    public int getProcessedFiles() {
        return processedFiles;
    }

    public int getFailedCommits() {
        return failedCommits;
    }

    public int getFailedFiles() {
        return failedFiles;
    }

    public String getFirstError() {
        return firstError;
    }

    public List<GitSyncError> getErrors() {
        return errors;
    }

    public boolean hasErrors() {
        return failedCommits > 0 || failedFiles > 0;
    }

    public String summary() {
        return "fileHistory processedCommits=" + processedCommits
                + ", processedFiles=" + processedFiles
                + ", failedCommits=" + failedCommits
                + ", failedFiles=" + failedFiles
                + (firstError == null ? "" : ", firstError=" + firstError);
    }
}
