package ru.spb.reshenie.chekerstatus.gitlab.diff;

public class StructuredDiffRow {

    private final Integer beforeLineNumber;
    private final String beforeText;
    private final Integer afterLineNumber;
    private final String afterText;
    private final String status;

    public StructuredDiffRow(Integer beforeLineNumber,
                             String beforeText,
                             Integer afterLineNumber,
                             String afterText,
                             String status) {
        this.beforeLineNumber = beforeLineNumber;
        this.beforeText = beforeText;
        this.afterLineNumber = afterLineNumber;
        this.afterText = afterText;
        this.status = status;
    }

    public Integer getBeforeLineNumber() {
        return beforeLineNumber;
    }

    public String getBeforeText() {
        return beforeText;
    }

    public Integer getAfterLineNumber() {
        return afterLineNumber;
    }

    public String getAfterText() {
        return afterText;
    }

    public String getStatus() {
        return status;
    }

    public String getStatusCss() {
        return "diff-row-" + status.toLowerCase();
    }
}
