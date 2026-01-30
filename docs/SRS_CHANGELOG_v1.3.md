# SRS TỔNG - CHANGELOG (Version 1.2 → 1.3)

**Date:** January 30, 2026  
**Updated by:** Principal Software Architect & System Analyst  
**Source:** Identity Service, User/Group Service, Project Config Service documentation

---

## SUMMARY

Cập nhật SRS tổng để phản ánh chính xác những gì đã được thiết kế và implement ở Identity Service, User/Group Service, và Project Config Service. SRS tổng là **SOURCE OF TRUTH** cho toàn hệ thống.

---

## [UPDATED] - Các phần đã cập nhật

### 1. Section 2.2 - User Classes / Roles

**Thay đổi:**
- Chuẩn hóa rõ ràng **System Roles** (ADMIN, LECTURER, STUDENT) vs **Group Roles** (LEADER, MEMBER)
- Loại bỏ tên gọi "Team Leader" và "Team Member" ở System Role level
- Thêm chi tiết quyền hạn cho từng role

**Lý do:**
- Identity Service quản lý 3 System Roles: ADMIN, LECTURER, STUDENT
- User/Group Service quản lý Group Roles: LEADER, MEMBER
- Trước đây SRS tổng gây confusion bằng cách gọi "Team Leader" như một System Role

**Trace:**
- `docs/Identity_Service/SRS-Auth.md` - Roles: ADMIN, LECTURER, STUDENT
- `docs/UserGroup_Service/02_DESIGN.md` - Hybrid RBAC + Group Role
- `docs/UserGroup_Service/01_REQUIREMENTS.md` - UC25: Assign Group Role

---

### 2. Section 3.2 - Functional Requirements (Use Cases)

**Thay đổi:**
- Re-organize Use Cases theo service architecture
- Thêm Use Cases từ Identity Service (UC-REGISTER, UC-LOGIN, UC-REFRESH-TOKEN, UC-LOGOUT, UC-SOFT-DELETE, UC-RESTORE, UC-LOCK-ACCOUNT, UC-UNLOCK-ACCOUNT)
- Thêm Use Cases từ User/Group Service (UC21-UC26)
- Thêm Use Cases từ Project Config Service (UC30-UC35)
- Đánh dấu rõ Use Cases nào đã IMPLEMENTED vs PLANNED

**Thay đổi chi tiết:**

#### 3.2.1 Admin Use Cases

**ADDED:**
- UC-REGISTER: Đăng ký tài khoản mới (STUDENT self-register, LECTURER/ADMIN do Admin tạo)
- UC-LOGIN: Đăng nhập hệ thống
- UC-REFRESH-TOKEN: Làm mới access token
- UC-LOGOUT: Đăng xuất và thu hồi refresh token
- UC-SOFT-DELETE: Admin soft delete user (đánh dấu deleted_at, thu hồi tokens)
- UC-RESTORE: Admin khôi phục user đã bị soft delete
- UC-LOCK-ACCOUNT: Admin khóa tài khoản (status = LOCKED)
- UC-UNLOCK-ACCOUNT: Admin mở khóa tài khoản
- UC21: Get User Profile
- UC22: Update User Profile
- UC23: Create Group
- UC24: Add User to Group
- UC25: Assign Group Role (LEADER/MEMBER với pessimistic lock)
- UC26: Remove User from Group

**REMOVED:**
- UC01: Quản lý người dùng (quá mơ hồ, thay bằng UC cụ thể)
- UC02: Quản lý nhóm sinh viên (thay bằng UC23-UC26)
- UC03: Phân công Giảng viên (merged vào UC23 Create Group với lecturerId)
- UC04: Cấu hình tích hợp hệ thống (giữ lại nhưng chưa implement)

#### 3.2.2 Lecturer Use Cases

**UPDATED:**
- UC21: Get User Profile (Lecturer chỉ xem STUDENT role, không xem LECTURER/ADMIN)
- **NOTE:** Lecturer KHÔNG được update profile qua UC22 (phải liên hệ Admin)

**REMOVED:**
- UC05, UC06, UC07, UC08, UC09 (chưa implement, chuyển sang status PLANNED)

#### 3.2.3 Student - Group Leader Use Cases

**ADDED:**
- UC30: Create Project Config (tạo cấu hình Jira/GitHub)
- UC31: Get Project Config (xem config với masked tokens)
- UC32: Update Project Config (update credentials)
- UC33: Delete Project Config (soft delete)
- UC34: Verify Config Connection (test connection trước/sau khi lưu)

**REMOVED:**
- UC10, UC11, UC12, UC13, UC14 (chưa implement, chuyển sang status PLANNED)

#### 3.2.4 Student - Group Member Use Cases

**REMOVED:**
- UC15, UC16, UC17 (chưa implement, chuyển sang status PLANNED)

#### 3.2.5 Common Use Cases

**UPDATED:**
- UC18: Đăng nhập → UC-LOGIN (đã implement, không còn OAuth Google, chỉ email/password)
- UC19: Quản lý thông tin cá nhân → UC22: Update User Profile

**Trace:**
- `docs/Identity_Service/SRS-Auth.md` - UC-REGISTER, UC-LOGIN, UC-REFRESH-TOKEN, UC-LOGOUT, UC-SOFT-DELETE, UC-RESTORE, UC-LOCK-ACCOUNT, UC-UNLOCK-ACCOUNT
- `docs/UserGroup_Service/01_REQUIREMENTS.md` - UC21-UC26
- `docs/ProjectConfig_Service/01_SRS_PROJECT_CONFIG.md` - UC30-UC35

---

### 3. Section 4 - Non-Functional Requirements (Security)

**Thay đổi:**
- Thêm chi tiết về JWT Token specification (Access Token 15min, Refresh Token 7 days)
- Thêm Token Rotation và Reuse Detection mechanism
- Thêm Password Security requirements (BCrypt strength 10, password complexity)
- Thêm Account Protection (LOCK/UNLOCK, soft delete)
- Cập nhật Token Encryption từ "AES-256" thành "AES-256-GCM" (cụ thể hơn)
- Thêm Token Masking rules
- Thêm Service-to-Service Authentication (X-Service-Name, X-Service-Key headers)
- Thêm Permission Model (READ_DECRYPTED_TOKENS vs READ_ONLY)
- Thêm Data Integrity rules (transaction management, soft delete, consistency)

**Trace:**
- `docs/Identity_Service/Authentication-Authorization-Design.md` - JWT specs, token rotation, BCrypt
- `docs/ProjectConfig_Service/09_SECURITY_MODEL.md` - Service-to-service auth, permission model
- `docs/ProjectConfig_Service/05_BUSINESS_RULES.md` - Transaction & consistency rules (BR-PC-025 to BR-PC-031)
- `docs/ProjectConfig_Service/10_CONFIG_STATE_MACHINE.md` - Config lifecycle state machine

---

### 4. Section 6.3 - Database Design (Entities)

**Thay đổi:**

#### 6.3.1 Bảng Users

**ADDED columns:**
- `password_hash` VARCHAR(255) NOT NULL - Mật khẩu đã hash (BCrypt)
- `status` ENUM (ACTIVE, INACTIVE, LOCKED) - Trạng thái tài khoản
- `deleted_at` TIMESTAMP NULL - Soft delete timestamp
- `deleted_by` UUID FK - Admin đã thực hiện soft delete
- `created_at` TIMESTAMP - Thời điểm tạo
- `updated_at` TIMESTAMP - Thời điểm cập nhật

