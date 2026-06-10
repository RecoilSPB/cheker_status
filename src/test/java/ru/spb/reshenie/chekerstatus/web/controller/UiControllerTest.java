package ru.spb.reshenie.chekerstatus.web.controller;

import ru.spb.reshenie.chekerstatus.sync.repository.SyncRunRepository;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredDiffRow;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffArtifact;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffSummary;
import ru.spb.reshenie.chekerstatus.web.repository.UiRepository;
import ru.spb.reshenie.chekerstatus.web.service.GitFileDiffViewService;
import ru.spb.reshenie.chekerstatus.web.service.UiTimeFormatters;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.spb.reshenie.chekerstatus.config.security.AppSecurityProperties;
import ru.spb.reshenie.chekerstatus.config.security.SecurityConfiguration;
import ru.spb.reshenie.chekerstatus.nsi.scheduler.NsiSyncScheduler;
import ru.spb.reshenie.chekerstatus.security.service.SecurityAccessService;
import ru.spb.reshenie.chekerstatus.security.service.SecurityPermissions;
import ru.spb.reshenie.chekerstatus.sync.model.DashboardSummary;
import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRun;
import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRunDetails;
import ru.spb.reshenie.chekerstatus.sync.query.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.sync.query.PagedResult;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunType;
import ru.spb.reshenie.chekerstatus.web.model.CommitRow;
import ru.spb.reshenie.chekerstatus.web.model.DashboardStats;
import ru.spb.reshenie.chekerstatus.web.model.DocumentDetails;
import ru.spb.reshenie.chekerstatus.web.model.DocumentSummary;
import ru.spb.reshenie.chekerstatus.web.model.DownloadedFileVersion;
import ru.spb.reshenie.chekerstatus.web.model.FileChangeRow;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UiController.class)
@Import({SecurityConfiguration.class, UiTimeFormatters.class})
@EnableConfigurationProperties(AppSecurityProperties.class)
@TestPropertySource(properties = "app.security.password=test")
class UiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UiRepository repository;

    @MockBean
    private SyncRunRepository syncRunRepository;

    @MockBean
    private NsiSyncScheduler scheduler;

    @MockBean(name = "securityAccessService")
    private SecurityAccessService securityAccessService;

    @MockBean
    private GitFileDiffViewService gitFileDiffViewService;

    @BeforeEach
    void setUp() {
        when(repository.dashboardStats()).thenReturn(new DashboardStats(
                1L,
                2L,
                3L,
                4L,
                5L,
                OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC)
        ));
        when(repository.documents(any(String.class), any(String.class))).thenReturn(Collections.singletonList(
                new DocumentSummary(
                        10L,
                        "1.2.3",
                        "Test document",
                        "group/project",
                        "main",
                        "OK",
                        null,
                        OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                        7L,
                        9L,
                        0L
                )
        ));
        when(repository.recentCommits(300)).thenReturn(Collections.singletonList(
                new CommitRow(
                        11L,
                        10L,
                        "Test document",
                        "1234567890abcdef",
                        "1234567890",
                        "Commit title",
                        "Author",
                        OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                        2L,
                        true,
                        "https://example.test/commit/123"
                )
        ));
        when(repository.recentFileChanges(300)).thenReturn(Collections.singletonList(
                new FileChangeRow(
                        12L,
                        10L,
                        "Test document",
                        "1234567890abcdef",
                        "abcdef1234567890",
                        "modified",
                        "old/path.xml",
                        "new/path.xml",
                        "new/path.xml",
                        "SUCCESS",
                        null,
                        100L,
                        120L,
                        "before",
                        "after",
                        "@@ -1 +1 @@",
                        OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC)
                )
        ));
        FileChangeRow selectedFile = new FileChangeRow(
                12L,
                10L,
                "Test document",
                "1234567890abcdef",
                "abcdef1234567890",
                "modified",
                "old/path.xml",
                "new/path.xml",
                "new/path.xml",
                "SUCCESS",
                null,
                100L,
                120L,
                "before",
                "after",
                "TEXT",
                "SUCCESS",
                "TEXT",
                new StructuredFileDiffSummary(1, 1, 0, 1, 0, "MODIFIED", null),
                true,
                null,
                "@@ -1 +1 @@",
                OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        when(repository.documentDetails(10L, null)).thenReturn(new DocumentDetails(
                new DocumentSummary(
                        10L,
                        "1.2.3",
                        "Test document",
                        "group/project",
                        "main",
                        "OK",
                        null,
                        OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                        7L,
                        9L,
                        0L
                ),
                Collections.singletonList(
                        new CommitRow(
                                11L,
                                "1234567890abcdef",
                                "1234567890",
                                "Commit title",
                                "Author",
                                OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                                2L,
                                true,
                                "https://example.test/commit/123"
                        )
                ),
                Collections.singletonList(selectedFile),
                selectedFile
        ));
        when(gitFileDiffViewService.loadStructuredDiff(eq(10L), any(FileChangeRow.class)))
                .thenReturn(new StructuredFileDiffArtifact(
                        "TEXT",
                        "TEXT",
                        new StructuredFileDiffSummary(1, 1, 0, 1, 0, "MODIFIED", null),
                        Collections.singletonList(new StructuredDiffRow(1, "before", 1, "after", "MODIFIED")),
                        Collections.emptyList()
                ));
        when(syncRunRepository.dashboardSummary()).thenReturn(new DashboardSummary(
                "IDLE",
                null,
                null,
                "1.2.643",
                "5",
                0L,
                0L,
                0L,
                0L,
                0L
        ));
        when(syncRunRepository.findRuns(any(NsiSyncRunFilter.class)))
                .thenReturn(new PagedResult<NsiSyncRun>(Collections.emptyList(), 0, 5, 12));
        when(securityAccessService.canManageSync()).thenReturn(true);
        when(securityAccessService.canViewDashboard()).thenReturn(true);
        when(securityAccessService.canViewDashboardMetrics()).thenReturn(true);
        when(securityAccessService.canViewDocuments()).thenReturn(true);
        when(securityAccessService.canViewCommits()).thenReturn(true);
        when(securityAccessService.canViewFileChanges()).thenReturn(true);
        when(securityAccessService.canManageUsers()).thenReturn(true);
        when(securityAccessService.isSecurityEnabled()).thenReturn(true);
        when(securityAccessService.isAuthenticated()).thenReturn(true);
        when(securityAccessService.currentUsername()).thenReturn("tester");
    }

    @ParameterizedTest
    @CsvSource({
            "Europe/Moscow,3",
            "Europe/Amsterdam,2"
    })
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void dashboardFilterUsesClientTimeZone(String timeZone, int expectedOffsetHours) throws Exception {
        mockMvc.perform(get("/dashboard")
                        .param("dateFrom", "2026-06-03T14:25")
                        .param("dateTo", "2026-06-03T16:00")
                        .param("tz", timeZone))
                .andExpect(status().isOk());

        ArgumentCaptor<NsiSyncRunFilter> captor = ArgumentCaptor.forClass(NsiSyncRunFilter.class);
        verify(syncRunRepository).findRuns(captor.capture());

        NsiSyncRunFilter filter = captor.getValue();
        ZoneOffset expectedOffset = ZoneOffset.ofHours(expectedOffsetHours);
        assertThat(filter.getSize()).isEqualTo(5);
        assertThat(filter.getDateFrom()).isEqualTo(OffsetDateTime.of(2026, 6, 3, 14, 25, 0, 0, expectedOffset));
        assertThat(filter.getDateTo()).isEqualTo(OffsetDateTime.of(2026, 6, 3, 16, 0, 0, 0, expectedOffset));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void dashboardRunningRunRowLinksToDetailsAndShowsProgressInsteadOfFinishFields() throws Exception {
        NsiSyncRun run = syncRun(77L, SyncRunStatus.RUNNING, null, null, 42);
        when(syncRunRepository.findRuns(any(NsiSyncRunFilter.class)))
                .thenReturn(new PagedResult<NsiSyncRun>(Collections.singletonList(run), 0, 5, 1));

        String html = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("data-row-href=\"/dashboard/sync-runs/77\"");
        assertThat(html).contains("role=\"link\"");
        assertThat(html).contains("Выполняется");
        assertThat(html).contains("42%");
        assertThat(html).doesNotContain("data-duration-ms=\"65000\"");
    }

    @Test
    @WithMockUser(authorities = {SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.DASHBOARD_SYNC_MANAGE})
    void dashboardDisablesManualSyncButtonWhenSyncIsRunning() throws Exception {
        when(syncRunRepository.dashboardSummary()).thenReturn(summary("RUNNING"));

        String html = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"sync-start-button\"");
        assertThat(html).contains("disabled=\"disabled\"");
        assertThat(html).contains("Синхронизация уже выполняется");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void dashboardHidesSecondaryMetricsWhenDedicatedPermissionIsMissing() throws Exception {
        when(securityAccessService.canViewDashboardMetrics()).thenReturn(false);

        String html = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("Доп. метрики");
        assertThat(html).doesNotContain("Всего найденных GitLab-ссылок");
        assertThat(html).doesNotContain("Новые обработанные коммиты");
        assertThat(html).doesNotContain("Файлы истории GitLab");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void dashboardShowsSecondaryMetricsWhenDedicatedPermissionIsGranted() throws Exception {
        String html = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Доп. метрики");
        assertThat(html).contains("Всего найденных GitLab-ссылок");
        assertThat(html).contains("GitLab-ссылки с ошибками синхронизации");
        assertThat(html).contains("Новые обработанные коммиты");
        assertThat(html).contains("Файлы истории GitLab");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void dashboardRendersMobileSidebarControlsWithoutCloseButton() throws Exception {
        String html = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("id=\"sidebarToggle\"");
        assertThat(html).contains("aria-controls=\"sidebar\"");
        assertThat(html).doesNotContain("data-sidebar-close");
        assertThat(html).contains("/js/sidebar.js");
    }

    @Test
    @WithMockUser(authorities = {
            SecurityPermissions.DOCUMENTS_VIEW,
            SecurityPermissions.COMMITS_VIEW,
            SecurityPermissions.FILE_CHANGES_VIEW
    })
    void listPagesRenderSharedTemplateFragments() throws Exception {
        String documentsHtml = mockMvc.perform(get("/documents"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String commitsHtml = mockMvc.perform(get("/commits"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String fileChangesHtml = mockMvc.perform(get("/file-changes"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(documentsHtml).contains("Документы");
        assertThat(documentsHtml).contains("Синхронизация");
        assertThat(documentsHtml).contains("id=\"sidebarToggle\"");
        assertThat(documentsHtml).doesNotContain("data-sidebar-close");

        assertThat(commitsHtml).contains("Коммиты");
        assertThat(commitsHtml).contains("Синхронизация");
        assertThat(commitsHtml).contains("id=\"sidebarToggle\"");
        assertThat(commitsHtml).doesNotContain("data-sidebar-close");

        assertThat(fileChangesHtml).contains("История файлов");
        assertThat(fileChangesHtml).contains("Синхронизация");
        assertThat(fileChangesHtml).contains("id=\"sidebarToggle\"");
        assertThat(fileChangesHtml).doesNotContain("data-sidebar-close");
    }

    @ParameterizedTest
    @CsvSource({
            "IDLE,Ожидание",
            "FAILED,Ошибка",
            "PARTIAL,Частично выполнено",
            "SERVER_STOPPED,Остановка сервера"
    })
    @WithMockUser(authorities = {SecurityPermissions.DASHBOARD_VIEW, SecurityPermissions.DASHBOARD_SYNC_MANAGE})
    void dashboardShowsTimerAndEnabledManualSyncButtonForNonRunningStatuses(String status, String displayStatus)
            throws Exception {
        when(syncRunRepository.dashboardSummary()).thenReturn(summary(status));
        when(scheduler.getNextAutoRunAt())
                .thenReturn(OffsetDateTime.of(2026, 6, 3, 10, 1, 0, 0, ZoneOffset.UTC));
        when(scheduler.getNextAutoRunDelayMs()).thenReturn(60_000L);

        String html = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains(displayStatus);
        assertThat(html).contains("data-countdown-target=\"2026-06-03T10:01Z\"");
        assertThat(html).contains("id=\"sync-start-button\"");
        assertThat(html).doesNotContain("disabled=\"disabled\"");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_SYNC_MANAGE)
    void startDashboardSyncRedirectsWithoutStartedFlashMessage() throws Exception {
        when(scheduler.startManualSynchronization("1.2.643", false)).thenReturn(42L);

        mockMvc.perform(post("/dashboard/sync-runs")
                        .with(csrf())
                        .param("dictionaryIdentifier", "1.2.643"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(0));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_SYNC_MANAGE)
    void startDashboardSyncRedirectsWithoutAlreadyRunningFlashMessage() throws Exception {
        doThrow(new IllegalStateException("Синхронизация уже выполняется"))
                .when(scheduler).startManualSynchronization("1.2.643", false);

        mockMvc.perform(post("/dashboard/sync-runs")
                        .with(csrf())
                        .param("dictionaryIdentifier", "1.2.643"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(0));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void completedRunShowsFinishFieldsAndDetailsPageHasLogButtonAndProgress() throws Exception {
        OffsetDateTime finishedAt = OffsetDateTime.of(2026, 6, 3, 10, 1, 5, 0, ZoneOffset.UTC);
        NsiSyncRun run = syncRun(78L, SyncRunStatus.SUCCESS, finishedAt, 65000L, 100);
        when(syncRunRepository.findRuns(any(NsiSyncRunFilter.class)))
                .thenReturn(new PagedResult<NsiSyncRun>(Collections.singletonList(run), 0, 5, 1));
        when(syncRunRepository.findDetails(78L))
                .thenReturn(new NsiSyncRunDetails(run, Collections.emptyList()));

        String dashboardHtml = mockMvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);
        String detailsHtml = mockMvc.perform(get("/dashboard/sync-runs/78"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(dashboardHtml).contains("data-row-href=\"/dashboard/sync-runs/78\"");
        assertThat(dashboardHtml).contains("data-utc=\"2026-06-03T10:01:05Z\"");
        assertThat(dashboardHtml).contains("data-duration-ms=\"65000\"");
        assertThat(detailsHtml).contains("href=\"/dashboard/sync-runs/78/logs\"");
        assertThat(detailsHtml).contains("Журнал действий");
        assertThat(detailsHtml).contains("100%");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DOCUMENTS_VIEW)
    void documentPageRendersStructuredDiffTabs() throws Exception {
        String html = mockMvc.perform(get("/documents/10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains("Structured Diff");
        assertThat(html).contains("GitLab Diff");
        assertThat(html).contains("Downloads");
        assertThat(html).contains("Download before");
        assertThat(html).contains("Download after");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DOCUMENTS_VIEW)
    void documentPageShowsZeroFilesForSuccessfulCommitWithoutFileRows() throws Exception {
        CommitRow commit = new CommitRow(
                22L,
                "3c45fb5b252ec0d0c47cb06a7fd237b022cb80c3",
                "3c45fb5b",
                "Обновление документации СЭМД",
                "Автор",
                OffsetDateTime.of(2026, 5, 27, 14, 29, 23, 0, ZoneOffset.UTC),
                0L,
                "SUCCESS",
                "https://example.test/commit/3c45fb5b"
        );
        FileChangeRow selectedFile = new FileChangeRow(
                12L,
                10L,
                "Test document",
                "1234567890abcdef",
                "abcdef1234567890",
                "modified",
                "old/path.xml",
                "new/path.xml",
                "new/path.xml",
                "SUCCESS",
                null,
                100L,
                120L,
                "before",
                "after",
                "@@ -1 +1 @@",
                OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC)
        );
        when(repository.documentDetails(10L, null)).thenReturn(new DocumentDetails(
                new DocumentSummary(
                        10L,
                        "1.2.3",
                        "Test document",
                        "group/project",
                        "main",
                        "OK",
                        null,
                        OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                        7L,
                        9L,
                        0L
                ),
                Collections.singletonList(commit),
                Collections.singletonList(selectedFile),
                selectedFile
        ));

        String html = mockMvc.perform(get("/documents/10"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString(StandardCharsets.UTF_8);

        assertThat(html).contains(">0</span>");
        assertThat(html).doesNotContain("not loaded");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DOCUMENTS_VIEW)
    void downloadEndpointReturnsRequestedVersion() throws Exception {
        when(gitFileDiffViewService.loadVersion(10L, 12L, "after"))
                .thenReturn(new DownloadedFileVersion("file.xml", "application/xml", "payload".getBytes(StandardCharsets.UTF_8)));

        mockMvc.perform(get("/documents/10/file-changes/12/download").param("version", "after"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.FILE_CHANGES_VIEW)
    void downloadEndpointRequiresDocumentsPermission() throws Exception {
        mockMvc.perform(get("/documents/10/file-changes/12/download").param("version", "after"))
                .andExpect(status().isForbidden());
    }

    private NsiSyncRun syncRun(long id,
                               SyncRunStatus status,
                               OffsetDateTime finishedAt,
                               Long durationMs,
                               int progressPercent) {
        return new NsiSyncRun(
                id,
                SyncRunType.AUTO,
                status,
                "1.2.643",
                "5",
                OffsetDateTime.of(2026, 6, 3, 10, 0, 0, 0, ZoneOffset.UTC),
                finishedAt,
                durationMs,
                false,
                10,
                1,
                2,
                0,
                3,
                4,
                5,
                6,
                progressPercent,
                0,
                null
        );
    }

    private DashboardSummary summary(String status) {
        return new DashboardSummary(
                status,
                null,
                null,
                "1.2.643",
                "5",
                0L,
                0L,
                0L,
                0L,
                0L
        );
    }
}
