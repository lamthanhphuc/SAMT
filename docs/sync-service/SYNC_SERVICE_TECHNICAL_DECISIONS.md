# Sync Service - Technical Decisions Log

**Document:** Confirmed Architecture & Implementation Decisions  
**Version:** 1.0  
**Date:** February 6, 2026  
**Status:** ✅ DECISIONS CONFIRMED - Ready for Implementation

---

## Executive Summary

All **BLOCKING issues** have been resolved. Development team can proceed with implementation following the decisions documented below.

**Decision Date:** February 6, 2026  
**Decided By:** Technical Lead & Architecture Team  
**Review Status:** APPROVED

---

## 1. ARCHITECTURAL DECISIONS

### Decision 1.1: Service-to-Service Communication (RESOLVED ✅)

**Issue:** Sync Service cần lấy danh sách verified configs từ Project Config Service.

**Decision:** **Use gRPC API - Option C**

**Rationale:**
- ✅ Tuân thủ microservices principle (database-per-service)
- ✅ Loose coupling giữa services
- ✅ Maintainable và testable
- ✅ Có thể implement authorization logic trong Project Config Service

**Implementation:**

#### 1.1.1 Update Proto Definition

**File:** `project-config-service/src/main/proto/project_config.proto`

```protobuf
syntax = "proto3";

package projectconfig;

option java_package = "com.fpt.swp391.grpc.projectconfig";
option java_multiple_files = true;

service ProjectConfigInternalService {
  // Existing methods
  rpc InternalGetDecryptedConfig(InternalGetDecryptedConfigRequest) returns (InternalGetDecryptedConfigResponse);
  
  // NEW: List verified configs for sync
  rpc ListVerifiedConfigs(ListVerifiedConfigsRequest) returns (ListVerifiedConfigsResponse);
}

// NEW: List verified configs messages
message ListVerifiedConfigsRequest {
  int32 page = 1;       // Optional pagination (default: 0)
  int32 size = 2;       // Optional page size (default: 100)
}

message ListVerifiedConfigsResponse {
  repeated ConfigSummary configs = 1;
  int32 total_count = 2;
}

message ConfigSummary {
  int64 config_id = 1;
  int64 group_id = 2;
  string jira_host_url = 3;
  string jira_project_key = 4;
  string github_repo_url = 5;
  string state = 6;              // Always "VERIFIED" in this response
  string created_at = 7;
  string updated_at = 8;
}
```

#### 1.1.2 Implementation in Project Config Service

**File:** `ProjectConfigGrpcService.java`

```java
@Override
public void listVerifiedConfigs(
        ListVerifiedConfigsRequest request,
        StreamObserver<ListVerifiedConfigsResponse> responseObserver) {
    
    try {
        // Validate service authentication (via interceptor)
        // Only allow sync-service, ai-service, reporting-service
        
        int page = request.getPage() > 0 ? request.getPage() : 0;
        int size = request.getSize() > 0 ? request.getSize() : 100;
        
        // Query verified configs
        Pageable pageable = PageRequest.of(page, size);
        Page<ProjectConfig> configsPage = projectConfigRepository
                .findByStateAndDeletedAtIsNull(ConfigState.VERIFIED, pageable);
        
        // Map to proto messages
        List<ConfigSummary> summaries = configsPage.getContent().stream()
                .map(this::toConfigSummary)
                .collect(Collectors.toList());
        
        ListVerifiedConfigsResponse response = ListVerifiedConfigsResponse.newBuilder()
                .addAllConfigs(summaries)
                .setTotalCount((int) configsPage.getTotalElements())
                .build();
        
        responseObserver.onNext(response);
        responseObserver.onCompleted();
        
    } catch (Exception e) {
        log.error("Failed to list verified configs", e);
        responseObserver.onError(Status.INTERNAL
                .withDescription("Failed to list verified configs: " + e.getMessage())
                .asRuntimeException());
    }
}

private ConfigSummary toConfigSummary(ProjectConfig config) {
    return ConfigSummary.newBuilder()
            .setConfigId(config.getId())
            .setGroupId(config.getGroupId())
            .setJiraHostUrl(config.getJiraHostUrl())
            .setJiraProjectKey(config.getJiraProjectKey() != null ? config.getJiraProjectKey() : "")
            .setGithubRepoUrl(config.getGithubRepoUrl())
            .setState(config.getState().name())
            .setCreatedAt(config.getCreatedAt().toString())
            .setUpdatedAt(config.getUpdatedAt().toString())
            .build();
}
```

#### 1.1.3 Usage in Sync Service

**File:** `ProjectConfigGrpcClient.java`

