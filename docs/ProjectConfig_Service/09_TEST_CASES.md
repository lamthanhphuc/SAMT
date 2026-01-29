# TEST CASES – PROJECT CONFIG SERVICE

## Test Case Overview

| Category | Test Count | Priority |
|----------|------------|----------|
| Create Config (UC30) | 12 | HIGH |
| Get Config by ID (UC31) | 6 | HIGH |
| Get Config by Group (UC32) | 6 | HIGH |
| Update Config (UC33) | 10 | HIGH |
| Delete Config (UC34) | 6 | MEDIUM |
| Verify Existing Config (UC35) | 8 | HIGH |
| Verify Pre-Create | 6 | MEDIUM |
| Internal Token Endpoint | 5 | HIGH |
| List Configs (Lecturer) | 4 | MEDIUM |
| Authorization & Security | 10 | CRITICAL |
| Validation Rules | 15 | HIGH |
| Business Rules | 12 | HIGH |
| **TOTAL** | **100** | - |

---

## 1. CREATE PROJECT CONFIG (UC30)

### TC-PC-001: Tạo config thành công với dữ liệu hợp lệ

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-001 |
| **Tên Test Case** | Create config successfully with valid data |
| **Mô tả** | Team Leader tạo config cho nhóm của mình với tất cả fields hợp lệ |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User đã login với role TEAM_LEADER
- Group G1 (UUID: `3fa85f64-5717-4562-b3fc-2c963f66afa6`) đã tồn tại
- User là leader của group G1
- Group G1 chưa có config

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs`
2. Header: `Authorization: Bearer <valid_jwt_token>`
3. Body: Valid config data (xem dữ liệu test)

**Dữ liệu test:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Response body chứa:
  - `configId`: UUID mới
  - `groupId`: Trùng với request
  - `jiraHostUrl`: Trùng với request
  - `jiraApiToken`: Masked (`ATATT3x***...`)
  - `githubRepoUrl`: Trùng với request
  - `githubToken`: Masked (`ghp_***...`)
  - `createdAt`: Timestamp hiện tại
  - `updatedAt`: Timestamp hiện tại
- Database: 1 record mới trong `project_configs` table
- Tokens được encrypt trong database (AES-256-GCM)
- Audit log: Event `CONFIG_CREATED` được ghi

---

### TC-PC-002: Tạo config thất bại - Missing required fields

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-002 |
| **Tên Test Case** | Create config fails - Missing required fields |
| **Mô tả** | Tạo config với request thiếu các trường bắt buộc |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Điều kiện tiên quyết:**
- User đã login với role TEAM_LEADER

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs`
2. Header: `Authorization: Bearer <valid_jwt_token>`
3. Body: Request thiếu fields (xem dữ liệu test)

**Dữ liệu test:**
```json
{
  "groupId": null,
  "jiraHostUrl": "",
  "githubRepoUrl": "https://github.com/testorg/testrepo"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response body:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "groupId",
      "message": "Group ID is required",
      "rejectedValue": null
    },
    {
      "field": "jiraHostUrl",
      "message": "Jira host URL is required",
      "rejectedValue": ""
    },
    {
      "field": "jiraApiToken",
      "message": "Jira API token is required",
      "rejectedValue": null
    },
    {
      "field": "githubToken",
      "message": "GitHub token is required",
      "rejectedValue": null
    }
  ]
}
```
- Database: Không có record mới

---

### TC-PC-003: Tạo config thất bại - Invalid Jira URL format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-003 |
| **Tên Test Case** | Create config fails - Invalid Jira URL format |
| **Mô tả** | Tạo config với Jira URL không đúng format (HTTP instead of HTTPS) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Điều kiện tiên quyết:**
- User đã login với role TEAM_LEADER
- Group G1 tồn tại, user là leader

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs`
2. Body: Request với invalid Jira URL

**Dữ liệu test:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "http://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response body:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "jiraHostUrl",
      "message": "Jira host URL must be a valid Atlassian URL (https://*.atlassian.net)",
      "rejectedValue": "http://testproject.atlassian.net"
    }
  ]
}
```

---

### TC-PC-004: Tạo config thất bại - Invalid GitHub URL format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-004 |
| **Tên Test Case** | Create config fails - Invalid GitHub URL format |
| **Mô tả** | Tạo config với GitHub URL có .git suffix hoặc không phải github.com |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Điều kiện tiên quyết:**
- User đã login với role TEAM_LEADER
- Group G1 tồn tại, user là leader

**Dữ liệu test:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://gitlab.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error message: "GitHub repository URL must be a valid format (https://github.com/owner/repo)"

---

### TC-PC-005: Tạo config thất bại - Invalid Jira token format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-005 |
| **Tên Test Case** | Create config fails - Invalid Jira token format |
| **Mô tả** | Tạo config với Jira token không bắt đầu bằng ATATT |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "invalid_token_format",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Jira API token must start with 'ATATT'"

---

### TC-PC-006: Tạo config thất bại - Invalid GitHub token format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-006 |
| **Tên Test Case** | Create config fails - Invalid GitHub token format |
| **Mô tả** | Tạo config với GitHub token không đúng format (không đủ 44 ký tự) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_short"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "GitHub token must start with 'ghp_' followed by 40 alphanumeric characters"

---

### TC-PC-007: Tạo config thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-007 |
| **Tên Test Case** | Create config fails - Group not found |
| **Mô tả** | Tạo config cho group không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Điều kiện tiên quyết:**
- User đã login với role TEAM_LEADER

