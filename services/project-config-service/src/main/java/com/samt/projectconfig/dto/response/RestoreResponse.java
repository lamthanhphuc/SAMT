package com.samt.projectconfig.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for restore operation.
 */
@Builder
public record RestoreResponse(
    String message,
    UUID configId,
    Instant restoredAt,
    String state
) {}
