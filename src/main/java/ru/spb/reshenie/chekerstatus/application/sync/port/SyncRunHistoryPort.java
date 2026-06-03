package ru.spb.reshenie.chekerstatus.application.sync.port;

import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunStatus;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;

import java.util.Map;

public interface SyncRunHistoryPort {

    long createRun(SyncRunType runType,
                   String dictionaryIdentifier,
                   boolean forceReload,
                   Map<String, Object> requestPayload);

    void finishRun(long runId, SyncRunStatus status, NsiSyncRunUpdate update);

    void updateRunProgress(long runId, NsiSyncRunUpdate update);

    void stopRunOnServerShutdown(long runId, NsiSyncRunUpdate update, String message);

    int stopRunningRunsAfterPreviousServerStop(String message);

    void addError(long runId,
                  SyncErrorStage stage,
                  String dictionaryIdentifier,
                  Long gitLinkId,
                  String projectPath,
                  String commitSha,
                  String filePath,
                  String message,
                  Throwable throwable);
}
