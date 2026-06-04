package ru.spb.reshenie.chekerstatus.nsi.service;

import ru.spb.reshenie.chekerstatus.config.nsi.NsiProperties;
import ru.spb.reshenie.chekerstatus.nsi.client.NsiClient;
import ru.spb.reshenie.chekerstatus.nsi.repository.NsiRepository;

import ru.spb.reshenie.chekerstatus.nsi.model.DataPage;
import ru.spb.reshenie.chekerstatus.nsi.model.NsiRecordSaveResult;
import ru.spb.reshenie.chekerstatus.nsi.model.PassportDocument;
import ru.spb.reshenie.chekerstatus.nsi.model.PassportSaveResult;
import ru.spb.reshenie.chekerstatus.nsi.model.RecordPayload;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitCommitTrackingResult;
import ru.spb.reshenie.chekerstatus.sync.model.NsiSyncRunUpdate;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.gitlab.service.GitCommitTrackingService;
import ru.spb.reshenie.chekerstatus.sync.service.SyncRunService;
import ru.spb.reshenie.chekerstatus.sync.model.SyncRunType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NsiSyncServiceTest {

    private final NsiClient client = mock(NsiClient.class);
    private final NsiRepository persistenceService = mock(NsiRepository.class);
    private final GitCommitTrackingService gitCommitTrackingService = mock(GitCommitTrackingService.class);
    private final SyncRunService syncRunService = mock(SyncRunService.class);
    private final NsiSyncService service = new NsiSyncService(
            client,
            persistenceService,
            gitCommitTrackingService,
            new NsiProperties(),
            syncRunService
    );

    @Test
    void doesNotSaveRecordsWhenLoadedTotalDoesNotMatchApiTotal() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        PassportDocument passport = PassportDocument.fromJson("identifier", objectMapper.readTree(
                "{\"result\":\"OK\",\"identifier\":\"identifier\",\"oid\":\"oid\",\"version\":\"1\",\"rowsCount\":2,\"fields\":[]}"
        ));
        RecordPayload record = record(objectMapper);

        when(client.fetchPassport("identifier")).thenReturn(passport);
        when(persistenceService.savePassport(passport)).thenReturn(new PassportSaveResult(10L, 20L, false));
        when(client.fetchDataPage(passport, 1, 100))
                .thenReturn(new DataPage(1, 100, 2, Collections.singletonList(record)));

        assertThatThrownBy(() -> service.synchronizeDictionary(1L, "identifier", false))
                .isInstanceOf(NsiSyncException.class)
                .hasMessageContaining("NSI total mismatch");

        verify(persistenceService, never()).saveLoadedRecords(10L, 20L, Collections.singletonList(record));
    }

    @Test
    void progressUsesLoadedRecordCountWithinNsiDataStep() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        NsiProperties properties = new NsiProperties();
        properties.setPageSize(2);
        PassportDocument passport = PassportDocument.fromJson("identifier", objectMapper.readTree(
                "{\"result\":\"OK\",\"identifier\":\"identifier\",\"oid\":\"oid\",\"version\":\"1\",\"rowsCount\":4,\"fields\":[]}"
        ));
        RecordPayload first = record(objectMapper, "1");
        RecordPayload second = record(objectMapper, "2");
        RecordPayload third = record(objectMapper, "3");
        RecordPayload fourth = record(objectMapper, "4");
        NsiSyncService localService = new NsiSyncService(
                client,
                persistenceService,
                gitCommitTrackingService,
                properties,
                syncRunService
        );
        List<NsiSyncRunUpdate> updates = new ArrayList<NsiSyncRunUpdate>();

        when(client.fetchPassport("identifier")).thenReturn(passport);
        when(persistenceService.savePassport(passport)).thenReturn(new PassportSaveResult(10L, 20L, false));
        when(client.fetchDataPage(passport, 1, 2))
                .thenReturn(new DataPage(1, 2, 4, Arrays.asList(first, second)));
        when(client.fetchDataPage(passport, 2, 2))
                .thenReturn(new DataPage(2, 2, 4, Arrays.asList(third, fourth)));
        when(persistenceService.saveLoadedRecords(eq(10L), eq(20L), any()))
                .thenReturn(new NsiRecordSaveResult(4, 4, 0, 0));
        when(gitCommitTrackingService.synchronizeDictionaryDocuments(eq(10L), eq(1L), eq("identifier"), any()))
                .thenReturn(new GitCommitTrackingResult());
        doAnswer(invocation -> {
            NsiSyncRunUpdate update = invocation.getArgument(2);
            updates.add(update.copy());
            return null;
        }).when(syncRunService).safeUpdateProgress(eq(1L), any(), any());

        localService.synchronizeDictionary(1L, "identifier", false);

        assertThat(updates)
                .extracting(NsiSyncRunUpdate::getProgressPercent)
                .contains(60, 99);
    }

    @Test
    void configuredDictionariesReportsFailedDictionary() {
        NsiProperties properties = new NsiProperties();
        NsiProperties.Dictionary dictionary = new NsiProperties.Dictionary();
        dictionary.setIdentifier("identifier");
        properties.setDictionaries(Collections.singletonList(dictionary));
        NsiSyncService localService = new NsiSyncService(
                client,
                persistenceService,
                gitCommitTrackingService,
                properties,
                syncRunService
        );

        when(syncRunService.startRun(SyncRunType.AUTO, "identifier", false)).thenReturn(1L);
        when(client.fetchPassport("identifier")).thenThrow(new NsiSyncException("boom"));

        assertThat(localService.synchronizeConfiguredDictionaries(false, SyncRunType.AUTO)).isTrue();
    }

    private RecordPayload record(ObjectMapper objectMapper) {
        return record(objectMapper, "1.2.3");
    }

    private RecordPayload record(ObjectMapper objectMapper, String oid) {
        ArrayNode row = objectMapper.createArrayNode();
        ObjectNode cell = row.addObject();
        cell.put("column", "OID");
        cell.put("value", oid);
        return RecordPayload.fromApiRow(objectMapper, 1, row, Collections.<String>emptyList());
    }
}
