package ru.spb.reshenie.chekerstatus.presentation.web;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
public class UiRepository {

    private final JdbcTemplate jdbcTemplate;

    public UiRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public UiModels.DashboardStats dashboardStats() {
        return jdbcTemplate.queryForObject(
                "SELECT " +
                        "(SELECT count(*) FROM nsi_dictionary) AS dictionaries, " +
                        "(SELECT count(*) FROM nsi_document_git_link WHERE active = true) AS documents, " +
                        "(SELECT count(*) FROM nsi_document_git_commit) AS commits, " +
                        "(SELECT count(*) FROM nsi_document_git_commit_file) AS file_changes, " +
                        "(SELECT count(*) FROM nsi_document_git_link WHERE last_sync_status = 'ERROR') + " +
                        "(SELECT count(*) FROM nsi_document_git_commit_file WHERE content_fetch_status = 'FAILED') AS errors, " +
                        "(SELECT max(last_sync_at) FROM nsi_document_git_link) AS last_sync_at",
                (rs, rowNum) -> new UiModels.DashboardStats(
                        rs.getLong("dictionaries"),
                        rs.getLong("documents"),
                        rs.getLong("commits"),
                        rs.getLong("file_changes"),
                        rs.getLong("errors"),
                        toOffsetDateTime(rs.getTimestamp("last_sync_at"))
                )
        );
    }

    public List<UiModels.DocumentSummary> documents(String query) {
        return documents(query, "");
    }

    public List<UiModels.DocumentSummary> documents(String query, String status) {
        String normalizedQuery = query == null ? "" : query.trim();
        String normalizedStatus = status == null ? "" : status.trim();
        String like = "%" + normalizedQuery.toLowerCase() + "%";
        return jdbcTemplate.query(
                "SELECT l.id, l.document_oid, COALESCE(NULLIF(l.document_name, ''), l.document_oid, 'Документ без названия') AS document_name, " +
                        "l.project_path, l.tree_ref, l.last_sync_status, l.last_sync_error, l.last_sync_at, " +
                        "count(DISTINCT c.id) AS commit_count, " +
                        "count(f.id) AS file_change_count, " +
                        "sum(CASE WHEN f.content_fetch_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_file_count " +
                        "FROM nsi_document_git_link l " +
                        "LEFT JOIN nsi_document_git_commit c ON c.document_git_link_id = l.id " +
                        "LEFT JOIN nsi_document_git_commit_file f ON f.commit_id = c.id " +
                        "WHERE l.active = true " +
                        "AND (? = '' OR lower(COALESCE(l.document_name, '')) LIKE ? " +
                        "OR lower(COALESCE(l.document_oid, '')) LIKE ? " +
                        "OR lower(COALESCE(l.project_path, '')) LIKE ?) " +
                        "GROUP BY l.id, l.document_oid, l.document_name, l.project_path, l.tree_ref, " +
                        "l.last_sync_status, l.last_sync_error, l.last_sync_at " +
                        "HAVING ? = '' OR l.last_sync_status = ? " +
                        "OR (? = 'ERROR' AND sum(CASE WHEN f.content_fetch_status = 'FAILED' THEN 1 ELSE 0 END) > 0) " +
                        "ORDER BY lower(COALESCE(NULLIF(l.document_name, ''), l.document_oid, '')) " +
                        "LIMIT 300",
                (rs, rowNum) -> documentSummary(rs.getLong("id"),
                        rs.getString("document_oid"),
                        rs.getString("document_name"),
                        rs.getString("project_path"),
                        rs.getString("tree_ref"),
                        rs.getString("last_sync_status"),
                        rs.getString("last_sync_error"),
                        rs.getTimestamp("last_sync_at"),
                        rs.getLong("commit_count"),
                        rs.getLong("file_change_count"),
                        rs.getLong("failed_file_count")),
                normalizedQuery,
                like,
                like,
                like,
                normalizedStatus,
                normalizedStatus,
                normalizedStatus
        );
    }

