package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Registration response DTO.
 * @see docs/SRS.md - UC-REGISTER Response (Success)
 */
public record RegisterResponse(
    @JsonProperty("user")
    UserDto user,

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
     * Factory method with default tokenType = "Bearer" and expiresIn = 900 (15 minutes)
     */
    public static RegisterResponse of(UserDto user, String accessToken, String refreshToken) {
        return new RegisterResponse(user, accessToken, refreshToken, "Bearer", 900);
    }
}
