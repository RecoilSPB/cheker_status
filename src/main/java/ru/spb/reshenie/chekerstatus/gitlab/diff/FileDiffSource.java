package ru.spb.reshenie.chekerstatus.gitlab.diff;

import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;

import java.nio.charset.StandardCharsets;

public class FileDiffSource {

    private final long fileChangeId;
    private final long gitLinkId;
    private final String filePath;
    private final String oldPath;
    private final String newPath;
    private final String changeType;
    private final Version before;
    private final Version after;

    public FileDiffSource(long fileChangeId,
                          long gitLinkId,
                          String filePath,
                          String oldPath,
                          String newPath,
                          String changeType,
                          GitFileContentSnapshot before,
                          GitFileContentSnapshot after) {
        this.fileChangeId = fileChangeId;
        this.gitLinkId = gitLinkId;
        this.filePath = filePath;
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.changeType = changeType;
        this.before = new Version(oldPath == null ? filePath : oldPath, before);
        this.after = new Version(newPath == null ? filePath : newPath, after);
    }

    public long getFileChangeId() {
        return fileChangeId;
    }

    public long getGitLinkId() {
        return gitLinkId;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public String getChangeType() {
        return changeType;
    }

    public Version getBefore() {
        return before;
    }

    public Version getAfter() {
        return after;
    }

    public String getPrimaryPath() {
        if (newPath != null && !newPath.isBlank()) {
            return newPath;
        }
        if (filePath != null && !filePath.isBlank()) {
            return filePath;
        }
        return oldPath;
    }

    public static class Version {
        private final String path;
        private final String content;
        private final String objectKey;
        private final String sha256;
        private final Long size;
        private final byte[] bytes;

        Version(String path, GitFileContentSnapshot snapshot) {
            this.path = path;
            this.content = snapshot == null ? null : snapshot.getContent();
            this.objectKey = snapshot == null ? null : snapshot.getObjectKey();
            this.sha256 = snapshot == null ? null : snapshot.getSha256();
            this.size = snapshot == null ? null : snapshot.getSize();
            this.bytes = snapshot == null ? null : snapshot.getRawBytes();
        }

        public String getPath() {
            return path;
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

        public byte[] getBytes() {
            return bytes == null ? null : bytes.clone();
        }

        public boolean exists() {
            return content != null || objectKey != null || sha256 != null || size != null || bytes != null;
        }

        public boolean hasReadableContent() {
            return (bytes != null && bytes.length >= 0) || content != null;
        }

        public byte[] readableBytes() {
            if (bytes != null) {
                return bytes.clone();
            }
            if (content != null) {
                return content.getBytes(StandardCharsets.UTF_8);
            }
            return null;
        }
    }
}
