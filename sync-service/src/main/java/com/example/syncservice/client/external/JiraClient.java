package com.example.syncservice.client.external;

import com.example.syncservice.dto.JiraIssueDto;
import com.example.syncservice.service.FallbackSignal;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Client for Jira REST API.
 * 
 * CRITICAL DESIGN:
 * - WebClient for non-blocking calls
 * - Must be called OUTSIDE @Transactional
 * - Rate limit: 10 req/sec (Jira Cloud)
 * - Exponential backoff with jitter
 * - Circuit breaker protects against cascading failures
 * - Handles 429 Too Many Requests explicitly
 */
@Component
@Slf4j
public class JiraClient {

    private final WebClient jiraWebClient;
    private final FallbackSignal fallbackSignal;

    public JiraClient(@Qualifier("jiraWebClient") WebClient jiraWebClient,
                      FallbackSignal fallbackSignal) {
        this.jiraWebClient = jiraWebClient;
        this.fallbackSignal = fallbackSignal;
    }

    /**
     * Fetch issues from Jira project.
     * 
     * Query: project = {projectKey} AND updated >= startAt
     * 
     * @param hostUrl Jira host URL (e.g., https://company.atlassian.net)
     * @param apiToken Jira API token (Basic auth)
     * @param projectKey Jira project key (e.g., SWP391)
     * @param maxResults Max results per request (default 100)
     * @return List of Jira issues
     */
    @Retry(name = "jiraRetry", fallbackMethod = "fetchIssuesFallback")
    @CircuitBreaker(name = "jiraCircuitBreaker", fallbackMethod = "fetchIssuesFallback")
    @RateLimiter(name = "jiraRateLimiter")
    public List<JiraIssueDto> fetchIssues(String hostUrl, String apiToken, String projectKey, int maxResults) {
        log.debug("Fetching Jira issues for project={}, hostUrl={}", projectKey, hostUrl);

        String authHeader = "Basic " + java.util.Base64.getEncoder()
                .encodeToString(("x:" + apiToken).getBytes());

        String jql = String.format("project = %s ORDER BY updated DESC", projectKey);

        try {
            Map<String, Object> response = jiraWebClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .scheme("https")
                            .host(extractHost(hostUrl))
                            .path("/rest/api/3/search")
                            .queryParam("jql", jql)
                            .queryParam("maxResults", maxResults)
                            .queryParam("fields", "summary,description,issuetype,status,assignee,reporter,priority,created,updated")
                            .build())
                    .header("Authorization", authHeader)
                    .header("Accept", "application/json")
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, clientResponse -> {
                        if (clientResponse.statusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                            log.warn("Jira rate limit exceeded (429)");
                            return Mono.error(new RateLimitExceededException("Jira rate limit exceeded"));
                        } else if (clientResponse.statusCode() == HttpStatus.UNAUTHORIZED) {
                            log.error("Jira authentication failed (401)");
                            return Mono.error(new AuthenticationException("Jira API token invalid"));
                        }
                        return clientResponse.createException();
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, clientResponse -> {
                        log.error("Jira server error: {}", clientResponse.statusCode());
                        return clientResponse.createException();
                    })
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .timeout(Duration.ofSeconds(30))
                    .block();

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> issues = (List<Map<String, Object>>) response.get("issues");
            
            if (issues == null || issues.isEmpty()) {
                log.info("No issues found for project={}", projectKey);
                return List.of();
            }

            log.info("Fetched {} issues from Jira project={}", issues.size(), projectKey);
            return convertToJiraIssueDtos(issues);

        } catch (RateLimitExceededException e) {
            log.error("Jira rate limit exceeded for project={}", projectKey);
            throw e;
        } catch (AuthenticationException e) {
            log.error("Jira authentication failed for project={}", projectKey);
            throw e;
        } catch (Exception e) {
            log.error("Error fetching Jira issues for project={}: {}", projectKey, e.getMessage(), e);
            throw new JiraClientException("Failed to fetch Jira issues: " + e.getMessage(), e);
        }
    }

    /**
     * Convert raw JSON to JiraIssueDto objects.
     */
    private List<JiraIssueDto> convertToJiraIssueDtos(List<Map<String, Object>> issues) {
        return issues.stream()
                .map(this::mapToJiraIssueDto)
                .toList();
    }

    /**
     * Map raw JSON to JiraIssueDto.
     */
    @SuppressWarnings("unchecked")
    private JiraIssueDto mapToJiraIssueDto(Map<String, Object> issue) {
        String key = (String) issue.get("key");
        String id = (String) issue.get("id");
        Map<String, Object> fields = (Map<String, Object>) issue.get("fields");

        JiraIssueDto.Fields dtoFields = new JiraIssueDto.Fields();
        dtoFields.setSummary((String) fields.get("summary"));
        dtoFields.setDescription((String) fields.get("description"));
        dtoFields.setCreated((String) fields.get("created"));
        dtoFields.setUpdated((String) fields.get("updated"));

        // IssueType
        Map<String, Object> issueType = (Map<String, Object>) fields.get("issuetype");
        if (issueType != null) {
            dtoFields.setIssueType(new JiraIssueDto.IssueType((String) issueType.get("name")));
        }

        // Status
        Map<String, Object> status = (Map<String, Object>) fields.get("status");
        if (status != null) {
            dtoFields.setStatus(new JiraIssueDto.Status((String) status.get("name")));
        }

        // Assignee
        Map<String, Object> assignee = (Map<String, Object>) fields.get("assignee");
        if (assignee != null) {
            dtoFields.setAssignee(new JiraIssueDto.User(
                    (String) assignee.get("emailAddress"),
                    (String) assignee.get("displayName")));
        }

        // Reporter
        Map<String, Object> reporter = (Map<String, Object>) fields.get("reporter");
        if (reporter != null) {
            dtoFields.setReporter(new JiraIssueDto.User(
                    (String) reporter.get("emailAddress"),
                    (String) reporter.get("displayName")));
        }

        // Priority
        Map<String, Object> priority = (Map<String, Object>) fields.get("priority");
        if (priority != null) {
            dtoFields.setPriority(new JiraIssueDto.Priority((String) priority.get("name")));
        }

        return JiraIssueDto.builder()
                .key(key)
                .id(id)
                .fields(dtoFields)
                .build();
    }

    /**
     * Fallback method for fetchIssues.
     * Called when retry exhausted or circuit breaker open.
     * 
     * CRITICAL: Sets degraded execution flag for orchestrator to detect.
     */
    private List<JiraIssueDto> fetchIssuesFallback(String hostUrl, String apiToken, String projectKey, int maxResults, Throwable throwable) {
        String errorMsg = String.format("Jira API unavailable for project=%s, host=%s: %s", 
                projectKey, hostUrl, throwable.getMessage());
        
        log.warn("⚠️ FALLBACK TRIGGERED: {}", errorMsg, throwable);
        
        // Signal degraded execution to orchestrator
        fallbackSignal.setDegraded(true, errorMsg);
        
        // Return safe empty result (prevents exception propagation)
        return List.of();
    }

    /**
     * Extract host from URL.
     */
    private String extractHost(String url) {
        return url.replace("https://", "").replace("http://", "").split("/")[0];
    }

    // Custom exceptions
    public static class JiraClientException extends RuntimeException {
        public JiraClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    public static class AuthenticationException extends RuntimeException {
        public AuthenticationException(String message) {
            super(message);
        }
    }
}
