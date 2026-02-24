# API Contract – Project Config Service

**Version:** 1.0  
**Base URL:** `/api`  
**Date:** February 18, 2026

---

## Standard Response Formats

### Success Response

```json
{
  "data": { ... },
  "timestamp": "2026-02-18T10:30:00Z"
}
```

### Error Response

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid Jira host URL format",
    "field": "jiraHostUrl",
    "details": { ... }
  },
  "timestamp": "2026-02-18T10:30:00Z"
}
```

### Standard Error Codes

| HTTP Status | Error Code | Usage |
|-------------|------------|-------|
| 400 | `VALIDATION_ERROR` | Input validation failed |
| 400 | `INVALID_STATE_TRANSITION` | State machine violation |
| 401 | `UNAUTHORIZED` | No token or invalid token |
| 403 | `FORBIDDEN` | Insufficient permissions |
| 404 | `CONFIG_NOT_FOUND` | Config not found |
| 404 | `GROUP_NOT_FOUND` | Group not found (from User-Group Service) |
| 409 | `CONFIG_ALREADY_EXISTS` | Group already has config |
| 409 | `CONFLICT` | Concurrent update conflict |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error |
| 503 | `SERVICE_UNAVAILABLE` | Dependency service down |
| 504 | `GATEWAY_TIMEOUT` | External API timeout |

---

## API Idempotency

All API operations are **naturally idempotent** based on project ID:

| Operation | HTTP Method | Idempotent | Safe Retry |
|-----------|-------------|------------|------------|
| Create | POST | ❌ (409 if exists) | ✅ (retry on network timeout) |
| Update | PUT | ✅ (same result) | ✅ (safe to retry) |
| Restore | POST | ✅ (state-based) | ✅ (no duplicate side effects) |
| Mark Complete | POST | ✅ (no-op if already COMPLETED) | ✅ (safe to retry) |
| Delete | DELETE | ✅ (404 if not exists) | ✅ (safe to retry) |
| Get | GET | ✅ (read-only) | ✅ (safe to retry) |

**Client Retry Guidance:**
- **Network timeout:** Retry any operation (all are safe)
- **409 Conflict:** Do NOT retry (indicates concurrent modification)
- **500 Internal Error:** Retry idempotent operations (PUT/POST restore/DELETE) after delay
- **400 Bad Request:** Do NOT retry (client error, fix request first)

**Optimistic Locking:** All updates use `version` field. Client must retry with latest version on 409.

---

## Public API Endpoints

### 1. POST `/api/project-configs`

**Description:** Create configuration for group

**Authorization:** Bearer Token (STUDENT with LEADER role in group, or ADMIN)

> **Note:** LEADER is not a system role. It's a group membership attribute. User must be a STUDENT with LEADER role in the target group, OR have system-wide ADMIN role.

**Request Body:**
```json
{
  "groupId": 1,
  "jiraHostUrl": "https://domain.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/owner/repo",
  "githubToken": "ghp_1234567890abcdefghijklmnopqr"
}
```

**Validation Rules:**

| Field | Constraints |
|-------|-------------|
| `groupId` | MUST: Valid Long (BIGINT), NOT NULL |
| `jiraHostUrl` | MUST: Valid URL, `.atlassian.net` domain, max 255 chars |
| `jiraApiToken` | MUST: 100-500 chars, starts with `ATATT` |
| `githubRepoUrl` | MUST: `https://github.com/{owner}/{repo}` format |
| `githubToken` | MUST: Starts with `ghp_`, 40-255 chars |

**Response 201 Created:**
```json
{
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": 1,
    "jiraHostUrl": "https://domain.atlassian.net",
    "jiraApiToken": "***ab12",
    "githubRepoUrl": "https://github.com/owner/repo",
    "githubToken": "ghp_***xyz9",
    "state": "DRAFT",
    "lastVerifiedAt": null,
    "createdAt": "2026-02-18T10:30:00Z",
    "updatedAt": "2026-02-18T10:30:00Z"
  },
  "timestamp": "2026-02-18T10:30:00Z"
}
```

**Business Rules:**
- Group MUST exist and not be deleted (verified via User-Group Service gRPC)
- User MUST be STUDENT with LEADER role in group, OR have ADMIN role
- Group can have ONLY ONE config (unique constraint)
- Tokens encrypted with AES-256-GCM before storage
- Initial state = `DRAFT`

