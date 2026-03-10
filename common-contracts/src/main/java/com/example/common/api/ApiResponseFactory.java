package com.example.common.api;

import java.time.Instant;

public final class ApiResponseFactory {

    private ApiResponseFactory() {
    }

    public static <T> ApiResponse<T> success(int status, T data, String path, String correlationId) {
        return success(status, data, path, correlationId, null);
    }

    public static <T> ApiResponse<T> success(int status, T data, String path, String correlationId, Boolean degraded) {
        return new ApiResponse<>(
            Instant.now().toString(),
            status,
            true,
            data,
            null,
            null,
            path,
            correlationId,
            degraded
        );
    }

    public static ApiResponse<Void> error(int status, String error, String message, String path, String correlationId) {
        return new ApiResponse<>(
            Instant.now().toString(),
            status,
            false,
            null,
            error,
            message,
            path,
            correlationId,
            null
        );
    }
}