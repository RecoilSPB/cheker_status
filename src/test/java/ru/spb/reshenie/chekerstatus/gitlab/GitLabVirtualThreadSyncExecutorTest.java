package ru.spb.reshenie.chekerstatus.gitlab;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.config.GitLabProperties;

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

        when(gitLabClient.fetchAllCommits(failedDocument)).thenThrow(new GitLabClientException("boom"));
        when(gitLabClient.fetchAllCommits(okDocument)).thenReturn(Collections.singletonList(commit));
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
