package ru.spb.reshenie.chekerstatus.gitlab.sync;

import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileHistorySyncResult;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommit;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitSyncError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.gitlab.client.GitLabClient;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitTrackingRepository;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitCommitFileHistoryService;
import ru.spb.reshenie.chekerstatus.sync.service.SyncRunService;
import ru.spb.reshenie.chekerstatus.sync.model.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunLogLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Service
public class GitLabSyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(GitLabSyncExecutor.class);

    private final GitLabClient gitLabClient;
    private final GitTrackingRepository repository;
    private final GitCommitFileHistoryService fileHistoryService;
    private final ExecutorService executorService;
    private final GitLabConcurrencyLimiter limiter;
    private final SyncRunService syncRunService;

    public GitLabSyncExecutor(GitLabClient gitLabClient,
                              GitTrackingRepository repository,
                              GitCommitFileHistoryService fileHistoryService,
                              @Qualifier("gitLabVirtualThreadExecutor") ExecutorService executorService,
                              GitLabConcurrencyLimiter limiter,
                              SyncRunService syncRunService) {
        this.gitLabClient = gitLabClient;
        this.repository = repository;
        this.fileHistoryService = fileHistoryService;
        this.executorService = executorService;
        this.limiter = limiter;
        this.syncRunService = syncRunService;
    }

    public GitCommitTrackingResult synchronizeDocuments(List<DocumentGitLink> documents) {
        return synchronizeDocuments(documents, null);
    }

    public GitCommitTrackingResult synchronizeDocuments(List<DocumentGitLink> documents,
                                                        Consumer<GitCommitTrackingResult> progressListener) {
        return synchronizeDocuments(documents, null, null, progressListener);
    }

    public GitCommitTrackingResult synchronizeDocuments(List<DocumentGitLink> documents,
                                                        Long runId,
                                                        String dictionaryIdentifier,
                                                        Consumer<GitCommitTrackingResult> progressListener) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (documents == null || documents.isEmpty()) {
            return result;
        }
        result.setProjectsTotal(documents.size());

        log.info("GitLab synchronization started: documents={}", documents.size());

        CompletionService<ProjectResult> completionService =
                new ExecutorCompletionService<ProjectResult>(executorService);
        List<Future<ProjectResult>> futures = new ArrayList<Future<ProjectResult>>();
        for (DocumentGitLink document : documents) {
            Future<ProjectResult> future = completionService.submit(new Callable<ProjectResult>() {
                @Override
                public ProjectResult call() {
                    try {
                        GitCommitTrackingResult documentResult = limiter.withProjectPermit(
                                new Callable<GitCommitTrackingResult>() {
                                    @Override
                                    public GitCommitTrackingResult call() {
                                        return synchronizeDocument(document, runId, dictionaryIdentifier);
                                    }
                                });
                        return ProjectResult.success(document, documentResult);
                    } catch (RuntimeException e) {
                        return ProjectResult.failure(document, e);
                    }
                }
            });
            futures.add(future);
        }

        for (int index = 0; index < futures.size(); index++) {
            try {
                ProjectResult projectResult = completionService.take().get();
                if (projectResult.error == null) {
                    result.merge(projectResult.result);
                } else {
                    result.addError(new GitSyncError(
                            SyncErrorStage.GITLAB_COMMITS,
                            projectResult.document.getId(),
                            projectResult.document.getProjectPath(),
                            null,
                            null,
                            projectResult.error.getMessage(),
                            projectResult.error
                    ));
                    log.warn("GitLab project task failed: documentOid={}, project={}, error={}",
                            projectResult.document.getDocumentOid(),
                            projectResult.document.getProjectPath(),
                            projectResult.error.getMessage());
                    log.debug("GitLab project task failure details", projectResult.error);
                }
                result.incrementProjectsChecked();
                notifyProgress(progressListener, result);
            } catch (InterruptedException e) {
                cancel(futures);
                Thread.currentThread().interrupt();
                throw new GitSyncException("Interrupted while waiting for GitLab project synchronization", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                result.addError(new GitSyncError(SyncErrorStage.GITLAB_COMMITS, null, null, null, null,
                        cause.getMessage(), cause));
                log.warn("GitLab project task failed before result was returned: error={}", cause.getMessage());
                log.debug("GitLab project task failure details", cause);
                result.incrementProjectsChecked();
                notifyProgress(progressListener, result);
            }
        }

        return result;
    }

    private GitCommitTrackingResult synchronizeDocument(DocumentGitLink document,
                                                        Long runId,
                                                        String dictionaryIdentifier) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier, document,
                null, null, "GitLab-проект: начало синхронизации", documentDetails(document));

        try {
            String latestKnownCommitSha = repository.findLatestStoredCommitSha(document.getId());
            List<GitLabCommit> documentCommits = gitLabClient.fetchCommitsUntilKnown(document, latestKnownCommitSha);
            int insertedCommits = limiter.withDbWritePermit(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return Integer.valueOf(repository.saveCommits(document.getId(), documentCommits));
                }
            });

            addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier, document,
                    null, null, "GitLab-коммиты загружены",
                    commitFetchDetails(latestKnownCommitSha, documentCommits.size(), insertedCommits));

            GitFileHistorySyncResult fileHistoryResult = fileHistoryService.synchronizeDocumentFiles(
                    document, runId, dictionaryIdentifier
            );
            if (insertedCommits > 0 || fileHistoryResult.getProcessedFiles() > 0) {
                result.incrementProjectsProcessed();
            }
            result.addCommitsProcessed(insertedCommits);
            result.addFilesProcessed(fileHistoryResult.getProcessedFiles());
            result.addErrors(fileHistoryResult.getErrors());

            if (fileHistoryResult.hasErrors()) {
                markSyncErrorSafely(document, fileHistoryResult.summary());
            } else {
                limiter.withDbWritePermit(new Runnable() {
                    @Override
                    public void run() {
                        repository.markSyncSuccess(document.getId());
                    }
                });
            }
            addLog(runId, fileHistoryResult.hasErrors() ? SyncRunLogLevel.WARN : SyncRunLogLevel.INFO,
                    SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier, document, null, null,
                    "GitLab-проект: синхронизация завершена",
                    projectResultDetails(insertedCommits, fileHistoryResult));
        } catch (RuntimeException e) {
            markSyncErrorSafely(document, e.getMessage());
            result.addError(new GitSyncError(
                    SyncErrorStage.GITLAB_COMMITS,
                    document.getId(),
                    document.getProjectPath(),
                    null,
                    null,
                    e.getMessage(),
                    e
            ));
            log.warn("Cannot synchronize GitLab commits: documentOid={}, project={}, ref={}, error={}",
                    document.getDocumentOid(), document.getProjectPath(), document.getTreeRef(), e.getMessage());
            log.debug("GitLab synchronization error details", e);
            addLog(runId, SyncRunLogLevel.ERROR, SyncErrorStage.GITLAB_COMMITS, dictionaryIdentifier, document,
                    null, null, "GitLab-проект: ошибка синхронизации", e, documentDetails(document));
        }

        return result;
    }

    private void notifyProgress(Consumer<GitCommitTrackingResult> progressListener, GitCommitTrackingResult result) {
        if (progressListener != null) {
            progressListener.accept(result);
        }
    }

    private void markSyncErrorSafely(DocumentGitLink document, String error) {
        try {
            limiter.withDbWritePermit(new Runnable() {
                @Override
                public void run() {
                    repository.markSyncError(document.getId(), error);
                }
            });
        } catch (RuntimeException dbError) {
            log.warn("Cannot mark GitLab document sync error: documentOid={}, project={}, error={}",
                    document.getDocumentOid(), document.getProjectPath(), dbError.getMessage());
            log.debug("GitLab sync-error marker failure details", dbError);
        }
    }

    private void cancel(List<Future<ProjectResult>> futures) {
        for (Future<ProjectResult> future : futures) {
            future.cancel(true);
        }
    }

    private void addLog(Long runId,
                        SyncRunLogLevel level,
                        SyncErrorStage stage,
                        String dictionaryIdentifier,
                        DocumentGitLink document,
                        String commitSha,
                        String filePath,
                        String message,
                        Map<String, Object> details) {
        addLog(runId, level, stage, dictionaryIdentifier, document, commitSha, filePath, message, null, details);
    }

    private void addLog(Long runId,
                        SyncRunLogLevel level,
                        SyncErrorStage stage,
                        String dictionaryIdentifier,
                        DocumentGitLink document,
                        String commitSha,
                        String filePath,
                        String message,
                        Throwable throwable,
                        Map<String, Object> details) {
        if (runId == null) {
            return;
        }
        syncRunService.safeAddLog(runId.longValue(), level, stage, dictionaryIdentifier,
                document == null ? null : Long.valueOf(document.getId()),
                document == null ? null : document.getProjectPath(),
                commitSha,
                filePath,
                message,
                throwable,
                details);
    }

    private Map<String, Object> documentDetails(DocumentGitLink document) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("documentOid", document.getDocumentOid());
        details.put("recordKey", document.getRecordKey());
        details.put("treeRef", document.getTreeRef());
        return details;
    }

    private Map<String, Object> commitFetchDetails(String latestKnownCommitSha, int fetched, int inserted) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("latestKnownCommitSha", latestKnownCommitSha);
        details.put("fetchedCommits", Integer.valueOf(fetched));
        details.put("insertedCommits", Integer.valueOf(inserted));
        return details;
    }

    private Map<String, Object> projectResultDetails(int insertedCommits, GitFileHistorySyncResult fileHistoryResult) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("insertedCommits", Integer.valueOf(insertedCommits));
        details.put("processedFileHistoryCommits", Integer.valueOf(fileHistoryResult.getProcessedCommits()));
        details.put("processedFiles", Integer.valueOf(fileHistoryResult.getProcessedFiles()));
        details.put("failedCommits", Integer.valueOf(fileHistoryResult.getFailedCommits()));
        details.put("failedFiles", Integer.valueOf(fileHistoryResult.getFailedFiles()));
        return details;
    }

    private static class ProjectResult {
        private final DocumentGitLink document;
        private final GitCommitTrackingResult result;
        private final RuntimeException error;

        private ProjectResult(DocumentGitLink document, GitCommitTrackingResult result, RuntimeException error) {
            this.document = document;
            this.result = result;
            this.error = error;
        }

        private static ProjectResult success(DocumentGitLink document, GitCommitTrackingResult result) {
            return new ProjectResult(document, result, null);
        }

        private static ProjectResult failure(DocumentGitLink document, RuntimeException error) {
            return new ProjectResult(document, null, error);
        }
    }
}
