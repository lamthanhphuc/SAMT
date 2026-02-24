# User-Group Service - Database Design

**Service:** User-Group Service  
**Database:** PostgreSQL (usergroup_db)  
**Version:** 1.0  
**Last Updated:** February 23, 2026

---

## Database Schema

### Overview

User-Group Service manages three core entities:
1. **Semesters** - Academic terms
2. **Groups** - Student project groups bound to a semester
3. **User-Semester Memberships** - Student membership in groups

**Cross-Service References:**
- `user_id`, `lecturer_id`, `deleted_by` are logical references to Identity Service
- **NO foreign key constraints** across service boundaries (microservices pattern)
- User validation performed via gRPC calls to Identity Service

---

## Table: `semesters`

**Purpose:** Store academic semester information

```sql
CREATE TABLE semesters (
    id BIGSERIAL PRIMARY KEY,
    semester_code VARCHAR(50) NOT NULL UNIQUE,
    semester_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_semesters_code ON semesters(semester_code);
CREATE INDEX idx_semesters_active ON semesters(is_active);
```

### Columns

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | Auto-increment semester ID |
| `semester_code` | VARCHAR(50) | NOT NULL, UNIQUE | Semester code (e.g., "SPRING2025", "FALL2024") |
| `semester_name` | VARCHAR(100) | NOT NULL | Human-readable name |
| `start_date` | DATE | NOT NULL | Semester start date |
| `end_date` | DATE | NOT NULL | Semester end date |
| `is_active` | BOOLEAN | NOT NULL, DEFAULT TRUE | Active semester flag |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT | Creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT | Last update timestamp |

### Business Rules

- ✅ **Semester code is unique** (case-sensitive)
- ✅ **One active semester at a time** (not enforced by DB constraint)
- ✅ **Semesters are managed locally** (User-Group Service is the source of truth)
- ✅ **Semester data is seeded via Flyway migrations** (V1__initial_schema.sql)

### Sample Data

```sql
INSERT INTO semesters (semester_code, semester_name, start_date, end_date, is_active)
VALUES 
    ('SPRING2025', 'Spring Semester 2025', '2025-01-01', '2025-05-31', TRUE),
    ('FALL2024', 'Fall Semester 2024', '2024-09-01', '2024-12-31', FALSE);
```

---

## Table: `groups`

**Purpose:** Store student project groups

```sql
CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    semester_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,  -- Logical reference to Identity Service (NO FK)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- Logical reference to Identity Service (NO FK)
    version INTEGER NOT NULL DEFAULT 0,  -- Optimistic locking (added in V5)
    
    CONSTRAINT fk_groups_semester FOREIGN KEY (semester_id) 
        REFERENCES semesters(id) ON DELETE CASCADE
);

-- Unique constraint: group name must be unique per semester (soft delete aware)
CREATE UNIQUE INDEX idx_groups_name_semester_unique 
    ON groups(group_name, semester_id) 
    WHERE deleted_at IS NULL;

CREATE INDEX idx_groups_lecturer_id ON groups(lecturer_id);
CREATE INDEX idx_groups_semester_id ON groups(semester_id);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);
```

### Columns

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | BIGSERIAL | PRIMARY KEY | Auto-increment group ID |
| `group_name` | VARCHAR(100) | NOT NULL | Group name (unique per semester) |
| `semester_id` | BIGINT | NOT NULL, FK | Reference to `semesters.id` |
| `lecturer_id` | BIGINT | NOT NULL | Logical reference to Identity Service `users.id` (NO FK) |
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT | Creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT | Last update timestamp |
| `deleted_at` | TIMESTAMP | NULL | Soft delete timestamp (NULL = active) |
| `deleted_by` | BIGINT | NULL | Logical reference to Identity Service `users.id` (NO FK) |
| `version` | INTEGER | NOT NULL, DEFAULT 0 | Optimistic locking version (JPA `@Version`) |

### Cross-Service References

| Field | References | Validation Method |
|-------|------------|-------------------|
| `lecturer_id` | Identity Service `users.id` | gRPC `VerifyUserExists(lecturerId)` |
| `deleted_by` | Identity Service `users.id` | Audit only (no validation) |

**Rationale for NO Foreign Key:**
- Microservices pattern: Each service owns its own database
- Identity Service can delete users without coordination
- Group retains lecturer reference for audit purposes

### Business Rules

