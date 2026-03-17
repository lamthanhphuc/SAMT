package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Admin dashboard overview metrics")
public class AdminOverviewResponse {

    private Long semesterId;
    private long totalUsers;
    private long totalGroups;
    private long activeProjects;
    private long pendingSyncJobs;
    private String jiraApiHealth;
    private String githubApiHealth;
    private String serverHealth;
    private LocalDateTime lastCalculatedAt;
}
