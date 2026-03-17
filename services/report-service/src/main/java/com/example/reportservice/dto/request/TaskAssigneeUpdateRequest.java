package com.example.reportservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record TaskAssigneeUpdateRequest(
    @NotNull(message = "assigneeUserId is required")
    @Positive(message = "assigneeUserId must be greater than 0")
    Long assigneeUserId
) {
}
