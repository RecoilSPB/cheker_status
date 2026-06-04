package ru.spb.reshenie.chekerstatus.gitlab.model;

import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class GitCommitTrackingResult {

    private final AtomicInteger linksFound = new AtomicInteger();
    private final AtomicInteger projectsProcessed = new AtomicInteger();
    private final AtomicInteger commitsProcessed = new AtomicInteger();
    private final AtomicInteger filesProcessed = new AtomicInteger();
    private final AtomicInteger projectsTotal = new AtomicInteger();
    private final AtomicInteger projectsChecked = new AtomicInteger();
    private final ConcurrentLinkedQueue<GitSyncError> errors = new ConcurrentLinkedQueue<GitSyncError>();

    public int getLinksFound() {
        return linksFound.get();
    }

    public void setLinksFound(int linksFound) {
        this.linksFound.set(linksFound);
    }

    public int getProjectsProcessed() {
        return projectsProcessed.get();
    }

    public void incrementProjectsProcessed() {
        this.projectsProcessed.incrementAndGet();
    }

    public void addProjectsProcessed(int projectsProcessed) {
        this.projectsProcessed.addAndGet(projectsProcessed);
    }

    public int getProjectsTotal() {
        return projectsTotal.get();
    }

    public void setProjectsTotal(int projectsTotal) {
        this.projectsTotal.set(Math.max(0, projectsTotal));
    }

    public int getProjectsChecked() {
        return projectsChecked.get();
    }

    public void incrementProjectsChecked() {
        this.projectsChecked.incrementAndGet();
    }

    public void addProjectsChecked(int projectsChecked) {
        this.projectsChecked.addAndGet(projectsChecked);
    }

    public int getCommitsProcessed() {
        return commitsProcessed.get();
    }

    public void addCommitsProcessed(int commitsProcessed) {
        this.commitsProcessed.addAndGet(commitsProcessed);
    }

    public int getFilesProcessed() {
        return filesProcessed.get();
    }

    public void addFilesProcessed(int filesProcessed) {
        this.filesProcessed.addAndGet(filesProcessed);
    }

    public int getErrorCount() {
        return errors.size();
    }

    public List<GitSyncError> getErrors() {
        return Collections.unmodifiableList(new ArrayList<GitSyncError>(errors));
    }

    public void addError(GitSyncError error) {
        if (error != null) {
            errors.add(error);
        }
    }

    public void addErrors(List<GitSyncError> items) {
        if (items == null) {
            return;
        }
        for (GitSyncError item : items) {
            addError(item);
        }
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public String firstErrorMessage() {
        GitSyncError error = errors.peek();
        return error == null ? null : error.getMessage();
    }

    public void merge(GitCommitTrackingResult other) {
        if (other == null) {
            return;
        }
        addCommitsProcessed(other.getCommitsProcessed());
        addFilesProcessed(other.getFilesProcessed());
        addProjectsProcessed(other.getProjectsProcessed());
        addProjectsChecked(other.getProjectsChecked());
        if (other.getProjectsTotal() > 0) {
            setProjectsTotal(Math.max(getProjectsTotal(), other.getProjectsTotal()));
        }
        addErrors(other.getErrors());
    }
}
