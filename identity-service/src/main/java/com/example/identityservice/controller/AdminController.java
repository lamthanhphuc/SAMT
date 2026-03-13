package com.example.identityservice.controller;

import com.example.common.api.ApiResponseFactory;
import com.example.identityservice.dto.*;
import com.example.identityservice.entity.AuditLog;
import com.example.identityservice.entity.User;
import com.example.identityservice.repository.AuditLogRepository;
import com.example.identityservice.service.UserAdminService;
import com.example.identityservice.web.CorrelationIdFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.MDC;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

/**
 * Admin endpoints for user management and audit log viewing.
 * Requires ADMIN role for all operations.
 * 
 * @see docs/SRS-Auth.md - Admin API Endpoints Summary
 * @see docs/Authentication-Authorization-Design.md - Section 8. Admin Operations Design
 */
@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Admin operations for user management")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserAdminService userAdminService;
    private final AuditLogRepository auditLogRepository;

    public AdminController(
            UserAdminService userAdminService,
            AuditLogRepository auditLogRepository) {
        this.userAdminService = userAdminService;
        this.auditLogRepository = auditLogRepository;
    }

    // ========================================
    // USER MANAGEMENT
    // ========================================

    /**
     * UC-ADMIN-CREATE-USER: Admin creates user account with any role.
     * 
     * POST /api/admin/users
     * 
     * Only ADMIN can create accounts with LECTURER or ADMIN roles.
     * Public registration endpoint only allows STUDENT role.
     * 
     * @param request AdminCreateUserRequest with email, password, fullName, role
     * @return 201 Created with user info and temporary password
     * @throws EmailAlreadyExistsException 409 Conflict (email already exists)
     */
    @Operation(
            summary = "Create user account (Admin only)",
            description = "Create user account with any role (STUDENT, LECTURER, ADMIN). Only accessible by ADMIN.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created"),
                    @ApiResponse(responseCode = "409", description = "Email already exists"),
                    @ApiResponse(responseCode = "403", description = "Not authorized")
            }
    )
    @PostMapping("/users")
    public ResponseEntity<com.example.common.api.ApiResponse<AdminCreateUserResponse>> createUser(
            @Valid @RequestBody AdminCreateUserRequest request,
            HttpServletRequest servletRequest) {
        
        User createdUser = userAdminService.createUser(
                request.email(),
                request.password(),
                request.fullName(),
                request.role()
        );
        
                return success(
                        HttpStatus.CREATED,
                        AdminCreateUserResponse.of(
                                "User created successfully",
                                UserDto.fromEntity(createdUser),
                                request.password()
                        ),
                        servletRequest
                );
    }

    /**
     * UC-SOFT-DELETE: Soft delete user
     * 
     * DELETE /api/admin/users/{userId}
     * 
     * @return 200 OK with message and userId
     * @throws UserNotFoundException 404 Not Found
     * @throws InvalidUserStateException 400 Bad Request (already deleted)
     * @throws SelfActionException 400 Bad Request (self-delete)
     */
    @Operation(
            summary = "Soft delete user",
            description = "Soft delete a user (set deleted_at, revoke tokens). Does not permanently remove data.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User deleted"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "400", description = "Already deleted or self-delete"),
                    @ApiResponse(responseCode = "403", description = "Not authorized")
            }
    )
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<com.example.common.api.ApiResponse<AdminActionResponse>> deleteUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId,
            HttpServletRequest servletRequest) {
        
        userAdminService.softDeleteUser(userId);
        
                return success(HttpStatus.OK, AdminActionResponse.of("User deleted successfully", userId), servletRequest);
    }

    /**
     * UC-RESTORE: Restore deleted user
     * 
     * POST /api/admin/users/{userId}/restore
     * 
     * @return 200 OK with message and userId
     * @throws UserNotFoundException 404 Not Found
     * @throws InvalidUserStateException 400 Bad Request (not deleted)
     */
    @Operation(
            summary = "Restore deleted user",
            description = "Restore a soft-deleted user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User restored"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "400", description = "User is not deleted")
            }
    )
    @PostMapping("/users/{userId}/restore")
    public ResponseEntity<com.example.common.api.ApiResponse<AdminActionResponse>> restoreUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId,
            HttpServletRequest servletRequest) {
        
        userAdminService.restoreUser(userId);
        
                return success(HttpStatus.OK, AdminActionResponse.of("User restored successfully", userId), servletRequest);
    }

    /**
     * UC-LOCK-ACCOUNT: Lock user account
     * 
     * POST /api/admin/users/{userId}/lock?reason=...
     * 
     * Idempotent: If already locked, returns 200 OK (no error).
     * 
     * @return 200 OK with message and userId
     * @throws UserNotFoundException 404 Not Found
     * @throws SelfActionException 400 Bad Request (self-lock)
     */
    @Operation(
            summary = "Lock user account",
            description = "Lock a user account and revoke all tokens. Idempotent.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Account locked"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "400", description = "Self-lock attempt")
            }
    )
    @PostMapping("/users/{userId}/lock")
    public ResponseEntity<com.example.common.api.ApiResponse<AdminActionResponse>> lockUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId,
            @Parameter(description = "Reason for locking") @RequestParam( value = "reason", required = false) String reason,
            HttpServletRequest servletRequest) {
        
        userAdminService.lockUser(userId, reason);
        
                return success(HttpStatus.OK, AdminActionResponse.of("User locked successfully", userId), servletRequest);
    }

    /**
     * UC-UNLOCK-ACCOUNT: Unlock user account
     * 
     * POST /api/admin/users/{userId}/unlock
     * 
     * @return 200 OK with message and userId
     * @throws UserNotFoundException 404 Not Found
     * @throws InvalidUserStateException 400 Bad Request (not locked)
     */
    @Operation(
            summary = "Unlock user account",
            description = "Unlock a locked user account",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Account unlocked"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "400", description = "User is not locked")
            }
    )
    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<com.example.common.api.ApiResponse<AdminActionResponse>> unlockUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId,
            HttpServletRequest servletRequest) {
        
        userAdminService.unlockUser(userId);
        
                return success(HttpStatus.OK, AdminActionResponse.of("User unlocked successfully", userId), servletRequest);
    }

    /**
     * UC-MAP-EXTERNAL-ACCOUNTS: Map/unmap external accounts (Jira, GitHub).
     * 
     * PUT /api/admin/users/{userId}/external-accounts
     * 
     * @param userId Target user ID
     * @param request ExternalAccountsRequest with jiraAccountId and githubUsername (null to unmap)
     * @return 200 OK with updated user data
     * @throws UserNotFoundException 404 Not Found
     * @throws InvalidUserStateException 400 Bad Request (user is deleted)
     * @throws ConflictException 409 Conflict (external account already mapped to another user)
     */
    @Operation(
            summary = "Map/unmap external accounts",
            description = "Map or unmap Jira account ID and GitHub username. Send null to unmap.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "External accounts updated"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "400", description = "User is deleted"),
                    @ApiResponse(responseCode = "409", description = "External account already mapped to another user")
            }
    )
    @PutMapping("/users/{userId}/external-accounts")
    public ResponseEntity<com.example.common.api.ApiResponse<ExternalAccountsResponse>> updateExternalAccounts(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId,
            @Valid @RequestBody ExternalAccountsRequest request,
            HttpServletRequest servletRequest) {
        
        User updatedUser = userAdminService.updateExternalAccounts(
                userId,
                request.jiraAccountId(),
                request.githubUsername()
        );
        
                return success(
                        HttpStatus.OK,
                        ExternalAccountsResponse.of(
                                "External accounts mapped successfully",
                                UserDto.fromEntity(updatedUser)
                        ),
                        servletRequest
        );
    }

    // ========================================
    // AUDIT LOG VIEWING
    // ========================================

    /**
     * GET /api/admin/audit/entity/{entityType}/{entityId}
     * Get audit history for a specific entity.
     */
    @Operation(
            summary = "Get audit logs for entity",
            description = "Get audit history for a specific entity (e.g., User)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Audit logs retrieved")
            }
    )
        @GetMapping("/audit/entity/{entityType}/{entityId:\\d+}")
    public ResponseEntity<com.example.common.api.ApiResponse<Page<AuditLog>>> getAuditByEntity(
            @Parameter(description = "Entity type (e.g., User)") @PathVariable("entityType") String entityType,
            @Parameter(description = "Entity ID") @PathVariable("entityId") Long entityId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest servletRequest) {
        
        Page<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId, pageable);
                return success(HttpStatus.OK, logs, servletRequest);
    }

    /**
     * GET /api/admin/audit/actor/{actorId}
     * Get all actions performed by a specific user.
     */
    @Operation(
            summary = "Get audit logs by actor",
            description = "Get all actions performed by a specific user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Audit logs retrieved")
            }
    )
        @GetMapping("/audit/actor/{actorId:\\d+}")
    public ResponseEntity<com.example.common.api.ApiResponse<Page<AuditLog>>> getAuditByActor(
            @Parameter(description = "Actor user ID") @PathVariable("actorId") Long actorId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest servletRequest) {
        
        Page<AuditLog> logs = auditLogRepository.findByActorIdOrderByTimestampDesc(actorId, pageable);
                return success(HttpStatus.OK, logs, servletRequest);
    }

    /**
     * GET /api/admin/audit/range?startDate=...&endDate=...
     * Get audit logs within a date range.
     */
    @Operation(
            summary = "Get audit logs by date range",
            description = "Get audit logs within a date range",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Audit logs retrieved")
            }
    )
    @GetMapping("/audit/range")
    public ResponseEntity<com.example.common.api.ApiResponse<Page<AuditLog>>> getAuditByDateRange(
            @Parameter(description = "Start date (ISO format)") 
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)") 
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest servletRequest) {
        
        Page<AuditLog> logs = auditLogRepository.findByTimestampBetween(startDate, endDate, pageable);
                return success(HttpStatus.OK, logs, servletRequest);
    }

    /**
     * GET /api/admin/audit/security-events
     * Get security-related events (login failures, token reuse, etc.)
     */
    @Operation(
            summary = "Get security events",
            description = "Get security-related audit events (login failures, token reuse, etc.)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security events retrieved")
            }
    )
    @GetMapping("/audit/security-events")
    public ResponseEntity<com.example.common.api.ApiResponse<Page<AuditLog>>> getSecurityEvents(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable,
            HttpServletRequest servletRequest) {
        
        Page<AuditLog> logs = auditLogRepository.findSecurityEvents(pageable);
                return success(HttpStatus.OK, logs, servletRequest);
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
}
