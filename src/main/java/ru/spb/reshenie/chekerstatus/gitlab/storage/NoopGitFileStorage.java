package ru.spb.reshenie.chekerstatus.gitlab.storage;

import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitContentUtils;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class NoopGitFileStorage implements GitFileStorage {

    private final Map<String, byte[]> artifacts = new ConcurrentHashMap<String, byte[]>();

    @Override
    public boolean storesExternally() {
        return false;
    }

    @Override
    public String store(DocumentGitLink document, String filePath, String ref, byte[] content, String sha256) {
        String key = objectKey("raw", document == null ? 0L : document.getId(), 0L, filePath,
                sha256 == null ? GitContentUtils.sha256(content) : sha256, ".bin");
        artifacts.put(key, copy(content));
        return key;
    }

    @Override
    public String storeArtifact(long gitLinkId, long fileChangeId, String filePath, byte[] content) {
        String key = objectKey("diff", gitLinkId, fileChangeId, filePath, GitContentUtils.sha256(content), ".json");
        artifacts.put(key, copy(content));
        return key;
    }

    @Override
    public byte[] read(String objectKey) {
        byte[] value = artifacts.get(objectKey);
        return value == null ? null : value.clone();
    }

    private String objectKey(String type,
                             long gitLinkId,
                             long fileChangeId,
                             String filePath,
                             String sha256,
                             String extension) {
        return type + "/git-link-" + gitLinkId
                + "/file-change-" + fileChangeId
                + "/" + safe(filePath)
                + "-" + safe(sha256)
                + extension;
    }

    private String safe(String value) {
        if (value == null || value.isBlank()) {
            return "_";
        }
        return value.replaceAll("[^a-zA-Z0-9._-]+", "_");
    }

    private byte[] copy(byte[] content) {
        return content == null ? new byte[0] : content.clone();
    }
}
