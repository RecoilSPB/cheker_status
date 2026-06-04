package ru.spb.reshenie.chekerstatus.common.json;

import com.fasterxml.jackson.databind.JsonNode;

public final class JsonNodes {

    private JsonNodes() {
    }

    public static String text(JsonNode node, String fieldName) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    public static int intValue(JsonNode node, String fieldName, int defaultValue) {
        if (node == null) {
            return defaultValue;
        }
        JsonNode value = node.get(fieldName);
        if (value == null || value.isNull()) {
            return defaultValue;
        }
        return value.asInt(defaultValue);
    }
}
