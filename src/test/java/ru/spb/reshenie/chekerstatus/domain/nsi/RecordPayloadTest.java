package ru.spb.reshenie.chekerstatus.domain.nsi;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RecordPayloadTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void buildsRecordKeyFromPrimaryColumns() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("OID", "1.2.3");
        data.put("RELEASE", "7");

        String key = RecordPayload.buildRecordKey(data, Arrays.asList("OID", "RELEASE"), 10);

        assertThat(key).isEqualTo("OID=1.2.3|RELEASE=7");
    }

    @Test
    void usesRowNumberWhenPrimaryKeyIsMissing() {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("NAME", "value");

        String key = RecordPayload.buildRecordKey(data, Collections.singletonList("OID"), 10);

        assertThat(key).isEqualTo("ROW=10");
    }
}