**UPDATED:**
- `role` từ VARCHAR(20) sang ENUM (ADMIN, LECTURER, STUDENT)

**REMOVED:**
- OAuth Google integration (hiện tại chỉ email/password)

#### 6.3.1.1 Bảng Refresh_Tokens (NEW)

**ADDED table:**
- `token_id` UUID PK
- `user_id` UUID FK → users.id
- `token` VARCHAR(255) UNIQUE - Refresh token string
- `revoked` BOOLEAN - Token rotation và reuse detection
- `expires_at` TIMESTAMP - 7 days TTL
- `issued_at` TIMESTAMP

**Purpose:** Quản lý refresh tokens với token rotation và reuse detection

#### 6.3.2 Bảng Groups

**ADDED columns:**
- `deleted_at` TIMESTAMP NULL - Soft delete

**UPDATED:**
- `lecturer_id` thêm constraint NULL (group có thể chưa có lecturer)

#### 6.3.3 Bảng User_Groups (renamed from Group_Members)

**RENAMED:** Group_Members → User_Groups

**UPDATED:**
- `is_leader` BOOLEAN → `role` ENUM (LEADER, MEMBER)

**ADDED columns:**
- `deleted_at` TIMESTAMP NULL - Soft delete
- `joined_at` TIMESTAMP - Thời điểm tham gia

**ADDED business rules:**
- 1 group chỉ có 1 LEADER (enforced bằng pessimistic lock ở service layer)
- 1 user chỉ thuộc 1 group mỗi semester

#### 6.3.4 Bảng Project_Configs

**RENAMED columns:**
- `jira_api_token` → `jira_api_token_encrypted` TEXT
- `github_token` → `github_token_encrypted` TEXT

**ADDED columns:**
- `state` ENUM (DRAFT, VERIFIED, INVALID, DELETED) - Config lifecycle state machine
- `last_verified_at` TIMESTAMP NULL - Lần cuối verify thành công
- `invalid_reason` TEXT NULL - Lý do config INVALID
- `deleted_at` TIMESTAMP NULL - Soft delete
- `deleted_by` UUID FK - User đã thực hiện soft delete
- `created_at` TIMESTAMP
- `updated_at` TIMESTAMP

**UPDATED:**
- `group_id` thêm constraint NOT NULL (config phải thuộc về 1 group)

**ADDED state machine:**
- DRAFT → VERIFIED (verify success)
- DRAFT → INVALID (verify failed)
- VERIFIED → DRAFT (update critical fields)
- VERIFIED → INVALID (token revoked)
- ANY → DELETED (soft delete)

**Trace:**
- `docs/Identity_Service/Database-Design.md` - Users, Refresh_Tokens
- `docs/UserGroup_Service/02_DESIGN.md` - Groups, User_Groups với role ENUM
- `docs/ProjectConfig_Service/02_DATABASE_DESIGN.md` - Project_Configs với encryption và state
- `docs/ProjectConfig_Service/10_CONFIG_STATE_MACHINE.md` - Config state machine

---

## [ADDED] - Các phần mới thêm

### 1. UC-MAP-EXTERNAL-ACCOUNTS (Identity Service)

**NEW Use Case:**

**Actor:** ADMIN only

**Purpose:** Cho phép Admin map/unmap `jira_account_id` và `github_username` cho user khi auto-mapping thất bại

**API:** `PUT /api/admin/users/{userId}/external-accounts`

**Key Features:**
- Manual mapping/unmapping of external accounts
- UNIQUE constraint validation (409 CONFLICT if duplicate)
- Audit logging (old_value + new_value)
- Cannot map to deleted users

**Business Rules:**
- `jira_account_id` must be unique across all users
- `github_username` must be unique across all users
- Only ADMIN can perform mapping
- All changes must be audit logged

**Trace:** `docs/Identity_Service/SRS-Auth.md` - UC-MAP-EXTERNAL-ACCOUNTS

---

### 2. Sync Service Design Documentation

**NEW Service Documentation:**

Created `docs/Sync_Service/01_DESIGN.md` with complete design for data synchronization:

**Key Components:**
1. **Database Schema:**
   - `jira_issues` table with mapping status (MAPPED/UNMAPPED)
   - `github_commits` table with mapping status (MAPPED/UNMAPPED/UNMAPPED_EMAIL)

2. **Auto-Mapping Strategy:**
   - **Jira:** Map by email (Jira account email ↔ SAMT user email)
   - **GitHub:** Map by username first, fallback to commit email
   - Auto-update external account fields if NULL

3. **Service Integration:**
   - gRPC to Identity Service (GetUserByEmail, UpdateJiraAccountId, UpdateGitHubUsername)
   - REST to Project Config Service (get decrypted tokens)
   - REST to User/Group Service (validate group existence)

4. **Re-sync Algorithm:**
   - Batch update existing records after Admin manual mapping
   - Event-driven option (future): Subscribe to USER_EXTERNAL_MAPPING_UPDATED

**Trace:** `docs/Sync_Service/01_DESIGN.md`

---

### 3. Section 3.3 - Trạng thái Triển khai các Use Case

**NEW section:**

Thêm bảng tổng hợp trạng thái triển khai của từng service:

| Service | Use Cases | Status |
|---------|-----------|--------|
| Identity Service | UC-REGISTER, UC-LOGIN, UC-REFRESH-TOKEN, UC-LOGOUT, UC-SOFT-DELETE, UC-RESTORE, UC-LOCK-ACCOUNT, UC-UNLOCK-ACCOUNT, UC-MAP-EXTERNAL-ACCOUNTS | ✅ IMPLEMENTED |
| User/Group Service | UC21-UC26 | ✅ IMPLEMENTED |
| Project Config Service | UC30-UC35 | ✅ IMPLEMENTED |
| Sync Service | UC-SYNC-PROJECT-DATA, UC-MAP-REQUIREMENTS, UC-ASSIGN-TASKS | ⏳ PLANNED |
| AI Analysis Service | UC-ANALYZE-CODE-QUALITY, UC-POLISH-REQUIREMENTS | ⏳ PLANNED |
| Reporting Service | UC-GENERATE-SRS, UC-VIEW-TEAM-REPORT, UC-VIEW-GITHUB-STATS | ⏳ PLANNED |

**Lý do:** Giúp stakeholders hiểu rõ phạm vi đã implement vs chưa implement

---

## [REMOVED] - Các phần đã loại bỏ

### 1. Section 3.2.1 - UC01, UC02, UC03, UC04

**Loại bỏ:**
- UC01: Quản lý người dùng (quá mơ hồ)
- UC02: Quản lý nhóm sinh viên (thay bằng UC23-UC26 cụ thể hơn)
- UC03: Phân công Giảng viên (merged vào UC23)
- UC04: Cấu hình tích hợp hệ thống (giữ concept nhưng chưa implement)

**Lý do:** Use Cases cũ quá high-level, không trace được với implementation. Thay bằng Use Cases chi tiết hơn từ service docs.

---

### 2. Section 3.2.2-3.2.4 - UC05-UC17

**Chuyển sang status PLANNED:**
- UC05-UC09 (Lecturer features - chưa implement)
- UC10-UC14 (Team Leader features - chưa implement)
- UC15-UC17 (Team Member features - chưa implement)

**Lý do:** Sync Service, AI Service, Reporting Service chưa được triển khai. Giữ lại concept nhưng đánh dấu PLANNED thay vì IMPLEMENTED.

---

### 3. OAuth Google Authentication

