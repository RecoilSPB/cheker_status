package ru.spb.reshenie.chekerstatus.domain.nsi;

public class PassportSaveResult {

    private final long dictionaryId;
    private final long versionId;
    private final boolean versionLoaded;

    public PassportSaveResult(long dictionaryId, long versionId, boolean versionLoaded) {
        this.dictionaryId = dictionaryId;
        this.versionId = versionId;
        this.versionLoaded = versionLoaded;
    }

    public long getDictionaryId() {
        return dictionaryId;
    }

    public long getVersionId() {
        return versionId;
    }

    public boolean isVersionLoaded() {
        return versionLoaded;
    }
}
