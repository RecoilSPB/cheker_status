package ru.spb.reshenie.chekerstatus.gitlab;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class GitCommitFileRepositoryTest {

    @Test
    void fileStateUpdateIsGuardedByCommitOrder() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        GitCommitFileRepository repository = new GitCommitFileRepository(jdbcTemplate);

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
        verify(jdbcTemplate, times(2)).update(sql.capture(), any(Object[].class));

        String fileStateSql = sql.getAllValues().get(1);
        assertThat(fileStateSql).contains("last_committed_date");
        assertThat(fileStateSql).contains("last_commit_row_id");
        assertThat(fileStateSql).contains("EXCLUDED.last_committed_date > nsi_document_git_file_state.last_committed_date");
    }
}
