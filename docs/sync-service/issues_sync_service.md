# Sync Service - Issues & Clarifications Required

**Document:** Các vấn đề cần làm rõ trước khi implement  
**Version:** 1.0  
**Date:** February 5, 2026  
**Status:** ⚠️ BLOCKING ISSUES

---

## ⚠️ CRITICAL: DO NOT IMPLEMENT WITHOUT CLARIFICATION

Document này liệt kê **tất cả các mâu thuẫn, vấn đề chưa rõ, và assumption cần xác nhận** từ tài liệu hệ thống hiện có. Lập trình viên **KHÔNG ĐƯỢC tự ý suy đoán** logic mà phải có xác nhận từ Product Owner hoặc Technical Lead.

---

## 1. DATABASE ARCHITECTURE ISSUES

### 1.1 ❌ CRITICAL: Project Config Database Access

**Problem:** Sync Service cần query `project_configs` table để lấy danh sách configs với `state = VERIFIED`.

**Current Documentation:**
- 00_SYSTEM_OVERVIEW.md: "project_configs thuộc project-config-service database"
- Microservices best practice: **Database-per-service** - mỗi service có database riêng

**Contradiction:**
```java
// In SyncScheduler.java - Tài liệu gợi ý code này:
private List<ProjectConfigDto> getVerifiedConfigs() {
    // Query project_configs table for configs with state=VERIFIED
    return projectConfigRepository.findAllByStateAndDeletedAtIsNull(ConfigState.VERIFIED);
}
```

**Issue:** `projectConfigRepository` nghĩa là Sync Service phải:
- **Option A:** Truy cập trực tiếp vào `project-config-service` database (vi phạm microservices principle)
- **Option B:** Có bản sao (replica) của `project_configs` table trong `sync_db` (data duplication, sync issues)
- **Option C:** Call gRPC `ListVerifiedConfigs()` từ Project Config Service (không tồn tại trong proto hiện tại)

**Questions:**
1. Sync Service có được phép truy cập database của Project Config Service không?
2. Nếu không, cần thêm gRPC method `ListVerifiedConfigs()` vào Project Config Service?
3. Proto definition cho method này như thế nào?

**Recommendation:**
```protobuf
// Thêm vào project_config.proto
rpc ListVerifiedConfigs(ListVerifiedConfigsRequest) returns (ListVerifiedConfigsResponse);

message ListVerifiedConfigsRequest {
  // Empty hoặc pagination params
  int32 page = 1;
  int32 size = 2;
}

message ListVerifiedConfigsResponse {
  repeated ConfigSummary configs = 1;
}

message ConfigSummary {
  int64 config_id = 1;
  int64 group_id = 2;
  string jira_host_url = 3;
  string jira_project_key = 4;
  string github_repo_url = 5;
  string state = 6;
}
```

**Impact:** HIGH - Blocking implementation của SyncScheduler.

---

### 1.2 ❌ CRITICAL: Foreign Key Constraint

**Problem:** `unified_activities.project_config_id` là FK đến `project_configs.id` nhưng 2 tables ở 2 databases khác nhau.

**From SYNC_SERVICE_OVERVIEW.md:**
```sql
Column: project_config_id | BIGINT | NOT NULL | FK to project_configs.id
```

**Note trong document:**
> "project_config_id là FK đến database khác (project-config-service). Không enforce FK constraint ở database level (microservices best practice), chỉ enforce trong application code."

**Questions:**
1. Làm sao validate `project_config_id` hợp lệ khi insert `unified_activities`?
2. Nếu Project Config bị xóa (soft delete), có cần cascade delete các records trong Sync Service không?
3. Referential integrity violation sẽ được handle như thế nào?

**Current Approach (từ doc):**
```java
// Application-level validation
private void validateConfigExists(Long configId) {
    DecryptedConfigDto config = projectConfigClient.getDecryptedConfig(configId);
    // If NOT_FOUND exception thrown, config doesn't exist
}
```

**Issue:** Đây là validation khi fetch data, nhưng không giải quyết vấn đề orphaned records nếu config bị xóa sau khi sync.