```java
public List<ConfigSummaryDto> listVerifiedConfigs() {
    log.info("Fetching list of verified configs");
    
    try {
        ListVerifiedConfigsRequest request = ListVerifiedConfigsRequest.newBuilder()
                .setPage(0)
                .setSize(1000)  // Fetch all in one call (assume < 1000 groups)
                .build();
        
        ListVerifiedConfigsResponse response = stub.listVerifiedConfigs(request);
        
        log.info("Fetched {} verified configs", response.getConfigsCount());
        
        return response.getConfigsList().stream()
                .map(this::toConfigSummaryDto)
                .collect(Collectors.toList());
        
    } catch (StatusRuntimeException e) {
        log.error("Failed to list verified configs: {}", e.getMessage());
        throw new GrpcCallException("GRPC_LIST_CONFIGS_FAILED", 
                "Failed to fetch verified configs: " + e.getMessage());
    }
}

private ConfigSummaryDto toConfigSummaryDto(ConfigSummary summary) {
    ConfigSummaryDto dto = new ConfigSummaryDto();
    dto.setConfigId(summary.getConfigId());
    dto.setGroupId(summary.getGroupId());
    dto.setJiraHostUrl(summary.getJiraHostUrl());
    dto.setJiraProjectKey(summary.getJiraProjectKey());
    dto.setGithubRepoUrl(summary.getGithubRepoUrl());
    dto.setState(summary.getState());
    return dto;
}
```

**Status:** ✅ RESOLVED - Implementation path clear

---

### Decision 1.2: Data Integrity Strategy (RESOLVED ✅)

**Issue:** Foreign key constraint giữa 2 databases, orphaned records khi config deleted.

**Decision:** **Keep Sync Data as Historical Archive - Option A**

**Rationale:**
- ✅ Preserve historical data for analytics và compliance
- ✅ Simple implementation (no cascade delete logic)
- ✅ Support future features (historical reporting, trend analysis)
- ✅ Soft delete mechanism already handles query filtering

**Implementation:**

#### 1.2.1 No Database FK Constraint

```sql
-- sync_db schema
CREATE TABLE unified_activities (
    id BIGSERIAL PRIMARY KEY,
    project_config_id BIGINT NOT NULL,  -- NO FK CONSTRAINT
    -- ... other columns
);

-- Comment in schema migration
COMMENT ON COLUMN unified_activities.project_config_id IS 
'Reference to project_configs.id (cross-database). No FK constraint enforced. 
Data preserved as historical archive even if config deleted.';
```

#### 1.2.2 Application-Level Validation

```java
@Service
public class SyncValidationService {
    
    private final ProjectConfigGrpcClient projectConfigClient;
    
    /**
     * Validate config exists before sync
     * Called ONCE at start of sync job
     */
    public void validateConfigExists(Long configId) {
        try {
            DecryptedConfigDto config = projectConfigClient.getDecryptedConfig(configId);
            
            if (!"VERIFIED".equals(config.getState())) {
                throw new InvalidConfigStateException(
                    "Config state is " + config.getState() + ", expected VERIFIED"
                );
            }
            
        } catch (ConfigNotFoundException e) {
            log.warn("Config {} not found, will skip sync", configId);
            throw e;
        }
    }
    
    /**
     * NO validation needed when inserting unified_activities
     * We trust that sync job was validated at start
     */
}
```

#### 1.2.3 Query Pattern for Reports

```java
// Reporting Service queries sync data
@Repository
public interface UnifiedActivityRepository extends JpaRepository<UnifiedActivity, Long> {
    
    // Include deleted configs in historical reports
    @Query("SELECT a FROM UnifiedActivity a WHERE a.projectConfigId = :configId")
    List<UnifiedActivity> findByConfigIdIncludingDeleted(@Param("configId") Long configId);
    
    // Exclude deleted configs in active reports
    @Query("SELECT a FROM UnifiedActivity a " +
           "WHERE a.projectConfigId IN " +
           "(SELECT c.id FROM ProjectConfig c WHERE c.deletedAt IS NULL)")
    List<UnifiedActivity> findByActiveConfigsOnly();
}
```

**Status:** ✅ RESOLVED - Keep historical data, no cascade delete

---

## 2. IDENTITY MAPPING DECISIONS

### Decision 2.1: GitHub Email Privacy Handling (RESOLVED ✅)

**Issue:** GitHub users có thể ẩn email, không thể map contributions về students.

**Decision:** **Dual Mapping Strategy - Email + Username**

**Rationale:**
- ✅ Fallback mechanism ensures no data loss
- ✅ Flexible: supports both email-based và username-based mapping
- ✅ Future-proof: prepare for multiple identity providers

**Implementation:**

#### 2.1.1 Update User Schema (Identity Service)

**Migration:** `V1.5__add_github_username.sql`

```sql
-- Add github_username column to users table
ALTER TABLE users 
ADD COLUMN github_username VARCHAR(255) NULL;

-- Create unique index
CREATE UNIQUE INDEX idx_users_github_username 
ON users(github_username) 
WHERE github_username IS NOT NULL AND deleted_at IS NULL;

-- Add comment
COMMENT ON COLUMN users.github_username IS 
'GitHub username for mapping commits/PRs. Optional. Must be unique if provided.';
```

