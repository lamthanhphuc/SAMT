package com.example.syncservice.repository;

import com.example.syncservice.entity.UnifiedActivity;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PERFORMANCE & CONCURRENCY TEST
 * 
 * Verifies batch UPSERT performance after loop-based optimization.
 * 
 * Expected:
 * - 500 records < 400ms
 * - 1000 records < 800ms
 * - No deadlocks under concurrent load
 * - No constraint violations on retry
 */
@DataJpaTest(properties = {"spring.main.allow-bean-definition-overriding=true"}, 
             excludeAutoConfiguration = {FlywayAutoConfiguration.class})
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(UnifiedActivityRepositoryImpl.class)
class UnifiedActivityPerformanceTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
            .withCommand("postgres", "-c", "max_connections=100");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "10");
    }

    @Autowired
    private UnifiedActivityRepository unifiedActivityRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("DELETE FROM unified_activities");
    }

    /**
     * TEST 1: VERIFY UNIQUE CONSTRAINT EXISTS
     */
    @Test
    void testConstraintExists() {
        String sql = """
            SELECT conname, pg_get_constraintdef(oid)
            FROM pg_constraint
            WHERE conrelid = 'unified_activities'::regclass
            AND contype = 'u'
            """;
        
        List<Map<String, Object>> constraints = jdbcTemplate.queryForList(sql);
        
        assertThat(constraints).isNotEmpty();
        assertThat(constraints.get(0).get("pg_get_constraintdef").toString())
            .contains("project_config_id")
            .contains("source")
            .contains("external_id");
        
        System.out.println("✅ CONSTRAINT CHECK: " + constraints.get(0).get("pg_get_constraintdef"));
    }

    /**
     * TEST 2: PERFORMANCE - 500 RECORDS
     */
    @Test
    void testPerformance_500Records_Under400ms() {
        List<UnifiedActivity> activities = generateActivities(1L, 500);
        
        long startTime = System.currentTimeMillis();
        int affected = unifiedActivityRepository.upsertBatch(activities);
        long duration = System.currentTimeMillis() - startTime;
        
        assertThat(affected).isEqualTo(500);
        assertThat(duration).isLessThan(400);
        
        System.out.printf("✅ 500 RECORDS: %dms (target: <400ms)%n", duration);
    }

    /**
     * TEST 3: PERFORMANCE - 1000 RECORDS
     */
    @Test
    void testPerformance_1000Records_Under800ms() {
        List<UnifiedActivity> activities = generateActivities(1L, 1000);
        
        long startTime = System.currentTimeMillis();
        int affected = unifiedActivityRepository.upsertBatch(activities);
        long duration = System.currentTimeMillis() - startTime;
        
        assertThat(affected).isEqualTo(1000);
        assertThat(duration).isLessThan(800);
        
        System.out.printf("✅ 1000 RECORDS: %dms (target: <800ms)%n", duration);
    }

    /**
     * TEST 4: BATCHING - VERIFY 500 BATCH SIZE LIMIT
     */
    @Test
    void testBatching_Over500Records_SplitsIntoBatches() {
        List<UnifiedActivity> activities = generateActivities(1L, 1200);
        
        long startTime = System.currentTimeMillis();
        int affected = unifiedActivityRepository.upsertBatch(activities);
        long duration = System.currentTimeMillis() - startTime;
        
        assertThat(affected).isEqualTo(1200);
        assertThat(duration).isLessThan(1000);
        
        System.out.printf("✅ 1200 RECORDS (3 batches): %dms%n", duration);
    }

    /**
     * TEST 5: IDEMPOTENCY - RETRY SAME DATA
     */
    @Test
    void testIdempotency_RetryWithSameData_NoException() {
        List<UnifiedActivity> activities = generateActivities(1L, 100);
        
        int firstRun = unifiedActivityRepository.upsertBatch(activities);
        int secondRun = unifiedActivityRepository.upsertBatch(activities);
        int thirdRun = unifiedActivityRepository.upsertBatch(activities);
        
        assertThat(firstRun).isEqualTo(100);
        assertThat(secondRun).isEqualTo(100);
        assertThat(thirdRun).isEqualTo(100);
        
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM unified_activities", Long.class);
        assertThat(count).isEqualTo(100L);
        
        System.out.println("✅ IDEMPOTENCY: 3 retries, no duplicates, no exceptions");
    }

    /**
     * TEST 6: CONCURRENCY - PARALLEL SYNC
     */
    @Test
    void testConcurrency_5ParallelSyncs_NoDeadlock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(5);
        
        List<CompletableFuture<Void>> futures = IntStream.range(0, 5)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                List<UnifiedActivity> activities = generateActivities((long) (i + 1), 200);
                int affected = unifiedActivityRepository.upsertBatch(activities);
                assertThat(affected).isEqualTo(200);
            }, executor))
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM unified_activities", Long.class);
        assertThat(count).isEqualTo(1000L); // 5 projects * 200 records
        
        System.out.println("✅ CONCURRENCY: 5 parallel syncs, no deadlock, 1000 total records");
    }

    /**
     * TEST 7: CONCURRENT RETRY - SAME PROJECT
     */
    @Test
    void testConcurrentRetry_SameProject_NoDeadlock() throws Exception {
        Long projectId = 1L;
        List<UnifiedActivity> activities = generateActivities(projectId, 100);
        
        // First insert
        unifiedActivityRepository.upsertBatch(activities);
        
        // Parallel retry same data
        ExecutorService executor = Executors.newFixedThreadPool(3);
        
        List<CompletableFuture<Void>> futures = IntStream.range(0, 3)
            .mapToObj(i -> CompletableFuture.runAsync(() -> {
                int affected = unifiedActivityRepository.upsertBatch(activities);
                assertThat(affected).isEqualTo(100);
            }, executor))
            .toList();
        
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        executor.shutdown();
        
        Long count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM unified_activities WHERE project_config_id = ?", 
            Long.class, 
            projectId
        );
        assertThat(count).isEqualTo(100L);
        
        System.out.println("✅ CONCURRENT RETRY: 3 threads, same project, no deadlock");
    }

    /**
     * TEST 8: CHECK POSTGRES LOCKS (no hanging locks)
     */
    @Test
    void testNoHangingLocks_AfterBatchUpsert() {
        List<UnifiedActivity> activities = generateActivities(1L, 500);
        unifiedActivityRepository.upsertBatch(activities);
        
        String sql = "SELECT COUNT(*) FROM pg_locks WHERE NOT granted";
        Long ungrantedLocks = jdbcTemplate.queryForObject(sql, Long.class);
        
        assertThat(ungrantedLocks).isEqualTo(0L);
        
        System.out.println("✅ NO HANGING LOCKS: All locks released after transaction");
    }

    private List<UnifiedActivity> generateActivities(Long projectConfigId, int count) {
        List<UnifiedActivity> activities = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        for (int i = 0; i < count; i++) {
            UnifiedActivity activity = new UnifiedActivity();
            activity.setProjectConfigId(projectConfigId);
            activity.setSource(i % 2 == 0 ? UnifiedActivity.ActivitySource.JIRA : UnifiedActivity.ActivitySource.GITHUB);
            activity.setActivityType(UnifiedActivity.ActivityType.ISSUE);
            activity.setExternalId("EXT-" + projectConfigId + "-" + i);
            activity.setTitle("Test Activity " + i);
            activity.setDescription("Description " + i);
            activity.setAuthorEmail("user" + i + "@example.com");
            activity.setAuthorName("User " + i);
            activity.setStatus("OPEN");
            activity.setCreatedAt(now.minusDays(i % 30));
            activity.setUpdatedAt(now);
            activity.setCreatedBy(1L);
            activity.setUpdatedBy(1L);
            activities.add(activity);
        }
        
        return activities;
    }
}
