package com.example.reportservice.controller;

import com.example.reportservice.dto.request.ReportRequest;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.ReportMetadataResponse;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.service.ReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "report-management", description = "Report generation, metadata and download APIs")
public class ReportController {

    private final ReportingService service;

    @PostMapping("/srs")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    @Operation(summary = "Generate SRS report")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Report generated", content = @Content(schema = @Schema(implementation = ReportResponse.class), examples = @ExampleObject(value = "{\"success\":true,\"status\":201,\"path\":\"/api/reports/srs\",\"data\":{\"reportId\":\"5f2c5d35-431f-4a59-8168-88ff3c42649e\",\"status\":\"COMPLETED\",\"createdAt\":\"2026-03-12T10:30:00\",\"downloadUrl\":\"/api/reports/5f2c5d35-431f-4a59-8168-88ff3c42649e/download\"},\"timestamp\":\"2026-03-12T10:30:01Z\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "404", description = "Project configuration not found")
    })
    public ResponseEntity<ReportResponse> generateSrs(
            @Valid @RequestBody ReportRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        ReportResponse response = service.generate(
                request.getProjectConfigId(),
                jwt.getSubject(),
                request.isUseAi(),
                request.getExportType());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{reportId}")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    @Operation(summary = "Get report metadata")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Metadata retrieved", content = @Content(schema = @Schema(implementation = ReportMetadataResponse.class))),
        @ApiResponse(responseCode = "404", description = "Report not found")
    })
    public ReportMetadataResponse getReport(@PathVariable UUID reportId) {
        return service.getReport(reportId);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    @Operation(summary = "List generated reports")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report history retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "400", description = "Invalid filter")
    })
    public PageResponse<ReportMetadataResponse> listReports(
        @RequestParam(required = false) Long projectConfigId,
        @RequestParam(required = false) String type,
        @RequestParam(required = false) UUID createdBy,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return service.listReports(projectConfigId, type, createdBy, page, size);
    }

    @GetMapping("/{reportId}/download")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    @Operation(summary = "Download generated report file")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Report file streamed", content = @Content(mediaType = "application/octet-stream")),
        @ApiResponse(responseCode = "404", description = "Report or file not found")
    })
    public ResponseEntity<Resource> downloadReport(@PathVariable UUID reportId) {
        ReportingService.ReportDownload download = service.loadReportDownload(reportId);

        return ResponseEntity.ok()
            .contentType(download.mediaType())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8)
                .build()
                .toString())
            .body(download.resource());
    }
}
