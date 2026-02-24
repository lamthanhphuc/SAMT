# SAMT System Overview

**Document Version:** 1.0  
**Generated:** 2026-02-02  
**Purpose:** High-level system architecture and service interaction summary

---

## System Architecture

SAMT (Student Assignment Management Tool) is a microservices-based system for managing student groups, assignments, and academic workflows.

### Service Topology

```
┌────────────────────────────────────────────────────────────────┐
│                        API Gateway                              │
│                 (Authentication & Routing)                      │
└─────────┬──────────────────┬──────────────────┬───────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌────────────────────┐ ┌────────────────────┐ ┌────────────────────┐
│ Identity Service   │ │ User-Group Service │ │ Project Config     │
│                    │◄┤                    │◄┤ Service            │
│ - Authentication   │ │ - Group Management │ │                    │
│ - User CRUD        │ │ - Member Mgmt      │ │ - Jira/GitHub      │
│ - JWT Generation   │ │ - User Profile     │ │   Config Mgmt      │
│ - Audit Logging    │ │   Proxy (gRPC)     │ │ - Token Encryption │
└──────────┬─────────┘ └──────────┬─────────┘ │ - Verification     │
           │ gRPC                 │ gRPC       │ - Group Validation │
           │ (9091)               │ (9095)     │   (gRPC)           │
           ▼                      ▼            └──────────┬─────────┘
┌────────────────────┐ ┌────────────────────┐            │ gRPC Client
│  PostgreSQL        │ │  PostgreSQL        │            │ Only
│  - users           │ │  - groups          │            ▼
│  - refresh_tokens  │ │  - user_groups     │ ┌────────────────────┐
│  - audit_logs      │ └────────────────────┘ │  PostgreSQL        │
└────────────────────┘                        │  - project_configs │
                                              └────────────────────┘
```

---

## Core Services

### 1. Identity Service
**Port:** 8081  
**Responsibilities:**
- User registration & authentication (JWT + Refresh Token)
- Password management (BCrypt hashing)
- Role-based access control (ADMIN, LECTURER, STUDENT)
- User lifecycle management (lock, unlock, soft delete, restore)
- Audit logging for security events
- gRPC server for user data provisioning

**Technology Stack:**
- Spring Boot 3.x
- Spring Security 6.x
- PostgreSQL (JPA/Hibernate)
- gRPC (server)
- JWT (HS256)

---

### 2. User-Group Service
**Port:** 8082  
**Responsibilities:**
- Student group management (CRUD)
- Group membership management (add, remove, promote, demote)
- Business rules enforcement:
  - One leader per group
  - One group per student per semester
  - Lecturer validation via Identity Service
- User profile operations (proxy to Identity Service)

**Technology Stack:**
- Spring Boot 3.x
- Spring Security 6.x (JWT validation only)
- PostgreSQL (JPA/Hibernate)
- gRPC (client to Identity Service)

**Does NOT:**
- Manage user accounts (delegates to Identity Service)
- Issue or validate JWT (only reads claims)
- Store user credentials

---

### 3. Project Config Service
**Port:** 8083 (REST)  
**Responsibilities:**
- Jira and GitHub integration configuration management
- Token encryption (AES-256-GCM) for API credentials
- Connection verification to external APIs
- Token masking for secure display
- gRPC client to User-Group Service (port 9095)
- Soft delete with 90-day retention
- Group validation via User-Group Service (gRPC)

**Technology Stack:**
- Spring Boot 3.x
- REST API (routed via API Gateway for client access)
- gRPC (client to User-Group Service, server for internal service-to-service communication)
- PostgreSQL (JPA/Hibernate)
- AES-256-GCM encryption

**Communication Patterns:**
- **Client → Project Config:** REST via API Gateway (JWT authentication)
- **Project Config → User-Group:** gRPC (group validation, leadership check)
- **Sync Service → Project Config:** gRPC service-to-service (decrypted tokens)

**Use Cases:**
- UC30: Create project configuration (LEADER only)
- UC31: Get configuration (masked tokens for STUDENT, full for ADMIN/LECTURER)
- UC32: Update configuration
- UC33: Soft delete configuration
- UC34: Verify Jira/GitHub connectivity
- UC35: Restore deleted configuration (ADMIN only)

**Does NOT:**
- Store Jira issues or GitHub commits (delegated to Sync Service)
- Manage user groups (delegates to User-Group Service)
- Perform automatic synchronization (triggered by Sync Service)

---

## Inter-Service Communication

### gRPC Contracts

#### 1. Identity Service → User-Group Service

