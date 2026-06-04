package ru.spb.reshenie.chekerstatus.gitlab.model;

public class GitFileContentRequestPlan {

    private final boolean fetchAfter;
    private final String afterPath;
    private final String afterRef;
    private final boolean fetchBefore;
    private final String beforePath;
    private final String beforeRef;

    public GitFileContentRequestPlan(boolean fetchAfter,
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

    public boolean isFetchAfter() {
        return fetchAfter;
    }

    public String getAfterPath() {
        return afterPath;
    }

    public String getAfterRef() {
        return afterRef;
    }

    public boolean isFetchBefore() {
        return fetchBefore;
    }

    public String getBeforePath() {
        return beforePath;
    }

    public String getBeforeRef() {
        return beforeRef;
    }
}
