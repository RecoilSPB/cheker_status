package ru.spb.reshenie.chekerstatus.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "gitlab")
public class GitLabProperties {

    private boolean enabled = true;
    private boolean requireToken = true;
    private String baseUrl = "https://git.minzdrav.gov.ru";
    private String token;
    private int perPage = 100;
    private int maxPages = 1000;
    private int maxDocumentsPerRun = 0;
    private boolean fileHistoryEnabled = true;
    private boolean fetchFileContent = true;
    private int maxFilesPerCommit = 1000;
    private long maxFileSizeBytes = 1_048_576L;
    private int retryCount = 3;
    private long retryDelayMs = 1000L;
    private boolean virtualThreadsEnabled = true;
    private int maxConcurrentProjects = 8;
    private int maxConcurrentCommits = 16;
    private int maxConcurrentFiles = 32;
    private int maxConcurrentDbWrites = 8;
    private int requestTimeoutMs = 30000;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isRequireToken() {
        return requireToken;
    }

    public void setRequireToken(boolean requireToken) {
        this.requireToken = requireToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public int getPerPage() {
        return perPage;
    }

    public void setPerPage(int perPage) {
        this.perPage = perPage;
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = maxPages;
    }

    public int getMaxDocumentsPerRun() {
        return maxDocumentsPerRun;
    }

    public void setMaxDocumentsPerRun(int maxDocumentsPerRun) {
        this.maxDocumentsPerRun = maxDocumentsPerRun;
    }

    public boolean isFileHistoryEnabled() {
        return fileHistoryEnabled;
    }

    public void setFileHistoryEnabled(boolean fileHistoryEnabled) {
        this.fileHistoryEnabled = fileHistoryEnabled;
    }

    public boolean isFetchFileContent() {
        return fetchFileContent;
    }

    public void setFetchFileContent(boolean fetchFileContent) {
        this.fetchFileContent = fetchFileContent;
    }

    public int getMaxFilesPerCommit() {
        return maxFilesPerCommit;
    }

    public void setMaxFilesPerCommit(int maxFilesPerCommit) {
        this.maxFilesPerCommit = maxFilesPerCommit;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(int retryCount) {
        this.retryCount = retryCount;
    }

    public long getRetryDelayMs() {
        return retryDelayMs;
    }

    public void setRetryDelayMs(long retryDelayMs) {
        this.retryDelayMs = retryDelayMs;
    }

    public boolean isVirtualThreadsEnabled() {
        return virtualThreadsEnabled;
    }

    public void setVirtualThreadsEnabled(boolean virtualThreadsEnabled) {
        this.virtualThreadsEnabled = virtualThreadsEnabled;
    }

    public int getMaxConcurrentProjects() {
        return maxConcurrentProjects;
    }

    public void setMaxConcurrentProjects(int maxConcurrentProjects) {
        this.maxConcurrentProjects = maxConcurrentProjects;
    }

    public int getMaxConcurrentCommits() {
        return maxConcurrentCommits;
    }

    public void setMaxConcurrentCommits(int maxConcurrentCommits) {
        this.maxConcurrentCommits = maxConcurrentCommits;
    }

    public int getMaxConcurrentFiles() {
        return maxConcurrentFiles;
    }

    public void setMaxConcurrentFiles(int maxConcurrentFiles) {
        this.maxConcurrentFiles = maxConcurrentFiles;
    }

    public int getMaxConcurrentDbWrites() {
        return maxConcurrentDbWrites;
    }

    public void setMaxConcurrentDbWrites(int maxConcurrentDbWrites) {
        this.maxConcurrentDbWrites = maxConcurrentDbWrites;
    }

    public int getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    public void setRequestTimeoutMs(int requestTimeoutMs) {
        this.requestTimeoutMs = requestTimeoutMs;
    }
}
