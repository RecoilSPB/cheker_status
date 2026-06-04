package ru.spb.reshenie.chekerstatus.security.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

public class AccessRole {

    private final long id;
    private final String code;
    private final String name;
    private final String description;
    private final Set<String> permissionCodes;

    public AccessRole(long id,
                      String code,
                      String name,
                      String description,
                      Set<String> permissionCodes) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
        this.permissionCodes = Collections.unmodifiableSet(new LinkedHashSet<String>(permissionCodes));
    }

    public long getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public Set<String> getPermissionCodes() {
        return permissionCodes;
    }

    public boolean grants(String permissionCode) {
        return permissionCodes.contains(permissionCode);
    }
}
