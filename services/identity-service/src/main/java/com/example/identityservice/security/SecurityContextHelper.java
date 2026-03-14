package com.example.identityservice.security;

import com.example.identityservice.entity.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Helper class to extract current user from SecurityContext.
 * 
 * Design Decision:
 * - Returns Optional to handle anonymous requests gracefully
 * - Used by AuditService to get actor information
 */
@Component
public class SecurityContextHelper {

    /**
     * Get current authenticated user.
     * @return Optional<User> - empty if not authenticated
     */
    public Optional<User> getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }

        Object principal = authentication.getPrincipal();
        
        if (principal instanceof User user) {
            return Optional.of(user);
        }
        
        return Optional.empty();
    }

    /**
     * Get current user ID.
     * @return Optional<Long> - empty if not authenticated
     */
    public Optional<Long> getCurrentUserId() {
        return getCurrentUser().map(User::getId);
    }

    /**
     * Get current user email.
     * @return Optional<String> - empty if not authenticated
     */
    public Optional<String> getCurrentUserEmail() {
        return getCurrentUser().map(User::getEmail);
    }

    /**
     * Check if current user is authenticated.
     */
    public boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.isAuthenticated() 
                && authentication.getPrincipal() instanceof User;
    }
}
