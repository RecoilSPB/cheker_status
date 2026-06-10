package ru.spb.reshenie.chekerstatus.gitlab.service;

import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitCommitFileRepository;

@Service
public class GitFileDiffBackfillService {

    private final GitCommitFileRepository gitCommitFileRepository;
    private final GitFileDiffProcessor gitFileDiffProcessor;

    public GitFileDiffBackfillService(GitCommitFileRepository gitCommitFileRepository,
                                      GitFileDiffProcessor gitFileDiffProcessor) {
        this.gitCommitFileRepository = gitCommitFileRepository;
        this.gitFileDiffProcessor = gitFileDiffProcessor;
    }

    public GitFileDiffBackfillResult backfill(FileDiffBackfillScope scope, Long documentId, int limit) {
        int safeLimit = limit <= 0 ? 100 : Math.min(limit, 1000);
        int attempted = 0;
        int succeeded = 0;
        int failed = 0;
        for (Long fileChangeId : gitCommitFileRepository.findFileChangeIdsForDiffBackfill(scope, documentId, safeLimit)) {
            attempted++;
            StoredGitCommitFileChange change = gitCommitFileRepository.findStoredFileChange(fileChangeId.longValue());
            GitFileDiffProcessResult result = gitFileDiffProcessor.process(change);
            if (result.hasError()) {
                failed++;
            } else {
                succeeded++;
            }
        }
        return new GitFileDiffBackfillResult(scope, documentId, safeLimit, attempted, succeeded, failed);
    }
}
