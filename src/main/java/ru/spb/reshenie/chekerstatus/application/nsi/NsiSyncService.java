package ru.spb.reshenie.chekerstatus.application.nsi;

import ru.spb.reshenie.chekerstatus.domain.nsi.DataPage;
import ru.spb.reshenie.chekerstatus.domain.nsi.NsiRecordSaveResult;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportDocument;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportSaveResult;
import ru.spb.reshenie.chekerstatus.domain.nsi.RecordPayload;
import ru.spb.reshenie.chekerstatus.application.nsi.port.NsiClientPort;
import ru.spb.reshenie.chekerstatus.application.nsi.port.NsiPersistencePort;
import ru.spb.reshenie.chekerstatus.application.nsi.port.NsiSyncSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.application.gitlab.GitCommitTrackingService;
import ru.spb.reshenie.chekerstatus.domain.gitlab.GitSyncError;
import ru.spb.reshenie.chekerstatus.application.sync.NsiSyncRunService;
import ru.spb.reshenie.chekerstatus.domain.sync.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.domain.sync.SyncRunType;

import java.util.ArrayList;
import java.util.List;

@Service
public class NsiSyncService {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncService.class);

    private final NsiClientPort client;
    private final NsiPersistencePort persistenceService;
    private final GitCommitTrackingService gitCommitTrackingService;
    private final NsiSyncSettings settings;
    private final NsiSyncRunService syncRunService;

    public NsiSyncService(NsiClientPort client,
                          NsiPersistencePort persistenceService,
                          GitCommitTrackingService gitCommitTrackingService,
                          NsiSyncSettings settings,
                          NsiSyncRunService syncRunService) {
        this.client = client;
        this.persistenceService = persistenceService;
        this.gitCommitTrackingService = gitCommitTrackingService;
        this.settings = settings;
        this.syncRunService = syncRunService;
    }

    public void synchronizeConfiguredDictionaries(boolean forceReload) {
        synchronizeConfiguredDictionaries(forceReload, SyncRunType.AUTO);
    }

    public void synchronizeConfiguredDictionaries(boolean forceReload, SyncRunType runType) {
        List<String> identifiers = settings.getEnabledDictionaryIdentifiers();
        if (identifiers.isEmpty()) {
            log.warn("No NSI dictionaries configured");
            return;
        }
        for (String identifier : identifiers) {
            try {
                synchronizeDictionary(identifier, forceReload, runType);
            } catch (RuntimeException e) {
                log.error("NSI dictionary synchronization failed: identifier={}", identifier, e);
            }
        }
    }

    public void synchronizeDictionary(String identifier, boolean forceReload) {
        synchronizeDictionary(identifier, forceReload, SyncRunType.AUTO);
    }

    public void synchronizeDictionary(String identifier, boolean forceReload, SyncRunType runType) {
        Long runId = null;
        try {
            runId = Long.valueOf(createSyncRun(identifier, forceReload, runType));
        } catch (RuntimeException e) {
            log.warn("Cannot create NSI sync run history row: identifier={}, error={}", identifier, e.getMessage());
        }
        synchronizeDictionary(runId, identifier, forceReload);
    }

    public long createSyncRun(String identifier, boolean forceReload, SyncRunType runType) {
        return syncRunService.startRun(runType, identifier, forceReload);
    }

    public void synchronizeDictionary(long runId, String identifier, boolean forceReload) {
        synchronizeDictionary(Long.valueOf(runId), identifier, forceReload);
    }

    private void synchronizeDictionary(Long runId, String identifier, boolean forceReload) {
        NsiSyncRunUpdate update = new NsiSyncRunUpdate();
        SyncErrorStage stage = SyncErrorStage.NSI_PASSPORT;
        try {
            PassportDocument passport = client.fetchPassport(identifier);
            update.setDictionaryVersion(passport.getVersion());
            updateProgress(runId, stage, update);

            stage = SyncErrorStage.NSI_PERSISTENCE;
            PassportSaveResult savedPassport = persistenceService.savePassport(passport);
            updateProgress(runId, stage, update);

            if (savedPassport.isVersionLoaded() && !forceReload) {
                log.info("NSI dictionary is up to date: oid={}, version={}",
                        passport.getDictionaryKey(), passport.getVersion());
                stage = SyncErrorStage.GITLAB_COMMITS;
                synchronizeGitDocuments(runId, identifier, update, savedPassport.getDictionaryId());
                updateProgress(runId, stage, update);
                finishCompleted(runId, update);
                return;
            }

            stage = SyncErrorStage.NSI_DATA;
            List<RecordPayload> records = downloadAllRecords(runId, passport, update);

            stage = SyncErrorStage.NSI_PERSISTENCE;
            NsiRecordSaveResult recordSaveResult = persistenceService.saveLoadedRecords(
                    savedPassport.getDictionaryId(),
                    savedPassport.getVersionId(),
                    records
            );
            update.setNsiRowsTotal(recordSaveResult.getTotal());
            update.setNsiRowsInserted(recordSaveResult.getInserted());
            update.setNsiRowsUpdated(recordSaveResult.getUpdated());
            update.setNsiRowsDeactivated(recordSaveResult.getDeactivated());
            updateProgress(runId, stage, update);

            log.info("NSI dictionary loaded: oid={}, version={}, records={}",
                    passport.getDictionaryKey(), passport.getVersion(), records.size());

            stage = SyncErrorStage.GITLAB_COMMITS;
            synchronizeGitDocuments(runId, identifier, update, savedPassport.getDictionaryId());
            updateProgress(runId, stage, update);
            finishCompleted(runId, update);
        } catch (RuntimeException e) {
            update.incrementErrorCount();
            update.setErrorMessage(e.getMessage());
            addRunError(runId, stage, identifier, null, null, null, null, e.getMessage(), e);
            updateProgress(runId, stage, update);
            finishFailed(runId, update);
            throw e;
        }
    }

    private void synchronizeGitDocuments(Long runId,
                                         String identifier,
                                         NsiSyncRunUpdate update,
                                         long dictionaryId) {
        int baseErrorCount = update.getErrorCount();
        String baseErrorMessage = update.getErrorMessage();
        GitCommitTrackingResult gitResult = gitCommitTrackingService.synchronizeDictionaryDocuments(
                dictionaryId,
                progress -> {
                    applyGitCounters(update, progress, baseErrorCount, baseErrorMessage);
                    updateProgress(runId, SyncErrorStage.GITLAB_COMMITS, update);
                }
        );
        applyGitCounters(update, gitResult, baseErrorCount, baseErrorMessage);
        addGitErrors(runId, identifier, gitResult);
    }

    private void applyGitCounters(NsiSyncRunUpdate update,
                                  GitCommitTrackingResult gitResult,
                                  int baseErrorCount,
                                  String baseErrorMessage) {
        update.setGitLinksFound(gitResult.getLinksFound());
        update.setGitProjectsProcessed(gitResult.getProjectsProcessed());
        update.setGitCommitsProcessed(gitResult.getCommitsProcessed());
        update.setGitFilesProcessed(gitResult.getFilesProcessed());
        update.setErrorCount(baseErrorCount + gitResult.getErrorCount());
        if (gitResult.firstErrorMessage() != null && baseErrorMessage == null) {
            update.setErrorMessage(gitResult.firstErrorMessage());
        } else {
            update.setErrorMessage(baseErrorMessage);
        }
    }

    private void addGitErrors(Long runId, String identifier, GitCommitTrackingResult gitResult) {
        for (GitSyncError error : gitResult.getErrors()) {
            addRunError(runId, error.getStage(), identifier, error.getGitLinkId(), error.getProjectPath(),
                    error.getCommitSha(), error.getFilePath(), error.getMessage(), error.getThrowable());
        }
    }

    private void finishCompleted(Long runId, NsiSyncRunUpdate update) {
        if (runId != null) {
            syncRunService.safeFinishCompleted(runId.longValue(), update);
        }
    }

    private void finishFailed(Long runId, NsiSyncRunUpdate update) {
        if (runId != null) {
            syncRunService.safeFinishFailed(runId.longValue(), update);
        }
    }

    private void addRunError(Long runId,
                             SyncErrorStage stage,
                             String dictionaryIdentifier,
                             Long gitLinkId,
                             String projectPath,
                             String commitSha,
                             String filePath,
                             String message,
                             Throwable throwable) {
        if (runId != null) {
            syncRunService.addError(runId.longValue(), stage, dictionaryIdentifier, gitLinkId, projectPath, commitSha,
                    filePath, message, throwable);
        }
    }

    private void updateProgress(Long runId, SyncErrorStage stage, NsiSyncRunUpdate update) {
        if (runId != null) {
            syncRunService.safeUpdateProgress(runId.longValue(), stage, update);
        }
    }

    private List<RecordPayload> downloadAllRecords(Long runId,
                                                   PassportDocument passport,
                                                   NsiSyncRunUpdate update) {
        int pageSize = settings.getPageSize();
        if (pageSize <= 0) {
            throw new IllegalArgumentException("NSI page size must be positive");
        }
        DataPage firstPage = client.fetchDataPage(passport, 1, pageSize);
        int total = firstPage.getTotal();
        int pages = total == 0 ? 1 : (int) Math.ceil((double) total / pageSize);

        List<RecordPayload> records = new ArrayList<RecordPayload>();
        records.addAll(firstPage.getRecords());
        update.setNsiRowsTotal(records.size());
        updateProgress(runId, SyncErrorStage.NSI_DATA, update);

        for (int page = 2; page <= pages; page++) {
            DataPage dataPage = client.fetchDataPage(passport, page, pageSize);
            records.addAll(dataPage.getRecords());
            update.setNsiRowsTotal(records.size());
            updateProgress(runId, SyncErrorStage.NSI_DATA, update);
        }

        if (passport.getRowsCount() > 0 && passport.getRowsCount() != records.size()) {
            log.warn("NSI row count mismatch: oid={}, passportRows={}, loadedRows={}",
                    passport.getDictionaryKey(), passport.getRowsCount(), records.size());
        }
        if (total != records.size()) {
            throw new NsiSyncException("NSI total mismatch: oid=" + passport.getDictionaryKey()
                    + ", apiTotal=" + total + ", loadedRows=" + records.size());
        }

        return records;
    }
}
