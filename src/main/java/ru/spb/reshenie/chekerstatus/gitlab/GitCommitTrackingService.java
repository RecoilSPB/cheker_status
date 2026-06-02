package ru.spb.reshenie.chekerstatus.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.spb.reshenie.chekerstatus.config.GitLabProperties;
import ru.spb.reshenie.chekerstatus.sync.SyncErrorStage;

import java.util.List;

@Service
public class GitCommitTrackingService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitTrackingService.class);

    private final GitLabProperties properties;
    private final GitTrackingRepository repository;
    private final GitLabVirtualThreadSyncExecutor syncExecutor;

    public GitCommitTrackingService(GitLabProperties properties,
                                    GitTrackingRepository repository,
                                    GitLabVirtualThreadSyncExecutor syncExecutor) {
        this.properties = properties;
        this.repository = repository;
        this.syncExecutor = syncExecutor;
    }

    public GitCommitTrackingResult synchronizeDictionaryDocuments(long dictionaryId) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (!properties.isEnabled()) {
            log.info("GitLab commit tracking is disabled");
            return result;
        }

        List<DocumentGitLink> documents = repository.refreshAndFindActiveLinks(dictionaryId);
        result.setLinksFound(documents.size());
        if (documents.isEmpty()) {
            log.info("No active GitLab document links found: dictionaryId={}", dictionaryId);
            return result;
        }

        if (properties.isRequireToken() && !StringUtils.hasText(properties.getToken())) {
            String error = "GitLab token is not configured";
            for (DocumentGitLink document : documents) {
                repository.markSyncError(document.getId(), error);
                result.addError(new GitSyncError(
                        SyncErrorStage.GITLAB_COMMITS,
                        document.getId(),
                        document.getProjectPath(),
                        null,
                        null,
                        error,
                        null
                ));
            }
            log.warn("{}: dictionaryId={}, documents={}", error, dictionaryId, documents.size());
            return result;
        }

        List<DocumentGitLink> documentsToProcess = documents;
        int limit = properties.getMaxDocumentsPerRun();
        if (limit > 0 && documents.size() > limit) {
            documentsToProcess = documents.subList(0, limit);
        }

        GitCommitTrackingResult processedResult = properties.isVirtualThreadsEnabled()
                ? syncExecutor.synchronizeDocuments(documentsToProcess)
                : syncExecutor.synchronizeDocumentsSequentially(documentsToProcess);
        result.merge(processedResult);

        log.info("GitLab commit tracking finished: dictionaryId={}, documents={}, processed={}, errors={}, commits={}, files={}",
                dictionaryId,
                documents.size(),
                result.getProjectsProcessed(),
                result.getErrorCount(),
                result.getCommitsProcessed(),
                result.getFilesProcessed());
        return result;
    }

}
