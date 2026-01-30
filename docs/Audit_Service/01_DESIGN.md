# Audit Service – Centralized Audit Logging

**Version:** 1.0  
**Date:** January 30, 2026  
**Status:** ⏳ PLANNED (Phase 2)

---

## 1. Overview

**Purpose:** Centralized audit logging service để track tất cả business actions across microservices, đảm bảo accountability, compliance và security monitoring.

**Key Responsibilities:**
1. Nhận audit events từ tất cả services (event-driven architecture)
2. Store audit logs trong centralized database
3. Provide query API cho Admin/Security team
4. Real-time alerting cho security events
5. Long-term retention và archival

---

## 2. Architecture

### 2.1 Event-Driven Pattern

```
┌─────────────────┐     Emit Event    ┌──────────────────────┐
│ Identity Service│────────────────────►                      │
└─────────────────┘                    │                      │
                                       │   Message Queue      │
┌─────────────────┐     Emit Event    │   (RabbitMQ/Kafka)   │
│ UserGroup Service│───────────────────►                      │
└─────────────────┘                    │                      │
                                       │                      │
┌─────────────────┐     Emit Event    │                      │
│ProjectConfig Svc│───────────────────►                      │
└─────────────────┘                    └──────────┬───────────┘
                                                  │
┌─────────────────┐     Emit Event               │ Subscribe
│ Sync Service    │──────────────────────────────┤
└─────────────────┘                               │
                                                  ▼
                                       ┌──────────────────────┐
                                       │   Audit Service      │
                                       │                      │
                                       │ - Store audit logs   │
                                       │ - Query API          │
                                       │ - Real-time alerting │
                                       └──────────┬───────────┘
                                                  │
                                                  ▼
                                       ┌──────────────────────┐
                                       │ Centralized Audit DB │
                                       │   (PostgreSQL)       │
                                       └──────────────────────┘
```

**Benefits:**
- **Loose coupling:** Services don't call Audit Service directly (fire-and-forget)
- **Fault tolerance:** If Audit Service down, events queued, no data loss
- **Scalability:** Multiple Audit Service instances can consume from queue
- **Async processing:** Business operations not blocked by audit logging

---

### 2.2 Message Queue

**Technology:** RabbitMQ (Spring Boot compatible) or Kafka (higher throughput)

**Exchange:** `audit.events.exchange` (Topic Exchange)

**Routing Keys:**
- `audit.identity.*` → Identity Service events
- `audit.usergroup.*` → UserGroup Service events
- `audit.projectconfig.*` → ProjectConfig Service events
- `audit.sync.*` → Sync Service events

**Queue:** `audit.events.queue` (durable, auto-ack disabled)

---

## 3. Standardized Audit Event Schema

### 3.1 Core Schema

Tất cả services phải emit audit events theo schema chuẩn:

```json
{
  // WHO - Actor information
  "actorId": "uuid",                      // User ID (from JWT)
  "actorType": "USER | SERVICE | SYSTEM", // Actor type
  "actorEmail": "user@example.com",       // Denormalized email (performance)
  
  // WHAT - Action performed
  "action": "CREATE_USER",                // Action enum (see section 3.2)
  "outcome": "SUCCESS | FAILURE | DENIED",
  
  // WHERE - Resource affected
  "resourceType": "User",                 // Entity type
  "resourceId": "uuid",                   // Entity ID
  
  // WHEN - Timing
  "timestamp": "2026-01-30T10:30:00Z",    // ISO 8601 UTC
  
  // HOW - Request context
  "serviceName": "IdentityService",       // Source service
  "requestId": "uuid",                    // Request trace ID (distributed tracing)
  "ipAddress": "192.168.1.100",           // Client IP
  "userAgent": "Mozilla/5.0...",          // Client user agent
  
  // DETAILS - Before/After state
  "oldValue": { ... },                    // State before change (JSON)
  "newValue": { ... },                    // State after change (JSON)
  "metadata": {                           // Additional context
    "reason": "Account compromised",
    "duration": 1234                      // ms
  }
}
```

---

### 3.2 Standard Action Enums

**Format:** `{VERB}_{RESOURCE_TYPE}`

#### Identity Service Actions

