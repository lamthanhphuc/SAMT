package com.example.identityservice.controller;

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
import java.util.Map;

/**
 * Admin endpoints for user management and audit log viewing.
 * Requires ADMIN role for all operations.
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

    @Operation(
            summary = "Soft delete user",
            description = "Soft delete a user (set deleted_at, revoke tokens). Does not permanently remove data.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User deleted"),
                    @ApiResponse(responseCode = "404", description = "User not found"),
                    @ApiResponse(responseCode = "403", description = "Not authorized")
            }
    )
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<Map<String, String>> deleteUser(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        userAdminService.softDeleteUser(userId);
        
        return ResponseEntity.ok(Map.of(
                "message", "User deleted successfully",
                "userId", userId.toString()
        ));
    }

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
    public ResponseEntity<Map<String, String>> restoreUser(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        userAdminService.restoreUser(userId);
        
        return ResponseEntity.ok(Map.of(
                "message", "User restored successfully",
                "userId", userId.toString()
        ));
    }

    @Operation(
            summary = "Lock user account",
            description = "Lock a user account and revoke all tokens",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Account locked"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/users/{userId}/lock")
    public ResponseEntity<Map<String, String>> lockUser(
            @Parameter(description = "User ID") @PathVariable Long userId,
            @Parameter(description = "Reason for locking") @RequestParam(required = false) String reason) {
        
        userAdminService.lockUser(userId, reason);
        
        return ResponseEntity.ok(Map.of(
                "message", "User locked successfully",
                "userId", userId.toString()
        ));
    }

    @Operation(
            summary = "Unlock user account",
            description = "Unlock a locked user account",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Account unlocked"),
                    @ApiResponse(responseCode = "404", description = "User not found")
            }
    )
    @PostMapping("/users/{userId}/unlock")
    public ResponseEntity<Map<String, String>> unlockUser(
            @Parameter(description = "User ID") @PathVariable Long userId) {
        
        userAdminService.unlockUser(userId);
        
        return ResponseEntity.ok(Map.of(
                "message", "User unlocked successfully",
                "userId", userId.toString()
        ));
    }

    // ========================================
    // AUDIT LOG VIEWING
    // ========================================

    @Operation(
            summary = "Get audit logs for entity",
            description = "Get audit history for a specific entity (e.g., User)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Audit logs retrieved")
            }
    )
    @GetMapping("/audit/entity/{entityType}/{entityId}")
    public ResponseEntity<Page<AuditLog>> getAuditByEntity(
            @Parameter(description = "Entity type (e.g., User)") @PathVariable String entityType,
            @Parameter(description = "Entity ID") @PathVariable Long entityId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByEntityTypeAndEntityId(entityType, entityId, pageable);
        return ResponseEntity.ok(logs);
    }

    @Operation(
            summary = "Get audit logs by actor",
            description = "Get all actions performed by a specific user",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Audit logs retrieved")
            }
    )
    @GetMapping("/audit/actor/{actorId}")
    public ResponseEntity<Page<AuditLog>> getAuditByActor(
            @Parameter(description = "Actor user ID") @PathVariable Long actorId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByActorId(actorId, pageable);
        return ResponseEntity.ok(logs);
    }

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
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @Parameter(description = "End date (ISO format)") 
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findByTimestampBetween(startDate, endDate, pageable);
        return ResponseEntity.ok(logs);
    }

    @Operation(
            summary = "Get security events",
            description = "Get security-related audit events (login failures, token reuse, etc.)",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Security events retrieved")
            }
    )
    @GetMapping("/audit/security-events")
    public ResponseEntity<Page<AuditLog>> getSecurityEvents(
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        
        Page<AuditLog> logs = auditLogRepository.findSecurityEvents(pageable);
        return ResponseEntity.ok(logs);
    }
}
