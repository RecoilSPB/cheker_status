package ru.spb.reshenie.chekerstatus.gitlab.service;

import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitContentUtils;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentRequestPlan;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileHistorySyncResult;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommitDetails;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommitDiff;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabFileContentResult;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitSyncError;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitLabCommit;
import ru.spb.reshenie.chekerstatus.gitlab.diff.TextContentDecoder;
import ru.spb.reshenie.chekerstatus.gitlab.storage.GitFileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
import ru.spb.reshenie.chekerstatus.gitlab.client.GitLabClient;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitCommitFileRepository;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitTrackingRepository;
import ru.spb.reshenie.chekerstatus.gitlab.sync.GitLabConcurrencyLimiter;
import ru.spb.reshenie.chekerstatus.gitlab.sync.GitSyncException;
import ru.spb.reshenie.chekerstatus.sync.service.SyncRunService;
import ru.spb.reshenie.chekerstatus.sync.model.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunLogLevel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class GitCommitFileHistoryService {

    static final String STATUS_SUCCESS = "SUCCESS";
    static final String STATUS_FAILED = "FAILED";
    static final String STATUS_SKIPPED_DISABLED = "SKIPPED_DISABLED";
    static final String STATUS_SKIPPED_TOO_LARGE = "SKIPPED_TOO_LARGE";
    static final String STATUS_SKIPPED_BINARY = "SKIPPED_BINARY";

    private static final Logger log = LoggerFactory.getLogger(GitCommitFileHistoryService.class);

    private final GitLabProperties settings;
    private final GitLabClient gitLabClient;
    private final GitTrackingRepository gitTrackingRepository;
    private final GitCommitFileRepository fileRepository;
    private final ExecutorService executorService;
    private final GitLabConcurrencyLimiter limiter;
    private final GitFileStorage gitFileStorage;
    private final GitFileDiffProcessor gitFileDiffProcessor;
    private final SyncRunService syncRunService;

    public GitCommitFileHistoryService(GitLabProperties settings,
                                       GitLabClient gitLabClient,
                                       GitTrackingRepository gitTrackingRepository,
                                       GitCommitFileRepository fileRepository,
                                       @Qualifier("gitLabVirtualThreadExecutor") ExecutorService executorService,
                                       GitLabConcurrencyLimiter limiter,
                                       GitFileStorage gitFileStorage,
                                       GitFileDiffProcessor gitFileDiffProcessor,
                                       SyncRunService syncRunService) {
        this.settings = settings;
        this.gitLabClient = gitLabClient;
        this.gitTrackingRepository = gitTrackingRepository;
        this.fileRepository = fileRepository;
        this.executorService = executorService;
        this.limiter = limiter;
        this.gitFileStorage = gitFileStorage;
        this.gitFileDiffProcessor = gitFileDiffProcessor;
        this.syncRunService = syncRunService;
    }

    public GitFileHistorySyncResult synchronizeDocumentFiles(DocumentGitLink document) {
        return synchronizeDocumentFiles(document, null, null);
    }

    public GitFileHistorySyncResult synchronizeDocumentFiles(DocumentGitLink document,
                                                             Long runId,
                                                             String dictionaryIdentifier) {
        if (!settings.isFileHistoryEnabled()) {
            log.info("GitLab file history tracking is disabled");
            addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier,
                    document, null, null, "GitLab file-history отключен", null);
            return GitFileHistorySyncResult.disabled();
        }
        List<StoredGitLabCommit> commits = gitTrackingRepository.findCommitsPendingFileHistory(document.getId());
        addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier,
                document, null, null, "GitLab file-history: старт", pendingCommitDetails(commits.size()));
        AtomicInteger processedCommits = new AtomicInteger();
        AtomicInteger processedFiles = new AtomicInteger();
        AtomicInteger failedCommits = new AtomicInteger();
        AtomicInteger failedFiles = new AtomicInteger();
        AtomicReference<String> firstError = new AtomicReference<String>();
        ConcurrentLinkedQueue<GitSyncError> errors = new ConcurrentLinkedQueue<GitSyncError>();

        List<Future<Void>> futures = new ArrayList<Future<Void>>();
        for (StoredGitLabCommit commit : commits) {
            futures.add(executorService.submit(new Callable<Void>() {
                @Override
                public Void call() {
                    limiter.withCommitPermit(new Callable<Void>() {
                        @Override
                        public Void call() {
                            synchronizeCommitFiles(
                                    document,
                                    commit,
                                    runId,
                                    dictionaryIdentifier,
                                    processedCommits,
                                    processedFiles,
                                    failedCommits,
                                    failedFiles,
                                    firstError,
                                    errors
                            );
                            return null;
                        }
                    });
                    return null;
                }
            }));
        }
        waitForCommitTasks(document, futures, failedCommits, firstError, errors);

        GitFileHistorySyncResult result = new GitFileHistorySyncResult(
                processedCommits.get(),
                processedFiles.get(),
                failedCommits.get(),
                failedFiles.get(),
                firstError.get(),
                new ArrayList<GitSyncError>(errors)
        );
        log.info("GitLab file history tracking finished: documentOid={}, {}",
                document.getDocumentOid(), result.summary());
        addLog(runId, result.hasErrors() ? SyncRunLogLevel.WARN : SyncRunLogLevel.INFO,
                SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier, document, null, null,
                "GitLab file-history: завершено", fileHistoryDetails(result));
        return result;
    }

    private void synchronizeCommitFiles(DocumentGitLink document,
                                        StoredGitLabCommit commit,
                                        Long runId,
                                        String dictionaryIdentifier,
                                        AtomicInteger processedCommits,
                                        AtomicInteger processedFiles,
                                        AtomicInteger failedCommits,
                                        AtomicInteger failedFiles,
                                        AtomicReference<String> firstError,
                                        ConcurrentLinkedQueue<GitSyncError> errors) {
        try {
            addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier,
                    document, commit.getCommitSha(), null, "GitLab commit file-history: старт", null);
            GitLabCommitDetails details = gitLabClient.fetchCommitDetails(document, commit.getCommitSha());
            String parentSha = details.getFirstParentSha();
            List<GitLabCommitDiff> diffs = gitLabClient.fetchCommitDiff(document, commit.getCommitSha());
            addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier,
                    document, commit.getCommitSha(), null, "GitLab commit diff загружен", diffDetails(diffs.size()));
            List<Future<Void>> fileFutures = new ArrayList<Future<Void>>();
            AtomicInteger commitFailedFiles = new AtomicInteger();
            AtomicReference<String> commitFirstError = new AtomicReference<String>();
            int fileLimit = settings.getMaxFilesPerCommit();
            int fileIndex = 0;
            for (GitLabCommitDiff diff : diffs) {
                if (fileLimit > 0 && fileIndex >= fileLimit) {
                    log.warn("GitLab commit file limit reached: documentOid={}, commit={}, limit={}",
                            document.getDocumentOid(), commit.getCommitSha(), fileLimit);
                    break;
                }
                fileIndex++;
                fileFutures.add(executorService.submit(new Callable<Void>() {
                    @Override
                    public Void call() {
                        limiter.withFilePermit(new Callable<Void>() {
                            @Override
                            public Void call() {
                                synchronizeFileChange(
                                        document,
                                        commit,
                                        parentSha,
                                        diff,
                                        runId,
                                        dictionaryIdentifier,
                                        processedFiles,
                                        failedFiles,
                                        commitFailedFiles,
                                        firstError,
                                        commitFirstError,
                                        errors
                                );
                                return null;
                            }
                        });
                        return null;
                    }
                }));
            }
            waitForFileTasks(document, commit, fileFutures, failedFiles, commitFailedFiles,
                    firstError, commitFirstError, errors);
            if (commitFailedFiles.get() > 0) {
                markCommitFileHistoryFailed(commit, commitFirstError.get());
            } else {
                markCommitFileHistorySuccess(commit);
                processedCommits.incrementAndGet();
                addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier,
                        document, commit.getCommitSha(), null, "GitLab commit file-history: успешно", null);
            }
        } catch (RuntimeException e) {
            failedCommits.incrementAndGet();
            firstError.compareAndSet(null, e.getMessage());
            errors.add(new GitSyncError(
                    SyncErrorStage.GITLAB_COMMIT_DIFF,
                    document.getId(),
                    document.getProjectPath(),
                    commit.getCommitSha(),
                    null,
                    e.getMessage(),
                    e
            ));
            log.warn("Cannot synchronize GitLab commit files: documentOid={}, commit={}, error={}",
                    document.getDocumentOid(), commit.getCommitSha(), e.getMessage());
            log.debug("GitLab commit file synchronization error details", e);
            markCommitFileHistoryFailed(commit, e.getMessage());
            addLog(runId, SyncRunLogLevel.ERROR, SyncErrorStage.GITLAB_COMMIT_DIFF, dictionaryIdentifier,
                    document, commit.getCommitSha(), null, "GitLab commit file-history: ошибка", e, null);
        }
    }

    private void synchronizeFileChange(DocumentGitLink document,
                                       StoredGitLabCommit commit,
                                       String parentSha,
                                       GitLabCommitDiff diff,
                                       Long runId,
                                       String dictionaryIdentifier,
                                       AtomicInteger processedFiles,
                                       AtomicInteger failedFiles,
                                       AtomicInteger commitFailedFiles,
                                       AtomicReference<String> firstError,
                                       AtomicReference<String> commitFirstError,
                                       ConcurrentLinkedQueue<GitSyncError> errors) {
        try {
            GitCommitFileChange change = buildFileChange(document, commit, parentSha, diff);
            var savedChange = limiter.withDbWritePermit(new Callable<ru.spb.reshenie.chekerstatus.gitlab.model.SavedGitCommitFileChange>() {
                @Override
                public ru.spb.reshenie.chekerstatus.gitlab.model.SavedGitCommitFileChange call() {
                    return fileRepository.saveFileChangeRecord(change);
                }
            });
            if (savedChange == null) {
                throw new IllegalStateException("Cannot save GitLab file change row");
            }
            boolean inserted = savedChange != null && savedChange.isInserted();
            GitFileDiffProcessResult diffResult = gitFileDiffProcessor.process(savedChange.getId(), change);
            if (diffResult.hasError()) {
                addLog(runId, SyncRunLogLevel.WARN, SyncErrorStage.GITLAB_FILE_CONTENT, dictionaryIdentifier,
                        document, commit.getCommitSha(), change.getFilePath(),
                        "GitLab file-history: structured diff fallback/error", structuredDiffDetails(diffResult));
            }
            if (inserted && !STATUS_FAILED.equals(change.getContentFetchStatus())) {
                processedFiles.incrementAndGet();
                if (STATUS_SUCCESS.equals(change.getContentFetchStatus())) {
                    addLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.GITLAB_FILE_CONTENT, dictionaryIdentifier,
                            document, commit.getCommitSha(), change.getFilePath(),
                            "GitLab file-history: файл сохранен", fileChangeDetails(change));
                }
            }
            if (STATUS_FAILED.equals(change.getContentFetchStatus())) {
                failedFiles.incrementAndGet();
                commitFailedFiles.incrementAndGet();
                firstError.compareAndSet(null, change.getContentFetchError());
                commitFirstError.compareAndSet(null, change.getContentFetchError());
                errors.add(new GitSyncError(
                        SyncErrorStage.GITLAB_FILE_CONTENT,
                        document.getId(),
                        document.getProjectPath(),
                        commit.getCommitSha(),
                        change.getFilePath(),
                        change.getContentFetchError(),
                        null
                ));
                addLog(runId, SyncRunLogLevel.ERROR, SyncErrorStage.GITLAB_FILE_CONTENT, dictionaryIdentifier,
                        document, commit.getCommitSha(), change.getFilePath(),
                        "GitLab file-history: ошибка загрузки файла", null, fileChangeDetails(change));
            } else if (inserted && !STATUS_SUCCESS.equals(change.getContentFetchStatus())) {
                addLog(runId, SyncRunLogLevel.WARN, SyncErrorStage.GITLAB_FILE_CONTENT, dictionaryIdentifier,
                        document, commit.getCommitSha(), change.getFilePath(),
                        "GitLab file-history: файл сохранен без содержимого", fileChangeDetails(change));
            }
        } catch (RuntimeException e) {
            failedFiles.incrementAndGet();
            commitFailedFiles.incrementAndGet();
            firstError.compareAndSet(null, e.getMessage());
            commitFirstError.compareAndSet(null, e.getMessage());
            errors.add(new GitSyncError(
                    SyncErrorStage.GITLAB_FILE_CONTENT,
                    document.getId(),
                    document.getProjectPath(),
                    commit.getCommitSha(),
                    diff.filePath(),
                    e.getMessage(),
                    e
            ));
            log.warn("Cannot synchronize GitLab commit file: documentOid={}, commit={}, file={}, error={}",
                    document.getDocumentOid(), commit.getCommitSha(), diff.filePath(), e.getMessage());
            log.debug("GitLab commit file synchronization error details", e);
            addLog(runId, SyncRunLogLevel.ERROR, SyncErrorStage.GITLAB_FILE_CONTENT, dictionaryIdentifier,
                    document, commit.getCommitSha(), diff.filePath(), "GitLab file-history: ошибка файла", e, null);
        }
    }

    private void markCommitFileHistorySuccess(StoredGitLabCommit commit) {
        limiter.withDbWritePermit(new Runnable() {
            @Override
            public void run() {
                gitTrackingRepository.markCommitFileHistorySuccess(commit.getId());
            }
        });
    }

    private void markCommitFileHistoryFailed(StoredGitLabCommit commit, String error) {
        limiter.withDbWritePermit(new Runnable() {
            @Override
            public void run() {
                gitTrackingRepository.markCommitFileHistoryFailed(commit.getId(), error);
            }
        });
    }

    private void waitForCommitTasks(DocumentGitLink document,
                                    List<Future<Void>> futures,
                                    AtomicInteger failedCommits,
                                    AtomicReference<String> firstError,
                                    ConcurrentLinkedQueue<GitSyncError> errors) {
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                cancel(futures);
                Thread.currentThread().interrupt();
                throw new GitSyncException("Interrupted while waiting for GitLab commit file history tasks", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                failedCommits.incrementAndGet();
                firstError.compareAndSet(null, cause.getMessage());
                errors.add(new GitSyncError(
                        SyncErrorStage.GITLAB_COMMIT_DIFF,
                        document.getId(),
                        document.getProjectPath(),
                        null,
                        null,
                        cause.getMessage(),
                        cause
                ));
            }
        }
    }

    private void waitForFileTasks(DocumentGitLink document,
                                  StoredGitLabCommit commit,
                                  List<Future<Void>> futures,
                                  AtomicInteger failedFiles,
                                  AtomicInteger commitFailedFiles,
                                  AtomicReference<String> firstError,
                                  AtomicReference<String> commitFirstError,
                                  ConcurrentLinkedQueue<GitSyncError> errors) {
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (InterruptedException e) {
                cancel(futures);
                Thread.currentThread().interrupt();
                throw new GitSyncException("Interrupted while waiting for GitLab file content tasks", e);
            } catch (ExecutionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                failedFiles.incrementAndGet();
                commitFailedFiles.incrementAndGet();
                firstError.compareAndSet(null, cause.getMessage());
                commitFirstError.compareAndSet(null, cause.getMessage());
                errors.add(new GitSyncError(
                        SyncErrorStage.GITLAB_FILE_CONTENT,
                        document.getId(),
                        document.getProjectPath(),
                        commit.getCommitSha(),
                        null,
                        cause.getMessage(),
                        cause
                ));
            }
        }
    }

    private void cancel(List<Future<Void>> futures) {
        for (Future<Void> future : futures) {
            future.cancel(true);
        }
    }

    GitCommitFileChange buildFileChange(DocumentGitLink document,
                                        StoredGitLabCommit commit,
                                        String parentSha,
                                        GitLabCommitDiff diff) {
        String oldPath = valueOrFallback(diff.getOldPath(), diff.filePath());
        String newPath = valueOrFallback(diff.getNewPath(), diff.filePath());
        String filePath = valueOrFallback(diff.filePath(), newPath);
        ContentFetchCollector collector = new ContentFetchCollector(diff.isTooLarge());

        if (!settings.isFetchFileContent()) {
            collector.skipDisabled();
        } else {
            GitFileContentRequestPlan plan = contentRequestPlan(diff, commit.getCommitSha(), parentSha);
            if (plan.isFetchAfter()) {
                collector.after(fetchContent(document, plan.getAfterPath(), plan.getAfterRef(), "content_after"));
            }
            if (plan.isFetchBefore()) {
                collector.before(fetchContent(document, plan.getBeforePath(), plan.getBeforeRef(), "content_before"));
            }
        }

        return new GitCommitFileChange(
                document.getId(),
                commit.getId(),
                commit.getCommitSha(),
                parentSha,
                oldPath,
                newPath,
                filePath,
                diff.changeType(),
                diff.isRenamedFile(),
                diff.isNewFile(),
                diff.isDeletedFile(),
                diff.getDiff(),
                collector.isDiffTooLarge(),
                collector.getContentAfter(),
                collector.getContentBefore(),
                collector.status(),
                collector.error(),
                commit.getCommittedDate()
        );
    }

    GitFileContentRequestPlan contentRequestPlan(GitLabCommitDiff diff, String commitSha, String parentSha) {
        boolean fetchAfter = !diff.isDeletedFile() && diff.getNewPath() != null;
        boolean fetchBefore = parentSha != null && !diff.isNewFile() && diff.getOldPath() != null;
        return new GitFileContentRequestPlan(
                fetchAfter,
                diff.getNewPath(),
                commitSha,
                fetchBefore,
                diff.getOldPath(),
                parentSha
        );
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

    private Map<String, Object> pendingCommitDetails(int commits) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("pendingCommits", Integer.valueOf(commits));
        return details;
    }

    private Map<String, Object> diffDetails(int files) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("files", Integer.valueOf(files));
        return details;
    }

    private Map<String, Object> fileHistoryDetails(GitFileHistorySyncResult result) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("processedCommits", Integer.valueOf(result.getProcessedCommits()));
        details.put("processedFiles", Integer.valueOf(result.getProcessedFiles()));
        details.put("failedCommits", Integer.valueOf(result.getFailedCommits()));
        details.put("failedFiles", Integer.valueOf(result.getFailedFiles()));
        details.put("firstError", result.getFirstError());
        return details;
    }

    private Map<String, Object> fileChangeDetails(GitCommitFileChange change) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("changeType", change.getChangeType());
        details.put("renamed", Boolean.valueOf(change.isRenamedFile()));
        details.put("newFile", Boolean.valueOf(change.isNewFile()));
        details.put("deletedFile", Boolean.valueOf(change.isDeletedFile()));
        details.put("diffTooLarge", Boolean.valueOf(change.isDiffTooLarge()));
        details.put("contentFetchStatus", change.getContentFetchStatus());
        details.put("contentFetchError", change.getContentFetchError());
        return details;
    }

    private Map<String, Object> structuredDiffDetails(GitFileDiffProcessResult result) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("status", result.getStatus());
        details.put("diffType", result.getDiffType());
        details.put("formatFamily", result.getFormatFamily());
        details.put("error", result.getError());
        return details;
    }

    private ContentFetchOutcome fetchContent(DocumentGitLink document,
                                             String filePath,
                                             String ref,
                                             String label) {
        try {
            GitLabFileContentResult result = gitLabClient.fetchRawFile(document, filePath, ref);
            if (!result.isFound()) {
                return ContentFetchOutcome.failed(label + " not found: file=" + filePath + ", ref=" + ref);
            }

            byte[] bytes = result.getContent();
            String sha256 = GitContentUtils.sha256(bytes);
            long size = bytes == null ? 0L : bytes.length;
            if (gitFileStorage.storesExternally()) {
                String objectKey = gitFileStorage.store(document, filePath, ref, bytes, sha256);
                return ContentFetchOutcome.success(new GitFileContentSnapshot(null, objectKey, sha256, size, bytes));
            }

            GitFileContentSnapshot metadataOnly = new GitFileContentSnapshot(null, null, sha256, size, bytes);

            if (size > settings.getMaxFileSizeBytes()) {
                return ContentFetchOutcome.skippedTooLarge(metadataOnly,
                        label + " is too large: file=" + filePath + ", ref=" + ref + ", size=" + size);
            }
            if (GitContentUtils.looksBinary(bytes)) {
                return ContentFetchOutcome.skippedBinary(metadataOnly,
                        label + " looks binary: file=" + filePath + ", ref=" + ref);
            }

            String content = TextContentDecoder.decode(bytes, filePath);
            return ContentFetchOutcome.success(new GitFileContentSnapshot(content, null, sha256, size, bytes));
        } catch (RuntimeException e) {
            return ContentFetchOutcome.failed(label + " fetch/store failed: file=" + filePath
                    + ", ref=" + ref + ", error=" + e.getMessage());
        }
    }

    private String valueOrFallback(String value, String fallback) {
        return value == null ? fallback : value;
    }

    private static class ContentFetchOutcome {
        private final String status;
        private final GitFileContentSnapshot snapshot;
        private final String error;

        private ContentFetchOutcome(String status, GitFileContentSnapshot snapshot, String error) {
            this.status = status;
            this.snapshot = snapshot;
            this.error = error;
        }

        static ContentFetchOutcome success(GitFileContentSnapshot snapshot) {
            return new ContentFetchOutcome(STATUS_SUCCESS, snapshot, null);
        }

        static ContentFetchOutcome skippedTooLarge(GitFileContentSnapshot snapshot, String error) {
            return new ContentFetchOutcome(STATUS_SKIPPED_TOO_LARGE, snapshot, error);
        }

        static ContentFetchOutcome skippedBinary(GitFileContentSnapshot snapshot, String error) {
            return new ContentFetchOutcome(STATUS_SKIPPED_BINARY, snapshot, error);
        }

        static ContentFetchOutcome failed(String error) {
            return new ContentFetchOutcome(STATUS_FAILED, null, error);
        }
    }

    private static class ContentFetchCollector {
        private GitFileContentSnapshot contentAfter;
        private GitFileContentSnapshot contentBefore;
        private String status = STATUS_SUCCESS;
        private StringBuilder errors;
        private boolean diffTooLarge;

        ContentFetchCollector(boolean diffTooLarge) {
            this.diffTooLarge = diffTooLarge;
        }

        void skipDisabled() {
            status = STATUS_SKIPPED_DISABLED;
        }

        void after(ContentFetchOutcome outcome) {
            contentAfter = outcome.snapshot;
            apply(outcome);
        }

        void before(ContentFetchOutcome outcome) {
            contentBefore = outcome.snapshot;
            apply(outcome);
        }

        private void apply(ContentFetchOutcome outcome) {
            if (STATUS_FAILED.equals(outcome.status)) {
                status = STATUS_FAILED;
            } else if (!STATUS_FAILED.equals(status) && STATUS_SKIPPED_BINARY.equals(outcome.status)) {
                status = STATUS_SKIPPED_BINARY;
            } else if (!STATUS_FAILED.equals(status)
                    && !STATUS_SKIPPED_BINARY.equals(status)
                    && STATUS_SKIPPED_TOO_LARGE.equals(outcome.status)) {
                status = STATUS_SKIPPED_TOO_LARGE;
            }

            if (STATUS_SKIPPED_TOO_LARGE.equals(outcome.status)) {
                diffTooLarge = true;
            }
            if (outcome.error != null) {
                if (errors == null) {
                    errors = new StringBuilder();
                } else {
                    errors.append("; ");
                }
                errors.append(outcome.error);
            }
        }

        GitFileContentSnapshot getContentAfter() {
            return contentAfter;
        }

        GitFileContentSnapshot getContentBefore() {
            return contentBefore;
        }

        boolean isDiffTooLarge() {
            return diffTooLarge;
        }

        String status() {
            return status;
        }

        String error() {
            if (errors == null) {
                return null;
            }
            String value = errors.toString();
            return value.length() > 4000 ? value.substring(0, 4000) : value;
        }
    }
}
