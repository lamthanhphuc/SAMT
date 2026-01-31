# Requirements - User & Group Service

## 1. Actors

| Actor | Mô tả |
|-------|-------|
| ADMIN | Quản trị hệ thống |
| LECTURER | Giảng viên |
| STUDENT | Sinh viên |

---

## 2. Use Case List

> **Note:** CHỈ các UC được liệt kê dưới đây được phép implement

---

### UC21 – Get User Profile

**Actor:** ADMIN, LECTURER, STUDENT  
**Description:** Lấy thông tin user

#### Preconditions

- User đã đăng nhập
- Access Token hợp lệ

#### Main Flow

1. Actor gọi API lấy user
2. System kiểm tra quyền
3. Trả về thông tin user

#### Authorization Rules

- **ADMIN:** xem mọi user
- **LECTURER:** xem student (target user phải có role STUDENT, nếu không → 403)
- **STUDENT:** chỉ xem chính mình

> **Clarification (UC21-AUTH):** LECTURER chỉ được xem user có role STUDENT. Nếu target user không phải STUDENT, system trả về `403 FORBIDDEN`. Trong trường hợp không thể verify role của target user (cross-service), mặc định ALLOW và log warning.

#### API

**Endpoint:** `GET /api/users/{userId}`

#### Response DTO

```json
{
  "id": "uuid",
  "email": "string",
  "fullName": "string",
  "status": "ACTIVE",
  "roles": ["STUDENT"]
}
```

#### Error Codes

- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 USER_NOT_FOUND`

---

### UC22 – Update User Profile

**Actor:** ADMIN, STUDENT

#### Preconditions

- User tồn tại
- status = ACTIVE

#### Main Flow

1. Actor gửi request update
2. System kiểm tra quyền
3. Update user

#### Rules

- **STUDENT:** chỉ update chính mình
- **ADMIN:** update mọi user
- **LECTURER:** KHÔNG được update profile (kể cả chính mình qua API này)
- Không update role tại UC này

> **Clarification (UC22-AUTH):** LECTURER role bị loại trừ hoàn toàn khỏi UC này. LECTURER muốn update profile phải thông qua kênh khác hoặc liên hệ ADMIN.

#### Implementation Details

> **IMPORTANT:** User/Group Service DOES NOT manage user data locally. This API acts as a **proxy/gateway** to Identity Service.

**Implementation Pattern:**

1. User/Group Service receives `PUT /api/users/{userId}` request
2. Validate authorization (ADMIN/STUDENT only, LECTURER excluded)
3. Forward request to Identity Service via gRPC `UpdateUser()` RPC
4. Identity Service performs update
5. Return updated user info to client

**gRPC Integration:**

```protobuf
// user_service.proto
rpc UpdateUser(UpdateUserRequest) returns (UpdateUserResponse);

message UpdateUserRequest {
  string user_id = 1;
  string full_name = 2;
}

message UpdateUserResponse {
  GetUserResponse user = 1;
}
```

**Rationale:**
- Maintains separation of concerns
- User/Group Service acts as API gateway for client convenience
- Authorization enforced at both service layers
- UC22 requirement satisfied without duplicating user data

**Error Handling:**
- Identity Service unavailable → `503 SERVICE_UNAVAILABLE`
- Identity Service timeout → `504 GATEWAY_TIMEOUT`
- User not found in Identity Service → `404 USER_NOT_FOUND`

#### API

**Endpoint:** `PUT /api/users/{userId}`

#### Request DTO

```json
{
  "fullName": "string"
}
```

#### Error Codes

- `403 FORBIDDEN`
- `404 USER_NOT_FOUND`
- `409 USER_INACTIVE`

---

### UC23 – Create Group

**Actor:** ADMIN

#### Preconditions

- Admin đăng nhập

#### Main Flow

1. Admin gửi thông tin group
2. System tạo group
3. Trả groupId

#### API

**Endpoint:** `POST /api/groups`

#### Request DTO

```json
{
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "uuid"
}
```

#### Error Codes

- `409 GROUP_NAME_DUPLICATE`
- `404 LECTURER_NOT_FOUND`

---

### UC24 – Add User to Group

**Actor:** ADMIN

#### Main Flow

1. Admin thêm user vào group
2. System validates user có role STUDENT (via gRPC)
3. System tạo user_groups record

#### Rules

- Mỗi user chỉ thuộc **1 group / semester**
- Không thêm user INACTIVE
- **CHỈ user có role STUDENT được thêm vào group** (ADMIN/LECTURER không được phép)

> **Business Rule BR-UG-009:** Only users with role `STUDENT` can be added to groups. System MUST validate user role via Identity Service gRPC call before adding to group.

#### API

**Endpoint:** `POST /api/groups/{groupId}/members`

#### Request DTO

```json
{
  "userId": "uuid",
  "isLeader": false
}
```

#### Error Codes

- `409 USER_ALREADY_IN_GROUP`
- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`