    public List<UiModels.CommitRow> recentCommits(int limit) {
        return jdbcTemplate.query(
                "SELECT c.id, l.id AS document_id, " +
                        "COALESCE(NULLIF(l.document_name, ''), l.document_oid, 'Документ без названия') AS document_name, " +
                        "c.commit_id, c.short_id, c.title, c.author_name, c.committed_date, c.web_url, " +
                        "count(f.id) AS file_count, " +
                        "EXISTS (SELECT 1 FROM nsi_document_git_commit_file fx WHERE fx.commit_id = c.id) AS file_history_loaded " +
                        "FROM nsi_document_git_commit c " +
                        "JOIN nsi_document_git_link l ON l.id = c.document_git_link_id " +
                        "LEFT JOIN nsi_document_git_commit_file f ON f.commit_id = c.id " +
                        "GROUP BY c.id, l.id, l.document_name, l.document_oid, c.commit_id, c.short_id, " +
                        "c.title, c.author_name, c.committed_date, c.web_url " +
                        "ORDER BY c.committed_date DESC NULLS LAST, c.id DESC " +
                        "LIMIT ?",
                (rs, rowNum) -> new UiModels.CommitRow(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getString("document_name"),
                        rs.getString("commit_id"),
                        rs.getString("short_id"),
                        rs.getString("title"),
                        rs.getString("author_name"),
                        toOffsetDateTime(rs.getTimestamp("committed_date")),
                        rs.getLong("file_count"),
                        rs.getBoolean("file_history_loaded"),
                        rs.getString("web_url")
                ),
                limit
        );
    }

    public List<UiModels.FileChangeRow> recentFileChanges(int limit) {
        return jdbcTemplate.query(
                "SELECT f.id, l.id AS document_id, " +
                        "COALESCE(NULLIF(l.document_name, ''), l.document_oid, 'Документ без названия') AS document_name, " +
                        "f.commit_sha, f.parent_sha, f.change_type, f.old_path, f.new_path, f.file_path, " +
                        "f.content_fetch_status, f.content_fetch_error, f.content_before_size, f.content_after_size, " +
                        "f.content_before_sha256, f.content_after_sha256, f.diff, f.committed_date " +
                        "FROM nsi_document_git_commit_file f " +
                        "JOIN nsi_document_git_link l ON l.id = f.git_link_id " +
                        "ORDER BY f.committed_date DESC NULLS LAST, f.id DESC " +
                        "LIMIT ?",
                (rs, rowNum) -> fileChangeRow(
                        rs.getLong("id"),
                        rs.getLong("document_id"),
                        rs.getString("document_name"),
                        rs.getString("commit_sha"),
                        rs.getString("parent_sha"),
                        rs.getString("change_type"),
                        rs.getString("old_path"),
                        rs.getString("new_path"),
                        rs.getString("file_path"),
                        rs.getString("content_fetch_status"),
                        rs.getString("content_fetch_error"),
                        longOrNull(rs.getObject("content_before_size")),
                        longOrNull(rs.getObject("content_after_size")),
                        rs.getString("content_before_sha256"),
                        rs.getString("content_after_sha256"),
                        rs.getString("diff"),
                        rs.getTimestamp("committed_date")),
                limit
        );
    }

    public UiModels.DocumentDetails documentDetails(long documentId, Long selectedFileChangeId) {
        UiModels.DocumentSummary document = document(documentId);
        List<UiModels.CommitRow> commits = commits(documentId);
        List<UiModels.FileChangeRow> fileChanges = fileChanges(documentId);
        UiModels.FileChangeRow selected = selectedFileChangeId == null
                ? (fileChanges.isEmpty() ? null : fileChanges.get(0))
                : fileChange(documentId, selectedFileChangeId);
        return new UiModels.DocumentDetails(document, commits, fileChanges, selected);
    }

