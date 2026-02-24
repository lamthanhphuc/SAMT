# API Contract – Sync Service

**Version:** 1.0  
**Base URL:** `/actuator`  
**Date:** February 23, 2026

---

## Overview

Sync Service is a **background service** with no user-facing REST endpoints. It exposes health check and metrics endpoints for monitoring purposes only.

---

## Standard Response Formats

### Success Response

```json
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "grpc": { "status": "UP" }
  }
}
```

### Error Response

Sync Service does not expose user-facing REST endpoints. All errors are logged to application logs and tracked in the `sync_jobs` table.

**Sync Job Error Format:**
```sql
-- sync_jobs table
| status  | error_message                      |
|---------|-----------------------------------|
| FAILED  | Connection timeout to jira.com... |
```

---

## Actuator Endpoints

### 1. GET `/actuator/health`

**Description:** Service health status

**Authorization:** None (public)

**Request Headers:**
```
Accept: application/json
```

**Response 200 OK:**
```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "grpc": {
      "status": "UP",
      "details": {
        "projectConfigService": "CONNECTED"
      }
    }
  }
}
```

**Response 503 Service Unavailable:**
```json
{
  "status": "DOWN",
  "components": {
    "db": { "status": "DOWN" },
    "grpc": { "status": "DOWN" }
  }
}
```

---

### 2. GET `/actuator/metrics`

**Description:** Service metrics for monitoring

**Authorization:** None (internal network only)

**Available Metrics:**
- `sync_jobs_total` - Total sync jobs executed
- `sync_jobs_success` - Successful sync jobs
- `sync_jobs_failed` - Failed sync jobs
- `sync_duration_seconds` - Sync job duration histogram
- `external_api_calls_total` - External API call count
- `external_api_errors_total` - External API error count

**Response 200 OK:**
```json
{
  "names": [
    "sync_jobs_total",
    "sync_jobs_success",
    "sync_jobs_failed"
  ]
}
```

---

### 3. GET `/actuator/prometheus`

**Description:** Prometheus-format metrics

**Authorization:** None (internal network only)

**Response 200 OK:**
```
# HELP sync_jobs_total Total sync jobs executed
# TYPE sync_jobs_total counter
sync_jobs_total{type="JIRA_ISSUES"} 150
sync_jobs_total{type="GITHUB_COMMITS"} 200

# HELP sync_duration_seconds Sync job duration
# TYPE sync_duration_seconds histogram
sync_duration_seconds_bucket{type="JIRA_ISSUES",le="5.0"} 120
sync_duration_seconds_bucket{type="JIRA_ISSUES",le="+Inf"} 150
```

---

## gRPC Integration

### gRPC Client (NOT Server)

Sync Service is a **gRPC client only**. It calls ProjectConfig Service to retrieve decrypted credentials.

**Calls ProjectConfig Service:**

#### InternalGetDecryptedConfig

**Request:**
```protobuf
message InternalGetDecryptedConfigRequest {
  int64 config_id = 1;
}
```

**Response:**
```protobuf
message InternalGetDecryptedConfigResponse {
  int64 config_id = 1;
  string jira_host_url = 2;
  string jira_api_token = 3;
  string jira_project_key = 4;
  string github_repo_url = 5;
  string github_access_token = 6;
}
```

**Authentication:** Service-to-service headers
- `x-service-name: sync-service`
- `x-service-key: ${SERVICE_AUTH_KEY}`

**Timeout:** 5 seconds

**Retry:** 3 attempts with exponential backoff

---

#### ListVerifiedConfigs

**Request:**
```protobuf
message ListVerifiedConfigsRequest {
  int32 page = 1;
  int32 size = 2;
}
```

**Response:**
```protobuf
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
  string state = 6;
}
```

---

## Error Handling

### External API Errors

| Error Code | Action | Retry | Logged |
|------------|--------|-------|--------|
| 401 Unauthorized | Mark token invalid, skip config | No | Yes |
| 403 Forbidden | Mark token invalid, skip config | No | Yes |
| 404 Not Found | Skip resource, continue sync | No | Yes |
| 429 Rate Limit | Wait and retry with exponential backoff | Yes (3x) | Yes |
| 500 Server Error | Retry with exponential backoff | Yes (3x) | Yes |
| Connection Timeout | Retry with exponential backoff | Yes (3x) | Yes |