**Recommendation:** Cần policy rõ ràng:
- **Option A:** Sync data remains even if config deleted (historical data preservation)
- **Option B:** Cascade soft delete sync data when config deleted (data cleanup)
- **Option C:** Move sync data to archive table when config deleted

**Impact:** MEDIUM - Cần quyết định architecture.

---

## 2. EXTERNAL API INTEGRATION ISSUES

### 2.1 ⚠️ Jira API - Sprint Association

**Problem:** Jira issues có thể thuộc multiple sprints (moved between sprints).

**From SYNC_SERVICE_OVERVIEW.md:**
```sql
Column: sprint_id | BIGINT | NULL | FK to jira_sprints.id
```

**Issue:** Schema chỉ support 1 sprint per issue. Nhưng Jira thực tế:
- Issue có thể di chuyển giữa các sprints
- Issue có thể thuộc multiple sprints (current + historical)
- Jira API trả về sprint history trong `fields.sprint` (có thể là array)

**Questions:**
1. Có lưu sprint history không? (issue đã ở sprint 1, giờ ở sprint 2)
2. Nếu lưu history, cần table `jira_issue_sprint_history` riêng?
3. Khi issue di chuyển sprint, có update existing record hay insert new record?

**Example Jira API Response:**
```json
{
  "key": "SWP391-123",
  "fields": {
    "sprint": {
      "id": 25,
      "name": "Sprint 3",
      "state": "active"
    },
    "closedSprints": [
      {
        "id": 23,
        "name": "Sprint 1",
        "state": "closed"
      },
      {
        "id": 24,
        "name": "Sprint 2",
        "state": "closed"
      }
    ]
  }
}
```

**Recommendation:**
- Store `sprint_id` = current active sprint only
- Store sprint history in `metadata_json`:
```json
{
  "currentSprintId": 25,
  "currentSprintName": "Sprint 3",
  "sprintHistory": [
    {"id": 23, "name": "Sprint 1", "movedAt": "2026-01-15T10:00:00Z"},
    {"id": 24, "name": "Sprint 2", "movedAt": "2026-01-28T14:30:00Z"}
  ]
}
```

**Impact:** LOW - Có thể implement với current schema + metadata.

---

### 2.2 ⚠️ GitHub API - Email Privacy

**Problem:** GitHub users có thể ẩn email (privacy settings).

**From SYNC_LOGIC_DESIGN.md:**
```java
activity.setAssigneeEmail(pr.getUser().getEmail());  // May be null if privacy settings
```

**Issue:** Nếu `assigneeEmail = null`, không thể map commit/PR về student trong hệ thống.

**Questions:**
1. Có accept commits/PRs without email không?
2. Nếu không có email, có map theo GitHub username không?
3. Cần require students configure public email trong GitHub không?

**GitHub API Response khi email hidden:**
```json
{
  "author": {
    "login": "john_student",
    "email": null,  // Email hidden
    "name": "John Doe"
  }
}
```

**Workaround Options:**
1. **Fallback to commit author email:**
   ```bash
   git log --format="%ae" <commit-sha>
   ```
   GitHub Git API vẫn trả về email từ commit metadata (không bị privacy setting ảnh hưởng).

2. **Map theo GitHub username:**
   - Require students thêm GitHub username vào profile (new field trong users table)
   - Match: `github_username` → `users.github_username`

3. **Reject contributions without email:**
   - Skip commits/PRs nếu không có email
   - Notify group leader

**Recommendation:** Option 1 (fallback to commit metadata email) + Option 2 (username mapping) combined.

**Impact:** MEDIUM - Cần update User schema + sync logic.

---

### 2.3 ⚠️ Rate Limiting Strategy

**From SYNC_SERVICE_OVERVIEW.md:**

> **GitHub API:** 5000 requests/hour per authenticated user

**Problem:** Nếu có 50 groups, mỗi group sync mỗi 15 phút (4 lần/hour):
- Total requests: 50 groups × 4 syncs × ~10 API calls = **2000 requests/hour**
- Safe, nhưng nếu scale lên 100 groups: **4000 requests/hour** (gần limit)

**Questions:**
1. Có rate limit policy nếu vượt quota không?
2. Có pause sync job khi gần rate limit không?
3. Có sử dụng multiple GitHub tokens (từ different users) để tăng quota không?

