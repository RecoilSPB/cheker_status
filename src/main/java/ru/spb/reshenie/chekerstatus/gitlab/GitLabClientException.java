package ru.spb.reshenie.chekerstatus.gitlab;

public class GitLabClientException extends RuntimeException {

    public GitLabClientException(String message) {
        super(message);
    }

    public GitLabClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
