package ru.spb.reshenie.chekerstatus.web.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffArtifact;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitCommitFileDiff;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitCommitFileRepository;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitFileDiffRepository;
import ru.spb.reshenie.chekerstatus.gitlab.storage.GitFileStorage;
import ru.spb.reshenie.chekerstatus.web.model.DownloadedFileVersion;
import ru.spb.reshenie.chekerstatus.web.model.FileChangeRow;

import java.nio.charset.StandardCharsets;

@Service
public class GitFileDiffViewService {

    private final GitCommitFileRepository gitCommitFileRepository;
    private final GitFileDiffRepository gitFileDiffRepository;
    private final GitFileStorage gitFileStorage;
    private final ObjectMapper objectMapper;

    public GitFileDiffViewService(GitCommitFileRepository gitCommitFileRepository,
                                  GitFileDiffRepository gitFileDiffRepository,
                                  GitFileStorage gitFileStorage,
                                  ObjectMapper objectMapper) {
        this.gitCommitFileRepository = gitCommitFileRepository;
        this.gitFileDiffRepository = gitFileDiffRepository;
        this.gitFileStorage = gitFileStorage;
        this.objectMapper = objectMapper;
    }

    public StructuredFileDiffArtifact loadStructuredDiff(long documentId, FileChangeRow fileChange) {
        if (fileChange == null || !fileChange.isArtifactAvailable()) {
            return null;
        }
        try {
            StoredGitCommitFileDiff diff = gitFileDiffRepository.findByDocumentAndFileChange(documentId, fileChange.getId());
            if (diff.getArtifactObjectKey() == null || diff.getArtifactObjectKey().isBlank()) {
                return null;
            }
            byte[] bytes = gitFileStorage.read(diff.getArtifactObjectKey());
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            return objectMapper.readValue(bytes, StructuredFileDiffArtifact.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot read structured diff artifact", e);
        }
    }

    public DownloadedFileVersion loadVersion(long documentId, long fileChangeId, String version) {
        StoredGitCommitFileChange change = gitCommitFileRepository.findStoredFileChange(documentId, fileChangeId);
        boolean beforeVersion = parseBefore(version);
        GitFileContentSnapshot snapshot = beforeVersion ? change.getContentBefore() : change.getContentAfter();
        if (snapshot == null) {
            throw new EmptyResultDataAccessException(1);
        }
        byte[] bytes = resolveBytes(snapshot);
        if (bytes == null) {
            throw new EmptyResultDataAccessException(1);
        }
        String path = beforeVersion
                ? (change.getOldPath() == null ? change.getFilePath() : change.getOldPath())
                : (change.getNewPath() == null ? change.getFilePath() : change.getNewPath());
        String contentType = MediaTypeFactory.getMediaType(path).map(MediaType::toString)
                .orElse(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        return new DownloadedFileVersion(fileName(path), contentType, bytes);
    }

    private boolean parseBefore(String version) {
        if ("before".equalsIgnoreCase(version)) {
            return true;
        }
        if ("after".equalsIgnoreCase(version)) {
            return false;
        }
        throw new IllegalArgumentException("Unknown file version: " + version);
    }

    private byte[] resolveBytes(GitFileContentSnapshot snapshot) {
        if (snapshot.hasRawBytes()) {
            return snapshot.getRawBytes();
        }
        if (snapshot.getObjectKey() != null) {
            return gitFileStorage.read(snapshot.getObjectKey());
        }
        if (snapshot.getContent() != null) {
            return snapshot.getContent().getBytes(StandardCharsets.UTF_8);
        }
        return null;
    }

    private String fileName(String path) {
        if (path == null || path.isBlank()) {
            return "file.bin";
        }
        int slashIndex = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
    }
}
