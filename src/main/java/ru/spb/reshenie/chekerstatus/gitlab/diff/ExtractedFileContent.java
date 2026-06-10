package ru.spb.reshenie.chekerstatus.gitlab.diff;

import java.util.Collections;
import java.util.List;

public class ExtractedFileContent {

    private final boolean available;
    private final List<String> lines;

    private ExtractedFileContent(boolean available, List<String> lines) {
        this.available = available;
        this.lines = lines == null ? List.of() : Collections.unmodifiableList(lines);
    }

    public static ExtractedFileContent unavailable() {
        return new ExtractedFileContent(false, List.of());
    }

    public static ExtractedFileContent available(List<String> lines) {
        return new ExtractedFileContent(true, lines);
    }

    public boolean isAvailable() {
        return available;
    }

    public List<String> getLines() {
        return lines;
    }
}
