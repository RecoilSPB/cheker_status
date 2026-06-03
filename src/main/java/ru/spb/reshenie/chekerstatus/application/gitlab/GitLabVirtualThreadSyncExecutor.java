package ru.spb.reshenie.chekerstatus.application.gitlab;

import ru.spb.reshenie.chekerstatus.domain.gitlab.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitFileHistorySyncResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommit;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitSyncError;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitLabClientPort;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitLabConcurrencyPort;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitLabSyncSettings;
import ru.spb.reshenie.chekerstatus.application.gitlab.port.GitTrackingRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

@Service
public class GitLabVirtualThreadSyncExecutor {

    private static final Logger log = LoggerFactory.getLogger(GitLabVirtualThreadSyncExecutor.class);

    private final GitLabSyncSettings settings;
    private final GitLabClientPort gitLabClient;
    private final GitTrackingRepositoryPort repository;
    private final GitCommitFileHistoryService fileHistoryService;
    private final ExecutorService executorService;
    private final GitLabConcurrencyPort limiter;

    public GitLabVirtualThreadSyncExecutor(GitLabSyncSettings settings,
                                           GitLabClientPort gitLabClient,
                                           GitTrackingRepositoryPort repository,
                                           GitCommitFileHistoryService fileHistoryService,
                                           @Qualifier("gitLabVirtualThreadExecutor") ExecutorService executorService,
                                           GitLabConcurrencyPort limiter) {
        this.settings = settings;
        this.gitLabClient = gitLabClient;
        this.repository = repository;
        this.fileHistoryService = fileHistoryService;
        this.executorService = executorService;
        this.limiter = limiter;
    }

    public GitCommitTrackingResult synchronizeDocuments(List<DocumentGitLink> documents) {
        return synchronizeDocuments(documents, null);
    }

    public GitCommitTrackingResult synchronizeDocuments(List<DocumentGitLink> documents,
                                                        Consumer<GitCommitTrackingResult> progressListener) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (documents == null || documents.isEmpty()) {
            return result;
        }

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
        GitCommitTrackingResult result = new GitCommitTrackingResult();
        if (documents == null || documents.isEmpty()) {
            return result;
        }

        log.info("GitLab synchronization started in sequential fallback: documents={}", documents.size());
        for (DocumentGitLink document : documents) {
            result.merge(synchronizeDocument(document));
            notifyProgress(progressListener, result);
        }
        return result;
    }

    private GitCommitTrackingResult synchronizeDocument(DocumentGitLink document) {
        GitCommitTrackingResult result = new GitCommitTrackingResult();

        try {
            String latestKnownCommitSha = repository.findLatestStoredCommitSha(document.getId());
            List<GitLabCommit> documentCommits = gitLabClient.fetchCommitsUntilKnown(document, latestKnownCommitSha);
            int insertedCommits = limiter.withDbWritePermit(new Callable<Integer>() {
                @Override
                public Integer call() {
                    return Integer.valueOf(repository.saveCommits(document.getId(), documentCommits));
                }
            });

            GitFileHistorySyncResult fileHistoryResult = fileHistoryService.synchronizeDocumentFiles(document);
            if (insertedCommits > 0
                    || fileHistoryResult.getProcessedCommits() > 0
                    || fileHistoryResult.getProcessedFiles() > 0
                    || fileHistoryResult.hasErrors()) {
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
        } catch (RuntimeException e) {
            result.incrementProjectsProcessed();
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

    private static class ProjectFuture {
        private final DocumentGitLink document;
        private final Future<GitCommitTrackingResult> future;

        private ProjectFuture(DocumentGitLink document, Future<GitCommitTrackingResult> future) {
            this.document = document;
            this.future = future;
        }
    }
}
