package com.example.reportservice.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AnalyticsReportRequest {

    @NotBlank
    private String projectConfigId;

    @NotBlank
    private String groupId;

    @Valid
    private TimeRange timeRange;

    @NotNull
    @Valid
    private List<Member> members = new ArrayList<>();

    @Valid
    private List<JiraIssue> jiraIssues = new ArrayList<>();

    @Valid
    private List<GitCommit> gitCommits = new ArrayList<>();

    @Data
    public static class TimeRange {
        private String from;

        private String to;
    }

    @Data
    public static class Member {
        @NotBlank
        private String id;

        @NotBlank
        private String name;

        // Optional fields to improve author/assignee mapping.
        // (GitHub) commit author is commonly matched by email/login.
        private String email;
        private String githubUsername;
    }

    @Data
    public static class JiraIssue {
        @NotBlank
        private String id;

        @NotBlank
        private String assigneeId;

        @NotBlank
        private String status; // TODO|IN_PROGRESS|DONE

        @NotBlank
        private String createdAt; // ISO_DATE

        private String completedAt; // ISO_DATE|null

        private String dueDate; // ISO_DATE|null
    }

    @Data
    public static class GitCommit {
        @NotBlank
        private String authorId;

        @NotBlank
        private String message;

        private long linesAdded;
        private long linesDeleted;

        @NotBlank
        private String timestamp; // ISO_DATE
    }
}

