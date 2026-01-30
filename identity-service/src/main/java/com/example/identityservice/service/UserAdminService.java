package com.example.identityservice.service;

import com.example.identityservice.entity.AuditAction;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.exception.InvalidUserStateException;
import com.example.identityservice.exception.SelfActionException;
import com.example.identityservice.exception.UserNotFoundException;
import com.example.identityservice.repository.UserRepository;
import com.example.identityservice.security.SecurityContextHelper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin service for user management.
 * Handles user creation, soft delete, restore, lock/unlock operations, and external account mapping.
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
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(
            UserRepository userRepository,
            RefreshTokenService refreshTokenService,
            AuditService auditService,
            SecurityContextHelper securityContextHelper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.auditService = auditService;
        this.securityContextHelper = securityContextHelper;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * UC-ADMIN-CREATE-USER: Admin creates user account with any role.
     * 
     * Only ADMIN can create LECTURER or ADMIN accounts.
     * Public registration (/api/auth/register) only allows STUDENT.
     * 
     * @param email User email
     * @param password User password (will be hashed)
     * @param fullName User full name
     * @param role User role (STUDENT, LECTURER, or ADMIN)
     * @return Created user entity
     * @throws EmailAlreadyExistsException if email already registered (409)
     */
    @Transactional
    public User createUser(String email, String password, String fullName, String role) {
        // Hash password with BCrypt
        String passwordHash = passwordEncoder.encode(password);

        // Create user
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setFullName(fullName);
        user.setRole(User.Role.valueOf(role));
        user.setStatus(User.Status.ACTIVE);

        // Save user - DB UNIQUE constraint handles race condition
        try {
            user = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            throw new EmailAlreadyExistsException();
        }

        // Audit
        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);
        auditService.logUserCreated(user);

        return user;
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

    /**
     * UC-MAP-EXTERNAL-ACCOUNTS: Map/unmap external accounts (Jira, GitHub).
     * 
     * Business Rules:
     * - BR-MAP-01: jira_account_id must be unique (409 if exists)
     * - BR-MAP-02: github_username must be unique (409 if exists)
     * - BR-MAP-04: Cannot map to deleted users (400)
     * - BR-MAP-05: Audit log with old_value + new_value
     * - BR-MAP-06: To unmap, send null value
     * 
     * @param userId ID of user to update
     * @param jiraAccountId Jira account ID (null to unmap)
     * @param githubUsername GitHub username (null to unmap)
     * @return Updated user entity
     * @throws UserNotFoundException if user not found (404)
     * @throws InvalidUserStateException if user is deleted (400)
     * @throws ConflictException if external account already mapped to another user (409)
     */
    @Transactional
    public User updateExternalAccounts(Long userId, String jiraAccountId, String githubUsername) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        // BR-MAP-04: Cannot map to deleted users
        if (user.isDeleted()) {
            throw new InvalidUserStateException("Cannot map external accounts to deleted user");
        }

        Long actorId = securityContextHelper.getCurrentUserId().orElse(null);

        // Store old values for audit
        String oldJiraAccountId = user.getJiraAccountId();
        String oldGithubUsername = user.getGithubUsername();

        // BR-MAP-01: Validate Jira account ID uniqueness
        if (jiraAccountId != null && !jiraAccountId.equals(oldJiraAccountId)) {
            if (userRepository.existsByJiraAccountId(jiraAccountId)) {
                throw new ConflictException("Jira account ID already mapped to another user");
            }
        }

        // BR-MAP-02: Validate GitHub username uniqueness
        if (githubUsername != null && !githubUsername.equals(oldGithubUsername)) {
            if (userRepository.existsByGithubUsername(githubUsername)) {
                throw new ConflictException("GitHub username already mapped to another user");
            }
        }

        // Update external accounts
        user.setJiraAccountId(jiraAccountId);
        user.setGithubUsername(githubUsername);
        userRepository.save(user);

        // BR-MAP-05: Audit with old and new values
        String oldValue = String.format("{jira: %s, github: %s}", oldJiraAccountId, oldGithubUsername);
        String newValue = String.format("{jira: %s, github: %s}", jiraAccountId, githubUsername);
        
        auditService.logExternalAccountsUpdated(user, actorId, oldValue, newValue);

        return user;
    }
}