    private UiModels.DocumentSummary document(long documentId) {
        return jdbcTemplate.queryForObject(
                "SELECT l.id, l.document_oid, COALESCE(NULLIF(l.document_name, ''), l.document_oid, 'Документ без названия') AS document_name, " +
                        "l.project_path, l.tree_ref, l.last_sync_status, l.last_sync_error, l.last_sync_at, " +
                        "count(DISTINCT c.id) AS commit_count, " +
                        "count(f.id) AS file_change_count, " +
                        "sum(CASE WHEN f.content_fetch_status = 'FAILED' THEN 1 ELSE 0 END) AS failed_file_count " +
                        "FROM nsi_document_git_link l " +
                        "LEFT JOIN nsi_document_git_commit c ON c.document_git_link_id = l.id " +
                        "LEFT JOIN nsi_document_git_commit_file f ON f.commit_id = c.id " +
                        "WHERE l.id = ? " +
                        "GROUP BY l.id, l.document_oid, l.document_name, l.project_path, l.tree_ref, " +
                        "l.last_sync_status, l.last_sync_error, l.last_sync_at",
                (rs, rowNum) -> documentSummary(rs.getLong("id"),
                        rs.getString("document_oid"),
                        rs.getString("document_name"),
                        rs.getString("project_path"),
                        rs.getString("tree_ref"),
                        rs.getString("last_sync_status"),
                        rs.getString("last_sync_error"),
                        rs.getTimestamp("last_sync_at"),
                        rs.getLong("commit_count"),
                        rs.getLong("file_change_count"),
                        rs.getLong("failed_file_count")),
                documentId
        );
    }

    private List<UiModels.CommitRow> commits(long documentId) {
        return jdbcTemplate.query(
                "SELECT c.id, c.commit_id, c.short_id, c.title, c.author_name, c.committed_date, c.web_url, " +
                        "count(f.id) AS file_count, " +
                        "EXISTS (SELECT 1 FROM nsi_document_git_commit_file fx WHERE fx.commit_id = c.id) AS file_history_loaded " +
                        "FROM nsi_document_git_commit c " +
                        "LEFT JOIN nsi_document_git_commit_file f ON f.commit_id = c.id " +
                        "WHERE c.document_git_link_id = ? " +
                        "GROUP BY c.id, c.commit_id, c.short_id, c.title, c.author_name, c.committed_date, c.web_url " +
                        "ORDER BY c.committed_date DESC NULLS LAST, c.id DESC " +
                        "LIMIT 120",
                (rs, rowNum) -> new UiModels.CommitRow(
                        rs.getLong("id"),
                        rs.getString("commit_id"),
                        rs.getString("short_id"),
                        rs.getString("title"),
                        rs.getString("author_name"),
                        toOffsetDateTime(rs.getTimestamp("committed_date")),
                        rs.getLong("file_count"),
                        rs.getBoolean("file_history_loaded"),
                        rs.getString("web_url")
                ),
                documentId
        );
    }

    private List<UiModels.FileChangeRow> fileChanges(long documentId) {
        return jdbcTemplate.query(
                "SELECT f.id, f.commit_sha, f.parent_sha, f.change_type, f.old_path, f.new_path, f.file_path, " +
                        "f.content_fetch_status, f.content_fetch_error, f.content_before_size, f.content_after_size, " +
                        "f.content_before_sha256, f.content_after_sha256, f.diff, f.committed_date " +
                        "FROM nsi_document_git_commit_file f " +
                        "WHERE f.git_link_id = ? " +
                        "ORDER BY f.committed_date DESC NULLS LAST, f.id DESC " +
                        "LIMIT 300",
                (rs, rowNum) -> fileChangeRow(rs.getLong("id"),
                        rs.getString("commit_sha"),
                        rs.getString("parent_sha"),
                        rs.getString("change_type"),
                        rs.getString("old_path"),
                        rs.getString("new_path"),
                        rs.getString("file_path"),
                        rs.getString("content_fetch_status"),
                        rs.getString("content_fetch_error"),
                        longOrNull(rs.getObject("content_before_size")),
                        longOrNull(rs.getObject("content_after_size")),
                        rs.getString("content_before_sha256"),
                        rs.getString("content_after_sha256"),
                        rs.getString("diff"),
                        rs.getTimestamp("committed_date")),
                documentId
        );
    }

