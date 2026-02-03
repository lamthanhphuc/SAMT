# Project Config Service - API Contract

**Service:** Project Config Service  
**Version:** 1.0  
**Base URL:** `/api`  
**Generated:** 2026-02-02

---

## Standard Response Formats

### Success Response

```json
{
  "data": { ... },
  "timestamp": "2026-02-02T10:30:00Z"
}
```

### Error Response

**MUST** follow this format (consistent with Identity Service):

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid Jira host URL format",
    "field": "jiraHostUrl",
    "details": { ... }
  },
  "timestamp": "2026-02-02T10:30:00Z"
}
```

### Standard Error Codes

| HTTP Status | Error Code | Usage |
|-------------|------------|-------|
| 400 | `VALIDATION_ERROR` | Input validation failed |
| 400 | `INVALID_STATE_TRANSITION` | State machine violation |
| 400 | `INVALID_REQUEST` | Generic bad request |
| 401 | `UNAUTHORIZED` | No token or invalid token |
| 403 | `FORBIDDEN` | Valid token but insufficient permissions |
| 404 | `CONFIG_NOT_FOUND` | Config ID doesn't exist |
| 404 | `GROUP_NOT_FOUND` | Group ID doesn't exist (from User-Group Service) |
| 409 | `CONFIG_ALREADY_EXISTS` | Group already has a config |
| 409 | `CONFLICT` | Generic conflict (e.g., concurrent update) |
| 500 | `INTERNAL_SERVER_ERROR` | Unexpected server error |
| 503 | `SERVICE_UNAVAILABLE` | Dependency service unavailable |
| 504 | `GATEWAY_TIMEOUT` | External API timeout (Jira/GitHub) |

---

## Public API Endpoints (REST)

### 1. POST `/api/project-configs`

**Description:** Create new configuration for a group (LEADER only)

**Authorization:** Bearer Token (ROLE_STUDENT with LEADER role in target group OR ROLE_ADMIN)

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json
```

**Request Body:**
```json
{
  "groupId": "550e8400-e29b-41d4-a716-446655440000",
  "jiraHostUrl": "https://your-domain.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/username/repo-name",
  "githubToken": "ghp_1234567890abcdefghijklmnopqrstuvwxyz"
}
```

**Validation Rules:**

| Field | Constraints | Regex/Pattern |
|-------|-------------|---------------|
| `groupId` | MUST: Valid UUID, NOT NULL | UUID format |
| `jiraHostUrl` | MUST: Valid URL, ends with `.atlassian.net` or custom domain, max 255 chars | `https?://[a-zA-Z0-9.-]+\.(atlassian\.net\|jira\.com)` |
| `jiraApiToken` | MUST: 100-500 chars, starts with `ATATT` (Jira Cloud API token format) | `^ATATT[A-Za-z0-9+/=_-]{100,500}$` |
| `githubRepoUrl` | MUST: Valid GitHub URL format `https://github.com/{owner}/{repo}` | `https://github\.com/[\w-]+/[\w-]+` |
| `githubToken` | MUST: Starts with `ghp_` (GitHub Personal Access Token), 40-255 chars | `^ghp_[A-Za-z0-9]{36,}$` |

**Response 201 Created:**
```json
{
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": "550e8400-e29b-41d4-a716-446655440000",
    "jiraHostUrl": "https://your-domain.atlassian.net",
    "jiraApiToken": "***ab12",
    "githubRepoUrl": "https://github.com/username/repo-name",
    "githubToken": "ghp_***xyz9",
    "state": "DRAFT",
    "lastVerifiedAt": null,
    "invalidReason": null,
    "createdAt": "2026-02-02T10:30:00Z",
    "updatedAt": "2026-02-02T10:30:00Z"
  },
  "timestamp": "2026-02-02T10:30:00Z"
}
```

**Business Rules:**
- **BR-CONFIG-01 (MUST):** Group MUST exist and not be soft-deleted (verify via `GET /api/groups/{groupId}` to User-Group Service)
- **BR-CONFIG-02 (MUST):** Current user MUST be LEADER of the group OR have ADMIN role
- **BR-CONFIG-03 (MUST):** Group MUST NOT already have a config (UNIQUE constraint on `group_id`)
- **BR-CONFIG-04 (MUST):** Tokens encrypted with AES-256-GCM before storage
- **BR-CONFIG-05 (MUST):** Initial state = `DRAFT` (verification required separately)

**Error Responses:**

