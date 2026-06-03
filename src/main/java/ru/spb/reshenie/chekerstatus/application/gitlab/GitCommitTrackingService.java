package ru.spb.reshenie.chekerstatus.application.gitlab;

import ru.spb.reshenie.chekerstatus.domain.gitlab.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitSyncError;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitLabSyncSettings;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitTrackingRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;

import java.util.List;
import java.util.function.Consumer;

@Service
public class GitCommitTrackingService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitTrackingService.class);

    private final GitLabSyncSettings settings;
    private final GitTrackingRepositoryPort repository;
    private final GitLabVirtualThreadSyncExecutor syncExecutor;

    public GitCommitTrackingService(GitLabSyncSettings settings,
                                    GitTrackingRepositoryPort repository,
                                    GitLabVirtualThreadSyncExecutor syncExecutor) {
        this.settings = settings;
        this.repository = repository;
        this.syncExecutor = syncExecutor;
    }

    public GitCommitTrackingResult synchronizeDictionaryDocuments(long dictionaryId) {
        return synchronizeDictionaryDocuments(dictionaryId, null);
    }

    public GitCommitTrackingResult synchronizeDictionaryDocuments(long dictionaryId,
                                                                  Consumer<GitCommitTrackingResult> progressListener) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (!settings.isEnabled()) {
            log.info("GitLab commit tracking is disabled");
            return result;
        }

        List<DocumentGitLink> documents = repository.refreshAndFindActiveLinks(dictionaryId);
        result.setLinksFound(documents.size());
        notifyProgress(progressListener, result);
        if (documents.isEmpty()) {
            log.info("No active GitLab document links found: dictionaryId={}", dictionaryId);
            return result;
        }

        if (settings.isRequireToken() && !StringUtils.hasText(settings.getToken())) {
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
            notifyProgress(progressListener, result);
            return result;
        }

        List<DocumentGitLink> documentsToProcess = documents;
        int limit = settings.getMaxDocumentsPerRun();
        if (limit > 0 && documents.size() > limit) {
            documentsToProcess = documents.subList(0, limit);
        }

        Consumer<GitCommitTrackingResult> aggregateProgress = new Consumer<GitCommitTrackingResult>() {
            @Override
            public void accept(GitCommitTrackingResult processedProgress) {
                GitCommitTrackingResult progress = new GitCommitTrackingResult();
                progress.setLinksFound(documents.size());
                progress.merge(processedProgress);
                notifyProgress(progressListener, progress);
            }
        };
        GitCommitTrackingResult processedResult = settings.isVirtualThreadsEnabled()
                ? syncExecutor.synchronizeDocuments(documentsToProcess, aggregateProgress)
                : syncExecutor.synchronizeDocumentsSequentially(documentsToProcess, aggregateProgress);
        result.merge(processedResult);
        notifyProgress(progressListener, result);

        log.info("GitLab commit tracking finished: dictionaryId={}, documents={}, processed={}, errors={}, commits={}, files={}",
                dictionaryId,
                documents.size(),
                result.getProjectsProcessed(),
                result.getErrorCount(),
                result.getCommitsProcessed(),
                result.getFilesProcessed());
        return result;
    }

    private void notifyProgress(Consumer<GitCommitTrackingResult> progressListener, GitCommitTrackingResult result) {
        if (progressListener != null) {
            progressListener.accept(result);
        }
    }
}
