package com.example.syncservice.service;

import com.example.syncservice.dto.GithubCommitDto;
import com.example.syncservice.dto.JiraIssueDto;
import com.example.syncservice.entity.UnifiedActivity;
import com.example.syncservice.metrics.SyncMetrics;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DataMapperTest {

    private final DataMapper dataMapper = new DataMapper(mock(SyncMetrics.class));

    @Test
    void githubCommitToUnifiedActivity_usesFirstLineForTitle_andFullMessageForDescription() {
        String longFirstLine = "A".repeat(1100);
        String fullMessage = longFirstLine + "\n\nDetailed body line 1\nDetailed body line 2";

        GithubCommitDto dto = GithubCommitDto.builder()
                .sha("abc123")
                .commit(new GithubCommitDto.CommitDetails(
                        fullMessage,
                        new GithubCommitDto.CommitAuthor("Octo Cat", "octo@example.com", "2026-03-09T10:00:00Z")
                ))
                .stats(new GithubCommitDto.Stats(12, 3, 15))
                .build();

        UnifiedActivity activity = dataMapper.githubCommitToUnifiedActivity(dto, UUID.randomUUID());

        assertThat(activity.getExternalId()).isEqualTo("abc123");
        assertThat(activity.getTitle()).hasSize(1000);
        assertThat(activity.getDescription()).contains(fullMessage);
        assertThat(activity.getDescription()).contains("+12 -3 lines");
        assertThat(activity.getAuthorName()).isEqualTo("Octo Cat");
        assertThat(activity.getAuthorEmail()).isEqualTo("octo@example.com");
        assertThat(activity.getCreatedAt()).isNotNull();
    }

    @Test
    void jiraIssueToUnifiedActivity_fallsBackWhenSummaryMissing() {
        JiraIssueDto.Fields fields = new JiraIssueDto.Fields();
        fields.setSummary(null);
        fields.setDescription("Description");
        fields.setCreated("2026-03-09T10:00:00Z");
        fields.setUpdated("2026-03-09T10:05:00Z");

        JiraIssueDto dto = JiraIssueDto.builder()
                .key("SAMT-123")
                .id("10001")
                .fields(fields)
                .build();

        UnifiedActivity activity = dataMapper.jiraIssueToUnifiedActivity(dto, UUID.randomUUID());

        assertThat(activity.getExternalId()).isEqualTo("SAMT-123");
        assertThat(activity.getTitle()).isEqualTo("SAMT-123");
        assertThat(activity.getDescription()).isEqualTo("Description");
        assertThat(activity.getCreatedAt()).isNotNull();
        assertThat(activity.getUpdatedAt()).isNotNull();
    }
}