package ru.spb.reshenie.chekerstatus.gitlab.diff;

public interface FileDiffHandler {

    boolean supports(FileDiffSource source);

    ExtractedFileContent extract(FileDiffSource.Version version);

    StructuredFileDiffArtifact buildDiff(FileDiffSource source,
                                         ExtractedFileContent before,
                                         ExtractedFileContent after);
}
