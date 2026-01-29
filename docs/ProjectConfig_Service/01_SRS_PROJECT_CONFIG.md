# SRS – PROJECT CONFIG SERVICE

## 1. Overview

Project Config Service quản lý cấu hình tích hợp của từng nhóm dự án với các hệ thống bên ngoài (Jira Software và GitHub). Service này lưu trữ thông tin kết nối API và đảm bảo bảo mật cho các token/credential.

**Source từ SRS tổng:**
- Section 6.3.4: Bảng Project_Configs
- Section 4: Bảo mật - Mã hóa API Keys bằng AES-256
- UC04: Cấu hình tích hợp hệ thống (Admin)
- UC10: Đồng bộ dữ liệu dự án (Team Leader)

---

## 2. Actors

| Actor | Mô tả |
|-------|-------|
| ADMIN | Quản trị viên - cấu hình hệ thống toàn cục |
| TEAM_LEADER | Trưởng nhóm - cấu hình cho nhóm của mình |
| LECTURER | Giảng viên - xem cấu hình của các nhóm phụ trách |

---

## 3. Use Case List

> **Note:** CHỈ các UC được liệt kê dưới đây được phép implement

---

### UC30 – Create Project Config

**Actor:** TEAM_LEADER  
**Description:** Tạo cấu hình tích hợp cho nhóm dự án

#### Preconditions

- Team Leader đã đăng nhập
- Group đã được tạo trong hệ thống
- Group chưa có config (constraint UNIQUE)

#### Main Flow

1. Team Leader gửi thông tin cấu hình Jira và GitHub
2. System kiểm tra quyền (user phải là Leader của group)
3. System kiểm tra group chưa có config
4. System mã hóa API tokens (AES-256)
5. System lưu config vào database
6. System trả về config (tokens đã masked)

#### Authorization Rules

- **TEAM_LEADER:** Chỉ tạo config cho nhóm của mình
- **ADMIN:** Tạo config cho bất kỳ nhóm nào

#### API

**Endpoint:** `POST /api/project-configs`

#### Request DTO

```json
{
  "groupId": "uuid",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_abc123..."
}
```

#### Response DTO

```json
{
  "configId": "uuid",
  "groupId": "uuid",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT***...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z"
}
```

#### Error Codes

- `403 FORBIDDEN` - Không phải leader của nhóm
- `404 GROUP_NOT_FOUND` - Nhóm không tồn tại
- `409 CONFIG_ALREADY_EXISTS` - Nhóm đã có config

---

### UC31 – Get Project Config

**Actor:** TEAM_LEADER, LECTURER  
**Description:** Lấy thông tin cấu hình của nhóm

#### Preconditions

- Config đã tồn tại
- User có quyền xem (Leader hoặc Lecturer phụ trách)

#### Main Flow

1. User yêu cầu lấy config theo groupId
2. System kiểm tra quyền
3. System trả về config (tokens đã masked)

#### Authorization Rules

- **TEAM_LEADER:** Xem config của nhóm mình
- **LECTURER:** Xem config của nhóm phụ trách
- **ADMIN:** Xem mọi config

#### API

**Endpoint:** `GET /api/project-configs/group/{groupId}`

#### Response DTO

```json
{
  "configId": "uuid",
  "groupId": "uuid",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT***...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T11:00:00Z"
}
```

#### Error Codes

- `403 FORBIDDEN` - Không có quyền xem
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

### UC32 – Update Project Config

**Actor:** TEAM_LEADER  
**Description:** Cập nhật thông tin cấu hình

#### Preconditions

- Config đã tồn tại
- User là Leader của group

#### Main Flow

1. Team Leader gửi thông tin cập nhật
2. System kiểm tra quyền
3. System mã hóa tokens mới (nếu có)
4. System cập nhật config
5. System trả về config đã cập nhật

#### Rules

- Có thể cập nhật từng phần hoặc toàn bộ
- Nếu không gửi token mới → giữ nguyên token cũ
- Không cho update groupId (immutable)

#### API

**Endpoint:** `PUT /api/project-configs/{configId}`

#### Request DTO

```json
{
  "jiraHostUrl": "https://newproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/neworg/repo",
  "githubToken": "ghp_xyz789..."
}
```

**Note:** Tất cả fields đều optional. Chỉ gửi field cần update.

#### Response DTO

