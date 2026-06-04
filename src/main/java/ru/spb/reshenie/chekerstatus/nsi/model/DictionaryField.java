package ru.spb.reshenie.chekerstatus.nsi.model;

import ru.spb.reshenie.chekerstatus.common.json.JsonNodes;
import com.fasterxml.jackson.databind.JsonNode;

public class DictionaryField {

    private final String name;
    private final String dataType;
    private final String alias;
    private final String description;
    private final String referenceOid;
    private final JsonNode rawJson;

    private DictionaryField(String name,
                            String dataType,
                            String alias,
                            String description,
                            String referenceOid,
                            JsonNode rawJson) {
        this.name = name;
        this.dataType = dataType;
        this.alias = alias;
        this.description = description;
        this.referenceOid = referenceOid;
        this.rawJson = rawJson;
    }

    public static DictionaryField fromJson(JsonNode json) {
        return new DictionaryField(
                JsonNodes.text(json, "field"),
                JsonNodes.text(json, "dataType"),
                JsonNodes.text(json, "alias"),
                JsonNodes.text(json, "description"),
                JsonNodes.text(json.path("referenceTo"), "oid"),
                json
        );
    }

    public String getName() {
        return name;
    }

    public String getDataType() {
        return dataType;
    }

    public String getAlias() {
        return alias;
    }

    public String getDescription() {
        return description;
    }

    public String getReferenceOid() {
        return referenceOid;
    }

    public JsonNode getRawJson() {
        return rawJson;
    }
}
