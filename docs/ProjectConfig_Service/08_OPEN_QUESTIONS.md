# OPEN QUESTIONS – PROJECT CONFIG SERVICE

## Overview

Tài liệu này liệt kê các câu hỏi và ambiguities được phát hiện trong quá trình phân tích SRS Section 6.3.4 (Project Configs) và các use cases liên quan (UC04, UC10). Các câu hỏi này cần được clarify trước khi implementation.

---

## 1. Database Schema Questions

### Q1.1: Group Deletion Cascade Behavior

**Context:** Trong SRS Section 6.3.4, `project_configs` table có `group_id` là FOREIGN KEY tới `groups` table.

**Question:** Khi một group bị xóa (soft delete), config của group đó nên xử lý như thế nào?

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| CASCADE SOFT DELETE | Config cũng bị soft delete | - Maintain data integrity<br>- Có thể restore cùng group | - Phức tạp hơn |
| RESTRICT | Không cho xóa group nếu còn config | - Đơn giản<br>- Tránh orphaned data | - UX không tốt |
| SET NULL | Set `group_id = NULL` | - Giữ config history | - Violate UNIQUE constraint |
| DO NOTHING | Giữ config, orphaned | - Đơn giản nhất | - Data integrity issue |

**Recommendation:** CASCADE SOFT DELETE (Option 1)

**Rationale:** 
- Khi group không còn active, config của group đó cũng không có ý nghĩa
- Có thể restore config khi restore group
- Maintain referential integrity

**Implementation:**
```java
// In GroupService.deleteGroup()
public void deleteGroup(UUID groupId, UUID deletedBy) {
    // 1. Soft delete associated config first
    projectConfigService.deleteConfigByGroupId(groupId, deletedBy);
    
    // 2. Then soft delete group
    Group group = groupRepository.findById(groupId)
        .orElseThrow(() -> new GroupNotFoundException(groupId));
    group.setDeletedAt(LocalDateTime.now());
    group.setDeletedBy(deletedBy);
    groupRepository.save(group);
}
```

**Required Clarification From:** Business Analyst / Product Owner

---

### Q1.2: Config Uniqueness After Soft Delete

**Context:** `group_id` có UNIQUE constraint để enforce "1 group = 1 config" rule.

**Question:** Sau khi soft delete config, có cho phép tạo config mới cho cùng group đó không?

**Scenario:**
```
1. Group G1 có config C1
2. User delete config C1 → C1.deleted_at = NOW
3. User tạo config mới C2 cho group G1
4. Database: 2 records cùng group_id → Violate UNIQUE constraint?
```

**Options:**

| Option | Implementation | Pros | Cons |
|--------|----------------|------|------|
| Partial UNIQUE | `UNIQUE(group_id) WHERE deleted_at IS NULL` | - Cho phép tạo config mới<br>- Giữ history | - Phụ thuộc database support |
| Restore Instead | Không cho tạo mới, phải restore C1 | - Đơn giản<br>- Không violate constraint | - UX phức tạp |
| Hard Delete | Delete config vĩnh viễn | - Đơn giản<br>- Không conflict | - Mất history |

**Recommendation:** Partial UNIQUE constraint (Option 1)

**PostgreSQL Syntax:**
```sql
CREATE UNIQUE INDEX idx_project_configs_group_id_active 
ON project_configs(group_id) 
WHERE deleted_at IS NULL;
```

**Required Clarification From:** Database Administrator / Tech Lead

---

### Q1.3: Audit Log Table Location

**Context:** BR-PC-019 yêu cầu audit log cho các config operations.

**Question:** Audit logs nên lưu ở đâu?

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| Same Database | `audit_logs` table trong `samt_projectconfig` | - Đơn giản<br>- Transaction support | - Có thể bị xóa cùng database |
| Separate Database | Dedicated audit database | - Bảo mật cao<br>- Không bị xóa | - Phức tạp hơn |
| External Service | Gửi logs đến logging service (ELK, Splunk) | - Centralized logging<br>- Powerful search | - Dependency on external service |

**Recommendation:** Option 1 (Same Database) cho MVP, sau đó migrate sang Option 3

**Required Clarification From:** Security Team / Tech Lead

---

## 2. Security & Encryption Questions

### Q2.1: Encryption Key Management

**Context:** BR-PC-004 yêu cầu AES-256-GCM encryption cho tokens.

**Question:** Secret key nên được quản lý như thế nào?

