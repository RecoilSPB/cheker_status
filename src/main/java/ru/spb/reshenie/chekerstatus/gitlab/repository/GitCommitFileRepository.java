package ru.spb.reshenie.chekerstatus.gitlab.repository;

import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitFileContentSnapshot;
import ru.spb.reshenie.chekerstatus.gitlab.model.SavedGitCommitFileChange;
import ru.spb.reshenie.chekerstatus.gitlab.model.StoredGitCommitFileChange;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.spb.reshenie.chekerstatus.gitlab.service.FileDiffBackfillScope;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@Repository
public class GitCommitFileRepository {

    private final JdbcTemplate jdbcTemplate;

    public GitCommitFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean saveFileChange(GitCommitFileChange change) {
        return saveFileChangeRecord(change).isInserted();
    }

    @Transactional
    public SavedGitCommitFileChange saveFileChangeRecord(GitCommitFileChange change) {
        SavedGitCommitFileChange saved = jdbcTemplate.queryForObject(
                "INSERT INTO nsi_document_git_commit_file (" +
                        "git_link_id, commit_id, commit_sha, parent_sha, old_path, new_path, file_path, " +
                        "change_type, renamed_file, new_file, deleted_file, diff, diff_too_large, " +
                        "content_after, content_after_object_key, content_after_sha256, content_after_size, " +
                        "content_before, content_before_object_key, content_before_sha256, content_before_size, " +
                        "content_fetch_status, content_fetch_error, committed_date" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (commit_id, old_path, new_path) DO UPDATE SET " +
                        "git_link_id = EXCLUDED.git_link_id, " +
                        "commit_sha = EXCLUDED.commit_sha, " +
                        "parent_sha = EXCLUDED.parent_sha, " +
                        "file_path = EXCLUDED.file_path, " +
                        "change_type = EXCLUDED.change_type, " +
                        "renamed_file = EXCLUDED.renamed_file, " +
                        "new_file = EXCLUDED.new_file, " +
                        "deleted_file = EXCLUDED.deleted_file, " +
                        "diff = EXCLUDED.diff, " +
                        "diff_too_large = EXCLUDED.diff_too_large, " +
                        "content_after = EXCLUDED.content_after, " +
                        "content_after_object_key = EXCLUDED.content_after_object_key, " +
                        "content_after_sha256 = EXCLUDED.content_after_sha256, " +
                        "content_after_size = EXCLUDED.content_after_size, " +
                        "content_before = EXCLUDED.content_before, " +
                        "content_before_object_key = EXCLUDED.content_before_object_key, " +
                        "content_before_sha256 = EXCLUDED.content_before_sha256, " +
                        "content_before_size = EXCLUDED.content_before_size, " +
                        "content_fetch_status = EXCLUDED.content_fetch_status, " +
                        "content_fetch_error = EXCLUDED.content_fetch_error, " +
                        "committed_date = EXCLUDED.committed_date, " +
                        "updated_at = now() " +
                        "RETURNING id, (xmax = 0) AS inserted",
                (rs, rowNum) -> new SavedGitCommitFileChange(rs.getLong("id"), rs.getBoolean("inserted")),
                change.getGitLinkId(),
                change.getCommitRowId(),
                change.getCommitSha(),
                change.getParentSha(),
                change.getOldPath(),
                change.getNewPath(),
                change.getFilePath(),
                change.getChangeType(),
                change.isRenamedFile(),
                change.isNewFile(),
                change.isDeletedFile(),
                change.getDiff(),
                change.isDiffTooLarge(),
                content(change.getContentAfter()),
                objectKey(change.getContentAfter()),
                sha256(change.getContentAfter()),
                size(change.getContentAfter()),
                content(change.getContentBefore()),
                objectKey(change.getContentBefore()),
                sha256(change.getContentBefore()),
                size(change.getContentBefore()),
                change.getContentFetchStatus(),
                change.getContentFetchError(),
                toTimestamp(change.getCommittedDate())
        );

        updateFileState(change);
        return saved;
    }

    public StoredGitCommitFileChange findStoredFileChange(long fileChangeId) {
        return jdbcTemplate.queryForObject(
                selectStoredFileChangeSql() + " WHERE f.id = ?",
                (rs, rowNum) -> storedFileChange(rs),
                fileChangeId
        );
    }

    public StoredGitCommitFileChange findStoredFileChange(long documentId, long fileChangeId) {
        return jdbcTemplate.queryForObject(
                selectStoredFileChangeSql() + " WHERE f.id = ? AND f.git_link_id = ?",
                (rs, rowNum) -> storedFileChange(rs),
                fileChangeId,
                documentId
        );
    }

