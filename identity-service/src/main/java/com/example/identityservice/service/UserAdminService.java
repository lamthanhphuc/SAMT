package com.example.identityservice.service;

import com.example.identityservice.entity.User;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin service for user management.
 * Handles soft delete, restore, lock/unlock operations.
 */
@Service
public class UserAdminService {

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AuditService auditService;
    private final SecurityContextHelper securityContextHelper;

    public UserAdminService(
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            SecurityContextHelper securityContextHelper) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.securityContextHelper = securityContextHelper;
    }

    /**
     * Soft delete a user.
     * - Sets deleted_at timestamp
     * - Revokes all refresh tokens
     * - Creates audit log
     * 
     * @param userId ID of user to delete
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    public void softDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        if (userId.equals(actorId)) {
            throw new IllegalArgumentException("Cannot delete own account");
        }

        // Soft delete
        user.softDelete(actorId);
        userRepository.save(user);

        // Revoke all tokens (user can't refresh anymore)
        refreshTokenService.revokeAllTokens(user);

        // Audit
        auditService.logUserDeleted(user, actorId);
    }

    /**
     * Restore a soft-deleted user.
     * 
     * @param userId ID of user to restore
     * @throws IllegalArgumentException if user not found
     */
    @Transactional
    public void restoreUser(Long userId) {
        // Use native query to find deleted user
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        if (!user.isDeleted()) {
            throw new IllegalArgumentException("User is not deleted: " + userId);
        }

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        // Restore
        user.restore();
        userRepository.save(user);

        // Audit
        auditService.logUserRestored(user, actorId);
    }

    /**
     * Lock a user account.
     * - Sets status to LOCKED
     * - Revokes all refresh tokens
     * 
     * @param userId ID of user to lock
     * @param reason Reason for locking
     */
    @Transactional
    public void lockUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        user.setStatus(User.Status.LOCKED);
        userRepository.save(user);

        // Revoke all tokens
        refreshTokenService.revokeAllTokens(user);

        // Audit
        auditService.logAccountLocked(user, actorId, reason);
    }

    /**
     * Unlock a user account.
     * 
     * @param userId ID of user to unlock
     */
    @Transactional
    public void unlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + userId));

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        user.setStatus(User.Status.ACTIVE);
        userRepository.save(user);

        // Audit
        auditService.logAccountUnlocked(user, actorId);
    }
}
