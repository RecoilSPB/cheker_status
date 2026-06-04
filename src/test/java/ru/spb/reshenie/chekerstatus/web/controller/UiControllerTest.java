package ru.spb.reshenie.chekerstatus.web.controller;

import ru.spb.reshenie.chekerstatus.sync.repository.SyncRunRepository;
import ru.spb.reshenie.chekerstatus.web.repository.UiRepository;
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
import ru.spb.reshenie.chekerstatus.sync.model.DashboardSummary;
import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRun;
import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRunDetails;
import ru.spb.reshenie.chekerstatus.sync.query.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.sync.query.PagedResult;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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

    @BeforeEach
    void setUp() {
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
    }

    @ParameterizedTest
    @CsvSource({
            "Europe/Moscow,3",
            "Europe/Amsterdam,2"
    })
    @WithMockUser
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
    @WithMockUser
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
    @WithMockUser
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

    @ParameterizedTest
    @CsvSource({
            "IDLE,Ожидание",
            "FAILED,Ошибка",
            "PARTIAL,Частично выполнено",
            "SERVER_STOPPED,Остановка сервера"
    })
    @WithMockUser
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
    @WithMockUser
    void startDashboardSyncRedirectsWithoutStartedFlashMessage() throws Exception {
        when(scheduler.startManualSynchronization("1.2.643", false)).thenReturn(42L);

        mockMvc.perform(post("/dashboard/sync-runs")
                        .with(csrf())
                        .param("dictionaryIdentifier", "1.2.643"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(0));
    }

    @Test
    @WithMockUser
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
    @WithMockUser
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
