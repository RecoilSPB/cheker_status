package ru.spb.reshenie.chekerstatus.application.sync;

import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.application.sync.port.SyncRunHistoryPort;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class NsiSyncRunService {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncRunService.class);
    private static final String SERVER_STOPPED_MESSAGE = "Синхронизация остановлена из-за остановки сервера";
    private static final String PREVIOUS_SERVER_STOPPED_MESSAGE =
            "Синхронизация остановлена: предыдущий запуск сервера завершился до окончания процесса";

    private final SyncRunHistoryPort repository;
    private final ConcurrentMap<Long, ActiveSyncRun> activeRuns = new ConcurrentHashMap<Long, ActiveSyncRun>();

    public NsiSyncRunService(SyncRunHistoryPort repository) {
        this.repository = repository;
    }

    public long startRun(SyncRunType runType, String dictionaryIdentifier, boolean forceReload) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("dictionaryIdentifier", dictionaryIdentifier);
        payload.put("forceReload", forceReload);
        payload.put("runType", runType.name());
        long runId = repository.createRun(runType, dictionaryIdentifier, forceReload, payload);
        activeRuns.put(Long.valueOf(runId), new ActiveSyncRun(dictionaryIdentifier));
        return runId;
    }

    public void finishSuccess(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, SyncRunStatus.SUCCESS, update);
        activeRuns.remove(Long.valueOf(runId));
    }

    public void finishPartial(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, SyncRunStatus.PARTIAL, update);
        activeRuns.remove(Long.valueOf(runId));
    }

    public void finishFailed(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, SyncRunStatus.FAILED, update);
        activeRuns.remove(Long.valueOf(runId));
    }

    public void finishCompleted(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, update.successStatus(), update);
        activeRuns.remove(Long.valueOf(runId));
    }

    public void updateProgress(long runId, SyncErrorStage stage, NsiSyncRunUpdate update) {
        ActiveSyncRun activeRun = activeRuns.get(Long.valueOf(runId));
        if (activeRun != null) {
            activeRun.update(stage, update);
        }
        repository.updateRunProgress(runId, update);
    }

    public void addError(long runId,
                         SyncErrorStage stage,
                         String dictionaryIdentifier,
                         Long gitLinkId,
                         String projectPath,
                         String commitSha,
                         String filePath,
                         String message,
                         Throwable throwable) {
        try {
            repository.addError(runId, stage, dictionaryIdentifier, gitLinkId, projectPath, commitSha, filePath,
                    message, throwable);
        } catch (RuntimeException e) {
            log.warn("Cannot save sync run error: runId={}, stage={}, error={}", runId, stage, e.getMessage());
        }
    }

    public void safeFinishCompleted(long runId, NsiSyncRunUpdate update) {
        try {
            finishCompleted(runId, update);
        } catch (RuntimeException e) {
            log.warn("Cannot finish sync run: runId={}, error={}", runId, e.getMessage());
        }
    }

    public void safeFinishFailed(long runId, NsiSyncRunUpdate update) {
        try {
            finishFailed(runId, update);
        } catch (RuntimeException e) {
            log.warn("Cannot mark sync run as failed: runId={}, error={}", runId, e.getMessage());
        }
    }

    public void safeUpdateProgress(long runId, SyncErrorStage stage, NsiSyncRunUpdate update) {
        try {
            updateProgress(runId, stage, update);
        } catch (RuntimeException e) {
            log.warn("Cannot update sync run progress: runId={}, stage={}, error={}",
                    runId, stage, e.getMessage());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void markRunsInterruptedBeforeStartup() {
        try {
            int stopped = repository.stopRunningRunsAfterPreviousServerStop(PREVIOUS_SERVER_STOPPED_MESSAGE);
            if (stopped > 0) {
                log.warn("Marked stale running sync runs as server-stopped: count={}", stopped);
            }
        } catch (RuntimeException e) {
            log.warn("Cannot mark stale running sync runs as server-stopped: error={}", e.getMessage());
        }
    }

    @PreDestroy
    public void markActiveRunsStoppedOnShutdown() {
        for (Map.Entry<Long, ActiveSyncRun> entry : activeRuns.entrySet()) {
            long runId = entry.getKey().longValue();
            ActiveSyncRun activeRun = entry.getValue();
            NsiSyncRunUpdate update = activeRun.updateWithStopMessage(SERVER_STOPPED_MESSAGE);
            try {
                repository.addError(
                        runId,
                        SyncErrorStage.UNKNOWN,
                        activeRun.dictionaryIdentifier,
                        null,
                        null,
                        null,
                        null,
                        update.getErrorMessage(),
                        null
                );
            } catch (RuntimeException e) {
                log.warn("Cannot save server-stop sync run error: runId={}, error={}", runId, e.getMessage());
            }
            try {
                repository.stopRunOnServerShutdown(runId, update, update.getErrorMessage());
                activeRuns.remove(entry.getKey());
                log.warn("Marked active sync run as server-stopped: runId={}, stage={}",
                        runId, activeRun.stage);
            } catch (RuntimeException e) {
                log.warn("Cannot mark active sync run as server-stopped: runId={}, error={}",
                        runId, e.getMessage());
            }
        }
    }

    static long durationMs(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return 0L;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }

    private static class ActiveSyncRun {
        private final String dictionaryIdentifier;
        private volatile SyncErrorStage stage = SyncErrorStage.UNKNOWN;
        private volatile NsiSyncRunUpdate update = new NsiSyncRunUpdate();

        private ActiveSyncRun(String dictionaryIdentifier) {
            this.dictionaryIdentifier = dictionaryIdentifier;
        }

        private void update(SyncErrorStage stage, NsiSyncRunUpdate update) {
            if (stage != null) {
                this.stage = stage;
            }
            this.update = update.copy();
        }

        private NsiSyncRunUpdate updateWithStopMessage(String message) {
            NsiSyncRunUpdate copy = update.copy();
            copy.incrementErrorCount();
            copy.setErrorMessage(message + ": последний этап " + stage.name());
            return copy;
        }
    }
}
