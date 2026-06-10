package ru.spb.reshenie.chekerstatus.web.controller;

import org.junit.jupiter.api.Test;
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
import ru.spb.reshenie.chekerstatus.gitlab.service.FileDiffBackfillScope;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitFileDiffBackfillResult;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitFileDiffBackfillService;
import ru.spb.reshenie.chekerstatus.security.service.SecurityPermissions;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FileDiffAdminController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(AppSecurityProperties.class)
@TestPropertySource(properties = "app.security.password=test")
class FileDiffAdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private GitFileDiffBackfillService gitFileDiffBackfillService;

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_SYNC_MANAGE)
    void backfillEndpointRequiresManagePermissionAndReturnsAccepted() throws Exception {
        when(gitFileDiffBackfillService.backfill(FileDiffBackfillScope.MISSING, 10L, 25))
                .thenReturn(new GitFileDiffBackfillResult(FileDiffBackfillScope.MISSING, 10L, 25, 10, 9, 1));

        mockMvc.perform(post("/api/file-diff/backfill")
                        .param("scope", "MISSING")
                        .param("documentId", "10")
                        .param("limit", "25"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.attempted").value(10))
                .andExpect(jsonPath("$.succeeded").value(9))
                .andExpect(jsonPath("$.failed").value(1));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DASHBOARD_VIEW)
    void backfillEndpointRejectsUsersWithoutManagePermission() throws Exception {
        mockMvc.perform(post("/api/file-diff/backfill"))
                .andExpect(status().isForbidden());
    }
}