**Options:**

| Option | Storage | Pros | Cons |
|--------|---------|------|------|
| Environment Variable | `ENCRYPTION_SECRET_KEY` | - Đơn giản<br>- Standard practice | - Exposed nếu env leaked |
| Secret Manager | AWS Secrets Manager, HashiCorp Vault | - Bảo mật cao<br>- Key rotation support | - Phức tạp<br>- Cost |
| Config Server | Spring Cloud Config Server | - Centralized<br>- Encrypted config | - Single point of failure |

**Recommendation:** 
- **MVP:** Environment Variable (Option 1)
- **Production:** Secret Manager (Option 2)

**Additional Questions:**
1. Key rotation strategy? (Monthly? Quarterly? Never?)
2. Khi rotate key, re-encrypt old tokens hay chỉ apply cho tokens mới?
3. Backup key ở đâu nếu key bị mất?

**Required Clarification From:** Security Team / DevOps Lead

---

### Q2.2: Token Expiration Policy

**Context:** Jira API Token và GitHub Token không có expiration built-in.

**Question:** Service có cần implement token expiration/rotation policy không?

**Considerations:**
- Jira tokens có thể valid vĩnh viễn nếu không revoke
- GitHub tokens có thể set expiration khi tạo (classic tokens không expire by default)

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| No Expiration | Tokens valid cho đến khi user update | - Đơn giản<br>- Ít maintenance | - Security risk nếu token leaked |
| Periodic Rotation | Force user update tokens mỗi X tháng | - Tăng security<br>- Best practice | - UX phức tạp<br>- Notification required |
| Automatic Rotation | Service tự động rotate tokens | - Tốt nhất cho security<br>- Transparent to user | - Phức tạp<br>- Jira/GitHub có support API không? |

**Recommendation:** Option 1 (No Expiration) cho MVP

**Future Enhancement:** Option 2 với notification system

**Required Clarification From:** Security Team / Product Owner

---

### Q2.3: Token Decryption Error Handling

**Context:** BR-PC-023 đề xuất graceful degradation khi decrypt failed.

**Question:** Nếu không decrypt được token (key bị mất, data corrupted), làm sao user có thể recover?

**Options:**

| Option | User Action | Pros | Cons |
|--------|-------------|------|------|
| Force Re-enter | Yêu cầu user nhập lại tokens | - Đơn giản<br>- User có control | - Mất tokens cũ |
| Show Warning | Hiển thị warning, user tự quyết định | - UX tốt hơn | - User có thể ignore |
| Admin Override | Admin có thể reset config | - Support option | - Security concern |

**Recommendation:** Option 1 + Admin notification

**UI Flow:**
```
1. GET /api/project-configs/{id} → Returns "***DECRYPTION_FAILED***"
2. UI shows error: "Cannot decrypt tokens. Please update your credentials."
3. User clicks "Update Credentials"
4. PUT /api/project-configs/{id} with new tokens
5. Old encrypted data replaced
```

**Required Clarification From:** Product Owner / UX Designer

---

## 3. API Design Questions

### Q3.1: Verify Endpoint Timeout Strategy

**Context:** BR-PC-011 đề xuất timeout 10s cho mỗi API call (Jira, GitHub).

**Question:** Nếu cả 2 calls đều timeout, nên retry không?

**Options:**

| Option | Behavior | Pros | Cons |
|--------|----------|------|------|
| No Retry | Return timeout status ngay | - Fast feedback<br>- Tránh abuse | - Có thể là network hiccup |
| Automatic Retry | Retry 1-2 lần với exponential backoff | - Tăng success rate | - Slow response time |
| User-Initiated Retry | User click "Retry" button | - User control<br>- Tránh spam | - Extra step |

**Recommendation:** Option 1 (No Retry) + Option 3 (User can retry from UI)

**Implementation:**
```json
{
  "jiraConnection": {
    "status": "TIMEOUT",
    "message": "Connection timeout after 10 seconds",
    "retryable": true
  },
  "githubConnection": {
    "status": "TIMEOUT",
    "message": "Connection timeout after 10 seconds",
    "retryable": true
  }
}
```

**Required Clarification From:** Product Owner / Tech Lead

---

### Q3.2: Partial Update Semantics

**Context:** `PUT /api/project-configs/{id}` API contract cho phép partial updates.

**Question:** Nếu chỉ update `jiraHostUrl`, có cần verify `jiraApiToken` vẫn work với host mới không?

