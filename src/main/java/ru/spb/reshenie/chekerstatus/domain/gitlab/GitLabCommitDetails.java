package ru.spb.reshenie.chekerstatus.domain.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import ru.spb.reshenie.chekerstatus.domain.common.JsonNodes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class GitLabCommitDetails {

    private final String id;
    private final List<String> parentIds;
    private final JsonNode rawJson;

    private GitLabCommitDetails(String id, List<String> parentIds, JsonNode rawJson) {
        this.id = id;
        this.parentIds = Collections.unmodifiableList(parentIds);
        this.rawJson = rawJson;
    }

    public static GitLabCommitDetails fromJson(JsonNode json) {
        List<String> parentIds = new ArrayList<String>();
        JsonNode parents = json.path("parent_ids");
        if (parents.isArray()) {
            for (JsonNode parent : parents) {
                if (!parent.isNull()) {
                    parentIds.add(parent.asText());
                }
            }
        }
        return new GitLabCommitDetails(JsonNodes.text(json, "id"), parentIds, json);
    }

    public String getId() {
        return id;
    }

    public List<String> getParentIds() {
        return parentIds;
    }

    public String getFirstParentSha() {
        return parentIds.isEmpty() ? null : parentIds.get(0);
    }

    public JsonNode getRawJson() {
        return rawJson;
    }
}
