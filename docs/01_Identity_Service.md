# Identity Service - Technical Documentation

**Service Name:** Identity Service  
**Version:** 1.0  
**Port:** 8081  
**Database:** PostgreSQL (identity_db)  
**Last Updated:** 2026-02-02

---

## Table of Contents

1. [Service Overview](#service-overview)
2. [Architecture & Package Structure](#architecture--package-structure)
3. [Data Model](#data-model)
4. [REST API Contracts](#rest-api-contracts)
5. [gRPC API Contracts](#grpc-api-contracts)
6. [Security Design](#security-design)
7. [Transaction & Consistency](#transaction--consistency)
8. [Error Handling](#error-handling)
9. [Important Implementation Notes](#important-implementation-notes)

---

## Service Overview

### Primary Responsibilities

Identity Service is the **SINGLE SOURCE OF TRUTH** for user authentication and authorization in SAMT system.

**What it DOES:**
- ✅ User registration (self-service for STUDENT role only)
- ✅ Login/logout with JWT + Refresh Token
- ✅ Password hashing and validation (BCrypt, strength 10)
- ✅ JWT generation and validation
- ✅ User CRUD operations (admin only)
- ✅ Account lifecycle management (lock, unlock, soft delete, restore)
- ✅ External account mapping (Jira, GitHub integration)
- ✅ Security audit logging (login attempts, admin actions)
- ✅ gRPC server for user data provisioning to other services

**What it DOES NOT DO:**
- ❌ Group management (delegated to User-Group Service)
- ❌ Assignment management (separate service)
- ❌ Email notifications (separate service)
- ❌ File storage (separate service)

### Dependencies

**Internal Services (via gRPC):**
- NONE (Identity Service does not call other services)

**External Services:**
- PostgreSQL database (identity_db)
- Future: Jira API, GitHub API (planned for external account sync)

---

## Architecture & Package Structure

### Maven Module Structure

```
identity-service/
├── src/main/java/com/example/identityservice/
│   ├── config/               # Configuration classes
│   │   ├── SecurityConfig.java           # Spring Security + JWT filter chain
│   │   ├── AsyncConfig.java              # Async task executor
│   │   └── OpenApiConfig.java            # Swagger/OpenAPI config
│   │
│   ├── controller/           # REST API endpoints
│   │   ├── AuthController.java           # /api/auth/** (register, login, refresh, logout)
│   │   └── AdminController.java          # /api/admin/** (user management, audit logs)
│   │
│   ├── dto/                  # Data Transfer Objects
│   │   ├── request/          # Request DTOs (validated by Bean Validation)
│   │   └── response/         # Response DTOs (immutable records)
│   │
│   ├── entity/               # JPA Entities (database mapping)
│   │   ├── User.java                     # users table (with soft delete)
│   │   ├── RefreshToken.java            # refresh_tokens table
│   │   ├── AuditLog.java                # audit_logs table (immutable)
│   │   └── [enums]                      # Role, Status, AuditAction
│   │
│   ├── exception/            # Custom exceptions + global handler
│   │   ├── GlobalExceptionHandler.java   # REST error responses
│   │   └── [specific exceptions]         # UserNotFoundException, etc.
│   │
│   ├── grpc/                 # gRPC service implementation
│   │   └── UserGrpcServiceImpl.java      # Implements user_service.proto
│   │
│   ├── repository/           # Spring Data JPA repositories
│   │   ├── UserRepository.java
│   │   ├── RefreshTokenRepository.java
│   │   └── AuditLogRepository.java
│   │
│   ├── security/             # Security infrastructure
│   │   ├── JwtAuthenticationFilter.java  # JWT validation filter
│   │   ├── SecurityContextHelper.java    # Extract current user from context
│   │   └── [other helpers]
│   │
│   └── service/              # Business logic
│       ├── AuthService.java              # Registration, login
│       ├── RefreshTokenService.java      # Token lifecycle
│       ├── JwtService.java               # JWT generation/parsing
│       ├── UserAdminService.java         # Admin user operations
│       └── AuditService.java             # Audit logging
│
├── src/main/proto/
│   └── user_service.proto    # gRPC contract definition
│
└── src/main/resources/
    ├── application.yml       # Configuration (DB, JWT, gRPC)
    └── [other configs]
```

### Request Flow Diagram

#### REST Request Flow

```
HTTP Request (e.g., POST /api/auth/login)
    ↓
┌───────────────────────────────────────┐
│ JwtAuthenticationFilter               │
│ - Extract & validate JWT (if present) │
│ - Set SecurityContext                 │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ Spring Security Filter Chain          │
│ - CORS                                │
│ - @PreAuthorize checks                │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ AuthController / AdminController      │
│ - Validate DTO (@Valid)               │
│ - Extract request parameters          │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ AuthService / UserAdminService        │
│ - Business logic                      │
│ - Transaction management              │
│ - Audit logging                       │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ UserRepository / RefreshTokenRepo     │
│ - JPA queries                         │
│ - Soft delete filtering (@SQLRestriction)
└───────────────────────────────────────┘
    ↓
PostgreSQL Database
```

#### gRPC Request Flow

```
gRPC Request (e.g., GetUser from User-Group Service)
    ↓
┌───────────────────────────────────────┐
│ UserGrpcServiceImpl                   │
│ - Extract request parameters          │
│ - No authentication (internal traffic)│
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ UserRepository                        │
│ - findByIdIgnoreDeleted() bypass filter
└───────────────────────────────────────┘
    ↓
PostgreSQL Database
    ↓
Build gRPC response (GetUserResponse proto)
```

---

## Data Model

### Entity Relationship Diagram

```
┌─────────────────────────────────┐
│          users                  │
├─────────────────────────────────┤
│ PK id (BIGINT, AUTO_INCREMENT)  │
│ UK email (VARCHAR 255)          │
│    password_hash (VARCHAR 255)  │
│    full_name (VARCHAR 100)      │
│    role (ENUM: ADMIN, LECTURER, STUDENT)
│    status (ENUM: ACTIVE, LOCKED)│
│ UK jira_account_id (VARCHAR 100)│
│ UK github_username (VARCHAR 100)│
│    created_at (TIMESTAMP)       │
│    updated_at (TIMESTAMP)       │
│    deleted_at (TIMESTAMP)       │ ← Soft delete
│    deleted_by (BIGINT)          │
└─────────────────────────────────┘
         ↑ 1
         │
         │ N
┌─────────────────────────────────┐
│      refresh_tokens             │
├─────────────────────────────────┤
│ PK id (BIGINT, AUTO_INCREMENT)  │
│ FK user_id → users(id)          │
│ UK token (VARCHAR 255, UUID)    │
│    expires_at (TIMESTAMP)       │
│    revoked (BOOLEAN)            │
│    created_at (TIMESTAMP)       │
└─────────────────────────────────┘

┌─────────────────────────────────┐
│        audit_logs               │
├─────────────────────────────────┤
│ PK id (BIGINT, AUTO_INCREMENT)  │
│    entity_type (VARCHAR 100)    │
│    entity_id (BIGINT)           │
│    action (ENUM: LOGIN, CREATE_USER, etc.)
│    actor_id (BIGINT)            │
│    actor_email (VARCHAR 255)    │ ← Denormalized for performance
│    timestamp (TIMESTAMP)        │
│    ip_address (VARCHAR 45)      │
│    user_agent (VARCHAR 500)     │
│    old_value (TEXT, JSON)       │
│    new_value (TEXT, JSON)       │
│    outcome (ENUM: SUCCESS, FAILURE, DENIED)
└─────────────────────────────────┘
```

### Entity: `User`

**File:** [`User.java`](../identity-service/src/main/java/com/example/identityservice/entity/User.java)

**Key Fields:**

| Field            | Type          | Constraints                | Description                          |
|------------------|---------------|----------------------------|--------------------------------------|
| `id`             | Long          | PK, AUTO_INCREMENT         | User ID (exposed in JWT sub claim)   |
| `email`          | String        | UNIQUE, NOT NULL           | Login identifier                     |
| `passwordHash`   | String        | NOT NULL                   | BCrypt hash (strength 10)            |
| `fullName`       | String        | NOT NULL                   | Display name                         |
| `role`           | Enum          | NOT NULL                   | ADMIN, LECTURER, STUDENT             |
| `status`         | Enum          | NOT NULL                   | ACTIVE, LOCKED                       |
| `jiraAccountId`  | String        | UNIQUE, NULLABLE           | External system mapping              |
| `githubUsername` | String        | UNIQUE, NULLABLE           | External system mapping              |
| `deletedAt`      | LocalDateTime | NULLABLE                   | Soft delete timestamp                |
| `deletedBy`      | Long          | NULLABLE                   | Who deleted this user                |

**Soft Delete Behavior:**

```java
@Entity
@SQLRestriction("deleted_at IS NULL")  // Automatic filtering
public class User {
    // ...
    
    public void softDelete(Long deletedByUserId) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = deletedByUserId;
    }
    
    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
    }
}
```

**CRITICAL:** Standard JPA queries (e.g., `findById`, `findAll`) automatically exclude soft-deleted users due to `@SQLRestriction`. Use `findByIdIgnoreDeleted()` in gRPC service to bypass this filter.

**Indexes:**
- `idx_users_email` (unique)
- `idx_users_status` (for admin filtering)
- `idx_users_jira_account` (unique)
- `idx_users_github_username` (unique)
- `idx_users_deleted_at` (for soft delete queries)

### Entity: `RefreshToken`

**File:** [`RefreshToken.java`](../identity-service/src/main/java/com/example/identityservice/entity/RefreshToken.java)

**Key Fields:**

| Field       | Type          | Constraints     | Description                       |
|-------------|---------------|-----------------|-----------------------------------|
| `id`        | Long          | PK              | Token ID                          |
| `user`      | User (FK)     | NOT NULL        | Owner of the token                |
| `token`     | String        | UNIQUE, NOT NULL| UUID (256 bits random)            |
| `expiresAt` | LocalDateTime | NOT NULL        | 7 days from creation              |
| `revoked`   | Boolean       | NOT NULL        | Manually revoked flag             |

**Business Rules:**
- One user can have multiple active refresh tokens (multi-device support)
- Revoked tokens are kept in database for audit purposes (never deleted)
- Expired tokens are cleaned up by scheduled task (future feature)

### Entity: `AuditLog`

**File:** [`AuditLog.java`](../identity-service/src/main/java/com/example/identityservice/entity/AuditLog.java)

**Key Fields:**

| Field        | Type          | Description                                |
|--------------|---------------|--------------------------------------------|
| `entityType` | String        | e.g., "User", "RefreshToken"               |
| `entityId`   | Long          | ID of affected entity                      |
| `action`     | AuditAction   | LOGIN, CREATE_USER, SOFT_DELETE, etc.      |
| `actorId`    | Long          | Who performed the action (NULL if system)  |
| `actorEmail` | String        | Denormalized for query performance         |
| `timestamp`  | LocalDateTime | When the action occurred                   |
| `ipAddress`  | String        | Client IP (for login attempts)             |
| `oldValue`   | String (JSON) | State before change (nullable)             |
| `newValue`   | String (JSON) | State after change (nullable)              |
| `outcome`    | Enum          | SUCCESS, FAILURE, DENIED                   |

**Design Decision:** Audit logs are **IMMUTABLE**. No updates or deletes allowed. Retention policy handled by separate archival process (not yet implemented).

---

## REST API Contracts

### Authentication Endpoints

#### 1. Register User

**Endpoint:** `POST /api/auth/register`  
**Authorization:** Public (no authentication required)  
**Use Case:** UC-REGISTER

**Request Body:**

```json
{
  "email": "student@example.com",
  "password": "SecurePass123",
  "confirmPassword": "SecurePass123",
  "fullName": "John Doe",
  "role": "STUDENT"
}
```

**Validation Rules:**
- `email`: Valid email format, max 255 chars
- `password`: Min 8 characters
- `confirmPassword`: Must match `password`
- `role`: Only "STUDENT" allowed (ADMIN/LECTURER require admin creation)

**Success Response (201 Created):**

```json
{
  "user": {
    "id": 123,
    "email": "student@example.com",
    "fullName": "John Doe",
    "role": "STUDENT",
    "status": "ACTIVE",
    "createdAt": "2026-02-02T10:30:00Z"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Error Responses:**

| Code | Error Code          | Condition                    |
|------|---------------------|------------------------------|
| 400  | PASSWORD_MISMATCH   | Passwords don't match        |
| 409  | EMAIL_EXISTS        | Email already registered     |

---

#### 2. Login

**Endpoint:** `POST /api/auth/login`  
**Authorization:** Public  
**Use Case:** UC-LOGIN

**Request Body:**

```json
{
  "email": "student@example.com",
  "password": "SecurePass123"
}
```

**Success Response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "expiresIn": 900
}
```

**Error Responses:**

| Code | Error Code          | Condition                               |
|------|---------------------|-----------------------------------------|
| 401  | INVALID_CREDENTIALS | Email not found or password incorrect   |
| 403  | ACCOUNT_LOCKED      | Account status is LOCKED                |

**SECURITY NOTE:** Password is validated BEFORE status check to prevent account enumeration attacks.

---

#### 3. Refresh Token

**Endpoint:** `POST /api/auth/refresh`  
**Authorization:** Public  
**Use Case:** UC-REFRESH-TOKEN

**Request Body:**

```json
{
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Success Response (200 OK):**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "new-uuid-here",
  "expiresIn": 900
}
```

**Behavior:** Issues NEW refresh token and revokes the old one (rotation strategy).

**Error Responses:**

| Code | Error Code      | Condition                      |
|------|-----------------|--------------------------------|
| 401  | TOKEN_INVALID   | Token not found or revoked     |
| 401  | TOKEN_EXPIRED   | Token expired                  |

---

#### 4. Logout

**Endpoint:** `POST /api/auth/logout`  
**Authorization:** Bearer token required  
**Use Case:** UC-LOGOUT

**Request Body:**

```json
{
  "refreshToken": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

**Success Response (204 No Content):** Empty body

**Behavior:**
- Revokes the provided refresh token
- Idempotent: returns 204 even if token not found/already revoked
- Access token remains valid until expiration (JWT cannot be revoked)

---

### Admin Endpoints

**Authorization:** All endpoints require ADMIN role

> **Note:** ROLE_ prefix is Spring Security internal. API documentation uses plain role names.

#### 5. Create User (Admin)

**Endpoint:** `POST /api/admin/users`  
**Use Case:** UC-ADMIN-CREATE-USER

**Request Body:**

```json
{
  "email": "lecturer@example.com",
  "password": "TempPass123",
  "fullName": "Jane Smith",
  "role": "LECTURER"
}
```

**Difference from `/register`:** Can create LECTURER and ADMIN accounts.

**Success Response (201 Created):**

```json
{
  "message": "User created successfully",
  "user": { ... },
  "temporaryPassword": "TempPass123"
}
```

---

#### 6. Soft Delete User

**Endpoint:** `DELETE /api/admin/users/{userId}`  
**Use Case:** UC-SOFT-DELETE

**Success Response (200 OK):**

```json
{
  "message": "User deleted successfully",
  "userId": 123
}
```

**Side Effects:**
- Sets `deleted_at` timestamp
- Revokes ALL refresh tokens for this user
- Creates audit log entry

**Error Responses:**

| Code | Error Code        | Condition                    |
|------|-------------------|------------------------------|
| 404  | USER_NOT_FOUND    | User ID not found            |
| 400  | INVALID_STATE     | User already deleted         |
| 400  | SELF_ACTION_DENIED| Admin cannot delete own account |

---

#### 7. Restore User

**Endpoint:** `POST /api/admin/users/{userId}/restore`  
**Use Case:** UC-RESTORE

**Success Response (200 OK):**

```json
{
  "message": "User restored successfully",
  "userId": 123
}
```

**Behavior:** Clears `deleted_at` and `deleted_by` fields.

---

#### 8. Lock Account

**Endpoint:** `POST /api/admin/users/{userId}/lock?reason=Suspicious+activity`  
**Use Case:** UC-LOCK-ACCOUNT

**Success Response (200 OK):**

```json
{
  "message": "User locked successfully",
  "userId": 123
}
```

**Side Effects:**
- Sets `status = LOCKED`
- Revokes all refresh tokens
- Idempotent (locking already locked account returns 200)

**Error:** `SELF_ACTION_DENIED` if admin tries to lock own account.

---

#### 9. Unlock Account

**Endpoint:** `POST /api/admin/users/{userId}/unlock`  
**Use Case:** UC-UNLOCK-ACCOUNT

**Success Response (200 OK):**

```json
{
  "message": "User unlocked successfully",
  "userId": 123
}
```

**Behavior:** Sets `status = ACTIVE`.

---

#### 10. Map External Accounts

**Endpoint:** `PUT /api/admin/users/{userId}/external-accounts`  
**Use Case:** External integration (Jira, GitHub)

**Request Body:**

```json
{
  "jiraAccountId": "557058:abc123",
  "githubUsername": "johndoe"
}
```

**Success Response (200 OK):**

```json
{
  "message": "External accounts updated",
  "user": { ... }
}
```

**Constraints:** Both `jiraAccountId` and `githubUsername` must be unique across all users.

---

#### 11. List Users

**Endpoint:** `GET /api/admin/users?page=0&size=20&status=ACTIVE&role=STUDENT`  
**Authorization:** ADMIN only

**Query Parameters:**

| Param    | Type   | Description                  |
|----------|--------|------------------------------|
| `page`   | int    | Page number (0-indexed)      |
| `size`   | int    | Page size (default: 20)      |
| `status` | string | Filter by ACTIVE/LOCKED      |
| `role`   | string | Filter by ADMIN/LECTURER/STUDENT |

**Success Response (200 OK):**

```json
{
  "content": [
    {
      "id": 123,
      "email": "student@example.com",
      "fullName": "John Doe",
      "role": "STUDENT",
      "status": "ACTIVE",
      "createdAt": "2026-02-02T10:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

#### 12. View Audit Logs

**Endpoint:** `GET /api/admin/audit-logs?page=0&size=50&entityId=123&action=LOGIN&outcome=SUCCESS&startDate=2026-01-01T00:00:00&endDate=2026-02-02T23:59:59`  
**Authorization:** ADMIN only

**Query Parameters:**

| Param       | Type          | Description                     |
|-------------|---------------|---------------------------------|
| `page`      | int           | Page number                     |
| `size`      | int           | Page size (default: 50)         |
| `entityId`  | long          | Filter by entity ID             |
| `action`    | AuditAction   | Filter by action type           |
| `outcome`   | AuditOutcome  | SUCCESS/FAILURE/DENIED          |
| `startDate` | LocalDateTime | Timestamp range start           |
| `endDate`   | LocalDateTime | Timestamp range end             |

**Success Response (200 OK):**

```json
{
  "content": [
    {
      "id": 1,
      "entityType": "User",
      "entityId": 123,
      "action": "LOGIN",
      "actorEmail": "user@example.com",
      "timestamp": "2026-02-02T10:30:00Z",
      "outcome": "SUCCESS",
      "ipAddress": "192.168.1.100"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 200,
  "totalPages": 4
}
```

---

## gRPC API Contracts

**Proto File:** [`user_service.proto`](../identity-service/src/main/proto/user_service.proto)

### 1. GetUser

**Purpose:** Fetch user profile by ID  
**Used By:** User-Group Service (UC21, group details, member info)

**Request:**

```protobuf
message GetUserRequest {
  string user_id = 1; // Long as string
}
```

**Response:**

```protobuf
message GetUserResponse {
  string user_id = 1;
  string email = 2;
  string full_name = 3;
  UserStatus status = 4;  // ACTIVE | LOCKED
  UserRole role = 5;      // ADMIN | LECTURER | STUDENT
  bool deleted = 6;       // true if soft-deleted
}
```

**Error Codes:**
- `NOT_FOUND`: User ID does not exist
- `INVALID_ARGUMENT`: Invalid user ID format

**Implementation Note:** Uses `findByIdIgnoreDeleted()` to bypass soft delete filter (returns deleted users with `deleted=true` flag).

---

### 2. GetUserRole

**Purpose:** Fetch user's system role only  
**Used By:** Group creation (validate lecturer role)

**Request:**

```protobuf
message GetUserRoleRequest {
  string user_id = 1;
}
```

**Response:**

```protobuf
message GetUserRoleResponse {
  UserRole role = 1;  // ADMIN | LECTURER | STUDENT
}
```

**Error:** `NOT_FOUND` if user doesn't exist

---

### 3. VerifyUserExists

**Purpose:** Check if user exists and is active  
**Used By:** Member operations (add member to group)

**Request:**

```protobuf
message VerifyUserRequest {
  string user_id = 1;
}
```

**Response:**

```protobuf
message VerifyUserResponse {
  bool exists = 1;       // User found in database
  bool active = 2;       // status == ACTIVE
  string message = 3;    // Human-readable status
}
```

**Example Responses:**

```json
// User exists and active
{ "exists": true, "active": true, "message": "User exists and is active" }

// User exists but locked
{ "exists": true, "active": false, "message": "User exists but not active" }

// User not found
{ "exists": false, "active": false, "message": "User not found" }
```

---

### 4. GetUsers (Batch)

**Purpose:** Fetch multiple users in one call (avoid N+1 problem)  
**Used By:** Group detail page, group list page

**Request:**

```protobuf
message GetUsersRequest {
  repeated string user_ids = 1;  // List of Long as strings
}
```

**Response:**

```protobuf
message GetUsersResponse {
  repeated GetUserResponse users = 1;
}
```

**Behavior:**
- Returns only users that exist (missing IDs are silently skipped)
- Deleted users are included with `deleted=true`

---

### 5. UpdateUser

**Purpose:** Proxy pattern for UC22 (update user profile from User-Group Service)  
**Used By:** User-Group Service `PUT /users/{userId}` endpoint

**Request:**

```protobuf
message UpdateUserRequest {
  string user_id = 1;
  string full_name = 2;
}
```

**Response:**

```protobuf
message UpdateUserResponse {
  GetUserResponse user = 1;
}
```

**Error:** `NOT_FOUND` if user doesn't exist

**Why This Exists:** User-Group Service does not store user data. Profile updates must go through Identity Service to maintain single source of truth.

---

### 6. ListUsers

**Purpose:** List users with pagination and filters  
**Used By:** User-Group Service admin user listing

**Request:**

```protobuf
message ListUsersRequest {
  int32 page = 1;
  int32 size = 2;
  string status = 3;  // ACTIVE | LOCKED
  string role = 4;    // ADMIN | LECTURER | STUDENT
}
```

**Response:**

```protobuf
message ListUsersResponse {
  repeated GetUserResponse users = 1;
  int64 total_elements = 2;
}
```

---

## Security Design

### Authentication Flow

#### 1. JWT Structure

**Algorithm:** HS256 (HMAC-SHA256)  
**Signing Key:** `JWT_SECRET` environment variable (min 32 bytes)  
**Access Token TTL:** 15 minutes (900 seconds)

**JWT Claims:**

```json
{
  "sub": "123",                   // User ID (Long as string)
  "email": "user@example.com",    // User email
  "roles": ["STUDENT"],           // List of roles (always 1 role)
  "token_type": "ACCESS",         // Distinguish from refresh tokens
  "iat": 1738483200,              // Issued at (Unix timestamp)
  "exp": 1738484100               // Expiration (iat + 900 seconds)
}
```

**Security Properties:**
- **Stateless:** No database lookup on every request
- **Tamper-proof:** Signature verification prevents modification
- **Cannot be revoked:** Valid until expiration (15 min window acceptable)

---

#### 2. Refresh Token Structure

**Format:** UUID v4 (128-bit random, 36 characters)  
**Storage:** Database (`refresh_tokens` table)  
**TTL:** 7 days

**Lifecycle:**
1. Created on login/register
2. Validated on `/api/auth/refresh`
3. Rotated (old token revoked, new token issued)
4. Revoked on logout or account lock/delete

**Why Refresh Tokens?**
- Long-lived sessions without long-lived JWTs
- Revocable (can force logout by revoking token)

---

#### 3. Password Security

**Hashing:** BCrypt with strength factor 10  
**Validation:** Constant-time comparison to prevent timing attacks

**Login Security Pattern (CRITICAL):**

```java
// BAD: Status check before password validation (enumeration attack)
User user = findByEmail(email);
if (user.isLocked()) throw AccountLockedException();
if (!passwordMatches(password, user.hash)) throw InvalidCredentialsException();

// GOOD: Password validation FIRST
User user = findByEmail(email);
if (!passwordMatches(password, user.hash)) throw InvalidCredentialsException(); // Same error for user not found
if (user.isLocked()) throw AccountLockedException(); // Different error code only AFTER password verified
```

**Rationale:** Prevents attacker from enumerating locked accounts.

---

### Authorization Rules

#### Role Hierarchy

```
ADMIN > LECTURER > STUDENT
```

No role inheritance (explicit role checks only).

#### REST Endpoint Authorization Matrix

| Endpoint                      | Allowed Roles         | Additional Rules                  |
|-------------------------------|-----------------------|-----------------------------------|
| `POST /api/auth/register`     | Public                | Can only create STUDENT accounts  |
| `POST /api/auth/login`        | Public                | -                                 |
| `POST /api/auth/refresh`      | Public                | -                                 |
| `POST /api/auth/logout`       | Authenticated         | Any role                          |
| `POST /api/admin/users`       | ADMIN                 | -                                 |
| `DELETE /api/admin/users/*`   | ADMIN                 | Cannot delete self                |
| `POST /api/admin/users/*/lock`| ADMIN                 | Cannot lock self                  |
| `GET /api/admin/audit-logs`   | ADMIN                 | -                                 |

#### gRPC Authorization

**NONE.** gRPC endpoints are internal only (assumed to be called from trusted services).

**Security Model:**
- Network-level security (firewall, service mesh)
- No authentication/authorization in gRPC layer

**Future:** Add mTLS (mutual TLS) for service-to-service auth.

---

### JWT Validation Filter

**File:** [`JwtAuthenticationFilter.java`](../identity-service/src/main/java/com/example/identityservice/security/JwtAuthenticationFilter.java)

**Flow:**

```java
1. Extract "Authorization: Bearer <token>" header
2. Parse JWT and validate signature
3. Check expiration
4. Extract userId from "sub" claim
5. Load User from database by ID
6. Check user.status == ACTIVE
7. Create Authentication object with role
8. Set SecurityContext
9. Continue to controller
```

**CRITICAL:** Filter loads full user from database on every request to check current status (soft delete, lock). This is intentional for security, but adds DB query overhead.

**Future Optimization:** Cache user status in Redis with 1-minute TTL.

---

## Transaction & Consistency

### Transactional Boundaries

#### Service Layer Transactions

**Rule:** All service methods are `@Transactional` unless explicitly read-only.

**Examples:**

```java
@Transactional  // Default: READ_WRITE
public void softDeleteUser(Long userId) {
    User user = userRepository.findById(userId).orElseThrow();
    user.softDelete(actorId);
    refreshTokenService.revokeAllTokensForUser(userId);  // Same transaction
    auditService.logUserDeleted(user);                   // Same transaction
}

@Transactional(readOnly = true)  // Explicit read-only
public Optional<User> getUserById(Long userId) {
    return userRepository.findById(userId);
}
```

**Isolation Level:** Default (READ_COMMITTED for PostgreSQL)

---

### Race Conditions & Locking

#### 1. Email Uniqueness (Registration)

**Scenario:** Two users register with same email simultaneously.

**Solution:** Database UNIQUE constraint on `users.email`

```java
try {
    user = userRepository.save(user);
} catch (DataIntegrityViolationException ex) {
    throw new EmailAlreadyExistsException();  // 409 Conflict
}
```

**Outcome:** One transaction succeeds, other gets 409 Conflict.

---

#### 2. Refresh Token Rotation

**Scenario:** User calls `/refresh` twice concurrently with same token.

**Current Behavior:**
- First request: revokes token, issues new one
- Second request: token already revoked → 401 Unauthorized

**No explicit locking.** Token revocation is idempotent.

**Potential Issue:** If second request reads token before first commits, both might succeed (issuing 2 new tokens). **NOT FIXED YET** (low severity, 15-min window).

**Future Fix:** Add optimistic locking (`@Version`) or pessimistic lock (`SELECT FOR UPDATE`).

---

#### 3. Soft Delete + Restore Race

**Scenario:** Admin deletes user while another admin restores same user.

**Current Behavior:** Last write wins (no locking).

**Risk:** User ends up in inconsistent state (e.g., deleted but tokens not revoked).

**Mitigation:** Rare edge case. Audit logs track both actions.

---

### Critical Transaction Notes

**1. Audit Logging Must Succeed:**

If audit log fails, should entire transaction rollback?

**Current Behavior:** YES (audit logging is part of transaction).

**Alternative Design:** Async audit logging (eventual consistency). **NOT IMPLEMENTED.**

**2. gRPC Calls Are NOT Transactional:**

User-Group Service calling Identity Service via gRPC does not participate in same transaction.

**Example:**
```java
// User-Group Service
@Transactional
public void createGroup(request) {
    // Validate lecturer via gRPC (NOT in transaction)
    VerifyUserResponse response = identityClient.verifyUser(lecturerId);
    
    // Save group (IN transaction)
    groupRepository.save(group);
}
```

**Risk:** Lecturer could be deleted between gRPC call and group save.

**Mitigation:** Business rules + compensating actions (delete group if lecturer invalid).

---

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── BaseException (abstract)
│   ├── EmailAlreadyExistsException (409)
│   ├── PasswordMismatchException (400)
│   ├── InvalidCredentialsException (401)
│   ├── AccountLockedException (403)
│   ├── TokenExpiredException (401)
│   ├── TokenInvalidException (401)
│   ├── UserNotFoundException (404)
│   ├── InvalidUserStateException (400)
│   ├── SelfActionException (400)
│   └── ConflictException (409)
└── [Other Spring/JPA exceptions]
```

### HTTP Status Code Mapping

| HTTP Code | Error Type               | Example                           |
|-----------|--------------------------|-----------------------------------|
| 400       | Bad Request              | Password mismatch, validation err |
| 401       | Unauthorized             | Invalid credentials, expired token|
| 403       | Forbidden                | Account locked                    |
| 404       | Not Found                | User ID not found                 |
| 409       | Conflict                 | Email already exists              |
| 500       | Internal Server Error    | Unexpected exceptions             |

### gRPC Status Code Mapping

| gRPC Status      | Condition                        |
|------------------|----------------------------------|
| `NOT_FOUND`      | User ID not found                |
| `INVALID_ARGUMENT` | Invalid user ID format         |
| `INTERNAL`       | Unexpected exception             |

### Error Response Format (REST)

**File:** [`ErrorResponse.java`](../identity-service/src/main/java/com/example/identityservice/dto/ErrorResponse.java)

```json
{
  "errorCode": "EMAIL_EXISTS",
  "message": "Email already registered: student@example.com",
  "timestamp": "2026-02-02T10:30:00Z"
}
```

**Error Codes:**

| Code                  | HTTP | Description                     |
|-----------------------|------|---------------------------------|
| `EMAIL_EXISTS`        | 409  | Email already registered        |
| `PASSWORD_MISMATCH`   | 400  | Passwords don't match           |
| `INVALID_CREDENTIALS` | 401  | Wrong email/password            |
| `ACCOUNT_LOCKED`      | 403  | Account is locked               |
| `TOKEN_EXPIRED`       | 401  | Refresh token expired           |
| `TOKEN_INVALID`       | 401  | Refresh token invalid/revoked   |
| `USER_NOT_FOUND`      | 404  | User ID not found               |
| `INVALID_STATE`       | 400  | User already deleted/locked     |
| `SELF_ACTION_DENIED`  | 400  | Cannot delete/lock own account  |
| `VALIDATION_ERROR`    | 400  | Bean validation failed          |

### Global Exception Handler

**File:** [`GlobalExceptionHandler.java`](../identity-service/src/main/java/com/example/identityservice/exception/GlobalExceptionHandler.java)

**Responsibilities:**
- Catch all exceptions from controllers
- Map to appropriate HTTP status + error response
- Log errors (sensitive data excluded)

**Security Consideration:** Does NOT expose stack traces or internal details in production.

---

## Important Implementation Notes

### 1. Soft Delete Must Be Respected

**Rule:** Deleted users MUST NOT appear in standard queries.

**Enforcement:**
- `@SQLRestriction("deleted_at IS NULL")` on `User` entity
- All JPA queries automatically filter soft-deleted users
- gRPC uses `findByIdIgnoreDeleted()` to bypass filter (intentional)

**When to Use Bypass:**
- gRPC `GetUser`: Return deleted users with `deleted=true` flag
- Admin restore: Need to find deleted user by ID

**When NOT to Bypass:**
- Login: Deleted users cannot login
- Admin list: Deleted users not shown (unless explicitly filtered)

---

### 2. Password Validation Order (CRITICAL)

**Anti-Enumeration Pattern:**

```java
// Step 1: Find user (return generic error if not found)
User user = userRepository.findByEmail(email)
    .orElseThrow(() -> new InvalidCredentialsException());

// Step 2: Validate password FIRST (constant-time BCrypt)
if (!passwordEncoder.matches(password, user.getPasswordHash())) {
    auditService.logLoginFailure(email, "Invalid password");
    throw new InvalidCredentialsException();  // SAME error as step 1
}

// Step 3: Check account status AFTER password verified
if (user.getStatus() == User.Status.LOCKED) {
    auditService.logLoginDenied(user, "Account is locked");
    throw new AccountLockedException();  // DIFFERENT error code
}
```

**Why:**
- Prevents attacker from distinguishing "user not found" vs "wrong password" vs "account locked"
- Account locked error only revealed if password is correct

**DO NOT CHANGE THIS ORDER** without security review.

---

### 3. Audit Logging Is Mandatory

**Rule:** All security-sensitive operations MUST create audit logs.

**Required Events:**
- Login attempts (success, failure)
- User creation (register, admin create)
- User deletion (soft delete, restore)
- Account lock/unlock
- Password change (future feature)
- External account mapping

**Audit Log Format:**
- `action`: AuditAction enum
- `outcome`: SUCCESS, FAILURE, DENIED
- `actorId` + `actorEmail`: Who performed the action
- `entityType` + `entityId`: What was affected
- `ipAddress` + `userAgent`: Request context
- `oldValue` + `newValue`: Change details (JSON)

**DO NOT:**
- Log passwords (even hashed)
- Delete audit logs (immutable)

---

### 4. Refresh Token Rotation

**Rule:** `/api/auth/refresh` MUST rotate refresh token.

**Implementation:**

```java
public LoginResponse refreshToken(String oldToken) {
    RefreshToken token = findByToken(oldToken).orElseThrow(...);
    
    // Revoke old token
    token.setRevoked(true);
    refreshTokenRepository.save(token);
    
    // Generate new access token + new refresh token
    String newAccessToken = jwtService.generateAccessToken(token.getUser());
    String newRefreshToken = createRefreshToken(token.getUser());
    
    return LoginResponse.of(newAccessToken, newRefreshToken, 900);
}
```

**Why Rotation:**
- Limits exposure if refresh token is stolen
- Allows detection of token theft (if old token is reused after rotation, both sessions are invalid)

**Future Enhancement:** Refresh token reuse detection (track token families).

---

### 5. gRPC Service Is Internal Only

**Security Model:**
- NO authentication on gRPC endpoints
- Assumes network-level security (firewall, service mesh)
- Trusts all incoming calls

**Production Deployment:**
- gRPC port (9091) MUST NOT be exposed to internet
- Use Kubernetes network policies to restrict access
- Future: Add mTLS for service-to-service auth

---

### 6. Database Indexes Are Critical

**Required Indexes:**
- `users.email` (UNIQUE) - login performance
- `users.status` - admin filtering
- `users.deleted_at` - soft delete queries
- `refresh_tokens.token` (UNIQUE) - refresh token lookup
- `audit_logs.entity_type, entity_id` - audit queries
- `audit_logs.timestamp` - date range queries

**DO NOT DROP** these indexes without performance testing.

---

### 7. Configuration Security

**Secrets Management:**

**Required Environment Variables:**
- `JWT_SECRET`: Min 32 bytes, random (use `openssl rand -hex 32`)
- `SPRING_DATASOURCE_PASSWORD`: Database password
- `CORS_ALLOWED_ORIGINS`: Comma-separated list of allowed origins

**DO NOT:**
- Hardcode secrets in `application.yml`
- Commit `.env` files to git
- Use default/weak JWT secret in production

**Production:** Use secret management service (AWS Secrets Manager, Vault, etc.)

---

## Known Issues & Tech Debt

### 1. JWT Revocation Not Supported

**Problem:** Access tokens are valid until expiration (15 min) even if user is deleted/locked.

**Impact:** Deleted/locked users can still make requests for up to 15 minutes.

**Mitigation:** Short TTL (15 min) limits exposure.

**Future Fix:** JWT blacklist in Redis (check on every request).

---

### 2. No Rate Limiting

**Problem:** No protection against brute-force login attacks.

**Impact:** Attacker can try unlimited passwords.

**Mitigation:** Account lockout (manual admin action required).

**Future Fix:** Implement rate limiting (e.g., 5 failed logins → temporary lock).

---

### 3. Refresh Token Cleanup

**Problem:** Expired/revoked refresh tokens never deleted from database.

**Impact:** Table grows unbounded.

**Future Fix:** Scheduled task to delete tokens older than 30 days.

---

### 4. Audit Log Archival

**Problem:** Audit logs never deleted (grows unbounded).

**Impact:** Table size, query performance degradation.

**Future Fix:** Archive to cold storage after 90 days (per retention policy).

---

### 5. No gRPC Metrics

**Problem:** No observability into gRPC call performance.

**Impact:** Difficult to debug User-Group Service issues.

**Future Fix:** Add gRPC interceptors for metrics (latency, error rate).

---

**End of Identity Service Documentation**
