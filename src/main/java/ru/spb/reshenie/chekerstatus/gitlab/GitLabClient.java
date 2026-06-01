package ru.spb.reshenie.chekerstatus.gitlab;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriUtils;
import ru.spb.reshenie.chekerstatus.config.GitLabProperties;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class GitLabClient {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final GitLabProperties properties;

    public GitLabClient(RestTemplate restTemplate,
                        ObjectMapper objectMapper,
                        GitLabProperties properties) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public List<GitLabCommit> fetchAllCommits(DocumentGitLink document) {
        int page = 1;
        int perPage = normalizedPerPage();
        int maxPages = Math.max(1, properties.getMaxPages());
        List<GitLabCommit> commits = new ArrayList<GitLabCommit>();

        while (page <= maxPages) {
            ResponseEntity<String> response = getCommitsPage(document, page, perPage);
            List<GitLabCommit> pageCommits = parseCommits(response.getBody(), document);
            commits.addAll(pageCommits);

            String nextPage = response.getHeaders().getFirst("X-Next-Page");
            if (StringUtils.hasText(nextPage)) {
                page = Integer.parseInt(nextPage);
                continue;
            }
            if (pageCommits.size() < perPage) {
                break;
            }
            page++;
        }

        return commits;
    }

    private ResponseEntity<String> getCommitsPage(DocumentGitLink document, int page, int perPage) {
        URI uri = commitsUri(document, page, perPage);
        try {
            return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<Void>(headers()), String.class);
        } catch (HttpStatusCodeException e) {
            throw new GitLabClientException("GitLab commits request failed: document="
                    + document.getRecordKey() + ", status=" + e.getRawStatusCode()
                    + ", response=" + trim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new GitLabClientException("Cannot call GitLab commits API: document="
                    + document.getRecordKey(), e);
        }
    }

    private URI commitsUri(DocumentGitLink document, int page, int perPage) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String encodedProject = UriUtils.encodePathSegment(document.getProjectPath(), StandardCharsets.UTF_8.name());
        String encodedRef = UriUtils.encodeQueryParam(document.getTreeRef(), StandardCharsets.UTF_8.name());
        String url = baseUrl + "/api/v4/projects/" + encodedProject + "/repository/commits"
                + "?ref_name=" + encodedRef
                + "&per_page=" + perPage
                + "&page=" + page;
        return URI.create(url);
    }

    private HttpHeaders headers() {
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        if (StringUtils.hasText(properties.getToken())) {
            headers.set("PRIVATE-TOKEN", properties.getToken());
        }
        return headers;
    }

    private List<GitLabCommit> parseCommits(String body, DocumentGitLink document) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                throw new GitLabClientException("Unexpected GitLab commits response for document="
                        + document.getRecordKey() + ": " + trim(body));
            }

            List<GitLabCommit> commits = new ArrayList<GitLabCommit>();
            for (JsonNode item : root) {
                GitLabCommit commit = GitLabCommit.fromJson(item);
                if (commit.getId() != null) {
                    commits.add(commit);
                }
            }
            return commits;
        } catch (IOException e) {
            throw new GitLabClientException("Cannot parse GitLab commits response for document="
                    + document.getRecordKey(), e);
        }
    }

    private int normalizedPerPage() {
        if (properties.getPerPage() < 1) {
            return 100;
        }
        return Math.min(100, properties.getPerPage());
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "https://git.minzdrav.gov.ru";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    private String trim(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ');
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }
}
