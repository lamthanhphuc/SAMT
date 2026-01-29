package com.example.user_groupservice.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

/**
 * Custom UserDetails implementation for JWT authentication.
 * Holds user ID and roles extracted from JWT token.
 */
@Getter
public class CurrentUser implements UserDetails {
    
    private final UUID userId;
    private final Collection<? extends GrantedAuthority> authorities;
    
    public CurrentUser(UUID userId, Collection<? extends GrantedAuthority> authorities) {
        this.userId = userId;
        this.authorities = authorities;
    }
    
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }
    
    @Override
    public String getPassword() {
        return null; // Not used for JWT authentication
    }
    
    @Override
    public String getUsername() {
        return userId.toString();
    }
    
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }
    
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }
    
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }
    
    @Override
    public boolean isEnabled() {
        return true;
    }
    
    /**
     * Check if user has a specific role.
     */
    public boolean hasRole(String role) {
        return authorities.stream()
                .anyMatch(auth -> auth.getAuthority().equals("ROLE_" + role));
    }
    
    /**
     * Check if user is an admin.
     */
    public boolean isAdmin() {
        return hasRole("ADMIN");
    }
    
    /**
     * Check if user is a lecturer.
     */
    public boolean isLecturer() {
        return hasRole("LECTURER");
    }
    
    /**
     * Check if user is a student.
     */
    public boolean isStudent() {
        return hasRole("STUDENT");
    }
}
