package com.fpt.projectconfig.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response format
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private ErrorDetail error;
    private Instant timestamp;

    @Data
    @Builder
    public static class ErrorDetail {
        private String code;
        private String message;
        private String field;
        private Map<String, String> details;
    }

    public static ErrorResponse of(String code, String message) {
        return ErrorResponse.builder()
                .error(ErrorDetail.builder().code(code).message(message).build())
                .timestamp(Instant.now())
                .build();
    }

    public static ErrorResponse of(String code, String message, String field) {
        return ErrorResponse.builder()
                .error(ErrorDetail.builder().code(code).message(message).field(field).build())
                .timestamp(Instant.now())
                .build();
    }
}
