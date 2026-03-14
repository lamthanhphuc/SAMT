package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Metadata for a generated report")
public class ReportMetadataResponse {

    @Schema(format = "uuid")
    private UUID reportId;

    @Schema(example = "42")
    private Long projectConfigId;

    @Schema(example = "SRS")
    private String type;

    @Schema(format = "uuid")
    private UUID createdBy;

    @Schema(format = "date-time")
    private LocalDateTime createdAt;

    @Schema(example = "COMPLETED")
    private String status;

    @Schema(example = "srs_5f2c5d35-431f-4a59-8168-88ff3c42649e.pdf")
    private String fileName;

    @Schema(example = "/api/reports/5f2c5d35-431f-4a59-8168-88ff3c42649e/download")
    private String downloadUrl;
}