    public List<Long> findFileChangeIdsForDiffBackfill(FileDiffBackfillScope scope, Long documentId, int limit) {
        StringBuilder sql = new StringBuilder();
        sql.append("SELECT f.id FROM nsi_document_git_commit_file f ")
                .append("LEFT JOIN nsi_document_git_commit_file_diff d ON d.file_change_id = f.id ")
                .append("WHERE 1 = 1 ");
        List<Object> args = new ArrayList<Object>();
        if (documentId != null) {
            sql.append("AND f.git_link_id = ? ");
            args.add(documentId);
        }
        if (scope == FileDiffBackfillScope.MISSING) {
            sql.append("AND d.id IS NULL ");
        } else if (scope == FileDiffBackfillScope.FAILED) {
            sql.append("AND d.status = 'FAILED' ");
        }
        sql.append("ORDER BY f.committed_date NULLS LAST, f.id LIMIT ?");
        args.add(Integer.valueOf(limit));
        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> Long.valueOf(rs.getLong("id")), args.toArray());
    }

    private void updateFileState(GitCommitFileChange change) {
        GitFileContentSnapshot after = change.getContentAfter();
        jdbcTemplate.update(
                "INSERT INTO nsi_document_git_file_state (" +
                        "git_link_id, file_path, last_commit_sha, content, content_object_key, content_sha256, content_size, deleted, " +
                        "last_committed_date, last_commit_row_id" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (git_link_id, file_path) DO UPDATE SET " +
                        "last_commit_sha = EXCLUDED.last_commit_sha, " +
                        "content = EXCLUDED.content, " +
                        "content_object_key = EXCLUDED.content_object_key, " +
                        "content_sha256 = EXCLUDED.content_sha256, " +
                        "content_size = EXCLUDED.content_size, " +
                        "deleted = EXCLUDED.deleted, " +
                        "last_committed_date = EXCLUDED.last_committed_date, " +
                        "last_commit_row_id = EXCLUDED.last_commit_row_id, " +
                        "updated_at = now() " +
                        "WHERE nsi_document_git_file_state.last_committed_date IS NULL " +
                        "OR EXCLUDED.last_committed_date > nsi_document_git_file_state.last_committed_date " +
                        "OR (EXCLUDED.last_committed_date = nsi_document_git_file_state.last_committed_date " +
                        "AND COALESCE(EXCLUDED.last_commit_row_id, 0) >= COALESCE(nsi_document_git_file_state.last_commit_row_id, 0))",
                change.getGitLinkId(),
                change.getFilePath(),
                change.getCommitSha(),
                change.isDeletedFile() ? null : content(after),
                change.isDeletedFile() ? null : objectKey(after),
                change.isDeletedFile() ? null : sha256(after),
                change.isDeletedFile() ? null : size(after),
                change.isDeletedFile(),
                toTimestamp(change.getCommittedDate()),
                change.getCommitRowId()
        );
    }

    private String content(GitFileContentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getContent();
    }

    private String sha256(GitFileContentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getSha256();
    }

    private String objectKey(GitFileContentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getObjectKey();
    }

    private Long size(GitFileContentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getSize();
    }

    private Timestamp toTimestamp(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Timestamp.from(dateTime.toInstant());
    }

    private String selectStoredFileChangeSql() {
        return "SELECT f.id, f.git_link_id, f.commit_sha, f.parent_sha, f.old_path, f.new_path, f.file_path, " +
                "f.change_type, f.renamed_file, f.new_file, f.deleted_file, f.diff, f.diff_too_large, " +
                "f.content_fetch_status, f.content_fetch_error, " +
                "f.content_before, f.content_before_object_key, f.content_before_sha256, f.content_before_size, " +
                "f.content_after, f.content_after_object_key, f.content_after_sha256, f.content_after_size, " +
                "f.committed_date " +
                "FROM nsi_document_git_commit_file f";
    }

    private StoredGitCommitFileChange storedFileChange(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new StoredGitCommitFileChange(
                rs.getLong("id"),
                rs.getLong("git_link_id"),
                rs.getString("commit_sha"),
                rs.getString("parent_sha"),
                rs.getString("old_path"),
                rs.getString("new_path"),
                rs.getString("file_path"),
                rs.getString("change_type"),
                rs.getBoolean("renamed_file"),
                rs.getBoolean("new_file"),
                rs.getBoolean("deleted_file"),
                rs.getString("diff"),
                rs.getBoolean("diff_too_large"),
                rs.getString("content_fetch_status"),
                rs.getString("content_fetch_error"),
                snapshot(rs.getString("content_before"),
                        rs.getString("content_before_object_key"),
                        rs.getString("content_before_sha256"),
                        longOrNull(rs.getObject("content_before_size"))),
                snapshot(rs.getString("content_after"),
                        rs.getString("content_after_object_key"),
                        rs.getString("content_after_sha256"),
                        longOrNull(rs.getObject("content_after_size"))),
                toOffsetDateTime(rs.getTimestamp("committed_date"))
        );
    }

    private GitFileContentSnapshot snapshot(String content, String objectKey, String sha256, Long size) {
        if (content == null && objectKey == null && sha256 == null && size == null) {
            return null;
        }
        return new GitFileContentSnapshot(content, objectKey, sha256, size);
    }

    private OffsetDateTime toOffsetDateTime(Timestamp timestamp) {
        if (timestamp == null) {
            return null;
        }
        return OffsetDateTime.ofInstant(timestamp.toInstant(), ZoneOffset.UTC);
    }

    private Long longOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            return Long.valueOf(((Number) value).longValue());
        }
        return Long.valueOf(value.toString());
    }
}
