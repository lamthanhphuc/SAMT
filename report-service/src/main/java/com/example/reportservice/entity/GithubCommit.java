package com.example.reportservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "github_commits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GithubCommit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_config_id", nullable = false)
    private UUID projectConfigId;

    @Column(name = "commit_sha", nullable = false, length = 40)
    private String commitSha;

    @Column(name = "message", nullable = false)
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

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}