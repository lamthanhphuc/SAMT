package com.example.reportservice.service.impl;

import com.example.reportservice.client.ProjectConfigClient;
import com.example.reportservice.client.ProjectConfigClient.ProjectConfigSnapshot;
import com.example.reportservice.client.UserGroupClient;
import com.example.reportservice.client.UserGroupClient.GroupDetail;
import com.example.reportservice.client.UserGroupClient.GroupSummary;
import com.example.reportservice.client.UserGroupClient.UserGroupMembership;
import com.example.reportservice.client.UserGroupClient.UserProfile;
import com.example.reportservice.dto.response.ContributionSummaryResponse;
import com.example.reportservice.dto.response.GithubStatsResponse;
import com.example.reportservice.dto.response.GroupProgressResponse;
import com.example.reportservice.dto.response.LecturerOverviewResponse;
import com.example.reportservice.dto.response.AdminOverviewResponse;
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.RecentActivityResponse;
import com.example.reportservice.dto.response.StudentTaskResponse;
import com.example.reportservice.entity.GithubCommit;
import com.example.reportservice.entity.JiraIssue;
import com.example.reportservice.entity.SyncJob;
import com.example.reportservice.entity.UnifiedActivity;
import com.example.reportservice.entity.UnifiedActivity.ActivitySource;
import com.example.reportservice.entity.UnifiedActivity.ActivityType;
import com.example.reportservice.repository.GithubCommitRepository;
import com.example.reportservice.repository.JiraIssueRepository;
import com.example.reportservice.repository.SyncJobRepository;
import com.example.reportservice.repository.UnifiedActivityRepository;
import com.example.reportservice.web.UpstreamServiceException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

@Service
@RequiredArgsConstructor
public class DashboardReportingServiceImpl implements com.example.reportservice.service.DashboardReportingService {

    private static final List<String> COMPLETED_STATUSES = List.of("done", "closed", "resolved", "completed", "merged");
    private static final String HEALTH_NO_DATA = "NO_DATA";
    private static final String HEALTH_HEALTHY = "HEALTHY";
    private static final String HEALTH_DEGRADED = "DEGRADED";
    private static final String HEALTH_ISSUE = "ISSUE";
    private static final String SERVER_ONLINE = "ONLINE";
    private static final String VERIFIED_STATE = "VERIFIED";

    private final UserGroupClient userGroupClient;
    private final ProjectConfigClient projectConfigClient;
    private final JiraIssueRepository jiraIssueRepository;
    private final UnifiedActivityRepository unifiedActivityRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final SyncJobRepository syncJobRepository;

    @Override
    public AdminOverviewResponse getAdminOverview(Long semesterId) {
        List<GroupSummary> groups = userGroupClient.listGroups(null, semesterId);
        long totalUsers = userGroupClient.countUsers();

        Map<Long, ProjectConfigSnapshot> configsByGroupId = resolveConfigsByGroupId(
            groups.stream().map(GroupSummary::groupId).toList()
        );
        List<ProjectConfigSnapshot> configs = new ArrayList<>(configsByGroupId.values());
        List<UUID> configIds = configs.stream().map(ProjectConfigSnapshot::configId).toList();

        long activeProjects = configs.stream()
            .filter(config -> VERIFIED_STATE.equalsIgnoreCase(config.state()))
            .count();

        long pendingSyncJobs = configIds.isEmpty()
            ? 0
            : syncJobRepository.countByProjectConfigIdInAndStatus(configIds, "PENDING");

        String jiraApiHealth = resolveSyncHealth(configIds, "JIRA_ISSUES");
        String githubApiHealth = resolveSyncHealth(configIds, "GITHUB_COMMITS");

        return AdminOverviewResponse.builder()
            .semesterId(semesterId)
            .totalUsers(totalUsers)
            .totalGroups(groups.size())
            .activeProjects(activeProjects)
            .pendingSyncJobs(pendingSyncJobs)
            .jiraApiHealth(jiraApiHealth)
            .githubApiHealth(githubApiHealth)
            .serverHealth(SERVER_ONLINE)
            .lastCalculatedAt(LocalDateTime.now())
            .build();
    }

