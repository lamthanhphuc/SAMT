package com.example.syncservice.client.grpc;

import com.example.grpc.projectconfig.InternalGetDecryptedConfigRequest;
import com.example.grpc.projectconfig.InternalGetDecryptedConfigResponse;
import com.example.grpc.projectconfig.InternalListVerifiedConfigsRequest;
import com.example.grpc.projectconfig.InternalListVerifiedConfigsResponse;
import com.example.grpc.projectconfig.ProjectConfigInternalServiceGrpc;
import com.example.syncservice.dto.ProjectConfigDto;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;
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
    public ProjectConfigDto getDecryptedConfig(Long configId) {
        log.debug("Fetching decrypted config for configId={}", configId);

        try {
            InternalGetDecryptedConfigRequest request = InternalGetDecryptedConfigRequest.newBuilder()
                    .setConfigId(configId)
                    .build();

            InternalGetDecryptedConfigResponse response = projectConfigStub
                    .withDeadlineAfter(5, TimeUnit.SECONDS)
                    .internalGetDecryptedConfig(request);

            ProjectConfigDto dto = mapToDto(response);
            log.debug("Successfully fetched config for configId={}", configId);
            return dto;

        } catch (StatusRuntimeException e) {
            log.error("gRPC error fetching config {}: {} - {}", 
                    configId, e.getStatus().getCode(), e.getStatus().getDescription());
            
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                throw new ConfigNotFoundException("Config not found: " + configId);
            } else if (e.getStatus().getCode() == Status.Code.PERMISSION_DENIED) {
                throw new GrpcClientException("Service authentication failed", e);
            } else if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                throw new GrpcClientException("gRPC timeout: " + configId, e);
            }
            
            throw new GrpcClientException("gRPC error: " + e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error fetching config {}: {}", configId, e.getMessage(), e);
            throw new GrpcClientException("Unexpected error: " + e.getMessage(), e);
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
    public List<Long> listVerifiedConfigIds() {
        log.debug("Fetching list of verified configs");

        try {
            InternalListVerifiedConfigsRequest request = InternalListVerifiedConfigsRequest.newBuilder()
                    .build();

            InternalListVerifiedConfigsResponse response = projectConfigStub
                    .withDeadlineAfter(10, TimeUnit.SECONDS)
                    .internalListVerifiedConfigs(request);

            List<Long> configIds = response.getConfigsList().stream()
                    .map(config -> config.getConfigId())
                    .collect(Collectors.toList());

            log.info("Found {} verified configs", configIds.size());
            return configIds;

        } catch (StatusRuntimeException e) {
            log.error("gRPC error listing configs: {} - {}", 
                    e.getStatus().getCode(), e.getStatus().getDescription());
            throw new GrpcClientException("gRPC error: " + e.getStatus(), e);
        } catch (Exception e) {
            log.error("Unexpected error listing configs: {}", e.getMessage(), e);
            throw new GrpcClientException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /**
     * Map gRPC response to DTO.
     */
    private ProjectConfigDto mapToDto(InternalGetDecryptedConfigResponse response) {
        return ProjectConfigDto.builder()
                .configId(response.getConfigId())
                .groupId(response.getGroupId())
                .jiraHostUrl(response.getJiraHostUrl())
                .jiraApiToken(response.getJiraApiToken())
                .jiraProjectKey(response.getJiraProjectKey())
                .githubRepoUrl(response.getGithubRepoUrl())
                .githubAccessToken(response.getGithubAccessToken())
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

    /**
     * Exception when config not found.
     */
    public static class ConfigNotFoundException extends GrpcClientException {
        public ConfigNotFoundException(String message) {
            super(message);
        }
    }
}
