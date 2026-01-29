# AUTHORIZATION – PROJECT CONFIG SERVICE

## 1. Authorization Model

### 1.1 System Roles

| Role | Source | Description |
|------|--------|-------------|
| ADMIN | Identity Service | Quản trị viên hệ thống |
| LECTURER | Identity Service | Giảng viên hướng dẫn |
| STUDENT | Identity Service | Sinh viên (có thể là Leader hoặc Member) |

**Note:** Role được lấy từ JWT token do Identity Service cấp.

### 1.2 Group Roles

| Role | Source | Description |
|------|--------|-------------|
| LEADER | User/Group Service | Trưởng nhóm - có quyền quản lý config |
| MEMBER | User/Group Service | Thành viên - chỉ xem |

---

## 2. Endpoint Authorization Matrix

### 2.1 Public Endpoints

| Endpoint | Method | Role Required | Additional Rules |
|----------|--------|---------------|------------------|
| /actuator/health | GET | PUBLIC | N/A |

### 2.2 Project Config Management

| Endpoint | Method | Role Required | Additional Rules |
|----------|--------|---------------|------------------|
| POST /api/project-configs | POST | STUDENT (LEADER), ADMIN | Must be leader of the group |
| GET /api/project-configs/{configId} | GET | AUTHENTICATED | Must be leader/lecturer/admin |
| GET /api/project-configs/group/{groupId} | GET | AUTHENTICATED | Must be leader/lecturer/admin |
| PUT /api/project-configs/{configId} | PUT | STUDENT (LEADER), ADMIN | Must be leader of the group |
| DELETE /api/project-configs/{configId} | DELETE | STUDENT (LEADER), ADMIN | Must be leader of the group |
| POST /api/project-configs/{configId}/verify | POST | STUDENT (LEADER), ADMIN | Must be leader of the group |
| POST /api/project-configs/verify | POST | STUDENT (LEADER), ADMIN | Public verification (no config yet) |

### 2.3 Internal Endpoints

| Endpoint | Method | Role Required | Additional Rules |
|----------|--------|---------------|------------------|
| GET /internal/project-configs/{configId}/tokens | GET | SERVICE | Internal service authentication only |

---

## 3. Authorization Rules Detail

### 3.1 Create Config (POST /api/project-configs)

**Who Can:**
- ADMIN: Tạo config cho bất kỳ group nào
- STUDENT (LEADER): Tạo config cho nhóm của mình

**Validation Steps:**
1. Extract userId from JWT
2. Extract groupId from request body
3. Call User/Group Service: `GET /api/groups/{groupId}/members`
4. Verify user is LEADER of the group
5. Check group chưa có config (UNIQUE constraint)

**Pseudo Code:**
```java
if (user.role == ADMIN) {
    allow();
} else if (user.role == STUDENT) {
    GroupMembership membership = groupService.getMembership(groupId, userId);
    if (membership != null && membership.role == LEADER) {
        allow();
    } else {
        throw new ForbiddenException("Only group leader can create config");
    }
}
```

---

### 3.2 Get Config (GET)

**Who Can:**
- ADMIN: Xem mọi config
- LECTURER: Xem config của nhóm phụ trách
- STUDENT (LEADER): Xem config của nhóm mình
- STUDENT (MEMBER): Không được xem (chứa thông tin nhạy cảm)

**Validation Steps:**
1. Extract userId from JWT
2. Get config by configId/groupId
3. Get group info from User/Group Service
4. Check authorization:
   - If ADMIN → allow
   - If LECTURER → check if user is lecturer of the group
   - If STUDENT (LEADER) → check if user is leader of the group
   - If STUDENT (MEMBER) → deny

**Pseudo Code:**
```java
Config config = getConfig(configId);
Group group = groupService.getGroup(config.groupId);

if (user.role == ADMIN) {
    return maskTokens(config);
} else if (user.role == LECTURER) {
    if (group.lecturerId == user.id) {
        return maskTokens(config);
    }
} else if (user.role == STUDENT) {
    GroupMembership membership = groupService.getMembership(group.id, user.id);
    if (membership != null && membership.role == LEADER) {
        return maskTokens(config);
    }
}
throw new ForbiddenException("Not authorized to view this config");
```

---

### 3.3 Update Config (PUT)

**Who Can:**
- ADMIN: Update mọi config
- STUDENT (LEADER): Update config của nhóm mình

**Validation Steps:**
Same as Create Config

**Rules:**
- groupId is immutable (cannot be changed)
- Tokens are re-encrypted if provided

---

### 3.4 Delete Config (DELETE)

**Who Can:**
- ADMIN: Xóa mọi config
- STUDENT (LEADER): Xóa config của nhóm mình

**Validation Steps:**
Same as Create Config

**Rules:**
- Soft delete only
- Should check if Sync Service is currently using this config

---

### 3.5 Verify Connection (POST /verify)

**Who Can:**
- ADMIN: Verify bất kỳ config nào
- STUDENT (LEADER): Verify config của nhóm mình hoặc credentials trước khi tạo

**Validation Steps:**
- If configId provided: Same as Get Config
- If no configId (pre-create verification): Any authenticated user can verify credentials

---

### 3.6 Get Decrypted Tokens (Internal)

**Who Can:**
- Internal services only (Sync Service)