**Error Responses:**

```json
// 403 - Not group leader
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Only group leader (STUDENT with LEADER role) can create config"
  }
}

// 404 - Group not found
{
  "error": {
    "code": "GROUP_NOT_FOUND",
    "message": "Group not found or deleted"
  }
}

// 409 - Config exists
{
  "error": {
    "code": "CONFIG_ALREADY_EXISTS",
    "message": "Group already has a configuration"
  }
}
```

---

### 2. GET `/api/project-configs/{id}`

**Description:** Get configuration (tokens masked)

**Authorization:** Bearer Token (AUTHENTICATED)

**Path Parameters:**
- `id` (UUID): Configuration ID

**Response 200 OK:**
```json
{
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": 1,  // BIGINT (Long)
    "jiraHostUrl": "https://domain.atlassian.net",
    "jiraApiToken": "***ab12",
    "githubRepoUrl": "https://github.com/owner/repo",
    "githubToken": "ghp_***xyz9",
    "state": "VERIFIED",
    "lastVerifiedAt": "2026-02-18T11:00:00Z",
    "createdAt": "2026-02-18T10:30:00Z",
    "updatedAt": "2026-02-18T11:00:00Z"
  }
}
```

**Token Masking:**
- Jira: `***ab12` (last 4 chars, or `****` if < 4 chars)
- GitHub: `ghp_***xyz9` (prefix + last 4 chars, or `ghp_****` if < 4 chars)

**Authorization:**
- ADMIN: View any config
- STUDENT with LEADER role in group: View own group config
- STUDENT with MEMBER role in group: 403 FORBIDDEN

**Error Responses:**

```json
// 404 - Not found
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration not found"
  }
}

// 403 - Not authorized
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Not authorized to view this configuration"
  }
}
```

---

### 3. PUT `/api/project-configs/{id}`

**Description:** Update configuration

**Authorization:** Bearer Token (LEADER or ADMIN)

**Request Body (all fields optional):**
```json
{
  "jiraHostUrl": "https://new-domain.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/new-owner/repo",
  "githubToken": "ghp_9876543210zyxwvutsrqponmlkji"
}
```

**Response 200 OK:**
```json
{
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "state": "DRAFT",
    "lastVerifiedAt": null,
    "invalidReason": "Configuration updated, verification required",
    "updatedAt": "2026-02-18T13:00:00Z",
    ...
  }
}
```

**Business Rules:**
- If tokens/URLs updated → state transitions to `DRAFT`
- Cannot update deleted config (404)
- User MUST be LEADER or ADMIN

**State Transitions:**

| Updated Fields | State Before | State After |
|----------------|--------------|-------------|
| Tokens/URLs | VERIFIED | DRAFT |
| Tokens/URLs | INVALID | DRAFT |
| Tokens/URLs | DRAFT | DRAFT |

**Note:** After update to DRAFT, verification required before state can transition to VERIFIED or INVALID.

---

### 4. DELETE `/api/project-configs/{id}`

**Description:** Soft delete configuration

**Authorization:** Bearer Token (LEADER or ADMIN)

**Response 200 OK:**
```json
{
  "data": {
    "message": "Configuration deleted successfully",
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "deletedAt": "2026-02-18T14:00:00Z",
    "retentionDays": 90
  }
}
```

**Business Rules:**
- Soft delete only (set `deleted_at`, `deleted_by`)
- State transitions to `DELETED`
- Retention: 90 days before hard delete
- Idempotent operation

---

### 5. POST `/api/project-configs/{id}/verify`

**Description:** Test connection to Jira/GitHub

**Authorization:** Bearer Token (LEADER or ADMIN)

**Response 200 OK (Success):**
```json
{
  "data": {
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "state": "VERIFIED",
    "verificationResults": {
      "jira": {
        "status": "SUCCESS",
        "message": "Connected successfully to Jira",
        "testedAt": "2026-02-18T15:00:00Z",
        "userEmail": "leader@university.edu"
      },
      "github": {
        "status": "SUCCESS",
        "message": "Connected successfully to GitHub",
        "testedAt": "2026-02-18T15:00:01Z",
        "repoName": "owner/repo",
        "hasWriteAccess": true
      }
    },
    "lastVerifiedAt": "2026-02-18T15:00:01Z"
  }
}
```

