package com.example.syncservice.repository;

import com.example.syncservice.entity.GithubCommit;
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
 * Verifies GithubCommit UPSERT idempotency and committed_date accuracy using REAL PostgreSQL.
 * 
 * CRITICAL: committed_date MUST reflect actual GitHub commit time, NOT sync execution time.
 */
@DataJpaTest(properties = {"spring.main.allow-bean-definition-overriding=true"},
             excludeAutoConfiguration = {FlywayAutoConfiguration.class})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(GithubCommitRepositoryImpl.class)
class GithubCommitUpsertIntegrationTest {

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
    private GithubCommitRepository githubCommitRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        githubCommitRepository.deleteAll();
    }

    /**
     * TEST 1: RETRY IDEMPOTENCY
     * 
     * Scenario: Sync runs twice with identical commit data
     * Expected: No duplicates, no exceptions, row count unchanged
     */
    @Test
    void testRetryIdempotency_NoDuplicates_NoExceptions() {
        // GIVEN: Historical commits (made 3 days ago)
        Long projectConfigId = 1L;
        LocalDateTime historicalCommitDate = LocalDateTime.of(2026, 2, 19, 15, 30);
        
        List<GithubCommit> syncData = List.of(
            buildGithubCommit(projectConfigId, "abc123", "Initial commit", historicalCommitDate),
            buildGithubCommit(projectConfigId, "def456", "Second commit", historicalCommitDate.plusHours(2)),
            buildGithubCommit(projectConfigId, "ghi789", "Third commit", historicalCommitDate.plusHours(4))
        );
        
        // WHEN: First sync run (INSERT)
        int affected1 = githubCommitRepository.upsertBatch(syncData);
        
        // THEN: 3 rows inserted
        assertThat(affected1).isEqualTo(3);
        assertThat(countRowsInDatabase()).isEqualTo(3);
        
        // WHEN: Second sync run with IDENTICAL data (RETRY SCENARIO)
        int affected2 = githubCommitRepository.upsertBatch(syncData);
        
        // THEN: No exception, row count unchanged
        assertThatNoException().isThrownBy(() -> githubCommitRepository.upsertBatch(syncData));
        assertThat(affected2).isEqualTo(3);
        assertThat(countRowsInDatabase())
            .as("Row count must remain 3 after retry (no duplicates)")
            .isEqualTo(3);
        
        // Verify no duplicate SHAs
        Integer duplicateCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM (SELECT project_config_id, commit_sha, COUNT(*) FROM github_commits GROUP BY project_config_id, commit_sha HAVING COUNT(*) > 1) as dups",
            Integer.class
        );
        assertThat(duplicateCount).isEqualTo(0);
    }

    /**
     * TEST 2: COMMITTED_DATE ACCURACY (CRITICAL)
     * 
     * Scenario: committed_date must be actual GitHub commit time, NOT sync time
     * Expected: committed_date is historical, not current time
     */
    @Test
    void testCommittedDateAccuracy_ActualCommitTime_NotSyncTime() {
        // GIVEN: Commit made 5 days ago
        Long projectConfigId = 2L;
        LocalDateTime actualCommitDate = LocalDateTime.of(2026, 2, 17, 10, 15, 30);
        LocalDateTime syncTime = LocalDateTime.now();
        
        GithubCommit commit = buildGithubCommit(projectConfigId, "xyz123", "Historical commit", actualCommitDate);
        
        // WHEN: UPSERT during sync (NOW)
        githubCommitRepository.upsertBatch(List.of(commit));
        
        // THEN: committed_date MUST be actual commit date (NOT sync time)
        GithubCommit saved = githubCommitRepository.findByProjectConfigIdAndCommitSha(projectConfigId, "xyz123")
            .orElseThrow();
        
        assertThat(saved.getCommittedDate())
            .as("CRITICAL: committed_date MUST be GitHub commit time, NOT sync execution time")
            .isEqualTo(actualCommitDate)
            .isBefore(syncTime.minusSeconds(10)); // Must be historical
        
        // Verify timestamps consistent
        assertThat(saved.getCreatedAt()).isEqualTo(actualCommitDate);
        assertThat(saved.getUpdatedAt()).isEqualTo(actualCommitDate);
    }

    /**
     * TEST 3: UPDATE SCENARIO
     * 
     * Scenario: Commit metadata updated (message changed)
     * Expected: Message updates, committed_date preserved
     */
    @Test
    void testUpdateScenario_MessageUpdated_CommittedDatePreserved() {
        // GIVEN: Initial commit
        Long projectConfigId = 3L;
        LocalDateTime commitDate = LocalDateTime.of(2026, 2, 15, 14, 20);
        
        GithubCommit initialCommit = buildGithubCommit(projectConfigId, "update123", 
            "Initial message", commitDate);
        initialCommit.setAdditions(10);
        
        githubCommitRepository.upsertBatch(List.of(initialCommit));
        
        // WHEN: Sync fetches updated commit (amended message, updated stats)
        GithubCommit updatedCommit = buildGithubCommit(projectConfigId, "update123", 
            "AMENDED message", commitDate);
        updatedCommit.setAdditions(15); // Stats changed
        
        githubCommitRepository.upsertBatch(List.of(updatedCommit));
        
        // THEN: Message and stats updated, committed_date preserved
        GithubCommit result = githubCommitRepository.findByProjectConfigIdAndCommitSha(projectConfigId, "update123")
            .orElseThrow();
        
        assertThat(result.getMessage()).isEqualTo("AMENDED message");
        assertThat(result.getAdditions()).isEqualTo(15);
        assertThat(result.getCommittedDate()).isEqualTo(commitDate);
        
        // Verify still only 1 row
        assertThat(countRowsInDatabase()).isEqualTo(1);
    }

    /**
     * TEST 4: MULTIPLE RETRIES
     * 
     * Scenario: Sync runs 5 times with same commit data
     * Expected: Only 1 row exists, data integrity maintained
     */
    @Test
    void testMultipleRetries_DataIntegrityMaintained() {
        Long projectConfigId = 4L;
        LocalDateTime commitDate = LocalDateTime.of(2026, 2, 10, 16, 0);
        String commitSha = "retry999";
        
        // Run sync 5 times
        for (int i = 1; i <= 5; i++) {
            GithubCommit commit = buildGithubCommit(projectConfigId, commitSha, 
                "Commit iteration " + i, commitDate);
            
            githubCommitRepository.upsertBatch(List.of(commit));
        }
        
        // Verify: Only 1 row after 5 runs
        assertThat(countRowsInDatabase())
            .as("Multiple retries should not create duplicate rows")
            .isEqualTo(1);
        
        // Verify: Data reflects last sync
        GithubCommit result = githubCommitRepository.findByProjectConfigIdAndCommitSha(projectConfigId, commitSha)
            .orElseThrow();
        
        assertThat(result.getMessage()).isEqualTo("Commit iteration 5");
        assertThat(result.getCommittedDate()).isEqualTo(commitDate);
    }

    /**
     * TEST 5: NULL COMMITTED_DATE HANDLING
     * 
     * Scenario: External API returns null commit date (edge case)
     * Expected: Fallback applied, no constraint violations
     */
    @Test
    void testNullCommittedDateHandling_NoConstraintViolation_FallbackApplied() {
        // GIVEN: Commit with null committedDate (edge case)
        Long projectConfigId = 5L;
        GithubCommit commitWithNull = GithubCommit.builder()
            .projectConfigId(projectConfigId)
            .commitSha("null123")
            .message("Commit with null date")
            .authorName("John Doe")
            .authorEmail("john@example.com")
            .additions(5)
            .deletions(2)
            .filesChanged(1)
            .build();
        
        // committedDate is null
        
        // WHEN: UPSERT
        assertThatNoException().isThrownBy(() -> 
            githubCommitRepository.upsertBatch(List.of(commitWithNull))
        );
        
        // THEN: No NOT NULL constraint violation
        GithubCommit saved = githubCommitRepository.findByProjectConfigIdAndCommitSha(projectConfigId, "null123")
            .orElseThrow();
        
        assertThat(saved.getCommittedDate())
            .as("Fallback should provide non-null committedDate")
            .isNotNull();
    }

    /**
     * TEST 6: CIRCUIT BREAKER RECOVERY
     * 
     * Scenario: Partial sync → circuit opens → recovery fetches overlapping commits
     * Expected: No constraint violations
     */
    @Test
    void testCircuitBreakerRecovery_NoConstraintViolations() {
        // GIVEN: Partial sync before failure
        Long projectConfigId = 6L;
        LocalDateTime commitDate = LocalDateTime.of(2026, 2, 20, 9, 0);
        
        List<GithubCommit> partialSync = List.of(
            buildGithubCommit(projectConfigId, "cb001", "Commit 1", commitDate),
            buildGithubCommit(projectConfigId, "cb002", "Commit 2", commitDate.plusMinutes(10))
        );
        
        githubCommitRepository.upsertBatch(partialSync);
        assertThat(countRowsInDatabase()).isEqualTo(2);
        
        // WHEN: Recovery sync (overlapping commits)
        List<GithubCommit> recoverySync = List.of(
            buildGithubCommit(projectConfigId, "cb002", "Commit 2", commitDate.plusMinutes(10)), // Duplicate
            buildGithubCommit(projectConfigId, "cb003", "Commit 3", commitDate.plusMinutes(20)), // New
            buildGithubCommit(projectConfigId, "cb004", "Commit 4", commitDate.plusMinutes(30))  // New
        );
        
        // THEN: No exception, 4 total rows
        assertThatNoException().isThrownBy(() -> githubCommitRepository.upsertBatch(recoverySync));
        assertThat(countRowsInDatabase()).isEqualTo(4);
    }

    /**
     * TEST 7: LARGE BATCH
     * 
     * Scenario: Sync 50 commits in one batch
     * Expected: All inserted, retry creates no duplicates
     */
    @Test
    void testLargeBatch_AllInserted_RetryNoDuplicates() {
        // GIVEN: 50 commits
        Long projectConfigId = 7L;
        LocalDateTime baseTime = LocalDateTime.of(2026, 2, 22, 8, 0);
        
        List<GithubCommit> largeBatch = new java.util.ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            largeBatch.add(buildGithubCommit(projectConfigId, "large" + i, 
                "Commit " + i, baseTime.plusMinutes(i)));
        }
        
        // WHEN: First insert
        githubCommitRepository.upsertBatch(largeBatch);
        assertThat(countRowsInDatabase()).isEqualTo(50);
        
        // WHEN: Retry
        githubCommitRepository.upsertBatch(largeBatch);
        
        // THEN: Still 50 rows
        assertThat(countRowsInDatabase())
            .as("Large batch retry should not create duplicates")
            .isEqualTo(50);
    }

    private GithubCommit buildGithubCommit(Long projectConfigId, String commitSha, 
                                          String message, LocalDateTime committedDate) {
        GithubCommit commit = GithubCommit.builder()
            .projectConfigId(projectConfigId)
            .commitSha(commitSha)
            .message(message)
            .authorName("John Doe")
            .authorEmail("john@example.com")
            .additions(10)
            .deletions(5)
            .filesChanged(3)
            .build();
        
        commit.setCommittedDate(committedDate);
        commit.setCreatedAt(committedDate);
        commit.setUpdatedAt(committedDate);
        
        return commit;
    }

    private long countRowsInDatabase() {
        return githubCommitRepository.count();
    }
}

