package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for UC27 - Update Group Lecturer.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateLecturerRequest {
    
    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerId;
}
