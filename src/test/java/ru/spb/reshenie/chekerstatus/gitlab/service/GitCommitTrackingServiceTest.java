package ru.spb.reshenie.chekerstatus.gitlab.service;

import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitTrackingRepository;
import ru.spb.reshenie.chekerstatus.gitlab.sync.GitLabSyncExecutor;

import ru.spb.reshenie.chekerstatus.sync.service.SyncRunService;
import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLinkRefreshResult;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GitCommitTrackingServiceTest {

    @Test
    void existingGitLinksAreNotCountedAsNewLinks() {
        GitCommitTrackingResult result = synchronizeWithInsertedLinks(0);

        assertThat(result.getLinksFound()).isZero();
    }

    @Test
    void insertedGitLinksAreCountedAsNewLinks() {
        GitCommitTrackingResult result = synchronizeWithInsertedLinks(1);

        assertThat(result.getLinksFound()).isEqualTo(1);
    }

    @Test
    void progressIncludesCheckedGitLinksEvenWhenNoNewChangesWereFound() {
        GitLabProperties properties = new GitLabProperties();
        properties.setVirtualThreadsEnabled(false);
        properties.setRequireToken(false);
        GitTrackingRepository repository = mock(GitTrackingRepository.class);
        GitLabSyncExecutor executor = mock(GitLabSyncExecutor.class);
        SyncRunService syncRunService = mock(SyncRunService.class);
        List<DocumentGitLink> documents = new ArrayList<DocumentGitLink>();
        documents.add(document(1L));
        documents.add(document(2L));
        List<GitCommitTrackingResult> progressEvents = new ArrayList<GitCommitTrackingResult>();

        when(repository.refreshAndFindActiveLinks(10L)).thenReturn(new GitLinkRefreshResult(
                documents,
                0,
                0,
                0,
                0
        ));
        when(executor.synchronizeDocumentsSequentially(eq(documents), eq(null), eq(null), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    Consumer<GitCommitTrackingResult> listener =
                            (Consumer<GitCommitTrackingResult>) invocation.getArgument(3);
                    GitCommitTrackingResult partial = new GitCommitTrackingResult();
                    partial.setProjectsTotal(2);
                    partial.incrementProjectsChecked();
                    listener.accept(partial);
                    return partial;
                });

        GitCommitTrackingService service = new GitCommitTrackingService(
                properties,
                repository,
                executor,
                syncRunService
        );

        GitCommitTrackingResult result = service.synchronizeDictionaryDocuments(10L, progressEvents::add);

        assertThat(result.getLinksFound()).isZero();
        assertThat(progressEvents)
                .anySatisfy(progress -> {
                    assertThat(progress.getProjectsTotal()).isEqualTo(2);
                    assertThat(progress.getProjectsChecked()).isEqualTo(1);
                });
    }

    private GitCommitTrackingResult synchronizeWithInsertedLinks(int insertedLinks) {
        GitLabProperties properties = new GitLabProperties();
        properties.setVirtualThreadsEnabled(false);
        properties.setRequireToken(false);
        GitTrackingRepository repository = mock(GitTrackingRepository.class);
        GitLabSyncExecutor executor = mock(GitLabSyncExecutor.class);
        SyncRunService syncRunService = mock(SyncRunService.class);
        DocumentGitLink document = document();
        GitCommitTrackingResult processed = new GitCommitTrackingResult();

        when(repository.refreshAndFindActiveLinks(10L)).thenReturn(new GitLinkRefreshResult(
                Collections.singletonList(document),
                insertedLinks,
                1,
                0,
                0
        ));
        when(executor.synchronizeDocumentsSequentially(
                eq(Collections.singletonList(document)),
                eq(null),
                eq(null),
                any()
        )).thenReturn(processed);

        GitCommitTrackingService service = new GitCommitTrackingService(
                properties,
                repository,
                executor,
                syncRunService
        );
        return service.synchronizeDictionaryDocuments(10L);
    }

    private DocumentGitLink document() {
        return document(1L);
    }

    private DocumentGitLink document(long id) {
        return new DocumentGitLink(
                id,
                10L,
                20L,
                "record",
                "oid",
                "Document",
                "https://git.minzdrav.gov.ru/group/project",
                "git.minzdrav.gov.ru",
                "group/project",
                "master"
        );
    }
}
