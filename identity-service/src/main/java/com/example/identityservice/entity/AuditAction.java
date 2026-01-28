package com.example.identityservice.entity;

/**
 * Audit action types for AuditLog.
 * Maps to Database-Design.md §6.2 Action Types.
 * 
 * @see docs/Database-Design.md
 */
public enum AuditAction {
    // Entity lifecycle
    CREATE,           // Entity created (user registration)
    UPDATE,           // Entity modified (profile update)
    SOFT_DELETE,      // Entity soft-deleted (admin deletes user)
    RESTORE,          // Entity restored from soft-delete
    
    // Authentication actions (from SRS)
    LOGIN_SUCCESS,    // UC-LOGIN success
    LOGIN_FAILED,     // UC-LOGIN failure (wrong password)
    LOGIN_DENIED,     // UC-LOGIN denied (account locked)
    LOGOUT,           // UC-LOGOUT
    REFRESH_SUCCESS,  // UC-REFRESH-TOKEN success
    REFRESH_REUSE,    // UC-REFRESH-TOKEN reuse detection (security event)
    REFRESH_EXPIRED,  // UC-REFRESH-TOKEN token expired
    
    // Account management
    ACCOUNT_LOCKED,   // Account locked by admin
    ACCOUNT_UNLOCKED, // Account unlocked by admin
    PASSWORD_CHANGE   // Password changed
}
