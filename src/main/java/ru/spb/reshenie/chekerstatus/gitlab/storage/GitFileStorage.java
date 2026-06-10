package ru.spb.reshenie.chekerstatus.gitlab.storage;

import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;

public interface GitFileStorage {

    boolean storesExternally();

    String store(DocumentGitLink document, String filePath, String ref, byte[] content, String sha256);

    String storeArtifact(long gitLinkId, long fileChangeId, String filePath, byte[] content);

    byte[] read(String objectKey);
}
