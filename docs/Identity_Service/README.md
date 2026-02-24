# Identity Service

**Version:** 1.0  
**Port:** 8081 (REST), 9091 (gRPC)  
**Database:** PostgreSQL (identity_db)  
**Last Updated:** February 9, 2026

---

## Quick Links

- **[API Contract](API_CONTRACT.md)** - REST & gRPC endpoints
- **[Database Design](DATABASE.md)** - Schema & migrations
- **[Security Design](SECURITY.md)** - JWT, authentication, authorization
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions
- **[Test Cases](TEST_CASES.md)** - Test specifications

---

## Overview

Identity Service is the **SINGLE SOURCE OF TRUTH** for user authentication and authorization in SAMT system.

### Core Responsibilities

✅ **Authentication:**
- User registration (self-service for STUDENT role only)
- Login/logout with JWT + Refresh Token
- Password hashing (BCrypt, strength 10)
- Token generation and validation

✅ **User Management:**
- User CRUD operations (admin only)
- Account lifecycle (lock, unlock, soft delete, restore)
- External account mapping (Jira, GitHub)
- Security audit logging

✅ **Service Integration:**
- gRPC server for user data provisioning to other services
- No dependencies on other internal services

### What It Does NOT Do

❌ Group management → Delegated to User-Group Service  
❌ Assignment management → Separate service  
❌ Email notifications → Separate service

---

## Architecture

### Service Communication

```
Client (REST/JSON)
      ↓
API Gateway (JWT Auth, port 8080)
      ↓
/api/identity/** → Identity Service (REST, port 8081)

Other Services (gRPC)
      ↓
Identity Service (gRPC, port 9091)
```

### Package Structure

```
identity-service/
├── src/main/java/com/example/identityservice/
│   ├── config/               # Security, Async, OpenAPI
│   ├── controller/           # AuthController, AdminController
│   ├── dto/                  # Request/Response DTOs
│   ├── entity/               # User, RefreshToken, AuditLog
│   ├── exception/            # Global error handling
│   ├── grpc/                 # gRPC service implementation
│   ├── repository/           # JPA repositories
│   ├── security/             # JWT filter, SecurityContext helper
│   └── service/              # Business logic
│
├── src/main/proto/           # gRPC contracts
└── src/main/resources/       # Config, migrations
```

---

## Data Model

### Core Entities

**users**
- Primary key: `id` (BIGINT)
- Unique keys: `email`, `jira_account_id`, `github_username`
- Soft delete: `deleted_at`, `deleted_by`
- Roles: `ADMIN`, `LECTURER`, `STUDENT`
- Status: `ACTIVE`, `LOCKED`

**refresh_tokens**
- Foreign key: `user_id` → users(id)
- Token: UUID (7-day expiration)
- Supports multi-device (one user → many tokens)

**audit_logs**
- Immutable log of all security events
- Fields: action, actor, timestamp, IP, user agent, old/new values

See [Database Design](DATABASE.md) for full schema.

---

## API Overview

### REST Endpoints

**Public (unauthenticated):**
- `POST /api/auth/register` - Student self-registration
- `POST /api/auth/login` - Get JWT access + refresh token
- `POST /api/auth/refresh` - Renew access token

**Protected (JWT required):**
- `POST /api/auth/logout` - Revoke refresh token
- `GET /api/profile` - Get current user info

**Admin only:**
- `POST /api/admin/users` - Create user (any role)
- `GET /api/admin/users` - List all users
- `PUT /api/admin/users/{id}` - Update user
- `DELETE /api/admin/users/{id}` - Soft delete user
- `POST /api/admin/users/{id}/restore` - Restore deleted user
- `GET /api/admin/audit-logs` - View security logs

See [API Contract](API_CONTRACT.md) for full specifications.

---

## gRPC Integration

Identity Service **exposes** gRPC APIs for inter-service communication.

**Purpose:** Provide user data provisioning to other microservices without direct database access.

**Port:** `9091` (gRPC Server)

**Exposed RPCs:**
- `GetUser` - Retrieve user details by ID
- `GetUserRole` - Get user's system role
- `VerifyUserExists` - Check if user exists and is ACTIVE
- `GetUsers` - Batch fetch users (optimized for performance)
- `UpdateUser` - Update user profile (proxy pattern)
- `ListUsers` - Paginated user listing with filters

**Consumed by:**
- User-Group Service (user validation, member details, profile updates)
- Project-Config Service (planned - user validation)

**📄 See:** **[GRPC_CONTRACT.md](GRPC_CONTRACT.md)** for complete gRPC API documentation, including request/response schemas, error handling, and usage examples.

---

## Security

### JWT Authentication

**Access Token (15 minutes):**
```json
{
  "sub": "1",
  "email": "admin@example.com",
  "roles": ["ADMIN"],
  "token_type": "ACCESS",
  "iat": 1706612400,
  "exp": 1706613300
}
```

**Refresh Token (7 days):**
- Stored in database with expiration
- Revoked on logout
- One user can have multiple refresh tokens (multi-device)

### Authorization

**Role Hierarchy:**
- `ADMIN` - Full system access
- `LECTURER` - Manage groups and project configs
- `STUDENT` - Limited access to own data

**Access Control:**
- REST API: `@PreAuthorize("hasRole('ADMIN')")` annotations
- gRPC API: No authorization (internal trusted traffic)

See [Security Design](SECURITY.md) for comprehensive details.

---

## Important Notes

### Soft Delete Behavior

- Standard JPA queries automatically exclude soft-deleted users (via `@SQLRestriction`)
- gRPC service uses `findByIdIgnoreDeleted()` to bypass filter
- Deleted users can be restored by admin

### Role Format **CRITICAL**

