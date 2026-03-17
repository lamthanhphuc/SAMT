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
import com.example.reportservice.dto.response.PageResponse;
import com.example.reportservice.dto.response.RecentActivityResponse;
import com.example.reportservice.dto.response.StudentTaskResponse;
import com.example.reportservice.dto.response.TeamCommitSummaryResponse;
import com.example.reportservice.dto.response.TeamMemberTaskStatsResponse;
import com.example.reportservice.entity.GithubCommit;
import com.example.reportservice.entity.JiraIssue;
import com.example.reportservice.entity.UnifiedActivity;
import com.example.reportservice.entity.UnifiedActivity.ActivitySource;
import com.example.reportservice.entity.UnifiedActivity.ActivityType;
import com.example.reportservice.grpc.GithubCommitResponse;
import com.example.reportservice.grpc.IssueResponse;
import com.example.reportservice.grpc.SyncGrpcClient;
import com.example.reportservice.grpc.UnifiedActivityResponse;
import com.example.reportservice.repository.GithubCommitRepository;
import com.example.reportservice.repository.JiraIssueRepository;
import com.example.reportservice.repository.SyncJobRepository;
import com.example.reportservice.repository.UnifiedActivityRepository;
import com.example.reportservice.service.JiraService;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardReportingServiceImpl implements com.example.reportservice.service.DashboardReportingService {

    private static final List<String> COMPLETED_STATUSES = List.of("done", "closed", "resolved", "completed", "merged", "approved");

    private final UserGroupClient userGroupClient;
    private final ProjectConfigClient projectConfigClient;
    private final JiraIssueRepository jiraIssueRepository;
    private final UnifiedActivityRepository unifiedActivityRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final SyncJobRepository syncJobRepository;
    private final SyncGrpcClient syncGrpcClient;
    private final JiraService jiraService;

    @Override
    public LecturerOverviewResponse getLecturerOverview(Long actorId, List<String> roles, Long semesterId) {
        boolean isAdmin = roles.contains("ADMIN");
        List<GroupSummary> groups = userGroupClient.listGroups(isAdmin ? null : actorId, semesterId);
        List<UUID> configIds = resolveConfigIds(groups.stream().map(GroupSummary::groupId).toList());

        long taskCount = 0;
        long completedTaskCount = 0;
        long githubCommitCount = 0;
        long githubPrCount = 0;

        for (UUID configId : configIds) {
            List<JiraIssue> mergedIssues = mergeIssues(
                jiraIssueRepository.findByProjectConfigId(configId),
                toJiraIssues(syncGrpcClient.getIssues(configId), configId)
            );
            taskCount += mergedIssues.size();
            completedTaskCount += mergedIssues.stream()
                .filter(issue -> "DONE".equals(normalizeTaskStatus(issue.getStatus())))
                .count();

            List<GithubCommit> mergedCommits = mergeGithubCommits(
                githubCommitRepository.findByProjectConfigIdsWithinRange(List.of(configId), null, null),
                toGithubCommits(syncGrpcClient.getGithubCommits(configId), configId)
            );
            githubCommitCount += mergedCommits.size();

            List<UnifiedActivity> localGithubActivities = unifiedActivityRepository
                .findRecentActivities(configId, ActivitySource.GITHUB, PageRequest.of(0, 5000))
                .getContent();
            List<UnifiedActivity> remoteGithubActivities = toUnifiedActivities(
                syncGrpcClient.getUnifiedActivities(configId),
                configId
            ).stream()
                .filter(item -> item.getSource() == ActivitySource.GITHUB)
                .toList();

            List<UnifiedActivity> mergedGithubActivities = mergeActivities(localGithubActivities, remoteGithubActivities);
            githubPrCount += mergedGithubActivities.stream()
                .filter(activity -> activity.getActivityType() == ActivityType.PULL_REQUEST)
                .count();
        }

        return LecturerOverviewResponse.builder()
            .lecturerId(actorId)
            .semesterId(semesterId)
            .groupCount(groups.size())
            .studentCount(groups.stream().mapToLong(GroupSummary::memberCount).sum())
            .taskCount(taskCount)
            .completedTaskCount(completedTaskCount)
            .githubCommitCount(githubCommitCount)
            .githubPrCount(githubPrCount)
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

        UUID configId = configOpt.get().configId();
        List<JiraIssue> localIssues = jiraIssueRepository.findByProjectConfigId(configId);
        List<JiraIssue> remoteIssues = toJiraIssues(syncGrpcClient.getIssues(configId), configId);
        List<JiraIssue> issues = filterByDate(mergeIssues(localIssues, remoteIssues), from, to);
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
        UUID configId = configOpt.get().configId();

        var activityPage = unifiedActivityRepository.findRecentActivities(configId, sourceFilter, PageRequest.of(page, size));
        if (activityPage.getTotalElements() > 0) {
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

        List<UnifiedActivity> remoteActivities = toUnifiedActivities(syncGrpcClient.getUnifiedActivities(configId), configId).stream()
            .filter(activity -> sourceFilter == null || activity.getSource() == sourceFilter)
            .sorted(Comparator.comparing(UnifiedActivity::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        int fromIndex = Math.min(page * size, remoteActivities.size());
        int toIndex = Math.min(fromIndex + size, remoteActivities.size());
        List<RecentActivityResponse> content = remoteActivities.subList(fromIndex, toIndex).stream()
            .map(activity -> mapRecentActivity(activity, configOpt.get()))
            .toList();

        return PageResponse.<RecentActivityResponse>builder()
            .content(content)
            .page(page)
            .size(size)
            .totalElements(remoteActivities.size())
            .totalPages(remoteActivities.isEmpty() ? 0 : (int) Math.ceil((double) remoteActivities.size() / size))
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

        if (filtered.isEmpty()) {
            filtered = configIds.stream()
                .flatMap(configId -> toJiraIssues(syncGrpcClient.getIssues(configId), configId).stream())
                .filter(issue -> equalsIgnoreCase(issue.getAssigneeEmail(), profile.email()))
                .filter(issue -> status == null || normalizeTaskStatus(issue.getStatus()).equals(status))
                .sorted(Comparator.comparing(JiraIssue::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        }

        if (filtered.isEmpty()) {
            List<UUID> leaderConfigIds = memberships.stream()
                .filter(item -> "LEADER".equalsIgnoreCase(item.role()))
                .map(item -> configsByGroupId.get(item.groupId()))
                .filter(item -> item != null && item.configId() != null)
                .map(ProjectConfigSnapshot::configId)
                .distinct()
                .toList();

            if (!leaderConfigIds.isEmpty()) {
                filtered = jiraIssueRepository.findByProjectConfigIdIn(leaderConfigIds).stream()
                    .filter(issue -> status == null || normalizeTaskStatus(issue.getStatus()).equals(status))
                    .sorted(Comparator.comparing(JiraIssue::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                    .toList();

                if (filtered.isEmpty()) {
                    filtered = leaderConfigIds.stream()
                        .flatMap(configId -> toJiraIssues(syncGrpcClient.getIssues(configId), configId).stream())
                        .filter(issue -> status == null || normalizeTaskStatus(issue.getStatus()).equals(status))
                        .sorted(Comparator.comparing(JiraIssue::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                        .toList();
                }
            }
        }

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

        boolean leaderInGroup = isLeaderInGroup(studentId, groupId);

        if (commitCount == 0 && pullRequests.isEmpty()) {
            List<GithubCommitResponse> remoteCommits = syncGrpcClient.getGithubCommits(configOpt.get().configId());
            List<UnifiedActivityResponse> remoteActivities = syncGrpcClient.getUnifiedActivities(configOpt.get().configId());

            List<GithubCommitResponse> filteredCommits = remoteCommits.stream()
                .filter(commit -> equalsIgnoreCase(commit.getAuthorEmail(), profile.email()))
                .filter(commit -> withinRange(parseDateTime(commit.getCommittedDate()), from, to))
                .toList();

            List<UnifiedActivityResponse> filteredPullRequests = remoteActivities.stream()
                .filter(activity -> "PULL_REQUEST".equalsIgnoreCase(activity.getActivityType()))
                .filter(activity -> profile.email().equalsIgnoreCase(defaultLabel(activity.getAuthorEmail())))
                .filter(activity -> withinRange(parseDateTime(activity.getCreatedAt()), from, to))
                .toList();

            commitCount = filteredCommits.size();
            pullRequests = filteredPullRequests.stream().map(this::toUnifiedActivity).toList();
            lastCommitAt = filteredCommits.stream()
                .map(commit -> parseDateTime(commit.getCommittedDate()))
                .filter(value -> value != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
            activeDays = filteredCommits.stream()
                .map(commit -> parseDateTime(commit.getCommittedDate()))
                .filter(value -> value != null)
                .map(LocalDateTime::toLocalDate)
                .collect(Collectors.toSet())
                .size();
        }

        if (leaderInGroup && commitCount == 0 && pullRequests.isEmpty()) {
            commitCount = githubCommitRepository.countByProjectConfigIdInAndDeletedAtIsNull(configIds);
            pullRequests = unifiedActivityRepository
                .findByProjectConfigIdInAndActivityTypeAndAuthorEmailIgnoreCaseAndDeletedAtIsNull(configIds, ActivityType.PULL_REQUEST, profile.email()).stream()
                .filter(activity -> withinRange(activity.getCreatedAt(), from, to))
                .toList();

            if (commitCount == 0 && pullRequests.isEmpty()) {
                List<GithubCommitResponse> remoteCommits = syncGrpcClient.getGithubCommits(configOpt.get().configId());
                List<UnifiedActivityResponse> remoteActivities = syncGrpcClient.getUnifiedActivities(configOpt.get().configId());
                commitCount = remoteCommits.stream()
                    .filter(commit -> withinRange(parseDateTime(commit.getCommittedDate()), from, to))
                    .count();
                pullRequests = remoteActivities.stream()
                    .filter(activity -> "PULL_REQUEST".equalsIgnoreCase(activity.getActivityType()))
                    .filter(activity -> withinRange(parseDateTime(activity.getCreatedAt()), from, to))
                    .map(this::toUnifiedActivity)
                    .toList();
            }
        }

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

        if (tasks.isEmpty()) {
            tasks = filterByDate(
                toJiraIssues(syncGrpcClient.getIssues(configOpt.get().configId()), configOpt.get().configId()).stream()
                    .filter(issue -> equalsIgnoreCase(issue.getAssigneeEmail(), profile.email()))
                    .toList(),
                from,
                to
            );
        }
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

        boolean leaderInGroup = isLeaderInGroup(studentId, groupId);

        if (githubCommitCount == 0 && pullRequests.isEmpty() && highlights.isEmpty()) {
            List<GithubCommitResponse> remoteCommits = syncGrpcClient.getGithubCommits(configOpt.get().configId());
            List<UnifiedActivityResponse> remoteActivities = syncGrpcClient.getUnifiedActivities(configOpt.get().configId());

            githubCommitCount = remoteCommits.stream()
                .filter(commit -> equalsIgnoreCase(commit.getAuthorEmail(), profile.email()))
                .filter(commit -> withinRange(parseDateTime(commit.getCommittedDate()), from, to))
                .count();

            pullRequests = remoteActivities.stream()
                .filter(activity -> "PULL_REQUEST".equalsIgnoreCase(activity.getActivityType()))
                .filter(activity -> profile.email().equalsIgnoreCase(defaultLabel(activity.getAuthorEmail())))
                .filter(activity -> withinRange(parseDateTime(activity.getCreatedAt()), from, to))
                .map(this::toUnifiedActivity)
                .toList();

            highlights = remoteActivities.stream()
                .filter(activity -> profile.email().equalsIgnoreCase(defaultLabel(activity.getAuthorEmail())))
                .filter(activity -> withinRange(parseDateTime(activity.getUpdatedAt()), from, to))
                .map(UnifiedActivityResponse::getTitle)
                .filter(title -> title != null && !title.isBlank())
                .limit(3)
                .toList();
        }

        if (leaderInGroup && githubCommitCount == 0 && pullRequests.isEmpty()) {
            githubCommitCount = githubCommitRepository.countByProjectConfigIdInAndDeletedAtIsNull(configIds);
            if (githubCommitCount == 0) {
                githubCommitCount = syncGrpcClient.getGithubCommits(configOpt.get().configId()).stream()
                    .filter(commit -> withinRange(parseDateTime(commit.getCommittedDate()), from, to))
                    .count();
            }
            if (pullRequests.isEmpty()) {
                pullRequests = syncGrpcClient.getUnifiedActivities(configOpt.get().configId()).stream()
                    .filter(activity -> "PULL_REQUEST".equalsIgnoreCase(activity.getActivityType()))
                    .filter(activity -> withinRange(parseDateTime(activity.getCreatedAt()), from, to))
                    .map(this::toUnifiedActivity)
                    .toList();
            }
        }

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

    @Override
    public PageResponse<StudentTaskResponse> getLeaderGroupTasks(Long actorId, Long groupId, String status, int page, int size) {
        if (!isLeaderInGroup(actorId, groupId)) {
            throw new AccessDeniedException("Leader can only access own group tasks");
        }

        GroupDetail group = userGroupClient.getGroup(groupId);
        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return emptyPage(page, size);
        }

        String normalizedStatus = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();
        UUID configId = configOpt.get().configId();
        List<JiraIssue> localIssues = jiraIssueRepository.findByProjectConfigId(configId);
        List<JiraIssue> remoteIssues = toJiraIssues(syncGrpcClient.getIssues(configId), configId);
        List<JiraIssue> issues = mergeIssues(localIssues, remoteIssues);

        List<JiraIssue> filtered = issues.stream()
            .filter(issue -> normalizedStatus == null || normalizeTaskStatus(issue.getStatus()).equalsIgnoreCase(normalizedStatus))
            .sorted(Comparator.comparing(JiraIssue::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<StudentTaskResponse> content = filtered.subList(fromIndex, toIndex).stream()
            .map(issue -> StudentTaskResponse.builder()
                .taskId(issue.getIssueId())
                .source("JIRA")
                .key(issue.getIssueKey())
                .title(issue.getSummary())
                .status(normalizeTaskStatus(issue.getStatus()))
                .priority(issue.getPriority())
                .groupId(group.groupId())
                .groupName(group.groupName())
                .assignee(issue.getAssigneeName() != null && !issue.getAssigneeName().isBlank() ? issue.getAssigneeName() : issue.getAssigneeEmail())
                .updatedAt(issue.getUpdatedAt())
                .url(configOpt.get().jiraHostUrl() != null && issue.getIssueKey() != null
                    ? configOpt.get().jiraHostUrl() + "/browse/" + issue.getIssueKey()
                    : null)
                .build())
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
    public StudentTaskResponse assignTaskToMember(Long actorId, Long groupId, String taskId, Long assigneeUserId) {
        if (!isLeaderInGroup(actorId, groupId)) {
            throw new AccessDeniedException("Leader can only assign tasks in own group");
        }

        GroupDetail group = userGroupClient.getGroup(groupId);
        ProjectConfigSnapshot config = projectConfigClient.getConfigByGroupId(groupId)
            .orElseThrow(() -> new EntityNotFoundException("Project configuration not found for group"));

        JiraIssue issue = findTaskByGroupConfig(config.configId(), taskId);
        UserGroupClient.GroupMember assignee = group.members().stream()
            .filter(member -> assigneeUserId.equals(member.userId()))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Assignee is not a member of the group"));

        if (assignee.jiraAccountId() == null || assignee.jiraAccountId().isBlank()) {
            throw new EntityNotFoundException("Assignee Jira account ID is not configured");
        }

        jiraService.assignIssue(requireIssueKey(issue), assignee.jiraAccountId());

        String assigneeName = assignee.fullName();
        if (assigneeName == null || assigneeName.isBlank()) {
            assigneeName = assignee.email();
        }
        if (assigneeName == null || assigneeName.isBlank()) {
            assigneeName = "User #" + assigneeUserId;
        }

        issue.setAssigneeEmail(assignee.email());
        issue.setAssigneeName(assigneeName);
        issue.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        JiraIssue saved = jiraIssueRepository.save(issue);

        return StudentTaskResponse.builder()
            .taskId(saved.getIssueId())
            .source("JIRA")
            .key(saved.getIssueKey())
            .title(saved.getSummary())
            .status(normalizeTaskStatus(saved.getStatus()))
            .priority(saved.getPriority())
            .groupId(group.groupId())
            .groupName(group.groupName())
            .assignee(saved.getAssigneeName() != null && !saved.getAssigneeName().isBlank() ? saved.getAssigneeName() : saved.getAssigneeEmail())
            .updatedAt(saved.getUpdatedAt())
            .url(config.jiraHostUrl() != null && saved.getIssueKey() != null ? config.jiraHostUrl() + "/browse/" + saved.getIssueKey() : null)
            .build();
    }

    @Override
    public StudentTaskResponse updateTaskStatusByLeader(Long actorId, Long groupId, String taskId, String status) {
        if (!isLeaderInGroup(actorId, groupId)) {
            throw new AccessDeniedException("Leader can only update tasks in own group");
        }

        GroupDetail group = userGroupClient.getGroup(groupId);
        ProjectConfigSnapshot config = projectConfigClient.getConfigByGroupId(groupId)
            .orElseThrow(() -> new EntityNotFoundException("Project configuration not found for group"));

        JiraIssue issue = findTaskByGroupConfig(config.configId(), taskId);
        String updatedJiraStatus = jiraService.transitionIssueToStatus(requireIssueKey(issue), status);
        issue.setStatus(updatedJiraStatus);
        issue.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        JiraIssue saved = jiraIssueRepository.save(issue);

        return StudentTaskResponse.builder()
            .taskId(saved.getIssueId())
            .source("JIRA")
            .key(saved.getIssueKey())
            .title(saved.getSummary())
            .status(normalizeTaskStatus(saved.getStatus()))
            .priority(saved.getPriority())
            .groupId(group.groupId())
            .groupName(group.groupName())
            .assignee(saved.getAssigneeName() != null && !saved.getAssigneeName().isBlank() ? saved.getAssigneeName() : saved.getAssigneeEmail())
            .updatedAt(saved.getUpdatedAt())
            .url(config.jiraHostUrl() != null && saved.getIssueKey() != null ? config.jiraHostUrl() + "/browse/" + saved.getIssueKey() : null)
            .build();
    }

    @Override
    public PageResponse<StudentTaskResponse> getMemberTasks(Long actorId, Long groupId, String status, int page, int size) {
        assertStudentInGroup(actorId, groupId);
        UserProfile profile = userGroupClient.getUserProfile(actorId);
        GroupDetail group = userGroupClient.getGroup(groupId);

        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return emptyPage(page, size);
        }

        UUID configId = configOpt.get().configId();
        String normalizedStatus = (status == null || status.isBlank()) ? null : status.trim().toUpperCase();

        List<JiraIssue> localTasks = jiraIssueRepository.findByProjectConfigIdInAndAssigneeEmailIgnoreCase(List.of(configId), profile.email());
        List<JiraIssue> remoteTasks = toJiraIssues(syncGrpcClient.getIssues(configId), configId).stream()
            .filter(item -> equalsIgnoreCase(item.getAssigneeEmail(), profile.email()))
            .toList();
        List<JiraIssue> tasks = mergeIssues(localTasks, remoteTasks);

        List<JiraIssue> filtered = tasks.stream()
            .filter(issue -> normalizedStatus == null || normalizeTaskStatus(issue.getStatus()).equalsIgnoreCase(normalizedStatus))
            .sorted(Comparator.comparing(JiraIssue::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
            .toList();

        int fromIndex = Math.min(page * size, filtered.size());
        int toIndex = Math.min(fromIndex + size, filtered.size());
        List<StudentTaskResponse> content = filtered.subList(fromIndex, toIndex).stream()
            .map(issue -> StudentTaskResponse.builder()
                .taskId(issue.getIssueId())
                .source("JIRA")
                .key(issue.getIssueKey())
                .title(issue.getSummary())
                .status(normalizeTaskStatus(issue.getStatus()))
                .priority(issue.getPriority())
                .groupId(group.groupId())
                .groupName(group.groupName())
                .assignee(defaultAssignee(issue, profile))
                .updatedAt(issue.getUpdatedAt())
                .url(configOpt.get().jiraHostUrl() != null && issue.getIssueKey() != null
                    ? configOpt.get().jiraHostUrl() + "/browse/" + issue.getIssueKey()
                    : null)
                .build())
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
    public StudentTaskResponse updateTaskStatusByMember(Long actorId, Long groupId, String taskId, String status) {
        assertStudentInGroup(actorId, groupId);

        GroupDetail group = userGroupClient.getGroup(groupId);
        UserProfile profile = userGroupClient.getUserProfile(actorId);
        ProjectConfigSnapshot config = projectConfigClient.getConfigByGroupId(groupId)
            .orElseThrow(() -> new EntityNotFoundException("Project configuration not found for group"));

        JiraIssue issue = findTaskByGroupConfig(config.configId(), taskId);
        if (issue.getAssigneeEmail() != null
            && !issue.getAssigneeEmail().isBlank()
            && !equalsIgnoreCase(issue.getAssigneeEmail(), profile.email())) {
            throw new AccessDeniedException("Member can only update own tasks");
        }

        String updatedJiraStatus = jiraService.transitionIssueToStatus(requireIssueKey(issue), status);
        issue.setStatus(updatedJiraStatus);
        issue.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
        JiraIssue saved = jiraIssueRepository.save(issue);

        return StudentTaskResponse.builder()
            .taskId(saved.getIssueId())
            .source("JIRA")
            .key(saved.getIssueKey())
            .title(saved.getSummary())
            .status(normalizeTaskStatus(saved.getStatus()))
            .priority(saved.getPriority())
            .groupId(group.groupId())
            .groupName(group.groupName())
            .assignee(defaultAssignee(saved, profile))
            .updatedAt(saved.getUpdatedAt())
            .url(config.jiraHostUrl() != null && saved.getIssueKey() != null ? config.jiraHostUrl() + "/browse/" + saved.getIssueKey() : null)
            .build();
    }

    @Override
    public GroupProgressResponse getLeaderGroupProgress(Long actorId, Long groupId, LocalDate from, LocalDate to) {
        if (!isLeaderInGroup(actorId, groupId)) {
            throw new AccessDeniedException("Leader can only access own group progress");
        }

        GroupDetail group = userGroupClient.getGroup(groupId);
        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return emptyProgress(group.groupId(), group.groupName());
        }

        UUID configId = configOpt.get().configId();
        List<JiraIssue> localIssues = jiraIssueRepository.findByProjectConfigId(configId);
        List<JiraIssue> remoteIssues = toJiraIssues(syncGrpcClient.getIssues(configId), configId);
        List<JiraIssue> issues = filterByDate(mergeIssues(localIssues, remoteIssues), from, to);
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
    public TeamCommitSummaryResponse getLeaderTeamCommitSummary(Long actorId, Long groupId, LocalDate from, LocalDate to) {
        if (!isLeaderInGroup(actorId, groupId)) {
            throw new AccessDeniedException("Leader can only access own group commit summary");
        }

        ProjectConfigSnapshot config = projectConfigClient.getConfigByGroupId(groupId)
            .orElseThrow(() -> new EntityNotFoundException("Project configuration not found for group"));

        LocalDateTime fromDate = toFromDateTime(from);
        LocalDateTime toDate = toToDateTime(to);
        List<GithubCommit> commits = githubCommitRepository.findByProjectConfigIdsWithinRange(List.of(config.configId()), fromDate, toDate);

        List<UnifiedActivityResponse> activities = syncGrpcClient.getUnifiedActivities(config.configId());
        long totalPullRequests = activities.stream()
            .filter(activity -> "PULL_REQUEST".equalsIgnoreCase(activity.getActivityType()))
            .filter(activity -> withinRange(parseDateTime(activity.getCreatedAt()), from, to))
            .count();

        if (commits.isEmpty()) {
            var remoteCommits = syncGrpcClient.getGithubCommits(config.configId()).stream()
                .filter(commit -> withinRange(parseDateTime(commit.getCommittedDate()), from, to))
                .toList();

            Map<String, TeamCommitSummaryResponse.MemberCommitSummary> members = new HashMap<>();
            for (var commit : remoteCommits) {
                String email = defaultLabel(commit.getAuthorEmail());
                TeamCommitSummaryResponse.MemberCommitSummary current = members.getOrDefault(
                    email,
                    TeamCommitSummaryResponse.MemberCommitSummary.builder()
                        .authorEmail(commit.getAuthorEmail())
                        .authorName(commit.getAuthorName())
                        .authorLogin(null)
                        .commitCount(0)
                        .additions(0)
                        .deletions(0)
                        .totalChanges(0)
                        .build()
                );
                current.setCommitCount(current.getCommitCount() + 1);
                members.put(email, current);
            }

            return TeamCommitSummaryResponse.builder()
                .groupId(groupId)
                .from(from)
                .to(to)
                .totalCommits(remoteCommits.size())
                .totalPullRequests(totalPullRequests)
                .activeContributors(members.size())
                .members(members.values().stream().sorted(Comparator.comparing(TeamCommitSummaryResponse.MemberCommitSummary::getCommitCount).reversed()).toList())
                .build();
        }

        Map<String, List<GithubCommit>> byAuthor = commits.stream()
            .collect(Collectors.groupingBy(commit -> defaultLabel(commit.getAuthorEmail())));

        List<TeamCommitSummaryResponse.MemberCommitSummary> members = byAuthor.entrySet().stream()
            .map(entry -> {
                List<GithubCommit> authorCommits = entry.getValue();
                GithubCommit first = authorCommits.get(0);
                int additions = authorCommits.stream().map(item -> item.getAdditions() == null ? 0 : item.getAdditions()).reduce(0, Integer::sum);
                int deletions = authorCommits.stream().map(item -> item.getDeletions() == null ? 0 : item.getDeletions()).reduce(0, Integer::sum);
                int totalChanges = authorCommits.stream().map(item -> item.getTotalChanges() == null ? 0 : item.getTotalChanges()).reduce(0, Integer::sum);
                return TeamCommitSummaryResponse.MemberCommitSummary.builder()
                    .authorEmail(first.getAuthorEmail())
                    .authorName(first.getAuthorName())
                    .authorLogin(first.getAuthorLogin())
                    .commitCount(authorCommits.size())
                    .additions(additions)
                    .deletions(deletions)
                    .totalChanges(totalChanges)
                    .build();
            })
            .sorted(Comparator.comparing(TeamCommitSummaryResponse.MemberCommitSummary::getCommitCount).reversed())
            .toList();

        return TeamCommitSummaryResponse.builder()
            .groupId(groupId)
            .from(from)
            .to(to)
            .totalCommits(commits.size())
            .totalPullRequests(totalPullRequests)
            .activeContributors(members.size())
            .members(members)
            .build();
    }

    @Override
    public TeamMemberTaskStatsResponse getMemberTaskStats(Long actorId, Long groupId) {
        assertStudentInGroup(actorId, groupId);
        UserProfile profile = userGroupClient.getUserProfile(actorId);

        Optional<ProjectConfigSnapshot> configOpt = projectConfigClient.getConfigByGroupId(groupId);
        if (configOpt.isEmpty()) {
            return TeamMemberTaskStatsResponse.builder()
                .groupId(groupId)
                .memberId(actorId)
                .totalAssigned(0)
                .completed(0)
                .inProgress(0)
                .todo(0)
                .completionRate(0.0)
                .build();
        }

        UUID configId = configOpt.get().configId();
        List<JiraIssue> localTasks = jiraIssueRepository.findByProjectConfigIdInAndAssigneeEmailIgnoreCase(List.of(configId), profile.email());
        List<JiraIssue> remoteTasks = toJiraIssues(syncGrpcClient.getIssues(configId), configId).stream()
            .filter(item -> equalsIgnoreCase(item.getAssigneeEmail(), profile.email()))
            .toList();
        List<JiraIssue> tasks = mergeIssues(localTasks, remoteTasks);

        long completed = tasks.stream().filter(item -> "DONE".equals(normalizeTaskStatus(item.getStatus()))).count();
        long inProgress = tasks.stream().filter(item -> "IN_PROGRESS".equals(normalizeTaskStatus(item.getStatus()))).count();
        long todo = tasks.stream().filter(item -> "TODO".equals(normalizeTaskStatus(item.getStatus()))).count();
        double completionRate = tasks.isEmpty() ? 0.0 : Math.round(((double) completed * 10000.0) / tasks.size()) / 100.0;

        return TeamMemberTaskStatsResponse.builder()
            .groupId(groupId)
            .memberId(actorId)
            .totalAssigned(tasks.size())
            .completed(completed)
            .inProgress(inProgress)
            .todo(todo)
            .completionRate(completionRate)
            .build();
    }

    private JiraIssue findTaskByGroupConfig(UUID configId, String taskId) {
        Optional<JiraIssue> local = jiraIssueRepository.findTaskByProjectConfigAndTaskId(configId, taskId);
        if (local.isPresent()) {
            return local.get();
        }

        return toJiraIssues(syncGrpcClient.getIssues(configId), configId).stream()
            .filter(item -> taskId.equalsIgnoreCase(defaultLabel(item.getIssueId()))
                || taskId.equalsIgnoreCase(defaultLabel(item.getIssueKey())))
            .findFirst()
            .orElseThrow(() -> new EntityNotFoundException("Task not found in group project configuration"));
    }

    private List<JiraIssue> mergeIssues(List<JiraIssue> localIssues, List<JiraIssue> remoteIssues) {
        Map<String, JiraIssue> merged = new LinkedHashMap<>();
        for (JiraIssue issue : remoteIssues) {
            merged.put(issueIdentity(issue), issue);
        }
        for (JiraIssue issue : localIssues) {
            merged.put(issueIdentity(issue), issue);
        }
        return new ArrayList<>(merged.values());
    }

    private List<UnifiedActivity> mergeActivities(List<UnifiedActivity> localActivities, List<UnifiedActivity> remoteActivities) {
        Map<String, UnifiedActivity> merged = new LinkedHashMap<>();
        for (UnifiedActivity activity : remoteActivities) {
            merged.put(activityIdentity(activity), activity);
        }
        for (UnifiedActivity activity : localActivities) {
            merged.put(activityIdentity(activity), activity);
        }
        return new ArrayList<>(merged.values());
    }

    private String activityIdentity(UnifiedActivity activity) {
        if (activity.getExternalId() != null && !activity.getExternalId().isBlank()) {
            return defaultLabel(activity.getSource() == null ? null : activity.getSource().name())
                + "|"
                + defaultLabel(activity.getActivityType() == null ? null : activity.getActivityType().name())
                + "|"
                + activity.getExternalId();
        }
        return defaultLabel(activity.getTitle()) + "|" + String.valueOf(activity.getCreatedAt());
    }

    private List<GithubCommit> mergeGithubCommits(List<GithubCommit> localCommits, List<GithubCommit> remoteCommits) {
        Map<String, GithubCommit> merged = new LinkedHashMap<>();
        for (GithubCommit commit : remoteCommits) {
            merged.put(commitIdentity(commit), commit);
        }
        for (GithubCommit commit : localCommits) {
            merged.put(commitIdentity(commit), commit);
        }
        return new ArrayList<>(merged.values());
    }

    private String commitIdentity(GithubCommit commit) {
        if (commit.getCommitSha() != null && !commit.getCommitSha().isBlank()) {
            return "SHA:" + commit.getCommitSha();
        }
        return defaultLabel(commit.getAuthorEmail())
            + "|"
            + defaultLabel(commit.getMessage())
            + "|"
            + String.valueOf(commit.getCommittedDate());
    }

    private String issueIdentity(JiraIssue issue) {
        if (issue.getIssueId() != null && !issue.getIssueId().isBlank()) {
            return "ID:" + issue.getIssueId();
        }
        if (issue.getIssueKey() != null && !issue.getIssueKey().isBlank()) {
            return "KEY:" + issue.getIssueKey();
        }
        return "FALLBACK:" + String.valueOf(issue.getId());
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
            .assignee(defaultAssignee(issue, profile))
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
        if (normalized.contains("progress")
            || normalized.contains("doing")
            || normalized.contains("design")
            || normalized.contains("testing")
            || normalized.contains("review")
            || normalized.contains("qa")
            || normalized.contains("uat")
            || normalized.contains("live")) {
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

    private String defaultAssignee(JiraIssue issue, UserProfile profile) {
        if (issue.getAssigneeName() != null && !issue.getAssigneeName().isBlank()) {
            return issue.getAssigneeName();
        }
        if (issue.getAssigneeEmail() != null && !issue.getAssigneeEmail().isBlank()) {
            return issue.getAssigneeEmail();
        }
        return profile.fullName();
    }

    private String requireIssueKey(JiraIssue issue) {
        if (issue.getIssueKey() == null || issue.getIssueKey().isBlank()) {
            throw new UpstreamServiceException("Issue key is missing, cannot synchronize with Jira");
        }
        return issue.getIssueKey();
    }

    private boolean equalsIgnoreCase(String left, String right) {
        if (left == null || right == null || left.isBlank() || right.isBlank()) {
            return false;
        }
        return left.equalsIgnoreCase(right);
    }

    private boolean isLeaderInGroup(Long studentId, Long groupId) {
        return userGroupClient.getUserGroups(studentId).stream()
            .anyMatch(item -> groupId.equals(item.groupId()) && "LEADER".equalsIgnoreCase(item.role()));
    }

    private List<JiraIssue> toJiraIssues(List<IssueResponse> source, UUID configId) {
        return source.stream()
            .map(item -> JiraIssue.builder()
                .projectConfigId(configId)
                .issueId(item.getIssueId())
                .issueKey(item.getIssueKey())
                .summary(item.getSummary())
                .description(item.getDescription())
                .issueType(item.getIssueType())
                .status(item.getStatus())
                .priority(item.getPriority())
                .assigneeEmail(item.getAssigneeEmail())
                .assigneeName(item.getAssigneeName())
                .reporterEmail(item.getReporterEmail())
                .reporterName(item.getReporterName())
                .createdAt(parseIssueTimestamp(item.getCreatedAt()))
                .updatedAt(parseIssueTimestamp(item.getUpdatedAt()))
                .build())
            .toList();
    }

    private List<GithubCommit> toGithubCommits(List<GithubCommitResponse> source, UUID configId) {
        return source.stream()
            .map(item -> {
                LocalDateTime committedAt = parseDateTime(item.getCommittedDate());
                return GithubCommit.builder()
                    .projectConfigId(configId)
                    .commitSha(item.getCommitSha())
                    .message(item.getMessage() == null || item.getMessage().isBlank() ? "N/A" : item.getMessage())
                    .committedDate(committedAt == null ? LocalDateTime.MIN : committedAt)
                    .authorEmail(item.getAuthorEmail())
                    .authorName(item.getAuthorName())
                    .build();
            })
            .toList();
    }

    private List<UnifiedActivity> toUnifiedActivities(List<UnifiedActivityResponse> source, UUID configId) {
        return source.stream()
            .map(item -> {
                UnifiedActivity activity = toUnifiedActivity(item);
                activity.setProjectConfigId(configId);
                return activity;
            })
            .toList();
    }

    private OffsetDateTime parseIssueTimestamp(String value) {
        LocalDateTime parsed = parseDateTime(value);
        return parsed == null ? null : parsed.atOffset(ZoneOffset.UTC);
    }

    private UnifiedActivity toUnifiedActivity(UnifiedActivityResponse source) {
        return UnifiedActivity.builder()
            .source(parseActivitySource(source.getSource()))
            .activityType(parseActivityType(source.getActivityType()))
            .externalId(source.getExternalId())
            .title(source.getTitle())
            .description(source.getDescription())
            .authorEmail(source.getAuthorEmail())
            .authorName(source.getAuthorName())
            .status(source.getStatus())
            .createdAt(parseDateTime(source.getCreatedAt()))
            .updatedAt(parseDateTime(source.getUpdatedAt()))
            .build();
    }

    private LocalDateTime parseDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private ActivitySource parseActivitySource(String value) {
        try {
            return value == null || value.isBlank() ? ActivitySource.JIRA : ActivitySource.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ActivitySource.JIRA;
        }
    }

    private ActivityType parseActivityType(String value) {
        try {
            return value == null || value.isBlank() ? ActivityType.TASK : ActivityType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return ActivityType.TASK;
        }
    }
}