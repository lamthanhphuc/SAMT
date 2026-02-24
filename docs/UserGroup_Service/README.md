# User-Group Service

**Version:** 1.0  
**Port:** 8082 (REST), 9095 (gRPC)  
**Database:** PostgreSQL (usergroup_db)  
**Last Updated:** February 23, 2026

---

## Quick Links

- **[API Contract](API_CONTRACT.md)** - REST endpoints & error handling
- **[Database Design](DATABASE.md)** - Schema & constraints
- **[Security Design](SECURITY.md)** - JWT validation & authorization
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup & gRPC client config
- **[Test Cases](TEST_CASES.md)** - Test specifications

---

## Overview

User-Group Service manages student groups and group memberships for academic project assignments in the SAMT system.

### Core Responsibilities

✅ **Group Management:**
- Group CRUD operations (create, read, update, soft delete)
- Semester-based group isolation
- Role-based access control (ADMIN, LECTURER, STUDENT)

✅ **Membership Management:**
- Add/remove members
- Promote/demote leaders
- One student per group per semester
- One leader per group

✅ **User Profile Proxy:**
- Get user profile (UC21) - delegates to Identity Service
- Update user profile (UC22) - delegates to Identity Service

✅ **Business Rules Enforcement:**
- Lecturer validation (must be LECTURER role and ACTIVE)
- Only STUDENT can be added to groups
- Automatic cleanup on user deletion (via Kafka events)

✅ **gRPC Server:**
- Group validation for other services
- Membership checking
- Leader verification

### What It Does NOT Do

❌ User authentication/registration → Identity Service  
❌ JWT generation → Identity Service  
❌ Password management → Identity Service  
❌ Project configuration → Project Config Service  
❌ Data synchronization → Sync Service

---

## Architecture

### Service Communication

```
Client (REST/JSON)
      ↓
API Gateway (JWT Auth, port 8080)
      ↓
/api/groups/**, /api/users/** → User-Group Service (REST, port 8082)
      ↓
gRPC Client → Identity Service (gRPC, port 9091)
      ↑
Kafka Consumer ← Identity Service (user.deleted events)
```

**Exposed gRPC Server (port 9095):**
- `VerifyGroupExists` - Check if group exists
- `CheckGroupLeader` - Verify if user is group leader
- `CheckGroupMember` - Check if user is group member
- `GetGroup` - Retrieve full group details

**Consumed by:**
- Project Config Service (group validation)
- Report Service (membership filtering)

### Package Structure

```
user-group-service/
├── src/main/java/com/example/user_groupservice/
│   ├── config/               # Security, Resilience, Kafka
│   ├── controller/           # REST endpoints
│   │   ├── GroupController.java          # /groups/** (group CRUD)
│   │   ├── GroupMemberController.java    # /groups/{id}/members/** (members)
│   │   └── UserController.java           # /users/** (profile proxy)
│   ├── dto/                  # Request/Response DTOs
│   ├── entity/               # JPA Entities (Group, Semester, UserSemesterMembership)
│   ├── event/                # Kafka consumer (user.deleted)
│   ├── grpc/                 # Identity Service client + server
│   ├── repository/           # JPA repositories
│   ├── security/             # JWT filter, CurrentUser
│   └── service/              # Business logic
│
├── src/main/proto/           # gRPC contracts
└── src/main/resources/
    ├── application.yml       # Configuration
    └── db/migration/         # Flyway migrations
```

---

## Data Model

### Core Tables

**semesters**
- Stores academic terms (e.g., "SPRING2025", "FALL2024")
- User-Group Service is the source of truth for semester data
- No external service dependencies

**groups**
- Primary key: `id` (BIGINT auto-increment)
- Unique constraint: `(group_name, semester_id)` per semester
- References: `semester_id` (FK), `lecturer_id` (logical reference to Identity Service)
- Soft delete: `deleted_at`, `deleted_by`

**user_semester_membership**
- Composite primary key: `(user_id, semester_id)` - enforces one group per student per semester
- Unique constraint: One LEADER per group
- References: `semester_id` (FK), `group_id` (FK), `user_id` (logical reference to Identity Service)
- Soft delete: `deleted_at`, `deleted_by`

### Cross-Service References

| Field | Service | Validation Method |
|-------|---------|-------------------|
| `lecturer_id` | Identity Service | gRPC `VerifyUserExists()` |
| `user_id` | Identity Service | gRPC `VerifyUserExists()` |
| `deleted_by` | Identity Service | Audit only (no validation) |

**No Foreign Key Constraints:** User references are logical only (microservices pattern). Validation performed via gRPC calls.

**See:** [DATABASE.md](DATABASE.md) for complete schema specification.

---

## Execution Flow

### Group Creation (UC23)

1. Validate JWT token (API Gateway)
2. Check actor role (ADMIN or LECTURER only)
3. Validate lecturer via gRPC: `IdentityService.VerifyUserExists(lecturerId)`
4. Check lecturer has LECTURER role and ACTIVE status
5. Validate semester exists and is active
6. Check group name uniqueness in semester
7. Create group in database
8. Return group details with HTTP 201 CREATED

**Circuit Breaker:** If Identity Service is unavailable, request fails fast with 503 Service Unavailable.

### Add Member (UC24)

1. Validate JWT token (API Gateway)
2. Check actor authorization (ADMIN, LECTURER, or group LEADER)
3. Validate user via gRPC: `IdentityService.VerifyUserExists(userId)`
4. Check user has STUDENT role and ACTIVE status
5. Check user not already in a group for this semester (PK constraint)
6. Check group exists and is not soft-deleted
7. Add membership with MEMBER role
8. Return membership details with HTTP 201 CREATED

