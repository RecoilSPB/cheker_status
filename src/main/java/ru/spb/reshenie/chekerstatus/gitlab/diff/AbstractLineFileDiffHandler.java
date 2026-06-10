package ru.spb.reshenie.chekerstatus.gitlab.diff;

import com.github.difflib.DiffUtils;
import com.github.difflib.patch.AbstractDelta;
import com.github.difflib.patch.Chunk;
import com.github.difflib.patch.Patch;

import java.util.ArrayList;
import java.util.List;

abstract class AbstractLineFileDiffHandler implements FileDiffHandler {

    private final String diffType;
    private final String formatFamily;

    protected AbstractLineFileDiffHandler(String diffType, String formatFamily) {
        this.diffType = diffType;
        this.formatFamily = formatFamily;
    }

    @Override
    public StructuredFileDiffArtifact buildDiff(FileDiffSource source,
                                                ExtractedFileContent before,
                                                ExtractedFileContent after) {
        List<String> beforeLines = before == null || !before.isAvailable() ? List.of() : before.getLines();
        List<String> afterLines = after == null || !after.isAvailable() ? List.of() : after.getLines();
        Patch<String> patch = DiffUtils.diff(beforeLines, afterLines);
        List<StructuredDiffRow> rows = new ArrayList<StructuredDiffRow>();
        SummaryCounter counter = new SummaryCounter();
        int beforeIndex = 0;
        int afterIndex = 0;

        for (AbstractDelta<String> delta : patch.getDeltas()) {
            Chunk<String> sourceChunk = delta.getSource();
            Chunk<String> targetChunk = delta.getTarget();

            while (beforeIndex < sourceChunk.getPosition() && afterIndex < targetChunk.getPosition()) {
                counter.unchanged++;
                rows.add(new StructuredDiffRow(
                        Integer.valueOf(beforeIndex + 1),
                        beforeLines.get(beforeIndex),
                        Integer.valueOf(afterIndex + 1),
                        afterLines.get(afterIndex),
                        "UNCHANGED"
                ));
                beforeIndex++;
                afterIndex++;
            }

            if (!sourceChunk.getLines().isEmpty() && !targetChunk.getLines().isEmpty()) {
                pairModifiedRows(rows, counter, beforeIndex, afterIndex, sourceChunk.getLines(), targetChunk.getLines());
                beforeIndex += sourceChunk.getLines().size();
                afterIndex += targetChunk.getLines().size();
                continue;
            }

            if (!sourceChunk.getLines().isEmpty()) {
                for (String line : sourceChunk.getLines()) {
                    counter.removed++;
                    rows.add(new StructuredDiffRow(Integer.valueOf(beforeIndex + 1), line, null, null, "REMOVED"));
                    beforeIndex++;
                }
            }

            if (!targetChunk.getLines().isEmpty()) {
                for (String line : targetChunk.getLines()) {
                    counter.added++;
                    rows.add(new StructuredDiffRow(null, null, Integer.valueOf(afterIndex + 1), line, "ADDED"));
                    afterIndex++;
                }
            }
        }

        while (beforeIndex < beforeLines.size() && afterIndex < afterLines.size()) {
            counter.unchanged++;
            rows.add(new StructuredDiffRow(
                    Integer.valueOf(beforeIndex + 1),
                    beforeLines.get(beforeIndex),
                    Integer.valueOf(afterIndex + 1),
                    afterLines.get(afterIndex),
                    "UNCHANGED"
            ));
            beforeIndex++;
            afterIndex++;
        }
        while (beforeIndex < beforeLines.size()) {
            counter.removed++;
            rows.add(new StructuredDiffRow(Integer.valueOf(beforeIndex + 1), beforeLines.get(beforeIndex),
                    null, null, "REMOVED"));
            beforeIndex++;
        }
        while (afterIndex < afterLines.size()) {
            counter.added++;
            rows.add(new StructuredDiffRow(null, null, Integer.valueOf(afterIndex + 1), afterLines.get(afterIndex),
                    "ADDED"));
            afterIndex++;
        }

        String changeStatus = counter.modified > 0 || counter.added > 0 || counter.removed > 0
                ? "MODIFIED"
                : "UNCHANGED";
        StructuredFileDiffSummary summary = new StructuredFileDiffSummary(
                counter.unchanged,
                counter.added,
                counter.removed,
                counter.modified,
                0,
                changeStatus,
                null
        );
        return new StructuredFileDiffArtifact(diffType, formatFamily, summary, rows, List.of());
    }

    private void pairModifiedRows(List<StructuredDiffRow> rows,
                                  SummaryCounter counter,
                                  int beforeIndex,
                                  int afterIndex,
                                  List<String> beforeLines,
                                  List<String> afterLines) {
        int pairs = Math.min(beforeLines.size(), afterLines.size());
        for (int i = 0; i < pairs; i++) {
            counter.modified++;
            rows.add(new StructuredDiffRow(
                    Integer.valueOf(beforeIndex + i + 1),
                    beforeLines.get(i),
                    Integer.valueOf(afterIndex + i + 1),
                    afterLines.get(i),
                    "MODIFIED"
            ));
        }
        for (int i = pairs; i < beforeLines.size(); i++) {
            counter.removed++;
            rows.add(new StructuredDiffRow(
                    Integer.valueOf(beforeIndex + i + 1),
                    beforeLines.get(i),
                    null,
                    null,
                    "REMOVED"
            ));
        }
        for (int i = pairs; i < afterLines.size(); i++) {
            counter.added++;
            rows.add(new StructuredDiffRow(
                    null,
                    null,
                    Integer.valueOf(afterIndex + i + 1),
                    afterLines.get(i),
                    "ADDED"
            ));
        }
    }

    private static class SummaryCounter {
        private int unchanged;
        private int added;
        private int removed;
        private int modified;
    }
}
