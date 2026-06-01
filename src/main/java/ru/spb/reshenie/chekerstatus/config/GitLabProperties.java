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
}