**Constraint Enforcement:** Database composite PK `(user_id, semester_id)` prevents duplicate group membership.

### User Deletion Cleanup

1. Identity Service publishes `user.deleted` event to Kafka topic
2. User-Group Service consumes event via `UserDeletedEventConsumer`
3. Find all memberships for deleted user
4. Soft delete all memberships (set `deleted_at`, `deleted_by`)
5. Groups remain intact (lecturer reference preserved for audit)

**Idempotency:** Duplicate events are safe (already soft-deleted memberships ignored).

**Known Gap:** Leader auto-promotion not implemented. Admin must manually promote new leader if deleted user was a leader.

---

## Dependencies

### Identity Service (gRPC)

**Connection:** `grpc://localhost:9091`

**Purpose:** User validation and profile operations

**RPCs Used:**
- `VerifyUserExists(userId)` - Validate user and get role/status
- `GetUsers(userIds)` - Batch fetch user details for member lists
- `UpdateUser(userId, request)` - Proxy for UC22 (Update User Profile)

**Resilience:**
- Circuit breaker (50% failure threshold, 10s wait)
- Retry pattern (3 attempts, exponential backoff)

**Failure Handling:** If gRPC call fails, return 503 Service Unavailable.

**See:** [IMPLEMENTATION.md](IMPLEMENTATION.md) for circuit breaker configuration.

### Kafka Event Bus

**Connection:** `kafka://localhost:9092`

**Purpose:** User deletion synchronization

**Consumed Topics:**
- `user.deleted` - Cleanup memberships when user is deleted

**Event Processing:**
- Idempotent (duplicate events safe)
- Automatic soft delete of all user memberships
- Groups preserved for audit purposes

---

## Configuration

### Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `SERVER_PORT` | `8082` | HTTP port |
| `GRPC_SERVER_PORT` | `9095` | gRPC server port |
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port |
| `DB_NAME` | `usergroup_db` | Database name |
| `JWT_SECRET` | (required) | Shared JWT secret (must match Identity Service) |
| `IDENTITY_SERVICE_HOST` | `localhost` | Identity Service gRPC host |
| `IDENTITY_SERVICE_PORT` | `9091` | Identity Service gRPC port |
| `KAFKA_BROKERS` | `localhost:9092` | Kafka bootstrap servers |

### Application Properties

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway handles migrations
  
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS}
    consumer:
      group-id: user-group-service

jwt:
  secret: ${JWT_SECRET}

grpc:
  client:
    identity-service:
      address: static://${IDENTITY_SERVICE_HOST}:${IDENTITY_SERVICE_PORT}
  server:
    port: ${GRPC_SERVER_PORT:9095}
```

**See:** [IMPLEMENTATION.md](IMPLEMENTATION.md) for complete configuration reference.

---

## Deployment Notes

### Database Setup

1. Create PostgreSQL database: `usergroup_db`
2. Flyway migrations run automatically on startup
3. Sample semester data created in V1 migration

### Dependencies

**Required Services:**
- Identity Service (gRPC port 9091) - CRITICAL
- Kafka (port 9092) - for user deletion events
- PostgreSQL (port 5432) - database
- API Gateway (port 8080) - external access

**Optional Services:**
- Project Config Service (gRPC client of this service)
- Report Service (gRPC client of this service)

### Health Check

```bash
GET /actuator/health

# Expected response
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

---

## Known Limitations

### 1. No Leader Auto-Promotion

**Issue:** When leader leaves or is deleted, group has no leader

**Impact:** Admin must manually promote new leader

**Workaround:** Frontend/admin must detect and handle leaderless groups

### 2. No Cascade Soft Delete

**Issue:** Deleting a group does NOT soft delete its memberships

**Behavior:** Group deletion requires empty group (0 active members)

**Rationale:** 
- Data safety (prevents accidental bulk deletion)
- Explicit admin actions required
- Clear audit trail (each member removal logged separately)

**Workaround:** Remove all members before deleting group

### 3. No User Foreign Key Constraints

**Issue:** `user_id`, `lecturer_id` have NO database foreign key

**Consequences:**
- Orphaned references possible if user deleted in Identity Service
- No automatic cascade delete at database level
- Must rely on Kafka events for cleanup

**Mitigation:**
- Kafka consumer handles user deletion events
- gRPC validation before operations

### 4. No Semester Archiving

**Issue:** Old semesters cannot be archived or hidden

**Impact:** All semesters visible in API responses

**Workaround:** Use `is_active` flag to filter current semester

### 5. No Retry for Kafka Events

**Issue:** If event processing fails, event is lost

**Impact:** Membership cleanup may fail on transient errors

**Mitigation:** Kafka retention policy (7 days), manual admin cleanup if needed

### 6. No Group Transfer

**Issue:** Cannot change group's lecturer after creation

**Impact:** If lecturer leaves, group cannot be reassigned

**Workaround:** Create new group, migrate members manually

---

## References

- **[API Contract](API_CONTRACT.md)** - REST & gRPC endpoint specifications
- **[Database Design](DATABASE.md)** - Complete schema documentation
- **[Security Design](SECURITY.md)** - JWT validation & authorization
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup & configuration
- **[Test Cases](TEST_CASES.md)** - Test specifications
- **[gRPC Contract](GRPC_CONTRACT.md)** - gRPC API documentation
- **[System Architecture](../SYSTEM_ARCHITECTURE.md)** - Overall system design

---

**Questions?** See [IMPLEMENTATION.md](IMPLEMENTATION.md) or contact Backend Team Lead

