# API Specification - User & Group Service

## ⚠️ Important Notes

### Note on Semester Data

`semesterCode` field in all API responses is resolved **internally** by User-Group Service from its local `semesters` table.

- ✅ **Source:** Local database (`semesters.semester_code`)
- ✅ **Resolution:** `SemesterRepository.findById(semesterId)`
- ❌ **NO external calls** to Project Config Service required
- ❌ API Gateway and clients MUST NOT call Project Config Service for semester information

**Example:**
```json
{
  "id": 1,
  "groupName": "Group Alpha",
  "semesterId": 1,
  "semesterCode": "SPRING2025",  // ✅ Resolved from local DB
  "lecturerId": 101,
  "lecturerName": "Dr. John Doe"
}
```

---

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
  "id": 456,
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
  "id": 456,
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
      "id": 456,
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

## Semester Management APIs

### 3. Create Semester

**Endpoint:** `POST /api/semesters`  
**Security:** JWT (ADMIN only)  
**Controller:** SemesterController

#### Request

```json
{
  "semesterCode": "SPRING2026",
  "semesterName": "Spring Semester 2026",
  "startDate": "2026-01-15",
  "endDate": "2026-05-30"
}
```

**Validation:**
- `semesterCode`: Required, not blank
- `semesterName`: Required, not blank
- `startDate`: Required, ISO date format (yyyy-MM-dd)
- `endDate`: Required, ISO date format (yyyy-MM-dd)

#### Response 201 Created

