package com.example.reportservice.service.impl;

import com.example.reportservice.service.JiraService;
import com.example.reportservice.web.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class JiraServiceImpl implements JiraService {

    private final RestTemplate restTemplate;

    @Value("${jira.host:}")
    private String jiraHost;

    @Value("${jira.email:}")
    private String jiraEmail;

    @Value("${jira.api-token:}")
    private String jiraApiToken;

    @Override
    public void assignIssue(String issueKey, String accountId) {
        ensureConfigured();
        String normalizedIssueKey = requireValue(issueKey, "issueKey");
        String normalizedAccountId = requireValue(accountId, "accountId");

        String url = issueApiUrl(normalizedIssueKey, "/assignee");
        Map<String, String> payload = Map.of("accountId", normalizedAccountId);

        log.info("Jira assign request: issueKey={}, accountId={}, url={}", normalizedIssueKey, normalizedAccountId, url);
        exchangeWithoutResponseBody(HttpMethod.PUT, url, payload, "assign issue");
    }

    @Override
    public String transitionIssueToStatus(String issueKey, String status) {
        ensureConfigured();
        String normalizedIssueKey = requireValue(issueKey, "issueKey");
        String desiredStatus = requireValue(status, "status");

        String transitionsUrl = issueApiUrl(normalizedIssueKey, "/transitions");
        log.info("Jira transitions request: issueKey={}, desiredStatus={}, url={}", normalizedIssueKey, desiredStatus, transitionsUrl);

        JsonNode transitionsResponse = exchangeForJson(HttpMethod.GET, transitionsUrl, null, "fetch transitions");
        TransitionResolution resolution = resolveTransition(transitionsResponse, desiredStatus);
        if (resolution == null || resolution.id() == null || resolution.id().isBlank()) {
            String currentStatus = fetchCurrentIssueStatus(normalizedIssueKey);
            if (matchesDesiredStatus(currentStatus, desiredStatus)) {
                log.info("Jira issue already satisfies desired status: issueKey={}, desiredStatus={}, currentStatus={}",
                    normalizedIssueKey, desiredStatus, currentStatus);
                return currentStatus;
            }
            throw new UpstreamServiceException("No Jira transition found for status '" + desiredStatus + "'");
        }

        Map<String, Object> payload = Map.of("transition", Map.of("id", resolution.id()));
        log.info("Jira transition request: issueKey={}, transitionId={}, desiredStatus={}, url={}",
            normalizedIssueKey, resolution.id(), desiredStatus, transitionsUrl);
        exchangeWithoutResponseBody(HttpMethod.POST, transitionsUrl, payload, "transition issue status");

        String resolvedStatusName = resolution.toStatusName() != null && !resolution.toStatusName().isBlank()
            ? resolution.toStatusName()
            : desiredStatus;
        log.info("Jira transition resolved status: issueKey={}, desiredStatus={}, resolvedStatus={}",
            normalizedIssueKey, desiredStatus, resolvedStatusName);
        return resolvedStatusName;
    }

    private JsonNode exchangeForJson(HttpMethod method, String url, Object payload, String operation) {
        try {
            HttpEntity<?> requestEntity = new HttpEntity<>(payload, authHeaders());
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, method, requestEntity, JsonNode.class);
            log.info("Jira {} response: status={}, url={}", operation, response.getStatusCode().value(), url);
            log.debug("Jira {} response body: {}", operation, response.getBody());
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            log.error("Jira {} failed: status={}, url={}, response={}", operation, ex.getStatusCode().value(), url, ex.getResponseBodyAsString());
            throw new UpstreamServiceException("Jira API failed to " + operation + " (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.error("Jira {} failed: url={}", operation, url, ex);
            throw new UpstreamServiceException("Jira API unavailable while trying to " + operation, ex);
        }
    }

    private void exchangeWithoutResponseBody(HttpMethod method, String url, Object payload, String operation) {
        try {
            HttpEntity<?> requestEntity = new HttpEntity<>(payload, authHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, method, requestEntity, String.class);
            log.info("Jira {} response: status={}, url={}", operation, response.getStatusCode().value(), url);
            log.debug("Jira {} response body: {}", operation, response.getBody());
        } catch (HttpStatusCodeException ex) {
            log.error("Jira {} failed: status={}, url={}, response={}", operation, ex.getStatusCode().value(), url, ex.getResponseBodyAsString());
            throw new UpstreamServiceException("Jira API failed to " + operation + " (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.error("Jira {} failed: url={}", operation, url, ex);
            throw new UpstreamServiceException("Jira API unavailable while trying to " + operation, ex);
        }
    }

    private TransitionResolution resolveTransition(JsonNode transitionsResponse, String desiredStatus) {
        if (transitionsResponse == null || !transitionsResponse.has("transitions")) {
            return null;
        }

        List<String> desiredCandidates = desiredStatusCandidates(desiredStatus);
        TransitionResolution fallback = null;

        for (JsonNode transition : transitionsResponse.path("transitions")) {
            String transitionName = transition.path("name").asText("");
            String transitionToName = transition.path("to").path("name").asText("");
            String transitionId = transition.path("id").asText(null);

            if (transitionId == null || transitionId.isBlank()) {
                continue;
            }

            String normalizedTransitionName = normalizeStatus(transitionName);
            String normalizedTransitionToName = normalizeStatus(transitionToName);

            for (String candidate : desiredCandidates) {
                if (normalizedTransitionToName.equals(candidate)) {
                    return new TransitionResolution(transitionId, transitionToName);
                }
                if (normalizedTransitionName.equals(candidate) && fallback == null) {
                    fallback = new TransitionResolution(transitionId, transitionToName);
                }
            }
        }

        return fallback;
    }

    private List<String> desiredStatusCandidates(String desiredStatus) {
        String normalized = normalizeStatus(desiredStatus);
        List<String> candidates = new ArrayList<>();
        candidates.add(normalized);

        switch (normalized) {
            case "DONE" -> candidates.addAll(List.of("APPROVED", "APPROVE", "CLOSED", "RESOLVED", "COMPLETED"));
            case "IN PROGRESS" -> candidates.addAll(List.of(
                "IN DESIGN",
                "USER TESTING",
                "LIVE",
                "DOING",
                "IN REVIEW",
                "REVIEW",
                "DEVELOPMENT",
                "IN DEVELOPMENT",
                "IMPLEMENTING",
                "TESTING",
                "QA",
                "UAT"
            ));
            case "TODO" -> candidates.addAll(List.of("TO DO", "OPEN", "BACKLOG", "SELECTED FOR DEVELOPMENT"));
            default -> {
            }
        }
        return candidates;
    }

    private String fetchCurrentIssueStatus(String issueKey) {
        String url = issueApiUrl(issueKey, "") + "?fields=status";
        JsonNode issueResponse = exchangeForJson(HttpMethod.GET, url, null, "fetch issue status");
        String status = issueResponse == null ? null : issueResponse.path("fields").path("status").path("name").asText(null);
        return status == null || status.isBlank() ? "UNKNOWN" : status;
    }

    private boolean matchesDesiredStatus(String currentStatus, String desiredStatus) {
        String normalizedCurrent = normalizeStatus(currentStatus);
        return desiredStatusCandidates(desiredStatus).stream().anyMatch(candidate -> candidate.equals(normalizedCurrent));
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String credentials = jiraEmail + ":" + jiraApiToken;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        return headers;
    }

    private String issueApiUrl(String issueKey, String suffix) {
        String baseHost = jiraHost == null ? "" : jiraHost.trim();
        while (baseHost.endsWith("/")) {
            baseHost = baseHost.substring(0, baseHost.length() - 1);
        }

        return UriComponentsBuilder.fromHttpUrl(baseHost)
            .path("/rest/api/3/issue/{issueKey}")
            .path(suffix)
            .buildAndExpand(issueKey)
            .toUriString();
    }

    private String normalizeStatus(String value) {
        return value == null
            ? ""
            : value.trim()
            .replace('_', ' ')
            .replace('-', ' ')
            .replaceAll("\\s+", " ")
            .toUpperCase();
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new UpstreamServiceException("Missing required Jira value: " + fieldName);
        }
        return value.trim();
    }

    private void ensureConfigured() {
        if (jiraHost == null || jiraHost.isBlank() || jiraEmail == null || jiraEmail.isBlank() || jiraApiToken == null || jiraApiToken.isBlank()) {
            throw new UpstreamServiceException("Jira integration is not configured (required: JIRA_HOST, JIRA_EMAIL, JIRA_API_TOKEN)");
        }
    }

    private record TransitionResolution(String id, String toStatusName) {
    }
}
