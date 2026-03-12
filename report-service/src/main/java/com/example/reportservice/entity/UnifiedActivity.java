package com.example.reportservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "unified_activities")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnifiedActivity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
    private UUID projectConfigId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 20)
    private ActivitySource source;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_type", nullable = false, length = 50)
    private ActivityType activityType;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "title", nullable = false, length = 1000)
    private String title;

    @Column(name = "description")
    private String description;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

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