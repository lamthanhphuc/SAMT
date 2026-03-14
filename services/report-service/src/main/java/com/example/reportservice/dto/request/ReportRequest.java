package com.example.reportservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ReportRequest {

    @NotNull
    @Min(1)
    private Long projectConfigId;

    // Có dùng AI hay không
    private boolean useAi = true;

    // DOCX hoặc PDF
    @NotBlank
    private String exportType = "DOCX";
}
