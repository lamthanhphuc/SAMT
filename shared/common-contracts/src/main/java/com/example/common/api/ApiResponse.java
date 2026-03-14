package com.example.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
    String timestamp,
    int status,
    boolean success,
    T data,
    String error,
    String message,
    String path,
    String correlationId,
    Boolean degraded
) {
}