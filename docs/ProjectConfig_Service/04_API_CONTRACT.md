# API CONTRACT – PROJECT CONFIG SERVICE

## Common Error Response Format

```json
{
  "code": "ERROR_CODE",
  "message": "Human readable message",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

### HTTP Status ↔ Error Code Mapping

| HTTP Status | Error Codes |
|-------------|-------------|
| 400 | BAD_REQUEST, INVALID_URL_FORMAT, INVALID_TOKEN_FORMAT |
| 401 | UNAUTHORIZED |
| 403 | FORBIDDEN |
| 404 | CONFIG_NOT_FOUND, GROUP_NOT_FOUND |
| 409 | CONFIG_ALREADY_EXISTS |
| 422 | CONNECTION_FAILED, INVALID_CREDENTIALS |
| 500 | ENCRYPTION_ERROR, INTERNAL_SERVER_ERROR |

---

## 1. Create Project Config

**Endpoint:** `POST /api/project-configs`  
**Security:** JWT (TEAM_LEADER, ADMIN)

### Request

```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/organization/repository",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

### Response

**Status:** `201 CREATED`

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3x***...",
  "githubRepoUrl": "https://github.com/organization/repository",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T10:00:00Z"
}
```

### Error Codes

- `400 BAD_REQUEST` - Missing required fields
- `400 INVALID_URL_FORMAT` - URL không đúng format
- `400 INVALID_TOKEN_FORMAT` - Token không đúng format
- `403 FORBIDDEN` - User không phải leader của group
- `404 GROUP_NOT_FOUND` - Group không tồn tại
- `409 CONFIG_ALREADY_EXISTS` - Group đã có config

---

## 2. Get Project Config by ID

**Endpoint:** `GET /api/project-configs/{configId}`  
**Security:** JWT (TEAM_LEADER, LECTURER, ADMIN)

### Path Parameters

- `configId` (UUID, required) - ID của config

### Response

**Status:** `200 OK`

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "groupName": "SE1705-G1",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3x***...",
  "githubRepoUrl": "https://github.com/organization/repository",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T11:30:00Z"
}
```

### Error Codes

- `403 FORBIDDEN` - Không có quyền xem config này
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

## 3. Get Project Config by Group

**Endpoint:** `GET /api/project-configs/group/{groupId}`  
**Security:** JWT (TEAM_LEADER, LECTURER, ADMIN)

### Path Parameters

- `groupId` (UUID, required) - ID của group

### Response

**Status:** `200 OK`

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "groupName": "SE1705-G1",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3x***...",
  "githubRepoUrl": "https://github.com/organization/repository",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T11:30:00Z"
}
```

### Error Codes

- `403 FORBIDDEN` - Không có quyền xem config của group này
- `404 CONFIG_NOT_FOUND` - Group chưa có config

---

## 4. Update Project Config

**Endpoint:** `PUT /api/project-configs/{configId}`  
**Security:** JWT (TEAM_LEADER, ADMIN)

### Path Parameters

- `configId` (UUID, required) - ID của config

### Request

**Note:** Tất cả fields đều optional. Chỉ gửi field cần update.

```json
{
  "jiraHostUrl": "https://newproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T9999999999newtoken",
  "githubRepoUrl": "https://github.com/neworg/newrepo",
  "githubToken": "ghp_newtokenxyz123abc456def789"
}
```

**Partial Update Example:**

```json
{
  "jiraApiToken": "ATATT3xFfGF0T9999999999newtoken"
}
```

### Response

**Status:** `200 OK`

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "groupName": "SE1705-G1",
  "jiraHostUrl": "https://newproject.atlassian.net",
  "jiraApiToken": "ATATT3x***...",
  "githubRepoUrl": "https://github.com/neworg/newrepo",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T14:20:00Z"
}
```

### Error Codes

- `400 INVALID_URL_FORMAT` - URL không đúng format
- `400 INVALID_TOKEN_FORMAT` - Token không đúng format
- `403 FORBIDDEN` - Không có quyền update config này
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

## 5. Delete Project Config

**Endpoint:** `DELETE /api/project-configs/{configId}`  
**Security:** JWT (TEAM_LEADER, ADMIN)

### Path Parameters

- `configId` (UUID, required) - ID của config

### Response

**Status:** `204 NO_CONTENT`

### Error Codes

- `403 FORBIDDEN` - Không có quyền xóa config này
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

## 6. Verify Config Connection (Existing Config)

**Endpoint:** `POST /api/project-configs/{configId}/verify`  
**Security:** JWT (TEAM_LEADER, ADMIN)

### Path Parameters

- `configId` (UUID, required) - ID của config cần verify

### Response

**Status:** `200 OK`

#### Success Response

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraConnection": {
    "status": "SUCCESS",
    "message": "Connected to Jira successfully",
    "projectKey": "SWP",
    "projectName": "Software Project 391",
    "responseTime": 245
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "organization/repository",
    "defaultBranch": "main",
    "isPrivate": true,
    "responseTime": 312
  },
  "overallStatus": "SUCCESS",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```

#### Partial Failure Response

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraConnection": {
    "status": "FAILED",
    "message": "Authentication failed: Invalid API token or insufficient permissions",
    "errorCode": "JIRA_AUTH_FAILED"
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "organization/repository",
    "defaultBranch": "main",
    "isPrivate": true,
    "responseTime": 298
  },
  "overallStatus": "FAILED",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```

