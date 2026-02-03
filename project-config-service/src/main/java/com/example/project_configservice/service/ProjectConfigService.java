package com.example.project_configservice.service;

import com.example.project_configservice.dto.request.CreateConfigRequest;
import com.example.project_configservice.dto.request.UpdateConfigRequest;
import com.example.project_configservice.dto.response.ConfigResponse;
import com.example.project_configservice.entity.ConfigState;
import com.example.project_configservice.entity.ProjectConfig;
import com.example.project_configservice.exception.*;
import com.example.project_configservice.grpc.UserGroupServiceGrpcClient;
import com.example.project_configservice.repository.ProjectConfigRepository;
import com.example.project_configservice.grpc.CheckGroupLeaderResponse;
import com.example.project_configservice.grpc.CheckGroupMemberResponse;
import com.example.project_configservice.grpc.VerifyGroupResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Business logic for Project Config Service.
 * 
 * Key Responsibilities:
 * - Create/Update/Delete configurations
 * - Verify group existence via gRPC
 * - Check user permissions (LEADER/MEMBER)
 * - Manage state machine (DRAFT → VERIFIED → INVALID → DELETED)
 * - Mask sensitive tokens in responses
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigService {
    
    private final ProjectConfigRepository configRepository;
    private final UserGroupServiceGrpcClient userGroupClient;
    private final TokenMaskingService tokenMaskingService;
    
    /**
     * Create new configuration for a group.
     * 
     * Business Rules:
     * - BR-CONFIG-01: Group MUST exist and not be deleted
     * - BR-CONFIG-02: User MUST be LEADER of the group OR have ADMIN role
     * - BR-CONFIG-03: Group MUST NOT already have a config
     * - BR-CONFIG-05: Initial state = DRAFT
     * 
     * @param request Configuration data
     * @param userId Current user ID (from JWT)
     * @param roles User roles (from JWT)
     * @return Created configuration with masked tokens
     */
    @Transactional
    public ConfigResponse createConfig(CreateConfigRequest request, Long userId, List<String> roles) {
        UUID groupId = request.getGroupId();
        
        log.info("User {} creating config for group {}", userId, groupId);
        
        // STEP 1: Verify group exists and is not deleted
        validateGroupExists(groupId);
        
        // STEP 2: Check user is LEADER or ADMIN
        checkLeaderPermission(groupId, userId, roles);
        
        // STEP 3: Check if config already exists
        if (configRepository.existsByGroupId(groupId)) {
            log.warn("Config already exists for group {}", groupId);
            throw new ConfigAlreadyExistsException("Group already has a configuration");
        }
        
        // STEP 4: Create config entity (tokens stored as-is for now, encryption TODO)
        ProjectConfig config = ProjectConfig.builder()
            .groupId(groupId)
            .jiraHostUrl(request.getJiraHostUrl())
            .jiraApiTokenEncrypted(request.getJiraApiToken()) // TODO: Encrypt
            .githubRepoUrl(request.getGithubRepoUrl())
            .githubTokenEncrypted(request.getGithubToken()) // TODO: Encrypt
            .state(ConfigState.DRAFT)
            .build();
        
        config = configRepository.save(config);
        
        log.info("Config created successfully: id={}, groupId={}", config.getId(), groupId);
        
        // STEP 5: Return DTO with masked tokens
        return toResponse(config);
    }
    
    /**
     * Get configuration by ID.
     * 
     * Authorization Rules:
     * - ADMIN: Can view ANY config
     * - LECTURER: Can view configs of supervised groups (TODO: implement via Identity Service)
     * - STUDENT (LEADER or MEMBER): Can view own group's config
     * 
     * @param id Config ID
     * @param userId Current user ID
     * @param roles User roles
     * @return Configuration with masked tokens
     */
    @Transactional(readOnly = true)
    public ConfigResponse getConfig(UUID id, Long userId, List<String> roles) {
        log.info("User {} requesting config {}", userId, id);
        
        // STEP 1: Find config
        ProjectConfig config = configRepository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // STEP 2: Check authorization
        boolean isAdmin = roles.contains("ROLE_ADMIN");
        boolean isLecturer = roles.contains("ROLE_LECTURER");
        
        if (!isAdmin && !isLecturer) {
            // Student: must be member of the group
            checkMemberPermission(config.getGroupId(), userId);
        }
        
        // STEP 3: Return DTO with masked tokens
        return toResponse(config);
    }
    
    /**
     * Update existing configuration.
     * 
     * Business Rules:
     * - BR-UPDATE-01: If credentials updated → state transitions to DRAFT
     * - BR-UPDATE-02: If state → DRAFT → clear lastVerifiedAt, set invalidReason
     * - BR-UPDATE-03: User MUST be LEADER or ADMIN
     * - BR-UPDATE-04: Cannot update soft-deleted config
     * 
     * @param id Config ID
     * @param request Update data (all fields optional)
     * @param userId Current user ID
     * @param roles User roles
     * @return Updated configuration
     */
    @Transactional
    public ConfigResponse updateConfig(UUID id, UpdateConfigRequest request, Long userId, List<String> roles) {
        log.info("User {} updating config {}", userId, id);
        
        // STEP 1: Find config
        ProjectConfig config = configRepository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // STEP 2: Check user is LEADER or ADMIN
        checkLeaderPermission(config.getGroupId(), userId, roles);
        
        // STEP 3: Apply updates
        boolean hasChanges = false;
        
        if (request.getJiraHostUrl() != null) {
            config.setJiraHostUrl(request.getJiraHostUrl());
            hasChanges = true;
        }
        
        if (request.getJiraApiToken() != null) {
            config.setJiraApiTokenEncrypted(request.getJiraApiToken()); // TODO: Encrypt
            hasChanges = true;
        }
        
        if (request.getGithubRepoUrl() != null) {
            config.setGithubRepoUrl(request.getGithubRepoUrl());
            hasChanges = true;
        }
        
        if (request.getGithubToken() != null) {
            config.setGithubTokenEncrypted(request.getGithubToken()); // TODO: Encrypt
            hasChanges = true;
        }
        
        // STEP 4: If credentials updated → transition to DRAFT
        if (hasChanges && request.hasCredentialUpdates()) {
            log.info("Credentials updated for config {}, transitioning to DRAFT", id);
            config.transitionToDraft();
        }
        
        config = configRepository.save(config);
        
        log.info("Config updated successfully: id={}", id);
        
        return toResponse(config);
    }
    
    /**
     * Soft delete configuration.
     * 
     * Business Rules:
     * - User MUST be LEADER or ADMIN
     * - Config transitions to DELETED state
     * - Retention: 90 days (per Soft Delete Retention Policy)
     * 
     * @param id Config ID
     * @param userId Current user ID
     * @param roles User roles
     */
    @Transactional
    public void deleteConfig(UUID id, Long userId, List<String> roles) {
        log.info("User {} deleting config {}", userId, id);
        
        // STEP 1: Find config
        ProjectConfig config = configRepository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // STEP 2: Check user is LEADER or ADMIN
        checkLeaderPermission(config.getGroupId(), userId, roles);
        
        // STEP 3: Soft delete
        config.softDelete(userId);
        configRepository.save(config);
        
        log.info("Config soft-deleted successfully: id={}, deletedBy={}", id, userId);
    }
    
    /**
     * Verify configuration credentials against Jira/GitHub.
     * 
     * State Transitions:
     * - Success: DRAFT/INVALID → VERIFIED (set lastVerifiedAt)
     * - Failure: DRAFT/VERIFIED → INVALID (set invalidReason)
     * 
     * @param id Config ID
     * @param userId Current user ID
     * @param roles User roles
     * @return Updated configuration
     */
    @Transactional
    public ConfigResponse verifyConfig(UUID id, Long userId, List<String> roles) {
        log.info("User {} verifying config {}", userId, id);
        
        // STEP 1: Find config
        ProjectConfig config = configRepository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // STEP 2: Check user is LEADER or ADMIN
        checkLeaderPermission(config.getGroupId(), userId, roles);
        
        // STEP 3: Verify credentials (stubbed for now)
        try {
            // TODO: Call JiraVerificationService.verify(jiraHostUrl, jiraToken)
            // TODO: Call GitHubVerificationService.verify(githubRepoUrl, githubToken)
            
            // For now, simulate success
            log.info("Verification successful for config {}", id);
            config.transitionToVerified();
            
        } catch (Exception e) {
            log.error("Verification failed for config {}: {}", id, e.getMessage());
            config.transitionToInvalid("Verification failed: " + e.getMessage());
        }
        
        config = configRepository.save(config);
        
        log.info("Config verification completed: id={}, state={}", id, config.getState());
        
        return toResponse(config);
    }
    
    // ==================== HELPER METHODS ====================
    
    /**
     * Validate that group exists and is not deleted via gRPC.
     * 
     * @throws GroupNotFoundException if group not found or deleted
     * @throws ServiceUnavailableException if gRPC call fails
     */
    private void validateGroupExists(UUID groupId) {
        try {
            VerifyGroupResponse response = userGroupClient.verifyGroupExists(groupId);
            
            if (!response.getExists()) {
                log.warn("Group does not exist: {}", groupId);
                throw new GroupNotFoundException(groupId);
            }
            
            if (response.getDeleted()) {
                log.warn("Group is deleted: {}", groupId);
                throw new GroupNotFoundException("Group has been deleted: " + groupId);
            }
            
            log.debug("Group validated: {}", groupId);
            
        } catch (StatusRuntimeException e) {
            handleGrpcError(e, "validate group");
        }
    }
    
    /**
     * Check if user is LEADER of the group (or ADMIN bypass).
     * 
     * @throws ForbiddenException if user is not LEADER and not ADMIN
     */
    private void checkLeaderPermission(UUID groupId, Long userId, List<String> roles) {
        // ADMIN bypass
        if (roles.contains("ROLE_ADMIN")) {
            log.debug("ADMIN bypass: user {} has permission for group {}", userId, groupId);
            return;
        }
        
        try {
            CheckGroupLeaderResponse response = userGroupClient.checkGroupLeader(groupId, userId);
            
            if (!response.getIsLeader()) {
                log.warn("User {} is not LEADER of group {}", userId, groupId);
                throw new ForbiddenException("Only group leader can perform this action");
            }
            
            log.debug("User {} is LEADER of group {}", userId, groupId);
            
        } catch (StatusRuntimeException e) {
            handleGrpcError(e, "check group leader");
        }
    }
    
    /**
     * Check if user is MEMBER (any role) of the group.
     * 
     * @throws ForbiddenException if user is not a member
     */
    private void checkMemberPermission(UUID groupId, Long userId) {
        try {
            CheckGroupMemberResponse response = userGroupClient.checkGroupMember(groupId, userId);
            
            if (!response.getIsMember()) {
                log.warn("User {} is not a member of group {}", userId, groupId);
                throw new ForbiddenException("You are not a member of this group");
            }
            
            log.debug("User {} is {} of group {}", userId, response.getRole(), groupId);
            
        } catch (StatusRuntimeException e) {
            handleGrpcError(e, "check group member");
        }
    }
    
    /**
     * Handle gRPC errors and map to domain exceptions.
     * 
     * Error Mapping:
     * - UNAVAILABLE → ServiceUnavailableException
     * - DEADLINE_EXCEEDED → GatewayTimeoutException
     * - NOT_FOUND → GroupNotFoundException
     * - PERMISSION_DENIED → ForbiddenException
     * - INVALID_ARGUMENT → BadRequestException
     */
    private void handleGrpcError(StatusRuntimeException e, String operation) {
        Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();
        
        log.error("gRPC error during {}: code={}, description={}", operation, code, description);
        
        switch (code) {
            case UNAVAILABLE:
                throw new ServiceUnavailableException("User-Group Service is currently unavailable");
            
            case DEADLINE_EXCEEDED:
                throw new GatewayTimeoutException("User-Group Service request timed out");
            
            case NOT_FOUND:
                throw new GroupNotFoundException(description != null ? description : "Group not found");
            
            case PERMISSION_DENIED:
                throw new ForbiddenException(description != null ? description : "Access denied");
            
            case INVALID_ARGUMENT:
                throw new BadRequestException(description != null ? description : "Invalid request");
            
            default:
                throw new RuntimeException("Failed to " + operation + ": " + code);
        }
    }
    
    /**
     * Convert entity to DTO with masked tokens.
     */
    private ConfigResponse toResponse(ProjectConfig config) {
        return ConfigResponse.builder()
            .id(config.getId())
            .groupId(config.getGroupId())
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(tokenMaskingService.maskJiraToken(config.getJiraApiTokenEncrypted()))
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(tokenMaskingService.maskGithubToken(config.getGithubTokenEncrypted()))
            .state(config.getState())
            .lastVerifiedAt(config.getLastVerifiedAt())
            .invalidReason(config.getInvalidReason())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }
}
