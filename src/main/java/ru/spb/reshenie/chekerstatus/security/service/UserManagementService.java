package ru.spb.reshenie.chekerstatus.security.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.spb.reshenie.chekerstatus.security.model.AccessPermission;
import ru.spb.reshenie.chekerstatus.security.model.AccessRole;
import ru.spb.reshenie.chekerstatus.security.model.ManagedUser;
import ru.spb.reshenie.chekerstatus.security.repository.AccessControlRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class UserManagementService {

    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final AccessControlRepository repository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(AccessControlRepository repository, PasswordEncoder passwordEncoder) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
    }

    public List<ManagedUser> findAllUsers() {
        return repository.findAllManagedUsers();
    }

    public ManagedUser findUser(long userId) {
        return repository.findManagedUser(userId);
    }

    public List<AccessRole> findAllRoles() {
        return repository.findAllRoles();
    }

    public List<AccessPermission> findAllPermissions() {
        return repository.findAllPermissions();
    }

    @Transactional
    public long createUser(String username,
                           String displayName,
                           String rawPassword,
                           boolean enabled,
                           List<String> roleCodes,
                           List<String> permissionCodes) {
        Catalog catalog = loadCatalog();
        String normalizedUsername = normalizeUsername(username);
        String normalizedDisplayName = normalizeDisplayName(displayName);
        String password = requirePassword(rawPassword);
        Set<String> normalizedRoles = normalizeCodes(roleCodes, catalog.rolesByCode.keySet(), "role");
        Set<String> normalizedPermissions = normalizeCodes(permissionCodes, catalog.permissionsByCode.keySet(), "permission");

        if (repository.findUserIdByUsername(normalizedUsername) != null) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует.");
        }

        long userId = repository.insertUser(
                normalizedUsername,
                normalizedDisplayName,
                passwordEncoder.encode(password),
                enabled
        );
        repository.replaceUserRoles(userId, normalizedRoles);
        repository.replaceUserPermissions(userId, normalizedPermissions);
        return userId;
    }

    @Transactional
    public void updateUser(long userId,
                           String username,
                           String displayName,
                           String rawPassword,
                           boolean enabled,
                           List<String> roleCodes,
                           List<String> permissionCodes) {
        ManagedUser existing = repository.findManagedUser(userId);
        if (existing == null) {
            throw new IllegalArgumentException("Пользователь не найден.");
        }

        Catalog catalog = loadCatalog();
        String normalizedUsername = normalizeUsername(username);
        String normalizedDisplayName = normalizeDisplayName(displayName);
        Set<String> normalizedRoles = normalizeCodes(roleCodes, catalog.rolesByCode.keySet(), "role");
        Set<String> normalizedPermissions = normalizeCodes(permissionCodes, catalog.permissionsByCode.keySet(), "permission");
        Set<String> effectivePermissions = effectivePermissions(normalizedRoles, normalizedPermissions, catalog.rolesByCode);

        Long duplicateUserId = repository.findUserIdByUsername(normalizedUsername);
        if (duplicateUserId != null && duplicateUserId.longValue() != userId) {
            throw new IllegalArgumentException("Пользователь с таким логином уже существует.");
        }

        if (existing.hasEffectivePermission(SecurityPermissions.USERS_MANAGE)
                && (!enabled || !effectivePermissions.contains(SecurityPermissions.USERS_MANAGE))
                && repository.countEnabledUsersWithPermission(SecurityPermissions.USERS_MANAGE, Long.valueOf(userId)) == 0L) {
            throw new IllegalArgumentException(
                    "В системе должен остаться хотя бы один включённый пользователь с правом управления доступом."
            );
        }

        repository.updateUser(userId, normalizedUsername, normalizedDisplayName, enabled);
        if (hasText(rawPassword)) {
            repository.updateUserPassword(userId, passwordEncoder.encode(rawPassword));
        }
        repository.replaceUserRoles(userId, normalizedRoles);
        repository.replaceUserPermissions(userId, normalizedPermissions);
    }

    @Transactional
    public void bootstrapAdmin(String username, String rawPassword) {
        String normalizedUsername = normalizeUsername(username);
        String password = requirePassword(rawPassword);

        Long existingUserId = repository.findUserIdByUsername(normalizedUsername);
        if (existingUserId == null) {
            long userId = repository.insertUser(
                    normalizedUsername,
                    "Администратор",
                    passwordEncoder.encode(password),
                    true
            );
            repository.ensureRoleAssigned(userId, ADMIN_ROLE_CODE);
            return;
        }

        repository.updateUserEnabled(existingUserId.longValue(), true);
        repository.updateUserPassword(existingUserId.longValue(), passwordEncoder.encode(password));
        repository.ensureRoleAssigned(existingUserId.longValue(), ADMIN_ROLE_CODE);
    }

    private Catalog loadCatalog() {
        List<AccessRole> roles = repository.findAllRoles();
        Map<String, AccessRole> rolesByCode = new LinkedHashMap<String, AccessRole>();
        for (AccessRole role : roles) {
            rolesByCode.put(role.getCode(), role);
        }

        List<AccessPermission> permissions = repository.findAllPermissions();
        Map<String, AccessPermission> permissionsByCode = new LinkedHashMap<String, AccessPermission>();
        for (AccessPermission permission : permissions) {
            permissionsByCode.put(permission.getCode(), permission);
        }
        return new Catalog(rolesByCode, permissionsByCode);
    }

    private Set<String> effectivePermissions(Set<String> roleCodes,
                                             Set<String> directPermissionCodes,
                                             Map<String, AccessRole> rolesByCode) {
        Set<String> result = new LinkedHashSet<String>(directPermissionCodes);
        for (String roleCode : roleCodes) {
            AccessRole role = rolesByCode.get(roleCode);
            if (role != null) {
                result.addAll(role.getPermissionCodes());
            }
        }
        return result;
    }

    private String normalizeUsername(String username) {
        String normalized = trimToNull(username);
        if (normalized == null) {
            throw new IllegalArgumentException("Логин обязателен.");
        }
        if (normalized.length() > 120) {
            throw new IllegalArgumentException("Логин не должен быть длиннее 120 символов.");
        }
        return normalized;
    }

    private String normalizeDisplayName(String displayName) {
        String normalized = trimToNull(displayName);
        if (normalized != null && normalized.length() > 200) {
            throw new IllegalArgumentException("Имя не должно быть длиннее 200 символов.");
        }
        return normalized;
    }

    private String requirePassword(String rawPassword) {
        if (!hasText(rawPassword)) {
            throw new IllegalArgumentException("Пароль обязателен.");
        }
        if (rawPassword.length() > 255) {
            throw new IllegalArgumentException("Пароль не должен быть длиннее 255 символов.");
        }
        return rawPassword;
    }

    private Set<String> normalizeCodes(List<String> values, Set<String> allowedValues, String label) {
        Set<String> normalized = new LinkedHashSet<String>();
        if (values == null) {
            return normalized;
        }
        for (String value : values) {
            String code = trimToNull(value);
            if (code == null) {
                continue;
            }
            if (!allowedValues.contains(code)) {
                throw new IllegalArgumentException("Unknown " + label + ": " + code);
            }
            normalized.add(code);
        }
        return normalized;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static class Catalog {
        private final Map<String, AccessRole> rolesByCode;
        private final Map<String, AccessPermission> permissionsByCode;

        private Catalog(Map<String, AccessRole> rolesByCode, Map<String, AccessPermission> permissionsByCode) {
            this.rolesByCode = rolesByCode;
            this.permissionsByCode = permissionsByCode;
        }
    }
}