---

### UC25 – Assign Group Role

**Actor:** ADMIN  
**Description:** Gán role trong group (LEADER / MEMBER)

#### API

**Endpoint:** `PUT /api/groups/{groupId}/members/{userId}/role`

#### Request DTO

```json
{
  "role": "LEADER"
}
```

#### Rules

- 1 group chỉ có 1 LEADER
- Khi assign LEADER mới, LEADER cũ tự động demote xuống MEMBER
- **Transaction:** Phải dùng pessimistic lock khi demote old leader để tránh race condition

> **Clarification (UC25-LOCK):** Khi có nhiều request đồng thời assign LEADER, system phải sử dụng `@Lock(PESSIMISTIC_WRITE)` trên query `findLeaderByGroupId()` để đảm bảo chỉ 1 request thành công. Request còn lại sẽ thấy user đã là LEADER và không thực hiện demote.

#### Error Codes

- `409 LEADER_ALREADY_EXISTS` (chỉ khi isLeader=true trong addMember, không áp dụng khi assignRole)

---

### UC26 – Remove User from Group

**Actor:** ADMIN

#### API

**Endpoint:** `DELETE /api/groups/{groupId}/members/{userId}`

#### Rules

- Không remove LEADER nếu group còn active members (role=MEMBER)

#### Validation Detail

**Remove LEADER validation:**

System counts users with role=MEMBER (excludes LEADER being removed):

```java
long memberCount = countMembersByGroupId(groupId); // Counts MEMBER role only
if (memberCount > 0) {
    throw CANNOT_REMOVE_LEADER;
}
```

**Examples:**

| Group State | Remove Operation | Result |
|-------------|------------------|--------|
| 1 LEADER + 0 MEMBER | Remove LEADER | 204 SUCCESS |
| 1 LEADER + 1 MEMBER | Remove LEADER | 409 CANNOT_REMOVE_LEADER |
| 1 LEADER + 2 MEMBER | Remove LEADER | 409 CANNOT_REMOVE_LEADER |
| 1 LEADER + 1 MEMBER | Remove MEMBER | 204 SUCCESS |

**Business Logic:** LEADER can only be removed from empty group (no MEMBER role users).

---

### UC27 – Update Group Lecturer

**Actor:** ADMIN

**Description:** Thay đổi lecturer phụ trách nhóm

#### Preconditions

- Admin đăng nhập
- Group tồn tại và không bị soft delete
- Lecturer mới tồn tại và có role LECTURER

#### Main Flow

1. Admin gửi request với lecturerId mới
2. System xác minh ROLE_ADMIN
3. System verify lecturer mới tồn tại và có role LECTURER (via Identity Service gRPC)
4. System update groups.lecturer_id
5. System ghi audit log (old_value, new_value)
6. System trả về group info cập nhật

#### Validation Flow (Detailed)

**Step 1: Authorization Check**
- Extract JWT → Verify `hasRole('ADMIN')`
- Non-admin → `403 FORBIDDEN`

**Step 2: Group Existence Check**
- Query: `SELECT * FROM groups WHERE id = :groupId AND deleted_at IS NULL`
- Not found → `404 GROUP_NOT_FOUND`

**Step 3: New Lecturer Verification (gRPC)**

```java
// Call Identity Service
VerifyUserResponse verification = identityClient.verifyUserExists(newLecturerId);

// Check 1: User exists
if (!verification.getExists()) {
    throw NotFoundException("LECTURER_NOT_FOUND", "Lecturer not found");
}

// Check 2: User is active
if (!verification.getActive()) {
    throw ConflictException("USER_INACTIVE", "Lecturer account is inactive");
}

// Check 3: User has LECTURER role
GetUserRoleResponse roleResponse = identityClient.getUserRole(newLecturerId);
if (roleResponse.getRole() != UserRole.LECTURER) {
    throw ConflictException("INVALID_ROLE", 
        "User is not a lecturer (role: " + roleResponse.getRole() + ")");
}
```

**Step 4: Update & Audit**

```java
UUID oldLecturerId = group.getLecturerId();
group.setLecturerId(newLecturerId);
groupRepository.save(group);

// Audit log
log.info("UC27 - LECTURER_UPDATED: groupId={}, oldLecturer={}, newLecturer={}, actor={}", 
    groupId, oldLecturerId, newLecturerId, actorId);
```

#### gRPC Error Handling