### Error Codes

- `403 FORBIDDEN` - Không có quyền verify config này
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

## 7. Verify Credentials (Pre-Create)

**Endpoint:** `POST /api/project-configs/verify`  
**Security:** JWT (TEAM_LEADER, ADMIN)

### Request

```json
{
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/organization/repository",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

### Response

**Status:** `200 OK`

#### Success Response

```json
{
  "jiraConnection": {
    "status": "SUCCESS",
    "message": "Connected to Jira successfully",
    "projectKey": "SWP",
    "projectName": "Software Project 391",
    "responseTime": 234
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "organization/repository",
    "defaultBranch": "main",
    "isPrivate": true,
    "responseTime": 289
  },
  "overallStatus": "SUCCESS",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```

#### Complete Failure Response

```json
{
  "jiraConnection": {
    "status": "FAILED",
    "message": "Connection timeout: Unable to reach Jira server",
    "errorCode": "JIRA_CONNECTION_TIMEOUT"
  },
  "githubConnection": {
    "status": "FAILED",
    "message": "Repository not found or insufficient permissions",
    "errorCode": "GITHUB_REPO_NOT_FOUND"
  },
  "overallStatus": "FAILED",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```

### Error Codes

- `400 BAD_REQUEST` - Missing required fields
- `400 INVALID_URL_FORMAT` - URL không đúng format
- `400 INVALID_TOKEN_FORMAT` - Token không đúng format

---

## 8. Get Decrypted Tokens (Internal)

**Endpoint:** `GET /internal/project-configs/{configId}/tokens`  
**Security:** Service-to-Service Authentication

**Note:** Endpoint này chỉ accessible từ internal network.

### Path Parameters

- `configId` (UUID, required) - ID của config

### Request Headers

```
X-Internal-Service-Key: [service-api-key]
X-Service-Name: sync-service
```

### Response

**Status:** `200 OK`

```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/organization/repository",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

### Error Codes

- `401 UNAUTHORIZED` - Service authentication failed
- `403 FORBIDDEN` - Service không có quyền truy cập endpoint này
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

## 9. List Configs by Lecturer

**Endpoint:** `GET /api/project-configs/lecturer/me`  
**Security:** JWT (LECTURER)

### Query Parameters

- `semester` (string, optional) - Filter by semester
- `page` (int, default: 0) - Page number
- `size` (int, default: 20) - Page size

### Response

**Status:** `200 OK`

```json
{
  "content": [
    {
      "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
      "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
      "groupName": "SE1705-G1",
      "semester": "Spring2026",
      "jiraHostUrl": "https://project1.atlassian.net",
      "jiraApiToken": "ATATT3x***...",
      "githubRepoUrl": "https://github.com/org1/repo1",
      "githubToken": "ghp_***...",
      "createdAt": "2026-01-15T10:00:00Z"
    },
    {
      "configId": "8d0f7780-8536-51ef-c05d-f18gd2g01bf8",
      "groupId": "4gb96g75-6828-5673-c4gd-3d074g77bgb7",
      "groupName": "AI2024-G5",
      "semester": "Spring2026",
      "jiraHostUrl": "https://project2.atlassian.net",
      "jiraApiToken": "ATATT5y***...",
      "githubRepoUrl": "https://github.com/org2/repo2",
      "githubToken": "ghp_***...",
      "createdAt": "2026-01-18T14:30:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 2,
  "totalPages": 1
}
```

---

## 10. Validation Error Response

### Example: Missing Required Fields

**Status:** `400 BAD_REQUEST`

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "groupId",
      "message": "Group ID is required"
    },
    {
      "field": "jiraApiToken",
      "message": "Jira API token is required"
    }
  ]
}
```

### Example: Invalid Format

**Status:** `400 BAD_REQUEST`

```json
{
  "code": "INVALID_URL_FORMAT",
  "message": "Jira host URL must be a valid Atlassian URL (*.atlassian.net)",
  "timestamp": "2026-01-29T10:00:00Z",
  "field": "jiraHostUrl",
  "providedValue": "https://example.com"
}
```

---

## 11. Connection Status Enum

### Jira Connection Status

| Status | Description |
|--------|-------------|
| SUCCESS | Kết nối thành công |
| FAILED | Kết nối thất bại |
| TIMEOUT | Connection timeout |

### GitHub Connection Status

| Status | Description |
|--------|-------------|
| SUCCESS | Kết nối thành công |
| FAILED | Kết nối thất bại |
| TIMEOUT | Connection timeout |

### Overall Status

| Status | Condition |
|--------|-----------|
| SUCCESS | Cả Jira và GitHub đều SUCCESS |
| PARTIAL | 1 trong 2 SUCCESS |
| FAILED | Cả 2 đều FAILED |

---

## 12. Rate Limiting

- **Per User:** 100 requests / minute
- **Per IP:** 200 requests / minute
- **Verify Endpoint:** 10 requests / minute (để tránh spam verify)

**Response Header:**
```
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 95
X-RateLimit-Reset: 1643457600
```

**Rate Limit Exceeded:**

**Status:** `429 TOO_MANY_REQUESTS`

```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded. Please try again later.",
  "timestamp": "2026-01-29T10:00:00Z",
  "retryAfter": 60
}
```
