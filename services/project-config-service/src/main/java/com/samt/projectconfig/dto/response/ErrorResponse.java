package com.samt.projectconfig.dto.response;

import lombok.Builder;

/**
 * Generic error response DTO.
 * Consistent with Identity Service error format.
 */
@Builder
public record ErrorResponse(
    int statusCode,
    String error,
    String message,
    Object details,
    String timestamp
) {
}
