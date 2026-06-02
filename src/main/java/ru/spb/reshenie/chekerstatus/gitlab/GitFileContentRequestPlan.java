package ru.spb.reshenie.chekerstatus.gitlab;

class GitFileContentRequestPlan {

    private final boolean fetchAfter;
    private final String afterPath;
    private final String afterRef;
    private final boolean fetchBefore;
    private final String beforePath;
    private final String beforeRef;

    GitFileContentRequestPlan(boolean fetchAfter,
                              String afterPath,
                              String afterRef,
                              boolean fetchBefore,
                              String beforePath,
                              String beforeRef) {
        this.fetchAfter = fetchAfter;
        this.afterPath = afterPath;
        this.afterRef = afterRef;
        this.fetchBefore = fetchBefore;
        this.beforePath = beforePath;
        this.beforeRef = beforeRef;
    }

    boolean isFetchAfter() {
        return fetchAfter;
    }

    String getAfterPath() {
        return afterPath;
    }

    String getAfterRef() {
        return afterRef;
    }

    boolean isFetchBefore() {
        return fetchBefore;
    }

    String getBeforePath() {
        return beforePath;
    }

    String getBeforeRef() {
        return beforeRef;
    }
}
