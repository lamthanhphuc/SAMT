# API Contract – Identity Service

**Version:** 1.0  
**Base URL:** `/api`  
**Date:** January 30, 2026

---

## Standard Response Formats

### Success Response

```json
{
  "data": { ... },           // Actual response data
  "timestamp": "2026-01-30T10:30:00Z"
}
```

### Error Response

**MUST** follow this format:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",        // Machine-readable error code
    "message": "Invalid email format", // Human-readable message
    "field": "email",                  // Optional: field name for validation errors
    "details": { ... }                 // Optional: additional context
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

### Standard Error Codes

| HTTP Status | Error Code | Usage |
|-------------|------------|-------|
| 400 | `VALIDATION_ERROR` | Input validation failed |
| 400 | `PASSWORD_MISMATCH` | Password != confirmPassword |
| 400 | `WEAK_PASSWORD` | Password doesn't meet requirements |
| 400 | `INVALID_REQUEST` | Generic bad request |
| 401 | `UNAUTHORIZED` | No token or invalid token |
| 401 | `INVALID_CREDENTIALS` | Email/password incorrect |
| 401 | `TOKEN_EXPIRED` | JWT or refresh token expired |
| 401 | `TOKEN_INVALID` | Token revoked or malformed |
| 403 | `FORBIDDEN` | Valid token but insufficient permissions |
| 403 | `ACCOUNT_LOCKED` | Account is locked by admin |
| 404 | `USER_NOT_FOUND` | User ID doesn't exist |
| 409 | `EMAIL_ALREADY_EXISTS` | Email uniqueness violation |
| 409 | `CONFLICT` | Generic conflict (duplicate, etc.) |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error |

---

## Authentication Endpoints

### 1. POST `/api/auth/register`

**Description:** Register new user account