| Action | Description | Logged When |
|--------|-------------|-------------|
| `CREATE_USER` | User created | Registration, Admin create |
| `UPDATE_USER` | User profile updated | UC22 Update Profile |
| `SOFT_DELETE_USER` | User soft deleted | Admin soft delete |
| `RESTORE_USER` | User restored | Admin restore |
| `HARD_DELETE_USER` | User permanently deleted | Super Admin hard delete |
| `LOCK_ACCOUNT` | Account locked | Admin lock |
| `UNLOCK_ACCOUNT` | Account unlocked | Admin unlock |
| `LOGIN_SUCCESS` | Successful login | UC-LOGIN |
| `LOGIN_FAILED` | Failed login | Wrong password |
| `LOGIN_DENIED` | Login denied | Account locked |
| `LOGOUT` | User logged out | UC-LOGOUT |
| `REFRESH_TOKEN_SUCCESS` | Token refreshed | UC-REFRESH-TOKEN |
| `REFRESH_TOKEN_REUSE` | **SECURITY EVENT** | Revoked token reused |
| `MAP_EXTERNAL_ACCOUNTS` | External account mapped | UC-MAP-EXTERNAL-ACCOUNTS |
| `UNMAP_EXTERNAL_ACCOUNTS` | External account unmapped | UC-MAP-EXTERNAL-ACCOUNTS (null value) |

#### UserGroup Service Actions

| Action | Description | Logged When |
|--------|-------------|-------------|
| `CREATE_GROUP` | Group created | UC23 Create Group |
| `UPDATE_GROUP_LECTURER` | Group lecturer changed | **UC27 Update Group Lecturer** |
| `SOFT_DELETE_GROUP` | Group soft deleted | Admin soft delete |
| `HARD_DELETE_GROUP` | Group permanently deleted | Super Admin hard delete |
| `ADD_USER_TO_GROUP` | User added to group | UC24 Add User |
| `REMOVE_USER_FROM_GROUP` | User removed from group | UC26 Remove User |
| `ASSIGN_GROUP_ROLE` | Group role assigned | UC25 Assign Role (LEADER/MEMBER) |
| `CHANGE_GROUP_LEADER` | Leader changed | UC25 demote old leader |

#### ProjectConfig Service Actions

| Action | Description | Logged When |
|--------|-------------|-------------|
| `CREATE_CONFIG` | Config created | UC30 Create Config |
| `UPDATE_CONFIG` | Config updated | UC32 Update Config |
| `SOFT_DELETE_CONFIG` | Config soft deleted | UC33 Delete Config |
| `HARD_DELETE_CONFIG` | Config permanently deleted | Super Admin hard delete |
| `RESTORE_CONFIG` | Config restored | UC35 Restore Config |
| `CONFIG_STATE_CHANGE` | **State machine transition** | UC34 Verify, UC32 Update |
| `VERIFY_CONFIG_SUCCESS` | Verification passed | UC34 Verify (success) |
| `VERIFY_CONFIG_FAILED` | Verification failed | UC34 Verify (failed) |
| `ACCESS_DECRYPTED_TOKENS` | **SECURITY EVENT** | Internal API `/internal/.../tokens` |

#### Sync Service Actions

| Action | Description | Logged When |
|--------|-------------|-------------|
| `SYNC_JIRA_START` | Jira sync started | Manual/scheduled sync |
| `SYNC_JIRA_SUCCESS` | Jira sync completed | Sync finished |
| `SYNC_JIRA_FAILED` | Jira sync failed | API error |
| `SYNC_GITHUB_START` | GitHub sync started | Manual/scheduled sync |
| `SYNC_GITHUB_SUCCESS` | GitHub sync completed | Sync finished |
| `SYNC_GITHUB_FAILED` | GitHub sync failed | API error |
| `AUTO_MAP_EXTERNAL_ACCOUNT` | Auto-mapping succeeded | Jira/GitHub account mapped |
| `AUTO_MAP_FAILED` | Auto-mapping failed | UNMAPPED status |

#### System Actions

| Action | Description | Logged When |
|--------|-------------|-------------|
| `SCHEDULED_JOB_START` | Scheduled job started | Hard delete cleanup job |
| `SCHEDULED_JOB_SUCCESS` | Scheduled job completed | Job finished |
| `SCHEDULED_JOB_FAILED` | Scheduled job failed | Job error |

---

### 3.3 Actor Types

```java
public enum ActorType {
    USER,        // Regular user (ADMIN, LECTURER, STUDENT)
    SERVICE,     // Service-to-service call (Sync Service, Reporting Service)
    SYSTEM       // Scheduled job, automated process
}
```

**Examples:**
- `actorType = USER`: Admin soft deletes user via API
- `actorType = SERVICE`: Sync Service calls ProjectConfig Service to get tokens
- `actorType = SYSTEM`: Scheduled job auto hard deletes expired entities

---

### 3.4 Outcome Values

```java
public enum AuditOutcome {
    SUCCESS,     // Action completed successfully
    FAILURE,     // Action failed (e.g., wrong password, invalid data)
    DENIED       // Action denied (e.g., insufficient permissions, account locked)
}
```

