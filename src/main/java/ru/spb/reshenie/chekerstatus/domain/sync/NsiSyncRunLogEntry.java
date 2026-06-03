package ru.spb.reshenie.chekerstatus.domain.sync;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class NsiSyncRunLogEntry {

    private final long id;
    private final long syncRunId;
    private final SyncRunLogLevel level;
    private final SyncErrorStage stage;
    private final String dictionaryIdentifier;
    private final Long gitLinkId;
    private final String projectPath;
    private final String commitSha;
    private final String filePath;
    private final String message;
    private final String stackTrace;
    private final Map<String, Object> details;
    private final OffsetDateTime createdAt;

    public NsiSyncRunLogEntry(long id,
                              long syncRunId,
                              SyncRunLogLevel level,
                              SyncErrorStage stage,
                              String dictionaryIdentifier,
                              Long gitLinkId,
                              String projectPath,
                              String commitSha,
                              String filePath,
                              String message,
                              String stackTrace,
                              Map<String, Object> details,
                              OffsetDateTime createdAt) {
        this.id = id;
        this.syncRunId = syncRunId;
        this.level = level;
        this.stage = stage;
        this.dictionaryIdentifier = dictionaryIdentifier;
        this.gitLinkId = gitLinkId;
        this.projectPath = projectPath;
        this.commitSha = commitSha;
        this.filePath = filePath;
        this.message = message;
        this.stackTrace = stackTrace;
        this.details = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(
                details == null ? Collections.emptyMap() : details
        ));
        this.createdAt = createdAt;
    }

    public long getId() {
        return id;
    }

    public long getSyncRunId() {
        return syncRunId;
    }

    public SyncRunLogLevel getLevel() {
        return level;
    }

    public SyncErrorStage getStage() {
        return stage;
    }

    public String getDictionaryIdentifier() {
        return dictionaryIdentifier;
    }

    public Long getGitLinkId() {
        return gitLinkId;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getMessage() {
        return message;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public Map<String, Object> getDetails() {
        return details;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public String getLevelCss() {
        if (SyncRunLogLevel.ERROR.equals(level)) {
            return "status-error";
        }
        if (SyncRunLogLevel.WARN.equals(level)) {
            return "status-pending";
        }
        return "status-ok";
    }
}
