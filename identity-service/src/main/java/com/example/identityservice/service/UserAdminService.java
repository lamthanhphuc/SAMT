package com.example.identityservice.service;

import com.example.identityservice.entity.User;
import com.example.identityservice.exception.InvalidUserStateException;
import com.example.identityservice.exception.SelfActionException;
import com.example.identityservice.exception.UserNotFoundException;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.security.SecurityContextHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin service for user management.
 * Handles soft delete, restore, lock/unlock operations.
 * 
 * @see docs/SRS-Auth.md - Admin API Endpoints
 * @see docs/Authentication-Authorization-Design.md - Section 8. Admin Operations Design
 * @see docs/Security-Review.md - Section 11.3 Soft Delete Rules Verification
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
     * UC-SOFT-DELETE: Soft delete a user.
     * - Sets deleted_at timestamp
     * - Revokes all refresh tokens
     * - Creates audit log
     * 
     * @param userId ID of user to delete
     * @throws UserNotFoundException if user not found (404)
     * @throws InvalidUserStateException if user already deleted (400)
     * @throws SelfActionException if admin tries to delete own account (400)
     */
    @Transactional
    public void softDeleteUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        // Admin self-delete prevention
        if (userId.equals(actorId)) {
            throw new SelfActionException("Cannot delete own account");
        }

        // Check if already deleted (should not happen due to @SQLRestriction, but defensive check)
        if (user.isDeleted()) {
            throw new InvalidUserStateException("User already deleted");
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
     * UC-RESTORE: Restore a soft-deleted user.
     * 
     * @param userId ID of user to restore
     * @throws UserNotFoundException if user not found (404)
     * @throws InvalidUserStateException if user is not deleted (400)
     */
    @Transactional
    public void restoreUser(Long userId) {
        // Use native query to find deleted user (bypasses @SQLRestriction)
        User user = userRepository.findByIdIncludingDeleted(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!user.isDeleted()) {
            throw new InvalidUserStateException("User is not deleted");
        }

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        // Restore
        user.restore();
        userRepository.save(user);

        // Audit
        auditService.logUserRestored(user, actorId);
    }

    /**
     * UC-LOCK-ACCOUNT: Lock a user account.
     * - Sets status to LOCKED
     * - Revokes all refresh tokens
     * 
     * Idempotent: If already locked, no error, no duplicate audit.
     * 
     * @param userId ID of user to lock
     * @param reason Reason for locking (optional)
     * @throws UserNotFoundException if user not found (404)
     * @throws SelfActionException if admin tries to lock own account (400)
     */
    @Transactional
    public void lockUser(Long userId, String reason) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        // Admin self-lock prevention
        if (userId.equals(actorId)) {
            throw new SelfActionException("Cannot lock own account");
        }

        // Idempotent: If already locked, do nothing
        if (user.isLocked()) {
            return;
        }

        user.lock();
        userRepository.save(user);

        // Revoke all tokens
        refreshTokenService.revokeAllTokens(user);

        // Audit
        auditService.logAccountLocked(user, actorId, reason);
    }

    /**
     * UC-UNLOCK-ACCOUNT: Unlock a user account.
     * 
     * @param userId ID of user to unlock
     * @throws UserNotFoundException if user not found (404)
     * @throws InvalidUserStateException if user is not locked (400)
     */
    @Transactional
    public void unlockUser(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // Validate user is actually locked
        if (!user.isLocked()) {
            throw new InvalidUserStateException("User is not locked");
        }

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        user.unlock();
        userRepository.save(user);

        // Audit
        auditService.logAccountUnlocked(user, actorId);
    }
}
