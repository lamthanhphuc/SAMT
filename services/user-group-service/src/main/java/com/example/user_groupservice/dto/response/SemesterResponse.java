package com.example.user_groupservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SemesterResponse {
    
    private Long id;
    private String semesterCode;
    private String semesterName;
    private LocalDate startDate;
    private LocalDate endDate;
    private Boolean isActive;
    private Instant createdAt;
    private Instant updatedAt;
}
