package ru.spb.reshenie.chekerstatus.gitlab.model;

public class GitFileContentSnapshot {

    private final String content;
    private final String objectKey;
    private final String sha256;
    private final Long size;
    private final byte[] rawBytes;

    public GitFileContentSnapshot(String content, String sha256, Long size) {
        this(content, null, sha256, size, null);
    }

    public GitFileContentSnapshot(String content, String objectKey, String sha256, Long size) {
        this(content, objectKey, sha256, size, null);
    }

    public GitFileContentSnapshot(String content, String objectKey, String sha256, Long size, byte[] rawBytes) {
        this.content = content;
        this.objectKey = objectKey;
        this.sha256 = sha256;
        this.size = size;
        this.rawBytes = rawBytes == null ? null : rawBytes.clone();
    }

    public String getContent() {
        return content;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public String getSha256() {
        return sha256;
    }

    public Long getSize() {
        return size;
    }

    public byte[] getRawBytes() {
        return rawBytes == null ? null : rawBytes.clone();
    }

    public boolean hasRawBytes() {
        return rawBytes != null;
    }
}