**Authentication:**
- Service-to-service authentication
- Mutual TLS or API Key
- Not accessible from public API Gateway

**Validation:**
```java
if (!isInternalServiceRequest(request)) {
    throw new UnauthorizedException("Internal endpoint only");
}

String serviceApiKey = request.getHeader("X-Internal-Service-Key");
if (!isValidServiceKey(serviceApiKey)) {
    throw new UnauthorizedException("Invalid service credentials");
}
```

---

## 4. Special Authorization Rules

### 4.1 Group Ownership Verification

**Rule:** Chỉ LEADER hiện tại của group mới có quyền thao tác config.

**Implementation:**
- Always verify group membership at request time
- Don't cache authorization decisions (role có thể thay đổi)
- Call User/Group Service for latest membership status

### 4.2 Lecturer Access

**Rule:** Lecturer chỉ xem được config, không được modify.

**Rationale:** 
- Lecturer cần xem để hướng dẫn, debug
- Không nên cho quyền sửa để tránh vô tình làm hỏng integration

### 4.3 Member Access

**Rule:** Member KHÔNG được xem config.

**Rationale:**
- Config chứa sensitive credentials (API tokens)
- Chỉ Leader cần biết để cấu hình
- Member chỉ cần biết kết quả đồng bộ (từ Sync Service)

### 4.4 Admin Override

**Rule:** ADMIN có full access cho mục đích support/troubleshooting.

**Audit:**
- Log mọi thao tác của ADMIN
- Bao gồm: timestamp, admin_id, action, config_id

---

## 5. Token Masking Strategy

### 5.1 Masking Rules

Khi trả về config cho client (GET endpoints):

**Jira API Token:**
- Original: `ATATT3xFfGF0T1234567890abcdef`
- Masked: `ATATT3***...` (hiển thị 7 ký tự đầu)

**GitHub Token:**
- Original: `ghp_abc123xyz456def789ghi012`
- Masked: `ghp_***...` (hiển thị 4 ký tự đầu)

### 5.2 Full Token Access

**Only for:**
- Internal service endpoint (GET /internal/.../tokens)
- Returns fully decrypted tokens
- Used by Sync Service to connect to Jira/GitHub

### 5.3 Implementation

```java
public ConfigResponse maskTokens(Config config) {
    return ConfigResponse.builder()
        .configId(config.getConfigId())
        .groupId(config.getGroupId())
        .jiraHostUrl(config.getJiraHostUrl())
        .jiraApiToken(maskToken(config.getJiraApiToken(), 7))
        .githubRepoUrl(config.getGithubRepoUrl())
        .githubToken(maskToken(config.getGithubToken(), 4))
        .build();
}

private String maskToken(String token, int visibleChars) {
    if (token.length() <= visibleChars) {
        return "***";
    }
    return token.substring(0, visibleChars) + "***...";
}
```

---

## 6. Security Best Practices

### 6.1 Defense in Depth

1. **JWT Validation:** Always validate JWT signature
2. **Role Check:** Verify role from JWT claims
3. **Ownership Check:** Verify user is leader/lecturer of the group
4. **Resource Check:** Verify resource exists and not deleted
5. **Rate Limiting:** Prevent brute force attacks

### 6.2 Audit Logging

Log following events:
- Config creation (who, when, which group)
- Config access (who viewed, masked or full tokens)
- Config modification (who, what changed)
- Config deletion (who, when)
- Failed authorization attempts

**Log Format:**
```json
{
  "timestamp": "2026-01-29T10:00:00Z",
  "action": "VIEW_CONFIG",
  "userId": "uuid",
  "userRole": "LECTURER",
  "configId": "uuid",
  "groupId": "uuid",
  "result": "SUCCESS",
  "ipAddress": "192.168.1.100"
}
```

### 6.3 Error Messages

**Don't leak information in error messages:**

❌ BAD:
```json
{
  "error": "You are not the leader of group SE1705-G1"
}
```

✅ GOOD:
```json
{
  "code": "FORBIDDEN",
  "message": "You do not have permission to perform this action"
}
```

---

## 7. Integration with Other Services

### 7.1 Identity Service

**Purpose:** JWT validation and user authentication

**Flow:**
```
Request → Config Service → Validate JWT (Identity Service)
       → Extract userId & roles
       → Continue authorization check
```

### 7.2 User/Group Service

**Purpose:** Verify group membership and leadership

**APIs Used:**
- `GET /api/groups/{groupId}` - Get group info
- `GET /api/groups/{groupId}/members` - Get all members
- `GET /api/groups/{groupId}/members/{userId}` - Check specific membership

**Caching Strategy:**
- Cache group membership for 5 minutes
- Invalidate cache on authorization failure
- Don't cache for sensitive operations (create/update/delete)

---

## 8. Authorization Flow Diagram

```
User Request
     ↓
JWT Filter → Extract userId & roles
     ↓
Authorization Service
     ↓
Check Role (ADMIN/LECTURER/STUDENT)
     ↓
If not ADMIN:
     ↓
Call User/Group Service
     ↓
Verify Membership & Leadership
     ↓
Allow / Deny
     ↓
If Allow: Execute Business Logic
     ↓
Mask Tokens (for external response)
     ↓
Return Response
```