```json
// 400 - Invalid Jira URL
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid Jira host URL format",
    "field": "jiraHostUrl"
  },
  "timestamp": "2026-02-02T10:30:00Z"
}

// 403 - Not group leader
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Only group leader can create config"
  },
  "timestamp": "2026-02-02T10:30:00Z"
}

// 404 - Group not found
{
  "error": {
    "code": "GROUP_NOT_FOUND",
    "message": "Group not found or deleted"
  },
  "timestamp": "2026-02-02T10:30:00Z"
}

// 409 - Config already exists
{
  "error": {
    "code": "CONFIG_ALREADY_EXISTS",
    "message": "Group already has a configuration"
  },
  "timestamp": "2026-02-02T10:30:00Z"
}
```

---

### 2. GET `/api/project-configs/{id}`

**Description:** Get configuration details (tokens masked)

**Authorization:** Bearer Token (AUTHENTICATED)

**Path Parameters:**
- `id` (UUID): Configuration ID

**Response 200 OK:**
```json
{
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": "550e8400-e29b-41d4-a716-446655440000",
    "jiraHostUrl": "https://your-domain.atlassian.net",
    "jiraApiToken": "***ab12",
    "githubRepoUrl": "https://github.com/username/repo-name",
    "githubToken": "ghp_***xyz9",
    "state": "VERIFIED",
    "lastVerifiedAt": "2026-02-02T11:00:00Z",
    "invalidReason": null,
    "createdAt": "2026-02-02T10:30:00Z",
    "updatedAt": "2026-02-02T11:00:00Z"
  },
  "timestamp": "2026-02-02T12:00:00Z"
}
```

**Token Masking Rules:**
- Jira token: Show first 3 chars `***` + last 4 chars (e.g., `***ab12`)
- GitHub token: Show prefix `ghp_***` + last 4 chars (e.g., `ghp_***xyz9`)

**Authorization Rules:**
- **ADMIN:** Can view ANY config
- **LECTURER:** Can view configs of supervised groups (call User-Group Service to verify)
- **STUDENT (LEADER):** Can view own group's config only
- **STUDENT (MEMBER):** Cannot view configs (403 FORBIDDEN)

**Error Responses:**

```json
// 404 - Config not found
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration not found"
  },
  "timestamp": "2026-02-02T12:00:00Z"
}

// 403 - Not authorized
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Not authorized to view this configuration"
  },
  "timestamp": "2026-02-02T12:00:00Z"
}
```

---

### 3. PUT `/api/project-configs/{id}`

**Description:** Update configuration (LEADER or ADMIN only)

**Authorization:** Bearer Token (ROLE_STUDENT with LEADER role OR ROLE_ADMIN)

**Path Parameters:**
- `id` (UUID): Configuration ID

**Request Body:**
```json
{
  "jiraHostUrl": "https://new-domain.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/new-owner/new-repo",
  "githubToken": "ghp_9876543210zyxwvutsrqponmlkjihgfedcba"
}
```

**All fields are OPTIONAL** (partial update supported)

**Response 200 OK:**
```json
{
  "data": {
    "id": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": "550e8400-e29b-41d4-a716-446655440000",
    "jiraHostUrl": "https://new-domain.atlassian.net",
    "jiraApiToken": "***cd34",
    "githubRepoUrl": "https://github.com/new-owner/new-repo",
    "githubToken": "ghp_***ba98",
    "state": "DRAFT",
    "lastVerifiedAt": null,
    "invalidReason": "Configuration updated, verification required",
    "createdAt": "2026-02-02T10:30:00Z",
    "updatedAt": "2026-02-02T13:00:00Z"
  },
  "timestamp": "2026-02-02T13:00:00Z"
}
```

**Business Rules:**
- **BR-UPDATE-01 (MUST):** If `jiraApiToken`, `githubToken`, `jiraHostUrl`, or `githubRepoUrl` updated → state transitions to `DRAFT`
- **BR-UPDATE-02 (MUST):** If state transitions to `DRAFT` → set `lastVerifiedAt = NULL`, `invalidReason = "Configuration updated, verification required"`
- **BR-UPDATE-03 (MUST):** Current user MUST be LEADER of the group OR ADMIN
- **BR-UPDATE-04 (MUST):** Cannot update soft-deleted config (404 NOT FOUND)

**State Transition Logic:**

| Updated Fields | State Before | State After | lastVerifiedAt | invalidReason |
|----------------|--------------|-------------|----------------|---------------|
| Tokens/URLs | VERIFIED | DRAFT | NULL | "Configuration updated, verification required" |
| Tokens/URLs | INVALID | DRAFT | NULL | "Configuration updated, verification required" |
| Tokens/URLs | DRAFT | DRAFT | NULL | Unchanged or updated reason |

**Error Responses:**

```json
// 404 - Config not found or deleted
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration not found or deleted"
  },
  "timestamp": "2026-02-02T13:00:00Z"
}

// 403 - Not authorized
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Only group leader can update config"
  },
  "timestamp": "2026-02-02T13:00:00Z"
}
```

---

