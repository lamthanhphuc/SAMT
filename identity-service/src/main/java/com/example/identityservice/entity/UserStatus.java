package com.example.identityservice.entity;

/**
 * User account status
 *
 * Controls whether user can authenticate
 */
public enum UserStatus {
    /**
     * User can login normally
     */
    ACTIVE,

    /**
     * User is locked by admin - cannot login
     * All refresh tokens are revoked when locked
     */
    LOCKED
}
