package com.example.syncservice.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Sync job status and execution summary")
public class SyncJobResponse {

    @Schema(example = "101")
    private Long syncJobId;

    @Schema(format = "uuid")
    private UUID projectConfigId;

    @Schema(example = "JIRA_ISSUES")
    private String jobType;

    @Schema(example = "COMPLETED")
    private String status;

    @Schema(format = "date-time")
    private LocalDateTime startedAt;

    @Schema(format = "date-time")
    private LocalDateTime completedAt;

    @Schema(example = "120")
    private Integer recordsFetched;

    @Schema(example = "118")
    private Integer recordsSaved;

    @Schema(example = "false")
    private boolean degraded;

    @Schema(example = "jira.example.com rate limit reached")
    private String errorMessage;

    @Schema(example = "corr-20260312-001")
    private String correlationId;
}