**GitHub Rate Limit Header:**
```
X-RateLimit-Limit: 5000
X-RateLimit-Remaining: 4850
X-RateLimit-Reset: 1675593600  # Unix timestamp
```

**Recommendation:**
```java
@Component
public class GitHubApiClient {
    
    private final AtomicInteger remainingQuota = new AtomicInteger(5000);
    
    public void checkRateLimit(HttpHeaders responseHeaders) {
        String remaining = responseHeaders.getFirst("X-RateLimit-Remaining");
        if (remaining != null) {
            int remainingInt = Integer.parseInt(remaining);
            remainingQuota.set(remainingInt);
            
            if (remainingInt < 100) {
                log.warn("GitHub API quota low: {} requests remaining", remainingInt);
                // Pause sync? Send alert?
            }
        }
    }
}
```

**Impact:** MEDIUM - Cần monitoring + alerting system.

---

## 3. DATA NORMALIZATION ISSUES

### 3.1 ❓ Effort Points Calculation

**From SYNC_LOGIC_DESIGN.md:**

| Activity Type | Effort Points |
|--------------|---------------|
| Jira Issue (Task) | Story Points (if available) |
| GitHub Commit | Lines Changed / 10 |
| GitHub PR | Lines Changed / 10 |

**Questions:**
1. Tại sao chia 10? (Lines Changed / 10)
2. Có nghiên cứu/data support công thức này không?
3. Có adjust cho file type không? (Java code vs JSON config vs Markdown docs)
4. Có penalize large commits (poor commit practices) không?

**Example:**
- Commit 1: +500 lines (all autogenerated code) → 50 points
- Commit 2: +20 lines (core business logic) → 2 points

Rõ ràng Commit 2 valuable hơn nhưng points thấp hơn.

**Recommendation:** Cần AI/ML model phân tích code quality, không chỉ dựa vào lines changed.

**Impact:** LOW - Có thể dùng simple formula trước, enhance sau với AI Service.

---

### 3.2 ❓ Duplicate Detection

**Problem:** Jira và GitHub có thể có overlapping data.

**Example:**
- Jira Issue: "SWP391-123: Implement login API"
- GitHub PR: "#42: Implement login API" (linked to Jira issue)

**Questions:**
1. Có detect link giữa Jira issue và GitHub PR không?
2. Nếu detect, có merge thành 1 `UnifiedActivity` không?
3. Nếu không merge, student có bị double-count points không?

**Jira-GitHub Integration:**
Nếu group config Jira Smart Commits:
```
git commit -m "SWP391-123 Add login endpoint"
```
Jira sẽ tự động link commit với issue.

**GitHub PR Description:**
```markdown
Fixes SWP391-123

## Changes
- Added login endpoint
- Added JWT validation
```

**Recommendation:**
- Store link in metadata:
  ```json
  {
    "linkedJiraIssue": "SWP391-123",
    "linkedGithubPR": 42
  }
  ```
- Reporting Service deduplicate khi tính contribution (count issue XOR PR, not both).

**Impact:** MEDIUM - Ảnh hưởng accuracy của contribution calculation.

---

## 4. SYNC STRATEGY ISSUES

### 4.1 ❓ Incremental vs Full Sync

**From SYNC_SERVICE_OVERVIEW.md:**

> **Future Enhancement:** Incremental Sync - Only fetch changes since last sync

**Current Implementation:** Full sync mỗi lần.

**Questions:**
1. Có implement incremental sync ngay từ đầu không?
2. Nếu có, làm sao track `lastSyncTimestamp`?
3. Jira API có support `updated > lastSyncDate` filter không?
4. GitHub API có support `since` parameter không?

**Jira API Incremental Sync:**
```
GET /rest/api/3/search?jql=project=SWP391 AND updated >= '2026-02-05 10:00'
```

**GitHub API Incremental Sync:**
```
GET /repos/owner/repo/commits?since=2026-02-05T10:00:00Z
```

**Recommendation:** Implement incremental sync từ đầu:
```java
@Entity
public class SyncJob {
    // ... existing fields
    
    @Column(name = "last_sync_cursor")
    private String lastSyncCursor;  // Timestamp hoặc pagination cursor
}
```

