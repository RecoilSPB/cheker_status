package ru.spb.reshenie.chekerstatus.domain.sync;

import java.time.OffsetDateTime;

public class NsiSyncRun {

    private final long id;
    private final SyncRunType runType;
    private final SyncRunStatus status;
    private final String dictionaryIdentifier;
    private final String dictionaryVersion;
    private final OffsetDateTime startedAt;
    private final OffsetDateTime finishedAt;
    private final Long durationMs;
    private final boolean forceReload;
    private final int nsiRowsTotal;
    private final int nsiRowsInserted;
    private final int nsiRowsUpdated;
    private final int nsiRowsDeactivated;
    private final int gitLinksFound;
    private final int gitProjectsProcessed;
    private final int gitCommitsProcessed;
    private final int gitFilesProcessed;
    private final int progressPercent;
    private final int errorCount;
    private final String errorMessage;

    public NsiSyncRun(long id,
                      SyncRunType runType,
                      SyncRunStatus status,
                      String dictionaryIdentifier,
                      String dictionaryVersion,
                      OffsetDateTime startedAt,
                      OffsetDateTime finishedAt,
                      Long durationMs,
                      boolean forceReload,
                      int nsiRowsTotal,
                      int nsiRowsInserted,
                      int nsiRowsUpdated,
                      int nsiRowsDeactivated,
                      int gitLinksFound,
                      int gitProjectsProcessed,
                      int gitCommitsProcessed,
                      int gitFilesProcessed,
                      int errorCount,
                      String errorMessage) {
        this(id, runType, status, dictionaryIdentifier, dictionaryVersion, startedAt, finishedAt, durationMs,
                forceReload, nsiRowsTotal, nsiRowsInserted, nsiRowsUpdated, nsiRowsDeactivated,
                gitLinksFound, gitProjectsProcessed, gitCommitsProcessed, gitFilesProcessed,
                defaultProgressPercent(status), errorCount, errorMessage);
    }

    public NsiSyncRun(long id,
                      SyncRunType runType,
                      SyncRunStatus status,
                      String dictionaryIdentifier,
                      String dictionaryVersion,
                      OffsetDateTime startedAt,
                      OffsetDateTime finishedAt,
                      Long durationMs,
                      boolean forceReload,
                      int nsiRowsTotal,
                      int nsiRowsInserted,
                      int nsiRowsUpdated,
                      int nsiRowsDeactivated,
                      int gitLinksFound,
                      int gitProjectsProcessed,
                      int gitCommitsProcessed,
                      int gitFilesProcessed,
                      int progressPercent,
                      int errorCount,
                      String errorMessage) {
        this.id = id;
        this.runType = runType;
        this.status = status;
        this.dictionaryIdentifier = dictionaryIdentifier;
        this.dictionaryVersion = dictionaryVersion;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.durationMs = durationMs;
        this.forceReload = forceReload;
        this.nsiRowsTotal = nsiRowsTotal;
        this.nsiRowsInserted = nsiRowsInserted;
        this.nsiRowsUpdated = nsiRowsUpdated;
        this.nsiRowsDeactivated = nsiRowsDeactivated;
        this.gitLinksFound = gitLinksFound;
        this.gitProjectsProcessed = gitProjectsProcessed;
        this.gitCommitsProcessed = gitCommitsProcessed;
        this.gitFilesProcessed = gitFilesProcessed;
        this.progressPercent = normalizeProgressPercent(progressPercent);
        this.errorCount = errorCount;
        this.errorMessage = errorMessage;
    }

    public long getId() {
        return id;
    }

    public SyncRunType getRunType() {
        return runType;
    }

    public SyncRunStatus getStatus() {
        return status;
    }

    public String getDictionaryIdentifier() {
        return dictionaryIdentifier;
    }

    public String getDictionaryVersion() {
        return dictionaryVersion;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getFinishedAt() {
        return finishedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public boolean isForceReload() {
        return forceReload;
    }

    public int getNsiRowsTotal() {
        return nsiRowsTotal;
    }

    public int getNsiRowsInserted() {
        return nsiRowsInserted;
    }

    public int getNsiRowsUpdated() {
        return nsiRowsUpdated;
    }

    public int getNsiRowsDeactivated() {
        return nsiRowsDeactivated;
    }

    public int getGitLinksFound() {
        return gitLinksFound;
    }

    public int getGitProjectsProcessed() {
        return gitProjectsProcessed;
    }

    public int getGitCommitsProcessed() {
        return gitCommitsProcessed;
    }

    public int getGitFilesProcessed() {
        return gitFilesProcessed;
    }

    public int getProgressPercent() {
        return progressPercent;
    }

    public String getProgressLabel() {
        return progressPercent + "%";
    }

    public int getErrorCount() {
        return errorCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public String getDisplayStatus() {
        if (SyncRunStatus.RUNNING.equals(status)) {
            return "Выполняется";
        }
        if (SyncRunStatus.SUCCESS.equals(status)) {
            return "Успешно";
        }
        if (SyncRunStatus.FAILED.equals(status)) {
            return "Ошибка";
        }
        if (SyncRunStatus.PARTIAL.equals(status)) {
            return "Частично выполнено";
        }
        if (SyncRunStatus.SERVER_STOPPED.equals(status)) {
            return "Остановка сервера";
        }
        return status == null ? "" : status.name();
    }

    public String getDisplayRunType() {
        if (SyncRunType.AUTO.equals(runType)) {
            return "Автоматический";
        }
        if (SyncRunType.MANUAL.equals(runType)) {
            return "Ручной";
        }
        if (SyncRunType.WEBHOOK.equals(runType)) {
            return "Webhook";
        }
        if (SyncRunType.RETRY.equals(runType)) {
            return "Повтор";
        }
        return runType == null ? "" : runType.name();
    }

    public String getStatusCss() {
        if (SyncRunStatus.SUCCESS.equals(status)) {
            return "status-ok";
        }
        if (SyncRunStatus.FAILED.equals(status)) {
            return "status-error";
        }
        if (SyncRunStatus.SERVER_STOPPED.equals(status)) {
            return "status-error";
        }
        if (SyncRunStatus.PARTIAL.equals(status)) {
            return "status-pending";
        }
        return "status-muted";
    }

    private static int defaultProgressPercent(SyncRunStatus status) {
        if (SyncRunStatus.SUCCESS.equals(status) || SyncRunStatus.PARTIAL.equals(status)) {
            return 100;
        }
        return 0;
    }

    private static int normalizeProgressPercent(int progressPercent) {
        return Math.max(0, Math.min(100, progressPercent));
    }
}
