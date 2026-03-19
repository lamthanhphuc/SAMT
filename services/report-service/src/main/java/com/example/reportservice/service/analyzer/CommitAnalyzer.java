package com.example.reportservice.service.analyzer;

import com.example.reportservice.dto.request.AnalyticsReportRequest;
import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CommitAnalyzer {

    private static final List<String> QUALITY_KEYWORDS = List.of("fix", "feat", "refactor", "test");

    public List<MemberCommitAnalysis> analyze(List<AnalyticsReportRequest.Member> members,
                                             List<AnalyticsReportRequest.GitCommit> commits,
                                             LocalDate rangeFrom,
                                             LocalDate rangeTo) {

        Map<String, Mutable> byAuthor = new HashMap<>(Math.max(16, members.size() * 2));

        for (AnalyticsReportRequest.GitCommit commit : commits) {
            if (commit == null || commit.getAuthorId() == null || commit.getAuthorId().isBlank()) {
                continue;
            }

            String authorId = commit.getAuthorId().trim();
            Mutable m = byAuthor.computeIfAbsent(authorId, k -> new Mutable());
            m.totalCommits++;

            long changed = safeNonNegative(commit.getLinesAdded()) + safeNonNegative(commit.getLinesDeleted());
            m.totalLinesChanged += changed;

            LocalDate day = parseIsoLocalDate(commit.getTimestamp());
            if (day != null) {
                m.activeDays.add(day);
            }

            m.messageQualityScore += scoreMessage(commit.getMessage());
        }

        long daysInRange = daysInclusive(rangeFrom, rangeTo);
        double maxAvgCommitSize = 0.0;
        Map<String, Derived> derivedByMember = new HashMap<>(Math.max(16, members.size() * 2));

        for (AnalyticsReportRequest.Member member : members) {
            String memberId = member.getId() == null ? "" : member.getId().trim();
            Mutable m = byAuthor.get(memberId);
            long totalCommits = m == null ? 0 : m.totalCommits;
            long totalLinesChanged = m == null ? 0 : m.totalLinesChanged;
            long activeDays = m == null ? 0 : m.activeDays.size();

            double avgCommitSize = totalCommits == 0 ? 0.0 : ((double) totalLinesChanged) / (double) totalCommits;
            maxAvgCommitSize = Math.max(maxAvgCommitSize, avgCommitSize);

            Derived d = new Derived(totalCommits, totalLinesChanged, activeDays, avgCommitSize, m == null ? 0 : m.messageQualityScore);
            derivedByMember.put(memberId, d);
        }

        double maxRaw = 0.0;
        Map<String, Double> rawByMember = new HashMap<>(derivedByMember.size());

        for (var entry : derivedByMember.entrySet()) {
            Derived d = entry.getValue();
            double avgCommitSizeNorm10 = maxAvgCommitSize <= 0.0 ? 0.0 : (d.avgCommitSize / maxAvgCommitSize) * 10.0;
            double raw = (d.totalCommits * 0.4)
                + (d.activeDays * 0.3)
                + (avgCommitSizeNorm10 * 0.1)
                + (d.messageQualityScore * 0.2);
            rawByMember.put(entry.getKey(), raw);
            maxRaw = Math.max(maxRaw, raw);
        }

        double finalMaxRaw = maxRaw;
        return members.stream().map(member -> {
            String memberId = member.getId() == null ? "" : member.getId().trim();
            Derived d = derivedByMember.getOrDefault(memberId, new Derived(0, 0, 0, 0.0, 0));
            double commitFrequency = daysInRange <= 0 ? 0.0 : ((double) d.totalCommits) / (double) daysInRange;

            double raw = rawByMember.getOrDefault(memberId, 0.0);
            double score10 = finalMaxRaw <= 0.0 ? 0.0 : (raw / finalMaxRaw) * 10.0;

            return MemberCommitAnalysis.builder()
                .memberName(member.getName())
                .commits(d.totalCommits)
                .activeDays(d.activeDays)
                .avgCommitSize(d.avgCommitSize)
                .commitFrequency(commitFrequency)
                .score(score10)
                .build();
        }).toList();
    }

    private int scoreMessage(String message) {
        if (message == null) {
            return 0;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return 0;
        }
        int score = 0;
        if (trimmed.length() > 10) {
            score++;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        for (String kw : QUALITY_KEYWORDS) {
            if (lower.contains(kw)) {
                score++;
                break;
            }
        }
        return score;
    }

    private long safeNonNegative(long value) {
        return Math.max(0, value);
    }

    private long daysInclusive(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            return 1;
        }
        if (to.isBefore(from)) {
            return 1;
        }
        return ChronoUnit.DAYS.between(from, to) + 1;
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
            return OffsetDateTime.parse(v).atZoneSameInstant(ZoneOffset.UTC).toLocalDate();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static class Mutable {
        long totalCommits;
        long totalLinesChanged;
        final Set<LocalDate> activeDays = new HashSet<>();
        int messageQualityScore;
    }

    private record Derived(long totalCommits,
                           long totalLinesChanged,
                           long activeDays,
                           double avgCommitSize,
                           int messageQualityScore) {}

    @Builder
    public record MemberCommitAnalysis(
        String memberName,
        long commits,
        long activeDays,
        double avgCommitSize,
        double commitFrequency,
        double score // 0..10
    ) {}
}

