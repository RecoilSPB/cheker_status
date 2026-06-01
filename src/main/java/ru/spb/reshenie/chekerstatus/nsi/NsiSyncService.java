package ru.spb.reshenie.chekerstatus.nsi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import ru.spb.reshenie.chekerstatus.config.NsiProperties;
import ru.spb.reshenie.chekerstatus.gitlab.GitCommitTrackingService;

import java.util.ArrayList;
import java.util.List;

@Service
public class NsiSyncService {

    private static final Logger log = LoggerFactory.getLogger(NsiSyncService.class);

    private final NsiClient client;
    private final NsiPersistenceService persistenceService;
    private final GitCommitTrackingService gitCommitTrackingService;
    private final NsiProperties properties;

    public NsiSyncService(NsiClient client,
                          NsiPersistenceService persistenceService,
                          GitCommitTrackingService gitCommitTrackingService,
                          NsiProperties properties) {
        this.client = client;
        this.persistenceService = persistenceService;
        this.gitCommitTrackingService = gitCommitTrackingService;
        this.properties = properties;
    }

    public void synchronizeConfiguredDictionaries(boolean forceReload) {
        if (properties.getDictionaries() == null) {
            log.warn("No NSI dictionaries configured");
            return;
        }
        for (NsiProperties.Dictionary dictionary : properties.getDictionaries()) {
            if (!dictionary.isEnabled() || dictionary.getIdentifier() == null) {
                continue;
            }
            try {
                synchronizeDictionary(dictionary.getIdentifier(), forceReload);
            } catch (RuntimeException e) {
                log.error("NSI dictionary synchronization failed: identifier={}", dictionary.getIdentifier(), e);
            }
        }
    }

    public void synchronizeDictionary(String identifier, boolean forceReload) {
        PassportDocument passport = client.fetchPassport(identifier);
        PassportSaveResult savedPassport = persistenceService.savePassport(passport);

        if (savedPassport.isVersionLoaded() && !forceReload) {
            log.info("NSI dictionary is up to date: oid={}, version={}",
                    passport.getDictionaryKey(), passport.getVersion());
            gitCommitTrackingService.synchronizeDictionaryDocuments(savedPassport.getDictionaryId());
            return;
        }

        List<RecordPayload> records = downloadAllRecords(passport);
        persistenceService.saveLoadedRecords(
                savedPassport.getDictionaryId(),
                savedPassport.getVersionId(),
                records
        );

        log.info("NSI dictionary loaded: oid={}, version={}, records={}",
                passport.getDictionaryKey(), passport.getVersion(), records.size());
        gitCommitTrackingService.synchronizeDictionaryDocuments(savedPassport.getDictionaryId());
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
            log.warn("NSI total mismatch: oid={}, apiTotal={}, loadedRows={}",
                    passport.getDictionaryKey(), total, records.size());
        }

        return records;
    }
}