---

## 4. Database Schema

### 4.1 Audit Events Table

```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- WHO
    actor_id UUID NULL,                        -- NULL for SYSTEM actions
    actor_type VARCHAR(20) NOT NULL,           -- USER, SERVICE, SYSTEM
    actor_email VARCHAR(255) NULL,             -- Denormalized for performance
    
    -- WHAT
    action VARCHAR(100) NOT NULL,              -- Standard action enum
    outcome VARCHAR(20) NOT NULL,              -- SUCCESS, FAILURE, DENIED
    
    -- WHERE
    resource_type VARCHAR(50) NOT NULL,        -- User, Group, ProjectConfig, etc.
    resource_id VARCHAR(255) NOT NULL,         -- Entity ID (may be non-UUID)
    
    -- WHEN
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- HOW
    service_name VARCHAR(50) NOT NULL,         -- Source service
    request_id UUID NULL,                      -- Distributed tracing
    ip_address VARCHAR(45) NULL,               -- IPv6 support
    user_agent VARCHAR(500) NULL,
    
    -- DETAILS
    old_value JSONB NULL,                      -- State before
    new_value JSONB NULL,                      -- State after
    metadata JSONB NULL,                       -- Additional context
    
    CONSTRAINT chk_actor_type CHECK (actor_type IN ('USER', 'SERVICE', 'SYSTEM')),
    CONSTRAINT chk_outcome CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED'))
);

-- Performance indexes
CREATE INDEX idx_audit_timestamp ON audit_events(timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_events(actor_id) WHERE actor_id IS NOT NULL;
CREATE INDEX idx_audit_resource ON audit_events(resource_type, resource_id);
CREATE INDEX idx_audit_action ON audit_events(action);
CREATE INDEX idx_audit_service ON audit_events(service_name);
CREATE INDEX idx_audit_outcome ON audit_events(outcome) WHERE outcome != 'SUCCESS';
CREATE INDEX idx_audit_request_id ON audit_events(request_id) WHERE request_id IS NOT NULL;

-- Security events index (fast query for security monitoring)
CREATE INDEX idx_audit_security_events ON audit_events(action)
WHERE action IN (
    'REFRESH_TOKEN_REUSE',
    'LOGIN_DENIED',
    'ACCESS_DECRYPTED_TOKENS',
    'HARD_DELETE_USER',
    'HARD_DELETE_GROUP',
    'HARD_DELETE_CONFIG'
);
```

---

### 4.2 Retention Policy

| Data Age | Action |
|----------|--------|
| 0-90 days | **Hot storage** (PostgreSQL, indexed) |
| 91-365 days | **Warm storage** (PostgreSQL, partitioned by month) |
| 366+ days | **Cold storage** (Archive to S3/Glacier, delete from DB) |

**Implementation:**
- Monthly partition tables: `audit_events_2026_01`, `audit_events_2026_02`, ...
- Automated archival job: Export old partitions to JSON.gz → S3
- GDPR compliance: Audit logs retained forever (legal requirement exception)

---

## 5. Service Integration

### 5.1 Publishing Audit Events (Service Side)

**Spring Boot + RabbitMQ Example:**

```java
@Service
public class AuditEventPublisher {
    
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    public void publishAuditEvent(AuditEvent event) {
        try {
            // Set service name and timestamp
            event.setServiceName("IdentityService");
            event.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));
            
            // Publish to exchange with routing key
            String routingKey = "audit.identity." + event.getAction().toLowerCase();
            rabbitTemplate.convertAndSend("audit.events.exchange", routingKey, event);
            
            log.debug("Published audit event: {}", event.getAction());
        } catch (Exception e) {
            // CRITICAL: Audit failure should NOT block business operation
            log.error("Failed to publish audit event: {}", event, e);
        }
    }
}
```

**Usage in Business Logic:**

```java
@Transactional
public void softDeleteUser(UUID userId, UUID adminId) {
    // Step 1: Get current state (for old_value)
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new UserNotFoundException(userId));
    
    // Step 2: Perform business operation
    user.setDeletedAt(LocalDateTime.now());
    user.setDeletedBy(adminId);
    userRepository.save(user);
    
    // Step 3: Publish audit event (async, non-blocking)
    auditEventPublisher.publishAuditEvent(AuditEvent.builder()
        .actorId(adminId)
        .actorType(ActorType.USER)
        .action("SOFT_DELETE_USER")
        .outcome(AuditOutcome.SUCCESS)
        .resourceType("User")
        .resourceId(userId.toString())
        .oldValue(Map.of(
            "deletedAt", null,
            "status", user.getStatus()
        ))
        .newValue(Map.of(
            "deletedAt", user.getDeletedAt().toString(),
            "status", user.getStatus()
        ))
        .build());
}
```

