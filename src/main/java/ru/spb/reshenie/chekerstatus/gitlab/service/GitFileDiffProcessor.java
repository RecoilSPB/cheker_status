package ru.spb.reshenie.chekerstatus.gitlab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.gitlab.diff.FileDiffEngine;
import ru.spb.reshenie.chekerstatus.gitlab.diff.FileDiffSource;
import ru.spb.reshenie.chekerstatus.gitlab.diff.FileTypeClassifier;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffArtifact;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitFileDiffRepository;
import ru.spb.reshenie.chekerstatus.gitlab.storage.GitFileStorage;

import java.nio.charset.StandardCharsets;

@Service
public class GitFileDiffProcessor {

    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    private final FileDiffEngine fileDiffEngine;
    private final GitFileDiffRepository gitFileDiffRepository;
    private final GitFileStorage gitFileStorage;
    private final ObjectMapper objectMapper;

    public GitFileDiffProcessor(FileDiffEngine fileDiffEngine,
                                GitFileDiffRepository gitFileDiffRepository,
                                GitFileStorage gitFileStorage,
                                ObjectMapper objectMapper) {
        this.fileDiffEngine = fileDiffEngine;
        this.gitFileDiffRepository = gitFileDiffRepository;
        this.gitFileStorage = gitFileStorage;
        this.objectMapper = objectMapper;
    }

    public GitFileDiffProcessResult process(long fileChangeId, GitCommitFileChange change) {
        return process(fileChangeId,
                change.getGitLinkId(),
                change.getFilePath(),
                change.getOldPath(),
                change.getNewPath(),
                change.getChangeType(),
                change.getContentFetchStatus(),
                change.getContentFetchError(),
                change.getContentBefore(),
                change.getContentAfter());
    }

    public GitFileDiffProcessResult process(StoredGitCommitFileChange change) {
        return process(change.getId(),
                change.getGitLinkId(),
                change.getFilePath(),
                change.getOldPath(),
                change.getNewPath(),
                change.getChangeType(),
                change.getContentFetchStatus(),
                change.getContentFetchError(),
                hydrate(change.getContentBefore()),
                hydrate(change.getContentAfter()));
    }

    private GitFileDiffProcessResult process(long fileChangeId,
                                             long gitLinkId,
                                             String filePath,
                                             String oldPath,
                                             String newPath,
                                             String changeType,
                                             String contentFetchStatus,
                                             String contentFetchError,
                                             GitFileContentSnapshot before,
                                             GitFileContentSnapshot after) {
        String formatFamily = FileTypeClassifier.formatFamily(filePath, firstAvailableBytes(before, after));
        if ("FAILED".equals(contentFetchStatus)) {
            return saveFailure(fileChangeId, gitLinkId, formatFamily,
                    trim(contentFetchError == null ? "Content fetch failed" : contentFetchError));
        }
        try {
            FileDiffSource source = new FileDiffSource(fileChangeId, gitLinkId, filePath, oldPath, newPath, changeType, before, after);
            StructuredFileDiffArtifact artifact = fileDiffEngine.buildDiff(source);
            byte[] artifactBytes = objectMapper.writeValueAsBytes(artifact);
            String artifactObjectKey = gitFileStorage.storeArtifact(gitLinkId, fileChangeId, filePath, artifactBytes);
            if (artifactObjectKey == null || artifactObjectKey.isBlank()) {
                return saveFailure(fileChangeId, gitLinkId, artifact.getFormatFamily(),
                        "Structured diff artifact storage did not return object key");
            }
            String summaryJson = objectMapper.writeValueAsString(artifact.getSummary());
            gitFileDiffRepository.save(fileChangeId, gitLinkId, STATUS_SUCCESS, artifact.getDiffType(),
                    artifact.getFormatFamily(), summaryJson, artifactObjectKey, null);
            return new GitFileDiffProcessResult(STATUS_SUCCESS, artifact.getDiffType(), artifact.getFormatFamily(), null);
        } catch (Exception e) {
            return saveFailure(fileChangeId, gitLinkId, formatFamily,
                    trim("Cannot build structured diff: " + e.getMessage()));
        }
    }

    private GitFileDiffProcessResult saveFailure(long fileChangeId, long gitLinkId, String formatFamily, String error) {
        gitFileDiffRepository.save(fileChangeId, gitLinkId, STATUS_FAILED, "METADATA",
                formatFamily == null ? "BINARY" : formatFamily,
                null, null, error);
        return new GitFileDiffProcessResult(STATUS_FAILED, "METADATA", formatFamily, error);
    }

    private GitFileContentSnapshot hydrate(GitFileContentSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }
        if (snapshot.hasRawBytes()) {
            return snapshot;
        }
        byte[] bytes = null;
        if (snapshot.getObjectKey() != null) {
            bytes = gitFileStorage.read(snapshot.getObjectKey());
        } else if (snapshot.getContent() != null) {
            bytes = snapshot.getContent().getBytes(StandardCharsets.UTF_8);
        }
        return new GitFileContentSnapshot(snapshot.getContent(), snapshot.getObjectKey(), snapshot.getSha256(),
                snapshot.getSize(), bytes);
    }

    private byte[] firstAvailableBytes(GitFileContentSnapshot before, GitFileContentSnapshot after) {
        if (after != null) {
            if (after.hasRawBytes()) {
                return after.getRawBytes();
            }
            if (after.getContent() != null) {
                return after.getContent().getBytes(StandardCharsets.UTF_8);
            }
        }
        if (before != null) {
            if (before.hasRawBytes()) {
                return before.getRawBytes();
            }
            if (before.getContent() != null) {
                return before.getContent().getBytes(StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 4000 ? value.substring(0, 4000) : value;
    }
}
