package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for UC27 - Update Group Lecturer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLecturerRequest {
    
    @NotNull(message = "Lecturer ID is required")
    @Positive(message = "Lecturer ID must be greater than 0")
    private Long lecturerId;
}
