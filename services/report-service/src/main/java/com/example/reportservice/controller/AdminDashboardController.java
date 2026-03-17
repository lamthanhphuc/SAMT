package com.example.reportservice.controller;

import com.example.reportservice.dto.response.AdminOverviewResponse;
import com.example.reportservice.service.DashboardReportingService;
import com.example.reportservice.web.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports/admin")
@RequiredArgsConstructor
@Validated
@Tag(name = "report-admin-dashboard", description = "Admin dashboard reporting APIs")
public class AdminDashboardController {

    private final DashboardReportingService dashboardReportingService;

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get admin dashboard overview")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Overview retrieved", content = @Content(schema = @Schema(implementation = AdminOverviewResponse.class))),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public AdminOverviewResponse getOverview(@RequestParam(required = false) String semesterId) {
        Long parsedSemesterId = parseOptionalPositiveLong(semesterId, "semesterId");
        return dashboardReportingService.getAdminOverview(parsedSemesterId);
    }

    private Long parseOptionalPositiveLong(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Invalid value for parameter '" + fieldName + "'");
        }
        try {
            long parsed = Long.parseLong(trimmed);
            if (parsed <= 0) {
                throw new BadRequestException("Invalid value for parameter '" + fieldName + "'");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new BadRequestException("Invalid value for parameter '" + fieldName + "'");
        }
    }
}
