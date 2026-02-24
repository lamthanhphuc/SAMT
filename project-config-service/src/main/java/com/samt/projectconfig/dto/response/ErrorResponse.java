package com.samt.projectconfig.dto.response;

import lombok.Builder;

/**
 * Generic error response DTO.
 * Consistent with Identity Service error format.
 */
@Builder
public record ErrorResponse(
    Error error,
    String timestamp
) {
    
    @Builder
    public record Error(
        String code,
        String message,
        String field,
        Object details
    ) {}
}
