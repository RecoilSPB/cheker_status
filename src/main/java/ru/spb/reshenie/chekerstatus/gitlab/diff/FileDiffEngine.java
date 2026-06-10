package ru.spb.reshenie.chekerstatus.gitlab.diff;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FileDiffEngine {

    private final List<FileDiffHandler> handlers;

    public FileDiffEngine(List<FileDiffHandler> handlers) {
        this.handlers = handlers;
    }

    public StructuredFileDiffArtifact buildDiff(FileDiffSource source) {
        FileDiffHandler handler = selectHandler(source);
        ExtractedFileContent before = handler.extract(source.getBefore());
        ExtractedFileContent after = handler.extract(source.getAfter());
        return handler.buildDiff(source, before, after);
    }

    FileDiffHandler selectHandler(FileDiffSource source) {
        for (FileDiffHandler handler : handlers) {
            if (handler.supports(source)) {
                return handler;
            }
        }
        throw new IllegalStateException("No file diff handler found for " + source.getPrimaryPath());
    }
}