**Impact:** HIGH - Performance improvement, reduce API calls.

---

### 4.2 ❓ Conflict Resolution

**Problem:** Nếu Jira issue updated trong lúc sync đang chạy.

**Scenario:**
1. Sync starts at 10:00:00
2. Fetch issue SWP391-123, status = "In Progress", updated_at = 09:55:00
3. User updates issue to "Done" at 10:00:30
4. Sync completes at 10:01:00, saves status = "In Progress"
5. Next sync at 10:30:00 will correct it

**Questions:**
1. Có acceptable delay (up to 30 minutes) không?
2. Có cần real-time sync (webhooks) không?
3. Có cần conflict resolution strategy (last-write-wins, timestamps) không?

**Recommendation:** Accept eventual consistency, document trong SLA:
> "Data sync có độ trễ tối đa 30 phút. Không phù hợp cho real-time monitoring."

**Impact:** LOW - Accept tradeoff giữa complexity và timeliness.

---

## 5. BUSINESS LOGIC ISSUES

### 5.1 ❓ Deleted Config Behavior

**Problem:** Nếu project config bị soft delete (group disbanded, project ended).

**Questions:**
1. Sync job có tiếp tục chạy không?
2. Có delete sync data khi config deleted không?
3. Có archive sync data để preserve historical data không?

**Options:**

**Option A: Stop Sync, Keep Data**
```java
private List<ProjectConfigDto> getVerifiedConfigs() {
    // Only query configs with state=VERIFIED AND deleted_at IS NULL
    return configs.stream()
            .filter(c -> c.getDeletedAt() == null)
            .collect(Collectors.toList());
}
```

**Option B: Archive Data**
```sql
-- When config soft deleted, move sync data to archive
INSERT INTO unified_activities_archive 
SELECT * FROM unified_activities WHERE project_config_id = ?;

DELETE FROM unified_activities WHERE project_config_id = ?;
```

**Option C: Keep Data, Add Deleted Flag**
```java
// unified_activities already has deleted_at column
// Just query with deleted_at IS NULL
```

**Recommendation:** Option C (simple, leverages existing soft delete).

**Impact:** LOW - Already handled by soft delete mechanism.

---

### 5.2 ❓ Invalid Token Handling

**From SYNC_SERVICE_OVERVIEW.md:**

> **Token Expiration:** Detect 401 Unauthorized từ external APIs, Update project_config state → INVALID

**Questions:**
1. Có automated notification đến group leader không?
2. Có retry logic (token có thể temporary fail) không?
3. Có manual intervention process (admin reactivate config) không?

**Flow:**
```
1. Sync detects 401 Unauthorized
2. Mark config state = INVALID
3. Send email to group leader: "Jira token expired, please re-verify connection"
4. Group leader logs in, clicks "Re-verify Connection" (UC34)
5. If success, state → VERIFIED
6. Sync resumes
```

**Issue:** Step 3-4 requires:
- Notification Service (not documented)
- Email templates
- Frontend UI for re-verification

**Recommendation:** Phase 1 - Manual (admin notification), Phase 2 - Automated.

**Impact:** MEDIUM - User experience issue.

---

## 6. PERFORMANCE & SCALABILITY ISSUES

### 6.1 ❓ Concurrent Sync Jobs

**From SYNC_SERVICE_OVERVIEW.md:**

> **Future Enhancement:** Parallel Processing - Process multiple project configs concurrently (thread pool)

**Questions:**
1. Có enable concurrent sync ngay từ đầu không?
2. Thread pool size bao nhiêu?
3. Có risk của rate limiting khi concurrent requests không?

**Sequential Sync (Current):**
```java
for (ProjectConfigDto config : configs) {
    syncJiraIssuesForConfig(config);  // Blocking
}
```
Time: 50 configs × 30 seconds = **25 minutes**

**Parallel Sync:**
```java
ExecutorService executor = Executors.newFixedThreadPool(10);
configs.forEach(config -> 
    executor.submit(() -> syncJiraIssuesForConfig(config))
);
```
Time: 50 configs / 10 threads × 30 seconds = **2.5 minutes**

**Issue:** Rate limiting risk - 10 concurrent requests × 5 API calls = 50 requests/second (GitHub limit: no per-second limit nhưng Jira: 10 req/sec).

