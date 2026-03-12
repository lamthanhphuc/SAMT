package com.example.reportservice.controller;

import com.example.reportservice.dto.response.ContributionSummaryResponse;
import com.example.reportservice.dto.response.GithubStatsResponse;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.StudentTaskResponse;
import com.example.reportservice.service.DashboardReportingService;
import com.example.reportservice.support.AuthenticatedRequestSupport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reports/students/me")
@RequiredArgsConstructor
@Validated
@Tag(name = "report-student-dashboard", description = "Student dashboard reporting APIs")
public class StudentDashboardController {

    private final DashboardReportingService dashboardReportingService;
    private final AuthenticatedRequestSupport requestSupport;

    @GetMapping("/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get current student tasks")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Tasks retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class), examples = @ExampleObject(value = "{\"success\":true,\"status\":200,\"path\":\"/api/reports/students/me/tasks\",\"data\":{\"content\":[{\"taskId\":\"10001\",\"source\":\"JIRA\",\"key\":\"SAMT-12\",\"title\":\"Implement dashboard API\",\"status\":\"IN_PROGRESS\",\"priority\":\"High\",\"groupId\":3,\"groupName\":\"SE1848-G1\",\"assignee\":\"Nguyen Van A\",\"updatedAt\":\"2026-03-12T08:00:00Z\",\"url\":\"https://jira.example.com/browse/SAMT-12\"}],\"page\":0,\"size\":20,\"totalElements\":1,\"totalPages\":1},\"timestamp\":\"2026-03-12T08:30:01Z\"}"))),
        @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    public PageResponse<StudentTaskResponse> getTasks(@RequestParam(required = false) Long semesterId,
                                                      @RequestParam(required = false) String status,
                                                      @RequestParam(defaultValue = "ALL") String source,
                                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                      Authentication authentication) {
        Long studentId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getStudentTasks(studentId, semesterId, status, page, size);
    }

    @GetMapping("/github-stats")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get current student GitHub stats")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "GitHub stats retrieved", content = @Content(schema = @Schema(implementation = GithubStatsResponse.class))),
        @ApiResponse(responseCode = "403", description = "Student does not belong to this group")
    })
    public GithubStatsResponse getGithubStats(@RequestParam Long groupId,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                              Authentication authentication) {
        Long studentId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getStudentGithubStats(studentId, groupId, from, to);
    }

    @GetMapping("/contribution-summary")
    @PreAuthorize("hasRole('STUDENT')")
    @Operation(summary = "Get current student contribution summary")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Contribution summary retrieved", content = @Content(schema = @Schema(implementation = ContributionSummaryResponse.class))),
        @ApiResponse(responseCode = "403", description = "Student does not belong to this group")
    })
    public ContributionSummaryResponse getContributionSummary(@RequestParam Long groupId,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                              @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                              Authentication authentication) {
        Long studentId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getContributionSummary(studentId, groupId, from, to);
    }
}