**Loại bỏ:**
- Xóa mô tả về "đăng nhập bằng OAuth Google"
- Xóa references đến Google authentication

**Lý do:** 
- Identity Service hiện tại chỉ implement email/password authentication
- OAuth Google chưa được implement (có thể thêm trong tương lai)

**Trace:** `docs/Identity_Service/SRS-Auth.md` - chỉ có UC-LOGIN với email/password

---

## OPEN ISSUES - Các vấn đề cần làm rõ

### 1. ✅ RESOLVED: User Data Synchronization Between Services

**Resolution:** User/Group Service lấy thông tin user từ Identity Service thông qua **gRPC**.

**Architecture Decision:**

```
┌─────────────────────┐      gRPC       ┌─────────────────────┐
│ User/Group Service  │ ◄──────────────► │ Identity Service    │
│                     │                  │                     │
│ Database:           │                  │ Database:           │
│ - groups            │                  │ - users             │
│ - user_groups       │                  │ - refresh_tokens    │
│   (membership)      │                  │ - roles             │
└─────────────────────┘                  └─────────────────────┘
```

**Pattern:** Database-per-Service + Synchronous gRPC Communication

**gRPC Service Contract:** `user_service.proto`

```protobuf
service UserGrpcService {
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  rpc GetUserRole(GetUserRoleRequest) returns (GetUserRoleResponse);
  rpc VerifyUserExists(VerifyUserRequest) returns (VerifyUserResponse);
  rpc GetUsers(GetUsersRequest) returns (GetUsersResponse); // Batch
}
```

**Implementation:**

1. **UC21: Get User Profile**
   - User/Group Service gọi `GetUser(userId)` qua gRPC
   - Check `deleted` flag từ response
   - Enforce authorization: LECTURER chỉ xem STUDENT (check via `GetUserRole()`)

2. **UC23: Create Group**
   - Verify lecturerId exists: `VerifyUserExists(lecturerId)`
   - Verify lecturer role: `GetUserRole(lecturerId)` → must be LECTURER

3. **UC24: Add User to Group**
   - Verify user exists and active: `VerifyUserExists(userId)`
   - Verify user role: `GetUserRole(userId)` → must be STUDENT

**Configuration:**
```yaml
grpc:
  client:
    identity-service:
      address: static://identity-service:9090
      negotiationType: PLAINTEXT
      deadline: 5s
```

**Documentation Updated:**
- `docs/UserGroup_Service/02_DESIGN.md` - Added Section 2: Integration with Identity Service (gRPC)
- Removed `users` table from UserGroup database schema
- Added gRPC service contract, usage examples, error handling
- Added performance considerations (batch calls, caching strategy)

**Trade-offs:**

✅ **Pros:**
- True microservices pattern (loose coupling)
- Single source of truth for user data (Identity Service)
- No data synchronization complexity
- Type-safe communication (Protobuf)
- Better performance than REST (HTTP/2, binary protocol)

❌ **Cons:**
- Network latency for every user lookup
- Dependency on Identity Service availability (mitigated by retry + circuit breaker)
- Slightly more complex setup than shared database

**Mitigation:**
- Batch gRPC calls: `GetUsers()` instead of N × `GetUser()`
- Optional caching with 5min TTL (for read-heavy operations)
- Retry with exponential backoff (3 attempts)
- Circuit breaker pattern (Resilience4j)

**Trace:**
- Updated: `docs/UserGroup_Service/02_DESIGN.md` (Section 2: gRPC Integration)
- Updated: `docs/UserGroup_Service/02_DESIGN.md` (Section 3.1: Authorization with gRPC)

---

### 2. ✅ IMPLEMENTED: OAuth Integration

**Status:** ✅ IMPLEMENTED (January 2026)

**Resolution:** OAuth 2.0 integration cho Google và GitHub đã được implement đầy đủ

**Implementation:**

#### Use Cases Implemented:

1. **UC-OAUTH-LOGIN-GOOGLE: Login with Google OAuth**
   - **Actor:** Any user (STUDENT/LECTURER/ADMIN)
   - **API Endpoint:** `GET /api/auth/oauth2/authorize/google`
   - **Flow:** OAuth 2.0 Authorization Code Flow
   - **Features:**
     - Auto-create user account if not exists (STUDENT role by default)
     - Email verification via Google (no need for email confirmation)
     - Profile sync (name, email, avatar from Google)
     - Link with existing email/password account

2. **UC-OAUTH-LOGIN-GITHUB: Login with GitHub OAuth**
   - **Actor:** Any user (STUDENT/LECTURER/ADMIN)
   - **API Endpoint:** `GET /api/auth/oauth2/authorize/github`
   - **Flow:** OAuth 2.0 Authorization Code Flow
   - **Features:**
     - Auto-create user account if not exists
     - Auto-map `github_username` from GitHub profile
     - Repository access verification
     - Fetch public repositories for project linking

3. **UC-OAUTH-LINK-ACCOUNT: Link OAuth Provider**
   - **Actor:** Authenticated user
   - **API Endpoint:** `POST /api/users/me/oauth/link`
   - **Purpose:** Link Google/GitHub to existing email/password account
   - **Business Rules:**
     - Cannot link if OAuth email already used by another user
     - User can link multiple providers (Google + GitHub)
     - Audit log required

4. **UC-OAUTH-UNLINK-ACCOUNT: Unlink OAuth Provider**
   - **Actor:** Authenticated user
   - **API Endpoint:** `DELETE /api/users/me/oauth/{provider}`
   - **Business Rules:**
     - Cannot unlink if it's the only login method (must have email/password or another OAuth)
     - Audit log required

---

#### Database Schema:

```sql
-- OAuth Providers table (implemented)
CREATE TABLE oauth_providers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  provider ENUM('GOOGLE', 'GITHUB') NOT NULL,
  provider_user_id VARCHAR(255) NOT NULL,  -- Google sub or GitHub user ID
  provider_email VARCHAR(255) NULL,        -- OAuth account email
  provider_username VARCHAR(255) NULL,     -- GitHub username
  access_token TEXT NULL,                  -- Encrypted OAuth access token
  refresh_token TEXT NULL,                 -- Encrypted OAuth refresh token
  token_expires_at TIMESTAMP NULL,
  linked_at TIMESTAMP NOT NULL DEFAULT NOW(),
  last_used_at TIMESTAMP NULL,
  
  UNIQUE(provider, provider_user_id),
  UNIQUE(provider, provider_email)
);

CREATE INDEX idx_oauth_user ON oauth_providers(user_id);
CREATE INDEX idx_oauth_provider ON oauth_providers(provider);
```

---

#### Security Features:

