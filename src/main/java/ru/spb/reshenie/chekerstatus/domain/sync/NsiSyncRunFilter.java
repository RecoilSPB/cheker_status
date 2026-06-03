package ru.spb.reshenie.chekerstatus.domain.sync;

import java.time.OffsetDateTime;

public class NsiSyncRunFilter {

    private int page;
    private int size = 20;
    private SyncRunStatus status;
    private SyncRunType runType;
    private String dictionaryIdentifier;
    private String dictionaryVersion;
    private OffsetDateTime dateFrom;
    private OffsetDateTime dateTo;
    private Boolean hasErrors;
    private String sort = "startedAt";
    private String direction = "desc";

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(page, 0);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        if (size <= 0) {
            this.size = 20;
        } else {
            this.size = Math.min(size, 100);
        }
    }

    public SyncRunStatus getStatus() {
        return status;
    }

    public void setStatus(SyncRunStatus status) {
        this.status = status;
    }

    public SyncRunType getRunType() {
        return runType;
    }

    public void setRunType(SyncRunType runType) {
        this.runType = runType;
    }

    public String getDictionaryIdentifier() {
        return dictionaryIdentifier;
    }

    public void setDictionaryIdentifier(String dictionaryIdentifier) {
        this.dictionaryIdentifier = trimToNull(dictionaryIdentifier);
    }

    public String getDictionaryVersion() {
        return dictionaryVersion;
    }

    public void setDictionaryVersion(String dictionaryVersion) {
        this.dictionaryVersion = trimToNull(dictionaryVersion);
    }

    public OffsetDateTime getDateFrom() {
        return dateFrom;
    }

    public void setDateFrom(OffsetDateTime dateFrom) {
        this.dateFrom = dateFrom;
    }

    public OffsetDateTime getDateTo() {
        return dateTo;
    }

    public void setDateTo(OffsetDateTime dateTo) {
        this.dateTo = dateTo;
    }

    public Boolean getHasErrors() {
        return hasErrors;
    }

    public void setHasErrors(Boolean hasErrors) {
        this.hasErrors = hasErrors;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        String value = trimToNull(sort);
        this.sort = value == null ? "startedAt" : value;
    }

    public String getDirection() {
        return direction;
    }

    public void setDirection(String direction) {
        String value = trimToNull(direction);
        this.direction = value == null ? "desc" : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
