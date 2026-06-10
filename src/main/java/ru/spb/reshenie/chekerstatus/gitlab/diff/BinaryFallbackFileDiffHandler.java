package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Order(100)
public class BinaryFallbackFileDiffHandler implements FileDiffHandler {

    @Override
    public boolean supports(FileDiffSource source) {
        return true;
    }

    @Override
    public ExtractedFileContent extract(FileDiffSource.Version version) {
        return ExtractedFileContent.unavailable();
    }

    @Override
    public StructuredFileDiffArtifact buildDiff(FileDiffSource source,
                                                ExtractedFileContent before,
                                                ExtractedFileContent after) {
        String formatFamily = FileTypeClassifier.formatFamily(source.getPrimaryPath(), firstAvailableBytes(source));
        String changeStatus = changeStatus(source);
        List<StructuredDiffMetadataEntry> metadataEntries = new ArrayList<StructuredDiffMetadataEntry>();
        metadataEntries.add(entry("GitLab change type", source.getChangeType(), source.getChangeType()));
        metadataEntries.add(entry("Format family", formatFamily, formatFamily));
        metadataEntries.add(entry("Media type",
                mediaType(source.getBefore().getPath()),
                mediaType(source.getAfter().getPath())));
        metadataEntries.add(entry("Path",
                source.getBefore().getPath(),
                source.getAfter().getPath()));
        metadataEntries.add(entry("Size",
                value(source.getBefore().getSize()),
                value(source.getAfter().getSize())));
        metadataEntries.add(entry("SHA-256",
                source.getBefore().getSha256(),
                source.getAfter().getSha256()));
        metadataEntries.add(entry("Object key",
                source.getBefore().getObjectKey(),
                source.getAfter().getObjectKey()));

        int changed = 0;
        for (StructuredDiffMetadataEntry entry : metadataEntries) {
            if (!"UNCHANGED".equals(entry.getStatus())) {
                changed++;
            }
        }
        StructuredFileDiffSummary summary = new StructuredFileDiffSummary(
                0,
                0,
                0,
                0,
                changed,
                changeStatus,
                "Binary metadata diff"
        );
        return new StructuredFileDiffArtifact("METADATA", formatFamily, summary, List.of(), metadataEntries);
    }

    private byte[] firstAvailableBytes(FileDiffSource source) {
        byte[] after = source.getAfter().readableBytes();
        if (after != null) {
            return after;
        }
        return source.getBefore().readableBytes();
    }

    private StructuredDiffMetadataEntry entry(String label, String before, String after) {
        return new StructuredDiffMetadataEntry(label, before, after, valueEquals(before, after) ? "UNCHANGED" : "MODIFIED");
    }

    private String mediaType(String path) {
        MediaType mediaType = FileTypeClassifier.mediaType(path);
        return mediaType == null ? null : mediaType.toString();
    }

    private String value(Long value) {
        return value == null ? null : value.toString();
    }

    private boolean valueEquals(String before, String after) {
        if (before == null) {
            return after == null;
        }
        return before.equals(after);
    }

    private String changeStatus(FileDiffSource source) {
        boolean beforeExists = source.getBefore().exists();
        boolean afterExists = source.getAfter().exists();
        if (!beforeExists && afterExists) {
            return "ADDED";
        }
        if (beforeExists && !afterExists) {
            return "REMOVED";
        }
        if (valueEquals(source.getBefore().getSha256(), source.getAfter().getSha256())) {
            return "UNCHANGED";
        }
        if (source.getChangeType() != null && !source.getChangeType().isBlank()) {
            return source.getChangeType().toUpperCase();
        }
        return "MODIFIED";
    }
}
