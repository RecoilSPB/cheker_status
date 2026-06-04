package ru.spb.reshenie.chekerstatus.security.model;

public class SecurityUserAccount {

    private final long id;
    private final String username;
    private final String passwordHash;
    private final boolean enabled;

    public SecurityUserAccount(long id, String username, String passwordHash, boolean enabled) {
        this.id = id;
        this.username = username;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    public long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
