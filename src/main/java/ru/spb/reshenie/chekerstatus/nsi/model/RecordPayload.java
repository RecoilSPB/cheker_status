package ru.spb.reshenie.chekerstatus.nsi.model;

import ru.spb.reshenie.chekerstatus.common.json.JsonNodes;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class RecordPayload {

    private final int rowNumber;
    private final String recordKey;
    private final ObjectNode data;
    private final ArrayNode rawCells;
    private final String contentHash;

    private RecordPayload(int rowNumber,
                          String recordKey,
                          ObjectNode data,
                          ArrayNode rawCells,
                          String contentHash) {
        this.rowNumber = rowNumber;
        this.recordKey = recordKey;
        this.data = data;
        this.rawCells = rawCells;
        this.contentHash = contentHash;
    }

    public static RecordPayload fromApiRow(ObjectMapper objectMapper,
                                           int rowNumber,
                                           JsonNode row,
                                           List<String> primaryKeys) {
        TreeMap<String, JsonNode> sortedValues = new TreeMap<String, JsonNode>();
        ArrayNode rawCells = JsonNodeFactory.instance.arrayNode();

        if (row != null && row.isArray()) {
            for (JsonNode cell : row) {
                rawCells.add(cell);
                String column = JsonNodes.text(cell, "column");
                if (column != null) {
                    JsonNode value = cell.get("value");
                    sortedValues.put(column, value == null ? JsonNodeFactory.instance.nullNode() : value);
                }
            }
        }

        ObjectNode data = JsonNodeFactory.instance.objectNode();
        for (Map.Entry<String, JsonNode> entry : sortedValues.entrySet()) {
            data.set(entry.getKey(), entry.getValue());
        }

        String recordKey = buildRecordKey(data, primaryKeys, rowNumber);
        String contentHash = sha256(toCanonicalJson(objectMapper, data));
        return new RecordPayload(rowNumber, recordKey, data, rawCells, contentHash);
    }

    static String buildRecordKey(JsonNode data, List<String> primaryKeys, int rowNumber) {
        StringBuilder builder = new StringBuilder();
        if (primaryKeys != null) {
            for (String primaryKey : primaryKeys) {
                JsonNode value = data.get(primaryKey);
                if (value != null && !value.isNull()) {
                    if (builder.length() > 0) {
                        builder.append('|');
                    }
                    builder.append(primaryKey).append('=').append(value.asText());
                }
            }
        }

        if (builder.length() == 0) {
            builder.append("ROW=").append(rowNumber);
        }
        return builder.toString();
    }

    private static String toCanonicalJson(ObjectMapper objectMapper, JsonNode data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            StringBuilder fallback = new StringBuilder();
            Iterator<String> fieldNames = data.fieldNames();
            while (fieldNames.hasNext()) {
                String fieldName = fieldNames.next();
                fallback.append(fieldName).append('=').append(data.get(fieldName)).append(';');
            }
            return fallback.toString();
        }
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                String item = Integer.toHexString(0xff & b);
                if (item.length() == 1) {
                    hex.append('0');
                }
                hex.append(item);
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    public int getRowNumber() {
        return rowNumber;
    }

    public String getRecordKey() {
        return recordKey;
    }

    public ObjectNode getData() {
        return data;
    }

    public ArrayNode getRawCells() {
        return rawCells;
    }

    public String getContentHash() {
        return contentHash;
    }
}