```json
{
  "configId": "uuid",
  "groupId": "uuid",
  "jiraHostUrl": "https://newproject.atlassian.net",
  "jiraApiToken": "ATATT***...",
  "githubRepoUrl": "https://github.com/neworg/repo",
  "githubToken": "ghp_***...",
  "updatedAt": "2026-01-29T12:00:00Z"
}
```

#### Error Codes

- `403 FORBIDDEN` - Không phải leader
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

### UC33 – Delete Project Config

**Actor:** ADMIN, TEAM_LEADER  
**Description:** Xóa cấu hình (soft delete)

#### Preconditions

- Config đã tồn tại

#### Main Flow

1. User yêu cầu xóa config
2. System kiểm tra quyền
3. System soft delete config
4. System trả về success

#### Authorization Rules

- **TEAM_LEADER:** Xóa config của nhóm mình
- **ADMIN:** Xóa bất kỳ config nào

#### API

**Endpoint:** `DELETE /api/project-configs/{configId}`

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `403 FORBIDDEN` - Không có quyền xóa
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

### UC34 – Verify Config Connection

**Actor:** TEAM_LEADER, ADMIN  
**Description:** Kiểm tra tính hợp lệ của config (test connection)

#### Preconditions

- Config đã tồn tại hoặc đang được tạo mới

#### Main Flow

1. User yêu cầu verify connection
2. System giải mã tokens
3. System thử kết nối đến Jira API
4. System thử kết nối đến GitHub API
5. System trả về kết quả kiểm tra

#### API

**Endpoint:** `POST /api/project-configs/{configId}/verify`

hoặc để verify trước khi tạo:

**Endpoint:** `POST /api/project-configs/verify`

#### Request DTO (cho verify trước khi tạo)

```json
{
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_abc123..."
}
```

#### Response DTO

```json
{
  "jiraConnection": {
    "status": "SUCCESS",
    "message": "Connected to Jira successfully",
    "projectKey": "SWP"
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "org/repo"
  },
  "overallStatus": "SUCCESS"
}
```

#### Error Response (Connection Failed)

```json
{
  "jiraConnection": {
    "status": "FAILED",
    "message": "Invalid API token or insufficient permissions"
  },
  "githubConnection": {
    "status": "SUCCESS",
    "message": "Connected to GitHub successfully",
    "repoName": "org/repo"
  },
  "overallStatus": "FAILED"
}
```

#### Error Codes

- `404 CONFIG_NOT_FOUND` - Config không tồn tại (cho endpoint với configId)
- `400 INVALID_CREDENTIALS` - Credentials không hợp lệ

---

### UC35 – Get Decrypted Tokens (Internal Only)

**Actor:** SYSTEM (Internal Service)  
**Description:** Lấy tokens đã giải mã cho Sync Service sử dụng

#### Preconditions

- Config tồn tại
- Request từ authenticated internal service

#### Main Flow

1. Sync Service gọi internal endpoint
2. System verify service authentication
3. System giải mã tokens
4. System trả về tokens đầy đủ

#### API

**Endpoint:** `GET /internal/project-configs/{configId}/tokens`

**Note:** Endpoint này chỉ accessible từ internal network, không expose ra public API Gateway.

#### Response DTO

```json
{
  "configId": "uuid",
  "groupId": "uuid",
  "jiraHostUrl": "https://yourproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_abc123xyz456..."
}
```

#### Error Codes

- `401 UNAUTHORIZED` - Service authentication failed
- `404 CONFIG_NOT_FOUND` - Config không tồn tại

---

## 4. Security Architecture

### 4.1 Token Encryption

**Algorithm:** AES-256-GCM  
**Key Management:** 
- Encryption key stored in environment variable
- Rotation policy: 90 days (configurable)

### 4.2 Token Masking

Khi trả về cho client, tokens được mask:
- Jira token: Hiển thị 6 ký tự đầu + `***...`
- GitHub token: Hiển thị 4 ký tự đầu + `***...`

### 4.3 Authorization Flow

```
Request → JWT Filter → Extract userId & roles
       → Check if user is Leader/Lecturer of group
       → Allow/Deny access
```

---

## 5. Integration Points

### 5.1 Dependencies on Other Services

- **User/Group Service:** Verify group existence, check group membership
- **Identity Service:** JWT token validation

### 5.2 Services Depending on Config Service

- **Sync Service:** Lấy credentials để đồng bộ dữ liệu từ Jira/GitHub