### 4. DELETE `/api/project-configs/{id}`

**Description:** Soft delete configuration (LEADER or ADMIN only)

**Authorization:** Bearer Token (ROLE_STUDENT with LEADER role OR ROLE_ADMIN)

**Path Parameters:**
- `id` (UUID): Configuration ID

**Response 200 OK:**
```json
{
  "data": {
    "message": "Configuration deleted successfully",
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "deletedAt": "2026-02-02T14:00:00Z",
    "retentionDays": 90
  },
  "timestamp": "2026-02-02T14:00:00Z"
}
```

**Business Rules:**
- **BR-DELETE-01 (MUST):** Soft delete only (set `deleted_at = NOW()`, `deleted_by = currentUserId`)
- **BR-DELETE-02 (MUST):** State transitions to `DELETED`
- **BR-DELETE-03 (MUST):** Retention period: 90 days before hard delete (per Soft Delete Policy)
- **BR-DELETE-04 (MUST):** Current user MUST be LEADER of the group OR ADMIN
- **BR-DELETE-05 (MUST):** Idempotent (deleting already deleted config returns 200 OK)

**Error Responses:**

```json
// 404 - Config not found
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration not found"
  },
  "timestamp": "2026-02-02T14:00:00Z"
}

// 403 - Not authorized
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Only group leader can delete config"
  },
  "timestamp": "2026-02-02T14:00:00Z"
}
```

---

### 5. POST `/api/project-configs/{id}/verify`

**Description:** Test connection to Jira and GitHub APIs (LEADER or ADMIN only)

**Authorization:** Bearer Token (ROLE_STUDENT with LEADER role OR ROLE_ADMIN)

**Path Parameters:**
- `id` (UUID): Configuration ID

**Response 200 OK (Success):**
```json
{
  "data": {
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "state": "VERIFIED",
    "verificationResults": {
      "jira": {
        "status": "SUCCESS",
        "message": "Connected successfully to Jira",
        "testedAt": "2026-02-02T15:00:00Z",
        "userEmail": "team-leader@university.edu"
      },
      "github": {
        "status": "SUCCESS",
        "message": "Connected successfully to GitHub",
        "testedAt": "2026-02-02T15:00:01Z",
        "repoName": "username/repo-name",
        "hasWriteAccess": true
      }
    },
    "lastVerifiedAt": "2026-02-02T15:00:01Z"
  },
  "timestamp": "2026-02-02T15:00:01Z"
}
```

**Response 200 OK (Partial Failure):**
```json
{
  "data": {
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "state": "INVALID",
    "verificationResults": {
      "jira": {
        "status": "SUCCESS",
        "message": "Connected successfully to Jira",
        "testedAt": "2026-02-02T15:00:00Z",
        "userEmail": "team-leader@university.edu"
      },
      "github": {
        "status": "FAILED",
        "message": "Invalid token or repository not found",
        "error": "401 Unauthorized",
        "testedAt": "2026-02-02T15:00:01Z"
      }
    },
    "lastVerifiedAt": null,
    "invalidReason": "GitHub: Invalid token or repository not found"
  },
  "timestamp": "2026-02-02T15:00:01Z"
}
```

**Verification Steps:**

1. **Decrypt tokens** from database
2. **Test Jira API:**
   - Endpoint: `GET https://{jiraHostUrl}/rest/api/3/myself`
   - Headers: `Authorization: Bearer {jiraApiToken}`
   - Timeout: 10 seconds
   - Success: 200 OK, response contains user email
   - Failure: Non-200 status or timeout

3. **Test GitHub API:**
   - Endpoint: `GET https://api.github.com/repos/{owner}/{repo}`
   - Headers: `Authorization: Bearer {githubToken}`
   - Timeout: 10 seconds
   - Success: 200 OK, check `permissions.push` = true
   - Failure: Non-200 status or timeout

4. **Update state:**
   - Both SUCCESS → state = `VERIFIED`, lastVerifiedAt = NOW()
   - Any FAILED → state = `INVALID`, lastVerifiedAt = NULL, invalidReason = error message

**Business Rules:**
- **BR-VERIFY-01 (MUST):** Verification runs in SEPARATE transaction (no rollback on failure)
- **BR-VERIFY-02 (MUST):** Total timeout: 20 seconds (10s per API)
- **BR-VERIFY-03 (MUST):** Cannot verify soft-deleted config (404 NOT FOUND)
- **BR-VERIFY-04 (MUST):** Current user MUST be LEADER of the group OR ADMIN

**Error Responses:**