    @Override
    public LecturerOverviewResponse getLecturerOverview(Long actorId, List<String> roles, Long semesterId) {
        boolean isAdmin = roles.contains("ADMIN");
        List<GroupSummary> groups = userGroupClient.listGroups(isAdmin ? null : actorId, semesterId);
        List<UUID> configIds = resolveConfigIds(groups.stream().map(GroupSummary::groupId).toList());

        return LecturerOverviewResponse.builder()
            .lecturerId(actorId)
            .semesterId(semesterId)
            .groupCount(groups.size())
            .studentCount(groups.stream().mapToLong(GroupSummary::memberCount).sum())
            .taskCount(configIds.isEmpty() ? 0 : jiraIssueRepository.findByProjectConfigIdIn(configIds).size())
            .completedTaskCount(configIds.isEmpty() ? 0 : jiraIssueRepository.countCompletedIssues(configIds, COMPLETED_STATUSES))
            .githubCommitCount(configIds.isEmpty() ? 0 : githubCommitRepository.countByProjectConfigIdInAndDeletedAtIsNull(configIds))
            .githubPrCount(configIds.isEmpty() ? 0 : unifiedActivityRepository.countByProjectConfigIdInAndSourceAndActivityTypeAndDeletedAtIsNull(configIds, ActivitySource.GITHUB, ActivityType.PULL_REQUEST))
            .lastSyncAt(configIds.isEmpty() ? null : syncJobRepository.findLastCompletedAt(configIds))
            .build();
    }

    @Override
    public GroupProgressResponse getGroupProgress(Long actorId, List<String> roles, Long groupId, LocalDate from, LocalDate to) {
        GroupDetail group = userGroupClient.getGroup(groupId);
        authorizeLecturerScope(actorId, roles, group);

        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return emptyProgress(group.groupId(), group.groupName());
        }

        List<JiraIssue> issues = filterByDate(jiraIssueRepository.findByProjectConfigId(configOpt.get().configId()), from, to);
        if (issues.isEmpty()) {
            return emptyProgress(group.groupId(), group.groupName());
        }

        Map<String, Long> taskByType = new LinkedHashMap<>();
        Map<String, Long> taskByStatus = new LinkedHashMap<>();
        long todo = 0;
        long inProgress = 0;
        long done = 0;

        for (JiraIssue issue : issues) {
            taskByType.merge(defaultLabel(issue.getIssueType()), 1L, Long::sum);
            taskByStatus.merge(defaultLabel(issue.getStatus()), 1L, Long::sum);

            switch (normalizeTaskStatus(issue.getStatus())) {
                case "DONE" -> done++;
                case "IN_PROGRESS" -> inProgress++;
                default -> todo++;
            }
        }

