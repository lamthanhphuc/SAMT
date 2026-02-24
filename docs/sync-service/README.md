# Sync Service

**Version:** 1.0  
**Port:** 8084 (REST)  
**Database:** PostgreSQL (sync_db)  
**Last Updated:** February 23, 2026

---

## Quick Links

- **[API Contract](API_CONTRACT.md)** - Health check endpoints
- **[Database Design](DATABASE.md)** - Schema & migrations
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions

---

## Overview

Sync Service is the **background data synchronization service** for the SAMT system. It periodically fetches data from external APIs (Jira, GitHub) and normalizes it into a unified data model for analytics and reporting.

### Core Responsibilities

✅ **Data Synchronization:**
- Scheduled sync of Jira issues and sprints
- Scheduled sync of GitHub commits and pull requests
- Batch processing with configurable intervals
- Sync job tracking and status logging

✅ **Data Normalization:**
- Adapter pattern for heterogeneous data sources
- Common data model (unified_activities table)
- Dual identity mapping (email + username)
- JSONB metadata storage for source-specific data

✅ **Service Integration:**
- gRPC client for ProjectConfig Service (get decrypted tokens)
- No gRPC server (operates autonomously)
- No user-facing REST endpoints

### What It Does NOT Do

❌ User authentication/authorization → Identity Service  
❌ Project configuration management → ProjectConfig Service  
❌ Real-time data streaming → Uses scheduled batch processing  
❌ User notifications → Notification Service (not implemented)

---

## Architecture

### Service Communication

```
Scheduler (Internal)
      ↓
Sync Service (gRPC Client) → ProjectConfig Service (gRPC, port 9093)
      ↓
Jira/GitHub APIs (External)
      ↓
PostgreSQL (sync_db)
      ↑
Reporting/AI Services (Read-only access)
```

### Package Structure

```
sync-service/
├── src/main/java/com/example/syncservice/
│   ├── adapter/              # Data transformation (Jira, GitHub)
│   ├── client/               # External API clients (Jira, GitHub)
│   ├── config/               # Spring configuration, async, resilience
│   ├── dto/                  # Data transfer objects
│   ├── entity/               # JPA entities
│   ├── exception/            # Error handling
│   ├── grpc/                 # gRPC client for ProjectConfig
│   ├── repository/           # JPA repositories with custom UPSERT
│   ├── scheduler/            # Scheduled sync jobs
│   └── service/              # Business logic
│
├── src/main/resources/       # Config, migrations
└── src/test/                 # Unit & integration tests
```

---

## Data Model

### Core Entities

**unified_activities**
- Primary key: `id` (BIGINT)
- Foreign key: `project_config_id` (logical, no constraint)
- Unique key: `(project_config_id, source, external_id)`
- Source: `JIRA`, `GITHUB`
- Activity type: `TASK`, `ISSUE`, `BUG`, `COMMIT`, `PULL_REQUEST`
- Soft delete: `deleted_at`, `deleted_by`

**sync_jobs**
- Primary key: `id` (BIGINT)
- Foreign key: `project_config_id` (logical, no constraint)
- Job type: `JIRA_ISSUES`, `JIRA_SPRINTS`, `GITHUB_COMMITS`, `GITHUB_PRS`
- Status: `RUNNING`, `COMPLETED`, `FAILED`
- Tracks: `items_processed`, `error_message`, `started_at`, `completed_at`

**Raw Data Tables:**
- `jira_issues` - Raw Jira issue data
- `jira_sprints` - Raw Jira sprint data
- `github_commits` - Raw GitHub commit data
- `github_pull_requests` - Raw GitHub PR data

---

## Scheduler Configuration

### Sync Intervals

| Job Type | Default Interval | Configurable |
|----------|------------------|--------------|
| Jira Issues | Every 30 minutes | `sync.scheduler.jira-issues-cron` |
| Jira Sprints | Every 2 hours | `sync.scheduler.jira-sprints-cron` |
| GitHub Commits | Every 15 minutes | `sync.scheduler.github-commits-cron` |
| GitHub PRs | Every 1 hour | `sync.scheduler.github-prs-cron` |

### Concurrency Model

- Thread pool: 2 core threads, 5 max threads
- Queue capacity: 100 pending configs
- Rejection policy: AbortPolicy (track failures)
- Multi-instance safety: ShedLock prevents duplicate execution

---

## Configuration

### Environment Variables

```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5436/sync_db
DATABASE_USERNAME=postgres
DATABASE_PASSWORD=secure_password

# Service Authentication
SERVICE_AUTH_KEY=sync-service-secret-key-change-in-production

# gRPC Client
PROJECT_CONFIG_SERVICE_GRPC_HOST=localhost
PROJECT_CONFIG_SERVICE_GRPC_PORT=9093

# Scheduler
SYNC_SCHEDULER_ENABLED=true

# External API Timeouts
JIRA_TIMEOUT=30
GITHUB_TIMEOUT=30
```

### Database Configuration

```yaml
spring:
  datasource:
    url: ${DATABASE_URL}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
```

---

## Getting Started

### Prerequisites

- Java 21+
- PostgreSQL 15+
- Maven 3.9+

### Docker Build

```bash
mvn clean package
docker build -t sync-service:1.0 .
docker run -p 8084:8080 sync-service:1.0
```

### Health Check

```bash
curl http://localhost:8084/actuator/health
```

### Metrics

```bash
curl http://localhost:8084/actuator/prometheus
```

---

## Dependencies

### Upstream Services (Required)
- ProjectConfig Service (gRPC port 9093) - For decrypted tokens
- PostgreSQL (port 5436) - sync_db database
- Jira API - External data source
- GitHub API - External data source

### Downstream Services (Consumers)
- Reporting Service - Reads unified_activities
- AI Service - Reads unified_activities
- Analysis Service - Reads unified_activities

---

## Key Features

### Resilience

- Retry logic with exponential backoff (3 attempts)
- Circuit breaker for gRPC calls
- Rate limit handling for external APIs
- Graceful degradation (fallback to empty results)

### Performance

- Batch UPSERT (500 records per transaction)
- Single database roundtrip per batch
- EntityManager flush/clear to prevent memory leaks
- Async execution with configurable thread pool

### Observability

- Prometheus metrics (success rate, duration, records processed)
- Correlation ID propagation (MDC)
- Structured logging with context
- Health checks for database and gRPC connectivity

### Security

- Service-to-service authentication (gRPC headers)
- Parameterized queries (no SQL injection)
- API tokens masked in logs
- Credential externalization via environment variables

---

## Known Limitations

- No real-time sync (scheduled batch processing only)
- No user-facing REST API (background service)
- Historical data preserved on config deletion (by design)
- Orphaned contributions if user identity mapping not found
- Manual token refresh required on expiration

---

## Related Documentation

- [System Architecture](../SYSTEM_ARCHITECTURE.md)
- [Identity Service](../Identity_Service/README.md)
- [ProjectConfig Service](../ProjectConfig/README.md)
- [User Group Service](../UserGroup_Service/README.md)
