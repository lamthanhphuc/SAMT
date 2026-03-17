package com.example.reportservice.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ReportRequest {

    @NotBlank
    private String projectConfigId;

    // Có dùng AI hay không
    private boolean useAi = true;

    // DOCX hoặc PDF
    @NotBlank
    private String exportType = "DOCX";
}