        double completionRate = issues.isEmpty() ? 0.0 : ((double) done * 100.0) / issues.size();
        return GroupProgressResponse.builder()
            .groupId(group.groupId())
            .groupName(group.groupName())
            .completionRate(Math.round(completionRate * 100.0) / 100.0)
            .todoCount(todo)
            .inProgressCount(inProgress)
            .doneCount(done)
            .taskByType(taskByType)
            .taskByStatus(taskByStatus)
            .build();
    }

    @Override
    public PageResponse<RecentActivityResponse> getRecentActivities(Long actorId, List<String> roles, Long groupId, String source, int page, int size) {
        GroupDetail group = userGroupClient.getGroup(groupId);
        authorizeLecturerScope(actorId, roles, group);

        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return PageResponse.<RecentActivityResponse>builder()
                .content(List.of())
                .page(page)
                .size(size)
                .totalElements(0)
                .totalPages(0)
                .build();
        }

        ActivitySource sourceFilter = parseSource(source);
        var activityPage = unifiedActivityRepository.findRecentActivities(configOpt.get().configId(), sourceFilter, PageRequest.of(page, size));
        List<RecentActivityResponse> content = activityPage.getContent().stream()
            .map(activity -> mapRecentActivity(activity, configOpt.get()))
            .toList();

        return PageResponse.<RecentActivityResponse>builder()
            .content(content)
            .page(page)
            .size(size)
            .totalElements(activityPage.getTotalElements())
            .totalPages(activityPage.getTotalPages())
            .build();
    }

    @Override
    public PageResponse<StudentTaskResponse> getStudentTasks(Long studentId, Long semesterId, String status, int page, int size) {
        UserProfile profile;
        List<UserGroupMembership> memberships;
        try {
            profile = userGroupClient.getUserProfile(studentId);
            memberships = userGroupClient.getUserGroups(studentId).stream()
                .filter(group -> semesterId == null || semesterId.equals(group.semesterId()))
                .toList();
        } catch (UpstreamServiceException ex) {
            return emptyPage(page, size);
        }

        if (memberships.isEmpty()) {
            return emptyPage(page, size);
        }

        Map<Long, ProjectConfigSnapshot> configsByGroupId = resolveConfigsByGroupId(memberships.stream().map(UserGroupMembership::groupId).toList());
        List<UUID> configIds = configsByGroupId.values().stream().map(ProjectConfigSnapshot::configId).toList();
        if (configIds.isEmpty()) {
            return emptyPage(page, size);
        }

        List<JiraIssue> filtered = jiraIssueRepository.findByProjectConfigIdInAndAssigneeEmailIgnoreCase(configIds, profile.email()).stream()
            .filter(issue -> status == null || normalizeTaskStatus(issue.getStatus()).equals(status))
            .sorted(Comparator.comparing(JiraIssue::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<StudentTaskResponse> content = filtered.subList(fromIndex, toIndex).stream()
            .map(issue -> mapStudentTask(issue, memberships, configsByGroupId, profile))
            .toList();

        return PageResponse.<StudentTaskResponse>builder()
            .content(content)
            .page(page)
            .size(size)
            .totalElements(filtered.size())
            .totalPages(filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / size))
            .build();
    }

    @Override
    public GithubStatsResponse getStudentGithubStats(Long studentId, Long groupId, LocalDate from, LocalDate to) {
        UserProfile profile = userGroupClient.getUserProfile(studentId);
        assertStudentInGroup(studentId, groupId);

        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return GithubStatsResponse.builder().reviewCount(0).build();
        }

        List<UUID> configIds = List.of(configOpt.get().configId());
        LocalDateTime fromDate = toFromDateTime(from);
        LocalDateTime toDate = toToDateTime(to);

        long commitCount = githubCommitRepository.countByAuthorAndProjectConfigWithinRange(
            configIds,
            profile.email(),
            fromDate,
            toDate
        );

        List<UnifiedActivity> pullRequests = unifiedActivityRepository
            .findByProjectConfigIdInAndActivityTypeAndAuthorEmailIgnoreCaseAndDeletedAtIsNull(configIds, ActivityType.PULL_REQUEST, profile.email()).stream()
            .filter(activity -> activity.getActivityType() == ActivityType.PULL_REQUEST)
            .filter(activity -> withinRange(activity.getCreatedAt(), from, to))
            .toList();

        LocalDateTime lastCommitAt = githubCommitRepository.findLastCommitAtWithinRange(
            configIds,
            profile.email(),
            fromDate,
            toDate
        );
        long activeDays = githubCommitRepository.countActiveCommitDaysWithinRange(
            configIds,
            profile.email(),
            fromDate,
            toDate
        );

        return GithubStatsResponse.builder()
            .commitCount(commitCount)
            .prCount(pullRequests.size())
            .mergedPrCount(pullRequests.stream().filter(activity -> "merged".equalsIgnoreCase(activity.getStatus())).count())
            .reviewCount(0)
            .activeDays(activeDays)
            .lastCommitAt(lastCommitAt)
            .build();
    }

    @Override
    public ContributionSummaryResponse getContributionSummary(Long studentId, Long groupId, LocalDate from, LocalDate to) {
        UserProfile profile = userGroupClient.getUserProfile(studentId);
        assertStudentInGroup(studentId, groupId);

        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return ContributionSummaryResponse.builder()
                .studentId(studentId)
                .groupId(groupId)
                .recentHighlights(List.of())
                .build();
        }

        List<UUID> configIds = List.of(configOpt.get().configId());
        List<JiraIssue> tasks = filterByDate(
            jiraIssueRepository.findByProjectConfigIdInAndAssigneeEmailIgnoreCase(configIds, profile.email()),
            from,
            to
        );
        long completedTaskCount = tasks.stream().filter(issue -> "DONE".equals(normalizeTaskStatus(issue.getStatus()))).count();

        long githubCommitCount = githubCommitRepository.countByAuthorAndProjectConfigWithinRange(
            configIds,
            profile.email(),
            toFromDateTime(from),
            toToDateTime(to)
        );

        List<UnifiedActivity> pullRequests = unifiedActivityRepository
            .findByProjectConfigIdInAndActivityTypeAndAuthorEmailIgnoreCaseAndDeletedAtIsNull(configIds, ActivityType.PULL_REQUEST, profile.email()).stream()
            .filter(activity -> withinRange(activity.getCreatedAt(), from, to))
            .toList();

        List<String> highlights = unifiedActivityRepository.findRecentHighlights(configIds, profile.email(), PageRequest.of(0, 3)).stream()
            .filter(activity -> withinRange(activity.getUpdatedAt(), from, to))
            .map(UnifiedActivity::getTitle)
            .toList();

        long contributionScore = completedTaskCount * 5 + githubCommitCount * 2 + pullRequests.size() * 4;

        return ContributionSummaryResponse.builder()
            .studentId(studentId)
            .groupId(groupId)
            .taskCount(tasks.size())
            .completedTaskCount(completedTaskCount)
            .githubCommitCount(githubCommitCount)
            .githubPrCount(pullRequests.size())
            .contributionScore(contributionScore)
            .recentHighlights(highlights)
            .build();
    }

    private void authorizeLecturerScope(Long actorId, List<String> roles, GroupDetail group) {
        if (roles.contains("ADMIN")) {
            return;
        }
        if (!roles.contains("LECTURER") || !actorId.equals(group.lecturerId())) {
            throw new AccessDeniedException("Lecturer can only access supervised groups");
        }
    }

    private void assertStudentInGroup(Long studentId, Long groupId) {
        boolean member = userGroupClient.getUserGroups(studentId).stream()
            .anyMatch(group -> group.groupId().equals(groupId));
        if (!member) {
            throw new AccessDeniedException("Student can only access own groups");
        }
    }

    private Map<Long, ProjectConfigSnapshot> resolveConfigsByGroupId(List<Long> groupIds) {
        Map<Long, ProjectConfigSnapshot> result = new LinkedHashMap<>();
        for (Long groupId : groupIds) {
            projectConfigClient.getConfigByGroupId(groupId).ifPresent(config -> result.put(groupId, config));
        }
        return result;
    }

    private List<UUID> resolveConfigIds(List<Long> groupIds) {
        return new ArrayList<>(resolveConfigsByGroupId(groupIds).values().stream().map(ProjectConfigSnapshot::configId).toList());
    }

    private String resolveSyncHealth(List<UUID> configIds, String jobType) {
        if (configIds.isEmpty()) {
            return HEALTH_NO_DATA;
        }
        return syncJobRepository.findFirstByProjectConfigIdInAndJobTypeOrderByCreatedAtDesc(configIds, jobType)
            .map(SyncJob::getStatus)
            .map(this::mapHealthByStatus)
            .orElse(HEALTH_NO_DATA);
    }

    private String mapHealthByStatus(String status) {
        if (status == null || status.isBlank()) {
            return HEALTH_NO_DATA;
        }
        return switch (status.toUpperCase()) {
            case "COMPLETED" -> HEALTH_HEALTHY;
            case "FAILED" -> HEALTH_ISSUE;
            case "PENDING", "RUNNING" -> HEALTH_DEGRADED;
            default -> HEALTH_NO_DATA;
        };
    }

    private GroupProgressResponse emptyProgress(Long groupId, String groupName) {
        return GroupProgressResponse.builder()
            .groupId(groupId)
            .groupName(groupName)
            .completionRate(0.0)
            .todoCount(0)
            .inProgressCount(0)
            .doneCount(0)
            .taskByType(Map.of())
            .taskByStatus(Map.of())
            .build();
    }

    private PageResponse<StudentTaskResponse> emptyPage(int page, int size) {
        return PageResponse.<StudentTaskResponse>builder()
            .content(List.of())
            .page(page)
            .size(size)
            .totalElements(0)
            .totalPages(0)
            .build();
    }

    private ActivitySource parseSource(String source) {
        if (source == null || source.isBlank() || "ALL".equalsIgnoreCase(source)) {
            return null;
        }
        return ActivitySource.valueOf(source.toUpperCase());
    }

    private RecentActivityResponse mapRecentActivity(UnifiedActivity activity, ProjectConfigSnapshot config) {
        return RecentActivityResponse.builder()
            .activityId(activity.getId())
            .source(activity.getSource().name())
            .type(activity.getActivityType().name())
            .title(activity.getTitle())
            .author(defaultAuthor(activity))
            .occurredAt(activity.getUpdatedAt())
            .externalId(activity.getExternalId())
            .url(buildActivityUrl(activity, config))
            .build();
    }

    private StudentTaskResponse mapStudentTask(JiraIssue issue,
                                               List<UserGroupMembership> memberships,
                                               Map<Long, ProjectConfigSnapshot> configsByGroupId,
                                               UserProfile profile) {
        Long groupId = configsByGroupId.entrySet().stream()
            .filter(entry -> entry.getValue().configId().equals(issue.getProjectConfigId()))
            .map(Map.Entry::getKey)
            .findFirst()
            .orElse(null);
        UserGroupMembership group = memberships.stream()
            .filter(item -> item.groupId().equals(groupId))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Group membership not found"));
        ProjectConfigSnapshot config = configsByGroupId.get(groupId);
        return StudentTaskResponse.builder()
            .taskId(issue.getIssueId())
            .source("JIRA")
            .key(issue.getIssueKey())
            .title(issue.getSummary())
            .status(normalizeTaskStatus(issue.getStatus()))
            .priority(issue.getPriority())
            .groupId(groupId)
            .groupName(group.groupName())
            .assignee(profile.fullName())
            .updatedAt(issue.getUpdatedAt())
            .url(config != null && config.jiraHostUrl() != null ? config.jiraHostUrl() + "/browse/" + issue.getIssueKey() : null)
            .build();
    }

    private List<JiraIssue> filterByDate(List<JiraIssue> issues, LocalDate from, LocalDate to) {
        Predicate<JiraIssue> predicate = issue -> {
            LocalDate issueDate = issue.getUpdatedAt() != null ? issue.getUpdatedAt().toLocalDate() : null;
            if (issueDate == null) {
                return from == null && to == null;
            }
            return (from == null || !issueDate.isBefore(from))
                && (to == null || !issueDate.isAfter(to));
        };
        return issues.stream().filter(predicate).toList();
    }

    private boolean withinRange(LocalDateTime value, LocalDate from, LocalDate to) {
        if (value == null) {
            return from == null && to == null;
        }
        LocalDate date = value.toLocalDate();
        return (from == null || !date.isBefore(from))
            && (to == null || !date.isAfter(to));
    }

    private String normalizeTaskStatus(String rawStatus) {
        String normalized = defaultLabel(rawStatus).toLowerCase();
        if (COMPLETED_STATUSES.contains(normalized)) {
            return "DONE";
        }
        if (normalized.contains("progress") || normalized.contains("doing")) {
            return "IN_PROGRESS";
        }
        return "TODO";
    }

    private LocalDateTime toFromDateTime(LocalDate from) {
        return from == null ? null : from.atStartOfDay();
    }

    private LocalDateTime toToDateTime(LocalDate to) {
        return to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
    }

    private String buildActivityUrl(UnifiedActivity activity, ProjectConfigSnapshot config) {
        if (activity.getSource() == ActivitySource.JIRA && config.jiraHostUrl() != null) {
            return config.jiraHostUrl() + "/browse/" + activity.getExternalId();
        }
        if (activity.getSource() == ActivitySource.GITHUB && config.githubRepoUrl() != null) {
            if (activity.getActivityType() == ActivityType.PULL_REQUEST) {
                return config.githubRepoUrl() + "/pull/" + activity.getExternalId();
            }
            if (activity.getActivityType() == ActivityType.COMMIT) {
                return config.githubRepoUrl() + "/commit/" + activity.getExternalId();
            }
        }
        return null;
    }

    private String defaultAuthor(UnifiedActivity activity) {
        if (activity.getAuthorName() != null && !activity.getAuthorName().isBlank()) {
            return activity.getAuthorName();
        }
        return activity.getAuthorEmail();
    }

    private String defaultLabel(String value) {
        return value == null || value.isBlank() ? "UNKNOWN" : value;
    }
}