| Layer | Format | Example |
|-------|--------|---------|
| **Database** | Plain string | `ADMIN`, `STUDENT` |
| **JWT Token** | Array of strings | `["ADMIN"]`, `["STUDENT"]` |
| **Spring Security** | Granted Authority | `ROLE_ADMIN` (auto-prefixed) |
| **@PreAuthorize** | hasRole() param | `hasRole('ADMIN')` |

⚠️ **DO NOT** store `ROLE_` prefix in database or JWT claims!  
⚠️ **DO NOT** use `hasRole('ROLE_ADMIN')` (double prefix)!

**Correct usage:**
```java
// Database
users.role = 'ADMIN'  // ✅ Plain string

// JWT claim
"roles": ["ADMIN"]  // ✅ No prefix

// Controller
@PreAuthorize("hasRole('ADMIN')")  // ✅ Spring auto-adds ROLE_ prefix
```

See **Appendix A: Role Format Clarification** below for detailed explanation.

---

## Getting Started

### Prerequisites

- Java 21+
- PostgreSQL 15+
- Maven 3.9+

### Build & Run

```bash
# Build
cd identity-service
mvn clean package -DskipTests

# Run locally
mvn spring-boot:run

# Or via Docker
docker-compose up identity-service
```

See [Implementation Guide](IMPLEMENTATION.md) for detailed setup.

---

## Testing

```bash
# Unit tests
mvn test

# Integration tests (requires PostgreSQL)
mvn verify

# API testing with curl
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"student@test.com","password":"Test1234!","fullName":"Test Student"}'
```

See [Test Cases](TEST_CASES.md) for full test specifications.

---

## Monitoring & Observability

### Health Check

```bash
curl http://localhost:8081/actuator/health
```

### Metrics

```bash
curl http://localhost:8081/actuator/metrics
curl http://localhost:8081/actuator/metrics/jvm.memory.used
```

### Audit Logs

All security events are logged in `audit_logs` table:
- Login attempts (success/failure)
- User CRUD operations
- Password changes
- Account lock/unlock
- Soft delete/restore

---

## Appendix A: Role Format Clarification

### Format by Layer

The format of roles varies depending on where they are used:

| Layer | Format | Example | Where Used |
|-------|--------|---------|------------|
| **Database** | Plain string | `ADMIN`, `STUDENT`, `LECTURER` | PostgreSQL `users.role` column |
| **Java Entity** | Enum | `Role.ADMIN`, `Role.STUDENT` | `User.java` entity class |
| **JWT Token** | Array of strings | `["ADMIN"]`, `["STUDENT"]` | JWT payload claims |
| **Spring Security** | Granted Authority | `ROLE_ADMIN`, `ROLE_STUDENT` | Internal Spring Security context |
| **@PreAuthorize** | hasRole() param | `hasRole('ADMIN')` | Controller annotations |

### Common Mistakes

❌ **WRONG: Storing ROLE_ in database**
```sql
INSERT INTO users (email, role) 
VALUES ('admin@example.com', 'ROLE_ADMIN');  -- ❌ WRONG!
```

✅ **CORRECT: Plain enum value**
```sql
INSERT INTO users (email, role) 
VALUES ('admin@example.com', 'ADMIN');  -- ✅ CORRECT
```

---

❌ **WRONG: ROLE_ prefix in JWT**
```java
.claim("roles", List.of("ROLE_" + user.getRole().name()))  // ❌ WRONG!
// Results in: {"roles": ["ROLE_ADMIN"]}
```

✅ **CORRECT: Plain enum value in JWT**
```java
.claim("roles", List.of(user.getRole().name()))  // ✅ CORRECT
// Results in: {"roles": ["ADMIN"]}
```

---

❌ **WRONG: Double prefix in @PreAuthorize**
```java
@PreAuthorize("hasRole('ROLE_ADMIN')")  // ❌ WRONG! Double prefix
```

✅ **CORRECT: Single prefix (auto-added by Spring)**
```java
@PreAuthorize("hasRole('ADMIN')")  // ✅ CORRECT
```

### Flow Diagram

```
┌─────────────┐
│  Database   │  role = "ADMIN"
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ Java Entity │  Role.ADMIN
└──────┬──────┘
       │
       ├──────────────────────┐
       │                      │
       ▼                      ▼
┌─────────────┐      ┌──────────────────┐
│  JWT Token  │      │ Spring Security  │
│ ["ADMIN"]   │      │ "ROLE_ADMIN"     │
└─────────────┘      └────────┬─────────┘
                              │
                              ▼
                     ┌──────────────────┐
                     │  @PreAuthorize   │
                     │ hasRole('ADMIN') │
                     └──────────────────┘
```

### Verification Checklist

- [ ] Database `users.role` stores plain enum (`ADMIN`, `STUDENT`, `LECTURER`)
- [ ] JWT token `roles` claim contains array without prefix (`["ADMIN"]`)
- [ ] `JwtAuthenticationFilter` adds `ROLE_` prefix when creating `SimpleGrantedAuthority`
- [ ] `@PreAuthorize` uses `hasRole('ADMIN')` NOT `hasRole('ROLE_ADMIN')`

---

## References

- [Authentication-Authorization-Design.md (deprecated)](Authentication-Authorization-Design.md) - Merged into SECURITY.md
- [Security-Review.md (deprecated)](Security-Review.md) - Merged into SECURITY.md
- [ADR-001](../ADR-001-project-config-rest-grpc-hybrid.md) - Architecture decisions (REST+gRPC)
- [SRS_REFACTORED.md](../SRS_REFACTORED.md) - System requirements

---

**Questions?** See [IMPLEMENTATION.md](IMPLEMENTATION.md) or contact Backend Team Lead