    private UiModels.FileChangeRow fileChange(long documentId, long fileChangeId) {
        return jdbcTemplate.queryForObject(
                "SELECT f.id, f.commit_sha, f.parent_sha, f.change_type, f.old_path, f.new_path, f.file_path, " +
                        "f.content_fetch_status, f.content_fetch_error, f.content_before_size, f.content_after_size, " +
                        "f.content_before_sha256, f.content_after_sha256, f.diff, f.committed_date " +
                        "FROM nsi_document_git_commit_file f WHERE f.id = ? AND f.git_link_id = ?",
                (rs, rowNum) -> fileChangeRow(rs.getLong("id"),
                        rs.getString("commit_sha"),
                        rs.getString("parent_sha"),
                        rs.getString("change_type"),
                        rs.getString("old_path"),
                        rs.getString("new_path"),
                        rs.getString("file_path"),
                        rs.getString("content_fetch_status"),
                        rs.getString("content_fetch_error"),
                        longOrNull(rs.getObject("content_before_size")),
                        longOrNull(rs.getObject("content_after_size")),
                        rs.getString("content_before_sha256"),
                        rs.getString("content_after_sha256"),
                        rs.getString("diff"),
                        rs.getTimestamp("committed_date")),
                fileChangeId,
                documentId
        );
    }

    private UiModels.DocumentSummary documentSummary(long id,
                                                     String documentOid,
                                                     String documentName,
                                                     String projectPath,
                                                     String treeRef,
                                                     String status,
                                                     String lastSyncError,
                                                     Timestamp lastSyncAt,
                                                     long commitCount,
                                                     long fileChangeCount,
                                                     long failedFileCount) {
        return new UiModels.DocumentSummary(
                id,
                documentOid,
                documentName,
                projectPath,
                treeRef,
                status,
                lastSyncError,
                toOffsetDateTime(lastSyncAt),
                commitCount,
                fileChangeCount,
                failedFileCount
        );
    }

    private UiModels.FileChangeRow fileChangeRow(long id,
                                                 String commitSha,
                                                 String parentSha,
                                                 String changeType,
                                                 String oldPath,
                                                 String newPath,
                                                 String filePath,
                                                 String contentFetchStatus,
                                                 String contentFetchError,
                                                 Long contentBeforeSize,
                                                 Long contentAfterSize,
                                                 String contentBeforeSha256,
                                                 String contentAfterSha256,
                                                 String diff,
                                                 Timestamp committedDate) {
        return fileChangeRow(id, 0, null, commitSha, parentSha, changeType, oldPath, newPath, filePath,
                contentFetchStatus, contentFetchError, contentBeforeSize, contentAfterSize, contentBeforeSha256,
                contentAfterSha256, diff, committedDate);
    }

    private UiModels.FileChangeRow fileChangeRow(long id,
                                                 long documentId,
                                                 String documentName,
                                                 String commitSha,
                                                 String parentSha,
                                                 String changeType,
                                                 String oldPath,
                                                 String newPath,
                                                 String filePath,
                                                 String contentFetchStatus,
                                                 String contentFetchError,
                                                 Long contentBeforeSize,
                                                 Long contentAfterSize,
                                                 String contentBeforeSha256,
                                                 String contentAfterSha256,
                                                 String diff,
                                                 Timestamp committedDate) {
        return new UiModels.FileChangeRow(
                id,
                documentId,
                documentName,
                commitSha,
                parentSha,
                changeType,
                oldPath,
                newPath,
                filePath,
                contentFetchStatus,
                contentFetchError,
                contentBeforeSize,
                contentAfterSize,
                contentBeforeSha256,
                contentAfterSha256,
                diff,
                toOffsetDateTime(committedDate)
        );
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
            return ((Number) value).longValue();
        }
        return Long.valueOf(value.toString());
    }
}
