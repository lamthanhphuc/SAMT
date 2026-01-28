package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Login/Token response DTO.
 * @see docs/SRS.md - UC-LOGIN Response
 */
public record LoginResponse(
    @JsonProperty("accessToken")
    String accessToken,

    @JsonProperty("refreshToken")
    String refreshToken,

    @JsonProperty("tokenType")
    String tokenType,

    @JsonProperty("expiresIn")
    long expiresIn
) {
    /**
     * Factory method with default tokenType = "Bearer"
     */
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn);
    }
}
