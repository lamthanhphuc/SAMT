package com.example.identityservice.controller;

import com.example.identityservice.dto.AdminActionResponse;
import com.example.identityservice.entity.AuditLog;
import com.example.identityservice.repository.AuditLogRepository;
import com.example.identityservice.service.UserAdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
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
    public ResponseEntity<AdminActionResponse> deleteUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId) {
        
        userAdminService.softDeleteUser(userId);
        
        return ResponseEntity.ok(AdminActionResponse.of("User deleted successfully", userId));
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
    public ResponseEntity<AdminActionResponse> restoreUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId) {
        
        userAdminService.restoreUser(userId);
        
        return ResponseEntity.ok(AdminActionResponse.of("User restored successfully", userId));
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
    public ResponseEntity<AdminActionResponse> lockUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId,
            @Parameter(description = "Reason for locking") @RequestParam( value = "reason", required = false) String reason) {
        
        userAdminService.lockUser(userId, reason);
        
        return ResponseEntity.ok(AdminActionResponse.of("User locked successfully", userId));
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
    public ResponseEntity<AdminActionResponse> unlockUser(
            @Parameter(description = "User ID") @PathVariable("userId") Long userId) {
        
        userAdminService.unlockUser(userId);
        
        return ResponseEntity.ok(AdminActionResponse.of("User unlocked successfully", userId));
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
    @GetMapping("/audit/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLog>> getAuditByEntity(
            @Parameter(description = "Entity type (e.g., User)") @PathVariable("entityType") String entityType,
            @Parameter(description = "Entity ID") @PathVariable("entityId") Long entityId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityIdOrderByTimestampDesc(entityType, entityId, pageable);
        return ResponseEntity.ok(logs);
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
    @GetMapping("/audit/actor/{actorId}")
    public ResponseEntity<Page<AuditLog>> getAuditByActor(
            @Parameter(description = "Actor user ID") @PathVariable("actorId") Long actorId,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByActorIdOrderByTimestampDesc(actorId, pageable);
        return ResponseEntity.ok(logs);
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
    public ResponseEntity<Page<AuditLog>> getAuditByDateRange(
            @Parameter(description = "Start date (ISO format)") 
            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)") 
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByTimestampBetween(startDate, endDate, pageable);
        return ResponseEntity.ok(logs);
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
    public ResponseEntity<Page<AuditLog>> getSecurityEvents(
            @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findSecurityEvents(pageable);
        return ResponseEntity.ok(logs);
    }
}
