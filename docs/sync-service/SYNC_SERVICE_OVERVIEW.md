# Sync Service - Technical Overview

**Service Name:** Sync Service  
**Version:** 1.0  
**Date:** February 5, 2026  
**Status:** Technical Specification

---

## 1. Service Purpose

Sync Service đảm nhận nhiệm vụ **tự động đồng bộ dữ liệu** từ Jira API và GitHub API vào database của hệ thống SAMT. Dữ liệu sau khi normalize sẽ phục vụ cho:
- **Reporting Service:** Tạo báo cáo tiến độ dự án
- **AI Analysis Service:** Phân tích chất lượng code và đóng góp cá nhân
- **Admin Dashboard:** Thống kê tổng quan các nhóm

---

## 2. System Context

### 2.1 Position in Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                         API Gateway                              │
└────────────┬──────────────────────────────────────┬─────────────┘
             │                                      │
             ▼                                      ▼
  ┌──────────────────────┐              ┌──────────────────────┐
  │ Project Config       │              │ Sync Service         │
  │ Service              │◄─────────────┤                      │
  │                      │   gRPC       │ - Jira Adapter       │
  │ - Config Management  │   (Get       │ - GitHub Adapter     │
  │ - Token Encryption   │   Decrypted  │ - Scheduler          │
  │ - Verification       │   Tokens)    │ - Data Normalizer    │
  └──────────┬───────────┘              └──────────┬───────────┘
             │                                      │
             ▼                                      ▼
  ┌──────────────────────┐              ┌──────────────────────┐
  │ PostgreSQL           │              │ PostgreSQL           │
  │ - project_configs    │              │ - sync_jobs          │
  └──────────────────────┘              │ - jira_issues        │
                                        │ - jira_sprints       │
                                        │ - github_commits     │
                                        │ - github_pull_reqs   │
                                        │ - unified_activities │
                                        └──────────────────────┘
