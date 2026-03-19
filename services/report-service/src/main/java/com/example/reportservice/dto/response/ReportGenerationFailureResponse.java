package com.example.reportservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ReportGenerationFailureResponse {
    private String status;
    private String step;
    private String reason;
    private List<String> logs;
}
