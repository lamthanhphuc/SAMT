package com.samt.projectconfig.client.grpc;

import com.samt.projectconfig.client.grpc.ConfigSummary;
import com.samt.projectconfig.client.grpc.InternalGetDecryptedConfigRequest;
import com.samt.projectconfig.client.grpc.InternalGetDecryptedConfigResponse;
import com.samt.projectconfig.client.grpc.InternalListVerifiedConfigsRequest;
import com.samt.projectconfig.client.grpc.InternalListVerifiedConfigsResponse;
import com.samt.projectconfig.client.grpc.ProjectConfigInternalServiceGrpc;
import com.samt.projectconfig.dto.response.DecryptedTokensResponse;
import com.samt.projectconfig.entity.ProjectConfig;
import com.samt.projectconfig.exception.ConfigNotFoundException;
import com.samt.projectconfig.exception.ForbiddenException;
import com.samt.projectconfig.service.ProjectConfigService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.server.service.GrpcService;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * gRPC server for Sync Service to fetch verified Project Config credentials.
 */
@GrpcService
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigInternalGrpcService extends ProjectConfigInternalServiceGrpc.ProjectConfigInternalServiceImplBase {

    private final ProjectConfigService projectConfigService;

    @Override
    @Transactional(readOnly = true)
    public void internalGetDecryptedConfig(
        InternalGetDecryptedConfigRequest request,
        StreamObserver<InternalGetDecryptedConfigResponse> responseObserver
    ) {
        try {
            UUID configId;
            try {
                configId = UUID.fromString(request.getConfigId());
            } catch (IllegalArgumentException ex) {
                responseObserver.onError(Status.INVALID_ARGUMENT
                    .withDescription("config_id must be a valid UUID")
                    .asRuntimeException());
                return;
            }

            log.info("gRPC InternalGetDecryptedConfig called: configId={}", configId);

            DecryptedTokensResponse response = projectConfigService.getDecryptedTokens(configId);

            InternalGetDecryptedConfigResponse grpcResponse = InternalGetDecryptedConfigResponse.newBuilder()
                .setConfigId(response.configId().toString())
                .setGroupId(response.groupId())
                .setJiraHostUrl(nullToEmpty(response.jiraHostUrl()))
                .setJiraApiToken(nullToEmpty(response.jiraApiToken()))
                .setJiraEmail(nullToEmpty(response.jiraEmail()))
                .setGithubRepoUrl(nullToEmpty(response.githubRepoUrl()))
                .setGithubToken(nullToEmpty(response.githubToken()))
                .setState(nullToEmpty(response.state()))
                .build();

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (ConfigNotFoundException ex) {
            responseObserver.onError(Status.NOT_FOUND
                .withDescription(ex.getMessage())
                .asRuntimeException());
        } catch (ForbiddenException ex) {
            responseObserver.onError(Status.FAILED_PRECONDITION
                .withDescription(ex.getMessage())
                .asRuntimeException());
        } catch (Exception ex) {
            log.error("gRPC InternalGetDecryptedConfig failed: {}", ex.getMessage(), ex);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException());
        }
    }

    @Override
    @Transactional(readOnly = true)
    public void internalListVerifiedConfigs(
        InternalListVerifiedConfigsRequest request,
        StreamObserver<InternalListVerifiedConfigsResponse> responseObserver
    ) {
        try {
            List<ProjectConfig> verifiedConfigs = projectConfigService.listVerifiedConfigs();

            InternalListVerifiedConfigsResponse grpcResponse = InternalListVerifiedConfigsResponse.newBuilder()
                .addAllConfigs(verifiedConfigs.stream()
                    .map(config -> ConfigSummary.newBuilder()
                        .setConfigId(config.getId().toString())
                        .setGroupId(config.getGroupId())
                        .setState(nullToEmpty(config.getState()))
                        .build())
                    .toList())
                .build();

            responseObserver.onNext(grpcResponse);
            responseObserver.onCompleted();
        } catch (Exception ex) {
            log.error("gRPC InternalListVerifiedConfigs failed: {}", ex.getMessage(), ex);
            responseObserver.onError(Status.INTERNAL
                .withDescription("Internal server error")
                .asRuntimeException());
        }
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}