- ✅ **Group name unique per semester** (enforced by partial unique index)
- ✅ **Soft delete aware** (deleted groups excluded from uniqueness check)
- ✅ **Lecturer must be LECTURER role** (validated via gRPC before creation)
- ✅ **Group cannot be deleted if it has members** (enforced at service layer)
- ✅ **One group per semester isolates projects** (prevents cross-semester conflicts)

### Soft Delete Behavior

**When Group is Soft Deleted:**
- `deleted_at` set to current timestamp
- `deleted_by` set to user ID who performed deletion
- Group remains in database (for audit/history)
- Members can see group history but cannot modify
- Group name can be reused in same semester after deletion

**Query Filtering:**
- Standard JPA queries filter soft-deleted records automatically
- Admin queries can include soft-deleted records via `findAllIncludingDeleted()`

---

## Table: `user_semester_membership`

**Purpose:** Store student membership in groups

```sql
CREATE TABLE user_semester_membership (
    user_id BIGINT NOT NULL,  -- Logical reference to Identity Service (NO FK)
    semester_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    group_role VARCHAR(20) NOT NULL CHECK (group_role IN ('LEADER', 'MEMBER')),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- Logical reference to Identity Service (NO FK)
    version INTEGER NOT NULL DEFAULT 0,  -- Optimistic locking (added in V5)
    
    PRIMARY KEY (user_id, semester_id),
    CONSTRAINT fk_usm_semester FOREIGN KEY (semester_id) 
        REFERENCES semesters(id) ON DELETE CASCADE,
    CONSTRAINT fk_usm_group FOREIGN KEY (group_id) 
        REFERENCES groups(id) ON DELETE CASCADE
);

-- Unique constraint: Only one LEADER per group
CREATE UNIQUE INDEX idx_usm_group_leader_unique 
    ON user_semester_membership(group_id) 
    WHERE group_role = 'LEADER' AND deleted_at IS NULL;

CREATE INDEX idx_usm_user_id ON user_semester_membership(user_id);
CREATE INDEX idx_usm_group_id ON user_semester_membership(group_id);
CREATE INDEX idx_usm_semester_id ON user_semester_membership(semester_id);
CREATE INDEX idx_usm_deleted_at ON user_semester_membership(deleted_at);
```

### Columns

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `user_id` | BIGINT | NOT NULL, PK | Logical reference to Identity Service `users.id` (NO FK) |
| `semester_id` | BIGINT | NOT NULL, PK, FK | Reference to `semesters.id` |
| `group_id` | BIGINT | NOT NULL, FK | Reference to `groups.id` |
| `group_role` | VARCHAR(20) | NOT NULL, CHECK | Role: 'LEADER' or 'MEMBER' |
| `joined_at` | TIMESTAMP | NOT NULL, DEFAULT | Membership creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT | Last update timestamp |
| `deleted_at` | TIMESTAMP | NULL | Soft delete timestamp (NULL = active) |
| `deleted_by` | BIGINT | NULL | Logical reference to Identity Service `users.id` (NO FK) |
| `version` | INTEGER | NOT NULL, DEFAULT 0 | Optimistic locking version (JPA `@Version`) |

### Composite Primary Key

**Primary Key:** `(user_id, semester_id)`

**Rationale:**
- Enforces "one group per user per semester" rule at database level
- Student cannot join multiple groups in same semester
- Student can join different groups in different semesters

**Error on Violation:**
```
ERROR: duplicate key value violates unique constraint "user_semester_membership_pkey"
Detail: Key (user_id, semester_id)=(1, 1) already exists.
```

### Cross-Service References

| Field | References | Validation Method |
|-------|------------|-------------------|
| `user_id` | Identity Service `users.id` | gRPC `VerifyUserExists(userId)` |
| `deleted_by` | Identity Service `users.id` | Audit only (no validation) |

### Business Rules

- ✅ **One group per student per semester** (enforced by composite PK)
- ✅ **One leader per group** (enforced by partial unique index)
- ✅ **Only STUDENT role can be added** (validated via gRPC before creation)
- ✅ **Leader promotion demotes current leader** (enforced at service layer)
- ✅ **Cannot remove leader** (must demote to MEMBER first)
- ✅ **Automatic cleanup on user deletion** (via Kafka `user.deleted` event)

### Unique Constraints

**One Leader Per Group:**
```sql
CREATE UNIQUE INDEX idx_usm_group_leader_unique 
    ON user_semester_membership(group_id) 
    WHERE group_role = 'LEADER' AND deleted_at IS NULL;
```

