package ru.spb.reshenie.chekerstatus.domain.sync;

public enum SyncErrorStage {
    NSI_PASSPORT,
    NSI_DATA,
    NSI_PERSISTENCE,
    GITLAB_LINK_PARSE,
    GITLAB_COMMITS,
    GITLAB_COMMIT_DIFF,
    GITLAB_FILE_CONTENT,
    UNKNOWN
}
