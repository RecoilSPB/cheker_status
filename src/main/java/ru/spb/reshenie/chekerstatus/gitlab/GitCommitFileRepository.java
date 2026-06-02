package ru.spb.reshenie.chekerstatus.gitlab;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.OffsetDateTime;

@Repository
public class GitCommitFileRepository {

    private final JdbcTemplate jdbcTemplate;

    public GitCommitFileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public void saveFileChange(GitCommitFileChange change) {
        jdbcTemplate.update(
                "INSERT INTO nsi_document_git_commit_file (" +
                        "git_link_id, commit_id, commit_sha, parent_sha, old_path, new_path, file_path, " +
                        "change_type, renamed_file, new_file, deleted_file, diff, diff_too_large, " +
                        "content_after, content_after_sha256, content_after_size, " +
                        "content_before, content_before_sha256, content_before_size, " +
                        "content_fetch_status, content_fetch_error, committed_date" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
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
                        "content_after_sha256 = EXCLUDED.content_after_sha256, " +
                        "content_after_size = EXCLUDED.content_after_size, " +
                        "content_before = EXCLUDED.content_before, " +
                        "content_before_sha256 = EXCLUDED.content_before_sha256, " +
                        "content_before_size = EXCLUDED.content_before_size, " +
                        "content_fetch_status = EXCLUDED.content_fetch_status, " +
                        "content_fetch_error = EXCLUDED.content_fetch_error, " +
                        "committed_date = EXCLUDED.committed_date, " +
                        "updated_at = now()",
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
                sha256(change.getContentAfter()),
                size(change.getContentAfter()),
                content(change.getContentBefore()),
                sha256(change.getContentBefore()),
                size(change.getContentBefore()),
                change.getContentFetchStatus(),
                change.getContentFetchError(),
                toTimestamp(change.getCommittedDate())
        );

        updateFileState(change);
    }

    private void updateFileState(GitCommitFileChange change) {
        GitFileContentSnapshot after = change.getContentAfter();
        jdbcTemplate.update(
                "INSERT INTO nsi_document_git_file_state (" +
                        "git_link_id, file_path, last_commit_sha, content, content_sha256, content_size, deleted" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT (git_link_id, file_path) DO UPDATE SET " +
                        "last_commit_sha = EXCLUDED.last_commit_sha, " +
                        "content = EXCLUDED.content, " +
                        "content_sha256 = EXCLUDED.content_sha256, " +
                        "content_size = EXCLUDED.content_size, " +
                        "deleted = EXCLUDED.deleted, " +
                        "updated_at = now()",
                change.getGitLinkId(),
                change.getFilePath(),
                change.getCommitSha(),
                change.isDeletedFile() ? null : content(after),
                change.isDeletedFile() ? null : sha256(after),
                change.isDeletedFile() ? null : size(after),
                change.isDeletedFile()
        );
    }

    private String content(GitFileContentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getContent();
    }

    private String sha256(GitFileContentSnapshot snapshot) {
        return snapshot == null ? null : snapshot.getSha256();
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
}