```json
// 404 - Config not found or deleted
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration not found or deleted"
  },
  "timestamp": "2026-02-02T15:00:00Z"
}

// 403 - Not authorized
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Only group leader can verify config"
  },
  "timestamp": "2026-02-02T15:00:00Z"
}

// 504 - Timeout
{
  "error": {
    "code": "GATEWAY_TIMEOUT",
    "message": "Verification timeout (Jira/GitHub APIs not responding)"
  },
  "timestamp": "2026-02-02T15:00:20Z"
}
```

---

### 6. POST `/api/admin/project-configs/{id}/restore`

**Description:** Restore soft-deleted configuration (ADMIN only)

**Authorization:** Bearer Token (ROLE_ADMIN)

**Path Parameters:**
- `id` (UUID): Configuration ID

**Response 200 OK:**
```json
{
  "data": {
    "message": "Configuration restored successfully",
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "restoredAt": "2026-02-02T16:00:00Z",
    "state": "DRAFT"
  },
  "timestamp": "2026-02-02T16:00:00Z"
}
```

**Business Rules:**
- **BR-RESTORE-01 (MUST):** Only ADMIN can restore
- **BR-RESTORE-02 (MUST):** Set `deleted_at = NULL`, `deleted_by = NULL`
- **BR-RESTORE-03 (MUST):** State transitions to `DRAFT` (requires re-verification)
- **BR-RESTORE-04 (MUST):** Cannot restore if retention period expired (90 days - hard deleted)

**Error Responses:**

```json
// 404 - Config not found (hard deleted)
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration permanently deleted"
  },
  "timestamp": "2026-02-02T16:00:00Z"
}

// 403 - Not admin
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Only admin can restore deleted configurations"
  },
  "timestamp": "2026-02-02T16:00:00Z"
}
```

---

## Internal API Endpoints (Service-to-Service)

### 7. GET `/internal/project-configs/{id}/tokens`

**Description:** Get decrypted tokens for Sync Service (Internal API)

**Authorization:** Service-to-Service Authentication

**Request Headers:**
```
X-Service-Name: sync-service
X-Service-Key: <INTERNAL_SERVICE_KEY from env>
```

**Path Parameters:**
- `id` (UUID): Configuration ID

**Response 200 OK:**
```json
{
  "data": {
    "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
    "groupId": "550e8400-e29b-41d4-a716-446655440000",
    "jiraHostUrl": "https://your-domain.atlassian.net",
    "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
    "githubRepoUrl": "https://github.com/username/repo-name",
    "githubToken": "ghp_1234567890abcdefghijklmnopqrstuvwxyz",
    "state": "VERIFIED"
  },
  "timestamp": "2026-02-02T17:00:00Z"
}
```

**Security:**
- **SEC-INTERNAL-01 (MUST):** Validate `X-Service-Name` header = "sync-service"
- **SEC-INTERNAL-02 (MUST):** Validate `X-Service-Key` header matches `INTERNAL_SERVICE_KEY` environment variable
- **SEC-INTERNAL-03 (MUST):** Return decrypted tokens (NO masking)
- **SEC-INTERNAL-04 (MUST):** Only return if state = `VERIFIED` (403 if DRAFT or INVALID)

**Error Responses:**

```json
// 401 - Invalid service key
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid service authentication"
  },
  "timestamp": "2026-02-02T17:00:00Z"
}

// 403 - Config not verified
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Configuration not verified (state: DRAFT)"
  },
  "timestamp": "2026-02-02T17:00:00Z"
}

// 404 - Config not found or deleted
{
  "error": {
    "code": "CONFIG_NOT_FOUND",
    "message": "Configuration not found or deleted"
  },
  "timestamp": "2026-02-02T17:00:00Z"
}
```

---

## Error Handling Strategy

### External API Failures

**Scenario:** Jira/GitHub API timeout or 5xx error during verification

**Response:**
```json
{
  "data": {
    "configId": "...",
    "state": "INVALID",
    "verificationResults": {
      "jira": {
        "status": "FAILED",
        "message": "Jira API timeout",
        "error": "Read timed out after 10000ms"
      },
      "github": {
        "status": "PENDING",
        "message": "Not tested due to previous failure"
      }
    },
    "invalidReason": "Jira API timeout"
  }
}
```

**HTTP Status:** 200 OK (verification endpoint itself succeeded, but external API failed)

---

### Dependency Service Failures

**Scenario:** User-Group Service unavailable during group validation

**Response:**
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "User-Group Service unavailable, please try again later"
  },
  "timestamp": "2026-02-02T18:00:00Z"
}
```

**HTTP Status:** 503 Service Unavailable

**Retry Strategy:** Client should retry with exponential backoff

---

## Rate Limiting (Future Enhancement)

**NOT IMPLEMENTED** in initial version - known tech debt

**Proposed:**
- Verification endpoint: 5 requests per minute per user
- CRUD endpoints: 100 requests per minute per user
- Internal API: No rate limit (trusted service)

---

**Next Document:** Database Design
