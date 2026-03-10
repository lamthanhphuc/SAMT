package com.samt.projectconfig.controller;

import com.example.common.api.ApiResponseFactory;
import com.samt.projectconfig.dto.request.CreateConfigRequest;
import com.samt.projectconfig.dto.request.UpdateConfigRequest;
import com.samt.projectconfig.dto.response.*;
import com.samt.projectconfig.security.CorrelationIdFilter;
import com.samt.projectconfig.service.ProjectConfigService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
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
    @Operation(summary = "Create configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Configuration created",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "400", description = "Bad request",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "409", description = "Conflict",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @PostMapping
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<ConfigResponse>>> createConfig(
            @Valid @RequestBody CreateConfigRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Creating config for group {} by user {} (async)", request.groupId(), userId);
        
        return service.createConfig(request, userId, roles)
            .thenApply(response -> success(HttpStatus.CREATED, response, servletRequest));
    }
    
    /**
     * GET /api/project-configs/{id}
     * Get configuration by ID (async non-blocking).
     * 
     * HTTP thread freed during gRPC membership check (if required).
     * 
     * @return CompletableFuture<ResponseEntity> - completes when config fetched
     */
    @Operation(summary = "Get configuration by id")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuration retrieved",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @GetMapping("/{id}")
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<ConfigResponse>>> getConfig(
            @PathVariable("id") UUID id,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Getting config {} for user {} (async)", id, userId);
        
        return service.getConfig(id, userId, roles)
            .thenApply(response -> success(HttpStatus.OK, response, servletRequest));
    }

    @GetMapping("/group/{groupId}")
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<ConfigResponse>>> getConfigByGroupId(
            @PathVariable("groupId") Long groupId,
            Authentication authentication,
            HttpServletRequest servletRequest) {

        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);

        log.info("Getting config for group {} and user {} (async)", groupId, userId);

        return service.getConfigByGroupId(groupId, userId, roles)
            .thenApply(response -> success(HttpStatus.OK, response, servletRequest));
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
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<ConfigResponse>>> updateConfig(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateConfigRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Updating config {} by user {} (async)", id, userId);
        
        return service.updateConfig(id, request, userId, roles)
            .thenApply(response -> success(HttpStatus.OK, response, servletRequest));
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
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<DeleteResponse>>> deleteConfig(
            @PathVariable("id") UUID id,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.warn("Deleting config {} by user {} (async)", id, userId);
        
        return service.deleteConfig(id, userId, roles)
            .thenApply(response -> success(HttpStatus.OK, response, servletRequest));
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
    @Operation(summary = "Verify configuration")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Configuration verified",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "404", description = "Configuration not found",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class))),
        @ApiResponse(responseCode = "503", description = "Verification service unavailable",
            content = @Content(schema = @Schema(implementation = com.example.common.api.ApiResponse.class)))
    })
    @PostMapping("/{id}/verify")
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<VerificationResponse>>> verifyConfig(
            @PathVariable("id") UUID id,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);
        
        log.info("Verifying config {} by user {} (fully async)", id, userId);
        
        return service.verifyConfig(id, userId, roles)
            .thenApply(response -> success(HttpStatus.OK, response, servletRequest));
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
        public CompletableFuture<ResponseEntity<com.example.common.api.ApiResponse<RestoreResponse>>> restoreConfig(
            @PathVariable("id") UUID id,
            Authentication authentication,
            HttpServletRequest servletRequest) {
        
        Long userId = getUserIdFromAuthentication(authentication);
        List<String> roles = getRolesFromAuthentication(authentication);
        
        // Check ADMIN role (defense in depth - also checked in service)
        if (!roles.contains("ADMIN") && !roles.contains("ROLE_ADMIN")) {
            return CompletableFuture.failedFuture(new com.samt.projectconfig.exception.ForbiddenException(
                "Only admin can restore deleted configurations"
            ));
        }
        
        log.warn("Restoring config {} by admin {} (async)", id, userId);
        
        return CompletableFuture.completedFuture(service.restoreConfig(id, userId, roles))
            .thenApply(response -> success(HttpStatus.OK, response, servletRequest));
    }

    private <T> ResponseEntity<com.example.common.api.ApiResponse<T>> success(HttpStatus status, T data, HttpServletRequest request) {
        return ResponseEntity.status(status).body(
            ApiResponseFactory.success(
                status.value(),
                data,
                request.getRequestURI(),
                resolveCorrelationId(request)
            )
        );
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String correlationId = request.getHeader(CorrelationIdFilter.HEADER_NAME);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        }
        return correlationId;
    }
    
    private List<String> getRolesFromAuthentication(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
    }

    private Long getUserIdFromAuthentication(Authentication authentication) {
        Object principal = authentication.getPrincipal();

        if (principal instanceof Jwt jwt) {
            return parseUserId(jwt.getSubject());
        }

        if (principal instanceof String s) {
            return parseUserId(s);
        }

        return parseUserId(authentication.getName());
    }

    private Long parseUserId(String value) {
        if (value == null || value.isBlank()) {
            throw new BadCredentialsException("Missing user id (sub)");
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new BadCredentialsException("Invalid user id (sub)");
        }
    }
}
