package com.example.reportservice.controller;

import com.example.reportservice.dto.request.TaskAssigneeUpdateRequest;
import com.example.reportservice.dto.request.TaskStatusUpdateRequest;
import com.example.reportservice.dto.response.GroupProgressResponse;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.StudentTaskResponse;
import com.example.reportservice.dto.response.TeamCommitSummaryResponse;
import com.example.reportservice.service.DashboardReportingService;
import com.example.reportservice.support.AuthenticatedRequestSupport;
import com.example.reportservice.web.BadRequestException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/reports/leader")
@RequiredArgsConstructor
@Validated
public class TeamLeaderDashboardController {

    private final DashboardReportingService dashboardReportingService;
    private final AuthenticatedRequestSupport requestSupport;

    @GetMapping("/groups/{groupId}/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<StudentTaskResponse> getGroupTasks(@PathVariable @Positive Long groupId,
                                                           @RequestParam(required = false) String status,
                                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                                           @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                           Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getLeaderGroupTasks(actorId, groupId, status, page, size);
    }

    @PatchMapping("/groups/{groupId}/tasks/{taskId}/assignee")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentTaskResponse assignTask(@PathVariable @Positive Long groupId,
                                          @PathVariable String taskId,
                                          @Valid @RequestBody TaskAssigneeUpdateRequest request,
                                          Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.assignTaskToMember(actorId, groupId, taskId, request.assigneeUserId());
    }

    @PatchMapping("/groups/{groupId}/tasks/{taskId}/status")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentTaskResponse updateTaskStatus(@PathVariable @Positive Long groupId,
                                                @PathVariable String taskId,
                                                @Valid @RequestBody TaskStatusUpdateRequest request,
                                                Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.updateTaskStatusByLeader(actorId, groupId, taskId, request.status());
    }

    @GetMapping("/groups/{groupId}/progress")
    @PreAuthorize("hasRole('STUDENT')")
    public GroupProgressResponse getGroupProgress(@PathVariable @Positive Long groupId,
                                                  @RequestParam(required = false) String from,
                                                  @RequestParam(required = false) String to,
                                                  Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getLeaderGroupProgress(actorId, groupId, parseOptionalDate(from, "from"), parseOptionalDate(to, "to"));
    }

    @GetMapping("/groups/{groupId}/commit-summary")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamCommitSummaryResponse getTeamCommitSummary(@PathVariable @Positive Long groupId,
                                                          @RequestParam(required = false) String from,
                                                          @RequestParam(required = false) String to,
                                                          Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getLeaderTeamCommitSummary(actorId, groupId, parseOptionalDate(from, "from"), parseOptionalDate(to, "to"));
    }

    private LocalDate parseOptionalDate(String value, String fieldName) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw new BadRequestException("Invalid value for parameter '" + fieldName + "'");
        }
        try {
            return LocalDate.parse(trimmed);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("Invalid value for parameter '" + fieldName + "'");
        }
    }
}
