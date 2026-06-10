package ru.spb.reshenie.chekerstatus.gitlab.diff;

import java.util.Collections;
import java.util.List;

public class StructuredFileDiffArtifact {

    private final String diffType;
    private final String formatFamily;
    private final StructuredFileDiffSummary summary;
    private final List<StructuredDiffRow> rows;
    private final List<StructuredDiffMetadataEntry> metadataEntries;

    public StructuredFileDiffArtifact(String diffType,
                                      String formatFamily,
                                      StructuredFileDiffSummary summary,
                                      List<StructuredDiffRow> rows,
                                      List<StructuredDiffMetadataEntry> metadataEntries) {
        this.diffType = diffType;
        this.formatFamily = formatFamily;
        this.summary = summary;
        this.rows = rows == null ? List.of() : Collections.unmodifiableList(rows);
        this.metadataEntries = metadataEntries == null ? List.of() : Collections.unmodifiableList(metadataEntries);
    }

    public String getDiffType() {
        return diffType;
    }

    public String getFormatFamily() {
        return formatFamily;
    }

    public StructuredFileDiffSummary getSummary() {
        return summary;
    }

    public List<StructuredDiffRow> getRows() {
        return rows;
    }

    public List<StructuredDiffMetadataEntry> getMetadataEntries() {
        return metadataEntries;
    }

    public boolean hasRows() {
        return !rows.isEmpty();
    }

    public boolean hasMetadataEntries() {
        return !metadataEntries.isEmpty();
    }
}