#### 2.1.2 Update User DTO

```java
@Data
public class UserDto {
    private Long id;
    private String username;
    private String email;
    private String fullName;
    private String role;
    
    // NEW: GitHub integration
    private String githubUsername;  // NEW FIELD
}
```

#### 2.1.3 Sync Service Mapping Logic

**File:** `GitHubCommitAdapter.java`

```java
private UnifiedActivity mapToUnifiedActivity(GitHubCommitDto commit, Long projectConfigId) {
    UnifiedActivity activity = new UnifiedActivity();
    
    // ... basic fields
    
    // Identity mapping with fallback
    String email = extractEmail(commit);
    String username = extractUsername(commit);
    
    activity.setAssigneeEmail(email);          // May be null
    activity.setAssigneeName(username);         // Always available
    
    // Store both for flexible mapping in Reporting Service
    Map<String, Object> metadata = new HashMap<>();
    metadata.put("githubUsername", commit.getAuthor().getLogin());
    metadata.put("githubEmail", email);  // null if hidden
    metadata.put("commitEmail", commit.getCommit().getAuthor().getEmail());  // From Git metadata
    activity.setMetadata(metadata);
    
    return activity;
}

private String extractEmail(GitHubCommitDto commit) {
    // Priority 1: GitHub API user email (if public)
    if (commit.getAuthor() != null && commit.getAuthor().getEmail() != null) {
        return commit.getAuthor().getEmail();
    }
    
    // Priority 2: Git commit metadata email (always available)
    if (commit.getCommit() != null && commit.getCommit().getAuthor() != null) {
        return commit.getCommit().getAuthor().getEmail();
    }
    
    return null;
}

private String extractUsername(GitHubCommitDto commit) {
    if (commit.getAuthor() != null && commit.getAuthor().getLogin() != null) {
        return commit.getAuthor().getLogin();
    }
    return "unknown";
}
```

#### 2.1.4 Reporting Service Identity Resolution

```java
@Service
public class ContributionCalculationService {
    
    private final UserRepository userRepository;
    
    public Long resolveUserId(UnifiedActivity activity) {
        // Strategy 1: Match by email
        if (activity.getAssigneeEmail() != null) {
            Optional<User> user = userRepository.findByEmailAndDeletedAtIsNull(
                activity.getAssigneeEmail()
            );
            if (user.isPresent()) {
                return user.get().getId();
            }
        }
        
        // Strategy 2: Match by GitHub username
        String githubUsername = (String) activity.getMetadata().get("githubUsername");
        if (githubUsername != null) {
            Optional<User> user = userRepository.findByGithubUsernameAndDeletedAtIsNull(
                githubUsername
            );
            if (user.isPresent()) {
                return user.get().getId();
            }
        }
        
        // Strategy 3: Match by commit email (from Git metadata)
        String commitEmail = (String) activity.getMetadata().get("commitEmail");
        if (commitEmail != null) {
            Optional<User> user = userRepository.findByEmailAndDeletedAtIsNull(commitEmail);
            if (user.isPresent()) {
                return user.get().getId();
            }
        }
        
        // Not found - orphaned contribution
        log.warn("Cannot resolve user for activity {}: email={}, username={}", 
            activity.getId(), 
            activity.getAssigneeEmail(), 
            githubUsername);
        
        return null;  // Will be excluded from contribution calculation
    }
}
```

**Status:** ✅ RESOLVED - Dual mapping strategy with fallbacks

---

## 3. SECURITY DECISIONS

### Decision 3.1: Service Key Management (RESOLVED ✅)

**Issue:** Service keys hard-coded, no rotation strategy.

**Decision:** **Environment Variables with K8s Secrets (MVP)**

**Rationale:**
- ✅ Simple để implement cho MVP
- ✅ Kubernetes Secrets support encryption at rest
- ✅ Dễ rotate (update secret + rolling restart)
- ❌ NOT scalable long-term (defer Vault to Phase 2)

**Implementation:**

#### 3.1.1 Remove Hard-coded Keys

**File:** `ServiceAuthInterceptor.java` (Project Config Service)

**BEFORE:**
```java
private static final Map<String, String> VALID_SERVICES = Map.of(
    "sync-service", "sync-service-secret-key-2026"  // HARD-CODED - BAD
);
```

**AFTER:**
```java
@Component
public class ServiceAuthInterceptor implements ServerInterceptor {
    
    @Value("${service.auth.valid-keys}")
    private String validKeysJson;  // JSON string from env var
    
    private Map<String, String> validServices;
    
    @PostConstruct
    public void init() {
        // Parse JSON: {"sync-service": "key1", "ai-service": "key2"}
        ObjectMapper mapper = new ObjectMapper();
        try {
            validServices = mapper.readValue(validKeysJson, 
                new TypeReference<Map<String, String>>() {});
            log.info("Loaded {} valid service keys", validServices.size());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse service.auth.valid-keys", e);
        }
    }
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String serviceName = headers.get(SERVICE_NAME_KEY);
        String providedKey = headers.get(SERVICE_KEY_KEY);
        
        if (serviceName == null || providedKey == null) {
            call.close(Status.UNAUTHENTICATED
                .withDescription("Missing service authentication headers"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
        
        String expectedKey = validServices.get(serviceName);
        if (expectedKey == null || !expectedKey.equals(providedKey)) {
            call.close(Status.PERMISSION_DENIED
                .withDescription("Invalid service authentication"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
        
        return next.startCall(call, headers);
    }
}
```

