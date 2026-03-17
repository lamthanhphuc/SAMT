package com.example.reportservice.controller;

import com.example.reportservice.dto.request.TaskStatusUpdateRequest;
import com.example.reportservice.dto.response.GithubStatsResponse;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.StudentTaskResponse;
import com.example.reportservice.dto.response.TeamMemberTaskStatsResponse;
import com.example.reportservice.service.DashboardReportingService;
import com.example.reportservice.support.AuthenticatedRequestSupport;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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

@RestController
@RequestMapping("/api/reports/members/me")
@RequiredArgsConstructor
@Validated
public class TeamMemberDashboardController {

    private final DashboardReportingService dashboardReportingService;
    private final AuthenticatedRequestSupport requestSupport;

    @GetMapping("/tasks")
    @PreAuthorize("hasRole('STUDENT')")
    public PageResponse<StudentTaskResponse> getMyTasks(@RequestParam @Positive Long groupId,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(defaultValue = "0") @Min(0) int page,
                                                        @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
                                                        Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getMemberTasks(actorId, groupId, status, page, size);
    }

    @PatchMapping("/tasks/{taskId}/status")
    @PreAuthorize("hasRole('STUDENT')")
    public StudentTaskResponse updateMyTaskStatus(@PathVariable String taskId,
                                                  @RequestParam @Positive Long groupId,
                                                  @Valid @RequestBody TaskStatusUpdateRequest request,
                                                  Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.updateTaskStatusByMember(actorId, groupId, taskId, request.status());
    }

    @GetMapping("/task-stats")
    @PreAuthorize("hasRole('STUDENT')")
    public TeamMemberTaskStatsResponse getMyTaskStats(@RequestParam @Positive Long groupId,
                                                      Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getMemberTaskStats(actorId, groupId);
    }

    @GetMapping("/commit-stats")
    @PreAuthorize("hasRole('STUDENT')")
    public GithubStatsResponse getMyCommitStats(@RequestParam @Positive Long groupId,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                                Authentication authentication) {
        Long actorId = requestSupport.requireUserId(authentication);
        return dashboardReportingService.getStudentGithubStats(actorId, groupId, from, to);
    }
}
