package ru.spb.reshenie.chekerstatus.web.dto;

public class ManualSyncRequest {

    private String dictionaryIdentifier;
    private boolean forceReload;

    public String getDictionaryIdentifier() {
        return dictionaryIdentifier;
    }

    public void setDictionaryIdentifier(String dictionaryIdentifier) {
        this.dictionaryIdentifier = dictionaryIdentifier;
    }

    public boolean isForceReload() {
        return forceReload;
    }

    public void setForceReload(boolean forceReload) {
        this.forceReload = forceReload;
    }
}
