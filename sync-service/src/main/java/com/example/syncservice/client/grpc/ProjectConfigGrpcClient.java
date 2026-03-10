package com.example.syncservice.client.grpc;

import com.example.syncservice.exception.ConfigNotFoundException;
import com.example.syncservice.dto.ProjectConfigDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import com.example.syncservice.client.grpc.InternalGetDecryptedConfigRequest;
import com.example.syncservice.client.grpc.InternalGetDecryptedConfigResponse;
import com.example.syncservice.client.grpc.InternalListVerifiedConfigsRequest;
import com.example.syncservice.client.grpc.InternalListVerifiedConfigsResponse;
import com.example.syncservice.client.grpc.ProjectConfigInternalServiceGrpc;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.UUID;
import java.util.Objects;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * gRPC Client for calling Project Config Service.
 * 
 * CRITICAL DESIGN:
 * - Calls MUST be made OUTSIDE @Transactional boundaries
 * - Uses Resilience4j for retry and circuit breaker
 * - Timeout configured at gRPC channel level
 * - Service-to-service authentication via interceptor
 */
@Component
@Slf4j
public class ProjectConfigGrpcClient {

    @GrpcClient("project-config-service")
    private ProjectConfigInternalServiceGrpc.ProjectConfigInternalServiceBlockingStub projectConfigStub;

    private final Map<UUID, CachedConfig> configCache = new ConcurrentHashMap<>();
    private final long configCacheTtlMillis;

    public ProjectConfigGrpcClient(
            @Value("${sync.grpc.config-cache-ttl-seconds:30}") long configCacheTtlSeconds) {
        this.configCacheTtlMillis = Math.max(1, configCacheTtlSeconds) * 1000L;
    }

    /**
     * Get decrypted project config by ID.
     * Retrieves API tokens in plaintext for external API calls.
     * 
     * @param configId Project config ID
     * @return ProjectConfigDto with decrypted tokens
     * @throws GrpcClientException if gRPC call fails
     */
    @Retry(name = "grpcRetry")
    @CircuitBreaker(name = "grpcCircuitBreaker")
    public ProjectConfigDto getDecryptedConfig(UUID configId) {
        log.debug("Fetching decrypted config for configId={}", configId);

        CachedConfig cached = configCache.get(configId);
        long now = System.currentTimeMillis();
        if (cached != null && cached.expiresAtEpochMs() > now) {
            log.debug("Cache hit for configId={}", configId);
            return cached.value();
        }

        try {
            InternalGetDecryptedConfigRequest request = InternalGetDecryptedConfigRequest.newBuilder()
                    .setConfigId(configId.toString())
                    .build();

            InternalGetDecryptedConfigResponse response = projectConfigStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .internalGetDecryptedConfig(request);

            ProjectConfigDto dto = mapToDto(response);
                configCache.put(configId, new CachedConfig(dto, now + configCacheTtlMillis));
            log.debug("Successfully fetched config for configId={}", configId);
            return dto;

        } catch (StatusRuntimeException e) {
            log.error("gRPC error fetching config {}: {} - {}", 
                    configId, e.getStatus().getCode(), e.getStatus().getDescription());

            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.NOT_FOUND) {
                configCache.remove(configId);
                throw new ConfigNotFoundException(configId);
            }
            if (code == Status.Code.PERMISSION_DENIED || code == Status.Code.UNAUTHENTICATED) {
                throw new NonRetryableGrpcClientException("Service authentication failed", e);
            }
            if (code == Status.Code.INVALID_ARGUMENT || code == Status.Code.FAILED_PRECONDITION) {
                throw new NonRetryableGrpcClientException("Invalid request for config " + configId, e);
            }

            // Transient transport/server failures are retryable by resilience4j.
            throw new GrpcClientException("Transient gRPC failure: " + e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error fetching config {}: {}", configId, e.getMessage(), e);
            throw new NonRetryableGrpcClientException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * List all verified project configs.
     * Used by scheduler to determine which configs to sync.
     * 
     * @return List of verified project config IDs
     */
    @Retry(name = "grpcRetry")
    @CircuitBreaker(name = "grpcCircuitBreaker")
    public List<UUID> listVerifiedConfigIds() {
        log.debug("Fetching list of verified configs");

        try {
            InternalListVerifiedConfigsRequest request = InternalListVerifiedConfigsRequest.newBuilder()
                    .build();

            InternalListVerifiedConfigsResponse response = projectConfigStub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .internalListVerifiedConfigs(request);

                List<UUID> configIds = response.getConfigsList().stream()
                    .map(config -> parseConfigId(config.getConfigId()))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            log.info("Found {} verified configs", configIds.size());
            return configIds;

        } catch (StatusRuntimeException e) {
            log.error("gRPC error listing configs: {} - {}", 
                    e.getStatus().getCode(), e.getStatus().getDescription());
            Status.Code code = e.getStatus().getCode();
            if (code == Status.Code.PERMISSION_DENIED || code == Status.Code.UNAUTHENTICATED) {
                throw new NonRetryableGrpcClientException("Service authentication failed", e);
            }
            throw new GrpcClientException("Transient gRPC failure: " + e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error listing configs: {}", e.getMessage(), e);
            throw new NonRetryableGrpcClientException("Unexpected error: " + e.getMessage(), e);
        }
    }

    private UUID parseConfigId(String rawConfigId) {
        try {
            return UUID.fromString(rawConfigId);
        } catch (IllegalArgumentException ex) {
            log.warn("Skipping malformed configId from gRPC response: {}", rawConfigId);
            return null;
        }
    }

    /**
     * Map gRPC response to DTO.
     */
    private ProjectConfigDto mapToDto(InternalGetDecryptedConfigResponse response) {
        return ProjectConfigDto.builder()
            .configId(UUID.fromString(response.getConfigId()))
                .groupId(response.getGroupId())
                .jiraHostUrl(response.getJiraHostUrl())
            .jiraEmail(response.getJiraEmail())
                .jiraApiToken(response.getJiraApiToken())
                .githubRepoUrl(response.getGithubRepoUrl())
            .githubToken(response.getGithubToken())
                .state(response.getState())
                .build();
    }

    /**
     * Exception for gRPC client errors.
     */
    public static class GrpcClientException extends RuntimeException {
        public GrpcClientException(String message) {
            super(message);
        }

        public GrpcClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class NonRetryableGrpcClientException extends GrpcClientException {
        public NonRetryableGrpcClientException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    private record CachedConfig(ProjectConfigDto value, long expiresAtEpochMs) {
    }
}
