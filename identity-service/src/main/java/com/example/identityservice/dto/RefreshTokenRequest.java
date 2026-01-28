package com.example.identityservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Refresh token request DTO.
 * @see docs/SRS.md - UC-REFRESH-TOKEN Input
 */
public record RefreshTokenRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