| gRPC Status | HTTP Status | Error Code | Action |
|-------------|-------------|------------|--------|
| UNAVAILABLE | 503 | SERVICE_UNAVAILABLE | Log + retry later |
| DEADLINE_EXCEEDED | 504 | GATEWAY_TIMEOUT | Log + retry later |
| NOT_FOUND | 404 | LECTURER_NOT_FOUND | Return error to client |
| PERMISSION_DENIED | 403 | FORBIDDEN | Return error to client |
| INTERNAL | 500 | INTERNAL_ERROR | Log + return generic error |

#### Idempotency

- Request với `lecturerId` giống current lecturer → `200 OK` (no-op)
- Multiple requests với cùng `lecturerId` mới → `200 OK` (idempotent)

#### API

**Endpoint:** `PATCH /api/groups/{groupId}/lecturer`

#### Request DTO

```json
{
  "lecturerId": "uuid"
}
```

#### Response

```json
{
  "groupId": "uuid",
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "new-lecturer-uuid",
  "updatedAt": "2026-01-30T10:30:00Z"
}
```

#### Business Rules

| Rule ID | Rule | Validation |
|---------|------|------------|
| BR-UG-27-01 | Lecturer mới phải tồn tại | Call Identity Service gRPC: `VerifyUserExists(lecturerId)` |
| BR-UG-27-02 | Lecturer mới phải có role LECTURER | Call Identity Service gRPC: `GetUserRole(lecturerId)` → must return LECTURER |
| BR-UG-27-03 | Group không bị soft delete | Check `deleted_at IS NULL` |
| BR-UG-27-04 | Phải audit log thay đổi | Log old_value (old lecturerId), new_value (new lecturerId) |

#### Error Codes

- `401 UNAUTHORIZED` - Not authenticated
- `403 FORBIDDEN` - Not ADMIN role
- `404 GROUP_NOT_FOUND` - Group không tồn tại
- `404 LECTURER_NOT_FOUND` - Lecturer mới không tồn tại
- `400 INVALID_ROLE` - User không phải LECTURER
- `400 GROUP_DELETED` - Group đã bị soft delete

#### Audit Log

**Action:** `UPDATE_GROUP_LECTURER`

```json
{
  "entityType": "Group",
  "entityId": "group-uuid",
  "action": "UPDATE_GROUP_LECTURER",
  "outcome": "SUCCESS",
  "actorId": "admin-uuid",
  "actorEmail": "admin@university.edu",
  "oldValue": {
    "lecturerId": "old-lecturer-uuid"
  },
  "newValue": {
    "lecturerId": "new-lecturer-uuid"
  },
  "timestamp": "2026-01-30T10:30:00Z",
  "serviceName": "UserGroupService"
}
```

---

### Delete Group (ADMIN)

**Endpoint:** `DELETE /api/groups/{groupId}`

**Actor:** ADMIN

**Behavior:**

System performs cascade soft delete:
1. Soft delete group (set deleted_at)
2. Soft delete all memberships in group (cascade)

**Business Rule:**
- Allowed regardless of member count
- All members automatically soft deleted
- Historical data preserved (soft delete)

**Examples:**

| Group State | Delete Operation | Result |
|-------------|------------------|--------|
| Empty group | Delete | Group + 0 memberships soft deleted |
| Group with 5 members | Delete | Group + 5 memberships soft deleted |

**Note:** Students will see group as deleted in their group list. No orphaned memberships remain.

**Alternative Approach (Not Implemented):**

Some systems prevent delete if group has members:
```java
if (memberCount > 0) {
    throw CANNOT_DELETE_GROUP_WITH_MEMBERS;
}
```

**Current Decision:** Allow cascade delete for operational flexibility.

**Trade-offs:**
- ✅ Easier for admins (one operation vs. remove all members first)
- ⚠️ Risk of accidental deletion (mitigated by soft delete)

**Error Codes:**
- `404 GROUP_NOT_FOUND`
- `401 UNAUTHORIZED`
- `403 FORBIDDEN` (not ADMIN)

---

## 3. Security Architecture

### 3.1 JWT Claims

```json
{
  "userId": "uuid",
  "roles": ["ADMIN", "LECTURER", "STUDENT"]
}
```

### 3.2 Authentication Flow

```
JwtFilter → Extract Token → Validate → Set SecurityContext
```

### 3.3 Role Mapping

**Important:** System Role ≠ Group Role

- **System Role:** ADMIN, LECTURER, STUDENT (stored in JWT)
- **Group Role:** LEADER, MEMBER (stored in user_groups table)

---

## 4. Open Questions & Answers

### Q1: Lecturer có thể đổi leader không?

**Answer:** NO (ADMIN only)

### Q2: Student có thể tự rời group không?

**Answer:** NO

### Q3: User có thể tham gia nhiều group ở các semester khác nhau không?

**Answer:** YES (1 group / semester)
