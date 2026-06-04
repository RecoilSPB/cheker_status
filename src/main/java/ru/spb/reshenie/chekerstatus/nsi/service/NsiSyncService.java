package ru.spb.reshenie.chekerstatus.nsi.service;

import ru.spb.reshenie.chekerstatus.nsi.model.DataPage;
import ru.spb.reshenie.chekerstatus.nsi.model.NsiRecordSaveResult;
import ru.spb.reshenie.chekerstatus.nsi.model.PassportDocument;
import ru.spb.reshenie.chekerstatus.nsi.model.PassportSaveResult;
import ru.spb.reshenie.chekerstatus.nsi.model.RecordPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.nsi.NsiProperties;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitCommitTrackingService;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitSyncError;
import ru.spb.reshenie.chekerstatus.nsi.client.NsiClient;
import ru.spb.reshenie.chekerstatus.nsi.repository.NsiRepository;
import ru.spb.reshenie.chekerstatus.sync.service.SyncRunService;
import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRunUpdate;
import ru.spb.reshenie.chekerstatus.sync.model.SyncErrorStage;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunLogLevel;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunType;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class NsiSyncService {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncService.class);

    private final NsiClient client;
    private final NsiRepository persistenceService;
    private final GitCommitTrackingService gitCommitTrackingService;
    private final NsiProperties settings;
    private final SyncRunService syncRunService;

    public NsiSyncService(NsiClient client,
                          NsiRepository persistenceService,
                          GitCommitTrackingService gitCommitTrackingService,
                          NsiProperties settings,
                          SyncRunService syncRunService) {
        this.client = client;
        this.persistenceService = persistenceService;
        this.gitCommitTrackingService = gitCommitTrackingService;
        this.settings = settings;
        this.syncRunService = syncRunService;
    }

    public boolean synchronizeConfiguredDictionaries(boolean forceReload) {
        return synchronizeConfiguredDictionaries(forceReload, SyncRunType.AUTO);
    }

    public boolean synchronizeConfiguredDictionaries(boolean forceReload, SyncRunType runType) {
        List<String> identifiers = settings.getEnabledDictionaryIdentifiers();
        if (identifiers.isEmpty()) {
            log.warn("No NSI dictionaries configured");
            return false;
        }
        boolean failed = false;
        for (String identifier : identifiers) {
            try {
                synchronizeDictionary(identifier, forceReload, runType);
            } catch (RuntimeException e) {
                failed = true;
                log.error("NSI dictionary synchronization failed: identifier={}", identifier, e);
            }
        }
        return failed;
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
        SyncProgressTracker progress = new SyncProgressTracker(settings.getProgressGitLinkWeight());
        SyncErrorStage stage = SyncErrorStage.NSI_PASSPORT;
        try {
            progress.start();
            applyProgress(update, progress);
            updateProgress(runId, stage, update);
            addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                    "Загрузка паспорта НСИ", null, valueDetails("identifier", identifier));
            PassportDocument passport = client.fetchPassport(identifier);
            update.setDictionaryVersion(passport.getVersion());
            update.setNsiRowsTotal(passport.getRowsCount());
            progress.setNsiRowsTotal(passport.getRowsCount());
            progress.completePassport();
            applyProgress(update, progress);
            addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                    "Паспорт НСИ загружен", null, passportDetails(passport));
            updateProgress(runId, stage, update);

            stage = SyncErrorStage.NSI_PERSISTENCE;
            addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                    "Сохранение паспорта НСИ", null, passportDetails(passport));
            PassportSaveResult savedPassport = persistenceService.savePassport(passport);
            applyProgress(update, progress);
            addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                    "Паспорт НСИ сохранен", null, passportSaveDetails(savedPassport));
            updateProgress(runId, stage, update);

            if (savedPassport.isVersionLoaded() && !forceReload) {
                log.info("NSI dictionary is up to date: oid={}, version={}",
                        passport.getDictionaryKey(), passport.getVersion());
                addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                        "Версия НСИ уже загружена", null, passportSaveDetails(savedPassport));
                progress.addNsiRowsLoaded(update.getNsiRowsTotal());
                stage = SyncErrorStage.GITLAB_COMMITS;
                synchronizeGitDocuments(runId, identifier, update, progress, savedPassport.getDictionaryId());
                updateProgress(runId, stage, update);
                finishCompleted(runId, update);
                return;
            }

            stage = SyncErrorStage.NSI_DATA;
            List<RecordPayload> records = downloadAllRecords(runId, passport, update, progress);

            stage = SyncErrorStage.NSI_PERSISTENCE;
            addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                    "Сохранение строк НСИ", null, valueDetails("records", Integer.valueOf(records.size())));
            NsiRecordSaveResult recordSaveResult = persistenceService.saveLoadedRecords(
                    savedPassport.getDictionaryId(),
                    savedPassport.getVersionId(),
                    records
            );
            update.setNsiRowsTotal(recordSaveResult.getTotal());
            update.setNsiRowsInserted(recordSaveResult.getInserted());
            update.setNsiRowsUpdated(recordSaveResult.getUpdated());
            update.setNsiRowsDeactivated(recordSaveResult.getDeactivated());
            applyProgress(update, progress);
            addRunLog(runId, SyncRunLogLevel.INFO, stage, identifier, null, null, null, null,
                    "Строки НСИ сохранены", null, recordSaveDetails(recordSaveResult));
            updateProgress(runId, stage, update);

            log.info("NSI dictionary loaded: oid={}, version={}, records={}",
                    passport.getDictionaryKey(), passport.getVersion(), records.size());

            stage = SyncErrorStage.GITLAB_COMMITS;
            synchronizeGitDocuments(runId, identifier, update, progress, savedPassport.getDictionaryId());
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
                                         SyncProgressTracker progress,
                                         long dictionaryId) {
        int baseErrorCount = update.getErrorCount();
        String baseErrorMessage = update.getErrorMessage();
        GitCommitTrackingResult gitResult = gitCommitTrackingService.synchronizeDictionaryDocuments(
                dictionaryId,
                runId,
                identifier,
                gitProgress -> {
                    applyGitCounters(update, gitProgress, baseErrorCount, baseErrorMessage);
                    applyGitProgress(update, progress, gitProgress, false);
                    updateProgress(runId, SyncErrorStage.GITLAB_COMMITS, update);
                }
        );
        applyGitCounters(update, gitResult, baseErrorCount, baseErrorMessage);
        applyGitProgress(update, progress, gitResult, true);
        addGitErrors(runId, identifier, gitResult);
    }

    private void applyGitCounters(NsiSyncRunUpdate update,
                                  GitCommitTrackingResult gitResult,
                                  int baseErrorCount,
                                  String baseErrorMessage) {
        update.setGitLinksFound(gitResult.getLinksFound());
        update.setGitProjectsProcessed(gitResult.getProjectsChecked());
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

    private void addRunLog(Long runId,
                           SyncRunLogLevel level,
                           SyncErrorStage stage,
                           String dictionaryIdentifier,
                           Long gitLinkId,
                           String projectPath,
                           String commitSha,
                           String filePath,
                           String message,
                           Throwable throwable,
                           Map<String, Object> details) {
        if (runId != null) {
            syncRunService.safeAddLog(runId.longValue(), level, stage, dictionaryIdentifier, gitLinkId, projectPath,
                    commitSha, filePath, message, throwable, details);
        }
    }

    private void updateProgress(Long runId, SyncErrorStage stage, NsiSyncRunUpdate update) {
        if (runId != null) {
            syncRunService.safeUpdateProgress(runId.longValue(), stage, update);
        }
    }

    private List<RecordPayload> downloadAllRecords(Long runId,
                                                   PassportDocument passport,
                                                   NsiSyncRunUpdate update,
                                                   SyncProgressTracker progress) {
        int pageSize = settings.getPageSize();
        if (pageSize <= 0) {
            throw new IllegalArgumentException("NSI page size must be positive");
        }
        DataPage firstPage = client.fetchDataPage(passport, 1, pageSize);
        int total = firstPage.getTotal();
        int pages = total == 0 ? 1 : (int) Math.ceil((double) total / pageSize);
        int progressTotal = total > 0 ? total : Math.max(passport.getRowsCount(), firstPage.getRecords().size());
        progress.setNsiRowsTotal(progressTotal);

        List<RecordPayload> records = new ArrayList<RecordPayload>();
        records.addAll(firstPage.getRecords());
        update.setNsiRowsTotal(records.size());
        progress.addNsiRowsLoaded(firstPage.getRecords().size());
        progress.setGitLinksTotal(countGitLinks(records));
        applyProgress(update, progress);
        addRunLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.NSI_DATA, passport.getDictionaryKey(),
                null, null, null, null, "Страница НСИ загружена", null,
                pageDetails(1, pages, firstPage.getRecords().size(), records.size(), total));
        updateProgress(runId, SyncErrorStage.NSI_DATA, update);

        for (int page = 2; page <= pages; page++) {
            DataPage dataPage = client.fetchDataPage(passport, page, pageSize);
            records.addAll(dataPage.getRecords());
            update.setNsiRowsTotal(records.size());
            progress.addNsiRowsLoaded(dataPage.getRecords().size());
            progress.setGitLinksTotal(countGitLinks(records));
            applyProgress(update, progress);
            addRunLog(runId, SyncRunLogLevel.INFO, SyncErrorStage.NSI_DATA, passport.getDictionaryKey(),
                    null, null, null, null, "Страница НСИ загружена", null,
                    pageDetails(page, pages, dataPage.getRecords().size(), records.size(), total));
            updateProgress(runId, SyncErrorStage.NSI_DATA, update);
        }

        if (passport.getRowsCount() > 0 && passport.getRowsCount() != records.size()) {
            log.warn("NSI row count mismatch: oid={}, passportRows={}, loadedRows={}",
                    passport.getDictionaryKey(), passport.getRowsCount(), records.size());
        }
        if (total > 0 && total != records.size()) {
            throw new NsiSyncException("NSI total mismatch: oid=" + passport.getDictionaryKey()
                    + ", apiTotal=" + total + ", loadedRows=" + records.size());
        }

        return records;
    }

    private void applyProgress(NsiSyncRunUpdate update, SyncProgressTracker progress) {
        update.setProgressPercent(progress.runningPercent());
    }

    private void applyGitProgress(NsiSyncRunUpdate update,
                                  SyncProgressTracker progress,
                                  GitCommitTrackingResult gitResult,
                                  boolean finalResult) {
        if (gitResult.getProjectsTotal() <= 0 && gitResult.getProjectsChecked() <= 0) {
            if (finalResult) {
                applyProgress(update, progress);
            }
            return;
        }
        progress.updateGitLinks(gitResult.getProjectsChecked(), gitResult.getProjectsTotal());
        applyProgress(update, progress);
    }

    private int countGitLinks(List<RecordPayload> records) {
        int count = 0;
        for (RecordPayload record : records) {
            if (hasGitLink(record)) {
                count++;
            }
        }
        return count;
    }

    private boolean hasGitLink(RecordPayload record) {
        if (record == null || record.getData() == null) {
            return false;
        }
        String gitLink = record.getData().path("GIT_LINK").asText(null);
        return gitLink != null && !gitLink.trim().isEmpty();
    }

    private Map<String, Object> passportDetails(PassportDocument passport) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("dictionaryKey", passport.getDictionaryKey());
        details.put("version", passport.getVersion());
        details.put("rowsCount", Integer.valueOf(passport.getRowsCount()));
        return details;
    }

    private Map<String, Object> passportSaveDetails(PassportSaveResult result) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("dictionaryId", Long.valueOf(result.getDictionaryId()));
        details.put("versionId", Long.valueOf(result.getVersionId()));
        details.put("versionLoaded", Boolean.valueOf(result.isVersionLoaded()));
        return details;
    }

    private Map<String, Object> recordSaveDetails(NsiRecordSaveResult result) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("total", Integer.valueOf(result.getTotal()));
        details.put("inserted", Integer.valueOf(result.getInserted()));
        details.put("updated", Integer.valueOf(result.getUpdated()));
        details.put("deactivated", Integer.valueOf(result.getDeactivated()));
        return details;
    }

    private Map<String, Object> pageDetails(int page, int pages, int pageRows, int loadedRows, int totalRows) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put("page", Integer.valueOf(page));
        details.put("pages", Integer.valueOf(pages));
        details.put("pageRows", Integer.valueOf(pageRows));
        details.put("loadedRows", Integer.valueOf(loadedRows));
        details.put("totalRows", Integer.valueOf(totalRows));
        return details;
    }

    private Map<String, Object> valueDetails(String key, Object value) {
        Map<String, Object> details = new LinkedHashMap<String, Object>();
        details.put(key, value);
        return details;
    }

}
