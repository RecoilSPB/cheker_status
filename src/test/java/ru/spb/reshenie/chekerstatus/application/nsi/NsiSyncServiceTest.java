package ru.spb.reshenie.chekerstatus.application.nsi;

import ru.spb.reshenie.chekerstatus.domain.nsi.DataPage;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportDocument;
import ru.spb.reshenie.chekerstatus.domain.nsi.PassportSaveResult;
import ru.spb.reshenie.chekerstatus.domain.nsi.RecordPayload;
import ru.spb.reshenie.chekerstatus.infrastructure.nsi.NsiClient;
import ru.spb.reshenie.chekerstatus.infrastructure.nsi.NsiPersistenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import ru.spb.reshenie.chekerstatus.infrastructure.config.NsiProperties;
import ru.spb.reshenie.chekerstatus.application.gitlab.GitCommitTrackingService;
import ru.spb.reshenie.chekerstatus.application.sync.NsiSyncRunService;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NsiSyncServiceTest {

    private final NsiClient client = mock(NsiClient.class);
    private final NsiPersistenceService persistenceService = mock(NsiPersistenceService.class);
    private final GitCommitTrackingService gitCommitTrackingService = mock(GitCommitTrackingService.class);
    private final NsiSyncRunService syncRunService = mock(NsiSyncRunService.class);
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

    private RecordPayload record(ObjectMapper objectMapper) {
        ArrayNode row = objectMapper.createArrayNode();
        ObjectNode cell = row.addObject();
        cell.put("column", "OID");
        cell.put("value", "1.2.3");
        return RecordPayload.fromApiRow(objectMapper, 1, row, Collections.<String>emptyList());
    }
}
