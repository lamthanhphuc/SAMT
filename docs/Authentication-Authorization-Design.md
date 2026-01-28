# Authentication & Authorization Design – Identity Service

## 1. Overview

- **Service**: Identity Service
- **Security Model**: Stateless JWT + Refresh Token Rotation
- **Standards**: Enterprise Security, Defense in Depth

---

## 2. JWT Specification

### 2.1 Algorithm

- **Algorithm**: `HS256`
- **Signing Key**: `JWT_SECRET` (environment variable)

### 2.2 Token Types

| Token Type       | TTL       |
|------------------|-----------|
| Access Token     | 15 minutes |
| Refresh Token    | 7 days    |

### 2.3 JWT Claims (Access Token)

| Claim        | Description                    |
|--------------|--------------------------------|
| `sub`        | User ID                        |
| `email`      | User email                     |
| `roles`      | List of roles (ROLE_*)         |
| `iat`        | Issued at                      |
| `exp`        | Expiration                     |
| `token_type` | ACCESS                         |

### 2.4 Refresh Token Format

- **Type**: Opaque random UUID string
- **Not a JWT**: Stored in database, not self-contained

---

## 3. Refresh Token Rotation Flow

```mermaid
sequenceDiagram
    Client->>Server: Send refresh token
    Server->>Server: Validate token (exists, not revoked, not expired)
    Server->>Server: Revoke old token
    Server->>Server: Generate new refresh token
    Server->>Database: Persist new token
    Server->>Server: Issue new access token
    Server->>Client: Return new tokens
```

**Steps:**

1. Client sends refresh token
2. Server validates token:
   - ✅ Exists in DB
   - ✅ Not revoked
   - ✅ Not expired
3. Server revokes old token
4. Generate new refresh token
5. Persist new token
6. Issue new access token

### Reuse Detection

⚠️ **Security**: If revoked token is reused → **revoke all refresh tokens** of user and force re-login

---

## 4. Password Security

- **Encoder**: `BCryptPasswordEncoder`
- **Strength**: 10

---

## 5. Roles & Authorities

- **Role Format**: `ROLE_ADMIN`, `ROLE_STUDENT`, `ROLE_LECTURER`
- **Storage**: Stored in DB as enum

---

## ✅ Status

**READY FOR IMPLEMENTATION**

---

## 6. Soft Delete Design

### 6.1 Overview

Users are never permanently deleted. Instead, soft delete marks records as deleted while preserving data integrity and audit history.

### 6.2 Soft Delete Fields

| Field | Type | Description |
|-------|------|-------------|
| `deleted_at` | TIMESTAMP | When user was deleted (NULL = active) |
| `deleted_by` | BIGINT | Admin user ID who performed deletion |

### 6.3 Hibernate Integration

```java
@Entity
@SQLRestriction("deleted_at IS NULL")
public class User {
    // Hibernate automatically filters deleted users
}
```

**Behavior:**
- All JPA queries automatically exclude soft-deleted users
- Native queries required to access deleted users (admin only)
- No manual `WHERE deleted_at IS NULL` needed

### 6.4 Soft Delete Flow

```mermaid
sequenceDiagram
    Admin->>System: DELETE /api/admin/users/{id}
    System->>System: Extract admin ID from JWT
    System->>Database: UPDATE users SET deleted_at=NOW(), deleted_by=admin_id
    System->>Database: UPDATE refresh_tokens SET revoked=true WHERE user_id=id
    System->>Database: INSERT INTO audit_logs (action=SOFT_DELETE, ...)
    System->>Admin: 200 OK
```

### 6.5 Restore Flow

```mermaid
sequenceDiagram
    Admin->>System: POST /api/admin/users/{id}/restore
    System->>Database: SELECT * FROM users WHERE id=? (native, bypass filter)
    System->>System: Verify deleted_at IS NOT NULL
    System->>Database: UPDATE users SET deleted_at=NULL, deleted_by=NULL
    System->>Database: INSERT INTO audit_logs (action=RESTORE, ...)
    System->>Admin: 200 OK
```

### 6.6 Authorization Rules