**Response 200 OK (Failure):**
```json
{
  "data": {
    "state": "INVALID",
    "verificationResults": {
      "jira": { "status": "SUCCESS", ... },
      "github": {
        "status": "FAILED",
        "message": "Invalid token or repository not found",
        "error": "401 Unauthorized"
      }
    },
    "invalidReason": "GitHub: Invalid token or repository not found"
  }
}
```

**Verification Steps:**
1. Decrypt tokens
2. Test Jira: `GET {jiraHostUrl}/rest/api/3/myself` (10s timeout)
3. Test GitHub: `GET https://api.github.com/repos/{owner}/{repo}` (10s timeout)
4. Update state: Both SUCCESS → `VERIFIED`, Any FAILED → `INVALID`

**State Transitions:**
- `DRAFT` → `VERIFIED` (both tests pass)
- `DRAFT` → `INVALID` (any test fails)
- `INVALID` → `VERIFIED` (re-verify after fixing credentials)
- `VERIFIED` → `INVALID` (rare: token revoked externally)

**Business Rules:**
- Verification in SEPARATE transaction (no rollback on failure)
- Total timeout: 20 seconds
- Cannot verify deleted config (404)

**Error Responses:**

```json
// 504 - Timeout
{
  "error": {
    "code": "GATEWAY_TIMEOUT",
    "message": "Verification timeout"
  }
}
```

---

### 6. POST `/api/admin/project-configs/{id}/restore`

**Description:** Restore deleted configuration

**Authorization:** Bearer Token (ADMIN only)

**Response 200 OK:**
```json
{
  "data": {
    "message": "Configuration restored successfully",
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "restoredAt": "2026-02-18T16:00:00Z",
    "state": "DRAFT"
  }
}
```

**Business Rules:**
- ADMIN only
- Set `deleted_at = NULL`, state = `DRAFT`
- Requires re-verification
- Cannot restore if hard deleted (90 days expired)

---

## Internal API Endpoints

### 7. GET `/internal/project-configs/{id}/tokens`

**Description:** Get decrypted tokens (for Sync Service)

**Authorization:** Service-to-Service

**Request Headers:**
```
X-Service-Name: sync-service
X-Service-Key: <INTERNAL_SERVICE_KEY>
```

**Response 200 OK:**
```json
{
  "data": {
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": 1,  // BIGINT (Long)
    "jiraHostUrl": "https://domain.atlassian.net",
    "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefgh",
    "githubRepoUrl": "https://github.com/owner/repo",
    "githubToken": "ghp_1234567890abcdefghijklmnopqr",
    "state": "VERIFIED"
  }
}
```

**Security:**
- Validate `X-Service-Name` header (must be whitelisted)
- Validate `X-Service-Key` header matches service-specific key
- Return decrypted tokens (NO masking)
- Only if state = `VERIFIED` (403 otherwise)

**Service Whitelist (v1.0):**
- `sync-service` - Primary consumer for data synchronization
- Future services require configuration update

**Rate Limiting:**
- 100 requests per minute per service
- 429 Too Many Requests if exceeded

**Configuration:**
```bash
# Environment variables
INTERNAL_SERVICE_KEY_SYNC=<unique-key-for-sync-service>
# Add new services as needed:
# INTERNAL_SERVICE_KEY_REPORT=<unique-key-for-report-service>
```

**Error Responses:**

```json
// 401 - Invalid auth
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid service authentication"
  }
}

// 403 - Not verified
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Configuration not verified (state: DRAFT)"
  }
}
```

---

## Error Handling

### External API Failures

**Scenario:** Jira/GitHub timeout during verification

**Response:** HTTP 200 with `state: INVALID` in data

### Dependency Failures

**Scenario:** User-Group Service unavailable

