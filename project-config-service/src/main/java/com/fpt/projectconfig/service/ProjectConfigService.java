package com.fpt.projectconfig.service;

import com.fpt.projectconfig.client.UserGroupServiceClient;
import com.fpt.projectconfig.dto.request.CreateConfigRequest;
import com.fpt.projectconfig.dto.request.UpdateConfigRequest;
import com.fpt.projectconfig.dto.response.ConfigResponse;
import com.fpt.projectconfig.dto.response.DecryptedTokensResponse;
import com.fpt.projectconfig.dto.response.VerificationResponse;
import com.fpt.projectconfig.entity.ProjectConfig;
import com.fpt.projectconfig.entity.ProjectConfig.ConfigState;
import com.fpt.projectconfig.exception.ConfigAlreadyExistsException;
import com.fpt.projectconfig.exception.ConfigNotFoundException;
import com.fpt.projectconfig.exception.ForbiddenException;
import com.fpt.projectconfig.exception.VerificationException;
import com.fpt.projectconfig.repository.ProjectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * UC30-35: Main service cho Project Config business logic
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigService {

    private final ProjectConfigRepository repository;
    private final TokenEncryptionService encryptionService;
    private final TokenMaskingService maskingService;
    private final JiraVerificationService jiraVerificationService;
    private final GitHubVerificationService gitHubVerificationService;
    private final UserGroupServiceClient userGroupClient;

    /**
     * UC30: Create Project Config
     * Chỉ Group Leader hoặc Admin mới có quyền
     */
    @Transactional
    public ConfigResponse createConfig(CreateConfigRequest request, Long userId, List<String> roles) {
        UUID groupId = request.getGroupId();
        log.info("Creating config for group: {} by user: {}", groupId, userId);

        // Validate group exists
        userGroupClient.getGroup(groupId);

        // Check authorization: Admin/Lecturer hoặc Group Leader
        if (!isAdminOrLecturer(roles) && !userGroupClient.isGroupLeader(groupId, userId)) {
            throw new ForbiddenException("Only group leader or admin can create config");
        }

        // Check group chưa có config
        if (repository.existsByGroupId(groupId)) {
            throw new ConfigAlreadyExistsException(groupId);
        }

        // Encrypt tokens
        String jiraEncrypted = encryptionService.encrypt(request.getJiraApiToken());
        String githubEncrypted = encryptionService.encrypt(request.getGithubToken());

        // Create config với state DRAFT
        ProjectConfig config = ProjectConfig.builder()
                .groupId(groupId)
                .jiraHostUrl(request.getJiraHostUrl())
                .jiraApiTokenEncrypted(jiraEncrypted)
                .githubRepoUrl(request.getGithubRepoUrl())
                .githubTokenEncrypted(githubEncrypted)
                .state(ConfigState.DRAFT)
                .build();

        ProjectConfig saved = repository.save(config);
        log.info("Config created: {}", saved.getId());

        return toResponse(saved, roles);
    }

    /**
     * UC31: Get Project Config
     * Tokens được mask tùy theo role
     */
    @Transactional(readOnly = true)
    public ConfigResponse getConfig(UUID configId, Long userId, List<String> roles) {
        log.debug("Getting config: {}", configId);

        ProjectConfig config = repository.findById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));

        // Check authorization
        if (!canAccessConfig(config, userId, roles)) {
            throw new ForbiddenException("Not authorized to view this configuration");
        }

        return toResponse(config, roles);
    }

    /**
     * UC32: Update Project Config
     * Nếu update critical fields → state chuyển về DRAFT
     */
    @Transactional
    public ConfigResponse updateConfig(UUID configId, UpdateConfigRequest request, Long userId, List<String> roles) {
        log.info("Updating config: {}", configId);

        ProjectConfig config = repository.findById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));

        if (config.isDeleted()) {
            throw new ConfigNotFoundException(configId);
        }

        // Check authorization
        if (!isAdminOrLecturer(roles) && !userGroupClient.isGroupLeader(config.getGroupId(), userId)) {
            throw new ForbiddenException("Only group leader or admin can update config");
        }

        boolean hasCriticalUpdate = false;

        if (request.getJiraHostUrl() != null) {
            config.setJiraHostUrl(request.getJiraHostUrl());
            hasCriticalUpdate = true;
        }
        if (request.getJiraApiToken() != null) {
            config.setJiraApiTokenEncrypted(encryptionService.encrypt(request.getJiraApiToken()));
            hasCriticalUpdate = true;
        }
        if (request.getGithubRepoUrl() != null) {
            config.setGithubRepoUrl(request.getGithubRepoUrl());
            hasCriticalUpdate = true;
        }
        if (request.getGithubToken() != null) {
            config.setGithubTokenEncrypted(encryptionService.encrypt(request.getGithubToken()));
            hasCriticalUpdate = true;
        }

        // Nếu update critical fields → chuyển về DRAFT
        if (hasCriticalUpdate) {
            config.transitionToDraft("Configuration updated, verification required");
        }

        repository.save(config);
        log.info("Config updated: {}", configId);

        return toResponse(config, roles);
    }

    /**
     * UC33: Delete Project Config (soft delete)
     */
    @Transactional
    public void deleteConfig(UUID configId, Long userId, List<String> roles) {
        log.info("Deleting config: {}", configId);

        ProjectConfig config = repository.findById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));

        // Idempotent: nếu đã deleted rồi thì OK
        if (config.isDeleted()) {
            return;
        }

        // Check authorization
        if (!isAdminOrLecturer(roles) && !userGroupClient.isGroupLeader(config.getGroupId(), userId)) {
            throw new ForbiddenException("Only group leader or admin can delete config");
        }

        config.softDelete(userId);
        repository.save(config);
        log.info("Config deleted: {}", configId);
    }

    /**
     * UC35: Restore (Admin only)
     */
    @Transactional
    public ConfigResponse restoreConfig(UUID configId, List<String> roles) {
        log.info("Restoring config: {}", configId);

        if (!roles.contains("ADMIN")) {
            throw new ForbiddenException("Only admin can restore");
        }

        ProjectConfig config = repository.findDeletedById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));

        config.restore();
        repository.save(config);
        log.info("Config restored: {}", configId);

        return toResponse(config, roles);
    }

    /**
     * UC34: Verify Config Connection
     */
    @Transactional
    public VerificationResponse verifyConfig(UUID configId, Long userId, List<String> roles) {
        log.info("Verifying config: {}", configId);

        ProjectConfig config = repository.findById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));

        // Check authorization
        if (!isAdminOrLecturer(roles) && !userGroupClient.isGroupLeader(config.getGroupId(), userId)) {
            throw new ForbiddenException("Only group leader or admin can verify config");
        }

        // Decrypt tokens
        String jiraToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        String githubToken = encryptionService.decrypt(config.getGithubTokenEncrypted());

        VerificationResponse.VerificationResults results = VerificationResponse.VerificationResults.builder().build();

        // Verify Jira
        try {
            jiraVerificationService.verify(config.getJiraHostUrl(), jiraToken);
            results.setJira(VerificationResponse.ServiceResult.builder()
                    .status("SUCCESS")
                    .message("Jira credentials verified")
                    .build());
        } catch (VerificationException e) {
            results.setJira(VerificationResponse.ServiceResult.builder()
                    .status("FAILED")
                    .message("Jira verification failed")
                    .error(e.getMessage())
                    .build());
        }

        // Verify GitHub (chỉ nếu Jira success)
        if ("SUCCESS".equals(results.getJira().getStatus())) {
            try {
                gitHubVerificationService.verify(githubToken);
                results.setGithub(VerificationResponse.ServiceResult.builder()
                        .status("SUCCESS")
                        .message("GitHub credentials verified")
                        .build());
            } catch (VerificationException e) {
                results.setGithub(VerificationResponse.ServiceResult.builder()
                        .status("FAILED")
                        .message("GitHub verification failed")
                        .error(e.getMessage())
                        .build());
            }
        } else {
            results.setGithub(VerificationResponse.ServiceResult.builder()
                    .status("PENDING")
                    .message("Not tested due to previous failure")
                    .build());
        }

        // Update state
        boolean allSuccess = "SUCCESS".equals(results.getJira().getStatus())
                && "SUCCESS".equals(results.getGithub().getStatus());

        if (allSuccess) {
            config.transitionToVerified();
        } else {
            String reason = buildInvalidReason(results);
            config.transitionToInvalid(reason);
        }

        repository.save(config);

        return VerificationResponse.builder()
                .configId(configId)
                .state(config.getState().name())
                .verificationResults(results)
                .invalidReason(config.getInvalidReason())
                .build();
    }

    /**
     * Internal API: Get decrypted tokens (cho Sync Service)
     */
    @Transactional(readOnly = true)
    public DecryptedTokensResponse getDecryptedTokens(UUID configId) {
        log.debug("Getting decrypted tokens: {}", configId);

        ProjectConfig config = repository.findById(configId)
                .orElseThrow(() -> new ConfigNotFoundException(configId));

        // Chỉ return nếu VERIFIED
        if (config.getState() != ConfigState.VERIFIED) {
            throw new ForbiddenException("Configuration not verified");
        }

        String jiraToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        String githubToken = encryptionService.decrypt(config.getGithubTokenEncrypted());

        return DecryptedTokensResponse.builder()
                .configId(config.getId())
                .groupId(config.getGroupId())
                .jiraHostUrl(config.getJiraHostUrl())
                .jiraApiToken(jiraToken)
                .githubRepoUrl(config.getGithubRepoUrl())
                .githubToken(githubToken)
                .state(config.getState().name())
                .build();
    }

    // Helper methods

    private ConfigResponse toResponse(ProjectConfig config, List<String> roles) {
        boolean canSeeFullToken = isAdminOrLecturer(roles);

        return ConfigResponse.builder()
                .id(config.getId())
                .groupId(config.getGroupId())
                .jiraHostUrl(config.getJiraHostUrl())
                .jiraApiToken(maskingService.maskToken(config.getJiraApiTokenEncrypted(), canSeeFullToken))
                .githubRepoUrl(config.getGithubRepoUrl())
                .githubToken(maskingService.maskToken(config.getGithubTokenEncrypted(), canSeeFullToken))
                .state(config.getState().name())
                .lastVerifiedAt(config.getLastVerifiedAt())
                .invalidReason(config.getInvalidReason())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build();
    }

    private boolean canAccessConfig(ProjectConfig config, Long userId, List<String> roles) {
        if (roles.contains("ADMIN") || roles.contains("LECTURER")) {
            return true;
        }
        return userGroupClient.isGroupLeader(config.getGroupId(), userId);
    }

    private boolean isAdminOrLecturer(List<String> roles) {
        return roles.contains("ADMIN") || roles.contains("LECTURER");
    }

    private String buildInvalidReason(VerificationResponse.VerificationResults results) {
        if ("FAILED".equals(results.getJira().getStatus())) {
            return "Jira verification failed: " + results.getJira().getError();
        }
        if ("FAILED".equals(results.getGithub().getStatus())) {
            return "GitHub verification failed: " + results.getGithub().getError();
        }
        return "Verification failed";
    }
}
