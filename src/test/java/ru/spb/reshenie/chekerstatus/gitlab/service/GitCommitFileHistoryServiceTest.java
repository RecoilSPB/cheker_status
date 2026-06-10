package ru.spb.reshenie.chekerstatus.gitlab.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
import ru.spb.reshenie.chekerstatus.gitlab.client.GitLabClient;
import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentRequestPlan;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommitDiff;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabFileContentResult;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitLabCommit;
import ru.spb.reshenie.chekerstatus.gitlab.storage.GitFileStorage;
import ru.spb.reshenie.chekerstatus.gitlab.sync.GitLabConcurrencyLimiter;
import ru.spb.reshenie.chekerstatus.sync.service.SyncRunService;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitCommitFileHistoryServiceTest {

    private final GitLabProperties properties = new GitLabProperties();
    private final ExecutorService executorService = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("test-gitlab-vt-", 0).factory()
    );
    private final GitFileStorage gitFileStorage = mock(GitFileStorage.class);
    private final GitFileDiffProcessor gitFileDiffProcessor = mock(GitFileDiffProcessor.class);
    private final GitCommitFileHistoryService service = new GitCommitFileHistoryService(
            properties,
            null,
            null,
            null,
            executorService,
            new GitLabConcurrencyLimiter(properties),
            gitFileStorage,
            gitFileDiffProcessor,
            mock(SyncRunService.class)
    );

    @AfterEach
    void tearDown() {
        executorService.shutdownNow();
    }

    @Test
    void deletedFileDoesNotRequestContentAfterFromCommitSha() {
        GitLabCommitDiff diff = new GitLabCommitDiff("old.xml", "old.xml", "", false, false, true, false, null);

        GitFileContentRequestPlan plan = service.contentRequestPlan(diff, "commit-sha", "parent-sha");

        assertThat(plan.isFetchAfter()).isFalse();
        assertThat(plan.isFetchBefore()).isTrue();
        assertThat(plan.getBeforePath()).isEqualTo("old.xml");
        assertThat(plan.getBeforeRef()).isEqualTo("parent-sha");
    }

    @Test
    void newFileDoesNotRequestContentBeforeFromParentSha() {
        GitLabCommitDiff diff = new GitLabCommitDiff("new.xml", "new.xml", "", true, false, false, false, null);

        GitFileContentRequestPlan plan = service.contentRequestPlan(diff, "commit-sha", "parent-sha");

        assertThat(plan.isFetchAfter()).isTrue();
        assertThat(plan.getAfterPath()).isEqualTo("new.xml");
        assertThat(plan.getAfterRef()).isEqualTo("commit-sha");
        assertThat(plan.isFetchBefore()).isFalse();
    }

    @Test
    void storesGitLabBytesInExternalStorageWhenMinioModeIsEnabled() {
        GitLabClient gitLabClient = mock(GitLabClient.class);
        GitFileStorage storage = mock(GitFileStorage.class);
        when(storage.storesExternally()).thenReturn(true);
        when(storage.store(any(DocumentGitLink.class), eq("path/file.xml"), eq("commit-sha"), any(byte[].class), anyString()))
                .thenReturn("gitlab/git.minzdrav.gov.ru/project/refs/commit-sha/path/file.xml");
        when(gitLabClient.fetchRawFile(any(DocumentGitLink.class), eq("path/file.xml"), eq("commit-sha")))
                .thenReturn(GitLabFileContentResult.found("payload".getBytes(StandardCharsets.UTF_8)));

        GitCommitFileHistoryService minioService = new GitCommitFileHistoryService(
                properties,
                gitLabClient,
                null,
                null,
                executorService,
                new GitLabConcurrencyLimiter(properties),
                storage,
                mock(GitFileDiffProcessor.class),
                mock(SyncRunService.class)
        );

        GitCommitFileChange change = minioService.buildFileChange(
                new DocumentGitLink(1L, 1L, 1L, "record", "1.2.3", "Document", "url",
                        "git.minzdrav.gov.ru", "project", "main"),
                new StoredGitLabCommit(10L, 1L, "commit-sha",
                        OffsetDateTime.of(2026, 6, 9, 10, 0, 0, 0, ZoneOffset.UTC)),
                null,
                new GitLabCommitDiff("path/file.xml", "path/file.xml", "@@ diff @@", true, false, false, false, null)
        );

        assertThat(change.getContentFetchStatus()).isEqualTo(GitCommitFileHistoryService.STATUS_SUCCESS);
        assertThat(change.getContentAfter()).isNotNull();
        assertThat(change.getContentAfter().getContent()).isNull();
        assertThat(change.getContentAfter().getObjectKey())
                .isEqualTo("gitlab/git.minzdrav.gov.ru/project/refs/commit-sha/path/file.xml");
        assertThat(change.getContentAfter().getSize()).isEqualTo(7L);
    }

    @Test
    void decodesWindows1251ContentWhenExternalStorageIsDisabled() {
        GitLabClient gitLabClient = mock(GitLabClient.class);
        GitFileStorage storage = mock(GitFileStorage.class);
        when(storage.storesExternally()).thenReturn(false);
        byte[] csvBytes = "№;Xpath;Кардинальность;Номер требования"
                .getBytes(Charset.forName("windows-1251"));
        when(gitLabClient.fetchRawFile(any(DocumentGitLink.class),
                eq("xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv"), eq("commit-sha")))
                .thenReturn(GitLabFileContentResult.found(csvBytes));

        GitCommitFileHistoryService textService = new GitCommitFileHistoryService(
                properties,
                gitLabClient,
                null,
                null,
                executorService,
                new GitLabConcurrencyLimiter(properties),
                storage,
                mock(GitFileDiffProcessor.class),
                mock(SyncRunService.class)
        );

        GitCommitFileChange change = textService.buildFileChange(
                new DocumentGitLink(1L, 1L, 1L, "record", "1.2.3", "Document", "url",
                        "git.minzdrav.gov.ru", "project", "main"),
                new StoredGitLabCommit(10L, 1L, "commit-sha",
                        OffsetDateTime.of(2026, 6, 9, 10, 0, 0, 0, ZoneOffset.UTC)),
                null,
                new GitLabCommitDiff("xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv",
                        "xpath/CDA_ПЕРВИЧНЫЙ_ОСМОТР_ВРАЧА_Р1.csv",
                        "@@ diff @@",
                        true,
                        false,
                        false,
                        false,
                        null)
        );

        assertThat(change.getContentAfter()).isNotNull();
        assertThat(change.getContentAfter().getContent())
                .isEqualTo("№;Xpath;Кардинальность;Номер требования");
    }
}
