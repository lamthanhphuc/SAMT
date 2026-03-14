package com.example.identityservice.entity;

/**
 * User roles for authorization
 *
 * Used in JWT claims and @PreAuthorize annotations
 * Spring Security automatically prefixes with "ROLE_"
 */
public enum Role {
    /**
     * Administrator - Full system access
     */
    ADMIN,

    /**
     * Lecturer - Can manage students and projects
     */
    LECTURER,

    /**
     * Student - Can view and contribute to projects
     */
    STUDENT
}