**Behavior:**
- Only one LEADER per group allowed
- Soft-deleted leaders excluded from uniqueness check
- Promoting new leader requires demoting current leader first

**Error on Violation:**
```
ERROR: duplicate key value violates unique constraint "idx_usm_group_leader_unique"
Detail: Key (group_id)=(1) already exists.
```

---

## Entity Relationship Diagram

```
┌──────────────────┐
│    semesters     │
├──────────────────┤
│ id (PK)          │
│ semester_code    │
│ semester_name    │
│ start_date       │
│ end_date         │
│ is_active        │
└────────┬─────────┘
         │ 1
         │
         │ has many
         │
         │ n
┌────────▼─────────────┐
│      groups          │
├──────────────────────┤
│ id (PK)              │
│ group_name           │
│ semester_id (FK)     │
│ lecturer_id (LR)     │──────► Identity Service (gRPC validation)
│ deleted_at           │
│ deleted_by (LR)      │──────► Identity Service (audit only)
│ version              │
└────────┬─────────────┘
         │ 1
         │
         │ has many
         │
         │ n
┌────────▼───────────────────────┐
│  user_semester_membership      │
├────────────────────────────────┤
│ user_id (PK, LR)               │──────► Identity Service (gRPC validation)
│ semester_id (PK, FK)           │
│ group_id (FK)                  │
│ group_role (LEADER/MEMBER)     │
│ joined_at                      │
│ deleted_at                     │
│ deleted_by (LR)                │──────► Identity Service (audit only)
│ version                        │
└────────────────────────────────┘

Legend:
- PK = Primary Key
- FK = Foreign Key (database constraint)
- LR = Logical Reference (no database constraint, validated via gRPC)
```

---

## Constraints & Rules Summary

### Database-Level Constraints

| Constraint | Type | Purpose |
|------------|------|---------|
| `PRIMARY KEY (user_id, semester_id)` | Composite PK | One group per student per semester |
| `UNIQUE (group_name, semester_id) WHERE deleted_at IS NULL` | Partial Unique Index | Unique group names per semester |
| `UNIQUE (group_id) WHERE group_role='LEADER' AND deleted_at IS NULL` | Partial Unique Index | One leader per group |
| `CHECK (group_role IN ('LEADER', 'MEMBER'))` | Check Constraint | Valid role values only |
| `FOREIGN KEY semester_id REFERENCES semesters(id)` | FK | Referential integrity |
| `FOREIGN KEY group_id REFERENCES groups(id)` | FK | Referential integrity |

### Application-Level Rules

| Rule | Enforcement |
|------|-------------|
| Lecturer must be LECTURER role | gRPC validation before group creation |
| Student must be STUDENT role | gRPC validation before adding member |
| Group must be empty before deletion | Service layer check (count members) |
| Leader promotion demotes current leader | Transaction in service layer |
| Cannot remove leader directly | Service layer validation |

---

## Database Migrations

### Migration History

| Version | File | Description |
|---------|------|-------------|
| V1 | `V1__initial_schema.sql` | Create tables, indexes, sample data |
| V2 | `V2__add_user_group_constraints.sql` | Add unique constraints, leader trigger |
| V3 | `V3__fix_leader_trigger.sql` | Fix leader promotion trigger logic |
| V4 | `V4__remove_unused_group_audit_log.sql` | Remove unused audit table |
| V5 | `V5__add_optimistic_locking.sql` | Add version column for concurrency control |

### Migration Strategy

- **Automatic:** Flyway runs on application startup
- **Rollback:** Not needed (forward-only migrations)
- **Validation:** Checksum verification (detects manual changes)
- **Idempotent:** Re-running migrations has no effect

---

## Audit Logging

**Implementation:** Application-level logging only (no database audit table)

**Rationale:**
- Simpler architecture (no additional database writes)
- Better performance (no audit table overhead)
- Centralized log aggregation (ELK stack, CloudWatch)
- Immutable log records (cannot be tampered via SQL)

**What is Logged:**
- Group creation, update, deletion
- Membership changes (add, remove, promote, demote)
- Authorization failures
- User deletion events (Kafka consumer)

**Log Format:** Structured JSON via SLF4J

