package ru.spb.reshenie.chekerstatus.gitlab.service;

public class GitFileDiffBackfillResult {

    private final FileDiffBackfillScope scope;
    private final Long documentId;
    private final int requestedLimit;
    private final int attempted;
    private final int succeeded;
    private final int failed;

    public GitFileDiffBackfillResult(FileDiffBackfillScope scope,
                                     Long documentId,
                                     int requestedLimit,
                                     int attempted,
                                     int succeeded,
                                     int failed) {
        this.scope = scope;
        this.documentId = documentId;
        this.requestedLimit = requestedLimit;
        this.attempted = attempted;
        this.succeeded = succeeded;
        this.failed = failed;
    }

    public FileDiffBackfillScope getScope() {
        return scope;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public int getRequestedLimit() {
        return requestedLimit;
    }

    public int getAttempted() {
        return attempted;
    }

    public int getSucceeded() {
        return succeeded;
    }

    public int getFailed() {
        return failed;
    }
}
