package com.fpt.projectconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

/**
 * Entity cho bảng project_configs
 * Lưu trữ cấu hình tích hợp Jira/GitHub đã được mã hóa cho các nhóm sinh viên
 */
@Entity
@Table(name = "project_configs")
@SQLRestriction("deleted_at IS NULL") // Soft delete: tự động lọc records đã xóa
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId; // One-to-one: mỗi group chỉ có 1 config

    // Jira Configuration
    @Column(name = "jira_host_url", nullable = false, length = 255)
    private String jiraHostUrl;

    @Column(name = "jira_api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String jiraApiTokenEncrypted; // AES-256-GCM encrypted

    // GitHub Configuration
    @Column(name = "github_repo_url", nullable = false, length = 255)
    private String githubRepoUrl;

    @Column(name = "github_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String githubTokenEncrypted; // AES-256-GCM encrypted

    // State Machine
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    @Builder.Default
    private ConfigState state = ConfigState.DRAFT;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;

    // Soft Delete (theo Soft Delete Retention Policy)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by")
    private Long deletedBy; // User ID from Identity Service (BIGINT)

    // Audit Timestamps
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version")
    private Long version; // Optimistic locking

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    // Business Methods

    public void transitionToDraft(String reason) {
        this.state = ConfigState.DRAFT;
        this.lastVerifiedAt = null;
        this.invalidReason = reason;
    }

    public void transitionToVerified() {
        this.state = ConfigState.VERIFIED;
        this.lastVerifiedAt = Instant.now();
        this.invalidReason = null;
    }

    public void transitionToInvalid(String reason) {
        this.state = ConfigState.INVALID;
        this.lastVerifiedAt = null;
        this.invalidReason = reason;
    }

    public void softDelete(Long userId) {
        this.state = ConfigState.DELETED;
        this.deletedAt = Instant.now();
        this.deletedBy = userId;
    }

    public void restore() {
        this.state = ConfigState.DRAFT;
        this.deletedAt = null;
        this.deletedBy = null;
        this.invalidReason = "Configuration restored, verification required";
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean canSync() {
        return state == ConfigState.VERIFIED && !isDeleted();
    }

    public enum ConfigState {
        DRAFT,      // Mới tạo hoặc đã update, chưa verify
        VERIFIED,   // Đã test connection thành công
        INVALID,    // Verification failed
        DELETED     // Soft deleted
    }
}
