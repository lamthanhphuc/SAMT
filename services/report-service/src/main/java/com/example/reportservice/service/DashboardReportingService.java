package com.example.reportservice.service;

import com.example.reportservice.dto.response.ContributionSummaryResponse;
import com.example.reportservice.dto.response.GithubStatsResponse;
import com.example.reportservice.dto.response.GroupProgressResponse;
import com.example.reportservice.dto.response.LecturerOverviewResponse;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.RecentActivityResponse;
import com.example.reportservice.dto.response.StudentTaskResponse;

import java.time.LocalDate;
import java.util.List;

public interface DashboardReportingService {

    LecturerOverviewResponse getLecturerOverview(Long actorId, List<String> roles, Long semesterId);

    GroupProgressResponse getGroupProgress(Long actorId, List<String> roles, Long groupId, LocalDate from, LocalDate to);

    PageResponse<RecentActivityResponse> getRecentActivities(Long actorId, List<String> roles, Long groupId, String source, int page, int size);

    PageResponse<StudentTaskResponse> getStudentTasks(Long studentId, Long semesterId, String status, int page, int size);

    GithubStatsResponse getStudentGithubStats(Long studentId, Long groupId, LocalDate from, LocalDate to);

    ContributionSummaryResponse getContributionSummary(Long studentId, Long groupId, LocalDate from, LocalDate to);
}