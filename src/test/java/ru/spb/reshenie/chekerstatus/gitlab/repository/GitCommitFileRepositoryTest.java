package ru.spb.reshenie.chekerstatus.gitlab.repository;

import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitCommitFileRepositoryTest {

    @Test
    void fileStateUpdateIsGuardedByCommitOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GitCommitFileRepository repository = new GitCommitFileRepository(jdbcTemplate);
        when(jdbcTemplate.queryForObject(any(String.class), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class)))
                .thenReturn(new ru.spb.reshenie.chekerstatus.gitlab.model.SavedGitCommitFileChange(10L, true));

        repository.saveFileChange(new GitCommitFileChange(
                1L,
                2L,
                "commit-sha",
                "parent-sha",
                "old.xml",
                "new.xml",
                "new.xml",
                "modified",
                false,
                false,
                false,
                "diff",
                false,
                new GitFileContentSnapshot("after", "hash-after", 5L),
                new GitFileContentSnapshot("before", "hash-before", 6L),
                "SUCCESS",
                null,
                OffsetDateTime.of(2026, 6, 3, 1, 0, 0, 0, ZoneOffset.UTC)
        ));

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForObject(sql.capture(), any(org.springframework.jdbc.core.RowMapper.class), any(Object[].class));
        assertThat(sql.getValue()).contains("content_after_object_key");
        assertThat(sql.getValue()).contains("content_before_object_key");
        assertThat(sql.getValue()).contains("RETURNING id, (xmax = 0) AS inserted");

        ArgumentCaptor<String> updateSql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(updateSql.capture(), any(Object[].class));
        String fileStateSql = updateSql.getValue();
        assertThat(fileStateSql).contains("content_object_key");
        assertThat(fileStateSql).contains("last_committed_date");
        assertThat(fileStateSql).contains("last_commit_row_id");
        assertThat(fileStateSql).contains("EXCLUDED.last_committed_date > nsi_document_git_file_state.last_committed_date");
    }
}
