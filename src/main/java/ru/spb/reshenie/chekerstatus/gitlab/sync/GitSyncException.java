package ru.spb.reshenie.chekerstatus.gitlab.sync;

public class GitSyncException extends RuntimeException {

    public GitSyncException(String message) {
        super(message);
    }

    public GitSyncException(String message, Throwable cause) {
        super(message, cause);
    }
}
