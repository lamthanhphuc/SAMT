package com.example.syncservice.repository;

import com.example.syncservice.entity.JiraIssue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * PRODUCTION GATE INTEGRATION TEST
 * 
 * Verifies JiraIssue UPSERT idempotency using REAL PostgreSQL database.
 * 
 * CRITICAL: Must pass before production deployment.
 */
@DataJpaTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, 
             excludeAutoConfiguration = {FlywayAutoConfiguration.class})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JiraIssueRepositoryImpl.class)
class JiraIssueUpsertIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private JiraIssueRepository jiraIssueRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jiraIssueRepository.deleteAll();
    }

    /**
     * TEST 1: RETRY IDEMPOTENCY
     * 
     * Scenario: Sync runs twice with identical external data
     * Expected: No duplicates, no exceptions, row count unchanged
     */
    @Test
    void testRetryIdempotency_NoDuplicates_NoExceptions_RowCountUnchanged() {
        // GIVEN: External Jira API data with timestamp T1
        Long projectConfigId = 1L;
        LocalDateTime externalCreatedAt = LocalDateTime.of(2026, 1, 15, 10, 0);
        LocalDateTime externalUpdatedAt_T1 = LocalDateTime.of(2026, 2, 20, 14, 30);
        
        List<JiraIssue> syncData = List.of(
            buildJiraIssue(projectConfigId, "PROJ-101", "10001", "First issue", externalCreatedAt, externalUpdatedAt_T1),
            buildJiraIssue(projectConfigId, "PROJ-102", "10002", "Second issue", externalCreatedAt, externalUpdatedAt_T1),
            buildJiraIssue(projectConfigId, "PROJ-103", "10003", "Third issue", externalCreatedAt, externalUpdatedAt_T1)
        );
        
        // WHEN: First sync run (INSERT)
        int affected1 = jiraIssueRepository.upsertBatch(syncData);
        
        // THEN: 3 rows inserted
        assertThat(affected1).isEqualTo(3);
        assertThat(countRowsInDatabase()).isEqualTo(3);
        
        // WHEN: Second sync run with IDENTICAL data (RETRY SCENARIO)
        int affected2 = jiraIssueRepository.upsertBatch(syncData);
        
        // THEN: No exception thrown, row count unchanged
        assertThatNoException().isThrownBy(() -> jiraIssueRepository.upsertBatch(syncData));
        assertThat(affected2).isEqualTo(3);
        assertThat(countRowsInDatabase())
            .as("Row count must remain 3 after retry (no duplicates)")
            .isEqualTo(3);
        
        // Verify no duplicate keys exist
        Integer duplicateCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM (SELECT project_config_id, issue_key, COUNT(*) as cnt FROM jira_issues GROUP BY project_config_id, issue_key HAVING COUNT(*) > 1) as dups",
            Integer.class
        );
        assertThat(duplicateCount).isEqualTo(0);
    }

    /**
     * TEST 2: EXTERNAL TIMESTAMP PRESERVED
     * 
     * Scenario: updated_at must reflect Jira's timestamp, NOT sync execution time
     * Expected: updated_at = external timestamp from API
     */
    @Test
    void testExternalTimestampPreserved_UpdatedAtFromJiraAPI_NotSyncTime() {
        // GIVEN: Jira issue with external timestamp (3 days ago)
        Long projectConfigId = 2L;
        LocalDateTime externalUpdatedAt = LocalDateTime.of(2026, 2, 19, 10, 15, 30);
        LocalDateTime syncTime = LocalDateTime.now();
        
        JiraIssue issue = buildJiraIssue(projectConfigId, "TEST-001", "20001", 
            "Test issue", externalUpdatedAt.minusDays(5), externalUpdatedAt);
        
        // WHEN: UPSERT
        jiraIssueRepository.upsertBatch(List.of(issue));
        
        // THEN: updated_at must be external timestamp (NOT current time)
        JiraIssue saved = jiraIssueRepository.findByProjectConfigIdAndIssueKey(projectConfigId, "TEST-001")
            .orElseThrow();
        
        assertThat(saved.getUpdatedAt())
            .as("updated_at MUST be Jira's timestamp, not sync execution time")
            .isEqualTo(externalUpdatedAt)
            .isBefore(syncTime.minusSeconds(10)); // Should be historical
    }

    /**
     * TEST 3: UPDATE SCENARIO
     * 
     * Scenario: Sync fetches updated Jira data with new timestamp
     * Expected: Data updates, updated_at changes, created_at preserved
     */
    @Test
    void testUpdateScenario_DataUpdated_UpdatedAtChanged_CreatedAtPreserved() {
        // GIVEN: Initial sync with T1
        Long projectConfigId = 3L;
        LocalDateTime createdAt = LocalDateTime.of(2026, 1, 10, 9, 0);
        LocalDateTime updatedAt_T1 = LocalDateTime.of(2026, 2, 15, 10, 0);
        
        JiraIssue initialIssue = buildJiraIssue(projectConfigId, "UPDATE-001", "30001", 
            "Original summary", createdAt, updatedAt_T1);
        initialIssue.setStatus("To Do");
        
        jiraIssueRepository.upsertBatch(List.of(initialIssue));
        
        // WHEN: Second sync with UPDATED data and timestamp T2
        LocalDateTime updatedAt_T2 = LocalDateTime.of(2026, 2, 22, 16, 45);
        JiraIssue updatedIssue = buildJiraIssue(projectConfigId, "UPDATE-001", "30001", 
            "UPDATED summary", createdAt, updatedAt_T2);
        updatedIssue.setStatus("In Progress");
        
        jiraIssueRepository.upsertBatch(List.of(updatedIssue));
        
        // THEN: Data updated, timestamps correct
        JiraIssue result = jiraIssueRepository.findByProjectConfigIdAndIssueKey(projectConfigId, "UPDATE-001")
            .orElseThrow();
        
        assertThat(result.getSummary()).isEqualTo("UPDATED summary");
        assertThat(result.getStatus()).isEqualTo("In Progress");
        assertThat(result.getUpdatedAt()).isEqualTo(updatedAt_T2);
        assertThat(result.getCreatedAt())
            .as("created_at must be preserved from first insert")
            .isEqualTo(createdAt);
        
        // Verify still only 1 row
        assertThat(countRowsInDatabase()).isEqualTo(1);
    }

    /**
     * TEST 4: CIRCUIT BREAKER RECOVERY
     * 
     * Scenario: Partial sync inserts 2 issues, then circuit opens.
     *           Recovery re-fetches same 2 + 1 new issue.
     * Expected: No constraint violations, total 3 rows
     */
    @Test
    void testCircuitBreakerRecovery_PartialFailureThenRetry_NoConstraintViolations() {
        // GIVEN: Partial sync before circuit breaker opened
        Long projectConfigId = 4L;
        LocalDateTime timestamp = LocalDateTime.of(2026, 2, 22, 10, 0);
        
        List<JiraIssue> partialSync = List.of(
            buildJiraIssue(projectConfigId, "CB-001", "40001", "Issue 1", timestamp, timestamp),
            buildJiraIssue(projectConfigId, "CB-002", "40002", "Issue 2", timestamp, timestamp)
        );
        
        jiraIssueRepository.upsertBatch(partialSync);
        assertThat(countRowsInDatabase()).isEqualTo(2);
        
        // WHEN: Circuit breaker recovery - re-fetch same 2 + fetch new 1
        List<JiraIssue> recoverySync = List.of(
            buildJiraIssue(projectConfigId, "CB-001", "40001", "Issue 1", timestamp, timestamp), // Duplicate
            buildJiraIssue(projectConfigId, "CB-002", "40002", "Issue 2", timestamp, timestamp), // Duplicate
            buildJiraIssue(projectConfigId, "CB-003", "40003", "Issue 3", timestamp, timestamp)  // New
        );
        
        // THEN: No exception, 3 total rows
        assertThatNoException().isThrownBy(() -> jiraIssueRepository.upsertBatch(recoverySync));
        assertThat(countRowsInDatabase()).isEqualTo(3);
    }

    /**
     * TEST 5: NULL TIMESTAMP HANDLING
     * 
     * Scenario: External API returns null timestamps
     * Expected: Fallback to current time, no constraint violations
     */
    @Test
    void testNullTimestampHandling_NoConstraintViolation_FallbackApplied() {
        // GIVEN: Issue with null timestamps (edge case)
        Long projectConfigId = 5L;
        JiraIssue issueWithNullTimestamps = JiraIssue.builder()
            .projectConfigId(projectConfigId)
            .issueKey("NULL-001")
            .issueId("50001")
            .summary("Issue with null timestamps")
            .description("Test")
            .issueType("Bug")
            .status("Open")
            .priority("High")
            .build();
        
        // createdAt and updatedAt are null
        
        // WHEN: UPSERT
        assertThatNoException().isThrownBy(() -> 
            jiraIssueRepository.upsertBatch(List.of(issueWithNullTimestamps))
        );
        
        // THEN: No NOT NULL constraint violation
        JiraIssue saved = jiraIssueRepository.findByProjectConfigIdAndIssueKey(projectConfigId, "NULL-001")
            .orElseThrow();
        
        assertThat(saved.getCreatedAt())
            .as("Fallback should provide non-null createdAt")
            .isNotNull();
        assertThat(saved.getUpdatedAt())
            .as("Fallback should provide non-null updatedAt")
            .isNotNull();
    }

    /**
     * TEST 6: LARGE BATCH
     * 
     * Scenario: Sync 100 issues in one batch
     * Expected: All inserted, retry creates no duplicates
     */
    @Test
    void testLargeBatch_AllInserted_RetryNoDuplicates() {
        // GIVEN: 100 Jira issues
        Long projectConfigId = 6L;
        LocalDateTime timestamp = LocalDateTime.of(2026, 2, 22, 12, 0);
        
        List<JiraIssue> largeBatch = new java.util.ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            largeBatch.add(buildJiraIssue(projectConfigId, "LARGE-" + i, "6000" + i, 
                "Issue " + i, timestamp, timestamp));
        }
        
        // WHEN: First insert
        jiraIssueRepository.upsertBatch(largeBatch);
        assertThat(countRowsInDatabase()).isEqualTo(100);
        
        // WHEN: Retry
        jiraIssueRepository.upsertBatch(largeBatch);
        
        // THEN: Still 100 rows
        assertThat(countRowsInDatabase())
            .as("Large batch retry should not create duplicates")
            .isEqualTo(100);
    }

    private JiraIssue buildJiraIssue(Long projectConfigId, String issueKey, String issueId, 
                                     String summary, LocalDateTime createdAt, LocalDateTime updatedAt) {
        JiraIssue issue = JiraIssue.builder()
            .projectConfigId(projectConfigId)
            .issueKey(issueKey)
            .issueId(issueId)
            .summary(summary)
            .description("Test description")
            .issueType("Task")
            .status("To Do")
            .priority("Medium")
            .assigneeEmail("assignee@example.com")
            .reporterEmail("reporter@example.com")
            .build();
        
        issue.setCreatedAt(createdAt);
        issue.setUpdatedAt(updatedAt);
        
        return issue;
    }

    private long countRowsInDatabase() {
        return jiraIssueRepository.count();
    }
}

