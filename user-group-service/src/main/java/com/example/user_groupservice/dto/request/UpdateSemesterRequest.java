package com.example.user_groupservice.dto.request;

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
public class UpdateSemesterRequest {
    
    @Pattern(regexp = ".*\\S.*", message = "Semester name must contain at least one non-whitespace character")
    private String semesterName;
    private LocalDate startDate;
    private LocalDate endDate;
}
