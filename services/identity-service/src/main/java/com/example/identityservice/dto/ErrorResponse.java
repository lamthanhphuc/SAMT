package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response DTO.
 * @see docs/Security-Review.md - Section 12. Exception Handling Requirements
 */
public record ErrorResponse(
    @JsonProperty("statusCode")
    int statusCode,

    @JsonProperty("error")
    String error,

    @JsonProperty("message")
    String message
) {
    public static ErrorResponse of(int statusCode, String error, String message) {
        return new ErrorResponse(statusCode, error, message);
    }
}
