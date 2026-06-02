package ru.spb.reshenie.chekerstatus.nsi;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.spb.reshenie.chekerstatus.config.NsiProperties;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class NsiClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final NsiProperties properties;

    public NsiClient(RestTemplate restTemplate, ObjectMapper objectMapper, NsiProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public PassportDocument fetchPassport(String identifier) {
        JsonNode root = getJson(properties.getPassportUrlTemplate(), variables(identifier, null, null), "passport");
        validateResult(root, "passport", identifier);
        return PassportDocument.fromJson(identifier, root);
    }

    public DataPage fetchDataPage(PassportDocument passport, int page, int size) {
        Map<String, Object> variables = variables(passport.getRequestedIdentifier(), page, size);
        JsonNode root = getJson(properties.getDataUrlTemplate(), variables, "data page " + page);
        validateResult(root, "data page " + page, passport.getRequestedIdentifier());

        int total = JsonNodes.intValue(root, "total", 0);
        List<RecordPayload> records = new ArrayList<RecordPayload>();
        JsonNode list = root.path("list");
        if (!list.isArray()) {
            if (total > 0) {
                throw new NsiClientException("NSI data page " + page + " has no list array: identifier="
                        + passport.getRequestedIdentifier() + ", total=" + total);
            }
            return new DataPage(page, size, total, records);
        }

        int offset = (page - 1) * size;
        int index = 0;
        for (JsonNode row : list) {
            records.add(RecordPayload.fromApiRow(
                    objectMapper,
                    offset + index + 1,
                    row,
                    passport.getPrimaryKeys()
            ));
            index++;
        }

        return new DataPage(page, size, total, records);
    }

    private JsonNode getJson(String urlTemplate, Map<String, Object> variables, String context) {
        try {
            String body = restTemplate.getForObject(urlTemplate, String.class, variables);
            if (body == null || body.trim().isEmpty()) {
                throw new NsiClientException("Empty NSI response for " + context);
            }
            return objectMapper.readTree(body);
        } catch (RestClientException e) {
            throw new NsiClientException("Cannot call NSI API for " + context, e);
        } catch (IOException e) {
            throw new NsiClientException("Cannot parse NSI response for " + context, e);
        }
    }

    private void validateResult(JsonNode root, String context, String identifier) {
        String result = JsonNodes.text(root, "result");
        if (!"OK".equalsIgnoreCase(result)) {
            String resultText = JsonNodes.text(root, "resultText");
            throw new NsiClientException("NSI " + context + " request failed for " + identifier
                    + ": result=" + result + ", resultText=" + resultText);
        }
    }

    private Map<String, Object> variables(String identifier, Integer page, Integer size) {
        Map<String, Object> variables = new HashMap<String, Object>();
        variables.put("identifier", identifier);
        variables.put("userKey", properties.getUserKey());
        if (page != null) {
            variables.put("page", page);
        }
        if (size != null) {
            variables.put("size", size);
        }
        return variables;
    }
}