**Proto Definition:** `user_service.proto`

| RPC Method          | Purpose                          | Used By               |
|---------------------|----------------------------------|-----------------------|
| `GetUser`           | Fetch user profile               | UC21, UC23, UC24      |
| `GetUserRole`       | Validate lecturer role           | Group creation        |
| `VerifyUserExists`  | Check user existence & status    | Member operations     |
| `GetUsers`          | Batch fetch users (N+1 避免)      | Group detail, list    |
| `UpdateUser`        | Proxy profile update (UC22)      | User-Group Service    |
| `ListUsers`         | List users with filters          | Admin user listing    |

**📄 Full Contract:** [Identity Service - GRPC_CONTRACT.md](Identity_Service/GRPC_CONTRACT.md)

#### 2. User-Group Service → Project Config Service

**Proto Definition:** `usergroup_service.proto`

| RPC Method          | Purpose                          | Used By               |
|---------------------|----------------------------------|-----------------------|
| `VerifyGroupExists` | Check group exists & not deleted | UC30-UC35             |
| `CheckGroupLeader`  | Verify user is group leader      | Config create/update  |
| `CheckGroupMember`  | Verify user is group member      | Config read access    |

**📄 Full Contract:** [User-Group Service - GRPC_CONTRACT.md](UserGroup_Service/GRPC_CONTRACT.md)

#### 3. Project Config Service → Internal Services

**Proto Definition:** `project_config_service.proto`

| RPC Method                    | Purpose                     | Used By       |
|-------------------------------|-----------------------------|---------------|
| `CreateProjectConfig`         | Create config (UC30)        | Client        |
| `GetProjectConfig`            | Get config (UC31)           | Client        |
| `UpdateProjectConfig`         | Update config (UC32)        | Client        |
| `DeleteProjectConfig`         | Soft delete (UC33)          | Client        |
| `VerifyConnection`            | Test Jira/GitHub (UC34)     | Client        |
| `RestoreProjectConfig`        | Restore config (UC35)       | Client        |
| `InternalGetDecryptedConfig`  | Get full tokens (internal)  | Sync Service  |

**📄 Full Contract:** [Project Config Service - GRPC_CONTRACT.md](ProjectConfig/GRPC_CONTRACT.md)

**Error Handling:**
- gRPC errors (NOT_FOUND, INVALID_ARGUMENT, etc.) are mapped to HTTP status codes
- Timeouts: 3 seconds per call (configurable)
- Circuit breaker: NOT YET IMPLEMENTED (known tech debt)

---

## Authentication & Authorization Flow

### 1. Login Flow (Identity Service)

```
Client → POST /api/auth/login {email, password}
         ↓
      Validate credentials (BCrypt)
         ↓
      Check account status (ACTIVE/LOCKED)
         ↓
      Generate JWT (15min) + Refresh Token (7 days)
         ↓
      Return tokens
```

### 2. Authenticated Request Flow (User-Group Service)

```
Client → GET /users/{userId} 
         Authorization: Bearer <JWT>
         ↓
      JwtAuthenticationFilter (validate JWT signature)
         ↓
      Extract userId & roles from JWT claims
         ↓
      Set SecurityContext (ROLE_ADMIN / ROLE_LECTURER / ROLE_STUDENT)
         ↓
      @PreAuthorize("isAuthenticated()") - Spring Security check
         ↓
      Business Authorization (role-based rules)
         ↓
      gRPC call to Identity Service (if needed)
         ↓
      Return response
```

**JWT Structure:**
```json
{
  "sub": "123",                   // User ID
  "email": "user@example.com",
  "roles": ["STUDENT"],
  "token_type": "ACCESS",
  "iat": 1738483200,
  "exp": 1738484100
}
```

---

## Data Ownership

| Data Type          | Owner Service       | Access Method                |
|--------------------|---------------------|------------------------------|
| User Credentials   | Identity Service    | Local DB only                |
| User Profile       | Identity Service    | gRPC `GetUser`               |
| Groups             | User-Group Service  | Local DB only                |
| Group Membership   | User-Group Service  | Local DB only                |
| Audit Logs         | Identity Service    | Local DB only (no export)    |

**Critical Design Rule:**
- User-Group Service NEVER stores user data locally (except `userId` foreign keys)
- All user profile data fetched via gRPC on-demand
- No data duplication between services

---

## Soft Delete Strategy

Both services implement soft delete (retention policy: 90 days per SRS).

### Implementation Details

