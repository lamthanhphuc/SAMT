package com.example.reportservice.dto.request;

import jakarta.validation.constraints.NotBlank;

public record TaskStatusUpdateRequest(
    @NotBlank(message = "status is required")
    String status
) {
}
