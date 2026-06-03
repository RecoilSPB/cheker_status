package ru.spb.reshenie.chekerstatus.application.gitlab;

import ru.spb.reshenie.chekerstatus.domain.gitlab.GitFileContentRequestPlan;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitLabCommitDiff;
import ru.spb.reshenie.chekerstatus.infrastructure.gitlab.GitLabConcurrencyLimiter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.application.sync.NsiSyncRunService;
import ru.spb.reshenie.chekerstatus.infrastructure.config.GitLabProperties;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class GitCommitFileHistoryServiceTest {

    private final GitLabProperties properties = new GitLabProperties();
    private final ExecutorService executorService = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("test-gitlab-vt-", 0).factory()
    );
    private final GitCommitFileHistoryService service = new GitCommitFileHistoryService(
            properties,
            null,
            null,
            null,
            executorService,
            new GitLabConcurrencyLimiter(properties),
            mock(NsiSyncRunService.class)
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
}
