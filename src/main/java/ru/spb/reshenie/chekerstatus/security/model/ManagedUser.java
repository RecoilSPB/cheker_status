package ru.spb.reshenie.chekerstatus.security.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class ManagedUser {

    private final long id;
    private final String username;
    private final String displayName;
    private final boolean enabled;
    private final Set<String> roleCodes;
    private final Set<String> directPermissionCodes;
    private final Set<String> effectivePermissionCodes;

    public ManagedUser(long id,
                       String username,
                       String displayName,
                       boolean enabled,
                       Set<String> roleCodes,
                       Set<String> directPermissionCodes,
                       Set<String> effectivePermissionCodes) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.enabled = enabled;
        this.roleCodes = immutableCopy(roleCodes);
        this.directPermissionCodes = immutableCopy(directPermissionCodes);
        this.effectivePermissionCodes = immutableCopy(effectivePermissionCodes);
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<String> getRoleCodes() {
        return roleCodes;
    }

    public Set<String> getDirectPermissionCodes() {
        return directPermissionCodes;
    }

    public Set<String> getEffectivePermissionCodes() {
        return effectivePermissionCodes;
    }

    public String getDisplayLabel() {
        return hasText(displayName) ? displayName : username;
    }

    public boolean hasRole(String roleCode) {
        return roleCodes.contains(roleCode);
    }

    public boolean hasDirectPermission(String permissionCode) {
        return directPermissionCodes.contains(permissionCode);
    }

    public boolean hasEffectivePermission(String permissionCode) {
        return effectivePermissionCodes.contains(permissionCode);
    }

    private Set<String> immutableCopy(Set<String> values) {
        return Collections.unmodifiableSet(new LinkedHashSet<String>(values));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
