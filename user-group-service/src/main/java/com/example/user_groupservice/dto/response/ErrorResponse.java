package com.example.user_groupservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private int statusCode;
    private String error;
    private String message;
    private Instant timestamp;
    private Map<String, String> errors;
    
    /**
     * Create error response without field errors.
     */
    public static ErrorResponse of(int statusCode, String error, String message) {
        return ErrorResponse.builder()
                .statusCode(statusCode)
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
    
    /**
     * Create error response with field errors (for validation).
     */
    public static ErrorResponse of(int statusCode, String error, String message, Map<String, String> errors) {
        return ErrorResponse.builder()
                .statusCode(statusCode)
                .error(error)
                .message(message)
                .timestamp(Instant.now())
                .errors(errors)
                .build();
    }
}