**Scenario:**
```
Current Config:
- jiraHostUrl: https://oldproject.atlassian.net
- jiraApiToken: ATATT_old_token

Update Request:
{
  "jiraHostUrl": "https://newproject.atlassian.net"
}

Issue: Old token có thể không work với new host
```

**Options:**

| Option | Behavior | Pros | Cons |
|--------|----------|------|------|
| Auto-Verify | Verify connection before save | - Prevent invalid config | - Slow update operation |
| Warning Only | Save but return warning | - Fast update<br>- Flexibility | - Invalid config có thể saved |
| Reject | Yêu cầu update cả token | - Ensure consistency | - UX không tốt |

**Recommendation:** Option 2 (Warning Only)

**Response:**
```json
{
  "configId": "...",
  "jiraHostUrl": "https://newproject.atlassian.net",
  "warnings": [
    {
      "field": "jiraHostUrl",
      "message": "Jira host URL was changed. Please verify connection to ensure token is valid for new host."
    }
  ]
}
```

**Required Clarification From:** Product Owner

---

### Q3.3: List Configs Pagination Default

**Context:** `GET /api/project-configs/lecturer/me` có pagination.

**Question:** Default page size nên là bao nhiêu?

**Considerations:**
- Lecturer có thể supervise nhiều groups (5-20 groups thường)
- Mỗi group có tối đa 1 config
- Data size khá nhỏ (chỉ metadata, không có large fields)

**Options:**

| Page Size | Use Case |
|-----------|----------|
| 10 | Conservative, mobile-friendly |
| 20 | Standard (recommended) |
| 50 | Desktop, power users |
| 100 | Admin, bulk operations |

**Recommendation:** Default = 20, max = 100

**Required Clarification From:** Product Owner / UX Designer

---

## 4. Business Logic Questions

### Q4.1: Config Creation Authorization

**Context:** BR-PC-017 cho phép TEAM_LEADER tạo config cho "own group".

**Question:** "Own group" được xác định như thế nào?

**Scenarios:**

| Scenario | Is "Own Group"? | Rationale |
|----------|-----------------|-----------|
| User là TEAM_LEADER của group | ✅ YES | Clear |
| User là STUDENT của group | ❌ NO | Not leader |
| User là LECTURER supervising group | ❓ TBD | Can lecturer create config for students? |
| User là ADMIN | ✅ YES (all groups) | Admin privilege |

**Recommendation:** 
- TEAM_LEADER: Only groups where `role = TEAM_LEADER` in `user_groups` table
- LECTURER: Read-only, cannot create
- ADMIN: All groups

**Implementation:**
```java
public boolean isTeamLeaderOfGroup(UUID userId, UUID groupId) {
    return userGroupRepository.existsByUserIdAndGroupIdAndRole(
        userId, groupId, Role.TEAM_LEADER
    );
}
```

**Required Clarification From:** Product Owner / Business Analyst

---

### Q4.2: Multiple Team Leaders Scenario

**Context:** Trong SRS UC11, có nhắc đến "Team Leader hoặc thành viên có quyền".

**Question:** Nếu 1 group có nhiều TEAM_LEADER (co-leaders), ai có quyền manage config?

**Scenarios:**

| Scenario | Solution |
|----------|----------|
| Group có 2 team leaders | Cả 2 đều có quyền manage config |
| Leader A tạo config, Leader B update | ✅ Allowed |
| Leader A tạo config, Leader B delete | ✅ Allowed (nhưng cần confirm dialog) |

**Recommendation:** All team leaders của cùng group có equal rights

**Audit Logging:** Log rõ ai thực hiện action để avoid confusion

**Required Clarification From:** Product Owner

---

### Q4.3: Config Deletion vs Sync Service Dependency

**Context:** Sync Service (UC10) cần config để sync Jira/GitHub data.

**Question:** Nếu user delete config khi sync đang chạy, xử lý như thế nào?

**Scenarios:**

| Scenario | Behavior | Risk |
|----------|----------|------|
| Delete config → Sync crashes | Stop sync immediately | Data inconsistency |
| Delete config → Sync finishes | Allow sync complete | Delayed deletion |
| Prevent deletion while syncing | Return `409 CONFLICT` | UX friction |

**Recommendation:** Option 3 (Prevent deletion while syncing)

