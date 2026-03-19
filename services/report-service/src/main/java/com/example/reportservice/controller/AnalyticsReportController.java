package com.example.reportservice.controller;

import com.example.reportservice.dto.request.AnalyticsReportRequest;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.service.AnalyticsReportingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "report-analytics", description = "Deterministic analytics reports (Excel)")
public class AnalyticsReportController {

    private final AnalyticsReportingService analyticsReportingService;

    @PostMapping("/work-distribution")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    @Operation(summary = "Generate Work Distribution report (Excel)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Report generated",
            content = @Content(schema = @Schema(implementation = ReportResponse.class),
                examples = @ExampleObject(value = "{\"reportId\":\"5f2c5d35-431f-4a59-8168-88ff3c42649e\",\"status\":\"COMPLETED\",\"createdAt\":\"2026-03-12T10:30:00\",\"downloadUrl\":\"/api/reports/5f2c5d35-431f-4a59-8168-88ff3c42649e/download\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ReportResponse> generateWorkDistribution(
        @Valid @RequestBody AnalyticsReportRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ReportResponse response = analyticsReportingService.generateWorkDistribution(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/commit-analysis")
    @PreAuthorize("hasAnyRole('ADMIN','LECTURER')")
    @Operation(summary = "Generate Commit Analysis report (Excel)")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Report generated",
            content = @Content(schema = @Schema(implementation = ReportResponse.class),
                examples = @ExampleObject(value = "{\"reportId\":\"5f2c5d35-431f-4a59-8168-88ff3c42649e\",\"status\":\"COMPLETED\",\"createdAt\":\"2026-03-12T10:30:00\",\"downloadUrl\":\"/api/reports/5f2c5d35-431f-4a59-8168-88ff3c42649e/download\"}"))),
        @ApiResponse(responseCode = "400", description = "Invalid request"),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public ResponseEntity<ReportResponse> generateCommitAnalysis(
        @Valid @RequestBody AnalyticsReportRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        ReportResponse response = analyticsReportingService.generateCommitAnalysis(request, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

