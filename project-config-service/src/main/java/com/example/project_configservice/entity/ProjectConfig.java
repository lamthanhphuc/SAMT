package com.example.project_configservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Project Configuration Entity.
 * Stores encrypted Jira and GitHub credentials for student groups.
 * 
 * One-to-one relationship: group_id UNIQUE constraint.
 * Soft delete enabled via deletedAt field with @SQLRestriction.
 */
@Entity
@Table(name = "project_configs",
       uniqueConstraints = @UniqueConstraint(
           name = "uq_config_group",
           columnNames = {"group_id"}
       ))
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;
    
    // Group reference (NO FK - microservices pattern)
    @Column(name = "group_id", nullable = false)
    private UUID groupId;
    
    // Jira configuration
    @Column(name = "jira_host_url", nullable = false, length = 255)
    private String jiraHostUrl;
    
    @Column(name = "jira_api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String jiraApiTokenEncrypted;
    
    // GitHub configuration
    @Column(name = "github_repo_url", nullable = false, length = 255)
    private String githubRepoUrl;
    
    @Column(name = "github_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String githubTokenEncrypted;
    
    // State machine
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private ConfigState state;
    
    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;
    
    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;
    
    // Soft delete
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;
    
    // Audit timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        if (this.createdAt == null) {
            this.createdAt = now;
        }
        if (this.updatedAt == null) {
            this.updatedAt = now;
        }
        if (this.state == null) {
            this.state = ConfigState.DRAFT;
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
    
    /**
     * Perform soft delete on this config.
     * Transitions state to DELETED and records deletion metadata.
     */
    public void softDelete(Long deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.state = ConfigState.DELETED;
        this.updatedAt = Instant.now();
    }
    
    /**
     * Transition state to DRAFT after update.
     * Clears verification metadata.
     */
    public void transitionToDraft() {
        this.state = ConfigState.DRAFT;
        this.lastVerifiedAt = null;
        this.invalidReason = "Configuration updated, verification required";
    }
    
    /**
     * Transition state to VERIFIED after successful verification.
     */
    public void transitionToVerified() {
        this.state = ConfigState.VERIFIED;
        this.lastVerifiedAt = Instant.now();
        this.invalidReason = null;
    }
    
    /**
     * Transition state to INVALID after failed verification.
     */
    public void transitionToInvalid(String reason) {
        this.state = ConfigState.INVALID;
        this.lastVerifiedAt = null;
        this.invalidReason = reason;
    }
}
