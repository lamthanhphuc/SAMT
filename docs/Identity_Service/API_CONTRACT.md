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
| 429 | `RATE_LIMIT_EXCEEDED` | Too many requests |
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
    "id": "550e8400-e29b-41d4-a716-446655440000",  // UUID string
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
- **BR-LOGIN-06 (SHOULD):** Rate limit: 5 login attempts per 5 minutes per IP

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
- **BR-REFRESH-06 (SHOULD):** Rate limit: 20 refreshes per 15 minutes per user
- **BR-REFRESH-07 (MUST):** Atomic transaction: revoke old + create new MUST be in same transaction

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

## OAuth Endpoints

### 5. GET `/api/auth/oauth2/authorize/{provider}`

**Description:** Redirect to OAuth provider (Google/GitHub)

**Authorization:** None (public)

**Path Parameters:**
- `provider`: `google` | `github`

**Query Parameters:**
```
?redirect_uri=https://frontend.com/auth/callback  // Optional: Override default callback
```

**Response 302 Found:**
```
Location: https://accounts.google.com/o/oauth2/v2/auth?client_id=...&redirect_uri=...&scope=openid%20profile%20email&state=<CSRF_TOKEN>
```

**Business Rules:**
- **BR-OAUTH-01 (MUST):** Generate random CSRF state token, store in session/Redis (5min TTL)
- **BR-OAUTH-02 (MUST):** Request scopes: `openid profile email` (Google), `read:user user:email` (GitHub)
- **BR-OAUTH-03 (MUST):** Redirect URI MUST be whitelisted in application.yml

---

### 6. GET `/api/auth/oauth2/callback/{provider}`

**Description:** OAuth callback handler (internal, called by OAuth provider)

**Authorization:** None (public, validates CSRF state)

**Path Parameters:**
- `provider`: `google` | `github`

**Query Parameters:**
```
?code=4/0AY0e-g7X...        // Authorization code from provider
&state=<CSRF_TOKEN>          // MUST match stored state
```

**Response 302 Found:**
```
Location: https://frontend.com/#/auth/callback?access_token=<JWT>&refresh_token=<UUID>&expires_in=900
```

**Error Response (Redirect to frontend with error):**
```
Location: https://frontend.com/#/auth/error?error=invalid_state&message=CSRF%20validation%20failed
```

**Business Rules:**
- **BR-OAUTH-CB-01 (MUST):** Validate state token matches stored value (CSRF protection)
- **BR-OAUTH-CB-02 (MUST):** Exchange authorization code for access token with provider
- **BR-OAUTH-CB-03 (MUST):** Fetch user info from provider (email, name, profile)
- **BR-OAUTH-CB-04 (MUST):** If email exists → Link provider to user, else → Create new user (STUDENT role)
- **BR-OAUTH-CB-05 (MUST):** Store OAuth tokens in `oauth_providers` table (encrypted with AES-256-GCM)
- **BR-OAUTH-CB-06 (MUST):** Generate SAMT JWT + refresh token
- **BR-OAUTH-CB-07 (MUST):** Redirect to frontend with tokens in URL fragment (not query params for security)
- **BR-OAUTH-CB-08 (SHOULD):** If GitHub OAuth → Auto-update `users.github_username` if NULL

---

### 7. POST `/api/users/me/oauth/link`

**Description:** Link OAuth provider to existing account

**Authorization:** Bearer Token (MUST)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body:**
```json
{
  "provider": "GOOGLE",                  // MUST: GOOGLE | GITHUB
  "authorizationCode": "4/0AY0e-g7X..."  // MUST: From OAuth consent flow
}
```

**Response 200 OK:**
```json
{
  "message": "Google account linked successfully",
  "provider": "GOOGLE",
  "linkedAt": "2026-01-30T10:30:00Z"
}
```

**Error Responses:**

