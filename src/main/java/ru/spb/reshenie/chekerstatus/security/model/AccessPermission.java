package ru.spb.reshenie.chekerstatus.security.model;

public class AccessPermission {

    private final long id;
    private final String code;
    private final String name;
    private final String description;

    public AccessPermission(long id, String code, String name, String description) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.description = description;
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
}
