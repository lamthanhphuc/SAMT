package com.samt.projectconfig.dto.response;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for delete operation.
 */
@Builder
public record DeleteResponse(
    String message,
    UUID configId,
    Instant deletedAt,
    int retentionDays
) {}