#### 3.1.2 Kubernetes Secrets

**File:** `k8s/project-config-service-secret.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: project-config-service-secrets
  namespace: samt
type: Opaque
stringData:
  service-auth-keys: |
    {
      "sync-service": "sync-svc-key-a1b2c3d4e5f6",
      "ai-service": "ai-svc-key-g7h8i9j0k1l2",
      "reporting-service": "report-svc-key-m3n4o5p6q7r8"
    }
```

**File:** `k8s/project-config-service-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: project-config-service
spec:
  template:
    spec:
      containers:
      - name: project-config-service
        image: samt/project-config-service:latest
        env:
        - name: SERVICE_AUTH_VALID_KEYS
          valueFrom:
            secretKeyRef:
              name: project-config-service-secrets
              key: service-auth-keys
```

#### 3.1.3 Key Rotation Process

**Procedure:**

1. **Generate new keys:**
   ```bash
   # Generate secure random keys
   openssl rand -hex 32  # For sync-service
   openssl rand -hex 32  # For ai-service
   openssl rand -hex 32  # For reporting-service
   ```

2. **Update secrets:**
   ```bash
   kubectl create secret generic project-config-service-secrets \
     --from-literal=service-auth-keys='{"sync-service":"NEW_KEY",...}' \
     --dry-run=client -o yaml | kubectl apply -f -
   ```

3. **Rolling restart (zero downtime):**
   ```bash
   # Update client services first (they use new keys)
   kubectl rollout restart deployment/sync-service
   kubectl rollout restart deployment/ai-service
   
   # Then update server (accepts both old and new keys temporarily)
   kubectl rollout restart deployment/project-config-service
   ```

**Status:** ✅ RESOLVED - Use K8s Secrets for MVP

---

## 4. PERFORMANCE DECISIONS

### Decision 4.1: Concurrent Sync Strategy (RESOLVED ✅)

**Issue:** Sequential sync quá chậm (50 configs × 30s = 25 phút).

**Decision:** **Spring @Async with Small Thread Pool (2-5 threads)**

**Rationale:**
- ✅ Simple Spring annotation-based approach
- ✅ Avoid rate limiting issues (Jira: 10 req/sec limit)
- ✅ Reduce memory footprint (small pool)
- ✅ Easy monitoring với ThreadPoolTaskExecutor metrics

**Implementation:**

#### 4.1.1 Enable Async Support

**File:** `AsyncConfig.java`

```java
@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Bean(name = "syncTaskExecutor")
    public Executor syncTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Core pool size: always running threads
        executor.setCorePoolSize(2);
        
        // Max pool size: scale up to this if needed
        executor.setMaxPoolSize(5);
        
        // Queue capacity: pending tasks
        executor.setQueueCapacity(100);
        
        // Thread naming
        executor.setThreadNamePrefix("sync-");
        
        // Rejection policy: block caller if queue full
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        executor.initialize();
        
        log.info("Initialized sync task executor: corePoolSize=2, maxPoolSize=5");
        
        return executor;
    }
}
```

#### 4.1.2 Async Sync Methods

**File:** `SyncScheduler.java`

```java
@Service
@Slf4j
public class SyncScheduler {
    
    private final ProjectConfigGrpcClient projectConfigClient;
    private final SyncExecutor syncExecutor;
    
    @Scheduled(cron = "${sync.scheduler.jira-issues-cron}")
    public void syncAllJiraIssues() {
        log.info("Starting scheduled Jira issues sync");
        
        List<ConfigSummaryDto> configs = projectConfigClient.listVerifiedConfigs();
        log.info("Found {} verified configs", configs.size());
        
        // Submit async tasks
        List<CompletableFuture<SyncResult>> futures = configs.stream()
                .map(config -> syncExecutor.syncJiraIssuesAsync(config.getConfigId()))
                .collect(Collectors.toList());
        
        // Wait for all to complete (with timeout)
        CompletableFuture<Void> allOf = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allOf.get(30, TimeUnit.MINUTES);  // Timeout after 30 minutes
            
            // Collect results
            long successCount = futures.stream()
                .map(f -> {
                    try { return f.get(); } catch (Exception e) { return null; }
                })
                .filter(r -> r != null && r.isSuccess())
                .count();
            
            log.info("Jira sync completed: {}/{} successful", successCount, configs.size());
            
        } catch (TimeoutException e) {
            log.error("Sync timeout after 30 minutes", e);
            allOf.cancel(true);
        } catch (Exception e) {
            log.error("Sync failed", e);
        }
    }
}
```

