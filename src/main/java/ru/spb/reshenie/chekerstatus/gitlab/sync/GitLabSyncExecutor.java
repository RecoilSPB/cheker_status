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
import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Service
public class GitLabSyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(GitLabSyncExecutor.class);

    private final GitLabProperties settings;
    private final GitLabClient gitLabClient;
    private final GitTrackingRepository repository;
    private final GitCommitFileHistoryService fileHistoryService;
    private final ExecutorService executorService;
    private final GitLabConcurrencyLimiter limiter;
    private final SyncRunService syncRunService;

    public GitLabSyncExecutor(GitLabProperties settings,
                                           GitLabClient gitLabClient,
                                           GitTrackingRepository repository,
                                           GitCommitFileHistoryService fileHistoryService,
                                           @Qualifier("gitLabVirtualThreadExecutor") ExecutorService executorService,
                                           GitLabConcurrencyLimiter limiter,
                                           SyncRunService syncRunService) {
        this.settings = settings;
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

        log.info("GitLab synchronization started: documents={}, virtualThreads={}, limits projects={}, commits={}, files={}, dbWrites={}",
                documents.size(),
                settings.isVirtualThreadsEnabled(),
                settings.getMaxConcurrentProjects(),
                settings.getMaxConcurrentCommits(),
                settings.getMaxConcurrentFiles(),
                settings.getMaxConcurrentDbWrites());

        List<ProjectFuture> futures = new ArrayList<ProjectFuture>();
        for (DocumentGitLink document : documents) {
            Future<GitCommitTrackingResult> future = executorService.submit(new Callable<GitCommitTrackingResult>() {
                @Override
                public GitCommitTrackingResult call() {
                    return limiter.withProjectPermit(new Callable<GitCommitTrackingResult>() {
                        @Override
                        public GitCommitTrackingResult call() {
                            return synchronizeDocument(document, runId, dictionaryIdentifier);
                        }
                    });
                }
            });
            futures.add(new ProjectFuture(document, future));
        }

        for (ProjectFuture projectFuture : futures) {
            try {
                result.merge(projectFuture.future.get());
                result.incrementProjectsChecked();
                notifyProgress(progressListener, result);
            } catch (InterruptedException e) {
                cancel(futures);
                Thread.currentThread().interrupt();
                throw new GitSyncException("Interrupted while waiting for GitLab project synchronization", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                result.addError(new GitSyncError(
                        SyncErrorStage.GITLAB_COMMITS,
                        projectFuture.document.getId(),
                        projectFuture.document.getProjectPath(),
                        null,
                        null,
                        cause.getMessage(),
                        cause
                ));
                log.warn("GitLab project task failed: documentOid={}, project={}, error={}",
                        projectFuture.document.getDocumentOid(),
                        projectFuture.document.getProjectPath(),
                        cause.getMessage());
                log.debug("GitLab project task failure details", cause);
                result.incrementProjectsChecked();
                notifyProgress(progressListener, result);
            }
        }

        return result;
    }

    public GitCommitTrackingResult synchronizeDocumentsSequentially(List<DocumentGitLink> documents) {
        return synchronizeDocumentsSequentially(documents, null);
    }

    public GitCommitTrackingResult synchronizeDocumentsSequentially(List<DocumentGitLink> documents,
                                                                    Consumer<GitCommitTrackingResult> progressListener) {
        return synchronizeDocumentsSequentially(documents, null, null, progressListener);
    }

    public GitCommitTrackingResult synchronizeDocumentsSequentially(List<DocumentGitLink> documents,
                                                                    Long runId,
                                                                    String dictionaryIdentifier,
                                                                    Consumer<GitCommitTrackingResult> progressListener) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (documents == null || documents.isEmpty()) {
            return result;
        }
        result.setProjectsTotal(documents.size());

        log.info("GitLab synchronization started in sequential fallback: documents={}", documents.size());
        for (DocumentGitLink document : documents) {
            result.merge(synchronizeDocument(document, runId, dictionaryIdentifier));
            result.incrementProjectsChecked();
            notifyProgress(progressListener, result);
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

    private void cancel(List<ProjectFuture> futures) {
        for (ProjectFuture future : futures) {
            future.future.cancel(true);
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

    private static class ProjectFuture {
        private final DocumentGitLink document;
        private final Future<GitCommitTrackingResult> future;

        private ProjectFuture(DocumentGitLink document, Future<GitCommitTrackingResult> future) {
            this.document = document;
            this.future = future;
        }
    }
}