**Identity Service:**
- `users.deleted_at` timestamp
- `users.deleted_by` actor tracking
- `@SQLRestriction("deleted_at IS NULL")` on `User` entity
- Explicit `findByIdIgnoreDeleted()` for gRPC (bypass filter)

**User-Group Service:**
- `groups.deleted_at` timestamp
- `user_groups.deleted_at` timestamp
- `@SQLRestriction("deleted_at IS NULL")` on both entities
- Cascade soft delete: deleting group → soft delete all memberships

**Restore Behavior:**
- Identity Service: `POST /api/admin/users/{id}/restore`
- User-Group Service: NOT IMPLEMENTED (groups cannot be restored yet)

---

## Business Rules Summary

### Identity Service

1. **Registration:**
   - Email uniqueness enforced by DB constraint
   - Default role: STUDENT (ADMIN/LECTURER require admin creation)
   - Password strength: min 8 chars (enforced by Bean Validation)

2. **Login Security:**
   - Password validated BEFORE status check (anti-enumeration)
   - Locked accounts: 403 Forbidden (after password verified)
   - Audit log on all login attempts

3. **Admin Operations:**
   - Self-delete: FORBIDDEN (400 Bad Request)
   - Self-lock: FORBIDDEN (400 Bad Request)
   - Soft delete → revoke all refresh tokens

### User-Group Service

1. **Group Management:**
   - Group name unique per semester (DB constraint)
   - Lecturer validation via gRPC (must exist + LECTURER role + ACTIVE)

2. **Membership Rules:**
   - One LEADER per group (enforced)
   - One group per student per semester (enforced)
   - Leader deletion → promote first MEMBER (if exists)

3. **Authorization Rules:**
   - ADMIN: can manage all groups
   - LECTURER: can manage own groups only
   - STUDENT: can view own groups only (no management)

---

## Known Tech Debt & Limitations

### 1. Missing gRPC Features
- **Circuit Breaker:** No resilience pattern → service outage cascades
- **Retry Logic:** Failed gRPC calls fail immediately
- **Caching:** User data fetched on every request (N+1 issue at scale)

### 2. Security Gaps
- **JWT Revocation:** No blacklist → access tokens valid until expiration (15 min)
- **Rate Limiting:** No protection against brute-force attacks
- **CSRF Protection:** Disabled (assumes stateless JWT only)

### 3. Missing Features
- **Group Restore:** Cannot restore soft-deleted groups
- **Audit in User-Group Service:** No audit logging (only Identity Service)
- **Batch Operations:** No bulk member add/remove
- **Pagination:** gRPC `GetUsers` does not support pagination (all results returned)

### 4. Database
- **No Replication:** Single point of failure
- **No Migration Tool:** Flyway/Liquibase not configured
- **Index Optimization:** Missing composite indexes for common queries

---

## Production Readiness Checklist

| Area                  | Status        | Notes                                    |
|-----------------------|---------------|------------------------------------------|
| Authentication        | ✅ Production | JWT + Refresh Token working             |
| Authorization         | ✅ Production | Role-based rules enforced               |
| Soft Delete           | ✅ Production | Implemented in both services            |
| Audit Logging         | ⚠️ Partial    | Identity Service only                   |
| Error Handling        | ✅ Production | Global exception handlers               |
| gRPC Communication    | ⚠️ Partial    | No retry/circuit breaker                |
| Database Transactions | ✅ Production | @Transactional boundaries correct       |
| API Documentation     | ✅ Production | Swagger/OpenAPI available               |
| Monitoring            | ❌ Missing    | No metrics/health checks                |
| Secrets Management    | ⚠️ Partial    | JWT_SECRET in env (no vault)            |

---

## Next Steps for New Developers

1. **Read in this order:**
   - `01_Identity_Service.md` - Authentication & user management
   - `02_User_Group_Service.md` - Group & membership management
   - `03_System_Interaction.md` - gRPC details & flow diagrams

2. **Setup Local Environment:**
   - PostgreSQL instances (ports: 5432 for identity, 5433 for user-group)
   - Environment variables (JWT_SECRET, CORS_ALLOWED_ORIGINS)
   - Run `mvn clean install` in root directory

3. **Key Files to Study:**
   - `JwtAuthenticationFilter.java` - How JWT validation works
   - `UserGrpcServiceImpl.java` - gRPC server implementation
   - `IdentityServiceClient.java` - gRPC client usage
   - `User.java`, `Group.java` - Entity soft delete behavior

4. **Testing:**
   - Import Postman collection (if available)
   - Test JWT flow: register → login → call protected endpoint
   - Test gRPC: create group → verify lecturer validation

---

**End of System Overview**
