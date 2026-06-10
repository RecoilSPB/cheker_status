package ru.spb.reshenie.chekerstatus.gitlab.service;

public class GitFileDiffProcessResult {

    private final String status;
    private final String diffType;
    private final String formatFamily;
    private final String error;

    public GitFileDiffProcessResult(String status, String diffType, String formatFamily, String error) {
        this.status = status;
        this.diffType = diffType;
        this.formatFamily = formatFamily;
        this.error = error;
    }

    public String getStatus() {
        return status;
    }

    public String getDiffType() {
        return diffType;
    }

    public String getFormatFamily() {
        return formatFamily;
    }

    public String getError() {
        return error;
    }

    public boolean hasError() {
        return "FAILED".equals(status);
    }
}