**File:** `SyncExecutor.java`

```java
@Service
@Slf4j
public class SyncExecutor {
    
    private final ProjectConfigGrpcClient projectConfigClient;
    private final JiraApiClient jiraApiClient;
    private final JiraIssueAdapter jiraIssueAdapter;
    private final UnifiedActivityRepository activityRepository;
    private final SyncJobRepository syncJobRepository;
    
    @Async("syncTaskExecutor")
    public CompletableFuture<SyncResult> syncJiraIssuesAsync(Long configId) {
        SyncJob job = createSyncJob(configId, JobType.JIRA_ISSUES);
        
        try {
            // Get decrypted config
            DecryptedConfigDto config = projectConfigClient.getDecryptedConfig(configId);
            
            // Fetch from Jira API
            List<JiraIssueDto> issues = jiraApiClient.fetchIssues(
                config.getJiraHostUrl(),
                config.getJiraApiToken(),
                config.getJiraProjectKey()
            );
            
            // Transform and save
            List<UnifiedActivity> activities = jiraIssueAdapter.extractActivities(issues, configId);
            List<UnifiedActivity> saved = upsertActivities(activities);
            
            // Update job
            job.setStatus(JobStatus.SUCCESS);
            job.setRecordsFetched(issues.size());
            job.setRecordsSaved(saved.size());
            job.setCompletedAt(LocalDateTime.now());
            syncJobRepository.save(job);
            
            log.info("Sync completed for config {}: fetched={}, saved={}", 
                configId, issues.size(), saved.size());
            
            return CompletableFuture.completedFuture(SyncResult.success(configId));
            
        } catch (Exception e) {
            log.error("Sync failed for config {}: {}", configId, e.getMessage(), e);
            
            job.setStatus(JobStatus.FAILED);
            job.setErrorMessage(e.getMessage());
            job.setCompletedAt(LocalDateTime.now());
            syncJobRepository.save(job);
            
            return CompletableFuture.completedFuture(SyncResult.failure(configId, e.getMessage()));
        }
    }
}
```

**Status:** ✅ RESOLVED - Use Spring @Async with pool size 2-5

---

### Decision 4.2: Rate Limiting Strategy (RESOLVED ✅)

**Issue:** Risk vượt GitHub API quota (5000 req/hour).

**Decision:** **Check X-RateLimit-Remaining Header + Adaptive Delay**

**Rationale:**
- ✅ Proactive: prevent hitting rate limit
- ✅ Self-healing: auto-adjust sync speed
- ✅ No external dependencies (no Redis for rate limiting)

**Implementation:**

**File:** `GitHubApiClient.java`

```java
@Service
@Slf4j
public class GitHubApiClient {
    
    private final WebClient webClient;
    private final AtomicInteger remainingQuota = new AtomicInteger(5000);
    private final AtomicLong resetTimestamp = new AtomicLong(0);
    
    public List<GitHubCommitDto> fetchCommits(String repoUrl, String token) {
        // Check quota before making request
        checkRateLimit();
        
        String apiUrl = convertToApiUrl(repoUrl) + "/commits";
        
        ClientResponse response = webClient.get()
                .uri(apiUrl)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .exchange()
                .block();
        
        // Update quota from response headers
        updateRateLimitInfo(response.headers());
        
        // Parse response
        return response.bodyToFlux(GitHubCommitDto.class)
                .collectList()
                .block();
    }
    
    private void checkRateLimit() {
        int remaining = remainingQuota.get();
        
        if (remaining < 100) {
            long resetTime = resetTimestamp.get();
            long now = System.currentTimeMillis() / 1000;
            
            if (now < resetTime) {
                long waitSeconds = resetTime - now;
                log.warn("GitHub API quota low ({}), sleeping {} seconds until reset", 
                    remaining, waitSeconds);
                
                try {
                    Thread.sleep(waitSeconds * 1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted while waiting for rate limit reset", e);
                }
                
                // Reset quota after wait
                remainingQuota.set(5000);
            }
        }
        
        // Adaptive delay based on quota
        if (remaining < 500) {
            try {
                Thread.sleep(1000);  // 1 second delay if quota < 10%
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private void updateRateLimitInfo(ClientResponse.Headers headers) {
        String remaining = headers.header("X-RateLimit-Remaining").stream().findFirst().orElse(null);
        String reset = headers.header("X-RateLimit-Reset").stream().findFirst().orElse(null);
        
        if (remaining != null) {
            int remainingInt = Integer.parseInt(remaining);
            remainingQuota.set(remainingInt);
            
            log.debug("GitHub API quota: {} remaining", remainingInt);
        }
        
        if (reset != null) {
            long resetTime = Long.parseLong(reset);
            resetTimestamp.set(resetTime);
        }
    }
    
    private String convertToApiUrl(String repoUrl) {
        // Convert: https://github.com/org/repo -> https://api.github.com/repos/org/repo
        return repoUrl.replace("github.com", "api.github.com/repos");
    }
}
```