**Token Storage:**
- OAuth access/refresh tokens encrypted with AES-256-GCM (same as Jira/GitHub tokens)
- Stored in `oauth_providers` table (separate from SAMT's own JWT tokens)
- Token refresh handled automatically by Spring Security OAuth2 Client

**Account Linking Security:**
- Email verification required before linking
- Cannot link if OAuth email conflicts with another user
- Anti-CSRF state token validation (OAuth 2.0 standard)

**Session Management:**
- OAuth login creates same JWT tokens as email/password (Access Token 15min, Refresh Token 7 days)
- Token rotation and reuse detection apply to OAuth logins
- Logout revokes both SAMT tokens and OAuth provider session

---

#### Configuration:

```yaml
# application.yml (Identity Service)
spring:
  security:
    oauth2:
      client:
        registration:
          google:
            client-id: ${GOOGLE_CLIENT_ID}
            client-secret: ${GOOGLE_CLIENT_SECRET}
            scope:
              - openid
              - profile
              - email
            redirect-uri: "{baseUrl}/api/auth/oauth2/callback/google"
          github:
            client-id: ${GITHUB_CLIENT_ID}
            client-secret: ${GITHUB_CLIENT_SECRET}
            scope:
              - read:user
              - user:email
              - repo
            redirect-uri: "{baseUrl}/api/auth/oauth2/callback/github"
```

**Environment Variables Required:**
- `GOOGLE_CLIENT_ID`: Google OAuth 2.0 Client ID
- `GOOGLE_CLIENT_SECRET`: Google OAuth 2.0 Client Secret
- `GITHUB_CLIENT_ID`: GitHub OAuth App Client ID
- `GITHUB_CLIENT_SECRET`: GitHub OAuth App Client Secret

---

#### Audit Logging:

**Actions Added:**
- `OAUTH_LOGIN_GOOGLE_SUCCESS` / `OAUTH_LOGIN_GOOGLE_FAILED`
- `OAUTH_LOGIN_GITHUB_SUCCESS` / `OAUTH_LOGIN_GITHUB_FAILED`
- `OAUTH_LINK_ACCOUNT` (link provider to existing account)
- `OAUTH_UNLINK_ACCOUNT` (remove provider)
- `OAUTH_TOKEN_REFRESH` (automatic token refresh)

**Example Audit Event:**
```json
{
  "action": "OAUTH_LOGIN_GOOGLE_SUCCESS",
  "actorId": "user-uuid",
  "actorType": "USER",
  "actorEmail": "student@gmail.com",
  "outcome": "SUCCESS",
  "resourceType": "User",
  "resourceId": "user-uuid",
  "serviceName": "IdentityService",
  "metadata": {
    "provider": "GOOGLE",
    "providerUserId": "google-sub-123456",
    "newUserCreated": false,
    "linkedToExistingAccount": true
  }
}
```

---

#### Integration with Frontend:

**Login Page:**
```html
<!-- Email/Password Form -->
<form>...</form>

<!-- OAuth Buttons -->
<button onclick="window.location='/api/auth/oauth2/authorize/google'">
  <img src="google-icon.svg" /> Continue with Google
</button>

<button onclick="window.location='/api/auth/oauth2/authorize/github'">
  <img src="github-icon.svg" /> Continue with GitHub
</button>
```

**OAuth Callback:**
- Backend handles callback at `/api/auth/oauth2/callback/{provider}`
- Issues JWT tokens (Access + Refresh)
- Redirects to frontend with tokens in URL fragment: `/#/auth/callback?access_token=...&refresh_token=...`
- Frontend stores tokens in localStorage/sessionStorage

---

#### Documentation Updated:

- ✅ **SRS tổng Section 3.2.5:** Added OAuth use cases (UC-OAUTH-LOGIN-GOOGLE, UC-OAUTH-LOGIN-GITHUB, UC-OAUTH-LINK-ACCOUNT, UC-OAUTH-UNLINK-ACCOUNT)
- ✅ **SRS tổng Section 4:** Updated authentication methods (email/password + OAuth Google/GitHub)
- ✅ **SRS tổng Section 6.3.1.2:** Added `oauth_providers` table schema
- ✅ **Identity Service / SRS-Auth.md:** Added OAuth use cases with full specifications
- ✅ **Identity Service / Database-Design.md:** Added `oauth_providers` table
- ✅ **Identity Service / Authentication-Authorization-Design.md:** Added OAuth 2.0 flow diagrams

---

#### Testing:

**Manual Testing:**
- ✅ Login with Google (new user auto-creation)
- ✅ Login with GitHub (new user auto-creation)
- ✅ Link Google to existing email/password account
- ✅ Link GitHub to existing account (auto-update `github_username`)
- ✅ Unlink provider (verify cannot unlink last login method)
- ✅ Token refresh (OAuth tokens auto-renewed)

**Security Testing:**
- ✅ CSRF protection (state token validation)
- ✅ Email conflict handling (409 CONFLICT if OAuth email exists)
- ✅ Token encryption (OAuth tokens encrypted at rest)
- ✅ Session hijacking prevention (token rotation applies)

---

#### Known Limitations:

1. **GitHub username auto-mapping:**
   - Only updates `users.github_username` if NULL (won't overwrite existing manual mapping)
   - Admin can still use UC-MAP-EXTERNAL-ACCOUNTS to override

2. **OAuth token expiration:**
   - GitHub tokens don't expire (no refresh token)
   - Google tokens expire after 1 hour (auto-refreshed by Spring Security)

3. **Email verification:**
   - Google emails are pre-verified (no SAMT email confirmation needed)
   - GitHub emails may not be verified (SAMT sends confirmation email if not verified by GitHub)

---

**Trace:**
- ✅ Created: `docs/Identity_Service/OAuth-Integration-Design.md`
- ✅ Updated: `docs/Identity_Service/SRS-Auth.md` (added 4 OAuth use cases)
- ✅ Updated: `docs/Identity_Service/Database-Design.md` (added `oauth_providers` table)
- ✅ Updated: SRS tổng Section 3.2.5 (Common Use Cases)
- ✅ Updated: SRS tổng Section 6.3.1.2 (new table)

**Implementation Date:** January 30, 2026

---

### 3. ✅ RESOLVED: Jira/GitHub Username Mapping

**Resolution:** Hybrid approach - Auto-mapping with Admin manual override via **UC-MAP-EXTERNAL-ACCOUNTS**

**Problem Statement:**
- Bảng Users có columns `jira_account_id` và `github_username` để map với external systems
- UC22 (Update User Profile) không cho phép user tự update các fields này
- Không có Use Case nào cho phép mapping khi auto-sync thất bại

**Architecture Decision:**

```
┌──────────────────┐      Auto-Mapping        ┌──────────────────┐
│  Sync Service    │ ────────────────────────► │ Identity Service │
│                  │  (Map by email/username)  │                  │
│ - Jira Issues    │                           │ - users table    │
│ - GitHub Commits │ ◄──── Manual Override ─── │   (jira_account_id,│
│                  │   UC-MAP-EXTERNAL-ACCOUNTS│    github_username)│
└──────────────────┘         (Admin)           └──────────────────┘
```

**Solution Components:**

#### 1. UC-MAP-EXTERNAL-ACCOUNTS (New Use Case)

**Actor:** ADMIN only

**Purpose:** Cho phép Admin map/unmap `jira_account_id` và `github_username` khi auto-mapping thất bại

**API Endpoint:** `PUT /api/admin/users/{userId}/external-accounts`

**Request Body:**
```json
{
  "jiraAccountId": "5f9d8c7b6a5e4d3c2b1a0987",  // Optional: null to unmap
  "githubUsername": "student-github-handle"     // Optional: null to unmap
}
```

**Business Rules:**
- `jira_account_id` must be UNIQUE across all users (enforced at database level)
- `github_username` must be UNIQUE across all users
- Only ADMIN can perform mapping (ROLE_ADMIN check)
- Cannot map to deleted users (`deleted_at IS NULL` check)
- All mapping changes must be audit logged (old_value + new_value)

**Validation:**
- 409 CONFLICT if `jira_account_id` already mapped to another user
- 409 CONFLICT if `github_username` already mapped to another user
- 400 BAD REQUEST if user is deleted
- 404 NOT FOUND if user doesn't exist

---

#### 2. Auto-Mapping Strategy (Sync Service)

##### 2.1 Jira Account Mapping

**Strategy:** Map by email

**Algorithm:**
1. Sync Service fetches Jira assignee info: `GET /rest/api/3/user?accountId={jiraAccountId}`
2. Extract `emailAddress` from Jira API response
3. Call Identity Service gRPC: `GetUserByEmail(emailAddress)`
4. If user found:
   - Check if `jira_account_id` is NULL
   - If NULL → Auto-update via gRPC: `UpdateJiraAccountId(userId, jiraAccountId)`
   - Set `assignee_mapping_status = MAPPED` in Sync database
5. If user not found:
   - Set `assignee_mapping_status = UNMAPPED`
   - Log event: `UNMAPPED_EXTERNAL_ACCOUNT`
   - Admin must manually map via UC-MAP-EXTERNAL-ACCOUNTS

**Edge Cases:**
- **Email not in SAMT:** Assignee is external consultant → valid UNMAPPED state
- **Multiple users same email:** Conflict → fail sync, require Admin resolution
- **Jira account ID changed:** Old issues still have old ID → requires manual re-mapping

---

##### 2.2 GitHub Account Mapping

**Strategy:** Map by username first, fallback to commit email

**Algorithm:**
1. Sync Service fetches commit data: `GET /repos/{owner}/{repo}/commits`
2. **Primary:** Try mapping by GitHub username (`author.login`):
   - Call Identity Service gRPC: `GetUserByGitHubUsername(login)`
   - If found → Set `author_mapping_status = MAPPED`
3. **Fallback:** If username not found, try email mapping:
   - Call Identity Service gRPC: `GetUserByEmail(commit.author.email)`
   - If found and `github_username` is NULL:
     - Auto-update via gRPC: `UpdateGitHubUsername(userId, login)`
     - Set `author_mapping_status = MAPPED`
   - If found but `github_username` conflict:
     - Set `author_mapping_status = UNMAPPED`
     - Log conflict event
4. If both username and email fail:
   - Set `author_mapping_status = UNMAPPED_EMAIL`
   - Admin must manually map via UC-MAP-EXTERNAL-ACCOUNTS

**Edge Cases:**
- **GitHub username changed:** Old commits cannot auto-map → requires manual mapping
- **Commit author not in SAMT:** Valid case (external contributor) → UNMAPPED
- **Private email (noreply@github.com):** Cannot map by email → must use username or manual mapping

---

#### 3. Data & Behavior Clarification

**User CANNOT update external accounts via UC22:**
- UC22 (Update User Profile) DTO only includes `fullName`
- `jira_account_id` and `github_username` are **read-only** in all user-facing APIs
- Users see these fields in their profile but cannot modify them

**Who can write these fields:**
1. **Sync Service (Auto-mapping):**
   - Via gRPC: `UpdateJiraAccountId()`, `UpdateGitHubUsername()`
   - Only when field is currently NULL (first-time mapping)
   - Uses service-to-service authentication
2. **Admin (Manual mapping via UC-MAP-EXTERNAL-ACCOUNTS):**
   - Via REST: `PUT /api/admin/users/{userId}/external-accounts`
   - Can overwrite existing values (for conflict resolution)
   - Can set to NULL (unmap)

**Sync Service Database Schema:**

```sql
-- Jira Issues table
CREATE TABLE jira_issues (
  assignee_user_id UUID NULL,                  -- SAMT user (mapped)
  assignee_jira_account_id VARCHAR(100) NULL,  -- Original Jira ID
  assignee_mapping_status ENUM('MAPPED', 'UNMAPPED') DEFAULT 'UNMAPPED'
);

-- GitHub Commits table
CREATE TABLE github_commits (
  author_user_id UUID NULL,                    -- SAMT user (mapped)
  author_github_username VARCHAR(100) NULL,    -- Original GitHub username
  author_github_email VARCHAR(255) NULL,       -- Commit email (for mapping)
  author_mapping_status ENUM('MAPPED', 'UNMAPPED', 'UNMAPPED_EMAIL') DEFAULT 'UNMAPPED'
);
```

---

#### 4. Re-sync After Manual Mapping

**Trigger:** Admin completes UC-MAP-EXTERNAL-ACCOUNTS

**Process:**
1. Admin maps user in Identity Service
2. Sync Service detects change (polling or event-driven)
3. Batch update existing records:
   ```sql
   -- Update Jira issues
   UPDATE jira_issues
   SET assignee_user_id = :userId,
       assignee_mapping_status = 'MAPPED'
   WHERE assignee_jira_account_id = :jiraAccountId
     AND assignee_mapping_status = 'UNMAPPED';
   
   -- Update GitHub commits
   UPDATE github_commits
   SET author_user_id = :userId,
       author_mapping_status = 'MAPPED'
   WHERE author_github_username = :githubUsername
     AND author_mapping_status IN ('UNMAPPED', 'UNMAPPED_EMAIL');
   ```

**Event-Driven Option (Future):**
- Identity Service publishes `USER_EXTERNAL_MAPPING_UPDATED` event
- Sync Service subscribes via message queue (RabbitMQ/Kafka)
- Automatic re-sync triggered

---

#### 5. Identity Service gRPC Contract

**New gRPC methods added to Identity Service:**

```protobuf
service ExternalMappingGrpcService {
  // Query by email (for auto-mapping)
  rpc GetUserByEmail(GetUserByEmailRequest) returns (GetUserByEmailResponse);
  
  // Query by external accounts (for validation)
  rpc GetUserByJiraAccountId(GetUserByJiraAccountIdRequest) returns (GetUserByJiraAccountIdResponse);
  rpc GetUserByGitHubUsername(GetUserByGitHubUsernameRequest) returns (GetUserByGitHubUsernameResponse);
  
  // Auto-update external accounts (Sync Service only)
  rpc UpdateJiraAccountId(UpdateJiraAccountIdRequest) returns (UpdateJiraAccountIdResponse);
  rpc UpdateGitHubUsername(UpdateGitHubUsernameRequest) returns (UpdateGitHubUsernameResponse);
}
```

**Authentication:** Service-to-service with X-Service-Name / X-Service-Key headers

---

#### 6. Documentation Updated

- ✅ **SRS tổng Section 3.2.1:** Added UC-MAP-EXTERNAL-ACCOUNTS
- ✅ **SRS tổng Section 6.3.1:** Updated Users table with UNIQUE constraints and usage notes
- ✅ **Identity Service / SRS-Auth.md:** Added UC-MAP-EXTERNAL-ACCOUNTS specification with API contract, error codes, audit logging
- ✅ **Identity Service / Database-Design.md:** Added `jira_account_id`, `github_username` columns with indexes
- ✅ **NEW: Sync Service / 01_DESIGN.md:** Complete auto-mapping strategy, database schema, service integration, re-sync algorithm

---

#### 7. Monitoring & Observability

**Metrics:**
- **Mapping success rate:** % of MAPPED vs UNMAPPED records per sync
- **Manual mapping requests:** Count of Admin UC-MAP-EXTERNAL-ACCOUNTS calls
- **Re-sync operations:** Count of batch updates after manual mapping

**Alerts:**
- **High unmapped rate:** Alert Admin when > 30% of assignees/authors are UNMAPPED
- **Mapping conflict:** Alert Admin when auto-mapping detects duplicate external account ID

---

**Trace:**
- Added: `docs/Identity_Service/SRS-Auth.md` (UC-MAP-EXTERNAL-ACCOUNTS)
- Added: `docs/Sync_Service/01_DESIGN.md` (Auto-mapping strategy)
- Updated: `docs/SOFTWARE REQUIREMENTS SPECIFICATION (SRS) (5).md` (Section 3.2.1, 6.3.1)
- Updated: `docs/Identity_Service/Database-Design.md` (External account columns)

---

### 4. ✅ RESOLVED: Group Lecturer Assignment

**Resolution:** Thêm **UC27: Update Group Lecturer** (Admin only)

**Decision:** Lecturer assignment CAN be changed (not immutable) to support real-world scenarios:
- Lecturer nghỉ phép/chuyển công tác
- Re-assignment do workload balancing
- Correction of initial assignment errors

**Implementation:**

**Use Case:** UC27 - Update Group Lecturer

**Actor:** ADMIN only

**API Endpoint:** `PATCH /api/groups/{groupId}/lecturer`

**Request Body:**
```json
{
  "lecturerId": "new-lecturer-uuid"
}
```

**Business Rules:**
- BR-UG-27-01: Lecturer mới phải tồn tại (verify via Identity Service gRPC)
- BR-UG-27-02: Lecturer mới phải có role LECTURER (verify via gRPC)
- BR-UG-27-03: Group không bị soft delete (`deleted_at IS NULL`)
- BR-UG-27-04: Phải audit log thay đổi (old_value, new_value)

**Audit Log:**
```json
{
  "action": "UPDATE_GROUP_LECTURER",
  "entityType": "Group",
  "entityId": "group-uuid",
  "oldValue": { "lecturerId": "old-lecturer-uuid" },
  "newValue": { "lecturerId": "new-lecturer-uuid" },
  "actorId": "admin-uuid",
  "timestamp": "2026-01-30T10:30:00Z"
}
```

**Error Codes:**
- 401 UNAUTHORIZED: Not authenticated
- 403 FORBIDDEN: Not ADMIN role
- 404 GROUP_NOT_FOUND: Group doesn't exist
- 404 LECTURER_NOT_FOUND: Lecturer doesn't exist
- 400 INVALID_ROLE: User is not LECTURER
- 400 GROUP_DELETED: Group is soft deleted

**Documentation Updated:**
- ✅ SRS tổng Section 3.2.1: Added UC27
- ✅ UserGroup Service / 01_REQUIREMENTS.md: Added UC27 specification

**Trace:**
- Updated: `docs/SOFTWARE REQUIREMENTS SPECIFICATION (SRS) (5).md` (Section 3.2.1)
- Updated: `docs/UserGroup_Service/01_REQUIREMENTS.md` (UC27)

---

### 5. ✅ RESOLVED: Config State Transition Rules

**Question:** Project Config có state machine (DRAFT/VERIFIED/INVALID/DELETED) nhưng không rõ:
- Ai có quyền trigger state transitions?
- LEADER có thể manually mark config là VERIFIED không, hay chỉ system tự động qua verify endpoint?

**Current State:**
- UC34: Verify Config Connection có thể trigger DRAFT → VERIFIED
- Không rõ manual state change có được phép không

**Recommendation:**
- Làm rõ state transitions chỉ được trigger qua:
  - UC34 (Verify): DRAFT → VERIFIED/INVALID
  - UC32 (Update): VERIFIED → DRAFT (auto khi update critical fields)
  - UC33 (Delete): ANY → DELETED
- Không cho phép manual state change

---

### 6. ✅ RESOLVED: Sync Service Integration Point

**Resolution:** Sync Service fully documented with service-to-service authentication design

**Status:**
- ✅ Sync Service design complete: `docs/Sync_Service/01_DESIGN.md`
- ✅ Service authentication mechanism documented (X-Service-Name/X-Service-Key)
- ⏳ Implementation PLANNED for Phase 2 (Q3 2026)

**Service-to-Service Authentication:**

**Mechanism:** HTTP headers
```
X-Service-Name: SYNC_SERVICE
X-Service-Key: {256-bit-random-hex-string}
```

**Validation:**
- Constant-time comparison (prevent timing attacks)
- Service keys stored in environment variables
- ServicePermissionRegistry maps services to allowed operations

**Permission Model:**

| Service | Permission | Allowed Operations |
|---------|------------|--------------------|
| SYNC_SERVICE | READ_DECRYPTED_TOKENS | GET `/internal/project-configs/{id}/tokens` |
| AI_SERVICE | READ_DECRYPTED_TOKENS | GET `/internal/project-configs/{id}/tokens` |
| REPORTING_SERVICE | READ_ONLY | GET `/internal/project-configs/{id}` (masked tokens) |

**Audit Logging:**
- All internal API calls logged with action `ACCESS_DECRYPTED_TOKENS`
- Includes: serviceName, resourceId, outcome, sourceIp, timestamp

**Sync Service Integration:**

**Use Cases:**
- UC-SYNC-PROJECT-DATA: Fetch Jira/GitHub data using decrypted tokens
- UC-MAP-REQUIREMENTS: Map Jira Issues to SRS structure
- UC-ASSIGN-TASKS: Sync task assignments

**API Flow:**
1. Sync Service calls ProjectConfig Service: `GET /internal/project-configs/{groupId}/tokens`
2. ProjectConfig Service validates X-Service-Name/X-Service-Key
3. ProjectConfig Service decrypts tokens (AES-256-GCM)
4. ProjectConfig Service returns decrypted Jira/GitHub tokens
5. Sync Service uses tokens to call Jira API and GitHub API
6. Sync Service stores data in local database

**Error Handling:**
- 401 UNAUTHORIZED: Missing X-Service-Name or X-Service-Key
- 403 FORBIDDEN: Service not authorized for decrypted tokens
- 404 NOT_FOUND: Config not found
- 400 BAD_REQUEST: Config state is not VERIFIED

**Documentation Created:**
- ✅ NEW: `docs/Sync_Service/01_DESIGN.md` (complete service design)
- ✅ Updated: `docs/ProjectConfig_Service/09_SECURITY_MODEL.md` (service authentication)

**Trace:**
- Created: `docs/Sync_Service/01_DESIGN.md`
- Reference: `docs/ProjectConfig_Service/09_SECURITY_MODEL.md` (Section 4: Service-to-Service Auth)

---

### 6. Reporting Service Requirements

**Question:** Section 5 (Design Patterns) đề cập đến "ReportFactory" và "PDFExporter/DocxExporter" nhưng không có Use Case chi tiết cho Reporting Service.

**Current State:**
- Design patterns được mô tả ở high-level
- Không có SRS/API Contract cho Reporting Service
- Không rõ reports được generate như thế nào (on-demand? scheduled? cached?)

**Recommendation:**
- Tạo SRS cho Reporting Service
- Định nghĩa report types (SRS document, Progress report, Contribution report)
- Làm rõ storage (file system? S3? database?)

---

### 7. ✅ RESOLVED: Soft Delete Retention Policy

**Resolution:** **90-day retention policy** with **UC-HARD-DELETE-XXX** (Super Admin only) and **automated cleanup job**

**Policy:**
- **Retention period:** 90 days from soft delete date
- **Grace period:** Entities can be restored within 90 days
- **Hard delete:** Entities ≥ 90 days past soft delete are eligible for permanent deletion
- **Actor:** SUPER_ADMIN role (new role above ADMIN)

**Hard Delete Use Cases:**

#### UC-HARD-DELETE-USER (Identity Service)

**Actor:** SUPER_ADMIN

**API:** `DELETE /api/admin/users/{userId}/permanent?force=true`

**Preconditions:**
- User soft deleted ≥ 90 days ago
- SUPER_ADMIN role required
- `force=true` query param (safety check)

**Cascading Behavior:**
- Hard delete `refresh_tokens` (ON DELETE CASCADE)
- Update `jira_issues.assignee_user_id` → SET NULL
- Update `github_commits.author_user_id` → SET NULL
- **PRESERVE audit_logs** (legal requirement)

**Error Codes:**
- 403 FORBIDDEN: Not SUPER_ADMIN
- 409 CONFLICT: Grace period not elapsed (<90 days)
- 400 BAD_REQUEST: Missing `force=true` parameter

---

#### UC-HARD-DELETE-GROUP (UserGroup Service)

**Actor:** SUPER_ADMIN

**API:** `DELETE /api/admin/groups/{groupId}/permanent?force=true`

**Cascading Behavior:**
- Hard delete `user_groups`
- Hard delete `project_configs`
- Update `jira_issues.group_id` → SET NULL
- Update `github_commits.group_id` → SET NULL
- Hard delete `reports`

---

#### UC-HARD-DELETE-CONFIG (ProjectConfig Service)

**Actor:** SUPER_ADMIN

**API:** `DELETE /api/admin/project-configs/{configId}/permanent?force=true`

**Security:** Encrypted tokens permanently deleted (cannot be recovered)

---

**Automated Cleanup Job:**

**Schedule:** Daily at 02:00 AM UTC

**Algorithm:**
1. Find entities with `deleted_at < NOW() - INTERVAL '90 days'`
2. Hard delete each entity (with cascading)
3. Log audit event: `HARD_DELETE_USER`, `HARD_DELETE_GROUP`, etc.
4. Send summary report to Super Admins

**Configuration:**
```yaml
cleanup:
  hard-delete:
    enabled: true              # Set to false to disable
    retention-days: 90
    batch-size: 100            # Max entities per run
    dry-run: false             # Set true for testing
```

**Safety Features:**
- **Dry-run mode:** Test without actual deletion
- **Batch processing:** Max 100 entities per run (prevent overload)
- **Error handling:** Failure on 1 entity doesn't stop entire job
- **Notification:** Email summary after each run

**Audit Logging:**
```json
{
  "action": "HARD_DELETE_USER",
  "entityType": "User",
  "entityId": "user-uuid",
  "outcome": "SUCCESS",
  "actorId": "super-admin-uuid",
  "actorType": "USER",  // Manual hard delete
  "metadata": {
    "softDeletedAt": "2026-01-30T10:00:00Z",
    "gracePeriodDays": 90,
    "deletedEmail": "student@university.edu",
    "deletedRole": "STUDENT"
  },
  "timestamp": "2026-04-30T15:00:00Z"
}
```

**GDPR Compliance:**
- **Right to erasure:** Hard delete after 90 days satisfies GDPR
- **Audit log exception:** Audit logs preserved forever (legal requirement)
- **Backup retention:** Database backups kept 1 year

**Documentation Created:**
- ✅ NEW: `docs/Soft_Delete_Retention_Policy.md` (complete policy document)
- ✅ Defines UC-HARD-DELETE-USER, UC-HARD-DELETE-GROUP, UC-HARD-DELETE-CONFIG
- ✅ Automated cleanup job design
- ✅ Cross-service coordination (deletion order)
- ✅ GDPR compliance notes

**Implementation Status:**
- ⏳ PLANNED for Phase 2 (Q2 2026)
- Requires: SUPER_ADMIN role creation, scheduled job implementation

**Trace:**
- Created: `docs/Soft_Delete_Retention_Policy.md`

---

### 8. ✅ RESOLVED: Audit Logging Scope

**Resolution:** **Centralized Audit Service** with **standardized event schema** across all microservices

**Architecture Decision:** Event-driven audit logging via RabbitMQ/Kafka

```
┌─────────────────┐
│ Services emit   │ ──► RabbitMQ ──► ┌──────────────────┐
│ audit events    │                  │ Audit Service    │
│                 │                  │ (Centralized)    │
│ - Identity      │                  │ - Store events   │
│ - UserGroup     │                  │ - Query API      │
│ - ProjectConfig │                  │ - Alerting       │
│ - Sync          │                  └──────────────────┘
└─────────────────┘                           │
                                               ▼
                                  ┌────────────────────────┐
                                  │ Centralized Audit DB   │
                                  │    (PostgreSQL)        │
                                  └────────────────────────┘
```

**Benefits:**
- **Loose coupling:** Services don't call Audit Service directly (async)
- **Fault tolerance:** Events queued if Audit Service down
- **Single source of truth:** All audit logs in one database
- **Consistent schema:** Standardized event format

---

**Standardized Audit Event Schema:**

```json
{
  // WHO
  "actorId": "uuid",
  "actorType": "USER | SERVICE | SYSTEM",
  "actorEmail": "user@example.com",
  
  // WHAT
  "action": "CREATE_USER",  // Standard action enum
  "outcome": "SUCCESS | FAILURE | DENIED",
  
  // WHERE
  "resourceType": "User",
  "resourceId": "uuid",
  
  // WHEN
  "timestamp": "2026-01-30T10:30:00Z",
  
  // HOW
  "serviceName": "IdentityService",
  "requestId": "trace-uuid",  // Distributed tracing
  "ipAddress": "192.168.1.100",
  "userAgent": "Mozilla/5.0...",
  
  // DETAILS
  "oldValue": { ... },  // State before
  "newValue": { ... },  // State after
  "metadata": { ... }   // Additional context
}
```

---

**Standard Action Enums:**

**Identity Service:**
- `CREATE_USER`, `UPDATE_USER`, `SOFT_DELETE_USER`, `RESTORE_USER`, `HARD_DELETE_USER`
- `LOCK_ACCOUNT`, `UNLOCK_ACCOUNT`
- `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGIN_DENIED`, `LOGOUT`
- `REFRESH_TOKEN_SUCCESS`, `REFRESH_TOKEN_REUSE` (SECURITY EVENT)
- `MAP_EXTERNAL_ACCOUNTS`, `UNMAP_EXTERNAL_ACCOUNTS`

**UserGroup Service:**
- `CREATE_GROUP`, `UPDATE_GROUP_LECTURER`, `SOFT_DELETE_GROUP`, `HARD_DELETE_GROUP`
- `ADD_USER_TO_GROUP`, `REMOVE_USER_FROM_GROUP`
- `ASSIGN_GROUP_ROLE`, `CHANGE_GROUP_LEADER`

**ProjectConfig Service:**
- `CREATE_CONFIG`, `UPDATE_CONFIG`, `SOFT_DELETE_CONFIG`, `HARD_DELETE_CONFIG`, `RESTORE_CONFIG`
- `CONFIG_STATE_CHANGE` (state machine transitions)
- `VERIFY_CONFIG_SUCCESS`, `VERIFY_CONFIG_FAILED`
- `ACCESS_DECRYPTED_TOKENS` (SECURITY EVENT)

**Sync Service:**
- `SYNC_JIRA_START`, `SYNC_JIRA_SUCCESS`, `SYNC_JIRA_FAILED`
- `SYNC_GITHUB_START`, `SYNC_GITHUB_SUCCESS`, `SYNC_GITHUB_FAILED`
- `AUTO_MAP_EXTERNAL_ACCOUNT`, `AUTO_MAP_FAILED`

**System:**
- `SCHEDULED_JOB_START`, `SCHEDULED_JOB_SUCCESS`, `SCHEDULED_JOB_FAILED`

---

**Audit Events to Track:**

**Critical Events (must audit):**
- CREATE, UPDATE, DELETE (soft & hard) for all entities
- STATE_CHANGE (config state machine)
- PERMISSION/ROLE changes (assign group role, update lecturer)
- LOCK/UNLOCK accounts
- ACCESS_DECRYPTED_TOKENS (security sensitive)
- REFRESH_TOKEN_REUSE (security alert)

**Optional Events (can audit):**
- Read operations (GET requests) - optional for performance
- Successful logins - optional, can rely on application logs

---

**Database Schema:**

```sql
CREATE TABLE audit_events (
    id UUID PRIMARY KEY,
    
    -- WHO
    actor_id UUID NULL,
    actor_type VARCHAR(20) NOT NULL,
    actor_email VARCHAR(255) NULL,
    
    -- WHAT
    action VARCHAR(100) NOT NULL,
    outcome VARCHAR(20) NOT NULL,
    
    -- WHERE
    resource_type VARCHAR(50) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    
    -- WHEN
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- HOW
    service_name VARCHAR(50) NOT NULL,
    request_id UUID NULL,
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    
    -- DETAILS
    old_value JSONB NULL,
    new_value JSONB NULL,
    metadata JSONB NULL
);

-- Performance indexes
CREATE INDEX idx_audit_timestamp ON audit_events(timestamp DESC);
CREATE INDEX idx_audit_actor ON audit_events(actor_id);
CREATE INDEX idx_audit_resource ON audit_events(resource_type, resource_id);
CREATE INDEX idx_audit_action ON audit_events(action);
CREATE INDEX idx_audit_service ON audit_events(service_name);

-- Security events index
CREATE INDEX idx_audit_security ON audit_events(action)
WHERE action IN (
    'REFRESH_TOKEN_REUSE',
    'LOGIN_DENIED',
    'ACCESS_DECRYPTED_TOKENS',
    'HARD_DELETE_USER'
);
```

---

**Query API:**

**GET** `/api/audit/events`

Query parameters:
- `actorId`, `action`, `resourceType`, `resourceId`, `serviceName`, `outcome`
- `startDate`, `endDate` (date range)
- `page`, `size` (pagination)

**GET** `/api/audit/events/{id}` - Get detailed event

**GET** `/api/audit/security-events` - Real-time security monitoring

**GET** `/api/audit/resource/{type}/{id}/history` - Complete audit trail for a resource

---

**Real-Time Alerting:**

| Event | Severity | Alert Channel |
|-------|----------|---------------|
| `REFRESH_TOKEN_REUSE` | CRITICAL | Email + Slack |
| `LOGIN_DENIED` (rate >5/min) | HIGH | Slack |
| `HARD_DELETE_USER` | MEDIUM | Email |
| `ACCESS_DECRYPTED_TOKENS` | LOW | Log only |

---

**Retention Policy:**
- **0-90 days:** Hot storage (PostgreSQL, indexed)
- **91-365 days:** Warm storage (PostgreSQL, partitioned)
- **366+ days:** Cold storage (Archive to S3, delete from DB)
- **Audit logs:** NEVER deleted (legal requirement exception)

---

**Implementation:**

**Service Integration (Spring Boot + RabbitMQ):**

```java
@Service
public class AuditEventPublisher {
    @Autowired
    private RabbitTemplate rabbitTemplate;
    
    public void publishAuditEvent(AuditEvent event) {
        try {
            event.setServiceName("IdentityService");
            event.setTimestamp(LocalDateTime.now(ZoneOffset.UTC));
            
            String routingKey = "audit.identity." + event.getAction().toLowerCase();
            rabbitTemplate.convertAndSend("audit.events.exchange", routingKey, event);
        } catch (Exception e) {
            // CRITICAL: Audit failure should NOT block business operation
            log.error("Failed to publish audit event", e);
        }
    }
}
```

**Audit Service (Consumer):**

```java
@RabbitListener(queues = "audit.events.queue")
public void handleAuditEvent(AuditEvent event) {
    auditEventRepository.save(event);
    
    if (isSecurityEvent(event)) {
        securityAlertService.sendAlert(event);
    }
}
```

---

**Documentation Created:**
- ✅ NEW: `docs/Audit_Service/01_DESIGN.md` (complete service design)
- ✅ Standardized event schema
- ✅ Action enums for all services
- ✅ Query API specification
- ✅ Real-time alerting rules
- ✅ Retention policy
- ✅ Service integration examples

**Implementation Status:**
- ⏳ PLANNED for Phase 2 (Q2 2026)
- Requires: RabbitMQ cluster, Audit Service deployment, service integration

**Estimated Timeline:**
- Design & Schema: 2 weeks (✅ Complete)
- RabbitMQ setup: 1 week
- Audit Service implementation: 4 weeks
- Service integration: 3 weeks (parallel)
- Testing & QA: 2 weeks
- **Target Release:** End of Q2 2026

**Trace:**
- Created: `docs/Audit_Service/01_DESIGN.md`

---

## TRACEABILITY MATRIX

| SRS Section | Source Document | Status |
|-------------|-----------------|--------|
| 2.2 User Classes | Identity Service/SRS-Auth.md, UserGroup Service/01_REQUIREMENTS.md | ✅ Updated |
| 3.2.1 Admin UC | Identity Service/SRS-Auth.md, UserGroup Service/01_REQUIREMENTS.md | ✅ Updated |
| 3.2.2 Lecturer UC | UserGroup Service/01_REQUIREMENTS.md | ✅ Updated |
| 3.2.3 Student Leader UC | ProjectConfig Service/01_SRS_PROJECT_CONFIG.md | ✅ Updated |
| 3.2.4 Student Member UC | - | ⏳ Planned |
| 4 Non-Functional (Security) | Identity Service/Authentication-Authorization-Design.md, ProjectConfig Service/09_SECURITY_MODEL.md, ProjectConfig Service/05_BUSINESS_RULES.md | ✅ Updated |
| 6.3.1 Users table | Identity Service/Database-Design.md | ✅ Updated |
| 6.3.1.1 Refresh_Tokens table | Identity Service/Database-Design.md | ✅ Added |
| 6.3.2 Groups table | UserGroup Service/02_DESIGN.md | ✅ Updated |
| 6.3.3 User_Groups table | UserGroup Service/02_DESIGN.md | ✅ Updated |
| 6.3.4 Project_Configs table | ProjectConfig Service/02_DATABASE_DESIGN.md, ProjectConfig Service/10_CONFIG_STATE_MACHINE.md | ✅ Updated |

---

## NEXT STEPS

1. **Review OPEN ISSUES** với stakeholders và product owner
2. **Resolve ambiguities** (OAuth, external account mapping, lecturer assignment, etc.)
3. **Create SRS cho Sync Service, AI Service, Reporting Service** để complete Use Case coverage
4. **Define audit logging standard** across all services
5. **Define soft delete retention policy** và hard delete process (nếu có)
6. **Validate traceability** - ensure mọi implemented feature đều có trong SRS tổng

---

## VERSION CONTROL

| Version | Date | Author | Changes |
|---------|------|--------|---------|
| 1.2 | January 13, 2026 | - | Original SRS |
| 1.3 | January 30, 2026 | Principal Software Architect | Updated based on Identity, User/Group, ProjectConfig Service implementation |

---

**END OF CHANGELOG**