Example:
```json
{
  "timestamp": "2026-02-23T10:30:00Z",
  "level": "INFO",
  "logger": "GroupService",
  "message": "Group created",
  "groupId": 123,
  "groupName": "Group A",
  "semesterId": 1,
  "lecturerId": 5,
  "actorId": 5,
  "actorRole": "LECTURER"
}
```

---

## Soft Delete Behavior

### Groups

**Soft Delete:**
- Set `deleted_at` = current timestamp
- Set `deleted_by` = user ID who performed deletion
- Group remains in database (audit/history)
- Members can view but cannot modify

**Queries:**
- Standard queries filter `deleted_at IS NULL`
- Admin queries can include deleted via `findAllIncludingDeleted()`

### Memberships

**Soft Delete:**
- Set `deleted_at` = current timestamp
- Set `deleted_by` = user ID who performed removal
- Membership remains in database (audit/history)

**Automatic Cleanup:**
- When user is deleted in Identity Service
- Kafka `user.deleted` event consumed
- All memberships for user soft-deleted automatically

---

## Optimistic Locking

**Implementation:** JPA `@Version` annotation

**Purpose:** Prevent concurrent modification conflicts

**How it Works:**
1. Entity has `version` column (INTEGER, DEFAULT 0)
2. On update, JPA checks version matches database
3. If version matches, update and increment version
4. If version doesn't match, throw `OptimisticLockException`

**Example:**
```java
// Thread A reads group (version = 0)
Group group = repository.findById(1);

// Thread B updates group (version = 0 → 1)
group.setGroupName("New Name");
repository.save(group);  // SUCCESS, version now 1

// Thread A tries to update (version still 0)
group.setGroupName("Another Name");
repository.save(group);  // FAIL - OptimisticLockException
                         // Expected version 0, found version 1
```

**Error Handling:**
```java
catch (OptimisticLockException e) {
    throw new ConflictException(
        "Group was modified by another user. Please refresh and try again."
    );
}
```

---

## Performance Considerations

### Indexes

All critical queries are indexed:
- Group lookup by ID (PK)
- Group lookup by semester (indexed)
- Group lookup by lecturer (indexed)
- Membership lookup by user (indexed)
- Membership lookup by group (indexed)
- Soft delete filtering (indexed)

### Query Patterns

**Efficient:**
```sql
-- Lookup group by ID (uses PK)
SELECT * FROM groups WHERE id = 123 AND deleted_at IS NULL;

-- List groups for semester (uses index)
SELECT * FROM groups WHERE semester_id = 1 AND deleted_at IS NULL;

-- Count members in group (uses index)
SELECT COUNT(*) FROM user_semester_membership 
WHERE group_id = 123 AND deleted_at IS NULL;
```

**Avoid:**
```sql
-- Full table scan (no index on group_name alone)
SELECT * FROM groups WHERE group_name = 'Group A';

-- Better: Include semester_id
SELECT * FROM groups 
WHERE group_name = 'Group A' AND semester_id = 1 AND deleted_at IS NULL;
```

---

## Known Limitations

### 1. No Cascade Soft Delete

**Behavior:** Deleting a group does NOT soft delete its memberships

**Rationale:**
- Explicit admin actions required
- Data safety (prevents accidental bulk deletion)
- Clear audit trail (each member removal logged separately)

**Workaround:** Admin must remove all members before deleting group

### 2. No User Foreign Key

**Limitation:** `user_id`, `lecturer_id` have NO database foreign key

**Consequences:**
- Orphaned references possible if user deleted in Identity Service
- No automatic cascade delete
- Must rely on Kafka events for cleanup

**Mitigation:**
- Kafka consumer handles user deletion events
- gRPC validation before operations
- Background job to detect orphaned references (not implemented)

### 3. No Semester Archiving

**Limitation:** Old semesters cannot be archived or hidden

**Consequences:**
- All semesters visible in API responses
- No way to mark semester as "archived" vs "inactive"

**Workaround:** Use `is_active` flag to filter current semester

### 4. No Leader Auto-Promotion

**Limitation:** When leader leaves, no automatic promotion

**Consequences:**
- Group left without leader
- Admin must manually promote new leader

**Workaround:** Frontend/admin must detect and handle

---

## References

- **[README](README.md)** - Service overview
- **[API Contract](API_CONTRACT.md)** - API specifications
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions
- **Flyway Migrations:** `user-group-service/src/main/resources/db/migration/`
- **JPA Entities:** `user-group-service/src/main/java/com/example/user_groupservice/entity/`

---

**Questions?** Contact Backend Database Team
