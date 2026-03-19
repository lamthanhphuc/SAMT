package com.example.reportservice.service;

import com.example.reportservice.dto.request.AnalyticsReportRequest;
import com.example.reportservice.dto.response.ReportResponse;
import com.example.reportservice.entity.GithubCommit;
import com.example.reportservice.entity.JiraIssue;
import com.example.reportservice.entity.Report;
import com.example.reportservice.entity.ReportType;
import com.example.reportservice.exporter.ExcelAnalyticsExporter;
import com.example.reportservice.grpc.SyncGrpcClient;
import com.example.reportservice.repository.GithubCommitRepository;
import com.example.reportservice.repository.JiraIssueRepository;
import com.example.reportservice.repository.ReportRepository;
import com.example.reportservice.service.analyzer.CommitAnalyzer;
import com.example.reportservice.service.analyzer.WorkDistributionAnalyzer;
import com.example.reportservice.web.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class AnalyticsReportingService {

    private static final String REPORT_STATUS_COMPLETED = "COMPLETED";

    private final ExcelAnalyticsExporter excelExporter;
    private final ReportRepository reportRepository;
    private final JiraIssueRepository jiraIssueRepository;
    private final GithubCommitRepository githubCommitRepository;
    private final TransactionTemplate transactionTemplate;
    private final SyncGrpcClient syncGrpcClient;

    private final WorkDistributionAnalyzer workDistributionAnalyzer = new WorkDistributionAnalyzer();
    private final CommitAnalyzer commitAnalyzer = new CommitAnalyzer();

    public ReportResponse generateWorkDistribution(AnalyticsReportRequest request, String subject) {
        return generate(request, subject, ReportType.WORK_DISTRIBUTION);
    }

    public ReportResponse generateCommitAnalysis(AnalyticsReportRequest request, String subject) {
        return generate(request, subject, ReportType.COMMIT_ANALYSIS);
    }

    private ReportResponse generate(AnalyticsReportRequest request, String subject, ReportType type) {
        validateRequest(request, subject);

        UUID createdBy = toCreatedBy(subject);
        OffsetDateTime nowUtc = OffsetDateTime.now();
        UUID projectConfigUuid = parseProjectConfigUuid(request.getProjectConfigId());

        LocalDate from = parseDate(extractFrom(request));
        LocalDate to = parseDate(extractTo(request));
        List<AnalyticsReportRequest.Member> members = safeMembers(request.getMembers());

        String fallbackMemberId = members.isEmpty() ? "" : members.getFirst().getId();
        Map<String, String> memberLookup = buildMemberLookup(members);

        String filePath;
        if (type == ReportType.WORK_DISTRIBUTION) {
            List<AnalyticsReportRequest.JiraIssue> jiraInRange;
            if (projectConfigUuid != null) {
                jiraInRange = loadJiraFromDb(projectConfigUuid, from, to, memberLookup, fallbackMemberId);
                if (jiraInRange.isEmpty()) {
                    log.info("Analytics {}: Jira DB empty, falling back to sync-service. projectConfigId={}, from={}, to={}",
                        type, projectConfigUuid, from, to);
                    jiraInRange = loadJiraFromSync(projectConfigUuid, from, to, memberLookup, fallbackMemberId);
                }
            } else {
                jiraInRange = filterJiraByRange(request.getJiraIssues(), from, to);
            }
            log.info("Analytics {}: Jira issues in range={}", type, jiraInRange.size());
            var workRows = workDistributionAnalyzer.analyze(request.getMembers(), jiraInRange, nowUtc);
            filePath = excelExporter.exportWorkDistribution(workRows);
        } else if (type == ReportType.COMMIT_ANALYSIS) {
            List<AnalyticsReportRequest.GitCommit> commitsInRange;
            if (projectConfigUuid != null) {
                commitsInRange = loadCommitsFromDb(projectConfigUuid, from, to, memberLookup, fallbackMemberId);
                if (commitsInRange.isEmpty()) {
                    log.info("Analytics {}: Commit DB empty, falling back to sync-service. projectConfigId={}, from={}, to={}",
                        type, projectConfigUuid, from, to);
                    commitsInRange = loadCommitsFromSync(projectConfigUuid, from, to, memberLookup, fallbackMemberId);
                }
            } else {
                commitsInRange = filterCommitsByRange(request.getGitCommits(), from, to);
            }
            log.info("Analytics {}: commits in range={}", type, commitsInRange.size());

            LocalDate effectiveFrom = from;
            LocalDate effectiveTo = to;
            if ((effectiveFrom == null || effectiveTo == null) && !commitsInRange.isEmpty()) {
                LocalDate minCommitDay = commitsInRange.stream()
                    .map(item -> parseDate(item == null ? null : item.getTimestamp()))
                    .filter(day -> day != null)
                    .min(LocalDate::compareTo)
                    .orElse(null);
                LocalDate maxCommitDay = commitsInRange.stream()
                    .map(item -> parseDate(item == null ? null : item.getTimestamp()))
                    .filter(day -> day != null)
                    .max(LocalDate::compareTo)
                    .orElse(null);
                effectiveFrom = effectiveFrom == null ? minCommitDay : effectiveFrom;
                effectiveTo = effectiveTo == null ? maxCommitDay : effectiveTo;
            }

            var commitRows = commitAnalyzer.analyze(request.getMembers(), commitsInRange, effectiveFrom, effectiveTo);
            filePath = excelExporter.exportCommitAnalysis(commitRows);
        } else {
            throw new BadRequestException("Unsupported analytics report type: " + type);
        }

        Path reportPath = Path.of(filePath).toAbsolutePath().normalize();
        if (!Files.exists(reportPath) || !Files.isRegularFile(reportPath)) {
            throw new BadRequestException("Export failed: file was not created");
        }

        Report report = transactionTemplate.execute(status -> {
            Report entity = Report.builder()
                .projectConfigId(request.getProjectConfigId().trim())
                .type(type)
                .filePath(reportPath.toString())
                .createdBy(createdBy)
                .createdAt(java.time.LocalDateTime.now())
                .build();
            reportRepository.save(entity);
            return entity;
        });

        return new ReportResponse(
            report.getReportId(),
            REPORT_STATUS_COMPLETED,
            report.getCreatedAt(),
            buildDownloadUrl(report.getReportId())
        );
    }

    private void validateRequest(AnalyticsReportRequest request, String subject) {
        if (request == null) {
            throw new BadRequestException("Request body is required");
        }
        if (subject == null || subject.isBlank()) {
            throw new BadRequestException("Authenticated subject is required");
        }
        if (request.getProjectConfigId() == null || request.getProjectConfigId().isBlank()) {
            throw new BadRequestException("projectConfigId is required");
        }
        if (request.getGroupId() == null || request.getGroupId().isBlank()) {
            throw new BadRequestException("groupId is required");
        }
        if (request.getMembers() == null || request.getMembers().isEmpty()) {
            throw new BadRequestException("members is required");
        }
    }

    private UUID parseProjectConfigUuid(String projectConfigId) {
        if (projectConfigId == null || projectConfigId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(projectConfigId.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private String extractFrom(AnalyticsReportRequest request) {
        return request.getTimeRange() == null ? null : request.getTimeRange().getFrom();
    }

    private String extractTo(AnalyticsReportRequest request) {
        return request.getTimeRange() == null ? null : request.getTimeRange().getTo();
    }

    private List<AnalyticsReportRequest.Member> safeMembers(List<AnalyticsReportRequest.Member> members) {
        return members == null ? List.of() : members;
    }

    private Map<String, String> buildMemberLookup(List<AnalyticsReportRequest.Member> members) {
        Map<String, String> lookup = new HashMap<>();
        for (AnalyticsReportRequest.Member member : members) {
            if (member == null || member.getId() == null || member.getId().isBlank()) {
                continue;
            }
            String id = member.getId().trim();
            String nameKey = toKey(member.getName());
            if (!nameKey.isBlank()) {
                lookup.put(nameKey, id);
            }
            String emailKey = toKey(member.getEmail());
            if (!emailKey.isBlank()) {
                lookup.put(emailKey, id);
            }
            String githubKey = toKey(member.getGithubUsername());
            if (!githubKey.isBlank()) {
                lookup.put(githubKey, id);
            }
        }
        return lookup;
    }

    private List<AnalyticsReportRequest.JiraIssue> loadJiraFromDb(UUID projectConfigId,
                                                                  LocalDate from,
                                                                  LocalDate to,
                                                                  Map<String, String> memberLookup,
                                                                  String fallbackMemberId) {
        List<JiraIssue> issues = jiraIssueRepository.findByProjectConfigId(projectConfigId);
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }

        List<AnalyticsReportRequest.JiraIssue> mapped = new ArrayList<>(issues.size());
        for (JiraIssue issue : issues) {
            if (issue == null) {
                continue;
            }

            LocalDate createdDay = issue.getCreatedAt() == null ? null : issue.getCreatedAt().toLocalDate();
            if (!withinRange(createdDay, from, to)) {
                continue;
            }

            AnalyticsReportRequest.JiraIssue row = new AnalyticsReportRequest.JiraIssue();
            row.setId(firstNonBlank(issue.getIssueId(), issue.getIssueKey(), "jira-unknown"));

            String assigneeId = firstResolvedMemberId(memberLookup, fallbackMemberId,
                issue.getAssigneeEmail(), issue.getAssigneeName(), issue.getReporterEmail(), issue.getReporterName());
            row.setAssigneeId(assigneeId);

            String normalizedStatus = normalizeIssueStatus(issue.getStatus());
            row.setStatus(normalizedStatus);

            String createdAt = issue.getCreatedAt() == null ? null : issue.getCreatedAt().toLocalDate().toString();
            row.setCreatedAt(firstNonBlank(createdAt, issue.getUpdatedAt() == null ? null : issue.getUpdatedAt().toLocalDate().toString()));

            if ("DONE".equals(normalizedStatus) && issue.getUpdatedAt() != null) {
                row.setCompletedAt(issue.getUpdatedAt().toLocalDate().toString());
            }

            if (issue.getDueDate() != null) {
                row.setDueDate(issue.getDueDate().toString());
            }

            mapped.add(row);
        }
        return mapped;
    }

    private List<AnalyticsReportRequest.JiraIssue> loadJiraFromSync(UUID projectConfigId,
                                                                    LocalDate from,
                                                                    LocalDate to,
                                                                    Map<String, String> memberLookup,
                                                                    String fallbackMemberId) {
        var remoteIssues = syncGrpcClient.getIssues(projectConfigId);
        if (remoteIssues == null || remoteIssues.isEmpty()) {
            return List.of();
        }

        List<AnalyticsReportRequest.JiraIssue> mapped = new ArrayList<>(remoteIssues.size());
        for (var issue : remoteIssues) {
            if (issue == null) {
                continue;
            }

            LocalDate createdDay = parseDate(issue.getCreatedAt());
            if (!withinRange(createdDay, from, to)) {
                continue;
            }

            AnalyticsReportRequest.JiraIssue row = new AnalyticsReportRequest.JiraIssue();
            row.setId(firstNonBlank(issue.getIssueId(), issue.getIssueKey(), "jira-unknown"));

            String assigneeId = firstResolvedMemberId(memberLookup, fallbackMemberId,
                issue.getAssigneeEmail(), issue.getAssigneeName(), issue.getReporterEmail(), issue.getReporterName());
            row.setAssigneeId(assigneeId);

            String normalizedStatus = normalizeIssueStatus(issue.getStatus());
            row.setStatus(normalizedStatus);

            row.setCreatedAt(firstNonBlank(createdDay == null ? null : createdDay.toString(), parseDate(issue.getUpdatedAt()) == null ? null : parseDate(issue.getUpdatedAt()).toString()));
            if ("DONE".equals(normalizedStatus)) {
                LocalDate completed = parseDate(issue.getUpdatedAt());
                if (completed != null) {
                    row.setCompletedAt(completed.toString());
                }
            }
            LocalDate due = parseDate(issue.getDueDate());
            if (due != null) {
                row.setDueDate(due.toString());
            }

            mapped.add(row);
        }

        return mapped;
    }

    private List<AnalyticsReportRequest.GitCommit> loadCommitsFromDb(UUID projectConfigId,
                                                                      LocalDate from,
                                                                      LocalDate to,
                                                                      Map<String, String> memberLookup,
                                                                      String fallbackMemberId) {
        LocalDateTime fromDateTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toDateTime = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);

        List<GithubCommit> commits = githubCommitRepository.findByProjectConfigIdsWithinRange(
            List.of(projectConfigId),
            fromDateTime,
            toDateTime
        );
        if (commits == null || commits.isEmpty()) {
            return List.of();
        }

        List<AnalyticsReportRequest.GitCommit> mapped = new ArrayList<>(commits.size());
        for (GithubCommit commit : commits) {
            if (commit == null || commit.getCommittedDate() == null) {
                continue;
            }

            AnalyticsReportRequest.GitCommit row = new AnalyticsReportRequest.GitCommit();

            String authorId = firstResolvedMemberId(memberLookup, fallbackMemberId,
                commit.getAuthorEmail(), commit.getAuthorName(), commit.getAuthorLogin());
            row.setAuthorId(authorId);

            row.setMessage(firstNonBlank(commit.getMessage(), "Git commit"));
            Integer additions = commit.getAdditions();
            Integer deletions = commit.getDeletions();
            Integer totalChanges = commit.getTotalChanges();

            long safeAdded = additions == null ? 0 : Math.max(0, additions);
            long safeDeleted = deletions == null ? 0 : Math.max(0, deletions);
            // Some sync pipelines only store totalChanges. If additions/deletions are missing, still produce a non-zero "size".
            if (safeAdded == 0 && safeDeleted == 0 && totalChanges != null && totalChanges > 0) {
                safeAdded = totalChanges;
            }

            row.setLinesAdded(safeAdded);
            row.setLinesDeleted(safeDeleted);
            row.setTimestamp(commit.getCommittedDate().toLocalDate().toString());

            mapped.add(row);
        }
        return mapped;
    }

    private List<AnalyticsReportRequest.GitCommit> loadCommitsFromSync(UUID projectConfigId,
                                                                       LocalDate from,
                                                                       LocalDate to,
                                                                       Map<String, String> memberLookup,
                                                                       String fallbackMemberId) {
        var remoteCommits = syncGrpcClient.getGithubCommits(projectConfigId);
        if (remoteCommits == null || remoteCommits.isEmpty()) {
            return List.of();
        }

        List<AnalyticsReportRequest.GitCommit> mapped = new ArrayList<>(remoteCommits.size());
        for (var commit : remoteCommits) {
            if (commit == null) {
                continue;
            }

            LocalDate day = parseDate(commit.getCommittedDate());
            if (!withinRange(day, from, to)) {
                continue;
            }

            AnalyticsReportRequest.GitCommit row = new AnalyticsReportRequest.GitCommit();
            String authorId = firstResolvedMemberId(memberLookup, fallbackMemberId, commit.getAuthorEmail(), commit.getAuthorName());
            row.setAuthorId(authorId);
            row.setMessage(firstNonBlank(commit.getMessage(), "Git commit"));
            long safeAdded = Math.max(0, commit.getAdditions());
            long safeDeleted = Math.max(0, commit.getDeletions());
            long safeTotalChanges = Math.max(0, commit.getTotalChanges());
            if (safeAdded == 0 && safeDeleted == 0 && safeTotalChanges > 0) {
                safeAdded = safeTotalChanges;
            }
            row.setLinesAdded(safeAdded);
            row.setLinesDeleted(safeDeleted);
            row.setTimestamp(day == null ? "" : day.toString());
            if (!row.getTimestamp().isBlank()) {
                mapped.add(row);
            }
        }

        return mapped;
    }

    private String normalizeIssueStatus(String rawStatus) {
        String normalized = rawStatus == null ? "" : rawStatus.trim().toUpperCase(Locale.ROOT);
        if (normalized.contains("DONE") || normalized.contains("CLOSED") || normalized.contains("RESOLVED") || normalized.contains("APPROVED")) {
            return "DONE";
        }
        if (normalized.contains("IN_PROGRESS") || normalized.contains("PROGRESS") || normalized.contains("IN DESIGN") || normalized.contains("IN_DESIGN") || normalized.contains("REVIEW") || normalized.contains("TEST")) {
            return "IN_PROGRESS";
        }
        return "TODO";
    }

    private String toKey(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String firstResolvedMemberId(Map<String, String> memberLookup, String fallbackMemberId, String... candidates) {
        for (String candidate : candidates) {
            String key = toKey(candidate);
            if (!key.isBlank() && memberLookup.containsKey(key)) {
                return memberLookup.get(key);
            }
        }
        return fallbackMemberId == null ? "" : fallbackMemberId;
    }

    private String firstNonBlank(String... candidates) {
        for (String candidate : candidates) {
            if (candidate != null && !candidate.isBlank()) {
                return candidate;
            }
        }
        return "";
    }

    private List<AnalyticsReportRequest.JiraIssue> filterJiraByRange(List<AnalyticsReportRequest.JiraIssue> issues, LocalDate from, LocalDate to) {
        if (issues == null || issues.isEmpty()) {
            return List.of();
        }
        return issues.stream()
            .filter(item -> withinRange(parseDate(item == null ? null : item.getCreatedAt()), from, to))
            .toList();
    }

    private List<AnalyticsReportRequest.GitCommit> filterCommitsByRange(List<AnalyticsReportRequest.GitCommit> commits, LocalDate from, LocalDate to) {
        if (commits == null || commits.isEmpty()) {
            return List.of();
        }
        return commits.stream()
            .filter(item -> withinRange(parseDate(item == null ? null : item.getTimestamp()), from, to))
            .toList();
    }

    private boolean withinRange(LocalDate value, LocalDate from, LocalDate to) {
        if (value == null) {
            return false;
        }
        return (from == null || !value.isBefore(from)) && (to == null || !value.isAfter(to));
    }

    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return java.time.OffsetDateTime.parse(v).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        // Common Jira/GitHub timestamps may be ISO local datetime (no timezone)
        try {
            return LocalDateTime.parse(v).toLocalDate();
        } catch (DateTimeParseException ignored) {
        }
        // Or instant-like strings
        try {
            return Instant.parse(v).atZone(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private String buildDownloadUrl(UUID reportId) {
        return "/api/reports/" + reportId + "/download";
    }

    private UUID toCreatedBy(String subject) {
        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException ignored) {
            return UUID.nameUUIDFromBytes(("user:" + subject).getBytes(StandardCharsets.UTF_8));
        }
    }
}