**Status:** ✅ RESOLVED - Check rate limit headers + adaptive delay

---

## 5. UPDATED DECISION LOG

| Issue ID | Decision | Status | Decided By | Date | Implementation Priority |
|----------|----------|--------|------------|------|------------------------|
| 1.1 | Use gRPC ListVerifiedConfigs | ✅ RESOLVED | Tech Lead | 2026-02-06 | P0 - Blocking |
| 1.2 | Keep historical data, no cascade delete | ✅ RESOLVED | Tech Lead | 2026-02-06 | P1 - High |
| 2.1 | Store current sprint in metadata_json | ✅ RESOLVED | Tech Lead | 2026-02-05 | P2 - Medium |
| 2.2 | Dual mapping: Email + GitHub username | ✅ RESOLVED | Tech Lead | 2026-02-06 | P0 - Blocking |
| 2.3 | Check X-RateLimit-Remaining header | ✅ RESOLVED | Tech Lead | 2026-02-06 | P1 - High |
| 3.1 | Simple formula for MVP (Lines/10) | ✅ RESOLVED | Tech Lead | 2026-02-05 | P2 - Medium |
| 3.2 | Store links in metadata, dedupe in Reporting | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P3 - Sprint 2 |
| 4.1 | Incremental sync via lastSyncCursor | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P3 - Sprint 2 |
| 4.2 | Accept eventual consistency (30-min delay) | ✅ RESOLVED | Tech Lead | 2026-02-05 | P2 - Medium |
| 5.1 | Keep data with soft delete | ✅ RESOLVED | Tech Lead | 2026-02-05 | P1 - High |
| 5.2 | Manual notification (Phase 1) | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P4 - Phase 2 |
| 6.1 | Spring @Async, pool size 2-5 | ✅ RESOLVED | Tech Lead | 2026-02-06 | P0 - Blocking |
| 6.2 | HikariCP max pool size 20 | ✅ RESOLVED | Tech Lead | 2026-02-06 | P1 - High |
| 7.1 | Mock API with fixtures | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P2 - Medium |
| 7.2 | Prometheus alerts (failure rate, latency) | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P3 - Sprint 2 |
| 8.1 | K8s Secrets for service keys | ✅ RESOLVED | Tech Lead | 2026-02-06 | P0 - Blocking |
| 9.1 | No Notification Service (manual for now) | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P4 - Phase 2 |
| 9.2 | Direct DB access for Reporting Service | ⏳ DEFERRED | Tech Lead | 2026-02-06 | P3 - Sprint 2 |
| 9.3 | AI Service integration Phase 2 | ⏳ DEFERRED | Tech Lead | 2026-02-05 | P4 - Phase 2 |

---

## 6. IMPLEMENTATION ACTION ITEMS

### Sprint 1 - Core Sync Functionality (Week 1-2)

#### Backend Team - Project Config Service

**Priority P0 (Blocking):**

- [ ] **Task 1.1:** Update `project_config.proto` với `ListVerifiedConfigs` RPC method
  - Assignee: Backend Dev 1
  - Estimate: 2 hours
  - Dependencies: None
  - Deliverable: Updated proto file, generated Java classes

- [ ] **Task 1.2:** Implement `listVerifiedConfigs()` method trong `ProjectConfigGrpcService`
  - Assignee: Backend Dev 1
  - Estimate: 4 hours
  - Dependencies: Task 1.1
  - Deliverable: Working gRPC endpoint with pagination

- [ ] **Task 1.3:** Update `ServiceAuthInterceptor` to use env vars instead of hard-coded keys
  - Assignee: Backend Dev 2
  - Estimate: 3 hours
  - Dependencies: None
  - Deliverable: JSON-based key configuration

- [ ] **Task 1.4:** Create K8s Secret manifest for service keys
  - Assignee: DevOps
  - Estimate: 1 hour
  - Dependencies: Task 1.3
  - Deliverable: `project-config-service-secret.yaml`

#### Backend Team - Identity Service

**Priority P0 (Blocking):**

- [ ] **Task 2.1:** Add `github_username` column to users table
  - Assignee: Backend Dev 3
  - Estimate: 2 hours
  - Dependencies: None
  - Deliverable: Flyway migration script, updated entity

- [ ] **Task 2.2:** Update User REST API to include `githubUsername` field
  - Assignee: Backend Dev 3
  - Estimate: 3 hours
  - Dependencies: Task 2.1
  - Deliverable: Updated DTOs, controllers, validation

- [ ] **Task 2.3:** Add unique index on `github_username`
  - Assignee: Backend Dev 3
  - Estimate: 1 hour
  - Dependencies: Task 2.1
  - Deliverable: Database index

#### Backend Team - Sync Service

**Priority P0 (Blocking):**

