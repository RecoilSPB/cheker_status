package ru.spb.reshenie.chekerstatus.gitlab.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.gitlab.diff.FileDiffEngine;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredDiffRow;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffArtifact;
import ru.spb.reshenie.chekerstatus.gitlab.diff.StructuredFileDiffSummary;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;
import ru.spb.reshenie.chekerstatus.gitlab.repository.GitFileDiffRepository;
import ru.spb.reshenie.chekerstatus.gitlab.storage.GitFileStorage;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitFileDiffProcessorTest {

    @Test
    void storesStructuredArtifactOnSuccess() {
        FileDiffEngine engine = mock(FileDiffEngine.class);
        GitFileDiffRepository repository = mock(GitFileDiffRepository.class);
        GitFileStorage storage = mock(GitFileStorage.class);
        when(engine.buildDiff(any())).thenReturn(new StructuredFileDiffArtifact(
                "TEXT",
                "TEXT",
                new StructuredFileDiffSummary(1, 1, 0, 1, 0, "MODIFIED", null),
                Collections.singletonList(new StructuredDiffRow(1, "before", 1, "after", "MODIFIED")),
                Collections.emptyList()
        ));
        when(storage.storeArtifact(eq(5L), eq(10L), eq("path/file.xml"), any(byte[].class)))
                .thenReturn("diff/key.json");

        GitFileDiffProcessor processor = new GitFileDiffProcessor(engine, repository, storage, new ObjectMapper());
        GitFileDiffProcessResult result = processor.process(10L, fileChange("SUCCESS", null));

        assertThat(result.hasError()).isFalse();
        verify(repository).save(eq(10L), eq(5L), eq("SUCCESS"), eq("TEXT"), eq("TEXT"),
                any(String.class), eq("diff/key.json"), eq(null));
    }

    @Test
    void persistsFailureWithoutArtifactWhenContentFetchAlreadyFailed() {
        FileDiffEngine engine = mock(FileDiffEngine.class);
        GitFileDiffRepository repository = mock(GitFileDiffRepository.class);
        GitFileStorage storage = mock(GitFileStorage.class);
        GitFileDiffProcessor processor = new GitFileDiffProcessor(engine, repository, storage, new ObjectMapper());

        GitFileDiffProcessResult result = processor.process(10L, fileChange("FAILED", "fetch failed"));

        assertThat(result.hasError()).isTrue();
        verify(repository).save(eq(10L), eq(5L), eq("FAILED"), eq("METADATA"), eq("TEXT"),
                eq(null), eq(null), eq("fetch failed"));
        verify(storage, never()).storeArtifact(anyLong(), anyLong(), any(String.class), any(byte[].class));
    }

    private GitCommitFileChange fileChange(String contentStatus, String error) {
        byte[] before = "<root>before</root>".getBytes(StandardCharsets.UTF_8);
        byte[] after = "<root>after</root>".getBytes(StandardCharsets.UTF_8);
        return new GitCommitFileChange(
                5L,
                6L,
                "commit-sha",
                "parent-sha",
                "path/file.xml",
                "path/file.xml",
                "path/file.xml",
                "modified",
                false,
                false,
                false,
                "@@ -1 +1 @@",
                false,
                new GitFileContentSnapshot("<root>after</root>", null, "sha-after", (long) after.length, after),
                new GitFileContentSnapshot("<root>before</root>", null, "sha-before", (long) before.length, before),
                contentStatus,
                error,
                OffsetDateTime.of(2026, 6, 9, 12, 0, 0, 0, ZoneOffset.UTC)
        );
    }
}
