package com.samt.projectconfig.controller;

import com.samt.projectconfig.dto.request.CreateConfigRequest;
import com.samt.projectconfig.dto.request.UpdateConfigRequest;
import com.samt.projectconfig.dto.response.*;
import com.samt.projectconfig.service.ProjectConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Async REST Controller for public API endpoints.
 * 
 * Architecture Changes (v2.0):
 * - ✅ All endpoints return CompletableFuture<ResponseEntity>
 * - ✅ HTTP threads freed immediately during I/O operations
 * - ✅ Spring MVC handles async response automatically
 * - ✅ Full non-blocking async flow from controller to gRPC
 * 
 * Base path: /api/project-configs
 * Authentication: JWT Bearer token
 * 
 * @author Production Team
 * @version 2.0 (Async refactored)
 */
@RestController
@RequestMapping("/api/project-configs")
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigController {
    
    private final ProjectConfigService service;
    
    
    /**
     * POST /api/project-configs
     * Create new configuration (async non-blocking).
     * 
     * HTTP thread freed during gRPC authorization and encryption.
     * Spring MVC automatically handles CompletableFuture response.
     * 
     * @return CompletableFuture<ResponseEntity> - completes when config created
     */
    @PostMapping
    public CompletableFuture<ResponseEntity<Map<String, Object>>> createConfig(
            @Valid @RequestBody CreateConfigRequest request,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Creating config for group {} by user {} (async)", request.groupId(), userId);
        
        return service.createConfig(request, userId, roles)
            .thenApply(response -> ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of(
                    "data", response,
                    "timestamp", Instant.now().toString()
                )));
    }
    
    /**
     * GET /api/project-configs/{id}
     * Get configuration by ID (async non-blocking).
     * 
     * HTTP thread freed during gRPC membership check (if required).
     * 
     * @return CompletableFuture<ResponseEntity> - completes when config fetched
     */
    @GetMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> getConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Getting config {} for user {} (async)", id, userId);
        
        return service.getConfig(id, userId, roles)
            .thenApply(response -> ResponseEntity.ok(Map.of(
                "data", response,
                "timestamp", Instant.now().toString()
            )));
    }
    
    /**
     * PUT /api/project-configs/{id}
     * Update configuration (async non-blocking).
     * 
     * HTTP thread freed during gRPC leader check.
     * 
     * @return CompletableFuture<ResponseEntity> - completes when config updated
     */
    @PutMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> updateConfig(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateConfigRequest request,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Updating config {} by user {} (async)", id, userId);
        
        return service.updateConfig(id, request, userId, roles)
            .thenApply(response -> ResponseEntity.ok(Map.of(
                "data", response,
                "timestamp", Instant.now().toString()
            )));
    }
    
    /**
     * DELETE /api/project-configs/{id}
     * Soft delete configuration (async non-blocking).
     * 
     * HTTP thread freed during gRPC leader check.
     * 
     * @return CompletableFuture<ResponseEntity> - completes when config deleted
     */
    @DeleteMapping("/{id}")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> deleteConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.warn("Deleting config {} by user {} (async)", id, userId);
        
        return service.deleteConfig(id, userId, roles)
            .thenApply(response -> ResponseEntity.ok(Map.of(
                "data", response,
                "timestamp", Instant.now().toString()
            )));
    }
    
    /**
     * POST /api/project-configs/{id}/verify
     * Verify configuration by testing Jira and GitHub APIs (fully async).
     * 
     * HTTP thread freed during:
     * - gRPC leader check (~800ms)
     * - External API calls (~10-12s parallel)
     * 
     * This is the endpoint with MAXIMUM benefit from async refactor!
     * 
     * @return CompletableFuture<ResponseEntity> - completes when verification done
     */
    @PostMapping("/{id}/verify")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> verifyConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Verifying config {} by user {} (fully async)", id, userId);
        
        return service.verifyConfig(id, userId, roles)
            .thenApply(response -> ResponseEntity.ok(Map.of(
                "data", response,
                "timestamp", Instant.now().toString()
            )));
    }
    
    
    /**
     * POST /api/admin/project-configs/{id}/restore
     * Restore soft-deleted configuration (ADMIN only).
     * 
     * Note: This endpoint doesn't have async gRPC calls, but kept as CompletableFuture
     * for consistency with other endpoints.
     * 
     * @return CompletableFuture<ResponseEntity> - completes when config restored
     */
    @PostMapping("/admin/{id}/restore")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> restoreConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        List<String> roles = getRolesFromAuthentication(authentication);
        
        // Check ADMIN role (defense in depth - also checked in service)
        if (!roles.contains("ADMIN") && !roles.contains("ROLE_ADMIN")) {
            return CompletableFuture.completedFuture(
                ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of(
                        "error", Map.of(
                            "code", "FORBIDDEN",
                            "message", "Only admin can restore deleted configurations"
                        ),
                        "timestamp", Instant.now().toString()
                    ))
            );
        }
        
        log.warn("Restoring config {} by admin (async)", id);
        
        return CompletableFuture.completedFuture(service.restoreConfig(id, roles))
            .thenApply(response -> ResponseEntity.ok(Map.of(
                "data", response,
                "timestamp", Instant.now().toString()
            )));
    }
    
    private List<String> getRolesFromAuthentication(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
    }
}
