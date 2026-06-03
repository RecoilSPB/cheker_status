package ru.spb.reshenie.chekerstatus.domain.nsi;

import ru.spb.reshenie.chekerstatus.domain.common.JsonNodes;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PassportDocument {

    private final String requestedIdentifier;
    private final String externalIdentifier;
    private final String oid;
    private final String version;
    private final int rowsCount;
    private final String fullName;
    private final String shortName;
    private final String publishDateText;
    private final String lastUpdateText;
    private final JsonNode rawJson;
    private final List<DictionaryField> fields;
    private final List<String> primaryKeys;

    private PassportDocument(String requestedIdentifier,
                             String externalIdentifier,
                             String oid,
                             String version,
                             int rowsCount,
                             String fullName,
                             String shortName,
                             String publishDateText,
                             String lastUpdateText,
                             JsonNode rawJson,
                             List<DictionaryField> fields,
                             List<String> primaryKeys) {
        this.requestedIdentifier = requestedIdentifier;
        this.externalIdentifier = externalIdentifier;
        this.oid = oid;
        this.version = version;
        this.rowsCount = rowsCount;
        this.fullName = fullName;
        this.shortName = shortName;
        this.publishDateText = publishDateText;
        this.lastUpdateText = lastUpdateText;
        this.rawJson = rawJson;
        this.fields = fields;
        this.primaryKeys = primaryKeys;
    }

    public static PassportDocument fromJson(String requestedIdentifier, JsonNode json) {
        List<DictionaryField> fields = new ArrayList<DictionaryField>();
        JsonNode fieldsNode = json.path("fields");
        if (fieldsNode.isArray()) {
            for (JsonNode field : fieldsNode) {
                fields.add(DictionaryField.fromJson(field));
            }
        }

        List<String> primaryKeys = new ArrayList<String>();
        JsonNode keysNode = json.path("keys");
        if (keysNode.isArray()) {
            for (JsonNode key : keysNode) {
                if ("PRIMARY".equalsIgnoreCase(JsonNodes.text(key, "type"))) {
                    String fieldName = JsonNodes.text(key, "field");
                    if (fieldName != null) {
                        primaryKeys.add(fieldName);
                    }
                }
            }
        }

        return new PassportDocument(
                requestedIdentifier,
                JsonNodes.text(json, "identifier"),
                JsonNodes.text(json, "oid"),
                JsonNodes.text(json, "version"),
                JsonNodes.intValue(json, "rowsCount", 0),
                JsonNodes.text(json, "fullName"),
                JsonNodes.text(json, "shortName"),
                JsonNodes.text(json, "publishDate"),
                JsonNodes.text(json, "lastUpdate"),
                json,
                Collections.unmodifiableList(fields),
                Collections.unmodifiableList(primaryKeys)
        );
    }

    public String getRequestedIdentifier() {
        return requestedIdentifier;
    }

    public String getExternalIdentifier() {
        return externalIdentifier;
    }

    public String getOid() {
        return oid;
    }

    public String getDictionaryKey() {
        return oid == null ? requestedIdentifier : oid;
    }

    public String getVersion() {
        return version;
    }

    public int getRowsCount() {
        return rowsCount;
    }

    public String getFullName() {
        return fullName;
    }

    public String getShortName() {
        return shortName;
    }

    public String getPublishDateText() {
        return publishDateText;
    }

    public String getLastUpdateText() {
        return lastUpdateText;
    }

    public JsonNode getRawJson() {
        return rawJson;
    }

    public List<DictionaryField> getFields() {
        return fields;
    }

    public List<String> getPrimaryKeys() {
        return primaryKeys;
    }
}
