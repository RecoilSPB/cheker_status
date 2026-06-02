package ru.spb.reshenie.chekerstatus.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class NsiSyncRunService {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncRunService.class);

    private final NsiSyncRunRepository repository;

    public NsiSyncRunService(NsiSyncRunRepository repository) {
        this.repository = repository;
    }

    public long startRun(SyncRunType runType, String dictionaryIdentifier, boolean forceReload) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("dictionaryIdentifier", dictionaryIdentifier);
        payload.put("forceReload", forceReload);
        payload.put("runType", runType.name());
        return repository.createRun(runType, dictionaryIdentifier, forceReload, payload);
    }

    public void finishSuccess(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, SyncRunStatus.SUCCESS, update);
    }

    public void finishPartial(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, SyncRunStatus.PARTIAL, update);
    }

    public void finishFailed(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, SyncRunStatus.FAILED, update);
    }

    public void finishCompleted(long runId, NsiSyncRunUpdate update) {
        repository.finishRun(runId, update.successStatus(), update);
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

    static long durationMs(OffsetDateTime startedAt, OffsetDateTime finishedAt) {
        if (startedAt == null || finishedAt == null) {
            return 0L;
        }
        return Duration.between(startedAt, finishedAt).toMillis();
    }
}
