package com.example.reportservice.client;

import com.example.reportservice.config.InternalServiceProperties;
import com.example.reportservice.support.AuthenticatedRequestSupport;
import com.example.reportservice.web.UpstreamServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityNotFoundException;
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

import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SyncJobClient {

    private final RestTemplate restTemplate;
    private final InternalServiceProperties properties;
    private final AuthenticatedRequestSupport requestSupport;

    /**
     * Returns the total number of sync jobs with a given status.
     * Uses sync-service paging metadata: data.totalElements.
     */
    public long countJobsByStatus(String status) {
        JsonNode body = listJobs(null, null, status, 0, 1);
        return body.path("data").path("totalElements").asLong(0);
    }

    /**
     * Returns latest job status for a given jobType (global, across all project configs).
     * If there are no jobs for this type, returns null.
     */
    public String findLatestStatusByJobType(String jobType) {
        JsonNode body = listJobs(null, jobType, null, 0, 1);
        JsonNode content = body.path("data").path("content");
        if (!content.isArray() || content.isEmpty()) {
            return null;
        }
        String status = content.get(0).path("status").asText(null);
        return status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * Returns latest COMPLETED job completedAt timestamp (ISO string) for a given project config + jobType.
     * Used when you want per-project sync freshness. Returns null when missing.
     */
    public String findLatestCompletedAt(UUID projectConfigId, String jobType) {
        JsonNode body = listJobs(projectConfigId, jobType, "COMPLETED", 0, 1);
        JsonNode content = body.path("data").path("content");
        if (!content.isArray() || content.isEmpty()) {
            return null;
        }
        JsonNode completedAt = content.get(0).path("completedAt");
        return completedAt.isMissingNode() || completedAt.isNull() ? null : completedAt.asText(null);
    }

    private JsonNode listJobs(UUID projectConfigId, String jobType, String status, int page, int size) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(properties.getSyncServiceBaseUrl())
            .path("/api/sync/jobs")
            .queryParam("page", page)
            .queryParam("size", size);
        if (projectConfigId != null) {
            builder.queryParam("projectConfigId", projectConfigId);
        }
        if (jobType != null && !jobType.isBlank()) {
            builder.queryParam("jobType", jobType);
        }
        if (status != null && !status.isBlank()) {
            builder.queryParam("status", status);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        requestSupport.applyCallerHeaders(headers);

        try {
            ResponseEntity<JsonNode> response = restTemplate.exchange(
                builder.toUriString(),
                HttpMethod.GET,
                new HttpEntity<>(headers),
                JsonNode.class
            );
            return response.getBody();
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode() == HttpStatus.NOT_FOUND) {
                throw new EntityNotFoundException("Requested resource not found");
            }
            throw new UpstreamServiceException("sync-service unavailable", ex);
        } catch (RestClientException ex) {
            throw new UpstreamServiceException("sync-service unavailable", ex);
        }
    }
}

