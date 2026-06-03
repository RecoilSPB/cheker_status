package ru.spb.reshenie.chekerstatus.application.gitlab;

import ru.spb.reshenie.chekerstatus.domain.gitlab.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitFileHistorySyncResult;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommit;
import ru.spb.reshenie.chekerstatus.infrastructure.gitlab.GitLabClient;
import ru.spb.reshenie.chekerstatus.infrastructure.gitlab.GitLabClientException;
import ru.spb.reshenie.chekerstatus.infrastructure.gitlab.GitLabConcurrencyLimiter;
import ru.spb.reshenie.chekerstatus.infrastructure.gitlab.GitTrackingRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.infrastructure.config.GitLabProperties;

import java.util.Collections;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitLabVirtualThreadSyncExecutorTest {

    @Test
    void projectErrorDoesNotStopOtherProjects() throws Exception {
        GitLabProperties properties = new GitLabProperties();
        properties.setVirtualThreadsEnabled(true);
        ExecutorService executorService = Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual().name("test-project-vt-", 0).factory()
        );
        GitLabClient gitLabClient = mock(GitLabClient.class);
        GitTrackingRepository repository = mock(GitTrackingRepository.class);
        GitCommitFileHistoryService fileHistoryService = mock(GitCommitFileHistoryService.class);
        GitLabConcurrencyLimiter limiter = new GitLabConcurrencyLimiter(properties);

        DocumentGitLink failedDocument = document(1L, "failed-project");
        DocumentGitLink okDocument = document(2L, "ok-project");
        GitLabCommit commit = GitLabCommit.fromJson(new ObjectMapper().readTree(
                "{\"id\":\"commit-ok\",\"short_id\":\"commit-ok\",\"title\":\"Update\"}"
        ));

        when(gitLabClient.fetchCommitsUntilKnown(failedDocument, null)).thenThrow(new GitLabClientException("boom"));
        when(gitLabClient.fetchCommitsUntilKnown(okDocument, null)).thenReturn(Collections.singletonList(commit));
        when(repository.saveCommits(okDocument.getId(), Collections.singletonList(commit))).thenReturn(1);
        when(fileHistoryService.synchronizeDocumentFiles(okDocument))
                .thenReturn(new GitFileHistorySyncResult(1, 2, 0, 0, null));
        doThrow(new IllegalStateException("marker write failed"))
                .when(repository).markSyncError(failedDocument.getId(), "boom");

        try {
            GitLabVirtualThreadSyncExecutor syncExecutor = new GitLabVirtualThreadSyncExecutor(
                    properties,
                    gitLabClient,
                    repository,
                    fileHistoryService,
                    executorService,
                    limiter
            );

            GitCommitTrackingResult result = syncExecutor.synchronizeDocuments(
                    java.util.Arrays.asList(failedDocument, okDocument)
            );

            assertThat(result.getProjectsProcessed()).isEqualTo(2);
            assertThat(result.getCommitsProcessed()).isEqualTo(1);
            assertThat(result.getFilesProcessed()).isEqualTo(2);
            assertThat(result.getErrorCount()).isEqualTo(1);
            verify(repository).saveCommits(okDocument.getId(), Collections.singletonList(commit));
            verify(fileHistoryService).synchronizeDocumentFiles(okDocument);
        } finally {
            executorService.shutdownNow();
        }
    }

    @Test
    void alreadyKnownAndLoadedProjectDoesNotIncreaseNewCounters() {
        GitLabProperties properties = new GitLabProperties();
        properties.setVirtualThreadsEnabled(false);
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        GitLabClient gitLabClient = mock(GitLabClient.class);
        GitTrackingRepository repository = mock(GitTrackingRepository.class);
        GitCommitFileHistoryService fileHistoryService = mock(GitCommitFileHistoryService.class);
        GitLabConcurrencyLimiter limiter = new GitLabConcurrencyLimiter(properties);
        DocumentGitLink document = document(3L, "loaded-project");

        when(repository.findLatestStoredCommitSha(document.getId())).thenReturn("known-commit");
        when(gitLabClient.fetchCommitsUntilKnown(document, "known-commit")).thenReturn(Collections.emptyList());
        when(repository.saveCommits(document.getId(), Collections.emptyList())).thenReturn(0);
        when(fileHistoryService.synchronizeDocumentFiles(document)).thenReturn(GitFileHistorySyncResult.disabled());

        try {
            GitLabVirtualThreadSyncExecutor syncExecutor = new GitLabVirtualThreadSyncExecutor(
                    properties,
                    gitLabClient,
                    repository,
                    fileHistoryService,
                    executorService,
                    limiter
            );

            GitCommitTrackingResult result = syncExecutor.synchronizeDocumentsSequentially(
                    Collections.singletonList(document)
            );

            assertThat(result.getProjectsProcessed()).isZero();
            assertThat(result.getCommitsProcessed()).isZero();
            assertThat(result.getFilesProcessed()).isZero();
            verify(gitLabClient).fetchCommitsUntilKnown(document, "known-commit");
            verify(fileHistoryService).synchronizeDocumentFiles(document);
        } finally {
            executorService.shutdownNow();
        }
    }

    private DocumentGitLink document(long id, String projectPath) {
        return new DocumentGitLink(
                id,
                10L,
                20L + id,
                "record-" + id,
                "oid-" + id,
                "Document " + id,
                "https://git.minzdrav.gov.ru/" + projectPath,
                "git.minzdrav.gov.ru",
                projectPath,
                "master"
        );
    }
}
