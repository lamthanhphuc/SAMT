package com.fpt.projectconfig.grpc.service;

import com.fpt.projectconfig.dto.request.CreateConfigRequest;
import com.fpt.projectconfig.dto.request.UpdateConfigRequest;
import com.fpt.projectconfig.dto.response.ConfigResponse;
import com.fpt.projectconfig.dto.response.DecryptedTokensResponse;
import com.fpt.projectconfig.dto.response.VerificationResponse;
import com.fpt.projectconfig.grpc.interceptor.AuthenticationInterceptor;
import com.fpt.projectconfig.grpc.mapper.ProjectConfigMapper;
import com.fpt.projectconfig.service.ProjectConfigService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * gRPC Service Implementation cho Project Config
 * 
 * TODO: Update extends clause khi có generated gRPC base class
 * Example: extends ProjectConfigServiceGrpc.ProjectConfigServiceImplBase
 * 
 * Protobuf Definition Requirements (.proto file):
 * 
 * service ProjectConfigService {
 *   rpc CreateProjectConfig(CreateConfigRequest) returns (ProjectConfigResponse);
 *   rpc GetProjectConfig(GetConfigRequest) returns (ProjectConfigResponse);
 *   rpc UpdateProjectConfig(UpdateConfigRequest) returns (ProjectConfigResponse);
 *   rpc DeleteProjectConfig(DeleteConfigRequest) returns (google.protobuf.Empty);
 *   rpc VerifyConnection(VerifyConfigRequest) returns (VerificationResult);
 *   rpc RestoreProjectConfig(RestoreConfigRequest) returns (ProjectConfigResponse);
 *   rpc InternalGetDecryptedConfig(GetConfigRequest) returns (DecryptedConfigResponse);
 * }
 * 
 * message CreateConfigRequest {
 *   int64 group_id = 1;
 *   string jira_host_url = 2;
 *   string jira_api_token = 3;
 *   string github_repo_url = 4;
 *   string github_access_token = 5;
 * }
 * 
 * message ProjectConfigResponse {
 *   string id = 1;
 *   int64 group_id = 2;
 *   string jira_host_url = 3;
 *   string jira_api_token = 4;  // Masked
 *   string github_repo_url = 5;
 *   string github_access_token = 6;  // Masked
 *   string state = 7;
 *   string invalid_reason = 8;
 *   google.protobuf.Timestamp created_at = 9;
 *   google.protobuf.Timestamp updated_at = 10;
 *   int64 created_by = 11;
 *   int64 updated_by = 12;
 * }
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigGrpcService {
    // TODO: extends ProjectConfigServiceGrpc.ProjectConfigServiceImplBase

    private final ProjectConfigService projectConfigService;

    /**
     * UC30: Create Project Config
     * 
     * TODO: Update method signature khi có generated Protobuf classes
     * public void createProjectConfig(CreateConfigRequestProto request,
     *                                  StreamObserver<ProjectConfigResponseProto> responseObserver)
     */
    public void createProjectConfig(Object request, StreamObserver<Object> responseObserver) {
        try {
            // Extract authentication từ gRPC context
            Long userId = AuthenticationInterceptor.getUserId();
            List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

            // TODO: Extract fields từ Protobuf request
            // Long groupId = request.getGroupId();
            // String jiraHostUrl = request.getJiraHostUrl();
            // String jiraApiToken = request.getJiraApiToken();
            // String githubRepoUrl = request.getGithubRepoUrl();
            // String githubAccessToken = request.getGithubAccessToken();

            // Build DTO
            CreateConfigRequest dtoRequest = CreateConfigRequest.builder()
                    // .groupId(groupId)
                    // .jiraHostUrl(jiraHostUrl)
                    // .jiraApiToken(jiraApiToken)
                    // .githubRepoUrl(githubRepoUrl)
                    // .githubAccessToken(githubAccessToken)
                    .build();

            // Call service
            ConfigResponse configResponse = projectConfigService.createConfig(dtoRequest, userId, roles);

            // Convert to Protobuf
            Object protoResponse = ProjectConfigMapper.toProto(configResponse);

            // Send response
            // responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();

            log.info("Created config for group: {} by user: {}", configResponse.getGroupId(), userId);

        } catch (Exception e) {
            log.error("Error creating config", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * UC31: Get Project Config by Group ID
     */
    public void getProjectConfig(Object request, StreamObserver<Object> responseObserver) {
        try {
            Long userId = AuthenticationInterceptor.getUserId();
            List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

            // TODO: Extract groupId from request
            // Long groupId = request.getGroupId();

            // Call service
            Long groupId = 0L; // TODO: Replace
            ConfigResponse configResponse = projectConfigService.getConfig(groupId, userId, roles);

            // Convert to Protobuf
            Object protoResponse = ProjectConfigMapper.toProto(configResponse);

            // responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting config", e);
            responseObserver.onError(Status.NOT_FOUND
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * UC32: Update Project Config
     */
    public void updateProjectConfig(Object request, StreamObserver<Object> responseObserver) {
        try {
            Long userId = AuthenticationInterceptor.getUserId();
            List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

            // TODO: Extract fields from request
            // UUID configId = UUID.fromString(request.getId());
            // String jiraHostUrl = request.hasJiraHostUrl() ? request.getJiraHostUrl() : null;
            // String jiraApiToken = request.hasJiraApiToken() ? request.getJiraApiToken() : null;
            // String githubRepoUrl = request.hasGithubRepoUrl() ? request.getGithubRepoUrl() : null;
            // String githubAccessToken = request.hasGithubAccessToken() ? request.getGithubAccessToken() : null;

            UpdateConfigRequest dtoRequest = UpdateConfigRequest.builder()
                    // .jiraHostUrl(jiraHostUrl)
                    // .jiraApiToken(jiraApiToken)
                    // .githubRepoUrl(githubRepoUrl)
                    // .githubAccessToken(githubAccessToken)
                    .build();

            UUID configId = UUID.randomUUID(); // TODO: Replace
            ConfigResponse configResponse = projectConfigService.updateConfig(configId, dtoRequest, userId, roles);

            Object protoResponse = ProjectConfigMapper.toProto(configResponse);

            // responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error updating config", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * UC33: Delete Project Config (Soft Delete)
     */
    public void deleteProjectConfig(Object request, StreamObserver<Object> responseObserver) {
        try {
            Long userId = AuthenticationInterceptor.getUserId();
            List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

            // TODO: Extract configId from request
            // UUID configId = UUID.fromString(request.getId());

            UUID configId = UUID.randomUUID(); // TODO: Replace
            projectConfigService.deleteConfig(configId, userId, roles);

            // Return Empty response
            // responseObserver.onNext(Empty.getDefaultInstance());
            responseObserver.onCompleted();

            log.info("Deleted config: {} by user: {}", configId, userId);

        } catch (Exception e) {
            log.error("Error deleting config", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * UC34: Verify Connection (Test Jira/GitHub credentials)
     */
    public void verifyConnection(Object request, StreamObserver<Object> responseObserver) {
        try {
            Long userId = AuthenticationInterceptor.getUserId();
            List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

            // TODO: Extract configId from request
            // UUID configId = UUID.fromString(request.getId());

            UUID configId = UUID.randomUUID(); // TODO: Replace
            VerificationResponse verificationResponse = projectConfigService.verifyConfig(configId, userId, roles);

            Object protoResponse = ProjectConfigMapper.toVerificationProto(verificationResponse);

            // responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error verifying config", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * UC35: Restore Deleted Config (Admin Only)
     */
    public void restoreProjectConfig(Object request, StreamObserver<Object> responseObserver) {
        try {
            Long userId = AuthenticationInterceptor.getUserId();
            List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

            // TODO: Extract configId from request
            // UUID configId = UUID.fromString(request.getId());

            UUID configId = UUID.randomUUID(); // TODO: Replace
            ConfigResponse configResponse = projectConfigService.restoreConfig(configId, userId, roles);

            Object protoResponse = ProjectConfigMapper.toProto(configResponse);

            // responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();

            log.info("Restored config: {} by user: {}", configId, userId);

        } catch (Exception e) {
            log.error("Error restoring config", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }

    /**
     * Internal API: Get Decrypted Config (For Sync Service)
     * Requires service-to-service authentication
     */
    public void internalGetDecryptedConfig(Object request, StreamObserver<Object> responseObserver) {
        try {
            // TODO: Extract configId from request
            // UUID configId = UUID.fromString(request.getId());

            UUID configId = UUID.randomUUID(); // TODO: Replace
            DecryptedTokensResponse tokensResponse = projectConfigService.getDecryptedTokens(configId);

            // TODO: Convert to Protobuf DecryptedConfigResponse
            // DecryptedConfigResponseProto protoResponse = DecryptedConfigResponseProto.newBuilder()
            //     .setConfigId(tokensResponse.getConfigId().toString())
            //     .setGroupId(tokensResponse.getGroupId())
            //     .setJiraHostUrl(tokensResponse.getJiraHostUrl())
            //     .setJiraApiToken(tokensResponse.getJiraApiToken())  // Full token
            //     .setGithubRepoUrl(tokensResponse.getGithubRepoUrl())
            //     .setGithubAccessToken(tokensResponse.getGithubAccessToken())  // Full token
            //     .setState(tokensResponse.getState().name())
            //     .build();

            // responseObserver.onNext(protoResponse);
            responseObserver.onCompleted();

        } catch (Exception e) {
            log.error("Error getting decrypted config", e);
            responseObserver.onError(Status.INTERNAL
                    .withDescription(e.getMessage())
                    .asRuntimeException());
        }
    }
}