---

### 5.2 Consuming Audit Events (Audit Service)

```java
@Service
public class AuditEventConsumer {
    
    @Autowired
    private AuditEventRepository auditEventRepository;
    
    @Autowired
    private SecurityAlertService securityAlertService;
    
    @RabbitListener(queues = "audit.events.queue")
    public void handleAuditEvent(AuditEvent event) {
        try {
            // Step 1: Validate event
            validateEvent(event);
            
            // Step 2: Store in database
            AuditEventEntity entity = auditEventMapper.toEntity(event);
            auditEventRepository.save(entity);
            
            // Step 3: Check for security events
            if (isSecurityEvent(event)) {
                securityAlertService.sendAlert(event);
            }
            
            log.info("Processed audit event: {} from {}", event.getAction(), event.getServiceName());
        } catch (Exception e) {
            log.error("Failed to process audit event: {}", event, e);
            // Dead letter queue will handle retry
        }
    }
    
    private boolean isSecurityEvent(AuditEvent event) {
        return List.of(
            "REFRESH_TOKEN_REUSE",
            "LOGIN_DENIED",
            "ACCESS_DECRYPTED_TOKENS",
            "HARD_DELETE_USER"
        ).contains(event.getAction());
    }
}
```

---

## 6. Query API

### 6.1 Admin Query Endpoints

**Base Path:** `/api/audit`

#### GET `/api/audit/events`

Query audit logs with filters.

**Query Parameters:**
- `actorId` (optional): Filter by actor
- `action` (optional): Filter by action
- `resourceType` (optional): Filter by resource type
- `resourceId` (optional): Filter by specific resource
- `serviceName` (optional): Filter by service
- `outcome` (optional): SUCCESS, FAILURE, DENIED
- `startDate` (optional): ISO 8601 datetime
- `endDate` (optional): ISO 8601 datetime
- `page` (default 0): Pagination
- `size` (default 50): Page size

**Example:** `GET /api/audit/events?action=SOFT_DELETE_USER&startDate=2026-01-01T00:00:00Z`

**Response:**
```json
{
  "content": [
    {
      "id": "uuid",
      "actorEmail": "admin@university.edu",
      "action": "SOFT_DELETE_USER",
      "outcome": "SUCCESS",
      "resourceType": "User",
      "resourceId": "user-uuid",
      "timestamp": "2026-01-30T10:30:00Z",
      "serviceName": "IdentityService"
    }
  ],
  "totalElements": 150,
  "totalPages": 3
}
```

---

#### GET `/api/audit/events/{id}`

Get detailed audit event by ID.

**Response:**
```json
{
  "id": "uuid",
  "actorId": "admin-uuid",
  "actorEmail": "admin@university.edu",
  "action": "SOFT_DELETE_USER",
  "outcome": "SUCCESS",
  "resourceType": "User",
  "resourceId": "user-uuid",
  "timestamp": "2026-01-30T10:30:00Z",
  "serviceName": "IdentityService",
  "requestId": "trace-uuid",
  "ipAddress": "192.168.1.100",
  "oldValue": {
    "deletedAt": null,
    "status": "ACTIVE"
  },
  "newValue": {
    "deletedAt": "2026-01-30T10:30:00Z",
    "status": "ACTIVE"
  }
}
```

---

#### GET `/api/audit/security-events`

Get security-sensitive events (real-time monitoring).

**Response:**
```json
{
  "events": [
    {
      "id": "uuid",
      "action": "REFRESH_TOKEN_REUSE",
      "resourceId": "user-uuid",
      "timestamp": "2026-01-30T10:25:00Z",
      "severity": "CRITICAL"
    }
  ]
}
```

---

#### GET `/api/audit/resource/{resourceType}/{resourceId}/history`

Get complete audit trail for a specific resource.

**Example:** `GET /api/audit/resource/User/123/history`

**Response:**
```json
{
  "resourceType": "User",
  "resourceId": "123",
  "history": [
    {
      "timestamp": "2026-01-30T10:30:00Z",
      "action": "SOFT_DELETE_USER",
      "actorEmail": "admin@university.edu",
      "outcome": "SUCCESS"
    },
    {
      "timestamp": "2026-01-15T09:00:00Z",
      "action": "UPDATE_USER",
      "actorEmail": "student@university.edu",
      "outcome": "SUCCESS"
    },
    {
      "timestamp": "2026-01-01T08:00:00Z",
      "action": "CREATE_USER",
      "actorEmail": "system",
      "outcome": "SUCCESS"
    }
  ]
}
```