```

### 2.2 Service Ports

| Type | Port | Purpose |
|------|------|---------|
| HTTP | 8084 | Actuator, Health Check, Admin API |
| gRPC Client | - | Connects to Project Config Service (9092) |

**Note:** Sync Service KHÔNG expose gRPC server. Nó chỉ hoạt động như scheduled job và gRPC client.

---

## 3. Core Responsibilities

### 3.1 Data Synchronization

**Scheduled Tasks:**
- **Jira Issues Sync:** Every 30 minutes (configurable)
- **Jira Sprints Sync:** Every 2 hours (configurable)
- **GitHub Commits Sync:** Every 15 minutes (configurable)
- **GitHub Pull Requests Sync:** Every 1 hour (configurable)

**Synchronization Flow:**
1. Query all active project configs (state = VERIFIED) from database
2. For each config, call `InternalGetDecryptedConfig` gRPC method từ Project Config Service
3. Use decrypted tokens to call external APIs (Jira REST API, GitHub REST API)
4. Parse raw JSON responses
5. Apply **Adapter Pattern** to normalize data into `UnifiedActivity` model
6. Save normalized data to Sync Service database
7. Log sync status (success, failure, error message)

### 3.2 Data Normalization

**Challenge:** Jira và GitHub có cấu trúc dữ liệu hoàn toàn khác nhau.

**Solution:** Áp dụng **Strategy Pattern + Adapter Pattern** để convert raw data → common data model (`UnifiedActivity`).

**Example:**
- Jira Issue → UnifiedActivity (type=TASK, source=JIRA)
- GitHub Commit → UnifiedActivity (type=COMMIT, source=GITHUB)
- GitHub PR → UnifiedActivity (type=PULL_REQUEST, source=GITHUB)

Chi tiết design pattern xem [SYNC_LOGIC_DESIGN.md](SYNC_LOGIC_DESIGN.md).

### 3.3 Error Handling

**Connection Failures:**
- Retry 3 times với exponential backoff (1s, 2s, 4s)
- Log error to `sync_jobs` table với status=FAILED
- Send notification to admin (future enhancement)

**Token Expiration:**
- Detect 401 Unauthorized từ external APIs
- Update project_config state → INVALID
- Notify group leader qua email (future enhancement)

**Rate Limiting:**
- Respect API rate limits:
  - Jira Cloud: 10 requests/second per IP
  - GitHub API: 5000 requests/hour per authenticated user
- Implement circuit breaker pattern (Resilience4j)

---

## 4. Database Design

### 4.1 Schema Overview

#### 4.1.1 sync_jobs

**Purpose:** Tracking lịch sử đồng bộ.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `project_config_id` | BIGINT | NOT NULL | FK to project_configs.id |
| `job_type` | VARCHAR(50) | NOT NULL | JIRA_ISSUES, JIRA_SPRINTS, GITHUB_COMMITS, GITHUB_PRS |
| `status` | VARCHAR(20) | NOT NULL | PENDING, RUNNING, SUCCESS, FAILED |
| `started_at` | TIMESTAMP | NULL | Job start time |
| `completed_at` | TIMESTAMP | NULL | Job completion time |
| `records_fetched` | INT | NULL | Number of records retrieved |
| `records_saved` | INT | NULL | Number of records saved to DB |
| `error_message` | TEXT | NULL | Error details if status=FAILED |
| `created_at` | TIMESTAMP | NOT NULL | Record creation time |
| `updated_at` | TIMESTAMP | NOT NULL | Last update time |
| `deleted_at` | TIMESTAMP | NULL | Soft delete timestamp (90-day retention) |
| `deleted_by` | BIGINT | NULL | User ID who deleted (Long/BIGINT) |

**Indexes:**
```sql
CREATE INDEX idx_sync_jobs_config_status ON sync_jobs(project_config_id, status);
CREATE INDEX idx_sync_jobs_created_at ON sync_jobs(created_at);
CREATE INDEX idx_sync_jobs_deleted_at ON sync_jobs(deleted_at) WHERE deleted_at IS NOT NULL;
```

---

#### 4.1.2 jira_issues

**Purpose:** Lưu trữ Jira issues (tasks, stories, bugs).

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `project_config_id` | BIGINT | NOT NULL | FK to project_configs.id |
| `jira_issue_key` | VARCHAR(50) | NOT NULL | UNIQUE: SWP391-123 |
| `jira_issue_id` | VARCHAR(50) | NOT NULL | Jira internal ID |
| `summary` | TEXT | NOT NULL | Issue title |
| `description` | TEXT | NULL | Issue description |
| `issue_type` | VARCHAR(50) | NOT NULL | Task, Story, Bug, Epic |
| `status` | VARCHAR(50) | NOT NULL | To Do, In Progress, Done |
| `priority` | VARCHAR(20) | NULL | Highest, High, Medium, Low, Lowest |
| `assignee_email` | VARCHAR(255) | NULL | Assigned user email (used to map to system user) |
| `assignee_display_name` | VARCHAR(255) | NULL | Display name from Jira |
| `reporter_email` | VARCHAR(255) | NULL | Reporter email |
| `sprint_id` | BIGINT | NULL | FK to jira_sprints.id |
| `story_points` | DECIMAL(5,2) | NULL | Story points estimate |
| `created_date` | TIMESTAMP | NOT NULL | Issue creation date in Jira |
| `updated_date` | TIMESTAMP | NOT NULL | Last update in Jira |
| `resolved_date` | TIMESTAMP | NULL | Resolution date |
| `synced_at` | TIMESTAMP | NOT NULL | Last sync timestamp |
| `deleted_at` | TIMESTAMP | NULL | Soft delete (90-day retention) |
| `deleted_by` | BIGINT | NULL | User ID who deleted (Long/BIGINT) |

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_jira_issues_key ON jira_issues(jira_issue_key) WHERE deleted_at IS NULL;
CREATE INDEX idx_jira_issues_config ON jira_issues(project_config_id);
CREATE INDEX idx_jira_issues_assignee ON jira_issues(assignee_email);
CREATE INDEX idx_jira_issues_sprint ON jira_issues(sprint_id);
CREATE INDEX idx_jira_issues_status ON jira_issues(status);
```

---

#### 4.1.3 jira_sprints

