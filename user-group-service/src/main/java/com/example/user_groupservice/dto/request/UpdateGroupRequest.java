package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request DTO for updating a group.
 * Note: Semester is immutable and cannot be updated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupRequest {
    
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 50, message = "Group name must be between 3 and 50 characters")
    @Pattern(regexp = "^[A-Z]{2,4}[0-9]{2,4}-G[0-9]+$", message = "Invalid group name format. Expected: SE1705-G1")
    private String groupName;
    
    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerId;
}