### gRPC Error Mapping

| gRPC Status | Sync Service Action | Logged |
|-------------|---------------------|--------|
| OK | Continue sync | Info |
| NOT_FOUND | Skip config, log warning | Warning |
| PERMISSION_DENIED | Skip config, log error | Error |
| UNAVAILABLE | Retry (3x), then fail | Error |
| DEADLINE_EXCEEDED | Retry (3x), then fail | Error |

---

## Observability

### Logging

**Log Pattern:**
```
%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} [%X{correlationId}] [configId=%X{configId}] [jobType=%X{jobType}] - %msg%n
```

**MDC Fields:**
- `correlationId` - Unique ID per sync job execution
- `configId` - Project config ID being synced
- `jobType` - JIRA_ISSUES | JIRA_SPRINTS | GITHUB_COMMITS | GITHUB_PRS

**Log Levels:**
- `INFO` - Sync start/completion, success count
- `WARN` - Retries, skipped configs, partial failures
- `ERROR` - Sync failures, external API errors, gRPC errors

### Metrics

**Custom Metrics:**
- `sync_jobs_scheduled` - Total scheduled sync jobs (gauge)
- `sync_jobs_running` - Currently running sync jobs (gauge)
- `sync_jobs_completed` - Completed sync jobs (counter)
- `sync_jobs_failed` - Failed sync jobs (counter)
- `sync_duration_seconds` - Sync duration histogram
- `sync_records_fetched` - Records fetched from external APIs (counter)
- `sync_records_saved` - Records saved to database (counter)
- `external_api_call_duration_seconds` - External API latency histogram

---

## Resilience Patterns

### Retry

**Configuration:**
```yaml
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
```

**Applied To:**
- Jira API calls
- GitHub API calls
- gRPC calls to ProjectConfig Service

### Circuit Breaker

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      projectConfigGrpc:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        minimumNumberOfCalls: 5
```

**Applied To:**
- gRPC calls to ProjectConfig Service

**Fallback Behavior:**
- Log circuit breaker open event
- Skip current sync cycle
- Retry on next scheduled execution

### Timeout

**External API Timeouts:**
- Jira API: 30 seconds
- GitHub API: 30 seconds

**gRPC Timeouts:**
- ProjectConfig Service: 5 seconds

**Scheduler Timeout:**
- Max sync duration: 30 minutes per cycle
- If exceeded, cancel async tasks and log timeout

---

## Security

### Service-to-Service Authentication

**With ProjectConfig Service:**
- Method: Custom gRPC interceptor
- Headers: `x-service-name`, `x-service-key`
- Key storage: Environment variable `SERVICE_AUTH_KEY`
- Validation: ProjectConfig Service validates service name + key pair

### External API Security

**Token Management:**
- Tokens retrieved from ProjectConfig Service (decrypted at runtime)
- Tokens are NOT cached
- Tokens are NOT logged
- 401/403 errors mark config as INVALID

### Authorization

**No User-Facing Endpoints:** Sync Service operates autonomously. No JWT validation required.

---

## Data Ownership & Retention

### Database Ownership

Sync Service owns dedicated `sync_db` database.

**Tables Owned:**
- `sync_jobs` - Sync execution tracking
- `unified_activities` - Normalized activity data
- `jira_issues` - Raw Jira issue data
- `jira_sprints` - Raw Jira sprint data
- `github_commits` - Raw GitHub commit data
- `github_pull_requests` - Raw GitHub PR data

### Soft Delete & Retention Policy

**Soft Delete:** 90-day retention

```sql
-- All sync tables have soft delete columns
deleted_at TIMESTAMP NULL
deleted_by BIGINT NULL

-- Query excludes soft-deleted records
SELECT * FROM sync_jobs WHERE deleted_at IS NULL;
```

**Cleanup Job:** Runs daily at 2:00 AM

---

## Known Limitations

- No user-facing REST API (background service only)
- No real-time sync (scheduled batch processing only)
- Historical data preserved on config deletion (by design)
- Token expiration requires manual intervention