```json
// 400 - Email mismatch
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "OAuth email does not match account email"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 409 - Provider already linked
{
  "error": {
    "code": "CONFLICT",
    "message": "Provider already linked to this account"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-OAUTH-LINK-01 (MUST):** OAuth email MUST match current user email (400 if mismatch)
- **BR-OAUTH-LINK-02 (MUST):** Cannot link if provider already linked to this user (409)
- **BR-OAUTH-LINK-03 (MUST):** User can have multiple providers (Google + GitHub both linked)
- **BR-OAUTH-LINK-04 (MUST):** Encrypt OAuth tokens with AES-256-GCM before storage
- **BR-OAUTH-LINK-05 (MUST):** Audit log action: `OAUTH_LINK_ACCOUNT`

---

### 8. DELETE `/api/users/me/oauth/{provider}`

**Description:** Unlink OAuth provider from account

**Authorization:** Bearer Token (MUST)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `provider`: `google` | `github`

**Response 200 OK:**
```json
{
  "message": "Google account unlinked successfully"
}
```

**Error Responses:**

```json
// 400 - Last login method
{
  "error": {
    "code": "INVALID_REQUEST",
    "message": "Cannot unlink: no other login method available"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}

// 404 - Provider not linked
{
  "error": {
    "code": "USER_NOT_FOUND",
    "message": "Provider not linked to this account"
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Business Rules:**
- **BR-OAUTH-UNLINK-01 (MUST):** Cannot unlink if only login method (check: no password OR no other OAuth)
- **BR-OAUTH-UNLINK-02 (MUST):** Delete row from `oauth_providers` table
- **BR-OAUTH-UNLINK-03 (MUST):** Audit log action: `OAUTH_UNLINK_ACCOUNT`

---

## Admin Endpoints

### 9. POST `/api/admin/users`

**Description:** Create user account with any role (admin only)

**Authorization:** Bearer Token (ROLE_ADMIN)

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

**Authorization:** Bearer Token (ROLE_ADMIN)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: UUID of target user

**Response 200 OK:**
```json
{
  "message": "User deleted successfully",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
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

**Authorization:** Bearer Token (ROLE_ADMIN)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: UUID of target user

**Response 200 OK:**
```json
{
  "message": "User restored successfully",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
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

**Authorization:** Bearer Token (ROLE_ADMIN)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: UUID of target user

**Query Parameters:**
```
?reason=Suspicious%20activity  // Optional: Reason for locking
```

**Response 200 OK:**
```json
{
  "message": "User locked successfully",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
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

**Authorization:** Bearer Token (ROLE_ADMIN)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Path Parameters:**
- `userId`: UUID of target user

**Response 200 OK:**
```json
{
  "message": "User unlocked successfully",
  "userId": "550e8400-e29b-41d4-a716-446655440000"
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

**Authorization:** Bearer Token (ROLE_ADMIN)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Path Parameters:**
- `userId`: UUID of target user

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
    "id": "550e8400-e29b-41d4-a716-446655440000",
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

## Rate Limiting (Production Only)

### Rate Limit Headers

All responses **SHOULD** include rate limit headers:

```
X-RateLimit-Limit: 5           // Max requests in window
X-RateLimit-Remaining: 3       // Requests remaining
X-RateLimit-Reset: 1706612400  // Unix timestamp when limit resets
```

### Rate Limit Error Response

**429 Too Many Requests:**
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Rate limit exceeded. Try again in 300 seconds.",
    "retryAfter": 300  // seconds
  },
  "timestamp": "2026-01-30T10:30:00Z"
}
```

### Recommended Limits

| Endpoint | Limit | Window | Key |
|----------|-------|--------|-----|
| `POST /api/auth/register` | 5 requests | 1 hour | IP address |
| `POST /api/auth/login` | 5 requests | 5 minutes | IP address |
| `POST /api/auth/refresh` | 20 requests | 15 minutes | User ID |
| `POST /api/auth/logout` | 10 requests | 1 minute | User ID |
| OAuth endpoints | 10 requests | 5 minutes | IP address |
| Admin endpoints | 100 requests | 1 minute | User ID |

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

**Exposed Headers:**
```
X-RateLimit-Limit, X-RateLimit-Remaining, X-RateLimit-Reset
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
  "sub": "550e8400-e29b-41d4-a716-446655440000",  // User ID
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
| `OAUTH_LINK_ACCOUNT` | OAuth linked | User | provider, oauth_email |
| `OAUTH_UNLINK_ACCOUNT` | OAuth unlinked | User | provider |
| `SOFT_DELETE` | User soft deleted | User | target_user_id, admin_id |
| `RESTORE` | User restored | User | target_user_id, admin_id |
| `ACCOUNT_LOCKED` | User locked | User | target_user_id, admin_id, reason |
| `ACCOUNT_UNLOCKED` | User unlocked | User | target_user_id, admin_id |
| `MAP_EXTERNAL_ACCOUNTS` | External accounts mapped | User | target_user_id, admin_id, old_value, new_value |
| `RATE_LIMIT_EXCEEDED` | Rate limit hit | RateLimit | endpoint, ip_address, user_id |
| `TOKEN_REUSE_DETECTED` | Revoked token reused | RefreshToken | user_id, token_id, ip_address |

---

## Status

✅ **COMPLETE** - Ready for implementation

**Next Steps:**
1. Implement controllers with exact endpoints
2. Implement service layer with business rules
3. Implement error handling with standard error codes
4. Implement rate limiting (optional for Phase 1)
5. Implement audit logging
6. Add integration tests for all endpoints
