package ru.spb.reshenie.chekerstatus.gitlab;

public class GitLabLink {

    private final String originalUrl;
    private final String host;
    private final String projectPath;
    private final String treeRef;

    public GitLabLink(String originalUrl, String host, String projectPath, String treeRef) {
        this.originalUrl = originalUrl;
        this.host = host;
        this.projectPath = projectPath;
        this.treeRef = treeRef;
    }

    public String getOriginalUrl() {
        return originalUrl;
    }

    public String getHost() {
        return host;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getTreeRef() {
        return treeRef;
    }
}
