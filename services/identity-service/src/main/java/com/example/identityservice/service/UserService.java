package com.example.identityservice.service;

import com.example.identityservice.dto.UpdateProfileRequest;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.ConflictException;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.exception.UserNotFoundException;
import com.example.identityservice.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for current-user profile operations.
 */
@Service
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final AuditService auditService;

    public UserService(UserRepository userRepository, AuditService auditService) {
        this.userRepository = userRepository;
        this.auditService = auditService;
    }

    /**
     * Get the current authenticated user's profile.
     *
     * @param userId authenticated user ID
     * @return user entity
     * @throws UserNotFoundException if user is not found
     */
    @Transactional(readOnly = true)
    public User getCurrentUserProfile(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /**
     * Update the current authenticated user's profile.
     *
     * @param userId authenticated user ID
     * @param request profile update payload
     * @return updated user entity
     * @throws UserNotFoundException if user is not found
     * @throws EmailAlreadyExistsException if email is already used by another user
     */
    @Transactional
    public User updateCurrentUserProfile(Long userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        String normalizedEmail = request.email().trim();
        String normalizedFullName = request.fullName().trim();

        userRepository.findByEmail(normalizedEmail)
                .filter(existingUser -> !existingUser.getId().equals(userId))
                .ifPresent(existingUser -> {
                    throw new EmailAlreadyExistsException(normalizedEmail);
                });

        String oldValue = String.format("{email: %s, fullName: %s}", user.getEmail(), user.getFullName());

        user.setEmail(normalizedEmail);
        user.setFullName(normalizedFullName);

        User updatedUser = userRepository.save(user);

        String newValue = String.format("{email: %s, fullName: %s}", updatedUser.getEmail(), updatedUser.getFullName());
        auditService.logProfileUpdated(updatedUser, oldValue, newValue);

        return updatedUser;
    }

    /**
     * Update member integration fields (only non-null fields are applied).
     *
     * @param memberId target user ID
     * @param jiraAccountId new Jira accountId (optional)
     * @param githubUsername new GitHub username (optional)
     */
    @Transactional
    public User updateMemberIntegrations(Long memberId, Long actorId, String jiraAccountId, String githubUsername) {
        User user = userRepository.findById(memberId)
            .orElseThrow(() -> new UserNotFoundException(memberId));

        log.info("user before update: {}", summarizeUser(user));

        String oldJira = user.getJiraAccountId();
        String oldGithub = user.getGithubUsername();

        if (jiraAccountId != null) {
            String normalized = jiraAccountId.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("jiraAccountId must not be blank");
            }
            if (!normalized.equals(oldJira) && userRepository.existsByJiraAccountId(normalized)) {
                throw new ConflictException("Jira account ID already mapped to another user");
            }
            user.setJiraAccountId(normalized);
        }

        if (githubUsername != null) {
            String normalized = githubUsername.trim();
            if (normalized.isBlank()) {
                throw new IllegalArgumentException("githubUsername must not be blank");
            }
            if (!normalized.equals(oldGithub) && userRepository.existsByGithubUsername(normalized)) {
                throw new ConflictException("GitHub username already mapped to another user");
            }
            user.setGithubUsername(normalized);
        }

        try {
            User saved = userRepository.save(user);
            log.info("user after update: {}", summarizeUser(saved));

            String oldValue = String.format("{jira: %s, github: %s}", oldJira, oldGithub);
            String newValue = String.format("{jira: %s, github: %s}", saved.getJiraAccountId(), saved.getGithubUsername());
            auditService.logExternalAccountsUpdated(saved, actorId, oldValue, newValue);
            return saved;
        } catch (RuntimeException ex) {
            log.error("Save error updating external accounts: memberId={}, actorId={}, jiraAccountId={}, githubUsername={}",
                memberId,
                actorId,
                jiraAccountId,
                githubUsername,
                ex
            );
            throw ex;
        }
    }

    private static String summarizeUser(User user) {
        if (user == null) return "null";
        return String.format("{id=%s, email=%s, jiraAccountId=%s, githubUsername=%s, status=%s, role=%s}",
            user.getId(),
            user.getEmail(),
            user.getJiraAccountId(),
            user.getGithubUsername(),
            user.getStatus(),
            user.getRole()
        );
    }
}