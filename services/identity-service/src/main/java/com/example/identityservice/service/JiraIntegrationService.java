package com.example.identityservice.service;

import com.example.identityservice.exception.BadGatewayException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraIntegrationService {

    private final RestTemplate restTemplate;

    @Value("${jira.host:}")
    private String jiraHost;

    @Value("${jira.email:}")
    private String jiraEmail;

    @Value("${jira.api-token:}")
    private String jiraApiToken;

    public String findAccountIdByEmail(String email) {
        ensureConfigured();
        String normalizedEmail = requireValue(email, "email");

        // Strategy:
        // 1) Try direct email query (may return empty due to Jira Cloud privacy)
        // 2) Fallback to local-part (temporary workaround)
        List<String> queries = List.of(
            normalizedEmail,
            extractLocalPart(normalizedEmail)
        ).stream().filter(Objects::nonNull).distinct().toList();

        for (String query : queries) {
            String accountId = findFirstAccountIdByQuery(query, normalizedEmail);
            if (accountId != null && !accountId.isBlank()) {
                return accountId;
            }
        }

        throw new EntityNotFoundException("No Jira user found for email");
    }

    public List<JiraUserResult> searchUsers(String query) {
        ensureConfigured();
        String normalizedQuery = requireValue(query, "query");

        String url = buildUserSearchUrl(normalizedQuery);
        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();

            int status = response.getStatusCode().value();
            String bodyText = body == null ? "null" : body.toString();
            log.info("Jira response: status={}, body={}, query={}", status, bodyText, maskQuery(normalizedQuery));

            if (body == null || !body.isArray() || body.isEmpty()) {
                return Collections.emptyList();
            }

            return streamArray(body).stream()
                .map(node -> new JiraUserResult(
                    node.path("accountId").asText(null),
                    node.path("displayName").asText(null)
                ))
                .filter(r -> r.accountId() != null && !r.accountId().isBlank())
                .toList();
        } catch (HttpStatusCodeException ex) {
            log.warn("Jira user search failed: status={}, url={}, body={}", ex.getStatusCode().value(), url, ex.getResponseBodyAsString());
            throw new BadGatewayException("Jira API failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.warn("Jira user search unavailable: url={}", url, ex);
            throw new BadGatewayException("Jira API unavailable", ex);
        }
    }

    private String findFirstAccountIdByQuery(String query, String originalEmailForMask) {
        String baseHost = jiraHost.trim();
        if (!baseHost.startsWith("http://") && !baseHost.startsWith("https://")) {
            // Jira Cloud host is typically <site>.atlassian.net; default to https
            baseHost = "https://" + baseHost;
        }
        while (baseHost.endsWith("/")) {
            baseHost = baseHost.substring(0, baseHost.length() - 1);
        }

        String url = UriComponentsBuilder.fromUriString(baseHost)
            .path("/rest/api/3/user/search")
            .queryParam("query", query)
            .build()
            .toUriString();

        try {
            HttpEntity<Void> entity = new HttpEntity<>(authHeaders());
            ResponseEntity<JsonNode> response = restTemplate.exchange(url, HttpMethod.GET, entity, JsonNode.class);
            JsonNode body = response.getBody();

            // IMPORTANT: log even for 200 OK + empty array (common Jira Cloud privacy behavior)
            int status = response.getStatusCode().value();
            String bodyText = body == null ? "null" : body.toString();
            log.info("Jira response: status={}, body={}, query={}", status, bodyText, maskEmail(originalEmailForMask, query));

            if (body == null || !body.isArray() || body.isEmpty()) {
                return null;
            }
            String accountId = body.get(0).path("accountId").asText(null);
            if (accountId == null || accountId.isBlank()) {
                return null;
            }
            return accountId;
        } catch (HttpStatusCodeException ex) {
            log.warn("Jira user search failed: status={}, url={}, body={}", ex.getStatusCode().value(), url, ex.getResponseBodyAsString());
            throw new BadGatewayException("Jira API failed (" + ex.getStatusCode().value() + ")", ex);
        } catch (RestClientException ex) {
            log.warn("Jira user search unavailable: url={}", url, ex);
            throw new BadGatewayException("Jira API unavailable", ex);
        }
    }

    private String buildUserSearchUrl(String query) {
        String baseHost = jiraHost.trim();
        if (!baseHost.startsWith("http://") && !baseHost.startsWith("https://")) {
            baseHost = "https://" + baseHost;
        }
        while (baseHost.endsWith("/")) {
            baseHost = baseHost.substring(0, baseHost.length() - 1);
        }
        return UriComponentsBuilder.fromUriString(baseHost)
            .path("/rest/api/3/user/search")
            .queryParam("query", query)
            .build()
            .toUriString();
    }

    private static List<JsonNode> streamArray(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        java.util.ArrayList<JsonNode> list = new java.util.ArrayList<>();
        node.forEach(list::add);
        return list;
    }

    private static String extractLocalPart(String email) {
        if (email == null) return null;
        int idx = email.indexOf('@');
        if (idx <= 0) return null;
        String local = email.substring(0, idx).trim();
        return local.isBlank() ? null : local;
    }

    private static String maskQuery(String query) {
        if (query == null) return null;
        // If query looks like an email, mask it; otherwise return as-is (it's typically a name keyword)
        if (query.contains("@")) {
            return maskEmail(query, query);
        }
        return query;
    }

    private static String maskEmail(String email, String query) {
        if (query == null) return null;
        if (!query.contains("@")) {
            return query; // local-part fallback is not an email; safe to log as-is
        }
        if (email == null || !email.contains("@")) return "***@***";
        int at = email.indexOf('@');
        String domain = email.substring(at + 1);
        return "***@" + domain;
    }

    private HttpHeaders authHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));

        String credentials = jiraEmail + ":" + jiraApiToken;
        String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.set(HttpHeaders.AUTHORIZATION, "Basic " + encoded);
        headers.set("X-Atlassian-Token", "no-check");
        return headers;
    }

    private void ensureConfigured() {
        if (jiraHost == null || jiraHost.isBlank()
            || jiraEmail == null || jiraEmail.isBlank()
            || jiraApiToken == null || jiraApiToken.isBlank()) {
            throw new BadGatewayException("Jira integration is not configured (required: JIRA_HOST, JIRA_EMAIL, JIRA_API_TOKEN)");
        }
    }

    private String requireValue(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required value: " + fieldName);
        }
        return value.trim();
    }

    public record JiraUserResult(String accountId, String displayName) {}
}

