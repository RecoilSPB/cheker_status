package ru.spb.reshenie.chekerstatus.gitlab.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitCommitFileDiff;

@Repository
public class GitFileDiffRepository {

    private final JdbcTemplate jdbcTemplate;

    public GitFileDiffRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void save(long fileChangeId,
                     long gitLinkId,
                     String status,
                     String diffType,
                     String formatFamily,
                     String summaryJson,
                     String artifactObjectKey,
                     String error) {
        jdbcTemplate.update(
                "INSERT INTO nsi_document_git_commit_file_diff (" +
                        "file_change_id, git_link_id, status, diff_type, format_family, summary_json, " +
                        "artifact_object_key, error" +
                        ") VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?) " +
                        "ON CONFLICT (file_change_id) DO UPDATE SET " +
                        "git_link_id = EXCLUDED.git_link_id, " +
                        "status = EXCLUDED.status, " +
                        "diff_type = EXCLUDED.diff_type, " +
                        "format_family = EXCLUDED.format_family, " +
                        "summary_json = EXCLUDED.summary_json, " +
                        "artifact_object_key = EXCLUDED.artifact_object_key, " +
                        "error = EXCLUDED.error, " +
                        "updated_at = now()",
                fileChangeId,
                gitLinkId,
                status,
                diffType,
                formatFamily,
                summaryJson,
                artifactObjectKey,
                error
        );
    }

    public StoredGitCommitFileDiff findByDocumentAndFileChange(long documentId, long fileChangeId) {
        return jdbcTemplate.queryForObject(
                "SELECT d.file_change_id, d.status, d.diff_type, d.format_family, d.summary_json::text AS summary_json, " +
                        "d.artifact_object_key, d.error " +
                        "FROM nsi_document_git_commit_file_diff d " +
                        "JOIN nsi_document_git_commit_file f ON f.id = d.file_change_id " +
                        "WHERE d.file_change_id = ? AND f.git_link_id = ?",
                (rs, rowNum) -> new StoredGitCommitFileDiff(
                        rs.getLong("file_change_id"),
                        rs.getString("status"),
                        rs.getString("diff_type"),
                        rs.getString("format_family"),
                        rs.getString("summary_json"),
                        rs.getString("artifact_object_key"),
                        rs.getString("error")
                ),
                fileChangeId,
                documentId
        );
    }
}