**Authorization:** None (public)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "email": "student@university.edu",       // MUST: RFC 5322, max 255 chars, unique
  "password": "SecurePass@123",            // MUST: 8-128 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special (@$!%*?&)
  "confirmPassword": "SecurePass@123",     // MUST: match password
  "fullName": "Nguyen Van A",              // MUST: 2-100 chars, Unicode letters/spaces/hyphens
  "role": "STUDENT"                        // MUST: STUDENT only (LECTURER/ADMIN requires admin creation)
}
```

**Response 201 Created:**
```json
{
  "user": {
    "id": 123,  // BIGINT (Long)
    "email": "student@university.edu",
    "fullName": "Nguyen Van A",
    "role": "STUDENT",
    "status": "ACTIVE",
    "createdAt": "2026-01-30T10:30:00Z"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // JWT, 15min TTL
  "refreshToken": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",    // UUID, 7 days TTL
  "tokenType": "Bearer",
  "expiresIn": 900                                            // seconds (15 * 60)
}
```

**Error Responses:**

```json
// 400 - Email format invalid
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid email format",
    "field": "email"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - Password too weak
{
  "error": {
    "code": "WEAK_PASSWORD",
    "message": "Password must contain at least 8 characters, including uppercase, lowercase, digit, and special character",
    "field": "password"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - Passwords don't match
{
  "error": {
    "code": "PASSWORD_MISMATCH",
    "message": "Passwords do not match",
    "field": "confirmPassword"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 409 - Email already exists
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "Email already registered",
    "field": "email"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-REG-01 (MUST):** Email uniqueness check MUST happen before password hashing
- **BR-REG-02 (MUST):** Password MUST be hashed with BCrypt strength 10
- **BR-REG-03 (MUST):** New user created with `status = ACTIVE`
- **BR-REG-04 (MUST):** STUDENT role is auto-assigned, ignore `role` field for LECTURER/ADMIN
- **BR-REG-05 (MUST):** Both access + refresh tokens generated immediately
- **BR-REG-06 (SHOULD):** Rate limit: 5 registrations per hour per IP

---

### 2. POST `/api/auth/login`

**Description:** Login with email + password

**Authorization:** None (public)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "email": "user@example.com",           // MUST: existing email
  "password": "SecurePass@123"           // MUST: correct password
}
```

**Response 200 OK:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Error Responses:**

```json
// 401 - Email not found OR password incorrect (SAME message for anti-enumeration)
{
  "error": {
    "code": "INVALID_CREDENTIALS",
    "message": "Invalid credentials"     // MUST be generic, no detail
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 403 - Account locked (only if password is correct)
{
  "error": {
    "code": "ACCOUNT_LOCKED",
    "message": "Account is locked. Contact admin."
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-LOGIN-01 (MUST):** Validate password BEFORE checking account status (anti-enumeration)
- **BR-LOGIN-02 (MUST):** Use BCrypt constant-time comparison
- **BR-LOGIN-03 (MUST):** Return generic "Invalid credentials" for email not found OR password incorrect
- **BR-LOGIN-04 (MUST):** Return "Account locked" ONLY if password is correct (user verified identity)
- **BR-LOGIN-05 (MUST):** Refresh token stored in DB with 7-day expiration

---

### 3. POST `/api/auth/refresh`

**Description:** Refresh access token using refresh token

**Authorization:** None (public, uses refresh token in body)

**Request Headers:**
```
Content-Type: application/json
```

**Request Body:**
```json
{
  "refreshToken": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"
}
```

**Response 200 OK:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",  // NEW JWT
  "refreshToken": "z9y8x7w6-v5u4-43t2-s1r0-q9p8o7n6m5l4",    // NEW UUID
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Error Responses:**

```json
// 401 - Token expired
{
  "error": {
    "code": "TOKEN_EXPIRED",
    "message": "Token expired"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 401 - Token revoked or not found
{
  "error": {
    "code": "TOKEN_INVALID",
    "message": "Token invalid"           // MUST be generic (don't reveal "revoked")
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 403 - Account locked (user status check)
{
  "error": {
    "code": "ACCOUNT_LOCKED",
    "message": "Account is locked. Contact admin."
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-REFRESH-01 (MUST):** Check user.status == LOCKED BEFORE generating new tokens
- **BR-REFRESH-02 (MUST):** If locked, revoke ALL user tokens (`revokeAllByUser()`) before throwing exception
- **BR-REFRESH-03 (MUST):** Revoke old token (set `revoked = true`) BEFORE creating new token
- **BR-REFRESH-04 (MUST):** **Token reuse detection**: If revoked token is reused → Revoke ALL tokens, force re-login
- **BR-REFRESH-05 (MUST):** New refresh token has new UUID + new 7-day expiration
- **BR-REFRESH-06 (MUST):** Atomic transaction: revoke old + create new MUST be in same transaction

---

### 4. POST `/api/auth/logout`

**Description:** Logout user (revoke refresh token)

**Authorization:** Bearer Token (MUST)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body:**
```json
{
  "refreshToken": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6"
}
```

**Response 204 No Content:**
```
(Empty body)
```

**Error Responses:**

```json
// 401 - Invalid/missing access token
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Unauthorized"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-LOGOUT-01 (MUST):** Require valid access token in header
- **BR-LOGOUT-02 (MUST):** Idempotent - revoking non-existent token returns 204 (silent success)
- **BR-LOGOUT-03 (MUST):** Revoke only the provided refresh token (not all tokens)
- **BR-LOGOUT-04 (SHOULD):** Client MUST discard access token locally (backend cannot invalidate JWT)

---

## Admin Endpoints

### 9. POST `/api/admin/users`

**Description:** Create user account with any role (admin only)

**Authorization:** Bearer Token (ADMIN role required)

> **Note:** ROLE_ prefix is Spring Security internal. APIs document plain role names: ADMIN, LECTURER, STUDENT.

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body:**
```json
{
  "email": "lecturer@university.edu",       // MUST: RFC 5322, max 255 chars, unique
  "password": "SecurePass@123",            // MUST: 8-128 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special (@$!%*?&)
  "fullName": "Nguyen Van B",              // MUST: 2-100 chars, Unicode letters/spaces/hyphens
  "role": "LECTURER"                       // MUST: STUDENT | LECTURER | ADMIN
}
```

**Response 201 Created:**
```json
{
  "message": "User created successfully",
  "user": {
    "id": 123,
    "email": "lecturer@university.edu",
    "fullName": "Nguyen Van B",
    "role": "LECTURER",
    "status": "ACTIVE",
    "jiraAccountId": null,
    "githubUsername": null,
    "createdAt": "2026-01-30T10:30:00Z"
  },
  "temporaryPassword": "SecurePass@123"    // Return password for admin to share with new user
}
```

**Error Responses:**

```json
// 401 - Not authenticated
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Unauthorized"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 403 - Not admin
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Access denied"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - Validation error
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid email format",
    "field": "email"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 409 - Email already exists
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "Email already registered",
    "field": "email"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-ADMIN-CREATE-01 (MUST):** Only ADMIN role can create users via this endpoint
- **BR-ADMIN-CREATE-02 (MUST):** Can create users with any role (STUDENT, LECTURER, ADMIN)
- **BR-ADMIN-CREATE-03 (MUST):** Email uniqueness check MUST happen before password hashing
- **BR-ADMIN-CREATE-04 (MUST):** Password MUST be hashed with BCrypt strength 10
- **BR-ADMIN-CREATE-05 (MUST):** New user created with `status = ACTIVE`
- **BR-ADMIN-CREATE-06 (MUST):** Temporary password returned in response for admin to share securely
- **BR-ADMIN-CREATE-07 (MUST):** Audit log action: `USER_CREATED` with actor = admin_id
- **BR-ADMIN-CREATE-08 (SHOULD):** Admin should inform user to change password on first login

**Security Notes:**
- This endpoint is separate from public registration (`POST /api/auth/register`)
- Public registration only allows `STUDENT` role
- Admin endpoint allows `LECTURER` and `ADMIN` roles for privileged accounts
- Temporary password should be transmitted securely to new user (out of scope for API)

---

### 11. DELETE `/api/admin/users/{userId}`

**Description:** Soft delete user (admin only)

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: Long (BIGINT) - User ID from database

**Response 200 OK:**
```json
{
  "message": "User deleted successfully",
  "userId": 123  // BIGINT (Long)
}
```

**Error Responses:**

```json
// 401 - Not authenticated
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Unauthorized"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 403 - Not admin
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Access denied"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 404 - User not found
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "User not found"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - Already deleted
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "User already deleted"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - Self-delete
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Cannot delete own account"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-SOFTDEL-01 (MUST):** Set `deleted_at = NOW()` and `deleted_by = admin_id`
- **BR-SOFTDEL-02 (MUST):** Revoke ALL refresh tokens of target user
- **BR-SOFTDEL-03 (MUST):** Cannot delete self (admin_id == user_id)
- **BR-SOFTDEL-04 (MUST):** Cannot delete already-deleted user (400 if deleted_at != NULL)
- **BR-SOFTDEL-05 (MUST):** Audit log action: `SOFT_DELETE` with target user info

---

### 11. POST `/api/admin/users/{userId}/restore`

**Description:** Restore soft-deleted user (admin only)

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: Long (BIGINT) - User ID from database

**Response 200 OK:**
```json
{
  "message": "User restored successfully",
  "userId": 123  // BIGINT (Long)
}
```

**Error Responses:**

```json
// 404 - User not found
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "User not found"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - User not deleted
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "User is not deleted"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-RESTORE-01 (MUST):** Set `deleted_at = NULL` and `deleted_by = NULL`
- **BR-RESTORE-02 (MUST):** Can only restore if user is currently soft-deleted (deleted_at != NULL)
- **BR-RESTORE-03 (MUST):** Audit log action: `RESTORE` with target user info
- **BR-RESTORE-04 (MUST):** Use native query to bypass `@SQLRestriction("deleted_at IS NULL")`

---

### 12. POST `/api/admin/users/{userId}/lock`

**Description:** Lock user account (admin only)

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: Long (BIGINT) - User ID from database

**Query Parameters:**
```
?reason=Suspicious%20activity  // Optional: Reason for locking
```

**Response 200 OK:**
```json
{
  "message": "User locked successfully",
  "userId": 123  // BIGINT (Long)
}
```

**Error Responses:**

```json
// 400 - Self-lock
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Cannot lock own account"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-LOCK-01 (MUST):** Set `status = LOCKED`
- **BR-LOCK-02 (MUST):** Revoke ALL refresh tokens of target user
- **BR-LOCK-03 (MUST):** Cannot lock self (admin_id == user_id)
- **BR-LOCK-04 (MUST):** Idempotent - locking already-locked user returns 200 (no error)
- **BR-LOCK-05 (MUST):** Audit log action: `ACCOUNT_LOCKED` with reason (if provided)

---

### 13. POST `/api/admin/users/{userId}/unlock`

**Description:** Unlock user account (admin only)

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: Long (BIGINT) - User ID from database

**Response 200 OK:**
```json
{
  "message": "User unlocked successfully",
  "userId": 123  // BIGINT (Long)
}
```

**Error Responses:**

```json
// 400 - User not locked
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "User is not locked"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-UNLOCK-01 (MUST):** Set `status = ACTIVE`
- **BR-UNLOCK-02 (MUST):** Can only unlock if user is currently locked (status == LOCKED)
- **BR-UNLOCK-03 (MUST):** Audit log action: `ACCOUNT_UNLOCKED`

---

### 14. PUT `/api/admin/users/{userId}/external-accounts`

**Description:** Map/unmap external accounts (Jira, GitHub)

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Path Parameters:**
- `userId`: Long (BIGINT) - User ID from database

**Request Body:**
```json
{
  "jiraAccountId": "5f9d8c7b6a5e4d3c2b1a0987",  // OPTIONAL: Jira account ID, null to unmap
  "githubUsername": "student-github-handle"     // OPTIONAL: GitHub username, null to unmap
}
```

**Response 200 OK:**
```json
{
  "message": "External accounts mapped successfully",
  "user": {
    "id": 123,  // BIGINT (Long)
    "email": "student@university.edu",
    "fullName": "Nguyen Van A",
    "role": "STUDENT",
    "status": "ACTIVE",
    "jiraAccountId": "5f9d8c7b6a5e4d3c2b1a0987",
    "githubUsername": "student-github-handle"
  }
}
```

**Error Responses:**

```json
// 409 - Jira account ID already used
{
  "error": {
    "code": "CONFLICT",
    "message": "Jira account ID already mapped to another user"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 409 - GitHub username already used
{
  "error": {
    "code": "CONFLICT",
    "message": "GitHub username already mapped to another user"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 400 - User is deleted
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Cannot map external accounts to deleted user"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-MAP-01 (MUST):** `jira_account_id` must be unique across all users (409 if exists)
- **BR-MAP-02 (MUST):** `github_username` must be unique across all users (409 if exists)
- **BR-MAP-03 (MUST):** Only ADMIN can perform mapping (403 for non-admin)
- **BR-MAP-04 (MUST):** Cannot map to deleted users (400 if deleted_at != NULL)
- **BR-MAP-05 (MUST):** Audit log with old_value + new_value for change tracking
- **BR-MAP-06 (MUST):** To unmap, send `null` value for field
- **BR-MAP-07 (SHOULD):** Validate Jira account ID format (alphanumeric, 20-30 chars)
- **BR-MAP-08 (SHOULD):** Validate GitHub username format (alphanumeric + hyphen, 1-39 chars)

---

## Audit Log Query Endpoints

### 15. GET `/api/admin/audit/entity/{entityType}/{entityId}`

**Description:** Get audit history for a specific entity

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `entityType`: String - Entity type (e.g., "User", "RefreshToken")
- `entityId`: Long (BIGINT) - Entity ID

**Query Parameters:**
```
?page=0&size=20&sort=timestamp,desc
```

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": 1,
      "entityType": "User",
      "entityId": 123,
      "action": "ACCOUNT_LOCKED",
      "actorId": 1,
      "actorEmail": "admin@university.edu",
      "outcome": "SUCCESS",
      "metadata": {
        "target_user_id": 123,
        "admin_id": 1,
        "reason": "Suspicious activity"
      },
      "timestamp": "2026-01-30T10:30:00Z"
    }
  ],
  "pageable": { ... },
  "totalElements": 50,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

---

### 16. GET `/api/admin/audit/actor/{actorId}`

**Description:** Get all actions performed by a specific user

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `actorId`: Long (BIGINT) - User ID of actor

**Query Parameters:**
```
?page=0&size=20&sort=timestamp,desc
```

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": 10,
      "entityType": "User",
      "entityId": 456,
      "action": "SOFT_DELETE",
      "actorId": 1,
      "actorEmail": "admin@university.edu",
      "outcome": "SUCCESS",
      "metadata": {
        "target_user_id": 456,
        "admin_id": 1
      },
      "timestamp": "2026-01-30T11:00:00Z"
    }
  ],
  "pageable": { ... },
  "totalElements": 25,
  "totalPages": 2,
  "size": 20,
  "number": 0
}
```

---

### 17. GET `/api/admin/audit/range`

**Description:** Get audit logs within a date range

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Query Parameters:**
```
?startDate=2026-01-01T00:00:00Z&endDate=2026-01-31T23:59:59Z&page=0&size=20&sort=timestamp,desc
```
- `startDate`: ISO 8601 DateTime (e.g., `2026-01-01T00:00:00Z`)
- `endDate`: ISO 8601 DateTime (e.g., `2026-01-31T23:59:59Z`)

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": 100,
      "entityType": "RefreshToken",
      "entityId": 789,
      "action": "TOKEN_REUSE_DETECTED",
      "actorId": 123,
      "actorEmail": "student@university.edu",
      "outcome": "FAILURE",
      "metadata": {
        "user_id": 123,
        "token_id": 789,
        "ip_address": "192.168.1.1"
      },
      "timestamp": "2026-01-15T14:30:00Z"
    }
  ],
  "pageable": { ... },
  "totalElements": 500,
  "totalPages": 25,
  "size": 20,
  "number": 0
}
```

---

### 18. GET `/api/admin/audit/security-events`

**Description:** Get security-related audit events (login failures, token reuse, account locks)

**Authorization:** Bearer Token (ADMIN role required)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Query Parameters:**
```
?page=0&size=20&sort=timestamp,desc
```

**Response 200 OK:**
```json
{
  "content": [
    {
      "id": 200,
      "entityType": "User",
      "entityId": 123,
      "action": "LOGIN_FAILED",
      "actorId": 123,
      "actorEmail": "student@university.edu",
      "outcome": "FAILURE",
      "metadata": {
        "email": "student@university.edu",
        "ip_address": "192.168.1.1",
        "reason": "Invalid credentials"
      },
      "timestamp": "2026-01-20T09:15:00Z"
    },
    {
      "id": 201,
      "entityType": "RefreshToken",
      "entityId": 456,
      "action": "TOKEN_REUSE_DETECTED",
      "actorId": 789,
      "actorEmail": "lecturer@university.edu",
      "outcome": "FAILURE",
      "metadata": {
        "user_id": 789,
        "token_id": 456,
        "ip_address": "10.0.0.5"
      },
      "timestamp": "2026-01-20T10:00:00Z"
    }
  ],
  "pageable": { ... },
  "totalElements": 150,
  "totalPages": 8,
  "size": 20,
  "number": 0
}
```

**Note:** Security events include:
- `LOGIN_FAILED`
- `TOKEN_REUSE_DETECTED`
- `ACCOUNT_LOCKED`
- `SOFT_DELETE`
- `RESTORE`

---

## Security Headers (MUST)

All responses **MUST** include:

```
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
X-XSS-Protection: 1; mode=block
Strict-Transport-Security: max-age=31536000; includeSubDomains
Content-Security-Policy: default-src 'self'
```

---

## CORS Configuration

**Allowed Origins (Production):**
```
https://frontend.university.edu
```

**Allowed Methods:**
```
GET, POST, PUT, DELETE, OPTIONS
```

**Allowed Headers:**
```
Authorization, Content-Type
```

**Credentials:**
```
Access-Control-Allow-Credentials: true
```

---

## JWT Token Format

### Access Token (JWT)

**Header:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**Payload:**
```json
{
  "sub": 123,  // User ID (BIGINT/Long)
  "email": "user@example.com",
  "roles": ["STUDENT"],                            // Array of roles (NO ROLE_ prefix)
  "iat": 1706612400,                               // Issued at (Unix timestamp)
  "exp": 1706613300,                               // Expires at (iat + 900 seconds)
  "token_type": "ACCESS"
}
```

**IMPORTANT**: Roles in JWT do NOT have `ROLE_` prefix. The prefix is only added internally by Spring Security when creating `GrantedAuthority`.

### Refresh Token (Opaque)

**Format:** UUID v4 string
```
a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6
```

**Storage:** Database table `refresh_tokens`

---

## Transaction Requirements

| Endpoint | Transaction Scope | Rollback On |
|----------|-------------------|-------------|
| `POST /api/auth/register` | User creation + token generation | Any exception |
| `POST /api/auth/login` | Token generation + DB persist | Any exception |
| `POST /api/auth/refresh` | Revoke old + create new token | Any exception (CRITICAL) |
| `POST /api/auth/logout` | Token revocation | Any exception |
| `DELETE /api/admin/users/{userId}` | Update user + revoke tokens + audit log | Any exception |
| `POST /api/admin/users/{userId}/restore` | Update user + audit log | Any exception |
| `POST /api/admin/users/{userId}/lock` | Update status + revoke tokens + audit log | Any exception |
| `POST /api/admin/users/{userId}/unlock` | Update status + audit log | Any exception |
| `PUT /api/admin/users/{userId}/external-accounts` | Update user + audit log | Any exception |

**CRITICAL:** `POST /api/auth/refresh` **MUST** be atomic - if new token creation fails, old token **MUST NOT** be revoked.

---

## Idempotency

| Endpoint | Idempotent? | Behavior |
|----------|-------------|----------|
| `POST /api/auth/register` | ❌ No | Multiple calls create multiple users (409 if email exists) |
| `POST /api/auth/login` | ❌ No | Multiple calls generate different tokens |
| `POST /api/auth/refresh` | ❌ No | Each call generates new tokens |
| `POST /api/auth/logout` | ✅ Yes | Revoking already-revoked token returns 204 |
| `DELETE /api/admin/users/{userId}` | ❌ No | Deleting already-deleted user returns 400 |
| `POST /api/admin/users/{userId}/restore` | ❌ No | Restoring non-deleted user returns 400 |
| `POST /api/admin/users/{userId}/lock` | ✅ Yes | Locking already-locked user returns 200 |
| `POST /api/admin/users/{userId}/unlock` | ❌ No | Unlocking non-locked user returns 400 |
| `PUT /api/admin/users/{userId}/external-accounts` | ✅ Yes | Setting same values returns 200 |

---

## Audit Logging Requirements

**MUST** log the following actions:

| Action | Trigger | Entity Type | Metadata |
|--------|---------|-------------|----------|
| `USER_REGISTERED` | User registers | User | email, role |
| `USER_LOGIN` | Successful login | User | email, ip_address |
| `LOGIN_FAILED` | Failed login | User | email, ip_address, reason |
| `TOKEN_REFRESHED` | Token refreshed | RefreshToken | user_id, old_token_id, new_token_id |
| `USER_LOGOUT` | User logs out | RefreshToken | user_id, token_id |
| `SOFT_DELETE` | User soft deleted | User | target_user_id, admin_id |
| `RESTORE` | User restored | User | target_user_id, admin_id |
| `ACCOUNT_LOCKED` | User locked | User | target_user_id, admin_id, reason |
| `ACCOUNT_UNLOCKED` | User unlocked | User | target_user_id, admin_id |
| `MAP_EXTERNAL_ACCOUNTS` | External accounts mapped | User | target_user_id, admin_id, old_value, new_value |
| `TOKEN_REUSE_DETECTED` | Revoked token reused | RefreshToken | user_id, token_id, ip_address |

---

## Observability

### Logging

**Framework:** SLF4J + Logback

**Log Levels:**
- `ERROR` - Exceptions, security events (token reuse, login failures)
- `WARN` - Business rule violations, account locks
- `INFO` - Successful operations (login, registration, admin actions)
- `DEBUG` - Detailed flow for debugging

**Structured Logging:**
```json
{
  "timestamp": "2026-01-30T10:30:00Z",
  "level": "INFO",
  "logger": "AuthService",
  "message": "User registered successfully",
  "userId": 123,
  "email": "user@example.com",
  "action": "USER_REGISTERED"
}
```

### Audit Logging

**Storage:** PostgreSQL `audit_logs` table

**Captured Events:**
- `USER_REGISTERED`, `USER_LOGIN`, `LOGIN_FAILED`
- `TOKEN_REFRESHED`, `USER_LOGOUT`, `TOKEN_REUSE_DETECTED`
- `SOFT_DELETE`, `RESTORE`, `ACCOUNT_LOCKED`, `ACCOUNT_UNLOCKED`
- `MAP_EXTERNAL_ACCOUNTS`

**Query Endpoints:**
- `GET /api/admin/audit/entity/{entityType}/{entityId}`
- `GET /api/admin/audit/actor/{actorId}`
- `GET /api/admin/audit/range?startDate=...&endDate=...`
- `GET /api/admin/audit/security-events`

### Correlation ID Support

**Status:** ❌ **NOT IMPLEMENTED**

**Recommendation:** Add `CorrelationIdFilter` to propagate `X-Request-ID` header through request lifecycle

### Resilience Patterns

**Circuit Breaker:** ❌ Not implemented  
**Retry Policy:** ❌ Not implemented  
**Bulkhead:** ❌ Not implemented  
**Timeout:** ✅ Database connection timeout (30s), Hikari leak detection (60s)

### Monitoring

**Health Endpoint:** `/actuator/health`

**Metrics (Future):**
- JWT token generation rate
- Login success/failure rate
- Refresh token usage patterns
- Database connection pool metrics (Hikari)

---

## Status

✅ **COMPLETE** - Ready for implementation

**Next Steps:**
1. Implement controllers with exact endpoints
2. Implement service layer with business rules
3. Implement error handling with standard error codes
4. Implement audit logging
5. Add integration tests for all endpoints
