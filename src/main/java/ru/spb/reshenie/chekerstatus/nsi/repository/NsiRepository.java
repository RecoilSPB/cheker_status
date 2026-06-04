package ru.spb.reshenie.chekerstatus.nsi.repository;

import ru.spb.reshenie.chekerstatus.nsi.model.DictionaryField;
import ru.spb.reshenie.chekerstatus.nsi.model.NsiRecordSaveResult;
import ru.spb.reshenie.chekerstatus.nsi.model.PassportDocument;
import ru.spb.reshenie.chekerstatus.nsi.model.PassportSaveResult;
import ru.spb.reshenie.chekerstatus.nsi.model.RecordPayload;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class NsiRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NsiRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PassportSaveResult savePassport(PassportDocument passport) {
        long dictionaryId = upsertDictionary(passport);
        upsertFields(dictionaryId, passport.getFields());
        long versionId = upsertVersion(dictionaryId, passport);
        boolean loaded = isVersionLoaded(versionId);
        return new PassportSaveResult(dictionaryId, versionId, loaded);
    }

    @Transactional
    public NsiRecordSaveResult saveLoadedRecords(long dictionaryId,
                                                 long versionId,
                                                 List<RecordPayload> records) {
        Map<String, String> existingHashes = findExistingHashes(dictionaryId, records);
        int inserted = 0;
        int updated = 0;
        for (RecordPayload record : records) {
            String existingHash = existingHashes.get(record.getRecordKey());
            if (existingHash == null) {
                inserted++;
            } else if (!existingHash.equals(record.getContentHash())) {
                updated++;
            }
            upsertRecord(dictionaryId, versionId, record);
            insertRecordHistory(dictionaryId, versionId, record);
        }
        int deactivated = jdbcTemplate.update(
                "UPDATE nsi_record " +
                        "SET active = false, updated_at = now() " +
                        "WHERE dictionary_id = ? AND dictionary_version_id <> ? AND active = true",
                dictionaryId,
                versionId
        );
        jdbcTemplate.update(
                "UPDATE nsi_dictionary_version SET loaded_at = now() WHERE id = ?",
                versionId
        );
        return new NsiRecordSaveResult(records.size(), inserted, updated, deactivated);
    }

    private long upsertDictionary(PassportDocument passport) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO nsi_dictionary (" +
                        "oid, api_identifier, external_identifier, current_version, rows_count, " +
                        "full_name, short_name, publish_date_text, last_update_text, passport_json" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb)) " +
                        "ON CONFLICT (oid) DO UPDATE SET " +
                        "api_identifier = EXCLUDED.api_identifier, " +
                        "external_identifier = EXCLUDED.external_identifier, " +
                        "current_version = EXCLUDED.current_version, " +
                        "rows_count = EXCLUDED.rows_count, " +
                        "full_name = EXCLUDED.full_name, " +
                        "short_name = EXCLUDED.short_name, " +
                        "publish_date_text = EXCLUDED.publish_date_text, " +
                        "last_update_text = EXCLUDED.last_update_text, " +
                        "passport_json = EXCLUDED.passport_json, " +
                        "updated_at = now() " +
                        "RETURNING id",
                Long.class,
                passport.getDictionaryKey(),
                passport.getRequestedIdentifier(),
                passport.getExternalIdentifier(),
                passport.getVersion(),
                passport.getRowsCount(),
                passport.getFullName(),
                passport.getShortName(),
                passport.getPublishDateText(),
                passport.getLastUpdateText(),
                toJson(passport.getRawJson())
        );
    }

    private long upsertVersion(long dictionaryId, PassportDocument passport) {
        return jdbcTemplate.queryForObject(
                "INSERT INTO nsi_dictionary_version (" +
                        "dictionary_id, version, rows_count, publish_date_text, last_update_text, passport_json" +
                        ") VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb)) " +
                        "ON CONFLICT (dictionary_id, version) DO UPDATE SET " +
                        "rows_count = EXCLUDED.rows_count, " +
                        "publish_date_text = EXCLUDED.publish_date_text, " +
                        "last_update_text = EXCLUDED.last_update_text, " +
                        "passport_json = EXCLUDED.passport_json " +
                        "RETURNING id",
                Long.class,
                dictionaryId,
                passport.getVersion(),
                passport.getRowsCount(),
                passport.getPublishDateText(),
                passport.getLastUpdateText(),
                toJson(passport.getRawJson())
        );
    }

    private boolean isVersionLoaded(long versionId) {
        Boolean loaded = jdbcTemplate.queryForObject(
                "SELECT loaded_at IS NOT NULL FROM nsi_dictionary_version WHERE id = ?",
                Boolean.class,
                versionId
        );
        return Boolean.TRUE.equals(loaded);
    }

    private void upsertFields(long dictionaryId, List<DictionaryField> fields) {
        List<String> activeFieldNames = new ArrayList<String>();
        for (DictionaryField field : fields) {
            if (field.getName() == null) {
                continue;
            }
            activeFieldNames.add(field.getName());
            jdbcTemplate.update(
                    "INSERT INTO nsi_dictionary_field (" +
                            "dictionary_id, field_name, data_type, alias, description, reference_oid, field_json, active" +
                            ") VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), true) " +
                            "ON CONFLICT (dictionary_id, field_name) DO UPDATE SET " +
                            "data_type = EXCLUDED.data_type, " +
                            "alias = EXCLUDED.alias, " +
                            "description = EXCLUDED.description, " +
                            "reference_oid = EXCLUDED.reference_oid, " +
                            "field_json = EXCLUDED.field_json, " +
                            "active = true, " +
                            "updated_at = now()",
                    dictionaryId,
                    field.getName(),
                    field.getDataType(),
                    field.getAlias(),
                    field.getDescription(),
                    field.getReferenceOid(),
                    toJson(field.getRawJson())
            );
        }
        markInactiveFields(dictionaryId, activeFieldNames);
    }

    private void markInactiveFields(long dictionaryId, List<String> activeFieldNames) {
        if (activeFieldNames.isEmpty()) {
            jdbcTemplate.update(
                    "UPDATE nsi_dictionary_field SET active = false, updated_at = now() WHERE dictionary_id = ?",
                    dictionaryId
            );
            return;
        }

        String placeholders = joinPlaceholders(activeFieldNames.size());
        List<Object> args = new ArrayList<Object>();
        args.add(dictionaryId);
        args.addAll(activeFieldNames);
        jdbcTemplate.update(
                "UPDATE nsi_dictionary_field " +
                        "SET active = false, updated_at = now() " +
                        "WHERE dictionary_id = ? AND field_name NOT IN (" + placeholders + ")",
                args.toArray()
        );
    }

    private void upsertRecord(long dictionaryId, long versionId, RecordPayload record) {
        jdbcTemplate.update(
                "INSERT INTO nsi_record (" +
                        "dictionary_id, dictionary_version_id, record_key, row_number, content_hash, data, raw_cells, active" +
                        ") VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), true) " +
                        "ON CONFLICT (dictionary_id, record_key) DO UPDATE SET " +
                        "dictionary_version_id = EXCLUDED.dictionary_version_id, " +
                        "row_number = EXCLUDED.row_number, " +
                        "content_hash = EXCLUDED.content_hash, " +
                        "data = EXCLUDED.data, " +
                        "raw_cells = EXCLUDED.raw_cells, " +
                        "active = true, " +
                        "last_seen_at = now(), " +
                        "updated_at = CASE " +
                        "    WHEN nsi_record.content_hash <> EXCLUDED.content_hash THEN now() " +
                        "    ELSE nsi_record.updated_at " +
                        "END",
                dictionaryId,
                versionId,
                record.getRecordKey(),
                record.getRowNumber(),
                record.getContentHash(),
                toJson(record.getData()),
                toJson(record.getRawCells())
        );
    }

    private Map<String, String> findExistingHashes(long dictionaryId, List<RecordPayload> records) {
        if (records.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> recordKeys = new ArrayList<String>();
        for (RecordPayload record : records) {
            recordKeys.add(record.getRecordKey());
        }

        String placeholders = joinPlaceholders(recordKeys.size());
        List<Object> args = new ArrayList<Object>();
        args.add(dictionaryId);
        args.addAll(recordKeys);

        List<Map.Entry<String, String>> rows = jdbcTemplate.query(
                "SELECT record_key, content_hash FROM nsi_record " +
                        "WHERE dictionary_id = ? AND record_key IN (" + placeholders + ")",
                (rs, rowNum) -> new java.util.AbstractMap.SimpleEntry<String, String>(
                        rs.getString("record_key"),
                        rs.getString("content_hash")
                ),
                args.toArray()
        );

        Map<String, String> hashes = new HashMap<String, String>();
        for (Map.Entry<String, String> row : rows) {
            hashes.put(row.getKey(), row.getValue());
        }
        return hashes;
    }

    private void insertRecordHistory(long dictionaryId, long versionId, RecordPayload record) {
        jdbcTemplate.update(
                "INSERT INTO nsi_record_history (" +
                        "dictionary_id, dictionary_version_id, record_key, row_number, content_hash, data, raw_cells" +
                        ") VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb)) " +
                        "ON CONFLICT (dictionary_version_id, record_key) DO NOTHING",
                dictionaryId,
                versionId,
                record.getRecordKey(),
                record.getRowNumber(),
                record.getContentHash(),
                toJson(record.getData()),
                toJson(record.getRawCells())
        );
    }

    private String joinPlaceholders(int count) {
        return String.join(",", Collections.nCopies(count, "?"));
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize JSON value", e);
        }
    }
}
