package com.example.syncservice.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

/**
 * Request payload for manual sync endpoints.
 */
public record SyncRequestDto(
    @NotNull(message = "Project config ID is required")
    UUID projectConfigId
) {}