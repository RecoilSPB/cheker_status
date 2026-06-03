package ru.spb.reshenie.chekerstatus.domain.nsi;

import java.util.Collections;
import java.util.List;

public class DataPage {

    private final int pageNumber;
    private final int pageSize;
    private final int total;
    private final List<RecordPayload> records;

    public DataPage(int pageNumber, int pageSize, int total, List<RecordPayload> records) {
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.total = total;
        this.records = Collections.unmodifiableList(records);
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public int getTotal() {
        return total;
    }

    public List<RecordPayload> getRecords() {
        return records;
    }
}
