package com.samt.projectconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity representing a project configuration for external integrations (Jira, GitHub).
 * 
 * One-to-one relationship with Group (each group has at most 1 config).
 * Implements soft delete pattern with 90-day retention.
 */
@Entity
@Table(name = "project_configs")
@SQLRestriction("deleted_at IS NULL")  // Auto-filter soft-deleted records
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    /**
     * Group ID from User-Group Service (no FK constraint - microservices pattern)
     * UNIQUE constraint: One config per group
     */
    @Column(name = "group_id", nullable = false, unique = true)
    private Long groupId;
    
    // Jira Configuration
    @Column(name = "jira_host_url", nullable = false, length = 255)
    private String jiraHostUrl;
    
    /**
     * Encrypted Jira API token.
     * Format: {iv_base64}:{ciphertext_base64}:{auth_tag_base64}
     * Encryption: AES-256-GCM
     */
    @Column(name = "jira_api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String jiraApiTokenEncrypted;
    
    // GitHub Configuration
    @Column(name = "github_repo_url", nullable = false, length = 512)
    private String githubRepoUrl;
    
    /**
     * Encrypted GitHub Personal Access Token.
     * Format: {iv_base64}:{ciphertext_base64}:{auth_tag_base64}
     * Encryption: AES-256-GCM
     */
    @Column(name = "github_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String githubTokenEncrypted;
    
    /**
     * State machine: DRAFT → VERIFIED / INVALID → DELETED
     */
    @Column(name = "state", nullable = false, length = 20)
    private String state = "DRAFT";
    
    /**
     * Timestamp of last successful verification.
     * NULL if never verified or after update.
     */
    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;
    
    /**
     * Error message if state = INVALID.
     * Set to "Configuration updated, verification required" after updates.
     */
    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;
    
    // Soft Delete Fields
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;  // User ID who deleted the config
    
    // Audit Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;
    
    @Column(name = "updated_by", nullable = false)
    private Long updatedBy;
    
    /**
     * Optimistic locking for concurrent updates
     */
    @Version
    @Column(name = "version")
    private Integer version;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
        if (this.state == null) {
            this.state = "DRAFT";
        }
        if (this.version == null) {
            this.version = 0;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * Transition config to DRAFT state after updates.
     * BR-UPDATE-01: If credentials updated → reset verification.
     */
    public void transitionToDraft() {
        this.state = "DRAFT";
        this.lastVerifiedAt = null;
        this.invalidReason = "Configuration updated, verification required";
    }
    
    /**
     * Transition to VERIFIED state after successful verification.
     */
    public void markVerified() {
        this.state = "VERIFIED";
        this.lastVerifiedAt = Instant.now();
        this.invalidReason = null;
    }
    
    /**
     * Transition to INVALID state after failed verification.
     */
    public void markInvalid(String reason) {
        this.state = "INVALID";
        this.lastVerifiedAt = null;
        this.invalidReason = reason;
    }
    
    /**
     * Soft delete: set deleted_at and deleted_by.
     * BR-DELETE-01: Soft delete only (90-day retention).
     */
    public void softDelete(Long deletedBy) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedBy;
        this.state = "DELETED";
    }
    
    /**
     * Restore soft-deleted config (ADMIN only).
     * BR-RESTORE-02: Clear deleted fields, reset to DRAFT.
     */
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
        this.state = "DRAFT";
        this.lastVerifiedAt = null;
        this.invalidReason = "Configuration restored, verification required";
    }
}
