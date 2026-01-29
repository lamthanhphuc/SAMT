package com.example.identityservice.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Standard error response DTO.
 * @see docs/Security-Review.md - Section 12. Exception Handling Requirements
 */
public record ErrorResponse(
    @JsonProperty("error")
    String error,

    @JsonProperty("message")
    String message
) {
    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message);
    }
}
