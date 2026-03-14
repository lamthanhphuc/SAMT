package com.example.reportservice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Response returned after generating a report")
public class ReportResponse {

    @Schema(format = "uuid")
    private UUID reportId;

    @Schema(example = "COMPLETED")
    private String status;

    @Schema(format = "date-time")
    private LocalDateTime createdAt;

    @Schema(example = "/api/reports/5f2c5d35-431f-4a59-8168-88ff3c42649e/download")
    private String downloadUrl;
}
