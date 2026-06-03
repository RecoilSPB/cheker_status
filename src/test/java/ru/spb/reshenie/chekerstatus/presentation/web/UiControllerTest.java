package ru.spb.reshenie.chekerstatus.presentation.web;

import ru.spb.reshenie.chekerstatus.infrastructure.persistence.NsiSyncRunRepository;
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
import ru.spb.reshenie.chekerstatus.infrastructure.config.AppSecurityProperties;
import ru.spb.reshenie.chekerstatus.infrastructure.config.SecurityConfiguration;
import ru.spb.reshenie.chekerstatus.application.nsi.NsiSyncScheduler;
import ru.spb.reshenie.chekerstatus.domain.sync.DashboardSummary;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRun;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.domain.sync.PagedResult;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
    private NsiSyncRunRepository syncRunRepository;

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
}
