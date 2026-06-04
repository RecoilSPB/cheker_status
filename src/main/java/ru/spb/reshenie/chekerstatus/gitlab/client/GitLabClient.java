package ru.spb.reshenie.chekerstatus.gitlab.client;

import ru.spb.reshenie.chekerstatus.gitlab.model.DocumentGitLink;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommit;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommitDetails;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabCommitDiff;
import ru.spb.reshenie.chekerstatus.gitlab.model.GitLabFileContentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import ru.spb.reshenie.chekerstatus.config.gitlab.GitLabProperties;

import java.io.IOException;
import java.net.URI;
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
        return fetchCommitsUntilKnown(document, null);
    }

    public List<GitLabCommit> fetchCommitsUntilKnown(DocumentGitLink document, String stopCommitSha) {
        int page = 1;
        int perPage = normalizedPerPage();
        int maxPages = Math.max(1, properties.getMaxPages());
        List<GitLabCommit> commits = new ArrayList<GitLabCommit>();

        while (page <= maxPages) {
            ResponseEntity<String> response = getCommitsPage(document, page, perPage);
            List<GitLabCommit> pageCommits = parseCommits(response.getBody(), document);
            for (GitLabCommit commit : pageCommits) {
                if (commit.getId() != null && commit.getId().equals(stopCommitSha)) {
                    return commits;
                }
                commits.add(commit);
            }

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
            return exchangeWithRetry(uri, String.class);
        } catch (HttpStatusCodeException e) {
            throw new GitLabClientException("GitLab commits request failed: document="
                    + document.getRecordKey() + ", status=" + e.getStatusCode().value()
                    + ", response=" + trim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new GitLabClientException("Cannot call GitLab commits API: document="
                    + document.getRecordKey(), e);
        }
    }

    private URI commitsUri(DocumentGitLink document, int page, int perPage) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String encodedProject = GitLabUrlEncoder.encodeProjectPath(document.getProjectPath());
        String encodedRef = GitLabUrlEncoder.encodeQueryValue(document.getTreeRef());
        String url = baseUrl + "/api/v4/projects/" + encodedProject + "/repository/commits"
                + "?ref_name=" + encodedRef
                + "&per_page=" + perPage
                + "&page=" + page;
        return URI.create(url);
    }

    public GitLabCommitDetails fetchCommitDetails(DocumentGitLink document, String commitSha) {
        URI uri = commitDetailsUri(document, commitSha);
        try {
            ResponseEntity<String> response = exchangeWithRetry(uri, String.class);
            JsonNode root = objectMapper.readTree(response.getBody());
            return GitLabCommitDetails.fromJson(root);
        } catch (HttpStatusCodeException e) {
            throw new GitLabClientException("GitLab commit details request failed: document="
                    + document.getRecordKey() + ", commit=" + commitSha + ", status=" + e.getStatusCode().value()
                    + ", response=" + trim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new GitLabClientException("Cannot call GitLab commit details API: document="
                    + document.getRecordKey() + ", commit=" + commitSha, e);
        } catch (IOException e) {
            throw new GitLabClientException("Cannot parse GitLab commit details response: document="
                    + document.getRecordKey() + ", commit=" + commitSha, e);
        }
    }

    public List<GitLabCommitDiff> fetchCommitDiff(DocumentGitLink document, String commitSha) {
        int page = 1;
        int perPage = normalizedPerPage();
        int maxPages = Math.max(1, properties.getMaxPages());
        List<GitLabCommitDiff> diffs = new ArrayList<GitLabCommitDiff>();

        while (page <= maxPages) {
            ResponseEntity<String> response = getCommitDiffPage(document, commitSha, page, perPage);
            List<GitLabCommitDiff> pageDiffs = parseCommitDiff(response.getBody(), document, commitSha);
            diffs.addAll(pageDiffs);

            String nextPage = response.getHeaders().getFirst("X-Next-Page");
            if (StringUtils.hasText(nextPage)) {
                page = Integer.parseInt(nextPage);
                continue;
            }
            if (pageDiffs.size() < perPage) {
                break;
            }
            page++;
        }

        return diffs;
    }

    public GitLabFileContentResult fetchRawFile(DocumentGitLink document, String filePath, String ref) {
        URI uri = rawFileUri(document, filePath, ref);
        try {
            ResponseEntity<byte[]> response = exchangeWithRetry(uri, byte[].class);
            return GitLabFileContentResult.found(response.getBody());
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                return GitLabFileContentResult.notFound();
            }
            throw new GitLabClientException("GitLab raw file request failed: document="
                    + document.getRecordKey() + ", file=" + filePath + ", ref=" + ref
                    + ", status=" + e.getStatusCode().value() + ", response=" + trim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new GitLabClientException("Cannot call GitLab raw file API: document="
                    + document.getRecordKey() + ", file=" + filePath + ", ref=" + ref, e);
        }
    }

    private ResponseEntity<String> getCommitDiffPage(DocumentGitLink document,
                                                     String commitSha,
                                                     int page,
                                                     int perPage) {
        URI uri = commitDiffUri(document, commitSha, page, perPage);
        try {
            return exchangeWithRetry(uri, String.class);
        } catch (HttpStatusCodeException e) {
            throw new GitLabClientException("GitLab commit diff request failed: document="
                    + document.getRecordKey() + ", commit=" + commitSha + ", status=" + e.getStatusCode().value()
                    + ", response=" + trim(e.getResponseBodyAsString()), e);
        } catch (RestClientException e) {
            throw new GitLabClientException("Cannot call GitLab commit diff API: document="
                    + document.getRecordKey() + ", commit=" + commitSha, e);
        }
    }

    private URI commitDetailsUri(DocumentGitLink document, String commitSha) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String encodedProject = GitLabUrlEncoder.encodeProjectPath(document.getProjectPath());
        String encodedSha = GitLabUrlEncoder.encodeProjectPath(commitSha);
        return URI.create(baseUrl + "/api/v4/projects/" + encodedProject + "/repository/commits/" + encodedSha);
    }

    private URI commitDiffUri(DocumentGitLink document, String commitSha, int page, int perPage) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String encodedProject = GitLabUrlEncoder.encodeProjectPath(document.getProjectPath());
        String encodedSha = GitLabUrlEncoder.encodeProjectPath(commitSha);
        String url = baseUrl + "/api/v4/projects/" + encodedProject + "/repository/commits/" + encodedSha + "/diff"
                + "?per_page=" + perPage
                + "&page=" + page;
        return URI.create(url);
    }

    private URI rawFileUri(DocumentGitLink document, String filePath, String ref) {
        String baseUrl = trimTrailingSlash(properties.getBaseUrl());
        String encodedProject = GitLabUrlEncoder.encodeProjectPath(document.getProjectPath());
        String encodedFilePath = GitLabUrlEncoder.encodeFilePath(filePath);
        String encodedRef = GitLabUrlEncoder.encodeQueryValue(ref);
        String url = baseUrl + "/api/v4/projects/" + encodedProject + "/repository/files/"
                + encodedFilePath + "/raw?ref=" + encodedRef;
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

    private List<GitLabCommitDiff> parseCommitDiff(String body, DocumentGitLink document, String commitSha) {
        if (body == null || body.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            JsonNode root = objectMapper.readTree(body);
            if (!root.isArray()) {
                throw new GitLabClientException("Unexpected GitLab commit diff response for document="
                        + document.getRecordKey() + ", commit=" + commitSha + ": " + trim(body));
            }

            List<GitLabCommitDiff> diffs = new ArrayList<GitLabCommitDiff>();
            for (JsonNode item : root) {
                diffs.add(GitLabCommitDiff.fromJson(item));
            }
            return diffs;
        } catch (IOException e) {
            throw new GitLabClientException("Cannot parse GitLab commit diff response for document="
                    + document.getRecordKey() + ", commit=" + commitSha, e);
        }
    }

    private <T> ResponseEntity<T> exchangeWithRetry(URI uri, Class<T> responseType) {
        int attempts = Math.max(1, properties.getRetryCount() + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<Void>(headers()), responseType);
            } catch (HttpStatusCodeException e) {
                if (attempt >= attempts || !isTemporary(e)) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            } catch (RestClientException e) {
                if (attempt >= attempts) {
                    throw e;
                }
                sleepBeforeRetry(attempt);
            }
        }
        throw new IllegalStateException("Retry loop finished unexpectedly");
    }

    private boolean isTemporary(HttpStatusCodeException e) {
        int status = e.getStatusCode().value();
        return status == 429 || status >= 500;
    }

    private void sleepBeforeRetry(int attempt) {
        long delay = Math.max(0L, properties.getRetryDelayMs()) * attempt;
        if (delay <= 0L) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitLabClientException("Interrupted while waiting for GitLab retry", e);
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