- [ ] **Task 3.1:** Setup Sync Service project structure (Maven, Docker)
  - Assignee: Backend Dev 4
  - Estimate: 4 hours
  - Dependencies: None
  - Deliverable: Buildable project with dependencies

- [ ] **Task 3.2:** Create database schema (sync_db) với 6 tables
  - Assignee: Backend Dev 4
  - Estimate: 4 hours
  - Dependencies: Task 3.1
  - Deliverable: Flyway migrations, JPA entities

- [ ] **Task 3.3:** Implement `ProjectConfigGrpcClient.listVerifiedConfigs()`
  - Assignee: Backend Dev 5
  - Estimate: 4 hours
  - Dependencies: Task 1.1, Task 3.1
  - Deliverable: Working gRPC client

- [ ] **Task 3.4:** Implement `AsyncConfig` với thread pool size 2-5
  - Assignee: Backend Dev 5
  - Estimate: 2 hours
  - Dependencies: Task 3.1
  - Deliverable: Configured ThreadPoolTaskExecutor

**Priority P1 (High):**

- [ ] **Task 3.5:** Implement `JiraApiClient.fetchIssues()` với rate limit handling
  - Assignee: Backend Dev 4
  - Estimate: 6 hours
  - Dependencies: Task 3.1
  - Deliverable: Working Jira API client with retry logic

- [ ] **Task 3.6:** Implement `GitHubApiClient.fetchCommits()` với rate limit checking
  - Assignee: Backend Dev 5
  - Estimate: 6 hours
  - Dependencies: Task 3.1
  - Deliverable: Working GitHub API client with quota monitoring

- [ ] **Task 3.7:** Implement `JiraIssueAdapter` với email + username mapping
  - Assignee: Backend Dev 4
  - Estimate: 4 hours
  - Dependencies: Task 3.5
  - Deliverable: Adapter with dual identity mapping

- [ ] **Task 3.8:** Implement `GitHubCommitAdapter` với metadata extraction
  - Assignee: Backend Dev 5
  - Estimate: 4 hours
  - Dependencies: Task 3.6
  - Deliverable: Adapter with Git metadata fallback

- [ ] **Task 3.9:** Implement `SyncScheduler` với @Async support
  - Assignee: Backend Dev 4
  - Estimate: 6 hours
  - Dependencies: Task 3.3, Task 3.4, Task 3.7, Task 3.8
  - Deliverable: Working scheduled sync jobs

**Priority P2 (Medium):**

- [ ] **Task 3.10:** Configure HikariCP connection pool (max=20)
  - Assignee: Backend Dev 4
  - Estimate: 1 hour
  - Dependencies: Task 3.2
  - Deliverable: Updated application.yml

- [ ] **Task 3.11:** Add Prometheus metrics for sync jobs
  - Assignee: Backend Dev 5
  - Estimate: 3 hours
  - Dependencies: Task 3.9
  - Deliverable: Custom metrics (success rate, duration, records)

---

### Sprint 2 - Advanced Features (Week 3-4)

**Priority P3 (Sprint 2):**

- [ ] **Task 4.1:** Implement incremental sync với `lastSyncCursor`
- [ ] **Task 4.2:** Implement Jira-GitHub link detection
- [ ] **Task 4.3:** Create Prometheus alert rules
- [ ] **Task 4.4:** Setup mock API server cho integration tests

---

### DevOps Tasks

**Priority P0:**

- [ ] **Task 5.1:** Create `sync_db` PostgreSQL database
  - Assignee: DevOps
  - Estimate: 1 hour
  - Deliverable: Database instance

- [ ] **Task 5.2:** Create K8s Secret for Sync Service authentication
  - Assignee: DevOps
  - Estimate: 1 hour
  - Dependencies: Task 1.4
  - Deliverable: `sync-service-secret.yaml`

**Priority P1:**

- [ ] **Task 5.3:** Setup Prometheus monitoring for Sync Service
  - Assignee: DevOps
  - Estimate: 2 hours
  - Deliverable: ServiceMonitor configuration

- [ ] **Task 5.4:** Create Grafana dashboard for sync metrics
  - Assignee: DevOps
  - Estimate: 3 hours
  - Dependencies: Task 5.3
  - Deliverable: Dashboard JSON export

---

### Frontend Tasks (Optional - User Profile)

**Priority P2:**

- [ ] **Task 6.1:** Add GitHub Username field to user profile edit form
  - Assignee: Frontend Dev
  - Estimate: 2 hours
  - Dependencies: Task 2.2
  - Deliverable: Updated profile form component

- [ ] **Task 6.2:** Add GitHub Username validation (unique, format)
  - Assignee: Frontend Dev
  - Estimate: 2 hours
  - Dependencies: Task 6.1
  - Deliverable: Form validation logic

---

## 7. TESTING STRATEGY

### Unit Tests

**Required Coverage: 80%**

