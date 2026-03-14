package com.example.syncservice.service;

import com.example.syncservice.dto.GithubCommitDto;
import com.example.syncservice.dto.JiraIssueDto;
import com.example.syncservice.dto.ProjectConfigDto;
import com.example.syncservice.entity.*;
import com.example.syncservice.metrics.SyncMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Adapter for converting external API DTOs to entities.
 * Applies normalization logic and maps to unified activity model.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class DataMapper {

    private static final DateTimeFormatter ISO_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;
    private static final int UNIFIED_ACTIVITY_TITLE_MAX_LENGTH = 1000;
    private static final int AUTHOR_EMAIL_MAX_LENGTH = 255;
    private static final int AUTHOR_NAME_MAX_LENGTH = 255;
    private static final int STATUS_MAX_LENGTH = 50;
    
    private final SyncMetrics syncMetrics;

    /**
     * Convert Jira issue to UnifiedActivity.
     */
    public UnifiedActivity jiraIssueToUnifiedActivity(JiraIssueDto dto, UUID projectConfigId) {
        UnifiedActivity.ActivityType activityType = mapJiraIssueTypeToActivityType(
                dto.getFields().getIssueType() != null ? dto.getFields().getIssueType().getName() : null);

        String externalId = firstNonBlank(dto.getKey(), dto.getId(), "jira-unknown");
        UnifiedActivity activity = UnifiedActivity.builder()
                .projectConfigId(projectConfigId)
                .source(UnifiedActivity.ActivitySource.JIRA)
                .activityType(activityType)
            .externalId(externalId)
            .title(requiredValue(dto.getFields().getSummary(), dto.getKey(), UNIFIED_ACTIVITY_TITLE_MAX_LENGTH))
            .description(trimToNull(dto.getFields().getDescription()))
            .authorEmail(truncate(dto.getFields().getReporter() != null ? dto.getFields().getReporter().getEmailAddress() : null, AUTHOR_EMAIL_MAX_LENGTH))
            .authorName(truncate(dto.getFields().getReporter() != null ? dto.getFields().getReporter().getDisplayName() : null, AUTHOR_NAME_MAX_LENGTH))
            .status(truncate(dto.getFields().getStatus() != null ? dto.getFields().getStatus().getName() : null, STATUS_MAX_LENGTH))
                .build();
        activity.setCreatedAt(parseIsoDateTime(dto.getFields().getCreated(), "createdAt", externalId));
        activity.setUpdatedAt(parseIsoDateTime(dto.getFields().getUpdated(), "updatedAt", externalId));
        return activity;
    }

    /**
     * Convert GitHub commit to UnifiedActivity.
     */
    public UnifiedActivity githubCommitToUnifiedActivity(GithubCommitDto dto, UUID projectConfigId) {
        String commitMessage = dto.getCommit() != null ? dto.getCommit().getMessage() : null;
        String commitDateRaw = dto.getCommit() != null && dto.getCommit().getAuthor() != null
            ? dto.getCommit().getAuthor().getDate()
            : null;
        String externalId = firstNonBlank(dto.getSha(), "missing-sha");
        String title = firstLine(commitMessage);

        UnifiedActivity activity = UnifiedActivity.builder()
                .projectConfigId(projectConfigId)
                .source(UnifiedActivity.ActivitySource.GITHUB)
                .activityType(UnifiedActivity.ActivityType.COMMIT)
            .externalId(externalId)
            .title(requiredValue(title, externalId, UNIFIED_ACTIVITY_TITLE_MAX_LENGTH))
            .description(buildCommitDescription(dto))
            .authorEmail(truncate(dto.getCommit().getAuthor() != null ? dto.getCommit().getAuthor().getEmail() : null, AUTHOR_EMAIL_MAX_LENGTH))
            .authorName(truncate(dto.getCommit().getAuthor() != null ? dto.getCommit().getAuthor().getName() : null, AUTHOR_NAME_MAX_LENGTH))
            .status("committed")
                .build();
        activity.setCreatedAt(parseIsoDateTime(commitDateRaw, "committedDate", externalId));
        activity.setUpdatedAt(parseIsoDateTime(commitDateRaw, "committedDate", externalId));
        return activity;
    }

    /**
     * Convert Jira issue to JiraIssue entity (denormalized storage).
     */
    public JiraIssue jiraIssueDtoToEntity(JiraIssueDto dto, UUID projectConfigId) {
        // Increment records parsed BEFORE mapping to ensure accurate denominator
        syncMetrics.recordRecordParsed();
        
        JiraIssue issue = JiraIssue.builder()
                .projectConfigId(projectConfigId)
                .issueKey(dto.getKey())
                .issueId(dto.getId())  // Jira numeric ID
                .summary(dto.getFields().getSummary())
                .description(dto.getFields().getDescription())
                .issueType(dto.getFields().getIssueType() != null ? dto.getFields().getIssueType().getName() : null)
                .status(dto.getFields().getStatus() != null ? dto.getFields().getStatus().getName() : null)
                .assigneeEmail(dto.getFields().getAssignee() != null ? dto.getFields().getAssignee().getEmailAddress() : null)
                .assigneeName(dto.getFields().getAssignee() != null ? dto.getFields().getAssignee().getDisplayName() : null)
                .reporterEmail(dto.getFields().getReporter() != null ? dto.getFields().getReporter().getEmailAddress() : null)
                .reporterName(dto.getFields().getReporter() != null ? dto.getFields().getReporter().getDisplayName() : null)
                .priority(dto.getFields().getPriority() != null ? dto.getFields().getPriority().getName() : null)
                .build();
        
        // Set timestamps from Jira API (not sync time)
        if (dto.getFields().getCreated() != null) {
            issue.setCreatedAt(parseIsoDateTime(dto.getFields().getCreated(), "createdAt", dto.getKey()));
        }
        if (dto.getFields().getUpdated() != null) {
            issue.setUpdatedAt(parseIsoDateTime(dto.getFields().getUpdated(), "updatedAt", dto.getKey()));
        }
        
        return issue;
    }

    /**
     * Convert GitHub commit to GithubCommit entity (denormalized storage).
     */
    public GithubCommit githubCommitDtoToEntity(GithubCommitDto dto, UUID projectConfigId) {
    // Increment records parsed BEFORE mapping to ensure accurate denominator
    syncMetrics.recordRecordParsed();
    
    GithubCommit commit = GithubCommit.builder()
            .projectConfigId(projectConfigId)
            .commitSha(firstNonBlank(dto.getSha(), "missing-sha"))
            .message(firstNonBlank(trimToNull(dto.getCommit() != null ? dto.getCommit().getMessage() : null), "(no commit message)"))
            .authorEmail(truncate(dto.getCommit() != null && dto.getCommit().getAuthor() != null ? dto.getCommit().getAuthor().getEmail() : null, AUTHOR_EMAIL_MAX_LENGTH))
            .authorName(truncate(dto.getCommit() != null && dto.getCommit().getAuthor() != null ? dto.getCommit().getAuthor().getName() : null, AUTHOR_NAME_MAX_LENGTH))
            .authorLogin(truncate(dto.getAuthor() != null ? dto.getAuthor().getLogin() : null, AUTHOR_NAME_MAX_LENGTH))
            .additions(dto.getStats() != null && dto.getStats().getAdditions() != null ? dto.getStats().getAdditions() : 0)
            .deletions(dto.getStats() != null && dto.getStats().getDeletions() != null ? dto.getStats().getDeletions() : 0)
            .totalChanges(dto.getStats() != null && dto.getStats().getTotal() != null ? dto.getStats().getTotal() : 0)
            .filesChanged(dto.getFiles() != null ? dto.getFiles().size() : 0)
            .build();
    
    // Set committed_date from GitHub API (not sync time)
    if (dto.getCommit() != null && dto.getCommit().getAuthor() != null && dto.getCommit().getAuthor().getDate() != null) {
        LocalDateTime committedDate = parseIsoDateTime(
                dto.getCommit().getAuthor().getDate(), 
                "committedDate", 
                dto.getSha());
        commit.setCommittedDate(committedDate);
        // Use commit date as both created_at and updated_at (commits are immutable)
        commit.setCreatedAt(committedDate);
        commit.setUpdatedAt(committedDate);
    }
    
    return commit;
}

    /**
     * Map Jira issue type to activity type.
     */
    private UnifiedActivity.ActivityType mapJiraIssueTypeToActivityType(String jiraIssueType) {
        if (jiraIssueType == null) {
            return UnifiedActivity.ActivityType.TASK;
        }

        return switch (jiraIssueType.toLowerCase()) {
            case "bug" -> UnifiedActivity.ActivityType.BUG;
            case "story" -> UnifiedActivity.ActivityType.STORY;
            case "task" -> UnifiedActivity.ActivityType.TASK;
            default -> UnifiedActivity.ActivityType.ISSUE;
        };
    }

    /**
     * Build commit description from stats.
     */
    private String buildCommitDescription(GithubCommitDto dto) {
        String message = dto.getCommit() != null ? trimToNull(dto.getCommit().getMessage()) : null;
        String stats = null;
        if (dto.getStats() != null) {
            int additions = dto.getStats().getAdditions() != null ? dto.getStats().getAdditions() : 0;
            int deletions = dto.getStats().getDeletions() != null ? dto.getStats().getDeletions() : 0;
            stats = String.format("+%d -%d lines", additions, deletions);
        }

        if (message == null) {
            return stats;
        }
        if (stats == null) {
            return message;
        }
        return message + "\n\n" + stats;
    }

    private String firstLine(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        int newline = normalized.indexOf('\n');
        return newline >= 0 ? normalized.substring(0, newline).trim() : normalized;
    }

    private String requiredValue(String primary, String fallback, int maxLength) {
        String value = firstNonBlank(primary, fallback, "[untitled]");
        return truncate(value, maxLength);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String truncate(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Parse ISO 8601 date string to LocalDateTime with production-grade logging.
     * Handles both Jira and GitHub API date formats.
     * Increments parser_warning_count metric when parsing fails and fallback is used.
     * 
     * PRODUCTION LOG FORMAT:
     * "⚠️ Failed to parse {fieldName}. recordId={issueKey/commitSha} rawValue=[{original}]. Fallback to now()."
     * 
     * This ensures alert triage can identify:
     * - Which field is problematic (createdAt/updatedAt/committedDate)
     * - Which record triggered the issue (issueKey or commitSha)
     * - The malformed timestamp value for debugging
     * 
     * @param isoDateString ISO 8601 date string (e.g., "2026-02-22T10:15:30Z" or "2026-02-22T10:15:30.123+0700")
     * @param fieldName Field name for logging (e.g., "createdAt", "updatedAt", "committedDate")
     * @param recordIdentifier Record identifier for logging (issueKey or commitSha)
     * @return LocalDateTime or current time if parsing fails
     */
    private LocalDateTime parseIsoDateTime(String isoDateString, String fieldName, String recordIdentifier) {
        if (isoDateString == null || isoDateString.isBlank()) {
            return null;
        }
        
        try {
            // Remove 'Z' suffix and parse as LocalDateTime (assuming UTC)
            String normalized = isoDateString.replace("Z", "");
            // Remove timezone offset if present (e.g., +07:00)
            if (normalized.contains("+")) {
                normalized = normalized.substring(0, normalized.indexOf('+'));
            }
            if (normalized.contains("-") && normalized.lastIndexOf('-') > 10) {
                normalized = normalized.substring(0, normalized.lastIndexOf('-'));
            }
            
            return LocalDateTime.parse(normalized, ISO_FORMATTER);
        } catch (Exception e) {
            // PRODUCTION-GRADE LOG: Include field name, record identifier, and raw value
            log.warn("⚠️ Failed to parse {}. recordId={} rawValue=[{}]. Fallback to now(). Error: {}", 
                    fieldName, recordIdentifier, isoDateString, e.getMessage());
            
            // Increment parser warning metric for production monitoring
            syncMetrics.recordParserWarning();
            return LocalDateTime.now();
        }
    }
}