**Purpose:** Lưu trữ Jira sprints.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `project_config_id` | BIGINT | NOT NULL | FK to project_configs.id |
| `jira_sprint_id` | VARCHAR(50) | NOT NULL | Jira sprint ID |
| `sprint_name` | VARCHAR(255) | NOT NULL | Sprint 1, Sprint 2, etc. |
| `state` | VARCHAR(20) | NOT NULL | future, active, closed |
| `start_date` | TIMESTAMP | NULL | Sprint start date |
| `end_date` | TIMESTAMP | NULL | Sprint end date |
| `complete_date` | TIMESTAMP | NULL | Sprint completion date |
| `goal` | TEXT | NULL | Sprint goal/description |
| `synced_at` | TIMESTAMP | NOT NULL | Last sync timestamp |
| `deleted_at` | TIMESTAMP | NULL | Soft delete (90-day retention) |
| `deleted_by` | BIGINT | NULL | User ID who deleted (Long/BIGINT) |

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_jira_sprints_jira_id ON jira_sprints(jira_sprint_id, project_config_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_jira_sprints_config ON jira_sprints(project_config_id);
CREATE INDEX idx_jira_sprints_state ON jira_sprints(state);
```

---

#### 4.1.4 github_commits

**Purpose:** Lưu trữ GitHub commits.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `project_config_id` | BIGINT | NOT NULL | FK to project_configs.id |
| `commit_sha` | VARCHAR(40) | NOT NULL | Git commit SHA (40 chars hex) |
| `commit_message` | TEXT | NOT NULL | Commit message |
| `author_email` | VARCHAR(255) | NOT NULL | Committer email (used to map to system user) |
| `author_name` | VARCHAR(255) | NOT NULL | Committer name |
| `commit_date` | TIMESTAMP | NOT NULL | Commit timestamp |
| `additions` | INT | NOT NULL | Lines added |
| `deletions` | INT | NOT NULL | Lines deleted |
| `files_changed` | INT | NOT NULL | Number of files changed |
| `branch` | VARCHAR(255) | NULL | Target branch (main, develop, etc.) |
| `pull_request_id` | BIGINT | NULL | FK to github_pull_requests.id if commit belongs to PR |
| `synced_at` | TIMESTAMP | NOT NULL | Last sync timestamp |
| `deleted_at` | TIMESTAMP | NULL | Soft delete (90-day retention) |
| `deleted_by` | BIGINT | NULL | User ID who deleted (Long/BIGINT) |

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_github_commits_sha ON github_commits(commit_sha, project_config_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_github_commits_config ON github_commits(project_config_id);
CREATE INDEX idx_github_commits_author ON github_commits(author_email);
CREATE INDEX idx_github_commits_date ON github_commits(commit_date);
CREATE INDEX idx_github_commits_pr ON github_commits(pull_request_id);
```

---

#### 4.1.5 github_pull_requests

**Purpose:** Lưu trữ GitHub pull requests.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `project_config_id` | BIGINT | NOT NULL | FK to project_configs.id |
| `github_pr_number` | INT | NOT NULL | PR number (e.g., 123) |
| `github_pr_id` | VARCHAR(50) | NOT NULL | GitHub internal PR ID |
| `title` | TEXT | NOT NULL | PR title |
| `description` | TEXT | NULL | PR body/description |
| `state` | VARCHAR(20) | NOT NULL | open, closed, merged |
| `author_email` | VARCHAR(255) | NULL | PR author email |
| `author_username` | VARCHAR(255) | NOT NULL | GitHub username |
| `created_date` | TIMESTAMP | NOT NULL | PR creation date |
| `updated_date` | TIMESTAMP | NOT NULL | Last update date |
| `merged_date` | TIMESTAMP | NULL | Merge date (if merged) |
| `closed_date` | TIMESTAMP | NULL | Close date (if closed without merge) |
| `source_branch` | VARCHAR(255) | NOT NULL | Source branch (feature/xxx) |
| `target_branch` | VARCHAR(255) | NOT NULL | Target branch (main, develop) |
| `additions` | INT | NOT NULL | Total lines added |
| `deletions` | INT | NOT NULL | Total lines deleted |
| `files_changed` | INT | NOT NULL | Total files changed |
| `commits_count` | INT | NOT NULL | Number of commits in PR |
| `synced_at` | TIMESTAMP | NOT NULL | Last sync timestamp |
| `deleted_at` | TIMESTAMP | NULL | Soft delete (90-day retention) |
| `deleted_by` | BIGINT | NULL | User ID who deleted (Long/BIGINT) |

**Indexes:**
```sql
CREATE UNIQUE INDEX idx_github_prs_number ON github_pull_requests(github_pr_number, project_config_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_github_prs_config ON github_pull_requests(project_config_id);
CREATE INDEX idx_github_prs_author ON github_pull_requests(author_email);
CREATE INDEX idx_github_prs_state ON github_pull_requests(state);
```

---

#### 4.1.6 unified_activities

**Purpose:** Common Data Model - chuẩn hóa tất cả activities từ Jira và GitHub.

**Design Goal:** Reporting Service và AI Service chỉ cần query table này thay vì join nhiều tables.

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | BIGSERIAL | NOT NULL | Primary key |
| `project_config_id` | BIGINT | NOT NULL | FK to project_configs.id |
| `activity_type` | VARCHAR(50) | NOT NULL | JIRA_ISSUE, JIRA_SPRINT, GITHUB_COMMIT, GITHUB_PR |
| `source_id` | BIGINT | NOT NULL | Foreign key to specific table (jira_issues.id, github_commits.id, etc.) |
| `source_key` | VARCHAR(100) | NOT NULL | Human-readable key (SWP391-123, commit SHA first 7 chars, PR #123) |
| `title` | TEXT | NOT NULL | Activity title/summary |
| `description` | TEXT | NULL | Activity description |
| `status` | VARCHAR(50) | NULL | Status (To Do, Done, open, closed, merged, etc.) |
| `assignee_email` | VARCHAR(255) | NULL | Assigned/Responsible user email |
| `assignee_name` | VARCHAR(255) | NULL | Display name |
| `activity_date` | TIMESTAMP | NOT NULL | Activity timestamp (created_date, commit_date, etc.) |
| `completed_date` | TIMESTAMP | NULL | Completion timestamp (resolved_date, merged_date, etc.) |
| `effort_points` | DECIMAL(10,2) | NULL | Story points (Jira) or Lines changed (GitHub) |
| `metadata_json` | JSONB | NULL | Source-specific additional data |
| `created_at` | TIMESTAMP | NOT NULL | Record creation |
| `updated_at` | TIMESTAMP | NOT NULL | Last update |
| `deleted_at` | TIMESTAMP | NULL | Soft delete (90-day retention) |
| `deleted_by` | BIGINT | NULL | User ID who deleted (Long/BIGINT) |

**Indexes:**
```sql
CREATE INDEX idx_unified_activities_config ON unified_activities(project_config_id);
CREATE INDEX idx_unified_activities_type ON unified_activities(activity_type);
CREATE INDEX idx_unified_activities_assignee ON unified_activities(assignee_email);
CREATE INDEX idx_unified_activities_date ON unified_activities(activity_date);
CREATE INDEX idx_unified_activities_status ON unified_activities(status);
CREATE INDEX idx_unified_activities_source ON unified_activities(source_id, activity_type);
```

**Example JSONB metadata:**

**Jira Issue:**
```json
{
  "issueType": "Task",
  "priority": "High",
  "reporter": "john@example.com",
  "sprintName": "Sprint 3",
  "labels": ["backend", "api"]
}
```

**GitHub Commit:**
```json
{
  "branch": "feature/login",
  "additions": 150,
  "deletions": 20,
  "filesChanged": 5,
  "sha": "abc123def456..."
}
```

**GitHub PR:**
```json
{
  "number": 42,
  "sourceBranch": "feature/auth",
  "targetBranch": "main",
  "commitsCount": 7,
  "reviewers": ["alice@example.com", "bob@example.com"]
}
```

---

### 4.2 Foreign Key Relationships

```
project_configs (in project-config-service)
    ↑
    └── sync_jobs (N:1)
    └── jira_issues (N:1)
    └── jira_sprints (N:1)
    └── github_commits (N:1)
    └── github_pull_requests (N:1)
    └── unified_activities (N:1)

jira_sprints
    ↑
    └── jira_issues.sprint_id (N:1)

github_pull_requests
    ↑
    └── github_commits.pull_request_id (N:1)

[jira_issues, github_commits, github_pull_requests]
    ↑
    └── unified_activities.source_id (based on activity_type)
```

**Note:** `project_config_id` là FK đến database khác (project-config-service). Không enforce FK constraint ở database level (microservices best practice), chỉ enforce trong application code.

---

## 5. Technology Stack

### 5.1 Core Framework

| Component | Technology | Version |
|-----------|------------|---------|
| Framework | Spring Boot | 3.2.x |
| Language | Java | 17 |
| Build Tool | Maven | 3.9.x |
| Database | PostgreSQL | 15.x |
| ORM | Hibernate (JPA) | 6.x |

### 5.2 Integration Libraries

| Purpose | Library | Version |
|---------|---------|---------|
| gRPC Client | io.grpc:grpc-netty-shaded | 1.60.x |
| gRPC Protobuf | io.grpc:grpc-protobuf | 1.60.x |
| gRPC Stub | io.grpc:grpc-stub | 1.60.x |
| HTTP Client | Spring WebClient | (included in Spring Boot) |
| JSON Parsing | Jackson | (included in Spring Boot) |
| Scheduler | Spring @Scheduled | (included in Spring Boot) |

### 5.3 Resilience & Monitoring

| Purpose | Library | Version |
|---------|---------|---------|
| Circuit Breaker | Resilience4j | 2.1.x |
| Retry Logic | Resilience4j Retry | 2.1.x |
| Rate Limiter | Resilience4j RateLimiter | 2.1.x |
| Actuator | Spring Boot Actuator | (included) |
| Metrics | Micrometer | (included) |

### 5.4 Security

| Purpose | Mechanism |
|---------|-----------|
| Service-to-Service Auth | gRPC Metadata (x-service-name, x-service-key) |
| External API Auth | Bearer Token (from Project Config Service) |
| Database Encryption | PostgreSQL TDE (Transparent Data Encryption) - infrastructure level |

**Note:** Sync Service KHÔNG cần JWT authentication vì không có REST endpoints cho client. Chỉ có scheduled jobs và gRPC client.

---

## 6. API Design

### 6.1 Internal REST Endpoints (Admin Only)

**Base URL:** `http://localhost:8084/api/internal/sync`

#### 6.1.1 Trigger Manual Sync

```http
POST /api/internal/sync/trigger
Authorization: Bearer <admin-jwt-token>
Content-Type: application/json

{
  "projectConfigId": 123,
  "jobTypes": ["JIRA_ISSUES", "GITHUB_COMMITS"]
}
```

**Response:**
```json
{
  "message": "Sync jobs triggered",
  "jobIds": [456, 457]
}
```

#### 6.1.2 Get Sync Job Status

```http
GET /api/internal/sync/jobs/{jobId}
Authorization: Bearer <admin-jwt-token>
```

**Response:**
```json
{
  "id": 456,
  "projectConfigId": 123,
  "jobType": "JIRA_ISSUES",
  "status": "SUCCESS",
  "startedAt": "2026-02-05T10:30:00Z",
  "completedAt": "2026-02-05T10:32:15Z",
  "recordsFetched": 150,
  "recordsSaved": 148,
  "errorMessage": null
}
```

#### 6.1.3 Get Sync History

```http
GET /api/internal/sync/history?projectConfigId=123&page=0&size=20
Authorization: Bearer <admin-jwt-token>
```

**Response:**
```json
{
  "content": [
    {
      "id": 456,
      "jobType": "JIRA_ISSUES",
      "status": "SUCCESS",
      "startedAt": "2026-02-05T10:30:00Z",
      "completedAt": "2026-02-05T10:32:15Z",
      "recordsFetched": 150,
      "recordsSaved": 148
    }
  ],
  "totalPages": 5,
  "totalElements": 87
}
```

---

## 7. Configuration

### 7.1 application.yml

```yaml
spring:
  application:
    name: sync-service
  datasource:
    url: jdbc:postgresql://localhost:5432/sync_db
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  jpa:
    hibernate:
      ddl-auto: validate  # Use Flyway for migrations
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true

server:
  port: 8084

# gRPC Client Configuration
grpc:
  client:
    project-config-service:
      address: static://localhost:9092
      negotiationType: PLAINTEXT

# Service Authentication
service:
  auth:
    service-name: sync-service
    service-key: ${SERVICE_AUTH_KEY}  # From environment variable

# Scheduler Configuration
sync:
  scheduler:
    enabled: true
    jira-issues-cron: "0 */30 * * * *"        # Every 30 minutes
    jira-sprints-cron: "0 0 */2 * * *"        # Every 2 hours
    github-commits-cron: "0 */15 * * * *"     # Every 15 minutes
    github-prs-cron: "0 0 * * * *"            # Every 1 hour

# Resilience4j Configuration
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
  circuitbreaker:
    instances:
      externalApi:
        slidingWindowSize: 10
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 5
  ratelimiter:
    instances:
      jiraApi:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
        timeoutDuration: 5s
      githubApi:
        limitForPeriod: 100
        limitRefreshPeriod: 1m
        timeoutDuration: 5s

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

## 8. Deployment

### 8.1 Docker Configuration

**Dockerfile:**
```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/sync-service.jar app.jar
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**docker-compose.yml:**
```yaml
services:
  sync-service:
    build: ./sync-service
    ports:
      - "8084:8084"
    environment:
      DB_USERNAME: sync_user
      DB_PASSWORD: ${SYNC_DB_PASSWORD}
      SERVICE_AUTH_KEY: ${SERVICE_AUTH_KEY}
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/sync_db
      GRPC_CLIENT_PROJECT_CONFIG_SERVICE_ADDRESS: static://project-config-service:9092
    depends_on:
      - postgres
      - project-config-service
    networks:
      - samt-network

  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: sync_db
      POSTGRES_USER: sync_user
      POSTGRES_PASSWORD: ${SYNC_DB_PASSWORD}
    volumes:
      - sync-db-data:/var/lib/postgresql/data
    networks:
      - samt-network

volumes:
  sync-db-data:

networks:
  samt-network:
    external: true
```

---

## 9. Security Considerations

### 9.1 Service-to-Service Authentication

**Requirement:** Khi gọi `InternalGetDecryptedConfig` từ Project Config Service, Sync Service phải gửi service authentication credentials.

**Implementation:**
```java
@Component
public class GrpcClientInterceptor implements ClientInterceptor {
    
    @Value("${service.auth.service-name}")
    private String serviceName;
    
    @Value("${service.auth.service-key}")
    private String serviceKey;
    
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
            
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                headers.put(Metadata.Key.of("x-service-name", Metadata.ASCII_STRING_MARSHALLER), serviceName);
                headers.put(Metadata.Key.of("x-service-key", Metadata.ASCII_STRING_MARSHALLER), serviceKey);
                super.start(responseListener, headers);
            }
        };
    }
}
```

### 9.2 Token Storage

**CRITICAL:** Sync Service lưu trữ decrypted API tokens trong memory ONLY. KHÔNG BAO GIỜ lưu vào database.

**Flow:**
1. Scheduler triggers sync job
2. Call gRPC `InternalGetDecryptedConfig(projectConfigId)`
3. Receive decrypted tokens in response
4. Use tokens immediately to call external APIs
5. Discard tokens sau khi sync job complete
6. Tokens never leave memory, never logged

### 9.3 Rate Limiting

**Jira API:**
- Cloud: 10 requests/second per IP
- Self-managed: No limits (typically)

**GitHub API:**
- Authenticated: 5000 requests/hour
- Unauthenticated: 60 requests/hour

**Implementation:** Sử dụng Resilience4j RateLimiter (configured in application.yml).

---

## 10. Monitoring & Observability

### 10.1 Metrics

**Custom Metrics to Track:**
- `sync_jobs_total{status, job_type}` - Total sync jobs executed
- `sync_records_fetched{job_type}` - Total records fetched from external APIs
- `sync_records_saved{job_type}` - Total records saved to database
- `sync_duration_seconds{job_type}` - Sync job execution time
- `sync_errors_total{job_type, error_type}` - Total sync errors
- `external_api_calls_total{api, status}` - External API call count
- `external_api_duration_seconds{api}` - External API response time

### 10.2 Health Checks

**Actuator Endpoints:**
- `/actuator/health` - Overall service health
- `/actuator/health/db` - Database connectivity
- `/actuator/health/grpc` - gRPC client connectivity to Project Config Service

**Custom Health Indicators:**
```java
@Component
public class ExternalApiHealthIndicator implements HealthIndicator {
    @Override
    public Health health() {
        // Check last sync job status
        // Return DOWN if consecutive failures > 5
    }
}
```

### 10.3 Logging

**Log Levels:**
- `INFO` - Sync job started/completed, records count
- `WARN` - Retry attempts, rate limiting, token expiration detected
- `ERROR` - Sync job failed, connection errors, parsing errors

**Structured Logging (JSON format):**
```json
{
  "timestamp": "2026-02-05T10:30:15.123Z",
  "level": "INFO",
  "service": "sync-service",
  "trace_id": "abc123",
  "message": "Sync job completed",
  "context": {
    "jobId": 456,
    "projectConfigId": 123,
    "jobType": "JIRA_ISSUES",
    "recordsFetched": 150,
    "recordsSaved": 148,
    "duration": 135000
  }
}
```

---

## 11. Data Retention & Cleanup

### 11.1 Soft Delete Policy

**Applies to all tables:** sync_jobs, jira_issues, jira_sprints, github_commits, github_pull_requests, unified_activities.

**Retention:** 90 days after soft delete.

**Hard Delete Job:**
```sql
-- Scheduled job runs daily at 2 AM
DELETE FROM sync_jobs WHERE deleted_at < NOW() - INTERVAL '90 days';
DELETE FROM jira_issues WHERE deleted_at < NOW() - INTERVAL '90 days';
DELETE FROM jira_sprints WHERE deleted_at < NOW() - INTERVAL '90 days';
DELETE FROM github_commits WHERE deleted_at < NOW() - INTERVAL '90 days';
DELETE FROM github_pull_requests WHERE deleted_at < NOW() - INTERVAL '90 days';
DELETE FROM unified_activities WHERE deleted_at < NOW() - INTERVAL '90 days';
```

**Implementation:** Spring @Scheduled task running daily.

### 11.2 Sync Job History Retention

**Policy:** Keep successful sync_jobs for 30 days, failed sync_jobs for 90 days.

**Cleanup Job:**
```sql
-- Runs daily at 3 AM
UPDATE sync_jobs SET deleted_at = NOW(), deleted_by = 0 
WHERE status = 'SUCCESS' AND completed_at < NOW() - INTERVAL '30 days' AND deleted_at IS NULL;

UPDATE sync_jobs SET deleted_at = NOW(), deleted_by = 0 
WHERE status = 'FAILED' AND completed_at < NOW() - INTERVAL '90 days' AND deleted_at IS NULL;
```

---

## 12. Future Enhancements

### 12.1 Planned Features

1. **Real-time Webhooks:** Replace scheduled polling with Jira/GitHub webhooks for instant sync
2. **Incremental Sync:** Only fetch changes since last sync (use `updated > lastSyncDate` filters)
3. **Parallel Processing:** Process multiple project configs concurrently (thread pool)
4. **Smart Retry:** Exponential backoff with jitter for failed sync jobs
5. **Notification System:** Email/Slack alerts for sync failures, token expiration
6. **Data Archiving:** Move old data (>1 year) to cold storage (AWS S3, Azure Blob)
7. **Advanced Analytics:** Pre-aggregate data for faster reporting (materialized views)

### 12.2 Performance Optimization

- **Database Indexes:** Analyze slow queries and add appropriate indexes
- **Connection Pooling:** Optimize HikariCP settings for high-concurrency
- **Batch Insert:** Use JDBC batch operations for inserting large datasets
- **Caching:** Cache project configs in memory to avoid repeated gRPC calls

---

## Summary

Sync Service là **data ingestion layer** của hệ thống SAMT, chịu trách nhiệm:
- Đồng bộ dữ liệu từ Jira và GitHub theo định kỳ
- Normalize dữ liệu vào Common Data Model (UnifiedActivity)
- Lưu trữ dữ liệu thô và chuẩn hóa trong PostgreSQL
- Áp dụng soft delete với 90-day retention
- Gọi Project Config Service qua gRPC để lấy decrypted tokens
- Implement resilience patterns (retry, circuit breaker, rate limiter)

**Next Steps:**
- Xem [SYNC_LOGIC_DESIGN.md](SYNC_LOGIC_DESIGN.md) để hiểu design patterns và data normalization flow
- Xem [GRPC_INTEGRATION.md](GRPC_INTEGRATION.md) để biết cách gọi Project Config Service
- Xem [issues_sync_service.md](issues_sync_service.md) để biết các vấn đề cần làm rõ trước khi implement
