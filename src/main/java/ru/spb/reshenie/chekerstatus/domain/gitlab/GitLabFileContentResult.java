package ru.spb.reshenie.chekerstatus.domain.gitlab;

public class GitLabFileContentResult {

    private final boolean found;
    private final byte[] content;

    private GitLabFileContentResult(boolean found, byte[] content) {
        this.found = found;
        this.content = content;
    }

    public static GitLabFileContentResult found(byte[] content) {
        return new GitLabFileContentResult(true, content == null ? new byte[0] : content);
    }

    public static GitLabFileContentResult notFound() {
        return new GitLabFileContentResult(false, null);
    }

    public boolean isFound() {
        return found;
    }

    public byte[] getContent() {
        return content;
    }
}