**Recommendation:** Start với thread pool = 5, monitor rate limits.

**Impact:** HIGH - Performance critical khi scale.

---

### 6.2 ❓ Database Connection Pool

**Questions:**
1. HikariCP settings có đủ cho concurrent sync không?
2. Connection pool size nên là bao nhiêu?
3. Có risk của connection exhaustion không?

**Recommendation:**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20          # For concurrent sync threads
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

**Impact:** MEDIUM - Database performance.

---

## 7. TESTING & OBSERVABILITY ISSUES

### 7.1 ❓ Test Data Strategy

**Questions:**
1. Có mock Jira/GitHub API cho integration tests không?
2. Có test data fixtures (sample JSON responses) không?
3. Có test với real Jira/GitHub sandbox environments không?

**Recommendation:**
```java
// src/test/resources/fixtures/jira_issues_response.json
// src/test/resources/fixtures/github_commits_response.json

@Test
public void testJiraSync_withMockApi() {
    String mockResponse = loadFixture("jira_issues_response.json");
    mockServer.expect(requestTo(containsString("/rest/api/3/search")))
              .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));
    
    // Run sync
    // Assert results
}
```

**Impact:** HIGH - Testing coverage.

---

### 7.2 ❓ Monitoring Alerts

**Questions:**
1. Có define alert rules không? (sync failure rate > 10%, latency > 5 minutes)
2. Có on-call rotation cho sync failures không?
3. Có dashboard cho sync status không?

**Recommendation:**
```yaml
# Prometheus Alert Rules
groups:
  - name: sync-service
    rules:
      - alert: SyncFailureRateHigh
        expr: rate(sync_jobs_total{status="FAILED"}[5m]) > 0.1
        annotations:
          summary: "Sync failure rate > 10%"
      
      - alert: SyncLatencyHigh
        expr: histogram_quantile(0.99, sync_duration_seconds_bucket) > 300
        annotations:
          summary: "Sync P99 latency > 5 minutes"
```

**Impact:** MEDIUM - Operational readiness.

---

## 8. SECURITY ISSUES

### 8.1 ❌ CRITICAL: Service Key Rotation

**From GRPC_INTEGRATION.md:**

```yaml
service:
  auth:
    service-key: ${SERVICE_AUTH_KEY}  # sync-service-secret-key-2026
```

**Questions:**
1. Có key rotation policy không?
2. Service key hard-coded trong Project Config Service (`ServiceAuthInterceptor`)?
3. Làm sao rotate key without downtime?

**Current Issue:**
```java
// In Project Config Service
private static final Map<String, String> VALID_SERVICES = Map.of(
    "sync-service", "sync-service-secret-key-2026"  // HARD-CODED
);
```

**Recommendation:** Store keys trong database hoặc secrets manager (Vault, AWS Secrets Manager):
```java
@Service
public class ServiceAuthService {
    
    @Autowired
    private ServiceKeyRepository serviceKeyRepository;
    
    public boolean validateServiceKey(String serviceName, String providedKey) {
        ServiceKey key = serviceKeyRepository.findByServiceName(serviceName)
                .orElseThrow(() -> new UnauthorizedException("Unknown service"));
        
        return key.getKeyHash().equals(hashKey(providedKey));
    }
}
```

**Impact:** HIGH - Security vulnerability.

---

## 9. UNRESOLVED DEPENDENCIES

### 9.1 Notification Service (Not Documented)

**Referenced in:**
- Token expiration notifications
- Sync failure alerts
- Admin notifications

**Status:** NOT IMPLEMENTED, NOT DOCUMENTED

**Questions:**
1. Có Notification Service trong roadmap không?
2. Nếu không, Sync Service tự gửi email (cần SMTP config)?
3. Notification channels: Email only, hoặc có Slack/Teams integration?

**Impact:** MEDIUM - User experience.

---

### 9.2 Reporting Service API Contract

**Referenced in:**
- Sync Service provides data for Reporting Service
- Reporting Service queries `unified_activities` table

**Questions:**
1. Reporting Service có access trực tiếp vào `sync_db` không?
2. Nếu không, cần expose REST/gRPC API từ Sync Service?
3. API contract là gì?

