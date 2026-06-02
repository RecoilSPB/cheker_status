package ru.spb.reshenie.chekerstatus.nsi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.NsiProperties;
import ru.spb.reshenie.chekerstatus.gitlab.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.gitlab.GitCommitTrackingService;
import ru.spb.reshenie.chekerstatus.gitlab.GitSyncError;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRunService;
import ru.spb.reshenie.chekerstatus.sync.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.sync.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.SyncRunType;

import java.util.ArrayList;
import java.util.List;

@Service
public class NsiSyncService {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncService.class);

    private final NsiClient client;
    private final NsiPersistenceService persistenceService;
    private final GitCommitTrackingService gitCommitTrackingService;
    private final NsiProperties properties;
    private final NsiSyncRunService syncRunService;

    public NsiSyncService(NsiClient client,
                          NsiPersistenceService persistenceService,
                          GitCommitTrackingService gitCommitTrackingService,
                          NsiProperties properties,
                          NsiSyncRunService syncRunService) {
        this.client = client;
        this.persistenceService = persistenceService;
        this.gitCommitTrackingService = gitCommitTrackingService;
        this.properties = properties;
        this.syncRunService = syncRunService;
    }

    public void synchronizeConfiguredDictionaries(boolean forceReload) {
        synchronizeConfiguredDictionaries(forceReload, SyncRunType.AUTO);
    }

    public void synchronizeConfiguredDictionaries(boolean forceReload, SyncRunType runType) {
        if (properties.getDictionaries() == null) {
            log.warn("No NSI dictionaries configured");
            return;
        }
        for (NsiProperties.Dictionary dictionary : properties.getDictionaries()) {
            if (!dictionary.isEnabled() || dictionary.getIdentifier() == null) {
                continue;
            }
            try {
                synchronizeDictionary(dictionary.getIdentifier(), forceReload, runType);
            } catch (RuntimeException e) {
                log.error("NSI dictionary synchronization failed: identifier={}", dictionary.getIdentifier(), e);
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

            stage = SyncErrorStage.NSI_PERSISTENCE;
            PassportSaveResult savedPassport = persistenceService.savePassport(passport);

            if (savedPassport.isVersionLoaded() && !forceReload) {
                log.info("NSI dictionary is up to date: oid={}, version={}",
                        passport.getDictionaryKey(), passport.getVersion());
                stage = SyncErrorStage.GITLAB_COMMITS;
                applyGitResult(runId, identifier, update,
                        gitCommitTrackingService.synchronizeDictionaryDocuments(savedPassport.getDictionaryId()));
                finishCompleted(runId, update);
                return;
            }

            stage = SyncErrorStage.NSI_DATA;
            List<RecordPayload> records = downloadAllRecords(passport);

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

            log.info("NSI dictionary loaded: oid={}, version={}, records={}",
                    passport.getDictionaryKey(), passport.getVersion(), records.size());

            stage = SyncErrorStage.GITLAB_COMMITS;
            applyGitResult(runId, identifier, update,
                    gitCommitTrackingService.synchronizeDictionaryDocuments(savedPassport.getDictionaryId()));
            finishCompleted(runId, update);
        } catch (RuntimeException e) {
            update.incrementErrorCount();
            update.setErrorMessage(e.getMessage());
            addRunError(runId, stage, identifier, null, null, null, null, e.getMessage(), e);
            finishFailed(runId, update);
            throw e;
        }
    }

    private void applyGitResult(Long runId,
                                String identifier,
                                NsiSyncRunUpdate update,
                                GitCommitTrackingResult gitResult) {
        update.setGitLinksFound(gitResult.getLinksFound());
        update.setGitProjectsProcessed(gitResult.getProjectsProcessed());
        update.setGitCommitsProcessed(gitResult.getCommitsProcessed());
        update.setGitFilesProcessed(gitResult.getFilesProcessed());
        update.setErrorCount(update.getErrorCount() + gitResult.getErrorCount());
        if (gitResult.firstErrorMessage() != null && update.getErrorMessage() == null) {
            update.setErrorMessage(gitResult.firstErrorMessage());
        }
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

    private List<RecordPayload> downloadAllRecords(PassportDocument passport) {
        int pageSize = properties.getPageSize();
        if (pageSize <= 0) {
            throw new IllegalArgumentException("NSI page size must be positive");
        }
        DataPage firstPage = client.fetchDataPage(passport, 1, pageSize);
        int total = firstPage.getTotal();
        int pages = total == 0 ? 1 : (int) Math.ceil((double) total / pageSize);

        List<RecordPayload> records = new ArrayList<RecordPayload>();
        records.addAll(firstPage.getRecords());

        for (int page = 2; page <= pages; page++) {
            DataPage dataPage = client.fetchDataPage(passport, page, pageSize);
            records.addAll(dataPage.getRecords());
        }

        if (passport.getRowsCount() > 0 && passport.getRowsCount() != records.size()) {
            log.warn("NSI row count mismatch: oid={}, passportRows={}, loadedRows={}",
                    passport.getDictionaryKey(), passport.getRowsCount(), records.size());
        }
        if (total != records.size()) {
            throw new NsiClientException("NSI total mismatch: oid=" + passport.getDictionaryKey()
                    + ", apiTotal=" + total + ", loadedRows=" + records.size());
        }

        return records;
    }
}
