package ru.spb.reshenie.chekerstatus.web;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import ru.spb.reshenie.chekerstatus.config.AppSecurityProperties;
import ru.spb.reshenie.chekerstatus.config.SecurityConfiguration;
import ru.spb.reshenie.chekerstatus.nsi.NsiSyncScheduler;
import ru.spb.reshenie.chekerstatus.sync.DashboardSummary;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRun;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRunRepository;
import ru.spb.reshenie.chekerstatus.sync.PagedResult;
import ru.spb.reshenie.chekerstatus.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.sync.SyncRunType;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DashboardApiController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(AppSecurityProperties.class)
@TestPropertySource(properties = "app.security.password=test")
class DashboardApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private NsiSyncRunRepository repository;

    @MockBean
    private NsiSyncScheduler scheduler;

    @Test
    @WithMockUser
    void returnsDashboardSummary() throws Exception {
        when(repository.dashboardSummary()).thenReturn(new DashboardSummary(
                "IDLE",
                null,
                null,
                "1.2.643",
                "5",
                100L,
                20L,
                3L,
                200L,
                500L
        ));

        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentStatus").value("IDLE"))
                .andExpect(jsonPath("$.dictionaryIdentifier").value("1.2.643"))
                .andExpect(jsonPath("$.dictionaryVersion").value("5"))
                .andExpect(jsonPath("$.totalRecords").value(100))
                .andExpect(jsonPath("$.totalGitLinks").value(20))
                .andExpect(jsonPath("$.gitLinksWithErrors").value(3))
                .andExpect(jsonPath("$.totalCommits").value(200))
                .andExpect(jsonPath("$.totalFiles").value(500));
    }

    @Test
    @WithMockUser
    void returnsFilteredSyncRunsPage() throws Exception {
        NsiSyncRun run = new NsiSyncRun(
                1L,
                SyncRunType.AUTO,
                SyncRunStatus.SUCCESS,
                "1.2.643",
                "5",
                OffsetDateTime.of(2026, 6, 2, 10, 0, 0, 0, ZoneOffset.UTC),
                OffsetDateTime.of(2026, 6, 2, 10, 1, 0, 0, ZoneOffset.UTC),
                60000L,
                false,
                10,
                1,
                2,
                0,
                3,
                3,
                30,
                40,
                0,
                null
        );
        when(repository.findRuns(any(NsiSyncRunFilter.class)))
                .thenReturn(new PagedResult<NsiSyncRun>(Collections.singletonList(run), 2, 5, 11));

        mockMvc.perform(get("/api/dashboard/sync-runs")
                        .param("page", "2")
                        .param("size", "5")
                        .param("status", "SUCCESS")
                        .param("runType", "AUTO")
                        .param("dictionaryIdentifier", "1.2.643")
                        .param("hasErrors", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(2))
                .andExpect(jsonPath("$.size").value(5))
                .andExpect(jsonPath("$.totalElements").value(11))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.content[0].id").value(1))
                .andExpect(jsonPath("$.content[0].status").value("SUCCESS"));

        ArgumentCaptor<NsiSyncRunFilter> captor = ArgumentCaptor.forClass(NsiSyncRunFilter.class);
        verify(repository).findRuns(captor.capture());
        assertThat(captor.getValue().getPage()).isEqualTo(2);
        assertThat(captor.getValue().getSize()).isEqualTo(5);
        assertThat(captor.getValue().getStatus()).isEqualTo(SyncRunStatus.SUCCESS);
        assertThat(captor.getValue().getRunType()).isEqualTo(SyncRunType.AUTO);
        assertThat(captor.getValue().getDictionaryIdentifier()).isEqualTo("1.2.643");
        assertThat(captor.getValue().getHasErrors()).isFalse();
    }

    @Test
    @WithMockUser
    void returnsNotFoundForMissingSyncRunDetails() throws Exception {
        when(repository.findDetails(404L)).thenThrow(new EmptyResultDataAccessException(1));

        mockMvc.perform(get("/api/dashboard/sync-runs/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }
}
