package com.example.syncservice.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Unified activity entity normalizing data from Jira and GitHub.
 * Provides consistent schema for analytics and reporting.
 */
@Entity
@Table(name = "unified_activities",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_unified_activities_config_source_external",
                        columnNames = {"project_config_id", "source", "external_id"})
        },
        indexes = {
                @Index(name = "idx_unified_activities_config", columnList = "project_config_id"),
                @Index(name = "idx_unified_activities_source_type", columnList = "source,activity_type"),
                @Index(name = "idx_unified_activities_author", columnList = "author_email"),
                @Index(name = "idx_unified_activities_created_at", columnList = "created_at"),
                @Index(name = "idx_unified_activities_deleted_at", columnList = "deleted_at")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedActivity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
    private Long projectConfigId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ActivitySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private ActivityType activityType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "title", nullable = false, length = 500)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "status", length = 50)
    private String status;

    public enum ActivitySource {
        JIRA,
        GITHUB
    }

    public enum ActivityType {
        TASK,
        ISSUE,
        BUG,
        STORY,
        COMMIT,
        PULL_REQUEST
    }
}
