package ru.spb.reshenie.chekerstatus.infrastructure.persistence;

import ru.spb.reshenie.chekerstatus.domain.sync.DashboardSummary;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRun;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunDetails;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunError;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunFilter;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.domain.sync.PagedResult;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;
import ru.spb.reshenie.chekerstatus.application.sync.port.SyncRunHistoryPort;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class NsiSyncRunRepository implements SyncRunHistoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NsiSyncRunRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public long createRun(SyncRunType runType,
                          String dictionaryIdentifier,
                          boolean forceReload,
                          Map<String, Object> payload) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO nsi_sync_run (" +
                        "run_type, status, dictionary_identifier, force_reload, payload" +
                        ") VALUES (?, 'RUNNING', ?, ?, CAST(? AS jsonb)) RETURNING id",
                Long.class,
                runType.name(),
                dictionaryIdentifier,
                forceReload,
                toJson(payload == null ? new LinkedHashMap<String, Object>() : payload)
        );
    }

    public void finishRun(long runId, SyncRunStatus status, NsiSyncRunUpdate update) {
        jdbcTemplate.update(
                "UPDATE nsi_sync_run SET " +
                        "status = ?, " +
                        "dictionary_version = ?, " +
                        "finished_at = now(), " +
                        "duration_ms = CAST(EXTRACT(EPOCH FROM (now() - started_at)) * 1000 AS BIGINT), " +
                        "nsi_rows_total = ?, " +
                        "nsi_rows_inserted = ?, " +
                        "nsi_rows_updated = ?, " +
                        "nsi_rows_deactivated = ?, " +
                        "git_links_found = ?, " +
                        "git_projects_processed = ?, " +
                        "git_commits_processed = ?, " +
                        "git_files_processed = ?, " +
                        "error_count = ?, " +
                        "error_message = ?, " +
                        "updated_at = now() " +
                        "WHERE id = ? AND status = 'RUNNING'",
                status.name(),
                update.getDictionaryVersion(),
                update.getNsiRowsTotal(),
                update.getNsiRowsInserted(),
                update.getNsiRowsUpdated(),
                update.getNsiRowsDeactivated(),
                update.getGitLinksFound(),
                update.getGitProjectsProcessed(),
                update.getGitCommitsProcessed(),
                update.getGitFilesProcessed(),
                update.getErrorCount(),
                trim(update.getErrorMessage(), 4000),
                runId
        );
    }

    public void updateRunProgress(long runId, NsiSyncRunUpdate update) {
        jdbcTemplate.update(
                "UPDATE nsi_sync_run SET " +
                        "dictionary_version = ?, " +
                        "nsi_rows_total = ?, " +
                        "nsi_rows_inserted = ?, " +
                        "nsi_rows_updated = ?, " +
                        "nsi_rows_deactivated = ?, " +
                        "git_links_found = ?, " +
                        "git_projects_processed = ?, " +
                        "git_commits_processed = ?, " +
                        "git_files_processed = ?, " +
                        "error_count = ?, " +
                        "error_message = ?, " +
                        "updated_at = now() " +
                        "WHERE id = ? AND status = 'RUNNING'",
                update.getDictionaryVersion(),
                update.getNsiRowsTotal(),
                update.getNsiRowsInserted(),
                update.getNsiRowsUpdated(),
                update.getNsiRowsDeactivated(),
                update.getGitLinksFound(),
                update.getGitProjectsProcessed(),
                update.getGitCommitsProcessed(),
                update.getGitFilesProcessed(),
                update.getErrorCount(),
                trim(update.getErrorMessage(), 4000),
                runId
        );
    }

    public void stopRunOnServerShutdown(long runId, NsiSyncRunUpdate update, String message) {
        jdbcTemplate.update(
                "UPDATE nsi_sync_run SET " +
                        "status = ?, " +
                        "dictionary_version = ?, " +
                        "finished_at = now(), " +
                        "duration_ms = CAST(EXTRACT(EPOCH FROM (now() - started_at)) * 1000 AS BIGINT), " +
                        "nsi_rows_total = ?, " +
                        "nsi_rows_inserted = ?, " +
                        "nsi_rows_updated = ?, " +
                        "nsi_rows_deactivated = ?, " +
                        "git_links_found = ?, " +
                        "git_projects_processed = ?, " +
                        "git_commits_processed = ?, " +
                        "git_files_processed = ?, " +
                        "error_count = ?, " +
                        "error_message = ?, " +
                        "updated_at = now() " +
                        "WHERE id = ? AND status = 'RUNNING'",
                SyncRunStatus.SERVER_STOPPED.name(),
                update.getDictionaryVersion(),
                update.getNsiRowsTotal(),
                update.getNsiRowsInserted(),
                update.getNsiRowsUpdated(),
                update.getNsiRowsDeactivated(),
                update.getGitLinksFound(),
                update.getGitProjectsProcessed(),
                update.getGitCommitsProcessed(),
                update.getGitFilesProcessed(),
                update.getErrorCount(),
                trim(message, 4000),
                runId
        );
    }

    public int stopRunningRunsAfterPreviousServerStop(String message) {
        return jdbcTemplate.update(
                "UPDATE nsi_sync_run SET " +
                        "status = ?, " +
                        "finished_at = now(), " +
                        "duration_ms = CAST(EXTRACT(EPOCH FROM (now() - started_at)) * 1000 AS BIGINT), " +
                        "error_count = error_count + 1, " +
                        "error_message = ?, " +
                        "updated_at = now() " +
                        "WHERE status = 'RUNNING'",
                SyncRunStatus.SERVER_STOPPED.name(),
                trim(message, 4000)
        );
    }

    public void addError(long runId,
                         SyncErrorStage stage,
                         String dictionaryIdentifier,
                         Long gitLinkId,
                         String projectPath,
                         String commitSha,
                         String filePath,
                         String message,
                         Throwable throwable) {
        jdbcTemplate.update(
                "INSERT INTO nsi_sync_run_error (" +
                        "sync_run_id, error_stage, dictionary_identifier, git_link_id, project_path, " +
                        "commit_sha, file_path, error_message, stack_trace" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                runId,
                stage.name(),
                dictionaryIdentifier,
                gitLinkId,
                projectPath,
                commitSha,
                filePath,
                trim(message, 4000),
                throwable == null ? null : trim(stackTrace(throwable), 12000)
        );
    }

    public DashboardSummary dashboardSummary() {
        NsiSyncRun lastRun = findFirstRun("ORDER BY started_at DESC, id DESC");
        NsiSyncRun lastSuccessfulRun = findFirstRun("WHERE status = 'SUCCESS' ORDER BY started_at DESC, id DESC");
        boolean running = countBySql("SELECT count(*) FROM nsi_sync_run WHERE status = 'RUNNING'") > 0;
        String currentStatus;
        if (running) {
            currentStatus = "RUNNING";
        } else if (lastRun != null
                && (SyncRunStatus.FAILED.equals(lastRun.getStatus())
                || SyncRunStatus.SERVER_STOPPED.equals(lastRun.getStatus()))) {
            currentStatus = lastRun.getStatus().name();
        } else {
            currentStatus = "IDLE";
        }

        String dictionaryIdentifier = lastRun == null ? latestDictionaryIdentifier() : lastRun.getDictionaryIdentifier();
        String dictionaryVersion = lastRun == null ? latestDictionaryVersion() : lastRun.getDictionaryVersion();
        if (dictionaryVersion == null) {
            dictionaryVersion = latestDictionaryVersion();
        }

        return new DashboardSummary(
                currentStatus,
                lastRun,
                lastSuccessfulRun,
                dictionaryIdentifier,
                dictionaryVersion,
                countBySql("SELECT count(*) FROM nsi_record WHERE active = true"),
                countBySql("SELECT count(*) FROM nsi_document_git_link WHERE active = true"),
                countBySql("SELECT count(*) FROM nsi_document_git_link WHERE active = true AND last_sync_status = 'ERROR'"),
                countBySql("SELECT count(*) FROM nsi_document_git_commit"),
                countBySql("SELECT count(*) FROM nsi_document_git_commit_file")
        );
    }

    public PagedResult<NsiSyncRun> findRuns(NsiSyncRunFilter filter) {
        SqlFilter sqlFilter = buildFilter(filter);
        long total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nsi_sync_run r " + sqlFilter.whereSql,
                Long.class,
                sqlFilter.args.toArray()
        );

        List<Object> args = new ArrayList<Object>(sqlFilter.args);
        args.add(filter.getSize());
        args.add(filter.getPage() * filter.getSize());
        List<NsiSyncRun> runs = jdbcTemplate.query(
                "SELECT " + runColumns("r") + " FROM nsi_sync_run r " +
                        sqlFilter.whereSql + " " +
                        orderBy(filter) + " LIMIT ? OFFSET ?",
                (rs, rowNum) -> mapRun("r", rs),
                args.toArray()
        );
        return new PagedResult<NsiSyncRun>(runs, filter.getPage(), filter.getSize(), total);
    }

    public NsiSyncRunDetails findDetails(long runId) {
        NsiSyncRun run = findRun(runId);
        return new NsiSyncRunDetails(run, findErrors(runId));
    }

    public NsiSyncRun findRun(long runId) {
        return jdbcTemplate.queryForObject(
                "SELECT " + runColumns("r") + " FROM nsi_sync_run r WHERE r.id = ?",
                (rs, rowNum) -> mapRun("r", rs),
                runId
        );
    }

    public List<NsiSyncRunError> findErrors(long runId) {
        return jdbcTemplate.query(
                "SELECT id, sync_run_id, error_stage, dictionary_identifier, git_link_id, project_path, " +
                        "commit_sha, file_path, error_message, stack_trace, created_at " +
                        "FROM nsi_sync_run_error WHERE sync_run_id = ? ORDER BY created_at, id",
                (rs, rowNum) -> new NsiSyncRunError(
                        rs.getLong("id"),
                        rs.getLong("sync_run_id"),
                        SyncErrorStage.valueOf(rs.getString("error_stage")),
                        rs.getString("dictionary_identifier"),
                        longOrNull(rs.getObject("git_link_id")),
                        rs.getString("project_path"),
                        rs.getString("commit_sha"),
                        rs.getString("file_path"),
                        rs.getString("error_message"),
                        rs.getString("stack_trace"),
                        toOffsetDateTime(rs.getTimestamp("created_at"))
                ),
                runId
        );
    }

    private NsiSyncRun findFirstRun(String suffix) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT " + runColumns("r") + " FROM nsi_sync_run r " + suffix + " LIMIT 1",
                    (rs, rowNum) -> mapRun("r", rs)
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private SqlFilter buildFilter(NsiSyncRunFilter filter) {
        List<String> conditions = new ArrayList<String>();
        List<Object> args = new ArrayList<Object>();

        if (filter.getStatus() != null) {
            conditions.add("r.status = ?");
            args.add(filter.getStatus().name());
        }
        if (filter.getRunType() != null) {
            conditions.add("r.run_type = ?");
            args.add(filter.getRunType().name());
        }
        if (filter.getDictionaryIdentifier() != null) {
            conditions.add("r.dictionary_identifier = ?");
            args.add(filter.getDictionaryIdentifier());
        }
        if (filter.getDictionaryVersion() != null) {
            conditions.add("r.dictionary_version = ?");
            args.add(filter.getDictionaryVersion());
        }
        if (filter.getDateFrom() != null) {
            conditions.add("r.started_at >= ?");
            args.add(toTimestamp(filter.getDateFrom()));
        }
        if (filter.getDateTo() != null) {
            conditions.add("r.started_at <= ?");
            args.add(toTimestamp(filter.getDateTo()));
        }
        if (filter.getHasErrors() != null) {
            conditions.add(filter.getHasErrors() ? "r.error_count > 0" : "r.error_count = 0");
        }

        String where = conditions.isEmpty() ? "" : "WHERE " + join(conditions, " AND ");
        return new SqlFilter(where, args);
    }

    private String orderBy(NsiSyncRunFilter filter) {
        String sort = filter.getSort();
        String column;
        if ("status".equalsIgnoreCase(sort)) {
            column = "r.status";
        } else if ("duration".equalsIgnoreCase(sort) || "durationMs".equalsIgnoreCase(sort)) {
            column = "r.duration_ms";
        } else if ("errorCount".equalsIgnoreCase(sort)) {
            column = "r.error_count";
        } else {
            column = "r.started_at";
        }

        String direction = "asc".equalsIgnoreCase(filter.getDirection()) ? "ASC" : "DESC";
        return "ORDER BY " + column + " " + direction + " NULLS LAST, r.id " + direction;
    }

    private String runColumns(String alias) {
        return alias + ".id, " + alias + ".run_type, " + alias + ".status, " +
                alias + ".dictionary_identifier, " + alias + ".dictionary_version, " +
                alias + ".started_at, " + alias + ".finished_at, " + alias + ".duration_ms, " +
                alias + ".force_reload, " + alias + ".nsi_rows_total, " + alias + ".nsi_rows_inserted, " +
                alias + ".nsi_rows_updated, " + alias + ".nsi_rows_deactivated, " +
                alias + ".git_links_found, " + alias + ".git_projects_processed, " +
                alias + ".git_commits_processed, " + alias + ".git_files_processed, " +
                alias + ".error_count, " + alias + ".error_message";
    }

    private NsiSyncRun mapRun(String alias, java.sql.ResultSet rs) throws java.sql.SQLException {
        return new NsiSyncRun(
                rs.getLong("id"),
                SyncRunType.valueOf(rs.getString("run_type")),
                SyncRunStatus.valueOf(rs.getString("status")),
                rs.getString("dictionary_identifier"),
                rs.getString("dictionary_version"),
                toOffsetDateTime(rs.getTimestamp("started_at")),
                toOffsetDateTime(rs.getTimestamp("finished_at")),
                longOrNull(rs.getObject("duration_ms")),
                rs.getBoolean("force_reload"),
                rs.getInt("nsi_rows_total"),
                rs.getInt("nsi_rows_inserted"),
                rs.getInt("nsi_rows_updated"),
                rs.getInt("nsi_rows_deactivated"),
                rs.getInt("git_links_found"),
                rs.getInt("git_projects_processed"),
                rs.getInt("git_commits_processed"),
                rs.getInt("git_files_processed"),
                rs.getInt("error_count"),
                rs.getString("error_message")
        );
    }

    private long countBySql(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private String latestDictionaryIdentifier() {
        return queryOptionalString("SELECT api_identifier FROM nsi_dictionary ORDER BY updated_at DESC, id DESC LIMIT 1");
    }

    private String latestDictionaryVersion() {
        return queryOptionalString("SELECT current_version FROM nsi_dictionary ORDER BY updated_at DESC, id DESC LIMIT 1");
    }

    private String queryOptionalString(String sql) {
        try {
            return jdbcTemplate.queryForObject(sql, String.class);
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize JSON value", e);
        }
    }

    private Timestamp toTimestamp(OffsetDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }
        return Timestamp.from(dateTime.toInstant());
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

    private String stackTrace(Throwable throwable) {
        StringWriter writer = new StringWriter();
        throwable.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }

    private String join(List<String> values, String delimiter) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) {
                builder.append(delimiter);
            }
            builder.append(value);
        }
        return builder.toString();
    }

    private static class SqlFilter {
        private final String whereSql;
        private final List<Object> args;

        private SqlFilter(String whereSql, List<Object> args) {
            this.whereSql = whereSql;
            this.args = args;
        }
    }
}