**Response:**
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "User-Group Service unavailable"
  }
}
```

**HTTP Status:** 503  
**Retry:** Exponential backoff recommended
---

## Observability

### Logging

**Framework:** SLF4J + Logback

**Log Levels:**
- `ERROR` - External API failures, gRPC errors, state transition violations
- `WARN` - Bulkhead rejections, retry attempts, circuit breaker events
- `INFO` - Configuration operations, verification attempts, state changes
- `DEBUG` - Async task execution, MDC propagation, encryption/decryption

**Structured Logging with Correlation ID:**
```json
{
  "timestamp": "2026-02-18T10:30:00Z",
  "level": "INFO",
  "logger": "ProjectConfigService",
  "correlationId": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "message": "Configuration verified successfully",
  "configId": "550e8400-e29b-41d4-a716-446655440000",
  "groupId": 1,
  "state": "VERIFIED",
  "action": "CONFIG_VERIFIED"
}
```

### Correlation ID Support

**Status:** ✅ **FULLY IMPLEMENTED**

**Implementation:**
- `CorrelationIdFilter` extracts `X-Request-ID` header (or generates UUID)
- Stored in MDC (Mapped Diagnostic Context)
- `MdcTaskDecorator` propagates to async threads (CompletableFuture)
- Returned in response header: `X-Request-ID`

**Usage:**
```bash
curl -H "X-Request-ID: my-trace-id" https://api/project-configs
```

**Logback Pattern:**
```xml
<pattern>%d{yyyy-MM-dd HH:mm:ss} [%X{correlationId}] %-5level %logger{36} - %msg%n</pattern>
```

### Resilience Patterns

**Circuit Breaker:** ✅ Implemented  
- **Targets:** JiraVerificationService, GitHubVerificationService, gRPC clients
- **Failure threshold:** 50%
- **Open state duration:** 10 seconds
- **Fallback methods:** Log error, return INVALID state

**Retry Policy:** ✅ Implemented  
- **Name:** `verificationRetry`
- **Max attempts:** 3
- **Backoff:** Exponential (500ms → 1s → 2s)
- **Retryable exceptions:** IOException, ResourceAccessException
- **Logging:** `[Retry] Attempt {}/{} failed: {}`

**Bulkhead:** ✅ Implemented  
- **Type:** SEMAPHORE
- **Concurrent calls limit:** 100
- **Rejection behavior:** BulkheadFullException → HTTP 503
- **Logging:** `[Bulkhead: jiraVerification] Starting...` / `Rejected (semaphore full)`

**Timeout:** ✅ Configured  
- **gRPC:** 2 seconds deadline
- **Jira API:** 6 seconds
- **GitHub API:** 6 seconds

### Async Architecture

**Pattern:** CompletableFuture with dedicated thread pools

**Thread Pools:**
- `verificationExecutor`: For Jira/GitHub API calls
- `@Async` with custom executor
- MDC propagation via `MdcTaskDecorator`

**Benefits:**
- HTTP threads freed during I/O operations
- Non-blocking verification flow
- Better throughput under load

**Memory Management:**
- MDC cleanup in `finally` blocks prevents memory leaks
- Thread pool shutdown on application stop

### Monitoring

**Health Endpoint:** `/actuator/health`

**Metrics (Future):**
- Verification success/failure rate
- Circuit breaker state transitions
- Bulkhead queue depth
- Retry attempt distribution
- gRPC call latency (p50, p95, p99)
- External API response times (Jira/GitHub)
- State transition counts

**Custom Metrics (Recommended):**
```java
@Timed(value = "projectconfig.verification", description = "Verification duration")
@Counted(value = "projectconfig.verification.attempts", description = "Verification attempts")
public CompletableFuture<Void> verifyConnection(UUID id) {
    // Implementation
}
```

### Distributed Tracing

**Status:** ⚠️ Correlation ID only (no full tracing)

**Future Enhancement:** Integrate Zipkin or Jaeger for end-to-end request tracing across services

---

## Implementation Status

✅ **COMPLETE** - Production-ready

**Features:**
- Async/non-blocking architecture with CompletableFuture
- Full Resilience4j stack (circuit breaker, retry, bulkhead)
- Correlation ID propagation across async boundaries
- AES-256 token encryption
- State machine with optimistic locking
- Internal API authentication for Sync Service

**Recommendations:**
1. Add distributed tracing (Zipkin/Jaeger)
2. Implement custom Prometheus metrics
3. Add rate limiting for public endpoints (optional)
4. Consider API versioning for breaking changes