**Implementation:**
```java
// Before delete config
if (syncService.isSyncRunning(groupId)) {
    throw new ConfigInUseException(
        "Cannot delete config while sync is in progress. Please wait or stop sync first."
    );
}
```

**Alternative:** Soft delete + Sync service check `deleted_at IS NULL`

**Required Clarification From:** Tech Lead / Sync Service Team

---

## 5. Integration Questions

### Q5.1: Jira API Version Support

**Context:** SRS chỉ nói "Jira Software API" nhưng không specify version.

**Question:** Service nên support Jira API version nào?

**Jira API Versions:**
- **Jira Cloud REST API v3** (Latest, recommended)
- **Jira Cloud REST API v2** (Deprecated but still supported)
- **Jira Server/Data Center API** (Different authentication)

**Recommendation:** Support Jira Cloud REST API v3 only

**Verification Endpoint:**
```
GET https://{jiraHostUrl}/rest/api/3/myself
Authorization: Bearer {jiraApiToken}
```

**Question for Clarification:**
- Có cần support Jira Server/Data Center không? (on-premise)
- Có cần fallback sang v2 API nếu v3 fail không?

**Required Clarification From:** Product Owner / Integration Team

---

### Q5.2: GitHub API Rate Limiting

**Context:** GitHub API có rate limits:
- **Authenticated:** 5,000 requests/hour
- **Unauthenticated:** 60 requests/hour

**Question:** Service có cần implement GitHub rate limit tracking không?

**Considerations:**
- Verify endpoint call GitHub API để check connection
- Sync Service cũng call GitHub API nhiều
- Multiple users verify cùng lúc có thể hit rate limit

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| No Tracking | Let GitHub API return 429 | - Đơn giản | - User confused |
| Track & Warn | Track remaining quota, warn user | - Proactive | - Extra complexity |
| Service-Level Token | Dùng 1 token service-wide | - Centralized quota | - Security concern |

**Recommendation:** Option 2 (Track & Warn)

**Response Header from GitHub:**
```
X-RateLimit-Limit: 5000
X-RateLimit-Remaining: 4999
X-RateLimit-Reset: 1643457600
```

**Service Response:**
```json
{
  "githubConnection": {
    "status": "SUCCESS",
    "rateLimitRemaining": 4999,
    "rateLimitResetAt": "2026-01-29T16:00:00Z",
    "warning": "GitHub API rate limit: 4999/5000 remaining"
  }
}
```

**Required Clarification From:** Tech Lead / GitHub Integration Team

---

### Q5.3: External API Error Mapping

**Context:** Jira và GitHub APIs trả về nhiều error codes khác nhau.

**Question:** Service nên map external errors như thế nào?

**Example Jira Errors:**
- `401 Unauthorized` → Invalid token
- `403 Forbidden` → Insufficient permissions
- `404 Not Found` → Project not found
- `429 Too Many Requests` → Rate limited
- `500 Internal Server Error` → Jira service down

**Example GitHub Errors:**
- `401 Bad credentials` → Invalid token
- `404 Not Found` → Repository not found or private
- `403 Forbidden` → Token scopes insufficient

**Mapping Strategy:**

| External Status | Internal Error Code | Message |
|-----------------|---------------------|---------|
| 401 | INVALID_CREDENTIALS | "Authentication failed: Invalid API token" |
| 403 | INVALID_CREDENTIALS | "Insufficient permissions: Token lacks required scopes" |
| 404 | CONNECTION_FAILED | "Resource not found: Project/Repo does not exist or is private" |
| 429 | RATE_LIMIT_EXCEEDED | "External API rate limit exceeded" |
| 500 | CONNECTION_FAILED | "External service unavailable" |

**Required Clarification From:** Tech Lead

---

## 6. Performance & Scalability Questions

### Q6.1: Concurrent Verify Requests

**Context:** BR-PC-010 giới hạn 10 verify requests/minute per user.

**Question:** Có cần limit concurrent verify requests (đang chạy đồng thời)?

**Scenario:**
```
User opens 10 browser tabs
Each tab calls POST /api/project-configs/verify
All 10 requests hitting backend simultaneously
Each verify takes ~2 seconds (call Jira + GitHub)
→ Backend có thể handle không?
```

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| No Limit | Allow all concurrent | - Simple | - Resource exhaustion |
| Limit = 2 | Max 2 concurrent per user | - Balanced | - May block legitimate use |
| Queue System | Queue additional requests | - Fair<br>- No rejection | - Complex implementation |

