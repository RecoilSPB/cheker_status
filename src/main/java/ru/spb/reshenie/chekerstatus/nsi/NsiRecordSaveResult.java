package ru.spb.reshenie.chekerstatus.nsi;

public class NsiRecordSaveResult {

    private final int total;
    private final int inserted;
    private final int updated;
    private final int deactivated;

    public NsiRecordSaveResult(int total, int inserted, int updated, int deactivated) {
        this.total = total;
        this.inserted = inserted;
        this.updated = updated;
        this.deactivated = deactivated;
    }

    public int getTotal() {
        return total;
    }

    public int getInserted() {
        return inserted;
    }

    public int getUpdated() {
        return updated;
    }

    public int getDeactivated() {
        return deactivated;
    }
}
