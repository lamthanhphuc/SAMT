package com.example.syncservice.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Aggregated result for manual full sync of a single project config.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncAllResultDto {

    private UUID projectConfigId;
    private boolean success;
    private boolean degraded;
    private long durationMs;
    private String correlationId;
    private SyncResultDto jira;
    private SyncResultDto github;
}