```json
{
  "id": 10,
  "semesterCode": "SPRING2026",
  "semesterName": "Spring Semester 2026",
  "startDate": "2026-01-15",
  "endDate": "2026-05-30",
  "isActive": false,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

#### Error Codes

- `401 UNAUTHORIZED` - Missing/invalid JWT
- `403 FORBIDDEN` - Not ADMIN role
- `400 BAD_REQUEST` - Validation failed
- `409 CONFLICT` - Semester code already exists

---

### 3a. Get Semester by ID

**Endpoint:** `GET /api/semesters/{id}`  
**Security:** JWT (AUTHENTICATED)  
**Controller:** SemesterController

#### Response 200 OK

```json
{
  "id": 10,
  "semesterCode": "SPRING2026",
  "semesterName": "Spring Semester 2026",
  "startDate": "2026-01-15",
  "endDate": "2026-05-30",
  "isActive": false,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

#### Error Codes

- `404 NOT_FOUND` - Semester ID not found

---

### 3b. Get Semester by Code

**Endpoint:** `GET /api/semesters/code/{code}`  
**Security:** JWT (AUTHENTICATED)  
**Controller:** SemesterController

**Path Parameters:**
- `code`: Semester code (e.g., "SPRING2026")

#### Response 200 OK

```json
{
  "id": 10,
  "semesterCode": "SPRING2026",
  "semesterName": "Spring Semester 2026",
  "startDate": "2026-01-15",
  "endDate": "2026-05-30",
  "isActive": false,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-01T10:00:00Z"
}
```

#### Error Codes

- `404 NOT_FOUND` - Semester code not found

---

### 3c. Get Active Semester

**Endpoint:** `GET /api/semesters/active`  
**Security:** JWT (AUTHENTICATED)  
**Controller:** SemesterController

**Purpose:** Get the currently active semester (only one semester can be active at a time)

#### Response 200 OK

```json
{
  "id": 10,
  "semesterCode": "SPRING2026",
  "semesterName": "Spring Semester 2026",
  "startDate": "2026-01-15",
  "endDate": "2026-05-30",
  "isActive": true,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-15T08:00:00Z"
}
```

#### Error Codes

- `404 NOT_FOUND` - No active semester found

---

### 3d. List All Semesters

**Endpoint:** `GET /api/semesters`  
**Security:** JWT (AUTHENTICATED)  
**Controller:** SemesterController

#### Response 200 OK

```json
[
  {
    "id": 10,
    "semesterCode": "SPRING2026",
    "semesterName": "Spring Semester 2026",
    "startDate": "2026-01-15",
    "endDate": "2026-05-30",
    "isActive": true,
    "createdAt": "2026-01-01T10:00:00Z",
    "updatedAt": "2026-01-15T08:00:00Z"
  },
  {
    "id": 9,
    "semesterCode": "FALL2025",
    "semesterName": "Fall Semester 2025",
    "startDate": "2025-09-01",
    "endDate": "2025-12-31",
    "isActive": false,
    "createdAt": "2025-08-01T10:00:00Z",
    "updatedAt": "2025-08-01T10:00:00Z"
  }
]
```

---

### 3e. Update Semester

**Endpoint:** `PUT /api/semesters/{id}`  
**Security:** JWT (ADMIN only)  
**Controller:** SemesterController

**Note:** Semester code CANNOT be updated (use semesterCode to identify, not change)

#### Request

```json
{
  "semesterName": "Spring Semester 2026 (Updated)",
  "startDate": "2026-01-20",
  "endDate": "2026-06-05"
}
```

**Validation:**
- All fields are optional
- Only provided fields will be updated

#### Response 200 OK

```json
{
  "id": 10,
  "semesterCode": "SPRING2026",
  "semesterName": "Spring Semester 2026 (Updated)",
  "startDate": "2026-01-20",
  "endDate": "2026-06-05",
  "isActive": false,
  "createdAt": "2026-01-01T10:00:00Z",
  "updatedAt": "2026-01-30T14:00:00Z"
}
```

#### Error Codes

- `404 NOT_FOUND` - Semester not found

---

### 3f. Activate Semester

**Endpoint:** `PATCH /api/semesters/{id}/activate`  
**Security:** JWT (ADMIN only)  
**Controller:** SemesterController

**Purpose:** Set a semester as active (automatically deactivates other semesters)

**Business Rule:** Only ONE semester can be active at any time

#### Response 204 No Content

```
(Empty response body)
```

#### Error Codes

- `404 NOT_FOUND` - Semester not found

**Implementation Note:**
- Atomically sets `is_active = false` for all semesters
- Then sets `is_active = true` for target semester
- Transaction ensures data consistency

---

## Group Management APIs

### 4. Create Group

**Endpoint:** `POST /api/groups`  
**Security:** JWT (ADMIN)
**Controller:** GroupController

#### Request

```json
{
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "lecturerId": 123
}
```

⚠️ **Field Types:**
- `semesterId`: Long (BIGINT) - FK to semesters.id
- `lecturerId`: Long (BIGINT) - Logical reference to Identity Service users.id

#### Response

```json
{
  "id": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "semesterCode": "SPRING2025",
  "lecturerId": 123,
  "lecturerName": "Dr. Nguyen Van A"
}
```
```

#### Error Codes

- `409 GROUP_NAME_DUPLICATE`
- `404 LECTURER_NOT_FOUND`

---

### 5. Get Group Details

**Endpoint:** `GET /api/groups/{groupId}`  
**Security:** JWT (AUTHENTICATED)
**Controller:** GroupController

⚠️ **Path Parameter:** `groupId` is Long (BIGINT), not UUID

#### Response

```json
{
  "id": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "semesterCode": "SPRING2025",
  "lecturerId": 123,
  "lecturerName": "Dr. Nguyen Van A",
  "members": [
    {
      "userId": 456,
      "groupRole": "LEADER",
      "joinedAt": "2026-01-10T08:00:00Z",
      "updatedAt": "2026-01-15T14:25:00Z"
    }
  ],
  "memberCount": 5
}
```

> **Note:** User profile data (email, fullName) is resolved by API Gateway via Identity Service. User-Group Service returns only domain membership data.

#### Error Codes

- `404 GROUP_NOT_FOUND`

---

### 6. List Groups

**Endpoint:** `GET /api/groups`  
**Security:** JWT (AUTHENTICATED)
**Controller:** GroupController

#### Query Parameters

- `page` (default: 0)
- `size` (default: 20)
- `semesterId` (optional, Long) - Filter by semester ID (e.g., 1, 2)
- `lecturerId` (optional, Long) - Filter by lecturer ID (e.g., 123)

#### Response

```json
{
  "content": [
    {
      "id": 1,
      "groupName": "SE1705-G1",
      "semesterId": 1,
      "semesterCode": "SPRING2025",
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
**Controller:** GroupController

⚠️ **Path Parameter:** `groupId` is Long (BIGINT)

#### Request

```json
{
  "groupName": "SE1705-G1-Updated",
  "lecturerId": 124
}
```

**Note:** `semesterId` is immutable (cannot be changed after creation)

#### Response

```json
{
  "id": 1,
  "groupName": "SE1705-G1-Updated",
  "semesterId": 1,
  "semesterCode": "SPRING2025",
  "lecturerId": 124,
  "lecturerName": "Dr. Nguyen Van B"
}
```
```

---

### 8. Delete Group

**Endpoint:** `DELETE /api/groups/{groupId}`  
**Security:** JWT (ADMIN)

> **Business Rule:** Group must be empty (0 members) before deletion. If group has any members (LEADER or MEMBER), request fails with 409 CONFLICT. Admin must explicitly remove all members first.

#### Deletion Behavior

**Soft Delete Strategy:**
- This endpoint performs a **soft delete** (sets `deleted_at` timestamp)
- Group record remains in database for audit/historical purposes
- Soft-deleted groups are excluded from normal queries

**Non-Cascading Policy:**
- ❌ Memberships are **NOT automatically deleted** when deleting a group
- ✅ Request fails with `409 CANNOT_DELETE_GROUP_WITH_MEMBERS` if any active memberships exist
- ✅ Admin **MUST explicitly remove all members** before deleting the group

**Rationale:**
- **Data Safety:** Prevents accidental data loss from cascade deletion
- **Explicit Actions:** Ensures admin is aware of membership removal
- **Auditability:** Clear audit trail of who removed members and when
- **Reversibility:** Memberships can be individually reviewed/restored if needed

**Deletion Workflow:**
1. Admin removes all members via `DELETE /api/groups/{groupId}/members/{userId}`
2. Verify group is empty via `GET /api/groups/{groupId}`
3. Delete the empty group via `DELETE /api/groups/{groupId}`

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `404 GROUP_NOT_FOUND` - Group doesn't exist or already deleted
- `409 CANNOT_DELETE_GROUP_WITH_MEMBERS` - Group has active memberships (count returned in error message)
- `401 UNAUTHORIZED` - Not authenticated
- `403 FORBIDDEN` - Not ADMIN

---

### 9. Update Group Lecturer

**Endpoint:** `PATCH /api/groups/{groupId}/lecturer`  
**Security:** JWT (ADMIN only)  
**Description:** Change the lecturer assigned to a group (UC27)

⚠️ **Path Parameter:** `groupId` is Long (BIGINT)

#### Request

```json
{
  "lecturerId": 125
}
```

⚠️ **Field Type:** `lecturerId` is Long (BIGINT)

#### Response

**Status:** `200 OK`

```json
{
  "id": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "semesterCode": "SPRING2025",
  "lecturerId": 125,
  "lecturerName": "Dr. New Lecturer"
}
```

> **Note:** Lecturer profile information (e.g., email) is NOT returned by User-Group Service. API Gateway resolves lecturerEmail via Identity Service during response aggregation.

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
- Application logs record each request regardless of change

#### Audit Logging

⚠️ **Audit logging is application-level only (SLF4J).** No database audit table exists.

System logs UC27 events with structured format:

```json
{
  "action": "UPDATE_GROUP_LECTURER",
  "groupId": 1,
  "oldLecturerId": 123,
  "newLecturerId": 125,
  "actorId": 100,
  "timestamp": "2026-01-31T10:30:00Z"
}
```

**Note:** Logs are written to application logs for centralized aggregation (ELK, CloudWatch, etc.), not stored in database.

#### Implementation Notes

- Uses gRPC `VerifyUserExists()` and `GetUserRole()` for validation
- Transaction-safe (rollback on gRPC failure)
- Old lecturer can still view group until audit period expires

---

## Group Member Management APIs

⚠️ **Controller:** GroupMemberController (separate from GroupController)  
**Base Path:** `/api/groups/{groupId}/members/**`

### 10. Add Member to Group

**Endpoint:** `POST /api/groups/{groupId}/members`  
**Security:** JWT (ADMIN, LECTURER)
**Controller:** GroupMemberController

⚠️ **Path Parameter:** `groupId` is Long (BIGINT)

> **Business Rule:** Only users with role `STUDENT` can be added to groups. ADMIN/LECTURER users will be rejected with `400 INVALID_ROLE`.

> **Note:** All members are added with **MEMBER** role by default. To assign a leader, add the member first, then use the promote endpoint (`PUT /api/groups/{groupId}/members/{userId}/promote`).

#### Request

```json
{
  "userId": 456
}
```

⚠️ **Field Types:**
- `userId`: Long (BIGINT) - logical reference to Identity Service

#### Response

**Status:** `201 CREATED`

```json
{
  "userId": 456,
  "groupId": 1,
  "semesterId": 1,
  "groupRole": "MEMBER",
  "joinedAt": "2026-01-15T10:30:00Z",
  "updatedAt": "2026-01-15T10:30:00Z"
}
```

> **Note:** User profile information (fullName, email) is NOT returned by User-Group Service. These fields are resolved by the API Gateway via Identity Service using batch requests to avoid N+1 queries. See "Response Aggregation Pattern" section below.

#### Response Aggregation Pattern

**Service Boundary:** User-Group Service returns only membership-related data (groupId, groupRole, timestamps). User profile data (name, email) is the responsibility of Identity Service.

**API Gateway Flow:**
1. Call User-Group Service `POST /api/groups/{groupId}/members` → receives MemberResponse with userId
2. Extract userId from response
3. Call Identity Service `GET /api/users/{userId}` → receives user profile (fullName, email)
4. Merge responses and return enriched payload to client:
```json
{
  "userId": 456,
  "groupId": 1,
  "semesterId": 1,
  "groupRole": "MEMBER",
  "joinedAt": "2026-01-15T10:30:00Z",
  "updatedAt": "2026-01-15T10:30:00Z",
  "fullName": "Student Name",
  "email": "student@example.com"
}
```

**Benefits:**
- Maintains Single Responsibility Principle
- Avoids cross-service data duplication
- Enables independent scaling of services
- Prevents tight coupling between services

#### Validation Steps

1. Verify user exists (gRPC `VerifyUserExists()`)
2. Verify user is active
3. **Verify user has STUDENT role** (gRPC `GetUserRole()`)
4. Check user not already in group
5. Check user not in another group same semester

#### Error Codes

- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`
- `400 INVALID_ROLE` - User is not STUDENT (ADMIN/LECTURER rejected)
- `409 USER_ALREADY_IN_GROUP`
- `409 USER_ALREADY_IN_GROUP_SAME_SEMESTER`
- `409 USER_INACTIVE`
- `503 SERVICE_UNAVAILABLE` - Identity Service unavailable
- `504 GATEWAY_TIMEOUT` - Identity Service timeout

---

### 11. Promote Member to Leader

**Endpoint:** `PUT /api/groups/{groupId}/members/{userId}/promote`  
**Security:** JWT (ADMIN, LECTURER)  
**Controller:** GroupMemberController
**Description:** Promote a member to group leader. Current leader is automatically demoted to MEMBER.

⚠️ **Path Parameters:** Both `groupId` and `userId` are Long (BIGINT)

#### Response

**Status:** `200 OK`

```json
{
  "userId": 456,
  "groupId": 1,
  "semesterId": 1,
  "groupRole": "LEADER",
  "joinedAt": "2026-01-10T08:00:00Z",
  "updatedAt": "2026-01-15T14:25:00Z"
}
```

> **Note:** User profile information (fullName, email) is NOT returned by User-Group Service. If needed, API Gateway aggregates this data from Identity Service. See "Response Aggregation Pattern" in UC24 (Add Member) section.

#### Business Logic

- User must be an existing member of the group
- Current leader (if exists) is automatically demoted to MEMBER
- Uses **pessimistic locking** to prevent race conditions
- Transaction-safe operation

#### Error Codes

- `404 MEMBERSHIP_NOT_FOUND` - User is not a member of this group
- `404 GROUP_NOT_FOUND` - Group doesn't exist
- `403 FORBIDDEN` - Not ADMIN or LECTURER

---

### 12. Demote Leader to Member

**Endpoint:** `PUT /api/groups/{groupId}/members/{userId}/demote`  
**Security:** JWT (ADMIN, LECTURER)  
**Controller:** GroupMemberController
**Description:** Demote a group leader to regular member. Group will have no leader after this operation.

⚠️ **Path Parameters:** Both `groupId` and `userId` are Long (BIGINT)

#### Response

**Status:** `200 OK`

```json
{
  "userId": 456,
  "groupId": 1,
  "semesterId": 1,
  "groupRole": "MEMBER",
  "joinedAt": "2026-01-10T08:00:00Z",
  "updatedAt": "2026-01-15T14:25:00Z"
}
```

> **Note:** User profile information (fullName, email) is NOT returned by User-Group Service. API Gateway performs response aggregation using Identity Service.

#### Business Logic

- User must be an existing member with LEADER role
- Group will have no leader after demotion (allowed state)
- Transaction-safe operation

#### Error Codes

- `404 MEMBERSHIP_NOT_FOUND` - User is not a member of this group
- `404 GROUP_NOT_FOUND` - Group doesn't exist
- `400 BAD_REQUEST` - User is not a LEADER
- `403 FORBIDDEN` - Not ADMIN or LECTURER

---

### 13. Remove Member from Group

**Endpoint:** `DELETE /api/groups/{groupId}/members/{userId}`  
**Security:** JWT (ADMIN)

⚠️ **Path Parameters:** Both `groupId` and `userId` are Long (BIGINT)

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `404 GROUP_NOT_FOUND` - Group doesn't exist
- `404 MEMBERSHIP_NOT_FOUND` - User is not a member of this group
- `409 CANNOT_REMOVE_LEADER` - Cannot remove LEADER when group has active members (groupRole=MEMBER)
- `401 UNAUTHORIZED` - Not authenticated
- `403 FORBIDDEN` - Not ADMIN role

**Note:** Validation checks if LEADER is being removed:
- If group has 1+ users with groupRole=MEMBER → 409 CANNOT_REMOVE_LEADER
- If group has 0 users with groupRole=MEMBER (only LEADER exists) → 204 SUCCESS

---

### 14. Get Group Members

**Endpoint:** `GET /api/groups/{groupId}/members`  
**Security:** JWT (AUTHENTICATED)

⚠️ **Path Parameter:** `groupId` is Long (BIGINT)

#### Query Parameters

- `groupRole` (optional: LEADER, MEMBER) - Filter by group role

#### Response

```json
{
  "groupId": 1,
  "groupName": "SE1705-G1",
  "members": [
    {
      "userId": 456,
      "groupId": 1,
      "semesterId": 1,
      "groupRole": "LEADER",
      "joinedAt": "2026-01-10T08:00:00Z",
      "updatedAt": "2026-01-15T14:25:00Z"
    },
    {
      "userId": 457,
      "groupId": 1,
      "semesterId": 1,
      "groupRole": "MEMBER",
      "joinedAt": "2026-01-12T09:15:00Z",
      "updatedAt": "2026-01-12T09:15:00Z"
    }
  ],
  "totalMembers": 2
}
```

> **Note:** User profile information (fullName, email) is NOT returned by User-Group Service. These fields must be resolved by the API Gateway.

#### Response Aggregation Pattern

**Service Boundary:** User-Group Service returns only membership data. User profiles are fetched separately to maintain service independence.

**API Gateway Flow:**
1. Call User-Group Service `GET /api/groups/{groupId}/members` → receives list of MemberResponse objects
2. Extract all userIds from members array: `[456, 457, ...]`
3. Call Identity Service `POST /api/users/batch` with userIds → receives batch user profiles
```json
[
  {"id": 456, "fullName": "Student Name", "email": "student@example.com"},
  {"id": 457, "fullName": "Another Student", "email": "another@example.com"}
]
```
4. Merge responses by userId and return enriched payload:
```json
{
  "groupId": 1,
  "groupName": "SE1705-G1",
  "members": [
    {
      "userId": 456,
      "groupId": 1,
      "semesterId": 1,
      "groupRole": "LEADER",
      "joinedAt": "2026-01-10T08:00:00Z",
      "updatedAt": "2026-01-15T14:25:00Z",
      "fullName": "Student Name",
      "email": "student@example.com"
    },
    {
      "userId": 457,
      "groupId": 1,
      "semesterId": 1,
      "groupRole": "MEMBER",
      "joinedAt": "2026-01-12T09:15:00Z",
      "updatedAt": "2026-01-12T09:15:00Z",
      "fullName": "Another Student",
      "email": "another@example.com"
    }
  ],
  "totalMembers": 2
}
```

**Benefits:**
- **Avoids N+1 Queries:** Single batch call to Identity Service instead of one call per member
- **Service Independence:** User-Group Service has no knowledge of user profile schema
- **Clean Architecture:** Each service maintains its own domain boundaries
- **Performance:** Batch requests are significantly faster than individual calls

**Implementation Notes:**
- API Gateway should cache user profiles for a short duration (e.g., 60 seconds)
- If Identity Service is unavailable, API Gateway can return membership data without profiles (graceful degradation)
- For deleted users, Identity Service returns a marker (isDeleted=true), allowing API Gateway to display "<Deleted User>" instead of name

---

### 15. Get User's Groups

**Endpoint:** `GET /api/users/{userId}/groups`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (any user), LECTURER (students), STUDENT (self only)

⚠️ **Path Parameter:** `userId` is Long (BIGINT)

#### Query Parameters

- `semesterId` (optional, Long) - Filter by semester ID (e.g., 1, 2)

#### Response

```json
{
  "userId": 456,
  "groups": [
    {
      "groupId": 1,
      "groupName": "SE1705-G1",
      "semesterId": 1,
      "semesterCode": "SPRING2025",
      "groupRole": "MEMBER",
      "lecturerName": "Dr. Nguyen Van A"
    }
  ]
}
```

---

## Health Check

### 16. Health Check

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
| GET | /api/users/{userId} | AUTHENTICATED | Get user profile | LECTURER: STUDENT targets only; IDs are Long |
| PUT | /api/users/{userId} | ADMIN, SELF | Update user profile (proxy) | LECTURER excluded, proxies to Identity Service |
| GET | /api/users | ADMIN | List all users | role filter deferred |
| POST | /api/groups | ADMIN | Create group | Uses semesterId (Long) |
| GET | /api/groups/{groupId} | AUTHENTICATED | Get group details | groupId is Long |
| GET | /api/groups | AUTHENTICATED | List groups | Filter by semesterId (Long) |
| PUT | /api/groups/{groupId} | ADMIN | Update group | groupId is Long |
| DELETE | /api/groups/{groupId} | ADMIN | Delete group (soft) | groupId is Long |
| PATCH | /api/groups/{groupId}/lecturer | ADMIN | Update group lecturer (UC27) | gRPC validation + audit log; IDs are Long |
| POST | /api/groups/{groupId}/members | ADMIN, LECTURER | Add member | STUDENT role only; IDs are Long |
| PUT | /api/groups/{groupId}/members/{userId}/promote | ADMIN, LECTURER | Promote to leader | Demotes current leader; IDs are Long |
| PUT | /api/groups/{groupId}/members/{userId}/demote | ADMIN, LECTURER | Demote to member | Group left without leader; IDs are Long |
| DELETE | /api/groups/{groupId}/members/{userId} | ADMIN | Remove member | Cannot remove leader; IDs are Long |
| GET | /api/groups/{groupId}/members | AUTHENTICATED | Get members | groupId is Long |
| GET | /api/users/{userId}/groups | AUTHENTICATED | Get user's groups | userId is Long; filter by semesterId |
| GET | /actuator/health | Public | Health check | Service status |

---

## Validation Rules Summary

### Group Validation

⚠️ **Note:** All IDs in this service are Long (BIGINT), not UUID.

| Field | Rule | Example |
|-------|------|---------|
| **name** | 3-50 chars, format: `[A-Z]{2,4}[0-9]{2,4}-G[0-9]+` | ✅ `SE1705-G1`, ❌ `Group 1` |
| **semester** | 4-20 chars, format: `(Spring\|Summer\|Fall\|Winter)[0-9]{4}` | ✅ `Spring2026`, ❌ `Q1-2026` |
| **lecturerId** | Must exist, Identity Service role=LECTURER, status=ACTIVE | Validated via gRPC |

### Membership Validation

| Rule | Enforcement | Error Code |
|------|-------------|------------|
| **Only STUDENT role** | gRPC validation | `INVALID_ROLE` (400) |
| **User must be ACTIVE** | gRPC validation | `USER_INACTIVE` (409) |
| **One group per semester** | DB trigger + app check | `USER_ALREADY_IN_GROUP_SAME_SEMESTER` (409) |
| **Cannot remove LEADER** | Application logic | `CANNOT_REMOVE_LEADER` (409) |

### Full Name Validation

- **Length:** 2-100 characters
- **Pattern:** Letters, spaces, Vietnamese diacritics only: `^[\p{L}\s\-]{2,100}$`
- **Examples:** ✅ `Nguyễn Văn A`, ✅ `John Doe`, ❌ `A`, ❌ `123456`

---

## Observability

### Logging

**Framework:** SLF4J + Logback

**Log Levels:**
- `ERROR` - gRPC failures, database errors, Identity Service unavailable
- `WARN` - Business rule violations (duplicate member, leader conflicts)
- `INFO` - Group/membership operations, gRPC calls (with timing)
- `DEBUG` - Detailed service flow, SQL queries

**Structured Logging:**
```json
{
  "timestamp": "2026-01-30T10:30:00Z",
  "level": "INFO",
  "logger": "GroupService",
  "message": "Group created successfully",
  "groupId": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "lecturerId": 123,
  "action": "GROUP_CREATED"
}
```

### Audit Logging

**Storage:** Application logs only (NO database audit table)

**Captured Events:**
- Group lifecycle: CREATE, UPDATE, DELETE, RESTORE
- Membership: ADD_MEMBER, REMOVE_MEMBER, PROMOTE_LEADER, DEMOTE_LEADER
- Semester: CREATE, UPDATE, ACTIVATE
- gRPC failures: IDENTITY_SERVICE_UNAVAILABLE, TIMEOUT

### Correlation ID Support

**Status:** ❌ **NOT IMPLEMENTED**

**Recommendation:** Add `CorrelationIdFilter` similar to ProjectConfig Service

### Resilience Patterns

**Circuit Breaker:** ✅ Implemented for Identity Service gRPC calls  
- Failure threshold: 50%
- Wait duration in open state: 10s
- Sliding window: 10 calls

**Retry Policy:** ✅ Implemented for Identity Service gRPC calls  
- Max attempts: 3
- Wait duration: 500ms

**Bulkhead:** ❌ Not implemented  
**Timeout:** ✅ gRPC deadline: 2 seconds (configurable)

### Kafka Integration

**Status:** ✅ Infrastructure ready, not actively used

**Configuration:**
- Bootstrap servers: `localhost:9092`
- Consumer group: `usergroup-service`
- Serialization: JSON

**Future Use Cases:**
- Listen to `user.deleted` events from Identity Service
- Listen to `user.updated` events (profile changes)
- Publish `group.created`, `membership.changed` events

### Monitoring

**Health Endpoint:** `/actuator/health`

**Metrics (Future):**
- gRPC call success/failure rate
- Circuit breaker state (open/closed/half-open)
- Group creation rate
- Membership operations per second
- Database connection pool metrics

---

## Deprecated Documents

This API Contract supersedes:
- ❌ **[04_VALIDATION_EXCEPTIONS.md](04_VALIDATION_EXCEPTIONS.md)** - Validation rules merged into this document