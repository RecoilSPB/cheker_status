package ru.spb.reshenie.chekerstatus.gitlab.model;

public class GitFileContentSnapshot {

    private final String content;
    private final String sha256;
    private final Long size;

    public GitFileContentSnapshot(String content, String sha256, Long size) {
        this.content = content;
        this.sha256 = sha256;
        this.size = size;
    }

    public String getContent() {
        return content;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getSize() {
        return size;
    }
}
