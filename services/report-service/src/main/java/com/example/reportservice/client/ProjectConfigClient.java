package com.example.reportservice.client;

import com.example.reportservice.config.InternalServiceProperties;
import com.example.reportservice.support.AuthenticatedRequestSupport;
import com.example.reportservice.web.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ProjectConfigClient {

    private final RestTemplate restTemplate;
    private final InternalServiceProperties properties;
    private final AuthenticatedRequestSupport requestSupport;

    public Optional<ProjectConfigSnapshot> getConfigByGroupId(Long groupId) {
        String url = UriComponentsBuilder.fromHttpUrl(properties.getProjectConfigBaseUrl())
            .path("/api/project-configs/group/{groupId}")
            .buildAndExpand(groupId)
            .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        requestSupport.applyCallerHeaders(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
            );
            return Optional.of(parseConfig(response.getBody()));
        } catch (HttpStatusCodeException ex) {
            // Missing config is normal.
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                return Optional.empty();
            }
            // Some configs may exist but have unreadable encrypted credentials (e.g. bad key rotation).
            // Treat them as "not usable" for dashboard aggregation instead of failing the whole overview.
            if (ex.getStatusCode() == HttpStatus.CONFLICT) {
                return Optional.empty();
            }
            throw new UpstreamServiceException("project-config-service unavailable", ex);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("project-config-service unavailable", ex);
        }
    }

    private ProjectConfigSnapshot parseConfig(JsonNode body) {
        JsonNode data = body.path("data");
        return new ProjectConfigSnapshot(
            UUID.fromString(data.path("id").asText()),
            data.path("groupId").asLong(),
            textOrNull(data, "state"),
            textOrNull(data, "jiraHostUrl"),
            textOrNull(data, "githubRepoUrl")
        );
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode field = node.path(fieldName);
        return field.isMissingNode() || field.isNull() ? null : field.asText();
    }

    public record ProjectConfigSnapshot(
        UUID configId,
        Long groupId,
        String state,
        String jiraHostUrl,
        String githubRepoUrl
    ) {
    }
}