**Impact:** MEDIUM - Inter-service integration.

---

### 9.3 AI Service Integration

**Referenced in:**
- AI Service uses sync data for code quality analysis

**Questions:**
1. AI Service có access database trực tiếp không?
2. Có cần real-time data hoặc batch export?
3. Data format requirements?

**Impact:** LOW - Future enhancement.

---

## 10. DECISION LOG (TO BE FILLED BY TECH LEAD)

| Issue ID | Decision | Decided By | Date | Notes |
|----------|----------|------------|------|-------|
| 1.1 | TBD | - | - | Project Config database access strategy |
| 1.2 | TBD | - | - | Foreign key validation approach |
| 2.1 | Store current sprint only | Tech Lead | 2026-02-05 | Sprint history in metadata_json |
| 2.2 | TBD | - | - | GitHub email privacy handling |
| 2.3 | TBD | - | - | Rate limiting strategy |
| 3.1 | Use simple formula for MVP | Tech Lead | 2026-02-05 | AI enhancement in Phase 2 |
| 3.2 | TBD | - | - | Jira-GitHub link detection |
| 4.1 | TBD | - | - | Incremental vs full sync |
| 4.2 | Accept eventual consistency | Tech Lead | 2026-02-05 | 30-min delay acceptable |
| 5.1 | Use existing soft delete | Tech Lead | 2026-02-05 | No additional archiving |
| 5.2 | TBD | - | - | Token expiration notification |
| 6.1 | TBD | - | - | Concurrent sync configuration |
| 6.2 | TBD | - | - | Database pool sizing |
| 7.1 | TBD | - | - | Test data strategy |
| 7.2 | TBD | - | - | Monitoring alert rules |
| 8.1 | TBD | - | - | Service key rotation strategy |
| 9.1 | TBD | - | - | Notification service integration |
| 9.2 | TBD | - | - | Reporting Service API contract |
| 9.3 | Low priority | Tech Lead | 2026-02-05 | AI integration in Phase 2 |

---

## 11. BLOCKING vs NON-BLOCKING

### 🚫 BLOCKING ISSUES (Must resolve before coding)

1. **Issue 1.1:** Project Config database access strategy
2. **Issue 8.1:** Service key rotation strategy (security critical)
3. **Issue 6.1:** Concurrent sync configuration (performance critical)

### ⚠️ HIGH PRIORITY (Resolve during Sprint 1)

1. **Issue 2.2:** GitHub email privacy handling
2. **Issue 3.2:** Duplicate detection (affects accuracy)
3. **Issue 4.1:** Incremental sync strategy

### 📋 MEDIUM PRIORITY (Can defer to Sprint 2)

1. **Issue 2.3:** Rate limiting monitoring
2. **Issue 5.2:** Token expiration notification
3. **Issue 9.2:** Reporting Service integration

### 💡 LOW PRIORITY (Future enhancements)

1. **Issue 3.1:** Advanced effort calculation
2. **Issue 7.2:** Advanced monitoring dashboards
3. **Issue 9.3:** AI Service integration

---

## 12. RECOMMENDED ACTIONS

### For Product Owner:
1. Review decision log và xác nhận business requirements
2. Define priorities: MVP features vs future enhancements
3. Clarify user acceptance criteria for contribution calculation

### For Technical Lead:
1. Resolve blocking issues (database access, service auth)
2. Define service boundaries và API contracts
3. Review security implications (key rotation, token handling)

### For Development Team:
1. **DO NOT START CODING** until blocking issues resolved
2. Review tài liệu này và add questions nếu có
3. Prepare test environments (Jira sandbox, GitHub test org)

---

## Summary

**Total Issues Identified:** 21  
**Blocking:** 3  
**High Priority:** 3  
**Medium Priority:** 3  
**Low Priority:** 3  
**Decisions Made:** 4  
**Decisions Pending:** 17

**Next Steps:**
1. Schedule architecture review meeting
2. Technical Lead resolve blocking issues
3. Update this document với decisions
4. Begin implementation when all blocking issues resolved

**Document Owner:** Technical Architect  
**Review Cycle:** Weekly until all issues resolved  
**Status:** 🔴 BLOCKING - Implementation cannot proceed
