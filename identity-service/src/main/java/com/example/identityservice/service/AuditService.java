package com.example.identityservice.service;

import com.example.identityservice.dto.UserAuditDto;
import com.example.identityservice.entity.AuditAction;
import com.example.identityservice.entity.AuditLog;
import com.example.identityservice.entity.User;
import com.example.identityservice.repository.AuditLogRepository;
import com.example.identityservice.security.SecurityContextHelper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Service for creating audit logs.
 * 
 * Design Decisions:
 * 1. @Async: Audit logging is non-blocking to not slow down main operations
 * 2. REQUIRES_NEW: Audit logs are persisted even if main transaction rolls back
 * 3. Graceful degradation: Failures in audit logging don't affect main operations
 * 4. UserAuditDto: Excludes passwordHash to prevent sensitive data leakage
 * 
 * @see docs/Database-Design.md - §6.2 Action Types
 * @see docs/Security-Review.md - §11 Implementation Review
 */
@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditLogRepository auditLogRepository;
    private final SecurityContextHelper securityContextHelper;
    private final ObjectMapper objectMapper;

    public AuditService(
            AuditLogRepository auditLogRepository,
            SecurityContextHelper securityContextHelper,
            ObjectMapper objectMapper) {
        this.auditLogRepository = auditLogRepository;
        this.securityContextHelper = securityContextHelper;
        this.objectMapper = objectMapper;
    }

    // ==================== Authentication Audit ====================

    /**
     * Log successful login.
     * UC-LOGIN success
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginSuccess(User user) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.LOGIN_SUCCESS,
                user.getId(),
                user.getEmail(),
                AuditLog.AuditOutcome.SUCCESS,
                null,
                null
        );
    }

    /**
     * Log failed login attempt.
     * UC-LOGIN failure (wrong password)
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginFailure(String email, String reason) {
        createAuditLog(
                "USER",
                null,  // User might not exist
                AuditAction.LOGIN_FAILED,
                null,
                email,
                AuditLog.AuditOutcome.FAILURE,
                null,
                reason
        );
    }

    /**
     * Log login denied (account locked).
     * UC-LOGIN denied
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLoginDenied(User user, String reason) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.LOGIN_DENIED,
                user.getId(),
                user.getEmail(),
                AuditLog.AuditOutcome.DENIED,
                null,
                reason
        );
    }

    /**
     * Log successful logout.
     * UC-LOGOUT
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logLogout(Long userId, String userEmail) {
        createAuditLog(
                "USER",
                userId != null ? userId : 0L,
                AuditAction.LOGOUT,
                userId,
                userEmail,
                AuditLog.AuditOutcome.SUCCESS,
                null,
                null
        );
    }

    /**
     * Log successful token refresh.
     * UC-REFRESH-TOKEN success
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRefreshSuccess(User user) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.REFRESH_SUCCESS,
                user.getId(),
                user.getEmail(),
                AuditLog.AuditOutcome.SUCCESS,
                null,
                null
        );
    }

    /**
     * Log token reuse detection (SECURITY EVENT).
     * UC-REFRESH-TOKEN reuse
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logRefreshReuse(User user) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.REFRESH_REUSE,
                user.getId(),
                user.getEmail(),
                AuditLog.AuditOutcome.DENIED,
                null,
                "Refresh token reuse detected - all tokens revoked"
        );
    }

    // ==================== User Lifecycle Audit ====================

    /**
     * Log user creation (registration).
     * Uses UserAuditDto to exclude passwordHash.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserCreated(User user) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.CREATE,
                user.getId(),
                user.getEmail(),
                AuditLog.AuditOutcome.SUCCESS,
                null,
                toUserJson(user)  // Uses UserAuditDto - no passwordHash
        );
    }

    /**
     * Log user soft delete.
     * Uses UserAuditDto to exclude passwordHash.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserDeleted(User user, Long deletedByUserId) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.SOFT_DELETE,
                deletedByUserId,
                securityContextHelper.getCurrentUserEmail().orElse("SYSTEM"),
                AuditLog.AuditOutcome.SUCCESS,
                toUserJson(user),  // old_value = state before delete
                null
        );
    }

    /**
     * Log user restore from soft delete.
     * Uses UserAuditDto to exclude passwordHash.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logUserRestored(User user, Long restoredByUserId) {
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.RESTORE,
                restoredByUserId,
                securityContextHelper.getCurrentUserEmail().orElse("SYSTEM"),
                AuditLog.AuditOutcome.SUCCESS,
                null,
                toUserJson(user)  // new_value = restored state
        );
    }

    /**
     * Log account lock.
     * Captures old status in old_value for audit trail.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccountLocked(User user, Long lockedByUserId, String reason) {
        // Capture old status before lock
        String oldStatus = "{\"status\":\"ACTIVE\"}";
        String newStatus = "{\"status\":\"LOCKED\",\"reason\":\"" + (reason != null ? reason : "") + "\"}";
        
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.ACCOUNT_LOCKED,
                lockedByUserId,
                securityContextHelper.getCurrentUserEmail().orElse("SYSTEM"),
                AuditLog.AuditOutcome.SUCCESS,
                oldStatus,
                newStatus
        );
    }

    /**
     * Log account unlock.
     * Captures status change in old_value/new_value.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void logAccountUnlocked(User user, Long unlockedByUserId) {
        String oldStatus = "{\"status\":\"LOCKED\"}";
        String newStatus = "{\"status\":\"ACTIVE\"}";
        
        createAuditLog(
                "USER",
                user.getId(),
                AuditAction.ACCOUNT_UNLOCKED,
                unlockedByUserId,
                securityContextHelper.getCurrentUserEmail().orElse("SYSTEM"),
                AuditLog.AuditOutcome.SUCCESS,
                oldStatus,
                newStatus
        );
    }

    // ==================== Internal ====================

    private void createAuditLog(
            String entityType,
            Long entityId,
            AuditAction action,
            Long actorId,
            String actorEmail,
            AuditLog.AuditOutcome outcome,
            String oldValue,
            String newValue) {

        try {
            AuditLog.Builder builder = AuditLog.builder()
                    .entityType(entityType)
                    .entityId(entityId != null ? entityId : 0L)
                    .action(action)
                    .actorId(actorId)
                    .actorEmail(actorEmail)
                    .outcome(outcome)
                    .oldValue(oldValue)
                    .newValue(newValue);

            // Add request context if available
            addRequestContext(builder);

            auditLogRepository.save(builder.build());

            log.debug("Audit log created: {} {} on {}:{}",
                    action, outcome, entityType, entityId);

        } catch (Exception e) {
            // Graceful degradation: log error but don't fail the main operation
            log.error("Failed to create audit log: {} {} on {}:{}",
                    action, outcome, entityType, entityId, e);
        }
    }

    private void addRequestContext(AuditLog.Builder builder) {
        try {
            ServletRequestAttributes attrs = 
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                builder.ipAddress(getClientIp(request));
                builder.userAgent(request.getHeader("User-Agent"));
            }
        } catch (Exception e) {
            // Ignore - request context not available (e.g., async task)
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Serialize User to JSON using UserAuditDto.
     * Excludes passwordHash for security.
     */
    private String toUserJson(User user) {
        return toJson(UserAuditDto.from(user));
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize object to JSON", e);
            return null;
        }
    }
}
