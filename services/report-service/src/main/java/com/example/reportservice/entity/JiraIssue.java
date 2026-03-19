package com.example.reportservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "jira_issues",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_jira_issues_config_key",
                        columnNames = {"project_config_id", "issue_key"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraIssue {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
        private UUID projectConfigId;

    @Column(name = "issue_key", nullable = false, length = 50)
    private String issueKey;

    @Column(name = "issue_id", nullable = false, length = 50)
    private String issueId;

    @Column(name = "summary", nullable = false, length = 500)
    private String summary;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "issue_type", length = 50)
    private String issueType;

    @Column(name = "status", length = 50)
    private String status;

    @Column(name = "assignee_email")
    private String assigneeEmail;

    @Column(name = "assignee_name")
    private String assigneeName;

    @Column(name = "reporter_email")
    private String reporterEmail;

    @Column(name = "reporter_name")
    private String reporterName;

    @Column(name = "priority", length = 50)
    private String priority;

    // Nếu BaseEntity bên sync-service có created_at, updated_at
    // thì bạn phải tự khai báo lại ở đây:

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "due_date")
    private LocalDate dueDate;
}
