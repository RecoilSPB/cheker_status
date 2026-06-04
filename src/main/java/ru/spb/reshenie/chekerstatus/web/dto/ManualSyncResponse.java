package ru.spb.reshenie.chekerstatus.web.dto;

public class ManualSyncResponse {

    private final long syncRunId;
    private final String status;

    public ManualSyncResponse(long syncRunId, String status) {
        this.syncRunId = syncRunId;
        this.status = status;
    }

    public long getSyncRunId() {
        return syncRunId;
    }

    public String getStatus() {
        return status;
    }
}