---

### 6.2 Authorization

**Access Control:**
- **ADMIN:** Can view all audit events
- **LECTURER:** Can view audit events for their supervised groups only
- **STUDENT:** NO access to audit logs
- **SUPER_ADMIN:** Can view all events + export to CSV/JSON

---

## 7. Real-Time Alerting

### 7.1 Alert Rules

| Event | Severity | Alert Channel | Recipients |
|-------|----------|---------------|------------|
| `REFRESH_TOKEN_REUSE` | CRITICAL | Email + Slack | Security team + affected user |
| `LOGIN_DENIED` (rate >5/min) | HIGH | Slack | Security team |
| `HARD_DELETE_USER` | MEDIUM | Email | Super Admins |
| `ACCESS_DECRYPTED_TOKENS` | LOW | Log only | - |
| `SYNC_GITHUB_FAILED` | LOW | Email | Group Leader |

---

### 7.2 Alert Payload

**Email Subject:** `[AUDIT ALERT] REFRESH_TOKEN_REUSE detected`

**Email Body:**
```
SECURITY ALERT: Token Reuse Detected

User: student@university.edu (ID: 123)
Action: REFRESH_TOKEN_REUSE
Timestamp: 2026-01-30 10:25:00 UTC
IP Address: 192.168.1.100
Service: IdentityService

Details:
A revoked refresh token was reused, indicating potential token theft.
All tokens for this user have been revoked (forced logout).

Recommended Actions:
1. Contact user to verify legitimate activity
2. Review recent login history
3. Consider locking account if suspicious

View full audit trail: https://samt.edu/audit/events/{eventId}
```

---

## 8. Performance Considerations

### 8.1 Write Performance

**Challenge:** High volume of audit events (1000+ events/second)

**Optimizations:**
- **Async publishing:** Business operations not blocked by audit logging
- **Batch inserts:** Audit Service batches 100 events before INSERT
- **Connection pooling:** RabbitMQ 50 connections, PostgreSQL 100 connections
- **Partitioning:** Monthly partitions reduce index size

---

### 8.2 Query Performance

**Challenge:** Complex queries on large audit tables (millions of rows)

**Optimizations:**
- **Indexes:** timestamp DESC, actor_id, resource (composite), action
- **Pagination:** Max 1000 results per query
- **Caching:** Recent security events cached (Redis, 5min TTL)
- **Read replicas:** Query API reads from replica, writes to master

---

## 9. Compliance & Security

### 9.1 GDPR Compliance

**Personal Data in Audit Logs:**
- `actorEmail`: Denormalized (for performance)
- `ipAddress`: Client IP address
- `oldValue`, `newValue`: May contain PII

**GDPR Right to Erasure:**
- **Exception:** Audit logs are exempt from erasure (legal requirement)
- **Pseudonymization:** After user hard delete, replace `actorEmail` with `<DELETED_USER_{userId}>`

---

### 9.2 Security Best Practices

- **Immutability:** Audit logs are INSERT-ONLY, no UPDATE/DELETE
- **Integrity:** Audit Service has NO public endpoints to modify logs
- **Access control:** Only ADMIN/SUPER_ADMIN can query audit logs
- **Encryption:** Database encrypted at rest (AES-256)
- **Audit the auditor:** Changes to Audit Service config are logged

---

## 10. Monitoring & SLA

### 10.1 SLAs

| Metric | Target | Alert Threshold |
|--------|--------|-----------------|
| Event processing latency | <500ms | >1s |
| Queue depth | <1000 | >5000 |
| Failed events | <0.1% | >1% |
| Query API response time | <200ms | >500ms |

---

### 10.2 Dashboards

**Grafana Panels:**
1. **Event volume:** Events per second (by service, by action)
2. **Security events:** Count of CRITICAL/HIGH severity events
3. **Queue depth:** RabbitMQ queue size over time
4. **Processing latency:** P50, P95, P99 latency
5. **Failed events:** Count of dead-lettered messages

---

## ✅ Status

**⏳ PLANNED for Phase 2** – Design complete, implementation Q2 2026

**Dependencies:**
- RabbitMQ cluster deployed
- Audit Service PostgreSQL database provisioned
- All services integrate with message queue

**Estimated Timeline:**
- Design & Schema: 2 weeks (✅ Complete)
- RabbitMQ setup: 1 week
- Audit Service implementation: 4 weeks
- Service integration: 3 weeks (parallel)
- Testing & QA: 2 weeks
- **Target Release:** End of Q2 2026
