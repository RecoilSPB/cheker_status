package ru.spb.reshenie.chekerstatus.security.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.spb.reshenie.chekerstatus.security.model.AccessPermission;
import ru.spb.reshenie.chekerstatus.security.model.AccessRole;
import ru.spb.reshenie.chekerstatus.security.model.ManagedUser;
import ru.spb.reshenie.chekerstatus.security.repository.AccessControlRepository;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserManagementServiceTest {

    @Mock
    private AccessControlRepository repository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private UserManagementService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UserManagementService(repository, passwordEncoder);
    }

    @Test
    void createUserStoresEncodedPasswordAndAssignments() {
        when(repository.findAllRoles()).thenReturn(List.of(
                new AccessRole(1L, "VIEWER", "Viewer", "Read only", Set.of(SecurityPermissions.DOCUMENTS_VIEW))
        ));
        when(repository.findAllPermissions()).thenReturn(List.of(
                new AccessPermission(1L, SecurityPermissions.DOCUMENTS_VIEW, "Documents", "Read documents")
        ));
        when(repository.findUserIdByUsername("viewer")).thenReturn(null);
        when(passwordEncoder.encode("secret")).thenReturn("{noop}secret");
        when(repository.insertUser("viewer", "Document Viewer", "{noop}secret", true)).thenReturn(42L);

        service.createUser(
                "viewer",
                "Document Viewer",
                "secret",
                true,
                List.of("VIEWER"),
                List.of(SecurityPermissions.DOCUMENTS_VIEW)
        );

        verify(repository).insertUser("viewer", "Document Viewer", "{noop}secret", true);
        verify(repository).replaceUserRoles(42L, Set.of("VIEWER"));
        verify(repository).replaceUserPermissions(42L, Set.of(SecurityPermissions.DOCUMENTS_VIEW));
    }

    @Test
    void updateUserRejectsRemovingLastUserWithAccessManagementPermission() {
        when(repository.findManagedUser(7L)).thenReturn(new ManagedUser(
                7L,
                "admin",
                "Admin",
                true,
                Set.of("ADMIN"),
                Set.of(),
                Set.of(SecurityPermissions.USERS_MANAGE)
        ));
        when(repository.findAllRoles()).thenReturn(List.of(
                new AccessRole(1L, "ADMIN", "Admin", "Full access", Set.of(SecurityPermissions.USERS_MANAGE))
        ));
        when(repository.findAllPermissions()).thenReturn(List.of(
                new AccessPermission(1L, SecurityPermissions.USERS_MANAGE, "Users", "Manage access")
        ));
        when(repository.findUserIdByUsername("admin")).thenReturn(7L);
        when(repository.countEnabledUsersWithPermission(eq(SecurityPermissions.USERS_MANAGE), eq(Long.valueOf(7L))))
                .thenReturn(0L);

        assertThatThrownBy(() -> service.updateUser(
                7L,
                "admin",
                "Admin",
                "",
                false,
                List.of(),
                List.of()
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("хотя бы один включённый пользователь");
    }
}
