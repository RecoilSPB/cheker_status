package ru.spb.reshenie.chekerstatus.gitlab.diff;

public class StructuredDiffMetadataEntry {

    private final String label;
    private final String beforeValue;
    private final String afterValue;
    private final String status;

    public StructuredDiffMetadataEntry(String label, String beforeValue, String afterValue, String status) {
        this.label = label;
        this.beforeValue = beforeValue;
        this.afterValue = afterValue;
        this.status = status;
    }

    public String getLabel() {
        return label;
    }

    public String getBeforeValue() {
        return beforeValue;
    }

    public String getAfterValue() {
        return afterValue;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusCss() {
        return "diff-row-" + status.toLowerCase();
    }
}
