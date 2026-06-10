package ru.spb.reshenie.chekerstatus.gitlab.storage;

import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.util.StringUtils;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitContentUtils;
import ru.spb.reshenie.chekerstatus.config.minio.MinioProperties;
import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

public class MinioGitFileStorage implements GitFileStorage {

    private static final Logger log = LoggerFactory.getLogger(MinioGitFileStorage.class);

    private final MinioClient minioClient;
    private final MinioProperties properties;
    private final Object bucketMonitor = new Object();
    private volatile boolean bucketReady;

    public MinioGitFileStorage(MinioClient minioClient, MinioProperties properties) {
        this.minioClient = minioClient;
        this.properties = properties;
    }

    @Override
    public boolean storesExternally() {
        return true;
    }

    @Override
    public String store(DocumentGitLink document, String filePath, String ref, byte[] content, String sha256) {
        byte[] bytes = content == null ? new byte[0] : content;
        String objectKey = objectKey(document, filePath, ref, sha256);
        return put(objectKey, bytes, contentType(filePath), "Cannot store GitLab file in MinIO: bucket="
                + properties.getBucket() + ", ref=" + ref + ", file=" + filePath + ", sha256=" + sha256);
    }

    @Override
    public String storeArtifact(long gitLinkId, long fileChangeId, String filePath, byte[] content) {
        byte[] bytes = content == null ? new byte[0] : content;
        String objectKey = artifactObjectKey(gitLinkId, fileChangeId, filePath, GitContentUtils.sha256(bytes));
        return put(objectKey, bytes, MediaType.APPLICATION_JSON_VALUE,
                "Cannot store GitLab diff artifact in MinIO: bucket=" + properties.getBucket()
                        + ", fileChangeId=" + fileChangeId + ", file=" + filePath);
    }

    @Override
    public byte[] read(String objectKey) {
        ensureBucketExists();
        try (var inputStream = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(properties.getBucket())
                        .object(objectKey)
                        .build()
        )) {
            return inputStream.readAllBytes();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read GitLab object from MinIO: bucket=" + properties.getBucket()
                    + ", objectKey=" + objectKey
                    + ", reason=" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    String objectKey(DocumentGitLink document, String filePath, String ref, String sha256) {
        StringBuilder key = new StringBuilder();
        appendPath(key, properties.getObjectPrefix());
        appendSegment(key, "git-link-" + (document == null ? "unknown" : document.getId()));
        appendSegment(key, "ref-" + safeSegment(ref));
        appendSegment(key, safeSegment(sha256) + "-" + shortPathHash(filePath) + extension(filePath));
        return key.toString();
    }

    String artifactObjectKey(long gitLinkId, long fileChangeId, String filePath, String sha256) {
        StringBuilder key = new StringBuilder();
        appendPath(key, properties.getObjectPrefix());
        appendSegment(key, "diff-artifacts");
        appendSegment(key, "git-link-" + gitLinkId);
        appendSegment(key, "file-change-" + fileChangeId);
        appendSegment(key, safeSegment(sha256) + "-" + shortPathHash(filePath) + ".json");
        return key.toString();
    }

    private String put(String objectKey, byte[] bytes, String contentType, String errorPrefix) {
        ensureBucketExists();
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(bytes)) {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(properties.getBucket())
                            .object(objectKey)
                            .stream(inputStream, bytes.length, -1)
                            .contentType(contentType)
                            .build()
            );
            return objectKey;
        } catch (Exception e) {
            throw new IllegalStateException(errorPrefix
                    + ", reason=" + e.getClass().getSimpleName() + ": " + e.getMessage(), e);
        }
    }

    private void ensureBucketExists() {
        if (bucketReady) {
            return;
        }
        synchronized (bucketMonitor) {
            if (bucketReady) {
                return;
            }
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(properties.getBucket()).build()
                );
                if (!exists) {
                    if (!properties.isAutoCreateBucket()) {
                        throw new IllegalStateException("MinIO bucket does not exist: " + properties.getBucket());
                    }
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(properties.getBucket()).build());
                    log.info("Created MinIO bucket for GitLab file history: {}", properties.getBucket());
                }
                bucketReady = true;
            } catch (Exception e) {
                throw new IllegalStateException("Cannot initialize MinIO bucket: " + properties.getBucket(), e);
            }
        }
    }

    private String contentType(String filePath) {
        return MediaTypeFactory.getMediaType(filePath)
                .map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
    }

    private void appendPath(StringBuilder target, String rawValue) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim() : "_";
        String normalized = value.replace('\\', '/');
        String[] parts = normalized.split("/");
        for (String part : parts) {
            appendSegment(target, part);
        }
    }

    private void appendSegment(StringBuilder target, String rawValue) {
        String value = StringUtils.hasText(rawValue) ? rawValue.trim() : "_";
        if (!target.isEmpty()) {
            target.append('/');
        }
        target.append(safeSegment(value));
    }

    private String safeSegment(String rawValue) {
        if (!StringUtils.hasText(rawValue)) {
            return "_";
        }
        String value = rawValue.trim();
        StringBuilder builder = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if ((ch >= 'a' && ch <= 'z')
                    || (ch >= 'A' && ch <= 'Z')
                    || (ch >= '0' && ch <= '9')
                    || ch == '.' || ch == '-' || ch == '_') {
                builder.append(ch);
            } else {
                builder.append('_');
            }
        }
        return builder.isEmpty() ? "_" : builder.toString();
    }

    private String shortPathHash(String filePath) {
        String hash = GitContentUtils.sha256((filePath == null ? "" : filePath).getBytes(StandardCharsets.UTF_8));
        return hash.substring(0, 12);
    }

    private String extension(String filePath) {
        if (!StringUtils.hasText(filePath)) {
            return "";
        }
        int slashIndex = Math.max(filePath.lastIndexOf('/'), filePath.lastIndexOf('\\'));
        int dotIndex = filePath.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex <= slashIndex) {
            return "";
        }
        String extension = filePath.substring(dotIndex).toLowerCase();
        return extension.length() > 16 ? extension.substring(0, 16) : extension;
    }
}
