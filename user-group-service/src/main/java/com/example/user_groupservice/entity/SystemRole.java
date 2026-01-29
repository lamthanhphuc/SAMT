package com.example.user_groupservice.entity;

/**
 * System-level roles (different from group roles).
 * Stored in JWT claims and used for authorization.
 */
public enum SystemRole {
    ADMIN,
    LECTURER,
    STUDENT
}
