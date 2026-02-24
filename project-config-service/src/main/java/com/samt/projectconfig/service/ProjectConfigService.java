package com.samt.projectconfig.service;

import com.samt.projectconfig.dto.VerificationStatus;
import com.samt.projectconfig.dto.request.CreateConfigRequest;
import com.samt.projectconfig.dto.request.UpdateConfigRequest;
import com.samt.projectconfig.dto.response.*;
import com.samt.projectconfig.entity.ProjectConfig;
import com.samt.projectconfig.exception.*;
import com.samt.projectconfig.repository.ProjectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Production-grade ProjectConfig service with phase-based transaction pattern.
 * 
 * Architecture Principles:
 * - Phase Separation: Validation → Computation → Short Transaction
 * - No network I/O inside @Transactional methods
 * - All authorization checks run BEFORE opening transactions
 * - Explicit exception handling for race conditions and optimistic locking
 * - Zero tolerance for resource starvation
 * 
 * Security:
 * - All group existence checks enforced (including ADMIN)
 * - Authorization validated via gRPC before any DB operations
 * - Tokens encrypted at rest, masked in responses
 * 
 * @author Production Team
 * @version 2.0 (Refactored for high-scale production)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigService {
    
    private final ProjectConfigRepository repository;
    private final TokenEncryptionService encryptionService;
    private final TokenMaskingService maskingService;
    private final JiraVerificationService jiraVerificationService;
    private final GitHubVerificationService githubVerificationService;
    private final com.samt.projectconfig.client.grpc.UserGroupServiceGrpcClient userGroupClient;
    
    /**
     * Create new project configuration with async non-blocking pattern.
     * 
     * Async Architecture (v2.0):
     * - ✅ gRPC calls return CompletableFuture immediately
     * - ✅ HTTP threads freed during authorization
     * - ✅ Async chain: gRPC auth → encryption → persistence
     * 
     * Phase Execution:
     * 1. Validation Phase (ASYNC): gRPC group existence check (non-blocking)
     * 2. Authorization Phase (ASYNC): gRPC leader check (non-blocking)
     * 3. Encryption Phase (CPU): Encrypt tokens
     * 4. Persistence Phase (TX): Atomic save
     * 
     * Business Rules:
     * - BR-CONFIG-01: Group must exist (enforced for ALL roles)
     * - BR-CONFIG-02: User must be LEADER or ADMIN
     * - BR-CONFIG-03: One config per group
     * - BR-CONFIG-04: Tokens encrypted before storage
     * - BR-CONFIG-05: Initial state is DRAFT
     * 
     * @param request Create request with plaintext tokens
     * @param userId Current user ID from JWT
     * @param roles User roles from JWT
     * @return CompletableFuture<ConfigResponse>
     */
    public CompletableFuture<ConfigResponse> createConfig(CreateConfigRequest request, Long userId, List<String> roles) {
        log.info("User {} creating config for group {} (async)", userId, request.groupId());
        
        // ========== PHASE 1: VALIDATION (ASYNC NON-BLOCKING) ==========
        return userGroupClient.verifyGroupExists(request.groupId())
            .thenCompose(verifyResponse -> {
                log.debug("Group {} existence verified", request.groupId());
                
                // ========== PHASE 2: AUTHORIZATION (ASYNC NON-BLOCKING) ==========
                boolean isAdmin = roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
                if (!isAdmin) {
                    return userGroupClient.checkGroupLeader(request.groupId(), userId)
                        .thenApply(leaderResponse -> {
                            log.debug("User {} is leader of group {}", userId, request.groupId());
                            return null; // Continue chain
                        });
                } else {
                    log.debug("ADMIN user {} bypasses leader check", userId);
                    return CompletableFuture.completedFuture(null);
                }
            })
            .thenApply(unused -> {
                // ========== PHASE 3: ENCRYPTION (CPU-BOUND) ==========
                String encryptedJiraToken = encryptionService.encrypt(request.jiraApiToken());
                String encryptedGithubToken = encryptionService.encrypt(request.githubToken());
                log.debug("Tokens encrypted successfully");
                
                // ========== PHASE 4: PERSISTENCE (SHORT TRANSACTION) ==========
                return persistNewConfig(request, encryptedJiraToken, encryptedGithubToken);
            });
    }
    
    /**
     * Persist new config with race condition handling.
     * Runs in separate transaction to keep it short-lived.
     */
    @Transactional
    protected ConfigResponse persistNewConfig(
        CreateConfigRequest request,
        String encryptedJiraToken,
        String encryptedGithubToken
    ) {
        try {
            ProjectConfig config = ProjectConfig.builder()
                .groupId(request.groupId())
                .jiraHostUrl(request.jiraHostUrl())
                .jiraApiTokenEncrypted(encryptedJiraToken)
                .githubRepoUrl(request.githubRepoUrl())
                .githubTokenEncrypted(encryptedGithubToken)
                .state("DRAFT")
                .build();
            
            ProjectConfig saved = repository.save(config);
            log.info("Config {} created successfully for group {}", saved.getId(), request.groupId());
            
            return toResponse(saved);
            
        } catch (DataIntegrityViolationException ex) {
            // Race condition: Another request created config for same group
            // Map to 409 Conflict with clear message
            log.warn("Race condition detected: Config already exists for group {}", request.groupId());
            throw new ConfigAlreadyExistsException(request.groupId());
        }
    }
    
    /**
     * Get configuration by ID with async role-based authorization.
     * 
     * Async Architecture (v2.0):
     * - ✅ gRPC membership check returns CompletableFuture
     * - ✅ HTTP threads freed during authorization
     * 
     * Phase Execution:
     * 1. Load Phase (SHORT TX): Read from DB
     * 2. Authorization Phase (ASYNC): Role-based gRPC checks
     * 
     * Authorization Rules:
     * - ADMIN: Can view any config (no gRPC check)
     * - LECTURER: Can view any config (no gRPC check)
     * - Others (STUDENT): Must be group member/leader (async gRPC check)
     * 
     * @param id Config ID
     * @param userId Current user ID from JWT
     * @param roles User roles from JWT
     * @return CompletableFuture<ConfigResponse>
     */
    public CompletableFuture<ConfigResponse> getConfig(UUID id, Long userId, List<String> roles) {
        log.debug("User {} fetching config {} (async)", userId, id);
        
        // ========== PHASE 1: LOAD CONFIG (SHORT TRANSACTION) ==========
        ProjectConfig config = loadConfig(id);
        
        // ========== PHASE 2: AUTHORIZATION (ASYNC NON-BLOCKING) ==========
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
        boolean isLecturer = roles.contains("LECTURER") || roles.contains("ROLE_LECTURER");
        
        if (!isAdmin && !isLecturer) {
            log.debug("Non-privileged user {}, checking group membership for group {} (async)", 
                userId, config.getGroupId());
            
            return userGroupClient.checkGroupMembership(config.getGroupId(), userId)
                .thenApply(memberResponse -> {
                    log.debug("User {} authorized to view config for group {}", userId, config.getGroupId());
                    return toResponse(config);
                });
        } else {
            log.debug("Privileged user {} (ADMIN={}, LECTURER={}) authorized to view any config",
                userId, isAdmin, isLecturer);
            return CompletableFuture.completedFuture(toResponse(config));
        }
    }
    
    /**
     * Load config in separate short transaction.
     */
    @Transactional(readOnly = true)
    protected ProjectConfig loadConfig(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
    }
    
    /**
     * Update configuration with async non-blocking authorization.
     * 
     * Async Architecture (v2.0):
     * - ✅ gRPC leader check returns CompletableFuture
     * - ✅ HTTP threads freed during authorization
     * 
     * Phase Execution:
     * 1. Load Phase (SHORT TX): Read existing config
     * 2. Authorization Phase (ASYNC): gRPC leader check
     * 3. Encryption Phase (CPU): Encrypt new tokens if provided
     * 4. Update Phase (SHORT TX): Apply updates with optimistic locking
     * 
     * Business Rules:
     * - BR-UPDATE-01: If credentials updated → state transitions to DRAFT
     * - BR-UPDATE-02: Reset lastVerifiedAt and set invalidReason
     * - BR-UPDATE-03: User must be LEADER or ADMIN
     * - BR-UPDATE-04: Cannot update soft-deleted config
     * - BR-UPDATE-05: Optimistic locking prevents concurrent update conflicts
     * 
     * @param id Config ID
     * @param request Update request
     * @param userId Current user ID from JWT
     * @param roles User roles from JWT
     * @return CompletableFuture<ConfigResponse>
     */
    public CompletableFuture<ConfigResponse> updateConfig(UUID id, UpdateConfigRequest request, Long userId, List<String> roles) {
        log.info("User {} updating config {} (async)", userId, id);
        
        // ========== PHASE 1: LOAD CONFIG (SHORT TRANSACTION) ==========
        ProjectConfig config = loadConfig(id);
        Long groupId = config.getGroupId();
        
        // ========== PHASE 2: AUTHORIZATION (ASYNC NON-BLOCKING) ==========
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
        
        CompletableFuture<Void> authFuture;
        if (!isAdmin) {
            authFuture = userGroupClient.checkGroupLeader(groupId, userId)
                .thenApply(leaderResponse -> {
                    log.debug("User {} is leader of group {}", userId, groupId);
                    return null;
                });
        } else {
            authFuture = CompletableFuture.completedFuture(null);
        }
        
        return authFuture.thenApply(unused -> {
            // ========== PHASE 3: ENCRYPTION (CPU-BOUND) ==========
            String encryptedJiraToken = null;
            String encryptedGithubToken = null;
            
            if (request.jiraApiToken() != null) {
                encryptedJiraToken = encryptionService.encrypt(request.jiraApiToken());
            }
            
            if (request.githubToken() != null) {
                encryptedGithubToken = encryptionService.encrypt(request.githubToken());
            }
            
            // ========== PHASE 4: UPDATE (SHORT TRANSACTION) ==========
            return persistUpdate(id, request, encryptedJiraToken, encryptedGithubToken);
        });
    }
    
    /**
     * Persist updates with optimistic locking.
     * Separate transaction to keep it short-lived.
     */
    @Transactional
    protected ConfigResponse persistUpdate(
        UUID id,
        UpdateConfigRequest request,
        String encryptedJiraToken,
        String encryptedGithubToken
    ) {
        try {
            // Reload config in this transaction (with pessimistic read lock via version)
            ProjectConfig config = repository.findById(id)
                .orElseThrow(() -> new ConfigNotFoundException(id));
            
            boolean credentialsUpdated = false;
            
            // Apply partial updates
            if (request.jiraHostUrl() != null) {
                config.setJiraHostUrl(request.jiraHostUrl());
                credentialsUpdated = true;
            }
            
            if (encryptedJiraToken != null) {
                config.setJiraApiTokenEncrypted(encryptedJiraToken);
                credentialsUpdated = true;
            }
            
            if (request.githubRepoUrl() != null) {
                config.setGithubRepoUrl(request.githubRepoUrl());
                credentialsUpdated = true;
            }
            
            if (encryptedGithubToken != null) {
                config.setGithubTokenEncrypted(encryptedGithubToken);
                credentialsUpdated = true;
            }
            
            // BR-UPDATE-01 & BR-UPDATE-02: State transition if credentials updated
            if (credentialsUpdated && !"DRAFT".equals(config.getState())) {
                log.info("Credentials updated, transitioning config {} to DRAFT", id);
                config.transitionToDraft();
            }
            
            ProjectConfig updated = repository.save(config);
            log.info("Config {} updated successfully", id);
            
            return toResponse(updated);
            
        } catch (jakarta.persistence.OptimisticLockException ex) {
            // Concurrent update detected - version mismatch
            log.warn("Optimistic lock conflict for config {}: {}", id, ex.getMessage());
            throw new ConflictException(
                "Configuration was modified by another user. Please refresh and retry.",
                ex
            );
        }
    }
    
    /**
     * Soft delete configuration with async non-blocking authorization.
     * 
     * Async Architecture (v2.0):
     * - ✅ gRPC leader check returns CompletableFuture
     * - ✅ HTTP threads freed during authorization
     * 
     * Phase Execution:
     * 1. Load Phase (SHORT TX): Read config including deleted
     * 2. Authorization Phase (ASYNC): gRPC leader check
     * 3. Delete Phase (SHORT TX): Soft delete
     * 
     * Business Rules:
     * - BR-DELETE-01: Soft delete only (set deleted_at, deleted_by)
     * - BR-DELETE-02: State transitions to DELETED
     * - BR-DELETE-03: 90-day retention before hard delete
     * - BR-DELETE-04: User must be LEADER or ADMIN
     * - BR-DELETE-05: Idempotent operation
     * 
     * @param id Config ID
     * @param userId Current user ID from JWT
     * @param roles User roles from JWT
     * @return CompletableFuture<DeleteResponse>
     */
    public CompletableFuture<DeleteResponse> deleteConfig(UUID id, Long userId, List<String> roles) {
        log.warn("User {} deleting config {} (async)", userId, id);
        
        // ========== PHASE 1: LOAD CONFIG (SHORT TRANSACTION) ==========
        ProjectConfig config = loadConfigIncludingDeleted(id);
        
        // BR-DELETE-05: Idempotent - if already deleted, return success
        if (config.getDeletedAt() != null) {
            log.info("Config {} already deleted", id);
            DeleteResponse response = DeleteResponse.builder()
                .message("Configuration already deleted")
                .configId(id)
                .deletedAt(config.getDeletedAt())
                .retentionDays(90)
                .build();
            return CompletableFuture.completedFuture(response);
        }
        
        // ========== PHASE 2: AUTHORIZATION (ASYNC NON-BLOCKING) ==========
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
        
        CompletableFuture<Void> authFuture;
        if (!isAdmin) {
            authFuture = userGroupClient.checkGroupLeader(config.getGroupId(), userId)
                .thenApply(leaderResponse -> {
                    log.debug("User {} is leader of group {}", userId, config.getGroupId());
                    return null;
                });
        } else {
            authFuture = CompletableFuture.completedFuture(null);
        }
        
        // ========== PHASE 3: SOFT DELETE (SHORT TRANSACTION) ==========
        return authFuture.thenApply(unused -> persistDelete(id, userId));
    }
    
    /**
     * Persist soft delete in separate transaction.
     */
    @Transactional
    protected DeleteResponse persistDelete(UUID id, Long userId) {
        ProjectConfig config = repository.findByIdIncludingDeleted(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        config.softDelete(userId);
        repository.save(config);
        
        log.info("Config {} soft-deleted successfully by user {}", id, userId);
        
        return DeleteResponse.builder()
            .message("Configuration deleted successfully")
            .configId(id)
            .deletedAt(config.getDeletedAt())
            .retentionDays(90)
            .build();
    }
    
    /**
     * Load config including soft-deleted in separate transaction.
     */
    @Transactional(readOnly = true)
    protected ProjectConfig loadConfigIncludingDeleted(UUID id) {
        return repository.findByIdIncludingDeleted(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
    }
    
    /**
     * Verify configuration with fully async non-blocking pattern.
     * 
     * Async Architecture (v2.0):
     * - ✅ gRPC leader check returns CompletableFuture
     * - ✅ HTTP threads freed during ALL I/O operations
     * - ✅ Entire flow is async: gRPC → external APIs → persistence
     * - ✅ No blocking anywhere in the chain
     * 
     * Phase Execution:
     * 1. Load & Decrypt Phase (SHORT TX ~50ms): Read config, decrypt tokens
     * 2. Authorization Phase (ASYNC ~800ms): gRPC leader check
     * 3. External API Phase (ASYNC ~10-12s): Test Jira + GitHub APIs parallel
     * 4. Status Update Phase (SHORT TX ~50ms): Update verification status
     * 
     * CRITICAL: Full async end-to-end - no HTTP thread blocking!
     * 
     * Business Rules:
     * - BR-VERIFY-01: External API calls run OUTSIDE transaction
     * - BR-VERIFY-02: Total external timeout 20 seconds (10s per API)
     * - BR-VERIFY-03: Cannot verify soft-deleted config
     * - BR-VERIFY-04: User must be LEADER or ADMIN
     * - BR-VERIFY-05: Update status regardless of API results
     * 
     * @param id Config ID
     * @param userId Current user ID from JWT
     * @param roles User roles from JWT
     * @return CompletableFuture<VerificationResponse>
     */
    public CompletableFuture<VerificationResponse> verifyConfig(UUID id, Long userId, List<String> roles) {
        log.info("User {} verifying config {} (fully async)", userId, id);
        
        // ========== PHASE 1: LOAD & DECRYPT (SHORT TRANSACTION ~50ms) ==========
        ConfigDecryptionResult decrypted = loadAndDecryptConfig(id);
        Long groupId = decrypted.groupId();
        
        // ========== PHASE 2: AUTHORIZATION (ASYNC NON-BLOCKING ~800ms) ==========
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
        
        CompletableFuture<Void> authFuture;
        if (!isAdmin) {
            authFuture = userGroupClient.checkGroupLeader(groupId, userId)
                .thenApply(leaderResponse -> {
                    log.debug("User {} is leader of group {}", userId, groupId);
                    return null;
                });
        } else {
            authFuture = CompletableFuture.completedFuture(null);
        }
        
        // ========== PHASE 3: EXTERNAL API CALLS (ASYNC PARALLEL ~10-12s) ==========
        return authFuture.thenCompose(unused -> {
            log.info("Starting PARALLEL async external API verification for config {} (NO blocking)", id);
            
            CompletableFuture<VerificationResponse.JiraResult> jiraFuture = 
                jiraVerificationService.verifyAsync(decrypted.jiraHostUrl(), decrypted.jiraToken());
            
            CompletableFuture<VerificationResponse.GitHubResult> githubFuture = 
                githubVerificationService.verifyAsync(decrypted.githubRepoUrl(), decrypted.githubToken());
            
            return CompletableFuture.allOf(jiraFuture, githubFuture)
                .thenCompose(voidResult -> {
                    return jiraFuture.thenCombine(githubFuture, (jiraResult, githubResult) -> {
                        log.info("PARALLEL async verification completed for config {} (jira={}, github={})", 
                            id, jiraResult.status(), githubResult.status());
                        
                        // ========== PHASE 4: STATUS UPDATE (SHORT TRANSACTION ~50ms) ==========
                        return persistVerificationResult(id, jiraResult, githubResult);
                    });
                })
                .exceptionally(throwable -> {
                    log.error("Async verification FAILED for config {}: {}", id, throwable.getMessage());
                    throw new VerificationException("Verification execution failed: " + throwable.getMessage());
                });
        });
    }
    
    /**
     * Load config and decrypt tokens in separate short transaction.
     * 
     * @return Decrypted config data needed for external API calls
     */
    @Transactional(readOnly = true)
    protected ConfigDecryptionResult loadAndDecryptConfig(UUID id) {
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        String jiraToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        String githubToken = encryptionService.decrypt(config.getGithubTokenEncrypted());
        
        return new ConfigDecryptionResult(
            config.getGroupId(),
            config.getJiraHostUrl(),
            jiraToken,
            config.getGithubRepoUrl(),
            githubToken
        );
    }
    
    /**
     * Persist verification results in separate short transaction.
     */
    @Transactional
    protected VerificationResponse persistVerificationResult(
        UUID id,
        VerificationResponse.JiraResult jiraResult,
        VerificationResponse.GitHubResult githubResult
    ) {
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        boolean allSuccess = VerificationStatus.SUCCESS.getValue().equals(jiraResult.status()) 
                          && VerificationStatus.SUCCESS.getValue().equals(githubResult.status());
        
        if (allSuccess) {
            config.markVerified();
            log.info("Config {} verified successfully", id);
        } else {
            String errorMsg = buildErrorMessage(jiraResult, githubResult);
            config.markInvalid(errorMsg);
            log.warn("Config {} verification failed: {}", id, errorMsg);
        }
        
        repository.save(config);
        
        return VerificationResponse.builder()
            .configId(id)
            .state(config.getState())
            .verificationResults(VerificationResponse.VerificationResults.builder()
                .jira(jiraResult)
                .github(githubResult)
                .build())
            .lastVerifiedAt(config.getLastVerifiedAt())
            .invalidReason(config.getInvalidReason())
            .build();
    }
    
    /**
     * DTO for passing decrypted config data between phases without holding transaction.
     */
    protected record ConfigDecryptionResult(
        Long groupId,
        String jiraHostUrl,
        String jiraToken,
        String githubRepoUrl,
        String githubToken
    ) {}
    
    /**
     * Restore soft-deleted configuration (ADMIN only).
     * 
     * Phase Execution:
     * 1. Authorization Phase (NO TX): Explicit ADMIN role check
     * 2. Load Phase (SHORT TX): Read deleted config
     * 3. Restore Phase (SHORT TX): Clear delete flags
     * 
     * Business Rules:
     * - BR-RESTORE-01: Only ADMIN can restore (enforced in service layer)
     * - BR-RESTORE-02: Clear deleted fields
     * - BR-RESTORE-03: State transitions to DRAFT
     * - BR-RESTORE-04: Cannot restore if permanently deleted (90+ days)
     * 
     * Security Fixes:
     * - ✅ Explicit ADMIN check in service layer (not relying on controller)
     * 
     * @param id Config ID
     * @param roles User roles from JWT
     * @return Restored config info
     * @throws ForbiddenException if user is not ADMIN
     * @throws ConfigNotFoundException if config not found
     * @throws IllegalStateException if config is not deleted
     */
    public RestoreResponse restoreConfig(UUID id, List<String> roles) {
        log.warn("Admin restoring config {}", id);
        
        // ========== PHASE 1: AUTHORIZATION (NO TRANSACTION) ==========
        boolean isAdmin = roles.contains("ADMIN") || roles.contains("ROLE_ADMIN");
        if (!isAdmin) {
            log.error("Non-admin user attempted to restore config {}", id);
            throw new ForbiddenException("Only ADMIN can restore deleted configurations");
        }
        
        // ========== PHASE 2 & 3: LOAD AND RESTORE (SHORT TRANSACTION) ==========
        return persistRestore(id);
    }
    
    /**
     * Persist restore operation in short transaction.
     */
    @Transactional
    protected RestoreResponse persistRestore(UUID id) {
        ProjectConfig config = repository.findByIdIncludingDeleted(id)
            .orElseThrow(() -> new ConfigNotFoundException("Configuration not found or permanently deleted"));
        
        if (config.getDeletedAt() == null) {
            throw new IllegalStateException("Configuration is not deleted");
        }
        
        config.restore();
        repository.save(config);
        
        log.info("Config {} restored successfully", id);
        
        return RestoreResponse.builder()
            .message("Configuration restored successfully")
            .configId(id)
            .restoredAt(Instant.now())
            .state(config.getState())
            .build();
    }
    
    /**
     * Get decrypted tokens for internal API (Sync Service).
     * 
     * Security:
     * - SEC-INTERNAL-03: Return decrypted tokens (no masking)
     * - SEC-INTERNAL-04: Only if state = VERIFIED
     * 
     * @param id Config ID
     * @return Decrypted tokens
     */
    @Transactional(readOnly = true)
    public DecryptedTokensResponse getDecryptedTokens(UUID id) {
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // SEC-INTERNAL-04: Only return VERIFIED configs
        if (!"VERIFIED".equals(config.getState())) {
            throw new ForbiddenException("Configuration not verified (state: " + config.getState() + ")");
        }
        
        // Decrypt tokens
        String jiraToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        String githubToken = encryptionService.decrypt(config.getGithubTokenEncrypted());
        
        return DecryptedTokensResponse.builder()
            .configId(config.getId())
            .groupId(config.getGroupId())
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(jiraToken)
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(githubToken)
            .state(config.getState())
            .build();
    }
    
    /**
     * Convert entity to response DTO with token masking.
     */
    private ConfigResponse toResponse(ProjectConfig config) {
        // Decrypt tokens for masking
        String jiraTokenDecrypted = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        String githubTokenDecrypted = encryptionService.decrypt(config.getGithubTokenEncrypted());
        
        return ConfigResponse.builder()
            .id(config.getId())
            .groupId(config.getGroupId())
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(maskingService.maskJiraToken(jiraTokenDecrypted))
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(maskingService.maskGithubToken(githubTokenDecrypted))
            .state(config.getState())
            .lastVerifiedAt(config.getLastVerifiedAt())
            .invalidReason(config.getInvalidReason())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }
    
    private String buildErrorMessage(VerificationResponse.JiraResult jira, 
                                     VerificationResponse.GitHubResult github) {
        if (!"SUCCESS".equals(jira.status()) && !"SUCCESS".equals(github.status())) {
            return String.format("Jira: %s; GitHub: %s", jira.message(), github.message());
        } else if (!"SUCCESS".equals(jira.status())) {
            return "Jira: " + jira.message();
        } else {
            return "GitHub: " + github.message();
        }
    }
}
