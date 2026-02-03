package com.example.project_configservice.entity;

/**
 * State machine for Project Config lifecycle.
 * 
 * DRAFT → VERIFIED → INVALID → DELETED
 *    ↓       ↓          ↓
 *    └───────┴──────────┘
 */
public enum ConfigState {
    /**
     * New config OR updated config not yet verified.
     * lastVerifiedAt = NULL
     */
    DRAFT,
    
    /**
     * Connection to Jira/GitHub tested successfully.
     * lastVerifiedAt = NOT NULL
     */
    VERIFIED,
    
    /**
     * Verification failed (invalid credentials or network error).
     * invalidReason = NOT NULL
     */
    INVALID,
    
    /**
     * Soft deleted (can be restored within 90 days).
     * deletedAt = NOT NULL
     */
    DELETED
}
