package ru.spb.reshenie.chekerstatus.application.gitlab;

import ru.spb.reshenie.chekerstatus.domain.gitlab.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLinkRefreshResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitSyncError;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitLabSyncSettings;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitTrackingRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import ru.spb.reshenie.chekerstatus.application.sync.NsiSyncRunService;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunLogLevel;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

@Service
public class GitCommitTrackingService {

    private static final Logger log = LoggerFactory.getLogger(GitCommitTrackingService.class);

    private final GitLabSyncSettings settings;
    private final GitTrackingRepositoryPort repository;
    private final GitLabVirtualThreadSyncExecutor syncExecutor;
    private final NsiSyncRunService syncRunService;

    public GitCommitTrackingService(GitLabSyncSettings settings,
                                    GitTrackingRepositoryPort repository,
                                    GitLabVirtualThreadSyncExecutor syncExecutor,
                                    NsiSyncRunService syncRunService) {
        this.settings = settings;
        this.repository = repository;
        this.syncExecutor = syncExecutor;
        this.syncRunService = syncRunService;
    }

    public GitCommitTrackingResult synchronizeDictionaryDocuments(long dictionaryId) {
        return synchronizeDictionaryDocuments(dictionaryId, null);
    }

    public GitCommitTrackingResult synchronizeDictionaryDocuments(long dictionaryId,
                                                                  Consumer<GitCommitTrackingResult> progressListener) {
        return synchronizeDictionaryDocuments(dictionaryId, null, null, progressListener);
    }

    public GitCommitTrackingResult synchronizeDictionaryDocuments(long dictionaryId,
                                                                  Long runId,
                                                                  String dictionaryIdentifier,
                                                                  Consumer<GitCommitTrackingResult> progressListener) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (!settings.isEnabled()) {
            log.info("GitLab commit tracking is disabled");
            addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier,
                    "GitLab-синхронизация отключена", null);
            return result;
        }

        GitLinkRefreshResult refreshResult = repository.refreshAndFindActiveLinks(dictionaryId);
        List<DocumentGitLink> documents = refreshResult.getActiveLinks();
        result.setLinksFound(refreshResult.getInsertedLinks());
        addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_LINK_PARSE, dictionaryIdentifier,
                "GitLab-ссылки обновлены", linkRefreshDetails(refreshResult));
        notifyProgress(progressListener, result);
        if (documents.isEmpty()) {
            log.info("No active GitLab document links found: dictionaryId={}", dictionaryId);
            return result;
        }

        List<DocumentGitLink> documentsToProcess = documents;
        int limit = settings.getMaxDocumentsPerRun();
        if (limit > 0 && documents.size() > limit) {
            documentsToProcess = documents.subList(0, limit);
        }
        final int projectsTotal = documentsToProcess.size();
        result.setProjectsTotal(projectsTotal);
        notifyProgress(progressListener, result);

        if (settings.isRequireToken() && !StringUtils.hasText(settings.getToken())) {
            String error = "GitLab token is not configured";
            for (DocumentGitLink document : documentsToProcess) {
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
            result.addProjectsChecked(projectsTotal);
            log.warn("{}: dictionaryId={}, documents={}", error, dictionaryId, documentsToProcess.size());
            addLog(runId, SyncRunLogLevel.ERROR, SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier,
                    error, documentsDetails(documentsToProcess));
            notifyProgress(progressListener, result);
            return result;
        }

        Consumer<GitCommitTrackingResult> aggregateProgress = new Consumer<GitCommitTrackingResult>() {
            @Override
            public void accept(GitCommitTrackingResult processedProgress) {
                GitCommitTrackingResult progress = new GitCommitTrackingResult();
                progress.setLinksFound(refreshResult.getInsertedLinks());
                progress.setProjectsTotal(projectsTotal);
                progress.merge(processedProgress);
                notifyProgress(progressListener, progress);
            }
        };
        GitCommitTrackingResult processedResult = settings.isVirtualThreadsEnabled()
                ? syncExecutor.synchronizeDocuments(documentsToProcess, runId, dictionaryIdentifier, aggregateProgress)
                : syncExecutor.synchronizeDocumentsSequentially(documentsToProcess, runId, dictionaryIdentifier,
                        aggregateProgress);
        result.merge(processedResult);
        notifyProgress(progressListener, result);

        log.info("GitLab commit tracking finished: dictionaryId={}, documents={}, processed={}, errors={}, commits={}, files={}",
                dictionaryId,
                documents.size(),
                result.getProjectsProcessed(),
                result.getErrorCount(),
                result.getCommitsProcessed(),
                result.getFilesProcessed());
        addLog(runId, result.hasErrors() ? SyncRunLogLevel.WARN : SyncRunLogLevel.INFO,
                SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier, "GitLab-синхронизация завершена",
                gitResultDetails(result, documents.size()));
        return result;
    }

    private void notifyProgress(Consumer<GitCommitTrackingResult> progressListener, GitCommitTrackingResult result) {
        if (progressListener != null) {
            progressListener.accept(result);
        }
    }

    private void addLog(Long runId,
                        SyncRunLogLevel level,
                        SyncErrorStage stage,
                        String dictionaryIdentifier,
                        String message,
                        Map<String, Object> details) {
        if (runId == null) {
            return;
        }
        syncRunService.safeAddLog(runId.longValue(), level, stage, dictionaryIdentifier,
                null, null, null, null, message, null, details);
    }

    private Map<String, Object> linkRefreshDetails(GitLinkRefreshResult result) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("activeLinks", Integer.valueOf(result.getActiveLinks().size()));
        details.put("insertedLinks", Integer.valueOf(result.getInsertedLinks()));
        details.put("updatedLinks", Integer.valueOf(result.getUpdatedLinks()));
        details.put("deactivatedLinks", Integer.valueOf(result.getDeactivatedLinks()));
        details.put("parseErrors", Integer.valueOf(result.getParseErrors()));
        return details;
    }

    private Map<String, Object> documentsDetails(List<DocumentGitLink> documents) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("documents", Integer.valueOf(documents == null ? 0 : documents.size()));
        return details;
    }

    private Map<String, Object> gitResultDetails(GitCommitTrackingResult result, int activeLinks) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("activeLinks", Integer.valueOf(activeLinks));
        details.put("insertedLinks", Integer.valueOf(result.getLinksFound()));
        details.put("projectsProcessed", Integer.valueOf(result.getProjectsProcessed()));
        details.put("commitsProcessed", Integer.valueOf(result.getCommitsProcessed()));
        details.put("filesProcessed", Integer.valueOf(result.getFilesProcessed()));
        details.put("errors", Integer.valueOf(result.getErrorCount()));
        return details;
    }
}
