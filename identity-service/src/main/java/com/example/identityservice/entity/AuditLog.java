package com.example.identityservice.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Audit Log entity for tracking security-sensitive operations.
 * 
 * Design Decision:
 * - Immutable: Audit logs are never updated or deleted
 * - Denormalized: actor_email stored for query performance (no JOIN needed)
 * - JSON values: old_value/new_value stored as TEXT for flexibility
 * 
 * @see docs/SRS-Auth.md - Security requirements
 */
@Entity
@Table(name = "audit_logs", indexes = {
    @Index(name = "idx_audit_entity", columnList = "entity_type, entity_id"),
    @Index(name = "idx_audit_actor", columnList = "actor_id"),
    @Index(name = "idx_audit_action", columnList = "action"),
    @Index(name = "idx_audit_created_at", columnList = "created_at"),
    @Index(name = "idx_audit_outcome", columnList = "outcome")
})
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // What was changed
    @Column(name = "entity_type", nullable = false, length = 100)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    // What action was performed
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    // Who performed the action (NULL for system/anonymous actions)
    @Column(name = "actor_id")
    private Long actorId;

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    // When
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // Request context
    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "user_agent", length = 500)
    private String userAgent;

    // Change details (JSON)
    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    // Outcome
    @Column(nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private AuditOutcome outcome = AuditOutcome.SUCCESS;

    public enum AuditOutcome {
        SUCCESS,  // Action completed successfully
        FAILURE,  // Action failed (e.g., wrong password)
        DENIED    // Action denied (e.g., account locked, token reuse)
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    // Private constructor - use Builder
    protected AuditLog() {
    }

    private AuditLog(Builder builder) {
        this.entityType = builder.entityType;
        this.entityId = builder.entityId;
        this.action = builder.action;
        this.actorId = builder.actorId;
        this.actorEmail = builder.actorEmail;
        this.ipAddress = builder.ipAddress;
        this.userAgent = builder.userAgent;
        this.oldValue = builder.oldValue;
        this.newValue = builder.newValue;
        this.outcome = builder.outcome;
    }

    /**
     * Builder pattern for immutable AuditLog creation.
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String entityType;
        private Long entityId;
        private AuditAction action;
        private Long actorId;
        private String actorEmail;
        private String ipAddress;
        private String userAgent;
        private String oldValue;
        private String newValue;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;

        public Builder entityType(String entityType) {
            this.entityType = entityType;
            return this;
        }

        public Builder entityId(Long entityId) {
            this.entityId = entityId;
            return this;
        }

        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public Builder actorId(Long actorId) {
            this.actorId = actorId;
            return this;
        }

        public Builder actorEmail(String actorEmail) {
            this.actorEmail = actorEmail;
            return this;
        }

        public Builder ipAddress(String ipAddress) {
            this.ipAddress = ipAddress;
            return this;
        }

        public Builder userAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder oldValue(String oldValue) {
            this.oldValue = oldValue;
            return this;
        }

        public Builder newValue(String newValue) {
            this.newValue = newValue;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder success() {
            this.outcome = AuditOutcome.SUCCESS;
            return this;
        }

        public Builder failure() {
            this.outcome = AuditOutcome.FAILURE;
            return this;
        }

        public Builder denied() {
            this.outcome = AuditOutcome.DENIED;
            return this;
        }

        public AuditLog build() {
            return new AuditLog(this);
        }
    }

    // Getters only (immutable)
    public Long getId() { return id; }
    public String getEntityType() { return entityType; }
    public Long getEntityId() { return entityId; }
    public AuditAction getAction() { return action; }
    public Long getActorId() { return actorId; }
    public String getActorEmail() { return actorEmail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getOldValue() { return oldValue; }
    public String getNewValue() { return newValue; }
    public AuditOutcome getOutcome() { return outcome; }
}
