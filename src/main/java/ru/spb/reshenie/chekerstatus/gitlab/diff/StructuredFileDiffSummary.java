package ru.spb.reshenie.chekerstatus.gitlab.diff;

public class StructuredFileDiffSummary {

    private final int unchangedLines;
    private final int addedLines;
    private final int removedLines;
    private final int modifiedLines;
    private final int metadataChanges;
    private final String changeStatus;
    private final String message;

    public StructuredFileDiffSummary(int unchangedLines,
                                     int addedLines,
                                     int removedLines,
                                     int modifiedLines,
                                     int metadataChanges,
                                     String changeStatus,
                                     String message) {
        this.unchangedLines = unchangedLines;
        this.addedLines = addedLines;
        this.removedLines = removedLines;
        this.modifiedLines = modifiedLines;
        this.metadataChanges = metadataChanges;
        this.changeStatus = changeStatus;
        this.message = message;
    }

    public int getUnchangedLines() {
        return unchangedLines;
    }

    public int getAddedLines() {
        return addedLines;
    }

    public int getRemovedLines() {
        return removedLines;
    }

    public int getModifiedLines() {
        return modifiedLines;
    }

    public int getMetadataChanges() {
        return metadataChanges;
    }

    public String getChangeStatus() {
        return changeStatus;
    }

    public String getMessage() {
        return message;
    }

    public String asText() {
        if (metadataChanges > 0 && addedLines == 0 && removedLines == 0 && modifiedLines == 0) {
            return metadataChanges + " metadata changes";
        }
        if (message != null && !message.isBlank()
                && addedLines == 0 && removedLines == 0 && modifiedLines == 0 && unchangedLines == 0) {
            return message;
        }
        return "changed " + modifiedLines + ", added " + addedLines + ", removed " + removedLines;
    }
}
