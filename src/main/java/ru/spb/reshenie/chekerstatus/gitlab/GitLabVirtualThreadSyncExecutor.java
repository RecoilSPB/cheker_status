package ru.spb.reshenie.chekerstatus.gitlab;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.GitLabProperties;
import ru.spb.reshenie.chekerstatus.sync.SyncErrorStage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

@Service
public class GitLabVirtualThreadSyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(GitLabVirtualThreadSyncExecutor.class);

    private final GitLabProperties properties;
    private final GitLabClient gitLabClient;
    private final GitTrackingRepository repository;
    private final GitCommitFileHistoryService fileHistoryService;
    private final ExecutorService executorService;
    private final GitLabConcurrencyLimiter limiter;

    public GitLabVirtualThreadSyncExecutor(GitLabProperties properties,
                                           GitLabClient gitLabClient,
                                           GitTrackingRepository repository,
                                           GitCommitFileHistoryService fileHistoryService,
                                           @Qualifier("gitLabVirtualThreadExecutor") ExecutorService executorService,
                                           GitLabConcurrencyLimiter limiter) {
        this.properties = properties;
        this.gitLabClient = gitLabClient;
        this.repository = repository;
        this.fileHistoryService = fileHistoryService;
        this.executorService = executorService;
        this.limiter = limiter;
    }

    public GitCommitTrackingResult synchronizeDocuments(List<DocumentGitLink> documents) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (documents == null || documents.isEmpty()) {
            return result;
        }

        log.info("GitLab synchronization started: documents={}, virtualThreads={}, limits projects={}, commits={}, files={}, dbWrites={}",
                documents.size(),
                properties.isVirtualThreadsEnabled(),
                properties.getMaxConcurrentProjects(),
                properties.getMaxConcurrentCommits(),
                properties.getMaxConcurrentFiles(),
                properties.getMaxConcurrentDbWrites());

        List<ProjectFuture> futures = new ArrayList<ProjectFuture>();
        for (DocumentGitLink document : documents) {
            Future<GitCommitTrackingResult> future = executorService.submit(new Callable<GitCommitTrackingResult>() {
                @Override
                public GitCommitTrackingResult call() {
                    return limiter.withProjectPermit(new Callable<GitCommitTrackingResult>() {
                        @Override
                        public GitCommitTrackingResult call() {
                            return synchronizeDocument(document);
                        }
                    });
                }
            });
            futures.add(new ProjectFuture(document, future));
        }

        for (ProjectFuture projectFuture : futures) {
            try {
                result.merge(projectFuture.future.get());
            } catch (InterruptedException e) {
                cancel(futures);
                Thread.currentThread().interrupt();
                throw new GitLabClientException("Interrupted while waiting for GitLab project synchronization", e);
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
            }
        }

        return result;
    }

    public GitCommitTrackingResult synchronizeDocumentsSequentially(List<DocumentGitLink> documents) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (documents == null || documents.isEmpty()) {
            return result;
        }

        log.info("GitLab synchronization started in sequential fallback: documents={}", documents.size());
        for (DocumentGitLink document : documents) {
            result.merge(synchronizeDocument(document));
        }
        return result;
    }

    private GitCommitTrackingResult synchronizeDocument(DocumentGitLink document) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        result.incrementProjectsProcessed();

        try {
            List<GitLabCommit> documentCommits = gitLabClient.fetchAllCommits(document);
            limiter.withDbWritePermit(new Runnable() {
                @Override
                public void run() {
                    repository.saveCommits(document.getId(), documentCommits);
                }
            });

            GitFileHistorySyncResult fileHistoryResult = fileHistoryService.synchronizeDocumentFiles(document);
            result.addCommitsProcessed(documentCommits.size());
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
        }

        return result;
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

    private static class ProjectFuture {
        private final DocumentGitLink document;
        private final Future<GitCommitTrackingResult> future;

        private ProjectFuture(DocumentGitLink document, Future<GitCommitTrackingResult> future) {
            this.document = document;
            this.future = future;
        }
    }
}
