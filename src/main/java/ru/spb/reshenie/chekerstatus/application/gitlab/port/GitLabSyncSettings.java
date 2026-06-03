package ru.spb.reshenie.chekerstatus.application.gitlab.port;

public interface GitLabSyncSettings {

    boolean isEnabled();

    boolean isRequireToken();

    String getToken();

    int getMaxDocumentsPerRun();

    boolean isFileHistoryEnabled();

    boolean isFetchFileContent();

    int getMaxFilesPerCommit();

    long getMaxFileSizeBytes();

    boolean isVirtualThreadsEnabled();

    int getMaxConcurrentProjects();

    int getMaxConcurrentCommits();

    int getMaxConcurrentFiles();

    int getMaxConcurrentDbWrites();
}
