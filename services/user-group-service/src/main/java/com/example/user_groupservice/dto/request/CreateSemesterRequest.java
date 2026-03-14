package com.example.user_groupservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSemesterRequest {
    
    @NotBlank(message = "Semester code is required")
    @Pattern(regexp = ".*\\S.*", message = "Semester code must contain at least one non-whitespace character")
    private String semesterCode;
    
    @NotBlank(message = "Semester name is required")
    @Pattern(regexp = ".*\\S.*", message = "Semester name must contain at least one non-whitespace character")
    private String semesterName;
    
    @NotNull(message = "Start date is required")
    private LocalDate startDate;
    
    @NotNull(message = "End date is required")
    private LocalDate endDate;
}
