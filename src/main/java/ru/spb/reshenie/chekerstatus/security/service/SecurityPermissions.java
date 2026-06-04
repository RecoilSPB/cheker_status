package ru.spb.reshenie.chekerstatus.security.service;

public final class SecurityPermissions {

    public static final String DASHBOARD_VIEW = "dashboard.view";
    public static final String DASHBOARD_METRICS_VIEW = "dashboard.metrics.view";
    public static final String DASHBOARD_SYNC_MANAGE = "dashboard.sync.manage";
    public static final String DOCUMENTS_VIEW = "documents.view";
    public static final String COMMITS_VIEW = "commits.view";
    public static final String FILE_CHANGES_VIEW = "fileChanges.view";
    public static final String USERS_MANAGE = "users.manage";

    private SecurityPermissions() {
    }
}
