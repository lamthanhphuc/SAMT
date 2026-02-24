# Implementation Guide – Sync Service

**Framework:** Spring Boot 3.2+  
**Java Version:** 21+  
**Database:** PostgreSQL 15+  
**Date:** February 23, 2026

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Configuration](#configuration)
3. [Module Structure](#module-structure)
4. [Sync Execution Flow](#sync-execution-flow)
5. [Transaction Boundaries](#transaction-boundaries)
6. [Concurrency Model](#concurrency-model)
7. [Batch Processing Strategy](#batch-processing-strategy)
8. [Resilience Strategy](#resilience-strategy)
9. [Scheduler & ShedLock](#scheduler--shedlock)
10. [Performance Characteristics](#performance-characteristics)
11. [Design Decisions](#design-decisions)
12. [Known Limitations](#known-limitations)

---

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+
- PostgreSQL 15+
- Docker (optional)

### Build & Run

```bash
# Build
mvn clean install

# Run locally
mvn spring-boot:run

# Run with Docker
docker build -t sync-service:1.0 .
docker run -p 8084:8080 sync-service:1.0
```

### Health Check

```bash
curl http://localhost:8084/actuator/health
```

---

## Configuration

### Environment Variables

**Required:**

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `DATABASE_URL` | PostgreSQL connection URL | - | `jdbc:postgresql://localhost:5436/sync_db` |
| `DATABASE_USERNAME` | Database username | `postgres` | `sync_user` |
| `DATABASE_PASSWORD` | Database password | - | `s3cur3_p4ssw0rd` |
| `SERVICE_AUTH_KEY` | gRPC service authentication key | - | `sync-service-secret-key-2026` |

**Optional:**

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP server port | `8084` |
| `DB_POOL_SIZE` | Max database connections | `20` |
| `SYNC_SCHEDULER_ENABLED` | Enable/disable scheduler | `true` |
| `SYNC_JIRA_ISSUES_CRON` | Jira issues sync cron | `0 */30 * * * *` |
| `SYNC_GITHUB_COMMITS_CRON` | GitHub commits sync cron | `0 */15 * * * *` |
| `JIRA_TIMEOUT` | Jira API timeout (seconds) | `30` |
| `GITHUB_TIMEOUT` | GitHub API timeout (seconds) | `30` |

### application.yml

```yaml
server:
  port: ${SERVER_PORT:8084}
  shutdown: graceful

spring:
  application:
    name: sync-service
    
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5436/sync_db}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:12345}
    driver-class-name: org.postgresql.Driver
    hikari:
      connection-timeout: 30000
      maximum-pool-size: ${DB_POOL_SIZE:20}
      minimum-idle: 5
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000
      connection-test-query: SELECT 1
      
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC
          batch_size: 20
        order_inserts: true
        order_updates: true
        
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration
    validate-on-migrate: true

# gRPC Client
grpc:
  client:
    project-config-service:
      address: static://${PROJECT_CONFIG_SERVICE_GRPC_HOST:localhost}:${PROJECT_CONFIG_SERVICE_GRPC_PORT:9093}
      negotiation-type: plaintext
      enable-keep-alive: true
      keep-alive-time: 30s
      keep-alive-timeout: 10s
      deadline: 5s

# Service Authentication
service:
  auth:
    service-name: sync-service
    service-key: ${SERVICE_AUTH_KEY:s3cr3t_k3y_ch4ng3_1n_pr0duct10n}

# Scheduler
sync:
  scheduler:
    enabled: ${SYNC_SCHEDULER_ENABLED:true}
    jira-issues-cron: ${SYNC_JIRA_ISSUES_CRON:0 */30 * * * *}        # Every 30 minutes
    jira-sprints-cron: ${SYNC_JIRA_SPRINTS_CRON:0 0 */2 * * *}       # Every 2 hours
    github-commits-cron: ${SYNC_GITHUB_COMMITS_CRON:0 */15 * * * *}  # Every 15 minutes
    github-prs-cron: ${SYNC_GITHUB_PRS_CRON:0 0 * * * *}             # Every 1 hour
    
  async:
    core-pool-size: 2
    max-pool-size: 5
    queue-capacity: 100
    thread-name-prefix: sync-

# External APIs
external-api:
  jira:
    timeout-seconds: ${JIRA_TIMEOUT:30}
  github:
    timeout-seconds: ${GITHUB_TIMEOUT:30}

# Resilience4j
resilience4j:
  retry:
    instances:
      externalApi:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - java.net.ConnectException
          - java.net.SocketTimeoutException
          - org.springframework.web.client.ResourceAccessException
          
  circuitbreaker:
    instances:
      projectConfigGrpc:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        minimumNumberOfCalls: 5

# Actuator
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

---

## Module Structure

### Package Organization

```
com.example.syncservice/
├── adapter/                   # Data transformation
│   ├── JiraIssueAdapter
│   ├── JiraSprintAdapter
│   ├── GithubCommitAdapter
│   └── GithubPullRequestAdapter
│
├── client/                    # External API clients
│   ├── JiraApiClient
│   ├── GithubApiClient
│   └── grpc/
│       └── ProjectConfigGrpcClient
│
├── config/                    # Spring configuration
│   ├── AsyncConfig
│   ├── RestTemplateConfig
│   └── ShedLockConfig
│
├── entity/                    # JPA entities
│   ├── SyncJob
│   ├── UnifiedActivity
│   ├── JiraIssue
│   ├── JiraSprint
│   ├── GithubCommit
│   └── GithubPullRequest
│
├── repository/                # Data access
│   ├── SyncJobRepository
│   ├── UnifiedActivityRepository
│   ├── JiraIssueRepositoryImpl (custom UPSERT)
│   └── GithubCommitRepositoryImpl (custom UPSERT)
│
├── scheduler/                 # Scheduled jobs
│   ├── SyncScheduler
│   ├── CleanupScheduler
│   └── HealthCheckScheduler
│
└── service/                   # Business logic
    ├── SyncExecutor
    ├── SyncValidationService
    └── CorrelationIdGenerator
```

### Design Patterns

**Adapter Pattern:**
- Each data source (Jira, GitHub) has dedicated adapter
- Converts raw JSON → common data model (UnifiedActivity)
- Implements `DataExtractor<T>` interface

**Strategy Pattern:**
- Different sync strategies for different data types
- Configurable execution intervals
- Pluggable retry/fallback strategies

**Repository Pattern:**
- Custom UPSERT implementation for batch operations
- EntityManager for batch flush/clear
- Parameterized queries to prevent SQL injection

---

## Sync Execution Flow

### Overall Flow

```
1. Scheduler triggers (e.g., every 30 minutes)
2. Fetch verified configs via gRPC (ProjectConfig Service)
3. For each config:
   a. Get decrypted tokens via gRPC
   b. Fetch data from external API (Jira/GitHub)
   c. Transform data via adapter
   d. Batch UPSERT to database
   e. Update sync job status
4. Aggregate results and log summary
```

### Sequence Diagram

```
Scheduler → ProjectConfigClient: listVerifiedConfigs()
ProjectConfigClient → Scheduler: List<ConfigSummary>

Scheduler → SyncExecutor: syncJiraIssuesAsync(configId)
SyncExecutor → ProjectConfigClient: getDecryptedConfig(configId)
ProjectConfigClient → SyncExecutor: DecryptedConfigDto

SyncExecutor → JiraApiClient: fetchIssues(url, token)
JiraApiClient → SyncExecutor: List<JiraIssueDto>

SyncExecutor → JiraIssueAdapter: extractActivities(issues)
JiraIssueAdapter → SyncExecutor: List<UnifiedActivity>

SyncExecutor → Repository: batchUpsert(activities)
Repository → Database: INSERT ... ON CONFLICT DO UPDATE
Database → Repository: success

SyncExecutor → SyncJobRepository: updateStatus(COMPLETED)
```

---

## Transaction Boundaries

### Critical Rule: External API Calls OUTSIDE @Transactional

**Problem:** Long-running external API calls inside transactions cause database lock contention.

**Solution:** Separate transaction boundaries

#### ❌ Wrong Pattern

```java
@Transactional
public void syncJiraIssues(Long configId) {
    // BAD: Holding transaction while calling external API
    List<JiraIssueDto> issues = jiraApiClient.fetchIssues(...); // 10-30 seconds
    
    // Database write (transaction still open)
    repository.saveAll(issues);
}
```

#### ✅ Correct Pattern

```java
// NO @Transactional here
public void syncJiraIssues(Long configId) {
    // External API call (no transaction)
    List<JiraIssueDto> issues = jiraApiClient.fetchIssues(...);
    
    // Transform data (no transaction)
    List<UnifiedActivity> activities = adapter.extractActivities(issues);
    
    // Only database writes in transaction
    saveActivities(activities);
}

@Transactional
private void saveActivities(List<UnifiedActivity> activities) {
    repository.batchUpsert(activities);
}
```

### Transaction Configuration

```java
@Transactional(
    propagation = Propagation.REQUIRED,
    isolation = Isolation.READ_COMMITTED,
    timeout = 60  // 60 seconds max
)
public void batchUpsert(List<JiraIssue> issues) {
    // Single transaction for batch operation
}
```

---

## Concurrency Model

### Thread Pool Configuration

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "syncTaskExecutor")
    public Executor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        executor.setCorePoolSize(2);         // Baseline: 2 threads
        executor.setMaxPoolSize(5);          // Peak: 5 threads
        executor.setQueueCapacity(100);      // Queue 100 configs
        executor.setThreadNamePrefix("sync-");
        
        // Rejection policy: throw exception (tracked)
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        
        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // MDC propagation
        executor.setTaskDecorator(new MdcTaskDecorator());
        
        executor.initialize();
        return executor;
    }
}
```

### Async Execution

```java
@Service
public class SyncExecutor {
    
    @Async("syncTaskExecutor")
    public CompletableFuture<SyncResult> syncJiraIssuesAsync(Long configId) {
        // MDC context
        MDC.put("configId", String.valueOf(configId));
        MDC.put("jobType", "JIRA_ISSUES");
        
        try {
            // Sync logic
            return CompletableFuture.completedFuture(SyncResult.success(configId));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(SyncResult.failure(configId, e));
        } finally {
            MDC.clear();
        }
    }
}
```

### Backpressure Handling

**When queue is full (100 configs pending):**
1. AbortPolicy throws `RejectedExecutionException`
2. Exception caught and logged
3. Metric `sync_jobs_rejected_total` incremented
4. Config will retry on next cycle

---

## Batch Processing Strategy

### True Batch UPSERT

**Problem:** N individual INSERT/UPDATE queries = slow

**Solution:** Single multi-row INSERT with ON CONFLICT

#### Implementation

```java
@Repository
public class JiraIssueRepositoryImpl {
    
    @PersistenceContext
    private EntityManager entityManager;
    
    @Transactional
    public void batchUpsert(List<JiraIssue> issues) {
        int batchSize = 500;
        
        for (int i = 0; i < issues.size(); i += batchSize) {
            int end = Math.min(i + batchSize, issues.size());
            List<JiraIssue> batch = issues.subList(i, end);
            
            upsertBatch(batch);
            
            entityManager.flush();
            entityManager.clear();  // Prevent memory leak
        }
    }
    
    private void upsertBatch(List<JiraIssue> batch) {
        StringBuilder sql = new StringBuilder(
            "INSERT INTO jira_issues (project_config_id, issue_key, summary, ...) VALUES ");
        
        for (int i = 0; i < batch.size(); i++) {
            if (i > 0) sql.append(", ");
            sql.append("(?, ?, ?, ...)");
        }
        
        sql.append(" ON CONFLICT (project_config_id, issue_key) ");
        sql.append("DO UPDATE SET summary = EXCLUDED.summary, status = EXCLUDED.status");
        
        Query query = entityManager.createNativeQuery(sql.toString());
        
        int paramIndex = 1;
        for (JiraIssue issue : batch) {
            query.setParameter(paramIndex++, issue.getProjectConfigId());
            query.setParameter(paramIndex++, issue.getIssueKey());
            query.setParameter(paramIndex++, issue.getSummary());
            // ... more parameters
        }
        
        query.executeUpdate();
    }
}
```

### Performance Comparison

| Records | Before (N queries) | After (1 query) | Improvement |
|---------|-------------------|-----------------|-------------|
| 500     | 5-10s             | <0.5s           | 10-20x      |
| 1000    | 10-20s            | <2s             | 5-10x       |
| 5000    | 50-100s           | <5s             | 10-20x      |

---

## Resilience Strategy

### Retry Configuration

```java
@Retry(name = "externalApi", fallbackMethod = "fetchIssuesFallback")
public List<JiraIssueDto> fetchIssues(String hostUrl, String token) {
    // API call
}

private List<JiraIssueDto> fetchIssuesFallback(String hostUrl, String token, Exception e) {
    log.warn("Fallback: Failed to fetch Jira issues: {}", e.getMessage());
    return Collections.emptyList();
}
```

**Configuration:**
- Max attempts: 3
- Wait duration: 1 second
- Exponential backoff: 2x multiplier
- Retry exceptions: ConnectException, SocketTimeoutException

### Circuit Breaker

```java
@CircuitBreaker(name = "projectConfigGrpc", fallbackMethod = "getConfigFallback")
public DecryptedConfigDto getDecryptedConfig(Long configId) {
    // gRPC call
}

private DecryptedConfigDto getConfigFallback(Long configId, Exception e) {
    log.error("Circuit breaker open: Cannot get config {}", configId);
    throw new ServiceUnavailableException("ProjectConfig Service unavailable");
}
```

**Configuration:**
- Sliding window: 10 calls
- Failure threshold: 50%
- Wait duration: 10 seconds
- Minimum calls: 5

### Graceful Degradation

**Fallback Strategy:**
- External API failure → Return empty list (not exception)
- Mark sync job status as `PARTIAL_FAILURE`
- Log detailed error for debugging
- Continue processing other configs

---

## Scheduler & ShedLock

### ShedLock Configuration

**Purpose:** Prevent duplicate execution in multi-instance deployment

```java
@Configuration
@EnableSchedulerLock(defaultLockAtMostFor = "PT25M")
public class ShedLockConfig {
    
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
            JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .usingDbTime()  // Clock-skew immune
                .build()
        );
    }
}
```

### Scheduled Jobs

```java
@Service
public class SyncScheduler {
    
    @Scheduled(cron = "${sync.scheduler.jira-issues-cron}")
    @SchedulerLock(
        name = "syncJiraIssues",
        lockAtMostFor = "PT25M",     // 25 minutes max lock
        lockAtLeastFor = "PT1M"      // 1 minute min lock
    )
    public void syncJiraIssues() {
        // Only 1 instance executes this
    }
}
```

**Lock Timing:**
- Cron interval: 30 minutes
- Lock at most for: 25 minutes (5-minute buffer)
- Lock at least for: 1 minute (prevent duplicate triggers)

### Multi-Instance Safety

**With ShedLock:**
- Only 1 instance acquires lock
- Other instances skip execution
- Lock released after completion or timeout
- `usingDbTime()` prevents clock skew issues

**Without ShedLock (development):**
- Set `sync.scheduler.enabled=false` on extra instances
- Or use profile-based configuration

---

## Performance Characteristics

### Capacity

**Single Instance:**
- 100 configs @ 5000 records/config = 11.6 minutes
- 150 configs @ 5000 records/config = 17.5 minutes
- 200+ configs: Partial rejection (tracked)

**Bottlenecks:**
1. External API rate limits (Jira: 10 req/sec, GitHub: 5000 req/hour)
2. Thread pool size (max 5 concurrent configs)
3. Database write throughput (optimized with batch UPSERT)

### Monitoring

**Key Metrics:**
- `sync_jobs_total` - Total sync jobs
- `sync_jobs_success` - Successful syncs
- `sync_jobs_failed` - Failed syncs
- `sync_duration_seconds` - Sync duration histogram
- `sync_records_fetched` - Records from external APIs
- `sync_records_saved` - Records saved to database
- `sync_jobs_rejected_total` - Rejected due to queue full

### Performance Tuning

**Increase capacity:**
1. Increase thread pool size (2 → 5 → 10)
2. Increase database pool size (20 → 30)
3. Optimize batch size (500 optimal for PostgreSQL)
4. Deploy multiple instances (with ShedLock)

---

## Design Decisions

### Service-to-Service Communication

**Decision:** Use gRPC to fetch verified configs from ProjectConfig Service

**Rationale:**
- Microservices principle (database-per-service)
- Loose coupling between services
- Maintainable and testable
- Authorization logic in ProjectConfig Service

**Implementation:** `ListVerifiedConfigs` gRPC method

---

### Data Integrity Strategy

**Decision:** Keep sync data as historical archive (no cascade delete)

**Rationale:**
- Preserve historical data for analytics
- Simple implementation (no cascade logic)
- Support future features (historical reporting)
- Soft delete handles query filtering

**Implementation:** No FK constraints, logical FKs only

---

### Identity Mapping

**Decision:** Dual mapping strategy (email + username)

**Rationale:**
- GitHub users can hide email (privacy)
- Fallback mechanism ensures no data loss
- Supports both mapping approaches
- Future-proof for multiple identity providers

**Implementation:**
- Priority 1: Match by email
- Priority 2: Match by GitHub username
- Priority 3: Match by Git commit email
- Orphaned: Log warning, exclude from contribution calculation

---

### Concurrency Strategy

**Decision:** Spring @Async with small thread pool (2-5 threads)

**Rationale:**
- Simple Spring annotation-based
- Avoid external API rate limiting
- Reduce memory footprint
- Easy monitoring with metrics

**Implementation:** ThreadPoolTaskExecutor with AbortPolicy

---

### Batch Processing

**Decision:** True batch UPSERT with ON CONFLICT

**Rationale:**
- 10-100x performance improvement
- Single database roundtrip per batch
- Idempotent (safe for retries)
- PostgreSQL-native feature

**Implementation:** Custom repository methods with EntityManager

---

## Known Limitations

### Current Limitations

1. **No Real-Time Sync**
   - Uses scheduled batch processing (15-30 minute intervals)
   - Not suitable for real-time dashboards
   - Mitigation: Adjust cron intervals if needed

2. **No User-Facing REST API**
   - Background service only (no manual sync trigger)
   - Status查询 requires database access
   - Mitigation: Use Prometheus metrics

3. **Historical Data Preserved**
   - Sync data remains after config deletion (by design)
   - May include orphaned contributions
   - Mitigation: Use soft delete filtering in queries

4. **Token Expiration Handling**
   - Manual intervention required to update tokens
   - No automatic notification to group leaders
   - Mitigation: Monitor failed sync jobs

5. **Orphaned Contributions**
   - If user identity not mapped (email/username)
   - Contribution not counted in reports
   - Mitigation: Ensure users update GitHub username in profile

### Future Enhancements

1. **Real-Time Webhooks**
   - Replace polling with Jira/GitHub webhooks
   - Instant sync on issue/commit creation

2. **Incremental Sync**
   - Only fetch changes since last sync
   - Use `updated > lastSyncDate` filters

3. **Manual Sync API**
   - Admin endpoint to trigger sync on-demand
   - Useful for debugging

4. **Automated Notifications**
   - Alert group leaders on token expiration
   - Email/Slack integration

5. **Advanced Rate Limiting**
   - Redis-based distributed rate limiter
   - More sophisticated quota management

---

## Production Deployment

### Pre-Deployment Checklist

- [ ] All migrations V1-V16 applied
- [ ] Schema validation passed
- [ ] Thread pool configuration validated
- [ ] ShedLock table exists (`shedlock`)
- [ ] Circuit breaker thresholds reviewed
- [ ] Prometheus metrics endpoint accessible
- [ ] Environment variables set (DATABASE_URL, SERVICE_AUTH_KEY)
- [ ] Log aggregation configured (ELK, Splunk)

### Post-Deployment Verification

- [ ] Scheduler executes on schedule
- [ ] Only 1 instance executes per cycle (ShedLock)
- [ ] Batch UPSERT shows 1 query per batch
- [ ] No constraint violations logged
- [ ] Circuit breaker functions properly
- [ ] Metrics exposed at `/actuator/prometheus`
- [ ] Failed syncs logged correctly

### Troubleshooting

**Issue:** Scheduler not executing

- Check: `sync.scheduler.enabled=true`
- Check: ShedLock table exists
- Check: Database connectivity

**Issue:** High rejection rate

- Check: Thread pool queue capacity
- Check: External API latency
- Solution: Increase thread pool size or queue capacity

**Issue:** Database lock contention

- Check: External API calls outside @Transactional
- Check: Transaction timeout settings
- Solution: Review transaction boundaries

---

## Related Documentation

- [API Contract](API_CONTRACT.md)
- [Database Design](DATABASE.md)
- [System Architecture](../SYSTEM_ARCHITECTURE.md)
- [ProjectConfig Service](../ProjectConfig/IMPLEMENTATION.md)
