package com.example.syncservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO representing the result of a sync operation.
 * Used for tracking metrics and logging.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResultDto {

    private Long syncJobId;
    private Long projectConfigId;
    private String jobType;
    private boolean success;
    private boolean degraded;  // TRUE if fallback triggered (partial failure)
    private int recordsFetched;
    private int recordsSaved;
    private long durationMs;
    private String errorMessage;
    private String correlationId;
}
