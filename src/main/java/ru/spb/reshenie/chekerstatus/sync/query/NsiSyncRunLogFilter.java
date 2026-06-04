package ru.spb.reshenie.chekerstatus.sync.query;

import ru.spb.reshenie.chekerstatus.sync.model.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunLogLevel;

public class NsiSyncRunLogFilter {

    private int page;
    private int size = 100;
    private SyncRunLogLevel level;
    private SyncErrorStage stage;

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        this.page = Math.max(0, page);
    }

    public int getSize() {
        return size;
    }

    public void setSize(int size) {
        this.size = size <= 0 ? 100 : Math.min(size, 500);
    }

    public SyncRunLogLevel getLevel() {
        return level;
    }

    public void setLevel(SyncRunLogLevel level) {
        this.level = level;
    }

    public SyncErrorStage getStage() {
        return stage;
    }

    public void setStage(SyncErrorStage stage) {
        this.stage = stage;
    }
}
