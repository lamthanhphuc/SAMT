package com.example.analysisservice.service;

import com.example.analysisservice.AnalysisServiceApplication;
import com.example.analysisservice.dto.response.AiStructuredResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ContextConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LIVE integration test against a running Ollama instance.
 *
 * Enable with:
 * - LIVE_AI=1
 * - AI_BASE_URL=http://localhost:11434  (or wherever Ollama is reachable)
 */
@SpringBootTest
@ContextConfiguration(classes = AnalysisServiceApplication.class)
@EnabledIfEnvironmentVariable(named = "LIVE_AI", matches = "1")
class LocalAiServiceLiveIT {

    @Autowired
    private AiService aiService;

    @Test
    void generateSrsStructured_shouldReturnSchemaValidStructuredJson() {
        // Minimal but "measurable" evidence. The extractor prompt requires SHALL + measurable acceptance criteria.
        String evidenceJson = """
                [
                  {
                    "sourceType": "jira",
                    "sourceId": "JIRA-123",
                    "timestamp": "2026-03-01T10:00:00Z",
                    "content": "The system SHALL allow an admin to generate an SRS report within 30 seconds for up to 50 evidence items."
                  },
                  {
                    "sourceType": "commit",
                    "sourceId": "abc123",
                    "timestamp": "2026-03-01T11:00:00Z",
                    "content": "Add report generation endpoint. Non-functional: API SHALL handle at least 10 requests/sec under normal load."
                  }
                ]
                """;

        AiStructuredResponse result = aiService.generateSrsStructured(evidenceJson, true);

        assertThat(result).isNotNull();
        assertThat(result.getSrsContent()).isNotBlank();
        assertThat(result.getRequirements()).isNotNull();
        assertThat(result.getRequirements()).isNotEmpty();
        assertThat(result.getRequirements().getFirst().getId()).isNotBlank();
        assertThat(result.getRequirements().getFirst().getSourceRefs()).isNotNull();
        assertThat(result.getRequirements().getFirst().getSourceRefs()).isNotEmpty();
    }
}

