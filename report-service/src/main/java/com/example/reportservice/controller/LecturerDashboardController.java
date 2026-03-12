package com.example.reportservice.controller;

import com.example.reportservice.dto.response.GroupProgressResponse;
import com.example.reportservice.dto.response.LecturerOverviewResponse;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.RecentActivityResponse;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/lecturer")
@RequiredArgsConstructor
@Validated
@Tag(name = "report-lecturer-dashboard", description = "Lecturer dashboard reporting APIs")
public class LecturerDashboardController {

    private final DashboardReportingService dashboardReportingService;
    private final AuthenticatedRequestSupport requestSupport;

    @GetMapping("/overview")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    @Operation(summary = "Get lecturer dashboard overview")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Overview retrieved", content = @Content(schema = @Schema(implementation = LecturerOverviewResponse.class), examples = @ExampleObject(value = "{\"success\":true,\"status\":200,\"path\":\"/lecturer/overview\",\"data\":{\"lecturerId\":12,\"semesterId\":2,\"groupCount\":3,\"studentCount\":15,\"taskCount\":42,\"completedTaskCount\":30,\"githubCommitCount\":128,\"githubPrCount\":9,\"lastSyncAt\":\"2026-03-12T08:30:00\"},\"timestamp\":\"2026-03-12T08:30:01Z\"}"))),
        @ApiResponse(responseCode = "403", description = "Forbidden"),
        @ApiResponse(responseCode = "503", description = "Dependency unavailable")
    })
    public LecturerOverviewResponse getOverview(@RequestParam(required = false) Long semesterId,
                                                Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getLecturerOverview(actorId, requestSupport.roles(authentication), semesterId);
    }

    @GetMapping("/groups/{groupId}/progress")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    @Operation(summary = "Get task progress for a group")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Progress retrieved", content = @Content(schema = @Schema(implementation = GroupProgressResponse.class))),
        @ApiResponse(responseCode = "403", description = "Lecturer does not supervise this group"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public GroupProgressResponse getGroupProgress(@PathVariable Long groupId,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                  Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getGroupProgress(actorId, requestSupport.roles(authentication), groupId, from, to);
    }

    @GetMapping("/groups/{groupId}/recent-activities")
    @PreAuthorize("hasAnyRole('LECTURER','ADMIN')")
    @Operation(summary = "Get recent normalized activities for a group")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Activities retrieved", content = @Content(schema = @Schema(implementation = PageResponse.class))),
        @ApiResponse(responseCode = "403", description = "Lecturer does not supervise this group"),
        @ApiResponse(responseCode = "404", description = "Group not found")
    })
    public PageResponse<RecentActivityResponse> getRecentActivities(
        @PathVariable Long groupId,
        @RequestParam(defaultValue = "0") @Min(0) int page,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
        @RequestParam(defaultValue = "ALL") String source,
        Authentication authentication
    ) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getRecentActivities(actorId, requestSupport.roles(authentication), groupId, source, page, size);
    }
}