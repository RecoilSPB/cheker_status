package ru.spb.reshenie.chekerstatus.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import ru.spb.reshenie.chekerstatus.nsi.JsonNodes;

public class GitLabCommitDiff {

    private final String oldPath;
    private final String newPath;
    private final String diff;
    private final boolean newFile;
    private final boolean renamedFile;
    private final boolean deletedFile;
    private final boolean tooLarge;
    private final JsonNode rawJson;

    public GitLabCommitDiff(String oldPath,
                            String newPath,
                            String diff,
                            boolean newFile,
                            boolean renamedFile,
                            boolean deletedFile,
                            boolean tooLarge,
                            JsonNode rawJson) {
        this.oldPath = oldPath;
        this.newPath = newPath;
        this.diff = diff;
        this.newFile = newFile;
        this.renamedFile = renamedFile;
        this.deletedFile = deletedFile;
        this.tooLarge = tooLarge;
        this.rawJson = rawJson;
    }

    public static GitLabCommitDiff fromJson(JsonNode json) {
        return new GitLabCommitDiff(
                JsonNodes.text(json, "old_path"),
                JsonNodes.text(json, "new_path"),
                JsonNodes.text(json, "diff"),
                json.path("new_file").asBoolean(false),
                json.path("renamed_file").asBoolean(false),
                json.path("deleted_file").asBoolean(false),
                json.path("too_large").asBoolean(false),
                json
        );
    }

    public String changeType() {
        if (newFile) {
            return "added";
        }
        if (deletedFile) {
            return "deleted";
        }
        if (renamedFile) {
            return "renamed";
        }
        return "modified";
    }

    public String filePath() {
        if (deletedFile && oldPath != null) {
            return oldPath;
        }
        return newPath == null ? oldPath : newPath;
    }

    public String getOldPath() {
        return oldPath;
    }

    public String getNewPath() {
        return newPath;
    }

    public String getDiff() {
        return diff;
    }

    public boolean isNewFile() {
        return newFile;
    }

    public boolean isRenamedFile() {
        return renamedFile;
    }

    public boolean isDeletedFile() {
        return deletedFile;
    }

    public boolean isTooLarge() {
        return tooLarge;
    }

    public JsonNode getRawJson() {
        return rawJson;
    }
}