| Operation | Required Role |
|-----------|---------------|
| Soft delete user | `ROLE_ADMIN` |
| Restore user | `ROLE_ADMIN` |
| View deleted users | `ROLE_ADMIN` |

---

## 7. Audit Logging Architecture

### 7.1 Overview

All security-sensitive actions are logged asynchronously to `audit_logs` table for compliance, forensics, and accountability.

### 7.2 Audit Log Structure

| Field | Description |
|-------|-------------|
| `entity_type` | Affected entity (User, RefreshToken) |
| `entity_id` | ID of affected entity |
| `action` | Action performed (see Action Types) |
| `outcome` | SUCCESS, FAILURE, DENIED |
| `actor_id` | User ID from JWT (NULL for anonymous) |
| `actor_email` | Denormalized for query performance |
| `timestamp` | When action occurred |
| `ip_address` | Client IP address |
| `user_agent` | Client user agent |
| `old_value` | JSON state before change |
| `new_value` | JSON state after change |

### 7.3 Audit Flow

```mermaid
sequenceDiagram
    participant Client
    participant Controller
    participant Service
    participant AuditService
    participant Database
    
    Client->>Controller: HTTP Request
    Controller->>Service: Business Logic
    Service->>Database: Entity Operation
    Service->>AuditService: Log Action (async)
    Service->>Controller: Return Result
    Controller->>Client: HTTP Response
    
    Note over AuditService,Database: Async, New Transaction
    AuditService->>Database: INSERT audit_log
```

### 7.4 Async Processing

```java
@Async
@Transactional(propagation = Propagation.REQUIRES_NEW)
public void logAction(...) {
    // Runs in separate thread
    // Separate transaction (persists even if main tx rolls back)
}
```

**Benefits:**
- Non-blocking: main request not delayed by logging
- Fault-tolerant: audit failure doesn't affect business logic
- Independent: audit persists even if main transaction fails

### 7.5 Actor Resolution

| Scenario | Actor Resolution |
|----------|-----------------|
| Authenticated request | Extract from `SecurityContext` |
| Login attempt | Use request email, lookup user ID |
| Registration | Use newly created user ID |
| System action | `actor_id = NULL`, `actor_email = "SYSTEM"` |

### 7.6 Action Types

| Category | Actions |
|----------|---------|
| **Authentication** | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGIN_DENIED`, `LOGOUT` |
| **Token** | `REFRESH_SUCCESS`, `REFRESH_REUSE` |
| **User Lifecycle** | `CREATE`, `UPDATE`, `SOFT_DELETE`, `RESTORE` |
| **Account Status** | `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED` |

### 7.7 Security Event Monitoring

High-priority events for security monitoring:

| Event | Action | Alert Level |
|-------|--------|-------------|
| Token theft suspected | `REFRESH_REUSE` | 🔴 Critical |
| Brute force attempt | Multiple `LOGIN_FAILED` | 🟠 Warning |
| Account takeover | `LOGIN_SUCCESS` from new IP | 🟡 Info |

---

## 8. Admin Operations Design

### 8.1 Admin Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/admin/users/{id}` | DELETE | Soft delete user |
| `/api/admin/users/{id}/restore` | POST | Restore user |
| `/api/admin/users/{id}/lock` | POST | Lock account |
| `/api/admin/users/{id}/unlock` | POST | Unlock account |
| `/api/admin/audit/**` | GET | Query audit logs |

### 8.2 Admin Authorization

```java
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {
    // All endpoints require ROLE_ADMIN
}
```

### 8.3 Admin Action Effects

| Action | User Table | Refresh Tokens | Audit Log |
|--------|------------|----------------|-----------|
| Soft Delete | `deleted_at`, `deleted_by` set | All revoked | `SOFT_DELETE` logged |
| Restore | `deleted_at`, `deleted_by` cleared | (none) | `RESTORE` logged |
| Lock | `status = LOCKED` | All revoked | `ACCOUNT_LOCKED` logged |
| Unlock | `status = ACTIVE` | (none) | `ACCOUNT_UNLOCKED` logged |

---

## ✅ Updated Status

**READY FOR IMPLEMENTATION** (including Soft Delete + Audit Log)

