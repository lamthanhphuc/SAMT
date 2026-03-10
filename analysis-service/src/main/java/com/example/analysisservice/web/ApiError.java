package com.example.analysisservice.web;

import java.time.Instant;

public record ApiError(
        String code,
        String message,
        String correlationId,
        Instant timestamp
) {
}
