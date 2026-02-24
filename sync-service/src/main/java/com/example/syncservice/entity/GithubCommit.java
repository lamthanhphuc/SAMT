package com.example.syncservice.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * GitHub commit entity storing raw commit data.
 * Denormalized for detailed GitHub-specific analytics.
 */
@Entity
@Table(name = "github_commits",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_github_commits_config_sha",
                        columnNames = {"project_config_id", "commit_sha"})
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubCommit extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
    private Long projectConfigId;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "committed_date", nullable = false)
    private LocalDateTime committedDate;

    @Column(name = "author_email")
    private String authorEmail;

    @Column(name = "author_name")
    private String authorName;

    @Column(name = "author_login")
    private String authorLogin;

    @Column(name = "additions")
    private Integer additions;

    @Column(name = "deletions")
    private Integer deletions;

    @Column(name = "total_changes")
    private Integer totalChanges;

    @Column(name = "files_changed")
    private Integer filesChanged;
}
