package com.example.reportservice.service.analyzer;

import com.example.reportservice.dto.request.AnalyticsReportRequest;
import lombok.Builder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class WorkDistributionAnalyzer {

    public List<MemberWorkDistribution> analyze(List<AnalyticsReportRequest.Member> members,
                                                List<AnalyticsReportRequest.JiraIssue> jiraIssues,
                                                OffsetDateTime nowUtc) {

        Map<String, Mutable> byAssignee = new HashMap<>(Math.max(16, members.size() * 2));

        for (AnalyticsReportRequest.JiraIssue issue : jiraIssues) {
            if (issue == null || issue.getAssigneeId() == null || issue.getAssigneeId().isBlank()) {
                continue;
            }

            String assigneeId = issue.getAssigneeId().trim();
            Mutable m = byAssignee.computeIfAbsent(assigneeId, k -> new Mutable());
            m.assigned++;

            boolean done = "DONE".equalsIgnoreCase(safeStatus(issue.getStatus()));
            if (done) {
                m.completed++;
                OffsetDateTime createdAt = parseIsoDate(issue.getCreatedAt());
                OffsetDateTime completedAt = parseIsoDate(issue.getCompletedAt());
                if (createdAt != null && completedAt != null && !completedAt.isBefore(createdAt)) {
                    m.completionSecondsSum += Duration.between(createdAt, completedAt).getSeconds();
                    m.completionSamples++;
                }
            } else {
                LocalDate dueDate = parseIsoLocalDate(issue.getDueDate());
                if (dueDate != null) {
                    LocalDate today = nowUtc.toLocalDate();
                    if (dueDate.isBefore(today)) {
                        m.overdue++;
                    }
                }
            }
        }

        return members.stream().map(member -> {
            Mutable m = byAssignee.get(member.getId() == null ? "" : member.getId().trim());
            long assigned = m == null ? 0 : m.assigned;
            long completed = m == null ? 0 : m.completed;
            long overdue = m == null ? 0 : m.overdue;
            double completionRate = assigned == 0 ? 0.0 : ((double) completed) / (double) assigned;
            double avgCompletionDays = (m == null || m.completionSamples == 0)
                ? 0.0
                : ((double) m.completionSecondsSum / 86400.0) / (double) m.completionSamples;

            return MemberWorkDistribution.builder()
                .memberName(member.getName())
                .assigned(assigned)
                .completed(completed)
                .completionRate(completionRate)
                .overdue(overdue)
                .avgCompletionDays(avgCompletionDays)
                .build();
        }).toList();
    }

    private String safeStatus(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    private OffsetDateTime parseIsoDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return OffsetDateTime.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDate.parse(v).atStartOfDay().atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        // Common case: ISO local datetime (no timezone)
        try {
            return java.time.LocalDateTime.parse(v).atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private LocalDate parseIsoLocalDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String v = value.trim();
        try {
            return LocalDate.parse(v);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(v).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static class Mutable {
        long assigned;
        long completed;
        long overdue;
        long completionSecondsSum;
        long completionSamples;
    }

    @Builder
    public record MemberWorkDistribution(
        String memberName,
        long assigned,
        long completed,
        double completionRate, // 0..1
        long overdue,
        double avgCompletionDays
    ) {}
}