**Dữ liệu test:**
```json
{
  "groupId": "99999999-9999-9999-9999-999999999999",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group with ID '99999999-9999-9999-9999-999999999999' not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-PC-008: Tạo config thất bại - User không phải leader của group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-008 |
| **Tên Test Case** | Create config fails - User not team leader |
| **Mô tả** | User cố tạo config cho group mà mình không phải leader |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User A login với role STUDENT (không phải TEAM_LEADER)
- Group G1 tồn tại
- User A là member của group G1 nhưng role = STUDENT

**Dữ liệu test:**
```json
{
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only team leader can manage config",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Audit log: Event `UNAUTHORIZED_ACCESS` được ghi

---

### TC-PC-009: Tạo config thất bại - Config already exists (BR-PC-001)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-009 |
| **Tên Test Case** | Create config fails - Config already exists |
| **Mô tả** | Tạo config thứ 2 cho group đã có config (vi phạm UNIQUE constraint) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User đã login với role TEAM_LEADER
- Group G1 tồn tại
- Group G1 ĐÃ CÓ config (configId: C1)

**Các bước thực hiện:**
1. Gửi POST request để tạo config thứ 2 cho group G1
2. System detect duplicate

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "CONFIG_ALREADY_EXISTS",
  "message": "Project config for group '3fa85f64-5717-4562-b3fc-2c963f66afa6' already exists",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Database: Không có record mới

---

### TC-PC-010: Tạo config thành công bởi ADMIN cho bất kỳ group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-010 |
| **Tên Test Case** | Admin creates config for any group successfully |
| **Mô tả** | ADMIN có thể tạo config cho bất kỳ group nào (không cần là leader) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Group G2 tồn tại
- User KHÔNG PHẢI là member của group G2
- Group G2 chưa có config

**Dữ liệu test:**
```json
{
  "groupId": "4gb96g75-6828-5673-c4gd-3d074g77bgb7",
  "jiraHostUrl": "https://adminproject.atlassian.net",
  "jiraApiToken": "ATATT5yXxYyZz9876543210zyxwvutsrqponml",
  "githubRepoUrl": "https://github.com/adminorg/adminrepo",
  "githubToken": "ghp_xyz987wvu654tsr321qpo098nml765kji432"
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Config được tạo thành công
- Tokens được encrypt và mask trong response

---

### TC-PC-011: Tạo config thất bại - Unauthorized (no JWT token)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-011 |
| **Tên Test Case** | Create config fails - Unauthorized |
| **Mô tả** | Gọi API không có JWT token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Điều kiện tiên quyết:**
- None (no authentication)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs`
2. KHÔNG gửi header Authorization
3. Body: Valid data

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Full authentication is required to access this resource",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-PC-012: Verify encryption trong database

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-012 |
| **Tên Test Case** | Verify tokens are encrypted in database (BR-PC-004) |
| **Mô tả** | Kiểm tra tokens được mã hóa bằng AES-256-GCM trong database |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Encryption |

**Điều kiện tiên quyết:**
- Config C1 đã được tạo thành công (TC-PC-001)

**Các bước thực hiện:**
1. Tạo config với tokens: `ATATT3xFfGF0T1234567890` và `ghp_abc123xyz456def789`
2. Query trực tiếp database: `SELECT jira_api_token, github_token FROM project_configs WHERE config_id = ?`
3. Verify encrypted values

**Kết quả mong đợi:**
- Database value ≠ Plaintext token
- Database value format: Base64(IV + EncryptedData)
- Length > Original token length
- Không thể đọc được plaintext từ database value
- Decrypt using encryption service → trả về original plaintext

---

## 2. GET PROJECT CONFIG BY ID (UC31)

### TC-PC-013: Lấy config thành công bởi Team Leader

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-013 |
| **Tên Test Case** | Get config by ID successfully - Team Leader |
| **Mô tả** | Team Leader lấy config của nhóm mình |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- User là leader của group G1
- Config C1 tồn tại cho group G1

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/{configId}`
2. configId = C1 (UUID)
3. Header: `Authorization: Bearer <valid_jwt_token>`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "groupName": "SE1705-G1",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3x***...",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T10:00:00Z"
}
```
- Tokens đã masked (BR-PC-005)

---

### TC-PC-014: Lấy config thành công bởi Lecturer

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-014 |
| **Tên Test Case** | Get config by ID successfully - Lecturer |
| **Mô tả** | Lecturer lấy config của group mình supervise |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer supervise group G1 (trong `lecturer_groups` table)
- Config C1 tồn tại cho group G1

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/{configId}`
2. configId = C1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa masked tokens
- Lecturer có thể xem nhưng không thể update/delete

---

### TC-PC-015: Lấy config thất bại - Config not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-015 |
| **Tên Test Case** | Get config fails - Config not found |
| **Mô tả** | Lấy config với ID không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi GET request với configId không tồn tại: `99999999-9999-9999-9999-999999999999`

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "CONFIG_NOT_FOUND",
  "message": "Project config with ID '99999999-9999-9999-9999-999999999999' not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-PC-016: Lấy config thất bại - Forbidden (not authorized)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-016 |
| **Tên Test Case** | Get config fails - Forbidden |
| **Mô tả** | User cố lấy config của group không có quyền |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User A login với role TEAM_LEADER
- User A là leader của group G1
- Config C2 tồn tại cho group G2
- User A KHÔNG PHẢI member của group G2

**Các bước thực hiện:**
1. User A gửi GET request đến `/api/project-configs/{configId}`
2. configId = C2 (của group G2)

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "You do not have permission to access this config",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-PC-017: Lấy config thành công bởi ADMIN (any group)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-017 |
| **Tên Test Case** | Admin gets any config successfully |
| **Mô tả** | ADMIN có thể lấy config của bất kỳ group nào |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Config C1 tồn tại cho group G1
- User không phải member của group G1

**Các bước thực hiện:**
1. Admin gửi GET request đến `/api/project-configs/{configId}`
2. configId = C1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa full config với masked tokens

---

### TC-PC-018: Get config không return soft deleted configs

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-018 |
| **Tên Test Case** | Get config excludes soft deleted configs |
| **Mô tả** | Config đã bị soft delete không thể get (BR-PC-012) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Điều kiện tiên quyết:**
- Config C1 đã bị soft delete (deleted_at IS NOT NULL)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/{configId}`
2. configId = C1 (đã deleted)

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "Config not found"
- Soft deleted configs không hiển thị

---

## 3. GET PROJECT CONFIG BY GROUP (UC32)

### TC-PC-019: Lấy config by group thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-019 |
| **Tên Test Case** | Get config by group successfully |
| **Mô tả** | Team Leader lấy config bằng groupId |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- User là leader của group G1
- Group G1 có config C1

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/group/{groupId}`
2. groupId = G1 (UUID)

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa config C1 với masked tokens
- groupName được populate

---

### TC-PC-020: Lấy config by group thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-020 |
| **Tên Test Case** | Get config by group fails - Group not found |
| **Mô tả** | Lấy config cho group không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi GET request với groupId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "Group not found"

---

### TC-PC-021: Lấy config by group thất bại - Config not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-021 |
| **Tên Test Case** | Get config by group fails - Config not found |
| **Mô tả** | Group tồn tại nhưng chưa có config |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Điều kiện tiên quyết:**
- Group G3 tồn tại
- Group G3 CHƯA CÓ config

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/group/{groupId}`
2. groupId = G3

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "CONFIG_NOT_FOUND",
  "message": "Project config for group 'G3' not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-PC-022: Lấy config by group thất bại - Forbidden

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-022 |
| **Tên Test Case** | Get config by group fails - Forbidden |
| **Mô tả** | User cố lấy config của group không có quyền |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User A login với role STUDENT
- Group G2 có config C2
- User A không phải member của group G2

**Các bước thực hiện:**
1. User A gửi GET request đến `/api/project-configs/group/{groupId}`
2. groupId = G2

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "You do not have permission to access this group's config"

---

### TC-PC-023: Lecturer xem config của group supervised

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-023 |
| **Tên Test Case** | Lecturer views config of supervised group |
| **Mô tả** | Lecturer có thể xem config của groups mình supervise |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer supervise group G1 (relationship trong `lecturer_groups`)
- Group G1 có config C1

**Các bước thực hiện:**
1. Lecturer gửi GET request đến `/api/project-configs/group/{groupId}`
2. groupId = G1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa config với masked tokens

---

### TC-PC-024: Lecturer không xem được config của group not supervised

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-024 |
| **Tên Test Case** | Lecturer cannot view config of non-supervised group |
| **Mô tả** | Lecturer không thể xem config của group không supervise |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer KHÔNG supervise group G2
- Group G2 có config C2

**Các bước thực hiện:**
1. Lecturer gửi GET request đến `/api/project-configs/group/{groupId}`
2. groupId = G2

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "You do not supervise this group"

---

## 4. UPDATE PROJECT CONFIG (UC33)

### TC-PC-025: Update config thành công - Full update

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-025 |
| **Tên Test Case** | Update config successfully - Full update |
| **Mô tả** | Team Leader update tất cả fields của config |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- User là leader của group G1
- Config C1 tồn tại cho group G1

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/project-configs/{configId}`
2. configId = C1
3. Body: Full update data

**Dữ liệu test:**
```json
{
  "jiraHostUrl": "https://newproject.atlassian.net",
  "jiraApiToken": "ATATT9xFfGF0T9999999999newtoken",
  "githubRepoUrl": "https://github.com/neworg/newrepo",
  "githubToken": "ghp_newtokenxyz123abc456def789ghi012jkl"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa updated config với masked new tokens
- `updatedAt` timestamp changed
- Database: Old tokens replaced với new encrypted tokens
- Audit log: Event `CONFIG_UPDATED` với changes details

---

### TC-PC-026: Update config thành công - Partial update

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-026 |
| **Tên Test Case** | Update config successfully - Partial update |
| **Mô tả** | Team Leader update chỉ 1 field (jiraApiToken) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Partial Update |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- Config C1 tồn tại

**Dữ liệu test:**
```json
{
  "jiraApiToken": "ATATT5yFfGF0T5555555555partialtoken"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Chỉ `jiraApiToken` changed
- Các fields khác (jiraHostUrl, githubRepoUrl, githubToken) giữ nguyên
- Audit log ghi: `jiraApiToken` changed from `ATATT3x***` to `ATATT5y***`

---

### TC-PC-027: Update config thất bại - Config not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-027 |
| **Tên Test Case** | Update config fails - Config not found |
| **Mô tả** | Update config với ID không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi PUT request với configId không tồn tại
2. Body: Valid update data

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "Config not found"

---

### TC-PC-028: Update config thất bại - Forbidden

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-028 |
| **Tên Test Case** | Update config fails - Forbidden |
| **Mô tả** | User cố update config của group không có quyền |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User A login với role TEAM_LEADER (leader của group G1)
- Config C2 tồn tại cho group G2
- User A không phải member của group G2

**Các bước thực hiện:**
1. User A gửi PUT request đến `/api/project-configs/{configId}`
2. configId = C2
3. Body: Valid update data

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "You do not have permission to update this config"
- Database: No changes

---

### TC-PC-029: Update config thất bại - Invalid URL format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-029 |
| **Tên Test Case** | Update config fails - Invalid URL format |
| **Mô tả** | Update với URL không hợp lệ |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "githubRepoUrl": "https://github.com/org/repo.git"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "GitHub repository URL must be a valid format (https://github.com/owner/repo)"
- Database: No changes

---

### TC-PC-030: Update config với token rotation audit (BR-PC-006)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-030 |
| **Tên Test Case** | Token rotation is audited |
| **Mô tả** | Khi update token, old token preview được log |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Audit |

**Điều kiện tiên quyết:**
- Config C1 tồn tại với jiraApiToken = `ATATT3xFfGF0T1234567890`

**Dữ liệu test:**
```json
{
  "jiraApiToken": "ATATT9xFfGF0T9999999999newtoken"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Audit log entry:
```json
{
  "action": "TOKEN_ROTATED",
  "configId": "...",
  "tokenType": "JIRA_API_TOKEN",
  "oldTokenPreview": "ATATT3x***...",
  "newTokenPreview": "ATATT9x***...",
  "rotatedBy": "user-uuid",
  "rotatedAt": "2026-01-29T15:00:00Z",
  "ipAddress": "192.168.1.100"
}
```

---

### TC-PC-031: Update config thất bại - Concurrent update (BR-PC-016)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-031 |
| **Tên Test Case** | Update config fails - Concurrent update |
| **Mô tả** | 2 users update cùng config đồng thời, detect optimistic lock |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Concurrency |

**Điều kiện tiên quyết:**
- Config C1 tồn tại với version = 1
- User A và User B đều là leaders của group G1

**Các bước thực hiện:**
1. User A lấy config C1 (version = 1)
2. User B lấy config C1 (version = 1)
3. User A update config → Success, version = 2
4. User B update config → Should fail (optimistic lock)

**Kết quả mong đợi:**
- User A: `200 OK`, config updated, version = 2
- User B: `409 CONFLICT`
- Response:
```json
{
  "code": "CONCURRENT_UPDATE",
  "message": "Config was updated by another user. Please refresh and try again.",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-PC-032: Update config bởi ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-032 |
| **Tên Test Case** | Admin updates any config successfully |
| **Mô tả** | ADMIN có thể update config của bất kỳ group nào |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Config C1 tồn tại cho group G1
- User không phải member của group G1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Config updated thành công

---

### TC-PC-033: Update config với empty body

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-033 |
| **Tên Test Case** | Update config with empty body |
| **Mô tả** | Gửi PUT request với body rỗng hoặc {} |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK` (no changes)
- Response: Config không thay đổi
- `updatedAt` không thay đổi

---

### TC-PC-034: Lecturer không thể update config (read-only)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-034 |
| **Tên Test Case** | Lecturer cannot update config |
| **Mô tả** | Lecturer chỉ xem được, không update được config |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer supervise group G1
- Config C1 tồn tại cho group G1

**Các bước thực hiện:**
1. Lecturer gửi PUT request đến `/api/project-configs/{configId}`
2. configId = C1
3. Body: Valid update data

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Lecturers have read-only access to configs"

---

## 5. DELETE PROJECT CONFIG (UC34)

### TC-PC-035: Delete config thành công (soft delete)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-035 |
| **Tên Test Case** | Delete config successfully - Soft delete |
| **Mô tả** | Team Leader soft delete config (BR-PC-012) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- User là leader của group G1
- Config C1 tồn tại cho group G1

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/project-configs/{configId}`
2. configId = C1

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- Database:
  - Record C1 vẫn tồn tại
  - `deleted_at` = current timestamp
  - `deleted_by` = user UUID
  - Tokens vẫn encrypted trong database
- Audit log: Event `CONFIG_DELETED`
- GET /api/project-configs/{C1} → `404 NOT_FOUND`

---

### TC-PC-036: Delete config thất bại - Config not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-036 |
| **Tên Test Case** | Delete config fails - Config not found |
| **Mô tả** | Delete config với ID không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi DELETE request với configId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "Config not found"

---

### TC-PC-037: Delete config thất bại - Forbidden

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-037 |
| **Tên Test Case** | Delete config fails - Forbidden |
| **Mô tả** | User cố delete config của group không có quyền |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User A login với role TEAM_LEADER (leader của group G1)
- Config C2 tồn tại cho group G2
- User A không phải member của group G2

**Các bước thực hiện:**
1. User A gửi DELETE request đến `/api/project-configs/{configId}`
2. configId = C2

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Database: Config C2 không bị xóa

---

### TC-PC-038: Delete config bởi ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-038 |
| **Tên Test Case** | Admin deletes any config successfully |
| **Mô tả** | ADMIN có thể delete config của bất kỳ group nào |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Config C1 tồn tại cho group G1

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- Config C1 bị soft delete

---

### TC-PC-039: Restore deleted config (BR-PC-013)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-039 |
| **Tên Test Case** | Restore deleted config within 30 days |
| **Mô tả** | Config bị xóa có thể restore trong 30 ngày |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Business Rule |

**Điều kiện tiên quyết:**
- Config C1 đã bị soft delete 10 ngày trước
- deleted_at = current_date - 10 days

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs/{configId}/restore`
2. configId = C1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Database:
  - `deleted_at` = NULL
  - `deleted_by` = NULL
- Config C1 có thể GET lại
- Audit log: Event `CONFIG_RESTORED`

---

### TC-PC-040: Restore deleted config thất bại - Expired (>30 days)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-040 |
| **Tên Test Case** | Restore deleted config fails - Expired |
| **Mô tả** | Config đã xóa quá 30 ngày không thể restore |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Config C1 đã bị soft delete 40 ngày trước
- deleted_at = current_date - 40 days

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs/{configId}/restore`
2. configId = C1

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "CONFIG_EXPIRED",
  "message": "Config deleted more than 30 days ago",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

## 6. VERIFY EXISTING CONFIG (UC35)

### TC-PC-041: Verify config thành công - Cả 2 connections OK

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-041 |
| **Tên Test Case** | Verify config successfully - Both connections OK |
| **Mô tả** | Verify config, cả Jira và GitHub đều connect thành công |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- Config C1 tồn tại cho group G1
- Jira API reachable và token valid
- GitHub API reachable và token valid

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs/{configId}/verify`
2. configId = C1
3. System calls Jira API: `GET https://testproject.atlassian.net/rest/api/3/myself`
4. System calls GitHub API: `GET https://api.github.com/repos/testorg/testrepo`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
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
    "repoName": "testorg/testrepo",
    "defaultBranch": "main",
    "isPrivate": true,
    "responseTime": 312
  },
  "overallStatus": "SUCCESS",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```
- Audit log: Event `VERIFY_CONNECTION` với status SUCCESS

---

### TC-PC-042: Verify config - Partial failure (Jira failed, GitHub OK)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-042 |
| **Tên Test Case** | Verify config - Partial failure |
| **Mô tả** | Jira connection failed, GitHub OK |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Partial Success |

**Điều kiện tiên quyết:**
- Config C1 tồn tại
- Jira API returns 401 Unauthorized
- GitHub API reachable

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "configId": "...",
  "jiraConnection": {
    "status": "FAILED",
    "message": "Authentication failed: Invalid API token or insufficient permissions",
    "errorCode": "JIRA_AUTH_FAILED"
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "testorg/testrepo",
    "defaultBranch": "main",
    "isPrivate": true,
    "responseTime": 298
  },
  "overallStatus": "FAILED",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```

---

### TC-PC-043: Verify config - Complete failure (cả 2 failed)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-043 |
| **Tên Test Case** | Verify config - Complete failure |
| **Mô tả** | Cả Jira và GitHub đều connection failed |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Failure Case |

**Điều kiện tiên quyết:**
- Config C1 tồn tại
- Jira API timeout (no response after 10s)
- GitHub API returns 404 Not Found

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "jiraConnection": {
    "status": "TIMEOUT",
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

---

### TC-PC-044: Verify config thất bại - Config not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-044 |
| **Tên Test Case** | Verify config fails - Config not found |
| **Mô tả** | Verify config với ID không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi POST request với configId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "Config not found"
- Không gọi external APIs

---

### TC-PC-045: Verify config thất bại - Forbidden

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-045 |
| **Tên Test Case** | Verify config fails - Forbidden |
| **Mô tả** | User cố verify config không có quyền |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User A login với role STUDENT
- Config C1 tồn tại cho group G1
- User A không phải member của group G1

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "You do not have permission to verify this config"

---

### TC-PC-046: Verify config với rate limiting (BR-PC-010)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-046 |
| **Tên Test Case** | Verify config - Rate limit exceeded |
| **Mô tả** | User verify quá 10 lần trong 1 phút |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Rate Limiting |

**Điều kiện tiên quyết:**
- User đã verify 10 lần trong vòng 1 phút

**Các bước thực hiện:**
1. Gửi POST request verify lần thứ 11

**Kết quả mong đợi:**
- HTTP Status: `429 TOO_MANY_REQUESTS`
- Response:
```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Too many verification requests. Please try again later.",
  "timestamp": "2026-01-29T10:00:00Z",
  "retryAfter": 60
}
```
- Header: `Retry-After: 60`

---

### TC-PC-047: Verify config timeout handling (BR-PC-011)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-047 |
| **Tên Test Case** | Verify config - Timeout handling |
| **Mô tả** | External API calls timeout sau 10 seconds |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Timeout |

**Điều kiện tiên quyết:**
- Config C1 tồn tại
- Jira API slow (timeout sau 10s)
- GitHub API slow (timeout sau 10s)

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Total response time < 30 seconds (với retries)
- Response chứa TIMEOUT status cho cả 2 connections
- overallStatus = "FAILED"

---

### TC-PC-048: Lecturer có thể verify config của supervised group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-048 |
| **Tên Test Case** | Lecturer verifies supervised group config |
| **Mô tả** | Lecturer có quyền verify config của groups mình supervise |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer supervise group G1
- Config C1 tồn tại cho group G1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Verification result returned

---

## 7. VERIFY PRE-CREATE CREDENTIALS

### TC-PC-049: Verify credentials thành công (pre-create)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-049 |
| **Tên Test Case** | Verify credentials successfully - Pre-create |
| **Mô tả** | User verify credentials trước khi tạo config |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER
- User chưa tạo config (optional step)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/project-configs/verify`
2. Body: Credentials to verify

**Dữ liệu test:**
```json
{
  "jiraHostUrl": "https://newproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/neworg/newrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "jiraConnection": {
    "status": "SUCCESS",
    "message": "Connected to Jira successfully",
    "projectKey": "NEW",
    "projectName": "New Project",
    "responseTime": 234
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "neworg/newrepo",
    "defaultBranch": "main",
    "isPrivate": true,
    "responseTime": 289
  },
  "overallStatus": "SUCCESS",
  "verifiedAt": "2026-01-29T15:00:00Z"
}
```
- Database: KHÔNG lưu credentials (chỉ verify, not create)

---

### TC-PC-050: Verify pre-create - Partial failure

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-050 |
| **Tên Test Case** | Verify pre-create - Partial failure |
| **Mô tả** | Verify trước khi create, 1 connection failed |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Failure Case |

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- overallStatus = "FAILED"
- User có thể quyết định tạo config hay không dựa vào kết quả

---

### TC-PC-051: Verify pre-create thất bại - Invalid URL format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-051 |
| **Tên Test Case** | Verify pre-create fails - Invalid URL |
| **Mô tả** | Verify với URL không hợp lệ |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "jiraHostUrl": "http://invalid.com",
  "jiraApiToken": "ATATT3xFfGF0T1234567890",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Jira host URL must be a valid Atlassian URL"

---

### TC-PC-052: Verify pre-create với rate limiting

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-052 |
| **Tên Test Case** | Verify pre-create - Rate limit exceeded |
| **Mô tả** | User verify credentials quá 10 lần/phút |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Rate Limiting |

**Kết quả mong đợi:**
- HTTP Status: `429 TOO_MANY_REQUESTS`
- Message: "Too many verification requests"

---

### TC-PC-053: Verify pre-create không cần groupId

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-053 |
| **Tên Test Case** | Verify pre-create without groupId |
| **Mô tả** | Verify credentials không cần groupId (khác với verify existing config) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - API Design |

**Dữ liệu test:**
- Request body KHÔNG chứa `groupId`
- Chỉ chứa: jiraHostUrl, jiraApiToken, githubRepoUrl, githubToken

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Verification thành công
- User có thể verify credentials bất kỳ lúc nào

---

### TC-PC-054: Verify pre-create có thể dùng nhiều lần

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-054 |
| **Tên Test Case** | Verify pre-create can be called multiple times |
| **Mô tả** | User có thể verify nhiều lần với different credentials |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Use Case |

**Các bước thực hiện:**
1. User verify credentials set A
2. User verify credentials set B (different)
3. User verify credentials set C

**Kết quả mong đợi:**
- Tất cả 3 calls đều thành công (trong rate limit)
- Không có side effects (no database changes)

---

## 8. INTERNAL TOKEN ENDPOINT

### TC-PC-055: Internal endpoint - Get decrypted tokens thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-055 |
| **Tên Test Case** | Internal endpoint - Get decrypted tokens successfully |
| **Mô tả** | Sync Service lấy decrypted tokens để sync data |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Internal API |

**Điều kiện tiên quyết:**
- Config C1 tồn tại
- Request từ internal network
- Valid service-to-service authentication

**Các bước thực hiện:**
1. Gửi GET request đến `/internal/project-configs/{configId}/tokens`
2. configId = C1
3. Headers:
   - `X-Internal-Service-Key: <valid_service_key>`
   - `X-Service-Name: sync-service`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```
- Tokens KHÔNG masked (plaintext)
- Audit log: Event `TOKEN_DECRYPTED` với service name

---

### TC-PC-056: Internal endpoint - Unauthorized (no service key)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-056 |
| **Tên Test Case** | Internal endpoint - Unauthorized |
| **Mô tả** | Gọi internal endpoint không có service key |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi GET request đến `/internal/project-configs/{configId}/tokens`
2. KHÔNG gửi header `X-Internal-Service-Key`

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Service authentication failed"

---

### TC-PC-057: Internal endpoint - Invalid service key

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-057 |
| **Tên Test Case** | Internal endpoint - Invalid service key |
| **Mô tả** | Gọi internal endpoint với service key không đúng |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi GET request với header `X-Internal-Service-Key: invalid_key`

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Audit log: Event `UNAUTHORIZED_ACCESS` với attempted service name

---

### TC-PC-058: Internal endpoint - Service không có quyền

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-058 |
| **Tên Test Case** | Internal endpoint - Service forbidden |
| **Mô tả** | Service không trong whitelist cố gọi internal endpoint |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Authorization |

**Các bước thực hiện:**
1. Gửi request với valid service key
2. Header: `X-Service-Name: unauthorized-service`
3. `unauthorized-service` không trong whitelist

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Service does not have permission to access this endpoint"

---

### TC-PC-059: Internal endpoint - Config not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-059 |
| **Tên Test Case** | Internal endpoint - Config not found |
| **Mô tả** | Internal service lấy tokens cho config không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi request với valid service authentication
2. configId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "Config not found"

---

## 9. LIST CONFIGS BY LECTURER

### TC-PC-060: Lecturer list configs thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-060 |
| **Tên Test Case** | Lecturer lists supervised group configs |
| **Mô tả** | Lecturer lấy list configs của tất cả groups mình supervise |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer supervise groups G1, G2, G3
- G1 có config C1
- G2 có config C2
- G3 chưa có config

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/lecturer/me`
2. Query params: `page=0&size=20`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
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
- Chỉ return configs của groups mà lecturer supervise

---

### TC-PC-061: Lecturer list configs với pagination

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-061 |
| **Tên Test Case** | Lecturer lists configs with pagination |
| **Mô tả** | Test pagination với nhiều configs |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Pagination |

**Điều kiện tiên quyết:**
- Lecturer supervise 25 groups
- 25 groups đều có configs

**Các bước thực hiện:**
1. Gửi GET request: `/api/project-configs/lecturer/me?page=0&size=10`
2. Gửi GET request: `/api/project-configs/lecturer/me?page=1&size=10`

**Kết quả mong đợi:**
- Page 0: 10 configs, totalElements = 25, totalPages = 3
- Page 1: 10 configs
- Page 2: 5 configs

---

### TC-PC-062: Lecturer list configs filter by semester

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-062 |
| **Tên Test Case** | Lecturer lists configs filtered by semester |
| **Mô tả** | Filter configs by semester query param |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Filtering |

**Các bước thực hiện:**
1. Gửi GET request: `/api/project-configs/lecturer/me?semester=Spring2026`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa configs của groups trong Spring2026 semester

---

### TC-PC-063: Non-lecturer không thể access lecturer endpoint

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-063 |
| **Tên Test Case** | Non-lecturer cannot access lecturer endpoint |
| **Mô tả** | TEAM_LEADER cố gọi lecturer endpoint |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role TEAM_LEADER (không phải LECTURER)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/project-configs/lecturer/me`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "This endpoint is only accessible by lecturers"

---

## 10. AUTHORIZATION & SECURITY TESTS

### TC-PC-064: JWT Token validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-064 |
| **Tên Test Case** | JWT token validation |
| **Mô tả** | Test JWT token được validate đúng cách |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Test Scenarios:**

| Scenario | Expected Result |
|----------|-----------------|
| Valid token | 200 OK |
| Expired token | 401 UNAUTHORIZED |
| Invalid signature | 401 UNAUTHORIZED |
| Malformed token | 401 UNAUTHORIZED |
| No token | 401 UNAUTHORIZED |

---

### TC-PC-065: Role-based access control (RBAC)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-065 |
| **Tên Test Case** | Role-based access control |
| **Mô tả** | Verify RBAC matrix (BR-PC-017) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Authorization |

**Access Matrix:**

| Action | ADMIN | LECTURER | TEAM_LEADER | STUDENT |
|--------|-------|----------|-------------|---------|
| Create Config | ✅ All | ❌ | ✅ Own | ❌ |
| View Config | ✅ All | ✅ Supervised | ✅ Own | ❌ |
| Update Config | ✅ All | ❌ | ✅ Own | ❌ |
| Delete Config | ✅ All | ❌ | ✅ Own | ❌ |
| Verify Config | ✅ All | ✅ Supervised | ✅ Own | ❌ |

**Test Steps:** Verify từng cell trong matrix

---

### TC-PC-066: Token masking in responses (BR-PC-005)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-066 |
| **Tên Test Case** | Token masking in responses |
| **Mô tả** | Verify tokens được mask trong API responses |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Data Protection |

**Test Data:**
- Jira Token: `ATATT3xFfGF0T1234567890abcdefghijklmnop`
- GitHub Token: `ghp_abc123xyz456def789ghi012jkl345mno678`

**Expected Masked Values:**
- Jira: `ATATT3x***...` (7 chars visible)
- GitHub: `ghp_***...` (4 chars visible)

**Endpoints to Test:**
- POST /api/project-configs (create response)
- GET /api/project-configs/{id}
- GET /api/project-configs/group/{groupId}
- PUT /api/project-configs/{id} (update response)

**Verification:**
- Response KHÔNG chứa plaintext tokens
- Database chứa encrypted tokens
- Internal endpoint `/internal/...` trả về plaintext (whitelisted)

---

### TC-PC-067: Audit logging for sensitive operations (BR-PC-019)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-067 |
| **Tên Test Case** | Audit logging for sensitive operations |
| **Mô tả** | Verify audit logs được ghi cho sensitive actions |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Audit |

**Events to Test:**

| Event | Trigger | Required Fields |
|-------|---------|-----------------|
| CONFIG_CREATED | Create config | configId, userId, ipAddress |
| CONFIG_UPDATED | Update config | configId, userId, changes |
| CONFIG_DELETED | Delete config | configId, userId, deletedBy |
| TOKEN_ROTATED | Update token | tokenType, oldPreview, newPreview |
| TOKEN_DECRYPTED | Internal access | configId, serviceName |
| UNAUTHORIZED_ACCESS | Access denied | userId, configId, reason |

**Verification:**
1. Perform action
2. Query audit_logs table
3. Verify log entry exists với correct details

---

### TC-PC-068: SQL Injection protection

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-068 |
| **Tên Test Case** | SQL Injection protection |
| **Mô tả** | Test SQL injection attacks bị block |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Injection |

**Attack Vectors:**

```sql
configId = "1' OR '1'='1"
groupId = "'; DROP TABLE project_configs;--"
jiraHostUrl = "https://test.atlassian.net'; DELETE FROM project_configs WHERE '1'='1"
```

**Kết quả mong đợi:**
- Tất cả attacks bị reject với validation error
- Database không bị modify
- Audit log ghi attempted attacks

---

### TC-PC-069: XSS protection trong response

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-069 |
| **Tên Test Case** | XSS protection in responses |
| **Mô tả** | Test XSS payloads bị escape/sanitize |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - XSS |

**Attack Payload:**
```json
{
  "groupId": "...",
  "jiraHostUrl": "https://test.atlassian.net<script>alert('XSS')</script>",
  "jiraApiToken": "ATATT3x...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_..."
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST` (validation error)
- URL không pass regex validation
- Database không lưu malicious content

---

### TC-PC-070: CORS configuration

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-070 |
| **Tên Test Case** | CORS configuration |
| **Mô tả** | Test CORS headers được set đúng cách |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Security - CORS |

**Test Steps:**
1. Gửi OPTIONS request (preflight)
2. Check response headers:
   - `Access-Control-Allow-Origin`
   - `Access-Control-Allow-Methods`
   - `Access-Control-Allow-Headers`

**Kết quả mong đợi:**
- Chỉ whitelisted origins được allow
- Internal endpoints không expose CORS

---

### TC-PC-071: Rate limiting enforcement (BR-PC-010)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-071 |
| **Tên Test Case** | Rate limiting enforcement |
| **Mô tả** | Test rate limits được enforce |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Rate Limiting |

**Rate Limits:**
- Verify endpoint: 10 requests/minute per user
- General endpoints: 100 requests/minute per user

**Test Steps:**
1. Gửi 11 verify requests trong 1 phút
2. Request 11 → `429 TOO_MANY_REQUESTS`
3. Wait 1 minute
4. Request 12 → `200 OK`

**Verify Headers:**
```
X-RateLimit-Limit: 10
X-RateLimit-Remaining: 0
X-RateLimit-Reset: <timestamp>
```

---

### TC-PC-072: HTTPS enforcement

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-072 |
| **Tên Test Case** | HTTPS enforcement |
| **Mô tả** | Test HTTP requests bị redirect sang HTTPS |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Transport |

**Test Steps:**
1. Gửi HTTP request: `http://api.samt.com/api/project-configs`
2. Check response

**Kết quả mong đợi:**
- HTTP Status: `301 MOVED_PERMANENTLY` hoặc `308 PERMANENT_REDIRECT`
- Location header: `https://api.samt.com/api/project-configs`

---

### TC-PC-073: Service-to-service authentication

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-073 |
| **Tén Test Case** | Service-to-service authentication |
| **Mô tả** | Test internal service authentication (BR-PC-018) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Authentication |

**Whitelist Services:**
- `sync-service`
- `analysis-service`

**Test Cases:**

| Service Name | Service Key | Expected Result |
|--------------|-------------|-----------------|
| sync-service | Valid | 200 OK |
| sync-service | Invalid | 401 UNAUTHORIZED |
| unknown-service | Valid | 403 FORBIDDEN |
| (empty) | Valid | 401 UNAUTHORIZED |

---

## 11. VALIDATION RULES TESTS

### TC-PC-074: Group ID validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-074 |
| **Tên Test Case** | Group ID validation |
| **Mô tả** | Test groupId validation rules |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Test Cases:**

| Value | Expected Result |
|-------|-----------------|
| `null` | 400 BAD_REQUEST - "Group ID is required" |
| `"invalid-uuid"` | 400 BAD_REQUEST - "Invalid UUID format" |
| Valid UUID but group not exists | 404 NOT_FOUND - "Group does not exist" |
| Valid UUID, group exists | Pass validation |

---

### TC-PC-075: Jira Host URL validation (BR-PC-007)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-075 |
| **Tên Test Case** | Jira Host URL validation |
| **Mô tả** | Test Jira URL format validation |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Test Cases:**

| Value | Expected Result |
|-------|-----------------|
| `""` (empty) | 400 - "Jira host URL is required" |
| `"not-a-url"` | 400 - "Invalid URL format" |
| `"http://project.atlassian.net"` | 400 - "HTTPS only" |
| `"https://project.com"` | 400 - "Must be atlassian.net domain" |
| `"https://project.atlassian.net"` | ✅ Pass |
| `"https://my-team.atlassian.net"` | ✅ Pass |

---

### TC-PC-076: Jira API Token validation (BR-PC-021)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-076 |
| **Tên Test Case** | Jira API Token validation |
| **Mô tả** | Test Jira token format validation |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Test Cases:**

| Value | Expected Result |
|-------|-----------------|
| `null` | 400 - "Jira API token is required" |
| `"short"` | 400 - "Token length must be between 20 and 500" |
| `"invalidtoken123"` | 400 - "Token must start with 'ATATT'" |
| `"ATATT3xFfGF0T1234567890"` | ✅ Pass (>20 chars, starts with ATATT) |

---

### TC-PC-077: GitHub Repository URL validation (BR-PC-008)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-077 |
| **Tên Test Case** | GitHub Repository URL validation |
| **Mô tả** | Test GitHub URL format validation |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Test Cases:**

| Value | Expected Result |
|-------|-----------------|
| `""` | 400 - "GitHub repository URL is required" |
| `"http://github.com/org/repo"` | 400 - "HTTPS only" |
| `"https://gitlab.com/org/repo"` | 400 - "Must be github.com" |
| `"https://github.com/org/repo.git"` | 400 - ".git suffix not allowed" |
| `"https://github.com/org"` | 400 - "Invalid format (missing repo)" |
| `"https://github.com/org/repo"` | ✅ Pass |

---

### TC-PC-078: GitHub Token validation (BR-PC-022)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-078 |
| **Tên Test Case** | GitHub Token validation |
| **Mô tả** | Test GitHub token format validation |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Test Cases:**

| Value | Expected Result |
|-------|-----------------|
| `null` | 400 - "GitHub token is required" |
| `"ghp_short"` | 400 - "Token must be 44 characters" |
| `"gho_1234567890123456789012345678901234567890"` | 400 - "Token must start with 'ghp_'" |
| `"ghp_abc123xyz456def789ghi012jkl345mno678"` | ✅ Pass (44 chars, starts with ghp_) |

---

### TC-PC-079: URL max length validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-079 |
| **Tên Test Case** | URL max length validation |
| **Mô tả** | Test URL không vượt quá 255 ký tự |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Test Data:**
- Jira URL: 256 characters
- GitHub URL: 256 characters

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "URL must not exceed 255 characters"

---

### TC-PC-080: Token max length validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-080 |
| **Tên Test Case** | Token max length validation |
| **Mô tả** | Test token không vượt quá 500 ký tự |
| **Độ ưu tiên** | LOW |
| **Loại test** | Negative - Validation |

**Test Data:**
- Jira Token: 501 characters

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Token length must not exceed 500 characters"

---

### TC-PC-081: Multiple validation errors

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-081 |
| **Tên Test Case** | Multiple validation errors |
| **Mô tả** | Request có nhiều validation errors cùng lúc |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Test Data:**
```json
{
  "groupId": null,
  "jiraHostUrl": "http://invalid.com",
  "jiraApiToken": "short",
  "githubRepoUrl": "",
  "githubToken": "invalid"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response chứa array của tất cả validation errors:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "errors": [
    {
      "field": "groupId",
      "message": "Group ID is required"
    },
    {
      "field": "jiraHostUrl",
      "message": "Jira host URL must be a valid Atlassian URL"
    },
    {
      "field": "jiraApiToken",
      "message": "Jira API token length must be between 20 and 500"
    },
    {
      "field": "githubRepoUrl",
      "message": "GitHub repository URL is required"
    },
    {
      "field": "githubToken",
      "message": "GitHub token must start with 'ghp_'"
    }
  ]
}
```

---

### TC-PC-082: Custom @GroupExists validator

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-082 |
| **Tên Test Case** | Custom @GroupExists validator |
| **Mô tả** | Test custom validator check group existence |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Custom Validation |

**Test Cases:**

| GroupId | Group Exists? | Expected Result |
|---------|---------------|-----------------|
| Valid UUID | Yes | Pass validation |
| Valid UUID | No | 400 - "Group does not exist" |
| `null` | N/A | Pass (@NotNull handles null) |

---

### TC-PC-083: Validation on UPDATE vs CREATE

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-083 |
| **Tên Test Case** | Validation differs on UPDATE vs CREATE |
| **Mô tả** | Update cho phép partial data, create không |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Validation Logic |

**CREATE Request:**
```json
{
  "jiraHostUrl": "https://test.atlassian.net"
}
```
**Expected:** `400 BAD_REQUEST` - Missing required fields

**UPDATE Request:**
```json
{
  "jiraHostUrl": "https://test.atlassian.net"
}
```
**Expected:** `200 OK` - Partial update allowed

---

### TC-PC-084: Validation messages are clear and actionable

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-084 |
| **Tên Test Case** | Validation messages clarity |
| **Mô tả** | Validation errors có messages rõ ràng |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - UX |

**Requirements:**
- Error message nói rõ field nào sai
- Error message nói rõ sai như thế nào
- Error message gợi ý cách fix (nếu có thể)

**Example:**
```json
{
  "field": "jiraHostUrl",
  "message": "Jira host URL must be a valid Atlassian URL (https://*.atlassian.net)",
  "providedValue": "http://test.com",
  "reason": "HTTP protocol not allowed, must use HTTPS"
}
```

---

### TC-PC-085: Trim whitespace trong input

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-085 |
| **Tên Test Case** | Trim whitespace in input |
| **Mô tả** | System trim leading/trailing whitespace |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Input Sanitization |

**Test Data:**
```json
{
  "jiraHostUrl": "  https://test.atlassian.net  ",
  "jiraApiToken": " ATATT3xFfGF0T1234567890 "
}
```

**Kết quả mong đợi:**
- Input được trim
- Validation pass
- Database lưu trimmed values

---

### TC-PC-086: Case-sensitive validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-086 |
| **Tên Test Case** | Case-sensitive validation |
| **Mô tả** | URLs và tokens case-sensitive |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Validation |

**Test Cases:**

| Input | Expected Result |
|-------|-----------------|
| `"ATATT3xFfGF0T1234567890"` | ✅ Pass |
| `"atatt3xFfGF0T1234567890"` | ❌ Fail - Must start with uppercase "ATATT" |
| `"https://TEST.atlassian.net"` | ✅ Pass (domain case-insensitive) |
| `"GHP_abc123..."` | ❌ Fail - Must be lowercase "ghp_" |

---

### TC-PC-087: Special characters trong URLs

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-087 |
| **Tên Test Case** | Special characters in URLs |
| **Mô tả** | Test special characters validation |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Test Cases:**

| URL | Expected Result |
|-----|-----------------|
| `"https://test-team.atlassian.net"` | ✅ Pass (hyphen allowed) |
| `"https://test_team.atlassian.net"` | ❌ Fail (underscore not allowed in subdomain) |
| `"https://github.com/org-name/repo_name"` | ✅ Pass (both hyphen and underscore allowed) |
| `"https://github.com/org name/repo"` | ❌ Fail (space not allowed) |

---

### TC-PC-088: Null vs empty string validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-088 |
| **Tên Test Case** | Null vs empty string validation |
| **Mô tả** | Test distinction between null và empty string |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Validation |

**Test Cases:**

| Field Value | Annotation | Expected Result |
|-------------|------------|-----------------|
| `null` | `@NotNull` | 400 - "Field is required" |
| `""` | `@NotBlank` | 400 - "Field cannot be blank" |
| `"  "` | `@NotBlank` | 400 - "Field cannot be blank" |
| `"value"` | Both | ✅ Pass |

---

## 12. BUSINESS RULES TESTS

### TC-PC-089: Unique config per group (BR-PC-001)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-089 |
| **Tên Test Case** | Unique config per group enforcement |
| **Mô tả** | Database constraint enforce 1 group = 1 config |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Database Constraint |

**Test Steps:**
1. Create config C1 for group G1 → Success
2. Create config C2 for group G1 → Fail (UNIQUE constraint)

**Kết quả mong đợi:**
- `409 CONFIG_ALREADY_EXISTS`
- Database chỉ có 1 config cho group G1

---

### TC-PC-090: Group deletion cascade (BR-PC-015) - OPEN QUESTION

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-090 |
| **Tên Test Case** | Group deletion cascades to config |
| **Mô tả** | Khi group bị xóa, config cũng soft delete |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 có config C1

**Các bước thực hiện:**
1. Delete group G1 (soft delete)
2. Check config C1

**Kết quả mong đợi:**
- Group G1: `deleted_at` IS NOT NULL
- Config C1: `deleted_at` IS NOT NULL
- Config C1 không thể GET

**Note:** This is an OPEN QUESTION in spec. Need clarification.

---

### TC-PC-091: Soft delete không xóa vật lý (BR-PC-012)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-091 |
| **Tên Test Case** | Soft delete preserves data |
| **Mô tả** | Soft delete không xóa record khỏi database |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Các bước thực hiện:**
1. Create config C1
2. Delete config C1
3. Query database: `SELECT * FROM project_configs WHERE config_id = C1`

**Kết quả mong đợi:**
- Record C1 vẫn tồn tại trong database
- `deleted_at` ≠ NULL
- `deleted_by` = user UUID
- Encrypted tokens vẫn còn
- API GET không return config này (filtered out)

---

### TC-PC-092: Permanent deletion sau 30 ngày (BR-PC-014)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-092 |
| **Tên Test Case** | Permanent deletion after 30 days |
| **Mô tả** | Cleanup job xóa vĩnh viễn configs deleted > 30 days |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Scheduled Job |

**Điều kiện tiên quyết:**
- Config C1 deleted 31 ngày trước
- Config C2 deleted 29 ngày trước

**Các bước thực hiện:**
1. Run cleanup job (scheduled at 2 AM daily)

**Kết quả mong đợi:**
- Config C1: Bị xóa vĩnh viễn (hard delete)
- Config C2: Vẫn còn (soft deleted)
- Audit log: Event `CONFIG_PERMANENTLY_DELETED` cho C1

---

### TC-PC-093: Optimistic locking prevents concurrent updates (BR-PC-016)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-093 |
| **Tên Test Case** | Optimistic locking enforcement |
| **Mô tả** | JPA @Version prevents concurrent modification |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Concurrency Control |

**Test Steps:**
1. User A reads config C1 (version = 1)
2. User B reads config C1 (version = 1)
3. User A updates config C1 → Success, version = 2
4. User B updates config C1 → Fail (OptimisticLockException)

**Kết quả mong đợi:**
- User B: `409 CONCURRENT_UPDATE`
- Message: "Config was updated by another user. Please refresh and try again."

---

### TC-PC-094: Token rotation audit (BR-PC-006)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-094 |
| **Tên Test Case** | Token rotation is audited |
| **Mô tả** | Old token preview logged when rotated |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Audit |

**Điều kiện tiên quyết:**
- Config C1 có jiraApiToken = `ATATT3xFfGF0T1234567890`

**Các bước thực hiện:**
1. Update config C1, change jiraApiToken to `ATATT9xFfGF0T9999999999`
2. Check audit log

**Kết quả mong đợi:**
- Audit log contains:
```json
{
  "action": "TOKEN_ROTATED",
  "tokenType": "JIRA_API_TOKEN",
  "oldTokenPreview": "ATATT3x***...",
  "newTokenPreview": "ATATT9x***...",
  "rotatedBy": "user-uuid",
  "rotatedAt": "...",
  "ipAddress": "..."
}
```

---

### TC-PC-095: Graceful degradation khi decrypt failed (BR-PC-023)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-095 |
| **Tên Test Case** | Graceful degradation on decryption failure |
| **Mô tả** | System vẫn return config khi decrypt failed |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Error Handling |

**Điều kiện tiên quyết:**
- Config C1 tồn tại
- Encryption key bị change/lost
- Decrypt operation sẽ throw DecryptionException

**Các bước thực hiện:**
1. GET /api/project-configs/{C1}
2. System cố decrypt tokens → Fail
3. System catches exception

**Kết quả mong đợi:**
- HTTP Status: `200 OK` (không crash)
- Response:
```json
{
  "configId": "...",
  "jiraApiToken": "***DECRYPTION_FAILED***",
  "githubToken": "***DECRYPTION_FAILED***",
  ...
}
```
- Error logged
- User có thể update credentials để recover

---

### TC-PC-096: Transaction rollback on error

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-096 |
| **Tên Test Case** | Transaction rollback on error |
| **Mô tả** | Nếu có error trong transaction, rollback tất cả changes |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Transaction |

**Test Scenario:**
1. Start transaction
2. Create config C1 → Success
3. Encrypt tokens → Success
4. Save to database → Fail (database down)
5. Transaction rollback

**Kết quả mong đợi:**
- Config C1 không được save
- Database state không thay đổi
- Error returned to client

---

### TC-PC-097: Jira URL format enforcement (BR-PC-007)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-097 |
| **Tên Test Case** | Jira URL format enforcement |
| **Mô tả** | Chỉ accept https://*.atlassian.net URLs |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Valid URLs:**
- `https://project.atlassian.net` ✅
- `https://my-team-123.atlassian.net` ✅

**Invalid URLs:**
- `http://project.atlassian.net` ❌ (HTTP)
- `https://project.jira.com` ❌ (Not atlassian.net)
- `https://atlassian.net` ❌ (No subdomain)
- `project.atlassian.net` ❌ (Missing protocol)

---

### TC-PC-098: GitHub URL format enforcement (BR-PC-008)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-098 |
| **Tên Test Case** | GitHub URL format enforcement |
| **Mô tả** | Chỉ accept https://github.com/owner/repo URLs |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Valid URLs:**
- `https://github.com/microsoft/vscode` ✅
- `https://github.com/test-org/test-repo` ✅

**Invalid URLs:**
- `http://github.com/org/repo` ❌ (HTTP)
- `https://github.com/org/repo.git` ❌ (.git suffix)
- `https://gitlab.com/org/repo` ❌ (Not GitHub)
- `https://github.com/org` ❌ (Missing repo)

---

### TC-PC-099: Lecturer read-only access (BR-PC-017)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-099 |
| **Tên Test Case** | Lecturer has read-only access |
| **Mô tả** | Lecturer chỉ xem được, không update/delete được |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Test Cases:**

| Action | Expected Result |
|--------|-----------------|
| GET config | 200 OK ✅ |
| Create config | 403 FORBIDDEN ❌ |
| Update config | 403 FORBIDDEN ❌ |
| Delete config | 403 FORBIDDEN ❌ |
| Verify config | 200 OK ✅ |

---

### TC-PC-100: Admin full access to all configs (BR-PC-017)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-PC-100 |
| **Tên Test Case** | Admin has full access to all configs |
| **Mô tả** | ADMIN có thể CRUD bất kỳ config nào |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Config C1 tồn tại cho group G1
- Admin KHÔNG PHẢI member của group G1

**Test Cases:**

| Action | Expected Result |
|--------|-----------------|
| GET config of any group | 200 OK ✅ |
| Create config for any group | 201 CREATED ✅ |
| Update config of any group | 200 OK ✅ |
| Delete config of any group | 204 NO_CONTENT ✅ |
| Verify config of any group | 200 OK ✅ |

---

## Summary

### Test Coverage by Category

| Category | Test Cases | Coverage |
|----------|------------|----------|
| Create Config | TC-PC-001 to TC-PC-012 | 12 tests |
| Get Config | TC-PC-013 to TC-PC-024 | 12 tests |
| Update Config | TC-PC-025 to TC-PC-034 | 10 tests |
| Delete Config | TC-PC-035 to TC-PC-040 | 6 tests |
| Verify Config | TC-PC-041 to TC-PC-048 | 8 tests |
| Verify Pre-Create | TC-PC-049 to TC-PC-054 | 6 tests |
| Internal Endpoint | TC-PC-055 to TC-PC-059 | 5 tests |
| List Configs | TC-PC-060 to TC-PC-063 | 4 tests |
| Security | TC-PC-064 to TC-PC-073 | 10 tests |
| Validation | TC-PC-074 to TC-PC-088 | 15 tests |
| Business Rules | TC-PC-089 to TC-PC-100 | 12 tests |
| **TOTAL** | | **100 tests** |

### Priority Breakdown

| Priority | Count | Percentage |
|----------|-------|------------|
| CRITICAL | 15 | 15% |
| HIGH | 60 | 60% |
| MEDIUM | 23 | 23% |
| LOW | 2 | 2% |

### Test Type Breakdown

| Type | Count |
|------|-------|
| Positive (Happy Path) | 45 |
| Negative (Error Cases) | 50 |
| Security | 10 |
| Performance | 5 |

---

## Test Execution Guidelines

### Prerequisites

1. **Environment Setup:**
   - PostgreSQL database running
   - Test data seeded (groups, users, user_groups)
   - Encryption key configured
   - External API mocks configured (Jira, GitHub)

2. **Test Users:**
   - ADMIN user: `admin@samt.com`
   - LECTURER user: `lecturer@samt.com`
   - TEAM_LEADER user: `leader@samt.com`
   - STUDENT user: `student@samt.com`

3. **Test Groups:**
   - Group G1: SE1705-G1 (leader: leader@samt.com)
   - Group G2: AI2024-G5
   - Group G3: Empty group (no config)

### Execution Order

1. **Phase 1 - Setup:** Create test data (users, groups, relationships)
2. **Phase 2 - CRUD:** TC-PC-001 to TC-PC-040
3. **Phase 3 - Verify:** TC-PC-041 to TC-PC-054
4. **Phase 4 - Internal:** TC-PC-055 to TC-PC-059
5. **Phase 5 - Security:** TC-PC-064 to TC-PC-073
6. **Phase 6 - Validation:** TC-PC-074 to TC-PC-088
7. **Phase 7 - Business Rules:** TC-PC-089 to TC-PC-100

### Tools

- **API Testing:** Postman, REST Assured
- **Database:** PostgreSQL client, DbUnit
- **Mocking:** WireMock (Jira/GitHub APIs)
- **Test Framework:** JUnit 5, Mockito
- **Coverage:** JaCoCo

---

## Copy to Excel Format

Để copy vào Excel/TestRail, format như sau:

```
Test Case ID | Tên Test Case | Mô tả | Điều kiện tiên quyết | Các bước thực hiện | Dữ liệu test | Kết quả mong đợi | Độ ưu tiên | Loại test
TC-PC-001 | Create config successfully | Team Leader tạo config... | User đã login... | 1. Gửi POST... | {...} | 201 CREATED | HIGH | Positive
...
```

**Note:** Mỗi test case có thể copy từ sections trên, đã format sẵn với tất cả required fields.
