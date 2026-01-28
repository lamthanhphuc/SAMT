package com.example.identityservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Logout request DTO.
 * @see docs/SRS.md - UC-LOGOUT Input
 */
public record LogoutRequest(
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {
}