**Recommendation:** Option 2 (Max 2 concurrent per user)

**Implementation:** Semaphore-based concurrency control

**Required Clarification From:** Tech Lead / Performance Engineer

---

### Q6.2: Encryption Performance

**Context:** AES-256-GCM encryption/decryption for every request.

**Question:** Có cần cache decrypted tokens để tăng performance không?

**Considerations:**
- Mỗi GET request phải decrypt 2 tokens (Jira + GitHub)
- Decrypt operation có thể slow (vài milliseconds)
- Security vs Performance trade-off

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| No Cache | Decrypt mỗi request | - Secure<br>- Simple | - Slower response |
| In-Memory Cache | Cache 5-10 minutes | - Fast<br>- Reduce CPU | - Security risk (memory dump) |
| Request-Scoped Cache | Cache trong 1 request lifecycle | - Balanced | - Limited benefit |

**Recommendation:** Option 3 (Request-Scoped Cache) cho MVP

**Security Note:** Cache NEVER persist to disk hoặc external cache (Redis)

**Required Clarification From:** Security Team / Tech Lead

---

## 7. Testing Questions

### Q7.1: External API Mocking Strategy

**Context:** Verify endpoint gọi Jira và GitHub APIs.

**Question:** Integration tests nên mock external APIs như thế nào?

**Options:**

| Option | Tool | Pros | Cons |
|--------|------|------|------|
| WireMock | HTTP mocking | - Realistic<br>- No network calls | - Setup overhead |
| Mockito | Java mocking | - Simple<br>- Fast | - Less realistic |
| Testcontainers | Docker-based mock servers | - Most realistic | - Slow<br>- Resource intensive |

**Recommendation:** WireMock for integration tests

**Test Scenarios:**
1. Successful connection (200 OK)
2. Invalid credentials (401)
3. Timeout (no response)
4. Rate limit (429)
5. Server error (500)

**Required Clarification From:** QA Lead / Tech Lead

---

### Q7.2: Encryption Key for Tests

**Context:** Tests cần valid encryption key để test encrypt/decrypt.

**Question:** Test environment nên dùng key gì?

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| Hardcoded Key | Fixed key in test code | - Simple<br>- Reproducible | - Security warning in code scan |
| Test Profile Config | Key in `application-test.yml` | - Standard practice | - Still committed to git |
| Generate Per Test Run | Random key each run | - Most secure | - Hard to debug |

**Recommendation:** Option 2 with clear documentation

**File:** `src/test/resources/application-test.yml`
```yaml
encryption:
  secret-key: "dGVzdC1lbmNyeXB0aW9uLWtleS0zMi1ieXRlcw==" # Base64 test key
```

**Comment in code:**
```java
// WARNING: This is a TEST-ONLY key. NEVER use in production.
```

**Required Clarification From:** Tech Lead / Security Team

---

## 8. Deployment Questions

### Q8.1: Service Discovery Registration

**Context:** Service sử dụng Eureka for service discovery.

**Question:** Internal endpoints có cần register riêng không?

**Scenario:**
```
- /api/project-configs/** → Public endpoints (qua API Gateway)
- /internal/project-configs/** → Internal endpoints (không qua Gateway)
```

**Options:**

| Option | Description | Pros | Cons |
|--------|-------------|------|------|
| Same Registration | 1 Eureka instance | - Simple | - Internal exposed via Gateway |
| Separate Ports | Public (8083), Internal (8084) | - Clear separation | - 2 ports to manage |
| Network-Level | Internal port không accessible từ outside | - Secure | - Infrastructure complexity |

**Recommendation:** Option 3 (Network-Level isolation)

**Implementation:**
- Public port `8083` → Exposed via API Gateway
- Internal port không exposed
- Internal services communicate trực tiếp (không qua Gateway)

**Required Clarification From:** DevOps Lead / Infrastructure Team

---

### Q8.2: Database Migration Strategy

**Context:** Flyway migrations cho database schema.

**Question:** Nếu migration fail ở production, rollback strategy là gì?

**Scenarios:**

| Scenario | Rollback Strategy |
|----------|-------------------|
| V1 → V2 migration failed | Flyway auto-rollback transaction |
| V2 applied nhưng có bug | Manual rollback script (DOWN migration) |
| Data corrupted | Database backup restore |

**Recommendation:** 
1. Test migrations thoroughly ở staging
2. Backup database trước mỗi production deployment
3. Có DOWN migration scripts cho mọi UP migration

