package com.example.user_groupservice.dto.request;

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
    
    private String semesterName;
    private LocalDate startDate;
    private LocalDate endDate;
}
