# API Specification - User & Group Service

## Common Error Response Format

```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

### HTTP Status ↔ Error Code Mapping

| HTTP Status | Error Codes | Scenario |
|-------------|-------------|----------|
| 400 | BAD_REQUEST, INVALID_ROLE | Invalid input, non-STUDENT user added to group |
| 401 | UNAUTHORIZED | Missing/invalid JWT token |
| 403 | FORBIDDEN, LECTURER_CANNOT_VIEW_NON_STUDENT | Insufficient permissions |
| 404 | USER_NOT_FOUND, GROUP_NOT_FOUND, LECTURER_NOT_FOUND, MEMBERSHIP_NOT_FOUND | Resource not found |
| 409 | USER_ALREADY_IN_GROUP, USER_ALREADY_IN_GROUP_SAME_SEMESTER, USER_INACTIVE, LEADER_ALREADY_EXISTS, CANNOT_REMOVE_LEADER, GROUP_NAME_DUPLICATE, LOCK_TIMEOUT | Business rule violation, concurrency conflict |
| 500 | INTERNAL_ERROR | Unexpected server error |
| 503 | SERVICE_UNAVAILABLE | Identity Service unavailable (gRPC UNAVAILABLE) |
| 504 | GATEWAY_TIMEOUT | Identity Service timeout (gRPC DEADLINE_EXCEEDED) |

### gRPC Status → HTTP Status Mapping (Implementation Reference)

**All gRPC calls to Identity Service map errors as follows:**

| gRPC Status Code | HTTP Status | Error Code | Use Case | Implementation |
|------------------|-------------|------------|----------|----------------|
| OK | 200 | - | Success | Return data |
| NOT_FOUND | 404 | USER_NOT_FOUND, LECTURER_NOT_FOUND | UC21, UC23, UC24, UC27 | ResourceNotFoundException |
| PERMISSION_DENIED | 403 | FORBIDDEN | All | ForbiddenException |
| UNAVAILABLE | 503 | SERVICE_UNAVAILABLE | All gRPC calls | ServiceUnavailableException |
| DEADLINE_EXCEEDED | 504 | GATEWAY_TIMEOUT | All gRPC calls (>5s) | GatewayTimeoutException |
| INVALID_ARGUMENT | 400 | BAD_REQUEST | Data validation | BadRequestException |
| FAILED_PRECONDITION | 409 | CONFLICT | Business rule violation | ConflictException |
| INTERNAL | 500 | INTERNAL_ERROR | Unexpected errors | RuntimeException |
| UNAUTHENTICATED | 401 | UNAUTHORIZED | Invalid credentials | UnauthorizedException |

**Implementation Pattern:**

```java
try {
    // gRPC call to Identity Service
    GetUserResponse user = identityServiceClient.getUser(userId);
    return processUser(user);
    
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case UNAVAILABLE:
            throw ServiceUnavailableException.identityServiceUnavailable(); // 503
        case DEADLINE_EXCEEDED:
            throw GatewayTimeoutException.identityServiceTimeout(); // 504
        case NOT_FOUND:
            throw new ResourceNotFoundException("USER_NOT_FOUND", "User not found"); // 404
        case PERMISSION_DENIED:
            throw new ForbiddenException("FORBIDDEN", "Access denied"); // 403
        default:
            throw new RuntimeException("Unexpected gRPC error: " + e.getStatus());
    }
}
```

**Timeout Configuration:**
- Default gRPC deadline: 5 seconds
- Configurable via `grpc.client.identity-service.deadline`

**Retry Strategy:**
- 503 SERVICE_UNAVAILABLE: Client should retry with exponential backoff
- 504 GATEWAY_TIMEOUT: Client should retry (may be transient)
- 4xx errors: Do not retry (client error)
- 500 errors: Do not retry (server error)

---

## User Management APIs

### 1. Get User Profile

**Endpoint:** `GET /api/users/{userId}`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (all users), LECTURER (students only), STUDENT (self only)

> **Authorization Clarification:** LECTURER can only view users with STUDENT role. If target user is not a STUDENT, returns `403 FORBIDDEN`. In cross-service scenarios where target role cannot be verified, the request is **allowed** with a warning log.

#### Response

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

### 2. Update User Profile

**Endpoint:** `PUT /api/users/{userId}`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (any user), STUDENT (self only)

> **Authorization Clarification:** LECTURER role is explicitly **EXCLUDED** from this API. A LECTURER cannot update profiles (including their own) via this endpoint — returns `403 FORBIDDEN`.

> **IMPLEMENTATION NOTE:** User/Group Service acts as a **proxy** to Identity Service for this API. Authorization is validated in User/Group Service, then request is forwarded via gRPC `UpdateUser()` RPC to Identity Service.

**Flow:**
1. Client → User/Group Service: `PUT /api/users/{userId}`
2. User/Group Service validates authorization (ADMIN/STUDENT only)
3. User/Group Service → Identity Service: gRPC `UpdateUser(userId, fullName)`
4. Identity Service updates database
5. Identity Service → User/Group Service: Updated user info
6. User/Group Service → Client: Response

**Why Proxy Pattern:**
- Client convenience (single API gateway)
- Consistent authorization across services
- No duplication of user data

#### Request

```json
{
  "fullName": "string"
}
```

#### Response

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

- `403 FORBIDDEN`
- `404 USER_NOT_FOUND`
- `409 USER_INACTIVE`

---

### 3. List Users

**Endpoint:** `GET /api/users`  
**Security:** JWT (ADMIN)

#### Query Parameters

- `page` (default: 0)
- `size` (default: 20)
- `status` (optional: ACTIVE, INACTIVE)
- `role` (optional: ADMIN, LECTURER, STUDENT) — **Deferred:** Filter by role NOT implemented (roles stored in identity-service)

> **Implementation Note:** The `role` parameter is accepted for backward compatibility but **ignored** in the current implementation. Filter by status works correctly.

#### Response

```json
{
  "content": [
    {
      "id": "uuid",
      "email": "string",
      "fullName": "string",
      "status": "ACTIVE",
      "roles": ["STUDENT"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

## Group Management APIs

### 4. Create Group

**Endpoint:** `POST /api/groups`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "uuid"
}
```

#### Response

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "uuid",
  "lecturerName": "Dr. Nguyen Van A"
}
```

#### Error Codes

- `409 GROUP_NAME_DUPLICATE`
- `404 LECTURER_NOT_FOUND`

---

### 5. Get Group Details

**Endpoint:** `GET /api/groups/{groupId}`  
**Security:** JWT (AUTHENTICATED)

#### Response

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturer": {
    "id": "uuid",
    "fullName": "Dr. Nguyen Van A",
    "email": "lecturer@example.com"
  },
  "members": [
    {
      "userId": "uuid",
      "fullName": "Student Name",
      "email": "student@example.com",
      "role": "LEADER"
    }
  ],
  "memberCount": 5
}
```

#### Error Codes

- `404 GROUP_NOT_FOUND`

---

### 6. List Groups

**Endpoint:** `GET /api/groups`  
**Security:** JWT (AUTHENTICATED)

#### Query Parameters

- `page` (default: 0)
- `size` (default: 20)
- `semester` (optional)
- `lecturerId` (optional)

#### Response

```json
{
  "content": [
    {
      "id": "uuid",
      "groupName": "SE1705-G1",
      "semester": "Spring2026",
      "lecturerName": "Dr. Nguyen Van A",
      "memberCount": 5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

### 7. Update Group

**Endpoint:** `PUT /api/groups/{groupId}`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "groupName": "SE1705-G1-Updated",
  "lecturerId": "uuid"
}
```

**Note:** Semester is immutable

#### Response

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1-Updated",
  "semester": "Spring2026",
  "lecturerId": "uuid",
  "lecturerName": "Dr. Nguyen Van B"
}
```

---

### 8. Delete Group

**Endpoint:** `DELETE /api/groups/{groupId}`  
**Security:** JWT (ADMIN)

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `404 GROUP_NOT_FOUND`

---

### 9. Update Group Lecturer

**Endpoint:** `PATCH /api/groups/{groupId}/lecturer`  
**Security:** JWT (ADMIN only)  
**Description:** Change the lecturer assigned to a group (UC27)

#### Request

```json
{
  "lecturerId": "uuid"
}
```

#### Response

**Status:** `200 OK`

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "new-lecturer-uuid",
  "lecturerName": "Dr. New Lecturer",
  "lecturerEmail": "new-lecturer@university.edu"
}
```

#### Validation Rules

| Rule | Condition | Error Code | HTTP Status |
|------|-----------|------------|-------------|
| Authorization | Actor must be ADMIN | FORBIDDEN | 403 |
| Group exists | Group must exist and not be soft-deleted | GROUP_NOT_FOUND | 404 |
| Lecturer exists | New lecturer must exist in Identity Service | LECTURER_NOT_FOUND | 404 |
| Lecturer active | New lecturer status = ACTIVE | USER_INACTIVE | 409 |
| Lecturer role | New lecturer role = LECTURER | INVALID_ROLE | 400 |
| gRPC available | Identity Service reachable | SERVICE_UNAVAILABLE | 503 |
| gRPC timeout | gRPC call completes in <5s | GATEWAY_TIMEOUT | 504 |

#### Error Codes

- `403 FORBIDDEN` - Non-admin actor
- `404 GROUP_NOT_FOUND` - Group doesn't exist
- `404 LECTURER_NOT_FOUND` - Lecturer doesn't exist in Identity Service
- `400 INVALID_ROLE` - User is not a lecturer (e.g., STUDENT, ADMIN)
- `409 USER_INACTIVE` - Lecturer account is inactive
- `503 SERVICE_UNAVAILABLE` - Identity Service unavailable
- `504 GATEWAY_TIMEOUT` - Identity Service timeout

#### Idempotency

- Multiple requests with same `lecturerId` → `200 OK` (no-op if already assigned)
- Audit log records each request regardless of change

#### Audit Logging

System logs UC27 events with structured format:

```json
{
  "action": "UPDATE_GROUP_LECTURER",
  "groupId": "uuid",
  "oldLecturerId": "uuid",
  "newLecturerId": "uuid",
  "actorId": "admin-uuid",
  "timestamp": "2026-01-31T10:30:00Z"
}
```

#### Implementation Notes

- Uses gRPC `VerifyUserExists()` and `GetUserRole()` for validation
- Transaction-safe (rollback on gRPC failure)
- Old lecturer can still view group until audit period expires

---

## Group Member Management APIs

### 10. Add Member to Group

**Endpoint:** `POST /api/groups/{groupId}/members`  
**Security:** JWT (ADMIN)

> **Business Rule:** Only users with role `STUDENT` can be added to groups. ADMIN/LECTURER users will be rejected with `400 INVALID_ROLE`.

#### Request

```json
{
  "userId": "uuid",
  "isLeader": false
}
```

#### Response

**Status:** `201 CREATED`

```json
{
  "userId": "uuid",
  "groupId": "uuid",
  "fullName": "Student Name",
  "email": "student@example.com",
  "role": "MEMBER"
}
```

#### Validation Steps

1. Verify user exists (gRPC `VerifyUserExists()`)
2. Verify user is active
3. **Verify user has STUDENT role** (gRPC `GetUserRole()`)
4. Check user not already in group
5. Check user not in another group same semester
6. If `isLeader=true`, check no existing leader

#### Error Codes

- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`
- `400 INVALID_ROLE` - User is not STUDENT (ADMIN/LECTURER rejected)
- `409 USER_ALREADY_IN_GROUP`
- `409 USER_ALREADY_IN_GROUP_SAME_SEMESTER`
- `409 USER_INACTIVE`
- `409 LEADER_ALREADY_EXISTS`
- `503 SERVICE_UNAVAILABLE` - Identity Service unavailable
- `504 GATEWAY_TIMEOUT` - Identity Service timeout

---

### 10. Assign Group Role

**Endpoint:** `PUT /api/groups/{groupId}/members/{userId}/role`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "role": "LEADER"
}
```

#### Response

**Status:** `200 OK`

```json
{
  "userId": "uuid",
  "groupId": "uuid",
  "fullName": "Student Name",
  "email": "student@example.com",
  "role": "LEADER"
}
```

#### Error Codes

- `404 USER_NOT_FOUND` (membership not found)
- `404 GROUP_NOT_FOUND`

**Notes:**
- When assigning new LEADER, old LEADER auto-demotes to MEMBER
- Uses **pessimistic locking** on leader query to prevent race conditions
- `409 LEADER_ALREADY_EXISTS` error does NOT apply here (only in addMember with isLeader=true)

---

### 11. Remove Member from Group

**Endpoint:** `DELETE /api/groups/{groupId}/members/{userId}`  
**Security:** JWT (ADMIN)

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `404 GROUP_NOT_FOUND` - Group không tồn tại
- `404 MEMBERSHIP_NOT_FOUND` - User không phải member của group
- `409 CANNOT_REMOVE_LEADER` - Không thể remove LEADER khi group còn active members (role=MEMBER)
- `401 UNAUTHORIZED` - Not authenticated
- `403 FORBIDDEN` - Not ADMIN role

**Note:** Validation checks if LEADER is being removed:
- If group has 1+ users with role=MEMBER → 409 CANNOT_REMOVE_LEADER
- If group has 0 users with role=MEMBER (only LEADER exists) → 204 SUCCESS

---

### 12. Get Group Members

**Endpoint:** `GET /api/groups/{groupId}/members`  
**Security:** JWT (AUTHENTICATED)

#### Query Parameters

- `role` (optional: LEADER, MEMBER)

#### Response

```json
{
  "groupId": "uuid",
  "groupName": "SE1705-G1",
  "members": [
    {
      "userId": "uuid",
      "fullName": "Student Name",
      "email": "student@example.com",
      "role": "LEADER"
    }
  ],
  "totalMembers": 5
}
```

---

### 13. Get User's Groups

**Endpoint:** `GET /api/users/{userId}/groups`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (any user), LECTURER (students), STUDENT (self only)

#### Query Parameters

- `semester` (optional)

#### Response

```json
{
  "userId": "uuid",
  "groups": [
    {
      "groupId": "uuid",
      "groupName": "SE1705-G1",
      "semester": "Spring2026",
      "role": "MEMBER",
      "lecturerName": "Dr. Nguyen Van A"
    }
  ]
}
```

---

## Health Check

### 14. Health Check

**Endpoint:** `GET /actuator/health`  
**Security:** Public

#### Response

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

---

## API Summary Table

| Method | Endpoint | Role | Description | Notes |
|--------|----------|------|-------------|-------|
| GET | /api/users/{userId} | AUTHENTICATED | Get user profile | LECTURER: STUDENT targets only |
| PUT | /api/users/{userId} | ADMIN, SELF | Update user profile (proxy) | LECTURER excluded, proxies to Identity Service |
| GET | /api/users | ADMIN | List all users | role filter deferred |
| POST | /api/groups | ADMIN | Create group | |
| GET | /api/groups/{groupId} | AUTHENTICATED | Get group details | |
| GET | /api/groups | AUTHENTICATED | List groups | |
| PUT | /api/groups/{groupId} | ADMIN | Update group | |
| DELETE | /api/groups/{groupId} | ADMIN | Delete group (soft) | |
| PATCH | /api/groups/{groupId}/lecturer | ADMIN | Update group lecturer (UC27) | gRPC validation + audit log |
| POST | /api/groups/{groupId}/members | ADMIN | Add member | STUDENT role only |
| PUT | /api/groups/{groupId}/members/{userId}/role | ADMIN | Assign role | pessimistic lock |
| DELETE | /api/groups/{groupId}/members/{userId} | ADMIN | Remove member | |
| GET | /api/groups/{groupId}/members | AUTHENTICATED | Get members | |
| GET | /api/users/{userId}/groups | AUTHENTICATED | Get user's groups | |
