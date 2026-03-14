package com.example.identityservice.service;

import com.example.identityservice.dto.UpdateProfileRequest;
import com.example.identityservice.entity.User;
import com.example.identityservice.exception.EmailAlreadyExistsException;
import com.example.identityservice.exception.UserNotFoundException;
import com.example.identityservice.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for current-user profile operations.
 */
@Service
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
}