- [ ] `ProjectConfigGrpcClient` - Mock gRPC stub
- [ ] `JiraApiClient` - Mock HTTP responses
- [ ] `GitHubApiClient` - Mock HTTP responses
- [ ] `JiraIssueAdapter` - Test DTO → Entity mapping
- [ ] `GitHubCommitAdapter` - Test identity resolution logic
- [ ] `SyncScheduler` - Test async execution

### Integration Tests

**Required:**

- [ ] gRPC integration: Sync Service ↔ Project Config Service (Testcontainers)
- [ ] Database integration: Sync Service write/read operations
- [ ] External API integration: Mock Jira/GitHub servers (WireMock)

### End-to-End Tests

**Scenarios:**

1. **Happy Path:** Create config → Verify → Sync → Query data
2. **Token Expired:** Sync detects 401 → Mark config INVALID
3. **Rate Limit:** Sync detects low quota → Delay execution
4. **Concurrent Sync:** Multiple configs sync simultaneously

---

## 8. DEPLOYMENT PLAN

### Phase 1: MVP (Week 1-2)

**Deployment Order:**

1. Deploy Project Config Service updates (new gRPC method)
2. Deploy Identity Service updates (github_username column)
3. Deploy Sync Service (initial version)
4. Run manual sync test with 5 sample configs
5. Monitor logs and metrics for 24 hours
6. Enable scheduled jobs for all verified configs

### Phase 2: Scale (Week 3-4)

**Scaling Strategy:**

1. Monitor thread pool utilization
2. Adjust pool size if needed (2 → 5)
3. Monitor rate limits (add alerts)
4. Implement incremental sync
5. Add more monitoring dashboards

---

## 9. RISK MITIGATION

### Risk 1: Jira/GitHub API Downtime

**Mitigation:**
- Implement circuit breaker (Resilience4j)
- Retry with exponential backoff (3 attempts)
- Log failures for manual retry
- Alert on-call engineer if failure rate > 10%

### Risk 2: Database Connection Exhaustion

**Mitigation:**
- HikariCP max pool size 20
- Monitor active connections
- Implement connection timeout (30s)
- Alert if pool utilization > 80%

### Risk 3: Memory Leak from Async Tasks

**Mitigation:**
- Set queue capacity limit (100)
- Use CallerRunsPolicy for rejection
- Enable heap dump on OOM
- Monitor memory usage metrics

### Risk 4: Data Quality Issues (Orphaned Contributions)

**Mitigation:**
- Log warning for unresolved identities
- Create daily report of orphaned contributions
- Provide admin UI to manually map users
- Document user onboarding process (add GitHub username)

---

## 10. SUCCESS CRITERIA

### Functional Requirements

- ✅ Sync Service can fetch verified configs via gRPC
- ✅ Sync Service can fetch Jira issues and GitHub commits
- ✅ Data normalized into unified_activities table
- ✅ Sync runs every 30 minutes (configurable)
- ✅ Contributions mapped to users via email OR username

### Non-Functional Requirements

- ✅ Sync latency < 5 minutes for 50 configs
- ✅ API error rate < 5%
- ✅ Database write throughput > 1000 records/minute
- ✅ Memory usage < 2GB per service instance
- ✅ Zero downtime deployments

### Quality Requirements

- ✅ Unit test coverage > 80%
- ✅ Integration test coverage > 60%
- ✅ No critical security vulnerabilities (SonarQube)
- ✅ Code review approval from 2+ engineers
- ✅ Documentation complete (README, API docs, runbooks)

---

## 11. DEFERRED TO FUTURE PHASES

### Phase 2 (Sprint 3-4)

- Incremental sync (lastSyncCursor)
- Jira-GitHub link detection
- Advanced Prometheus alerts
- Grafana dashboards
- Mock API test fixtures

### Phase 3 (Month 2)

- Real-time webhooks (replace polling)
- AI-based effort calculation
- Notification Service integration
- Automated token expiration handling
- Advanced rate limiting (Redis-based)

### Phase 4 (Month 3+)

- Vault integration for secrets
- Multi-region deployment
- Data archiving (cold storage)
- Advanced analytics (materialized views)
- Performance optimization (caching)

---

## 12. SIGN-OFF

**Architecture Review:**
- ✅ Approved by: Technical Lead
- ✅ Date: February 6, 2026
- ✅ Notes: All blocking issues resolved, ready for implementation

**Security Review:**
- ✅ Approved by: Security Engineer
- ✅ Date: February 6, 2026
- ✅ Notes: Service key management acceptable for MVP, plan Vault migration

**DevOps Review:**
- ✅ Approved by: DevOps Lead
- ✅ Date: February 6, 2026
- ✅ Notes: K8s manifests ready, monitoring setup planned

---

## Document Control

**Version History:**

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.0 | 2026-02-06 | Technical Architect | Initial decisions document |

**Next Review:** February 20, 2026 (Post Sprint 1 retrospective)

**Status:** ✅ APPROVED - Implementation can proceed
