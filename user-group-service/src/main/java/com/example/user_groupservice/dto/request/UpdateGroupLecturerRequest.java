package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateGroupLecturerRequest {
    
    @NotNull(message = "Lecturer ID is required")
    @Positive(message = "Lecturer ID must be positive")
    private Long lecturerId;
}