**Flyway Scripts:**
```
db/migration/
├── V1__create_project_configs.sql (UP)
├── V1__rollback.sql (DOWN - manual)
├── V2__add_deleted_at.sql (UP)
└── V2__rollback.sql (DOWN - manual)
```

**Required Clarification From:** DBA / DevOps Lead

---

## 9. Monitoring & Observability Questions

### Q9.1: Metrics to Track

**Context:** Service cần monitoring cho production.

**Question:** Những metrics nào quan trọng nhất cần track?

**Proposed Metrics:**

| Metric | Type | Purpose |
|--------|------|---------|
| `config.created.count` | Counter | Track config creation rate |
| `config.verify.duration` | Histogram | Monitor verify performance |
| `config.verify.success.rate` | Gauge | Success rate of verifications |
| `encryption.duration` | Histogram | Encryption performance |
| `external.api.errors` | Counter | Jira/GitHub API errors |
| `rate.limit.exceeded` | Counter | Rate limiting effectiveness |

**Tools:**
- Micrometer (metrics collection)
- Prometheus (storage)
- Grafana (visualization)

**Required Clarification From:** DevOps Lead / SRE Team

---

### Q9.2: Alerting Thresholds

**Context:** Production alerts cho critical issues.

**Question:** Alert thresholds nên set như thế nào?

**Proposed Alerts:**

| Alert | Condition | Severity | Action |
|-------|-----------|----------|--------|
| High Verify Failure Rate | > 50% failed verifications in 5 min | WARNING | Check external APIs |
| Encryption Errors | > 10 errors in 5 min | CRITICAL | Check encryption key |
| External API Unavailable | All verifications timeout | CRITICAL | Check Jira/GitHub status |
| Database Connection Lost | No DB queries success | CRITICAL | Check DB health |

**Required Clarification From:** SRE Team / On-Call Engineer

---

## 10. Compliance & Legal Questions

### Q10.1: Token Storage Compliance

**Context:** Service lưu API tokens của third-party services.

**Question:** Có cần comply với regulations nào? (GDPR, SOC2, etc.)

**Considerations:**
- API tokens là sensitive data
- Có thể access private repositories và projects
- Encryption ở rest và transit là required

**Compliance Checklist:**
- [x] Encryption at rest (AES-256-GCM)
- [x] Encryption in transit (HTTPS)
- [x] Access control (JWT, RBAC)
- [x] Audit logging
- [ ] Data retention policy?
- [ ] Right to deletion? (GDPR)
- [ ] Data export capability?

**Required Clarification From:** Legal Team / Compliance Officer

---

### Q10.2: Data Residency

**Context:** Service deployed ở region nào?

**Question:** Có requirements về data residency không? (e.g., EU data phải stay trong EU)

**Options:**

| Deployment | Compliance | Cost |
|------------|------------|------|
| Single Region (US) | - Simple | $ |
| Multi-Region | - GDPR compliant | $$$ |
| Hybrid | - Balanced | $$ |

**Required Clarification From:** Legal Team / Infrastructure Team

---

## Summary Table

| Category | Question Count | Priority |
|----------|----------------|----------|
| Database Schema | 3 | HIGH |
| Security & Encryption | 3 | CRITICAL |
| API Design | 3 | MEDIUM |
| Business Logic | 3 | HIGH |
| Integration | 3 | HIGH |
| Performance & Scalability | 2 | MEDIUM |
| Testing | 2 | MEDIUM |
| Deployment | 2 | HIGH |
| Monitoring & Observability | 2 | MEDIUM |
| Compliance & Legal | 2 | HIGH |
| **Total** | **25** | - |

---

## Priority Legend

- **CRITICAL:** Must be resolved before implementation starts
- **HIGH:** Should be resolved before feature completion
- **MEDIUM:** Can be deferred to future iterations
- **LOW:** Nice to have, not blocking

---

## Next Steps

1. **Review Meeting:** Schedule với Product Owner, Tech Lead, Security Team
2. **Document Decisions:** Update this document với final decisions
3. **Update SRS:** Nếu cần, propose SRS updates
4. **Create Subtasks:** Break down clarified requirements thành implementation tasks
5. **Begin Implementation:** Only after critical questions resolved

---

**Document Version:** 1.0  
**Last Updated:** 2026-01-29  
**Status:** DRAFT - Pending Review
