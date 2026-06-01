package ru.spb.reshenie.chekerstatus.gitlab;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import ru.spb.reshenie.chekerstatus.nsi.JsonNodes;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Repository
public class GitTrackingRepository {

    private static final Logger log = LoggerFactory.getLogger(GitTrackingRepository.class);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final GitLabLinkParser linkParser;

    public GitTrackingRepository(JdbcTemplate jdbcTemplate,
                                 ObjectMapper objectMapper,
                                 GitLabLinkParser linkParser) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.linkParser = linkParser;
    }

    @Transactional
    public List<DocumentGitLink> refreshAndFindActiveLinks(long dictionaryId) {
        List<DocumentGitLinkCandidate> candidates = findCandidates(dictionaryId);
        List<String> activeRecordKeys = new ArrayList<String>();

        for (DocumentGitLinkCandidate candidate : candidates) {
            GitLabLink parsedLink = linkParser.parse(candidate.getGitLink());
            if (parsedLink == null) {
                log.warn("Cannot parse GitLab link: recordKey={}, link={}",
                        candidate.getRecordKey(), candidate.getGitLink());
                continue;
            }

            upsertDocumentLink(dictionaryId, candidate, parsedLink);
            activeRecordKeys.add(candidate.getRecordKey());
        }

        markInactiveLinks(dictionaryId, activeRecordKeys);
        return findActiveLinks(dictionaryId);
    }

    @Transactional
    public void saveCommits(long documentGitLinkId, List<GitLabCommit> commits) {
        for (GitLabCommit commit : commits) {
            if (commit.getId() == null) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO nsi_document_git_commit (" +
                            "document_git_link_id, commit_id, short_id, title, message, " +
                            "author_name, author_email, authored_date, committer_name, committer_email, " +
                            "committed_date, created_at_git, web_url, raw_json" +
                            ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb)) " +
                            "ON CONFLICT (document_git_link_id, commit_id) DO UPDATE SET " +
                            "short_id = EXCLUDED.short_id, " +
                            "title = EXCLUDED.title, " +
                            "message = EXCLUDED.message, " +
                            "author_name = EXCLUDED.author_name, " +
                            "author_email = EXCLUDED.author_email, " +
                            "authored_date = EXCLUDED.authored_date, " +
                            "committer_name = EXCLUDED.committer_name, " +
                            "committer_email = EXCLUDED.committer_email, " +
                            "committed_date = EXCLUDED.committed_date, " +
                            "created_at_git = EXCLUDED.created_at_git, " +
                            "web_url = EXCLUDED.web_url, " +
                            "raw_json = EXCLUDED.raw_json, " +
                            "last_seen_at = now(), " +
                            "updated_at = now()",
                    documentGitLinkId,
                    commit.getId(),
                    commit.getShortId(),
                    commit.getTitle(),
                    commit.getMessage(),
                    commit.getAuthorName(),
                    commit.getAuthorEmail(),
                    toTimestamp(commit.getAuthoredDate()),
                    commit.getCommitterName(),
                    commit.getCommitterEmail(),
                    toTimestamp(commit.getCommittedDate()),
                    toTimestamp(commit.getCreatedAt()),
                    commit.getWebUrl(),
                    toJson(commit.getRawJson())
            );
        }
    }

    public void markSyncSuccess(long documentGitLinkId) {
        jdbcTemplate.update(
                "UPDATE nsi_document_git_link " +
                        "SET last_sync_at = now(), last_sync_status = 'OK', last_sync_error = NULL, updated_at = now() " +
                        "WHERE id = ?",
                documentGitLinkId
        );
    }

    public void markSyncError(long documentGitLinkId, String error) {
        jdbcTemplate.update(
                "UPDATE nsi_document_git_link " +
                        "SET last_sync_at = now(), last_sync_status = 'ERROR', last_sync_error = ?, updated_at = now() " +
                        "WHERE id = ?",
                trim(error, 4000),
                documentGitLinkId
        );
    }

    private List<DocumentGitLinkCandidate> findCandidates(long dictionaryId) {
        return jdbcTemplate.query(
                "SELECT id, record_key, data::text AS data_json " +
                        "FROM nsi_record " +
                        "WHERE dictionary_id = ? " +
                        "AND active = true " +
                        "AND NULLIF(BTRIM(data->>'GIT_LINK'), '') IS NOT NULL",
                (rs, rowNum) -> {
                    JsonNode data = parseJson(rs.getString("data_json"));
                    return new DocumentGitLinkCandidate(
                            rs.getLong("id"),
                            rs.getString("record_key"),
                            JsonNodes.text(data, "OID"),
                            JsonNodes.text(data, "FULL_NAME"),
                            JsonNodes.text(data, "GIT_LINK")
                    );
                },
                dictionaryId
        );
    }

    private long upsertDocumentLink(long dictionaryId,
                                    DocumentGitLinkCandidate candidate,
                                    GitLabLink parsedLink) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO nsi_document_git_link (" +
                        "dictionary_id, nsi_record_id, record_key, document_oid, document_name, " +
                        "git_link, git_host, project_path, tree_ref, active" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, true) " +
                        "ON CONFLICT (dictionary_id, record_key) DO UPDATE SET " +
                        "nsi_record_id = EXCLUDED.nsi_record_id, " +
                        "document_oid = EXCLUDED.document_oid, " +
                        "document_name = EXCLUDED.document_name, " +
                        "git_link = EXCLUDED.git_link, " +
                        "git_host = EXCLUDED.git_host, " +
                        "project_path = EXCLUDED.project_path, " +
                        "tree_ref = EXCLUDED.tree_ref, " +
                        "active = true, " +
                        "updated_at = now() " +
                        "RETURNING id",
                Long.class,
                dictionaryId,
                candidate.getNsiRecordId(),
                candidate.getRecordKey(),
                candidate.getDocumentOid(),
                candidate.getDocumentName(),
                parsedLink.getOriginalUrl(),
                parsedLink.getHost(),
                parsedLink.getProjectPath(),
                parsedLink.getTreeRef()
        );
    }

    private void markInactiveLinks(long dictionaryId, List<String> activeRecordKeys) {
        if (activeRecordKeys.isEmpty()) {
            jdbcTemplate.update(
                    "UPDATE nsi_document_git_link SET active = false, updated_at = now() WHERE dictionary_id = ?",
                    dictionaryId
            );
            return;
        }

        String placeholders = joinPlaceholders(activeRecordKeys.size());
        List<Object> args = new ArrayList<Object>();
        args.add(dictionaryId);
        args.addAll(activeRecordKeys);
        jdbcTemplate.update(
                "UPDATE nsi_document_git_link " +
                        "SET active = false, updated_at = now() " +
                        "WHERE dictionary_id = ? AND record_key NOT IN (" + placeholders + ")",
                args.toArray()
        );
    }

    private List<DocumentGitLink> findActiveLinks(long dictionaryId) {
        return jdbcTemplate.query(
                "SELECT id, dictionary_id, nsi_record_id, record_key, document_oid, document_name, " +
                        "git_link, git_host, project_path, tree_ref " +
                        "FROM nsi_document_git_link " +
                        "WHERE dictionary_id = ? AND active = true " +
                        "ORDER BY document_oid NULLS LAST, record_key",
                (rs, rowNum) -> new DocumentGitLink(
                        rs.getLong("id"),
                        rs.getLong("dictionary_id"),
                        rs.getLong("nsi_record_id"),
                        rs.getString("record_key"),
                        rs.getString("document_oid"),
                        rs.getString("document_name"),
                        rs.getString("git_link"),
                        rs.getString("git_host"),
                        rs.getString("project_path"),
                        rs.getString("tree_ref")
                ),
                dictionaryId
        );
    }

    private JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot parse JSON from database", e);
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

    private String joinPlaceholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() > maxLength ? normalized.substring(0, maxLength) : normalized;
    }
}
