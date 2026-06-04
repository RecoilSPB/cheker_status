package ru.spb.reshenie.chekerstatus.web.controller;

import org.junit.jupiter.api.BeforeEach;
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
import ru.spb.reshenie.chekerstatus.security.model.AccessPermission;
import ru.spb.reshenie.chekerstatus.security.model.AccessRole;
import ru.spb.reshenie.chekerstatus.security.model.ManagedUser;
import ru.spb.reshenie.chekerstatus.security.service.SecurityAccessService;
import ru.spb.reshenie.chekerstatus.security.service.SecurityPermissions;
import ru.spb.reshenie.chekerstatus.security.service.UserManagementService;

import java.util.List;
import java.util.Set;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminUserController.class)
@Import(SecurityConfiguration.class)
@EnableConfigurationProperties(AppSecurityProperties.class)
@TestPropertySource(properties = "app.security.password=test")
class AdminUserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserManagementService userManagementService;

    @MockBean(name = "securityAccessService")
    private SecurityAccessService securityAccessService;

    @BeforeEach
    void setUp() {
        when(securityAccessService.canViewDashboard()).thenReturn(true);
        when(securityAccessService.canViewDocuments()).thenReturn(true);
        when(securityAccessService.canViewCommits()).thenReturn(true);
        when(securityAccessService.canViewFileChanges()).thenReturn(true);
        when(securityAccessService.canManageUsers()).thenReturn(true);
        when(securityAccessService.isSecurityEnabled()).thenReturn(true);
        when(securityAccessService.isAuthenticated()).thenReturn(true);
        when(securityAccessService.currentUsername()).thenReturn("admin");
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.USERS_MANAGE)
    void rendersUserListPageWithLinkToUserCardAndCreateButton() throws Exception {
        when(userManagementService.findAllUsers()).thenReturn(List.of(sampleUser()));

        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Добавить пользователя")))
                .andExpect(content().string(containsString("href=\"/admin/users/new\"")))
                .andExpect(content().string(containsString("href=\"/admin/users/1\"")));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.USERS_MANAGE)
    void rendersNewUserPage() throws Exception {
        when(userManagementService.findAllRoles()).thenReturn(sampleRoles());
        when(userManagementService.findAllPermissions()).thenReturn(samplePermissions());

        mockMvc.perform(get("/admin/users/new"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Новый пользователь")))
                .andExpect(content().string(containsString("action=\"/admin/users\"")))
                .andExpect(content().string(containsString("Создать пользователя")));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.USERS_MANAGE)
    void rendersUserCardPage() throws Exception {
        when(userManagementService.findUser(1L)).thenReturn(sampleUser());
        when(userManagementService.findAllRoles()).thenReturn(sampleRoles());
        when(userManagementService.findAllPermissions()).thenReturn(samplePermissions());

        mockMvc.perform(get("/admin/users/1"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Карточка пользователя")))
                .andExpect(content().string(containsString("action=\"/admin/users/1\"")))
                .andExpect(content().string(containsString("Administrator (admin)")));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.USERS_MANAGE)
    void returnsNotFoundForMissingUserCard() throws Exception {
        when(userManagementService.findUser(999L)).thenReturn(null);

        mockMvc.perform(get("/admin/users/999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.USERS_MANAGE)
    void createUserRedirectsToUserCard() throws Exception {
        when(userManagementService.createUser(
                "alice",
                "Alice",
                "secret",
                true,
                List.of("ADMIN"),
                List.of(SecurityPermissions.USERS_MANAGE)
        )).thenReturn(42L);

        mockMvc.perform(post("/admin/users")
                        .with(csrf())
                        .param("username", "alice")
                        .param("displayName", "Alice")
                        .param("password", "secret")
                        .param("enabled", "true")
                        .param("roleCodes", "ADMIN")
                        .param("permissionCodes", SecurityPermissions.USERS_MANAGE))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/42"));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.USERS_MANAGE)
    void updateUserRedirectsBackToUserCard() throws Exception {
        mockMvc.perform(post("/admin/users/1")
                        .with(csrf())
                        .param("username", "admin")
                        .param("displayName", "Administrator")
                        .param("password", "")
                        .param("enabled", "true")
                        .param("roleCodes", "ADMIN")
                        .param("permissionCodes", SecurityPermissions.USERS_MANAGE))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/users/1"));
    }

    @Test
    @WithMockUser(authorities = SecurityPermissions.DOCUMENTS_VIEW)
    void forbidsUserManagementWithoutPermission() throws Exception {
        mockMvc.perform(get("/admin/users"))
                .andExpect(status().isForbidden());
    }

    private ManagedUser sampleUser() {
        return new ManagedUser(
                1L,
                "admin",
                "Administrator",
                true,
                Set.of("ADMIN"),
                Set.of(),
                Set.of(SecurityPermissions.USERS_MANAGE)
        );
    }

    private List<AccessRole> sampleRoles() {
        return List.of(
                new AccessRole(1L, "ADMIN", "Administrator", "Full access", Set.of(SecurityPermissions.USERS_MANAGE))
        );
    }

    private List<AccessPermission> samplePermissions() {
        return List.of(
                new AccessPermission(1L, SecurityPermissions.USERS_MANAGE, "Manage users", "Access management")
        );
    }
}
