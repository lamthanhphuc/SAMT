package com.example.syncservice.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Jira issue entity storing raw Jira data.
 * Denormalized for detailed Jira-specific querying.
 */
@Entity
@Table(name = "jira_issues",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_jira_issues_config_key",
                        columnNames = {"project_config_id", "issue_key"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraIssue extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
    private Long projectConfigId;

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
}
