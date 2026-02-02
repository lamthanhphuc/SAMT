# System Interaction & Integration Guide

**Document Version:** 1.0  
**Generated:** 2026-02-02  
**Purpose:** Detailed service interaction patterns, gRPC flows, and integration guidelines

---

## Table of Contents

1. [Service Communication Patterns](#service-communication-patterns)
2. [gRPC Contract Details](#grpc-contract-details)
3. [End-to-End Use Case Flows](#end-to-end-use-case-flows)
4. [Error Propagation & Handling](#error-propagation--handling)
5. [Performance & Optimization](#performance--optimization)
6. [Integration Checklist](#integration-checklist)

---

## Service Communication Patterns

### Pattern 1: REST → JWT → gRPC → Database

**Most Common Flow:**

```
Client (Browser/Mobile)
    ↓ HTTP
┌─────────────────────────────────────────┐
│ API Gateway (Future)                    │
│ - Route based on path                   │
│ - Forward Authorization header          │
└─────────────────────────────────────────┘
    ↓ HTTP
┌─────────────────────────────────────────┐
│ User-Group Service (Port 8082)          │
│ - Validate JWT signature                │
│ - Extract userId + roles from claims    │
│ - Check authorization (role-based)      │
└─────────────────────────────────────────┘
    ↓ gRPC
┌─────────────────────────────────────────┐
│ Identity Service (Port 9090 gRPC)       │
│ - Fetch user data from PostgreSQL       │
│ - Return gRPC response                  │
└─────────────────────────────────────────┘
    ↓
PostgreSQL (identity_db)
```

**Example: Get Group Details**

1. **Client:** `GET /groups/{groupId}` with `Authorization: Bearer <JWT>`
2. **User-Group Service:** 
   - Validate JWT → extract userId + roles
   - Query `groups` table by groupId
   - Query `user_groups` table by groupId → get list of userIds
   - gRPC call: `GetUsers([456, 789])` to Identity Service
3. **Identity Service:**
   - Query `users` table by IDs
   - Return `GetUsersResponse` proto
4. **User-Group Service:**
   - Combine group data + user data
   - Return HTTP JSON response
5. **Client:** Receive group details with member names

---

### Pattern 2: REST → Database Only (No gRPC)

**Direct Operations (No User Data Needed):**

```
Client
    ↓ HTTP
Identity Service
    ↓ JPA
PostgreSQL (identity_db)
```

**Examples:**
- `POST /api/auth/login` - Validate password, generate JWT (no gRPC)
- `POST /api/auth/refresh` - Rotate refresh token (no gRPC)
- `GET /api/admin/audit-logs` - Query audit logs (no gRPC)

**No Cross-Service Communication.**

---

### Pattern 3: Cascade gRPC (User-Group → Identity)

**Validation Before Write:**

```
User-Group Service
    ↓ gRPC (VerifyUserExists)
Identity Service
    ↓ Return exists=true, active=true
User-Group Service
    ↓ gRPC (GetUserRole)
Identity Service
    ↓ Return role=STUDENT
User-Group Service
    ↓ Save membership
PostgreSQL (usergroup_db)
```

**Example: Add Member to Group (UC24)**

```java
// Step 1: Validate user exists and active
VerifyUserResponse verification = identityServiceClient.verifyUserExists(userId);
if (!verification.getExists() || !verification.getActive()) {
    throw ...;
}

// Step 2: Validate user role is STUDENT
GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(userId);
if (roleResponse.getRole() != UserRole.STUDENT) {
    throw ...;
}

// Step 3: Save membership
UserGroup membership = userGroupRepository.save(...);

// Step 4: Fetch user info for response
GetUserResponse userInfo = identityServiceClient.getUser(userId);
return MemberResponse.of(membership, userInfo);
```

**gRPC Calls:** 3 calls (verify, get role, get user)

**Optimization Opportunity:** Combine into single gRPC method `ValidateAndGetStudent(userId)` (not yet implemented).

---

## gRPC Contract Details

### Proto File Structure

**Location:** `src/main/proto/user_service.proto`

**Package:** `com.example.identityservice.grpc` (shared by both services)

**Service Definition:**

```protobuf
service UserGrpcService {
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  rpc GetUserRole(GetUserRoleRequest) returns (GetUserRoleResponse);
  rpc VerifyUserExists(VerifyUserRequest) returns (VerifyUserResponse);
  rpc GetUsers(GetUsersRequest) returns (GetUsersResponse);
  rpc UpdateUser(UpdateUserRequest) returns (UpdateUserResponse);
  rpc ListUsers(ListUsersRequest) returns (ListUsersResponse);
}
```

---

### Method: GetUser

**Purpose:** Fetch full user profile by ID.

**Request:**

```protobuf
message GetUserRequest {
  string user_id = 1;  // Long as string (e.g., "123")
}
```

**Response:**

```protobuf
message GetUserResponse {
  string user_id = 1;
  string email = 2;
  string full_name = 3;
  UserStatus status = 4;  // ACTIVE | LOCKED
  UserRole role = 5;      // ADMIN | LECTURER | STUDENT
  bool deleted = 6;       // true if soft-deleted
}
```

**Implementation Notes:**

- **Server:** Uses `findByIdIgnoreDeleted()` to bypass soft delete filter
- **Client:** Used in 90% of gRPC calls (fetch user info for display)
- **Error:** Returns `NOT_FOUND` if user ID doesn't exist

**Example Usage:**

```java
// Java client
GetUserRequest request = GetUserRequest.newBuilder()
    .setUserId("123")
    .build();

GetUserResponse response = userStub
    .withDeadlineAfter(3, TimeUnit.SECONDS)
    .getUser(request);

String fullName = response.getFullName();  // "John Doe"
boolean isDeleted = response.getDeleted(); // false
```

---

### Method: GetUserRole

**Purpose:** Fetch user's system role only (lightweight, no email/name).

**Request:**

```protobuf
message GetUserRoleRequest {
  string user_id = 1;
}
```

**Response:**

```protobuf
message GetUserRoleResponse {
  UserRole role = 1;  // ADMIN=0, LECTURER=1, STUDENT=2
}
```

**Implementation Notes:**

- **Server:** Returns role from `users.role` column
- **Client:** Used in authorization checks (e.g., validate lecturer)
- **Performance:** Faster than `GetUser` (no email/name in response)

**Example Usage:**

```java
GetUserRoleResponse response = identityServiceClient.getUserRole(123L);

if (response.getRole() != UserRole.LECTURER) {
    throw new ConflictException("User is not a lecturer");
}
```

---

### Method: VerifyUserExists

**Purpose:** Check if user exists and is active (lightweight validation).

**Request:**

```protobuf
message VerifyUserRequest {
  string user_id = 1;
}
```

**Response:**

```protobuf
message VerifyUserResponse {
  bool exists = 1;       // User found in database
  bool active = 2;       // status == ACTIVE
  string message = 3;    // Human-readable status
}
```

**Response Examples:**

```json
// User exists and active
{ "exists": true, "active": true, "message": "User exists and is active" }

// User exists but locked
{ "exists": true, "active": false, "message": "User exists but not active" }

// User not found
{ "exists": false, "active": false, "message": "User not found" }
```

**Implementation Notes:**

- **Server:** Queries `users` table, checks `status == ACTIVE`
- **Client:** Used before adding member to group (validate user is active)

---

### Method: GetUsers (Batch)

**Purpose:** Fetch multiple users in one call (avoid N+1 problem).

**Request:**

```protobuf
message GetUsersRequest {
  repeated string user_ids = 1;  // List of Long as strings
}
```

**Response:**

```protobuf
message GetUsersResponse {
  repeated GetUserResponse users = 1;
}
```

**Implementation Notes:**

- **Server:** Uses `findAllByIdInIgnoreDeleted(userIds)` (single SQL query with IN clause)
- **Client:** CRITICAL for performance when displaying group members
- **Behavior:** Returns only users that exist (missing IDs silently skipped)

**Example Usage:**

```java
// Fetch 10 users in one gRPC call
GetUsersRequest request = GetUsersRequest.newBuilder()
    .addAllUserIds(List.of("123", "456", "789", ...))
    .build();

GetUsersResponse response = userStub.getUsers(request);

// response.getUsersList() contains 0-10 users (depending on how many exist)
for (GetUserResponse user : response.getUsersList()) {
    System.out.println(user.getFullName());
}
```

**Performance:**

| Approach         | gRPC Calls | DB Queries | Latency (10 users) |
|------------------|------------|------------|---------------------|
| GetUser loop     | 10         | 10         | ~300ms              |
| GetUsers batch   | 1          | 1          | ~30ms               |

**ALWAYS USE BATCH** when fetching multiple users.

---

### Method: UpdateUser

**Purpose:** Proxy pattern for UC22 (update user profile from User-Group Service).

**Request:**

```protobuf
message UpdateUserRequest {
  string user_id = 1;
  string full_name = 2;
}
```

**Response:**

```protobuf
message UpdateUserResponse {
  GetUserResponse user = 1;  // Updated user profile
}
```

**Implementation Notes:**

- **Server:** Updates `users.full_name`, returns updated user
- **Client:** Used in `PUT /users/{userId}` endpoint (User-Group Service)
- **Why Exists:** User data stored in Identity Service only

**Example Usage:**

```java
UpdateUserRequest request = UpdateUserRequest.newBuilder()
    .setUserId("123")
    .setFullName("John Doe - Updated")
    .build();

UpdateUserResponse response = userStub.updateUser(request);

// response.getUser() contains updated user profile
```

---

### Method: ListUsers

**Purpose:** List users with pagination and filters.

**Request:**

```protobuf
message ListUsersRequest {
  int32 page = 1;
  int32 size = 2;
  string status = 3;  // "ACTIVE" | "LOCKED" | null
  string role = 4;    // "ADMIN" | "LECTURER" | "STUDENT" | null
}
```

**Response:**

```protobuf
message ListUsersResponse {
  repeated GetUserResponse users = 1;
  int64 total_elements = 2;
}
```

**Implementation Notes:**

- **Server:** Uses paginated JPA query with filters
- **Client:** Used in `GET /users` endpoint (User-Group Service, ADMIN only)

---

## End-to-End Use Case Flows

### Use Case: UC24 - Add Member to Group

**Actors:** ADMIN or LECTURER (group owner)

**Preconditions:**
- Group exists
- User exists and is STUDENT
- User not already in a group for this semester

**Flow:**

```
1. Client → POST /groups/{groupId}/members
   Authorization: Bearer <JWT>
   {
     "userId": 456,
     "isLeader": false
   }

2. User-Group Service: Validate JWT
   - Extract actorId=999, actorRoles=["ADMIN"] from JWT
   - Set SecurityContext

3. User-Group Service: Check authorization
   - Query groups table: group.lecturerId == 123
   - Check: actorId==999 is ADMIN OR actorId==123 (lecturer) → PASS

4. User-Group Service → Identity Service: VerifyUserExists(456)
   gRPC Request:
   {
     "user_id": "456"
   }
   
   gRPC Response:
   {
     "exists": true,
     "active": true,
     "message": "User exists and is active"
   }

5. User-Group Service → Identity Service: GetUserRole(456)
   gRPC Request:
   {
     "user_id": "456"
   }
   
   gRPC Response:
   {
     "role": STUDENT  // Enum value 2
   }
   
   Validation: role == STUDENT → PASS

6. User-Group Service: Check business rules
   - Query user_groups: userId=456, groupId=... → NOT EXISTS → PASS
   - Query user_groups JOIN groups: userId=456, semester="2024-FALL" → NOT EXISTS → PASS
   - If isLeader=true: Query user_groups: groupId=..., role=LEADER → NOT EXISTS → PASS

7. User-Group Service: Save membership
   INSERT INTO user_groups (user_id, group_id, role, created_at)
   VALUES (456, 'uuid...', 'MEMBER', NOW())

8. User-Group Service → Identity Service: GetUser(456)
   gRPC Request:
   {
     "user_id": "456"
   }
   
   gRPC Response:
   {
     "user_id": "456",
     "email": "john@example.com",
     "full_name": "John Doe",
     "status": ACTIVE,
     "role": STUDENT,
     "deleted": false
   }

9. User-Group Service → Client: 201 Created
   {
     "userId": 456,
     "groupId": "uuid...",
     "fullName": "John Doe",
     "email": "john@example.com",
     "role": "MEMBER"
   }
```

**gRPC Calls:** 3 (VerifyUserExists, GetUserRole, GetUser)

**Database Queries:**
- User-Group Service: 4 queries (group, 2 business rule checks, insert)
- Identity Service: 3 queries (verify, get role, get user)

**Total Latency:** ~150ms (3 × 30ms gRPC + 60ms DB)

---

### Use Case: UC25 - Promote Member to Leader

**Actors:** ADMIN or LECTURER (group owner)

**Preconditions:**
- Group exists
- Member exists in group
- If demoting old leader, transaction must be atomic

**Flow:**

```
1. Client → PUT /groups/{groupId}/members/{userId}/role
   Authorization: Bearer <JWT>
   {
     "role": "LEADER"
   }

2. User-Group Service: Validate JWT
   - Extract actorId, actorRoles from JWT

3. User-Group Service: Check authorization
   - Same as UC24

4. User-Group Service: Start transaction (SERIALIZABLE isolation)
   BEGIN TRANSACTION ISOLATION LEVEL SERIALIZABLE;

5. User-Group Service: Acquire lock on old leader
   SELECT * FROM user_groups
   WHERE group_id = '...' AND role = 'LEADER'
   FOR UPDATE;  -- Pessimistic lock
   
   Result: userId=789, role=LEADER (locked)

6. User-Group Service: Demote old leader
   UPDATE user_groups
   SET role = 'MEMBER'
   WHERE user_id = 789 AND group_id = '...';

7. User-Group Service: Promote new leader
   UPDATE user_groups
   SET role = 'LEADER'
   WHERE user_id = 456 AND group_id = '...';

8. User-Group Service: Commit transaction
   COMMIT;

9. User-Group Service → Identity Service: GetUser(456)
   gRPC call to fetch user info for response

10. User-Group Service → Client: 200 OK
    {
      "userId": 456,
      "groupId": "uuid...",
      "fullName": "John Doe",
      "email": "john@example.com",
      "role": "LEADER"
    }
```

**Concurrency Handling:**

**Scenario:** Two concurrent requests promote different members.

```
Thread A: Promote userId=456
Thread B: Promote userId=789

Time  Thread A                        Thread B
----  -------------------------------- --------------------------------
T0    BEGIN SERIALIZABLE               
T1    SELECT ... FOR UPDATE (lock 789)
T2                                     BEGIN SERIALIZABLE
T3                                     SELECT ... FOR UPDATE (WAIT - blocked by A's lock)
T4    UPDATE role=MEMBER (userId=789)
T5    UPDATE role=LEADER (userId=456)
T6    COMMIT (release lock)
T7                                     (lock acquired)
T8                                     Find no leader (userId=456 is now leader)
T9                                     UPDATE role=LEADER (userId=789) - would create 2 leaders!
T10                                    SERIALIZABLE ABORT (phantom read detected)
T11                                    ROLLBACK
```

**Result:** Thread A succeeds, Thread B gets serialization error (retryable).

---

### Use Case: UC21 - Get User Profile

**Actors:** ADMIN (all users), LECTURER (students only), STUDENT (self only)

**Flow:**

```
1. Client → GET /users/456
   Authorization: Bearer <JWT (actorId=123, roles=[LECTURER])>

2. User-Group Service: Validate JWT
   - Extract actorId=123, actorRoles=["LECTURER"]

3. User-Group Service: Check authorization
   - actorId != 456 (not self)
   - actorRoles contains "LECTURER"
   - Need to verify userId=456 is STUDENT (LECTURER can only view students)

4. User-Group Service → Identity Service: GetUserRole(456)
   gRPC Response: role=STUDENT → PASS

5. User-Group Service → Identity Service: GetUser(456)
   gRPC Response: full user profile

6. User-Group Service → Client: 200 OK
   {
     "id": 456,
     "email": "john@example.com",
     "fullName": "John Doe",
     "roles": ["STUDENT"],
     "status": "ACTIVE"
   }
```

**Authorization Matrix:**

| Actor Role | Target User | gRPC Calls              | Result    |
|------------|-------------|-------------------------|-----------|
| ADMIN      | Any         | GetUser                 | ALLOWED   |
| LECTURER   | STUDENT     | GetUserRole, GetUser    | ALLOWED   |
| LECTURER   | LECTURER    | GetUserRole             | FORBIDDEN |
| LECTURER   | ADMIN       | GetUserRole             | FORBIDDEN |
| STUDENT    | Self        | GetUser                 | ALLOWED   |
| STUDENT    | Other       | -                       | FORBIDDEN |

---

### Use Case: UC22 - Update User Profile

**Actors:** ADMIN (all users), STUDENT (self only), LECTURER (EXCLUDED)

**Flow:**

```
1. Client → PUT /users/456
   Authorization: Bearer <JWT (actorId=456, roles=[STUDENT])>
   {
     "fullName": "John Doe - Updated"
   }

2. User-Group Service: Validate JWT
   - Extract actorId=456, actorRoles=["STUDENT"]

3. User-Group Service: Check authorization
   - actorRoles contains "LECTURER"? NO → PASS (LECTURER excluded)
   - actorId == 456 (self)? YES → PASS

4. User-Group Service → Identity Service: UpdateUser(456, "John Doe - Updated")
   gRPC Request:
   {
     "user_id": "456",
     "full_name": "John Doe - Updated"
   }
   
   gRPC Response:
   {
     "user": { /* updated user profile */ }
   }

5. Identity Service: Update database
   UPDATE users
   SET full_name = 'John Doe - Updated', updated_at = NOW()
   WHERE id = 456;

6. User-Group Service → Client: 200 OK
   {
     "id": 456,
     "email": "john@example.com",
     "fullName": "John Doe - Updated",
     "roles": ["STUDENT"],
     "status": "ACTIVE"
   }
```

**CRITICAL:** LECTURER is explicitly excluded even if updating own profile.

**Rationale:** Business rule per SRS (UC22-AUTH).

---

## Error Propagation & Handling

### gRPC Error → HTTP Error Mapping

**Pattern:**

```java
try {
    GetUserResponse user = identityServiceClient.getUser(userId);
    // Success path
} catch (StatusRuntimeException e) {
    log.error("gRPC call failed: {}", e.getStatus());
    
    switch (e.getStatus().getCode()) {
        case NOT_FOUND:
            throw new ResourceNotFoundException("USER_NOT_FOUND", 
                "User not found: " + userId);
        
        case INVALID_ARGUMENT:
            throw new BadRequestException("INVALID_USER_ID", 
                "Invalid user ID format");
        
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException("IDENTITY_SERVICE_TIMEOUT", 
                "Identity Service did not respond in time");
        
        case UNAVAILABLE:
            throw new ServiceUnavailableException("IDENTITY_SERVICE_DOWN", 
                "Identity Service is unavailable");
        
        default:
            throw new InternalServerErrorException("INTERNAL_ERROR", 
                "Unexpected error: " + e.getStatus().getCode());
    }
}
```

**Mapping Table:**

| gRPC Status Code    | HTTP Status | Error Code                | Client Action          |
|---------------------|-------------|---------------------------|------------------------|
| `OK`                | 200         | -                         | Success                |
| `NOT_FOUND`         | 404         | USER_NOT_FOUND            | Display error message  |
| `INVALID_ARGUMENT`  | 400         | INVALID_USER_ID           | Fix request data       |
| `DEADLINE_EXCEEDED` | 504         | IDENTITY_SERVICE_TIMEOUT  | Retry after delay      |
| `UNAVAILABLE`       | 503         | IDENTITY_SERVICE_DOWN     | Retry after delay      |
| `INTERNAL`          | 500         | INTERNAL_ERROR            | Contact support        |

---

### Cascading Failures

**Scenario:** Identity Service is down.

```
Client → User-Group Service → Identity Service (gRPC)
                                      ↓
                                   UNAVAILABLE

User-Group Service → Client: 503 Service Unavailable
{
  "errorCode": "IDENTITY_SERVICE_DOWN",
  "message": "Identity Service is unavailable",
  "timestamp": "2026-02-02T10:30:00Z"
}
```

**Impact:**
- ALL endpoints requiring user data fail (UC21, UC22, UC24, etc.)
- Group CRUD without user data still works (create, update, delete)

**Mitigation (Future):**
- Circuit breaker (fail fast after N failures)
- Fallback: return partial data with `"userDeleted": true`
- Caching: serve stale user data from cache

---

### Transactional Boundaries & gRPC

**CRITICAL:** gRPC calls are NOT transactional.

**Example: Create Group with gRPC Validation**

```java
@Transactional
public GroupResponse createGroup(CreateGroupRequest request) {
    // Step 1: Validate lecturer via gRPC (NOT in transaction)
    VerifyUserResponse verification = identityServiceClient.verifyUserExists(request.getLecturerId());
    if (!verification.getExists()) {
        throw ResourceNotFoundException.lecturerNotFound();
    }
    
    // Step 2: Save group (IN transaction)
    Group group = groupRepository.save(...);
    
    // Step 3: Fetch lecturer name via gRPC (NOT in transaction)
    GetUserResponse lecturer = identityServiceClient.getUser(request.getLecturerId());
    
    return GroupResponse.of(group, lecturer);
}
```

**What Could Go Wrong:**

1. **Lecturer deleted between validation and save:**
   - Validation passes (lecturer exists)
   - Lecturer soft-deleted in Identity Service
   - Group saved with invalid lecturerId
   - **Mitigation:** Validate again before critical operations, handle deleted users gracefully

2. **gRPC timeout after database commit:**
   - Group saved successfully
   - GetUser call times out (3s deadline exceeded)
   - Transaction committed (cannot rollback)
   - Client receives 504 Gateway Timeout
   - **Result:** Group created but client thinks it failed
   - **Mitigation:** Client should check if group exists before retrying

**Best Practice:** Separate read-only gRPC calls from transactional writes.

---

## Performance & Optimization

### 1. Batch Fetching (N+1 Prevention)

**Problem:**

```java
// BAD: N+1 gRPC calls
for (UserGroup membership : memberships) {
    GetUserResponse user = identityServiceClient.getUser(membership.getUserId());
    members.add(new MemberInfo(user));
}
```

**Solution:**

```java
// GOOD: 1 batch gRPC call
List<Long> userIds = memberships.stream()
    .map(UserGroup::getUserId)
    .toList();

GetUsersResponse usersResponse = identityServiceClient.getUsers(userIds);

// Map back
for (UserGroup membership : memberships) {
    GetUserResponse user = usersResponse.getUsersList().stream()
        .filter(u -> Long.parseLong(u.getUserId()) == membership.getUserId())
        .findFirst()
        .orElse(null);
    
    members.add(new MemberInfo(user));
}
```

**Performance:**

| Group Size | N+1 Approach | Batch Approach | Improvement |
|------------|--------------|----------------|-------------|
| 5 members  | 150ms        | 30ms           | 5x faster   |
| 10 members | 300ms        | 30ms           | 10x faster  |
| 50 members | 1500ms       | 50ms           | 30x faster  |

**Rule:** Always batch when fetching >1 user.

---

### 2. gRPC Connection Pooling

**Current:** gRPC client reuses single channel (connection pooling built-in).

**Configuration:**

```yaml
grpc:
  client:
    identity-service:
      address: 'static://localhost:9090'
      negotiation-type: plaintext
      max-inbound-message-size: 4194304  # 4MB
```

**Performance:** Connection reuse reduces latency (no TCP handshake per call).

---

### 3. Deadline Enforcement

**Problem:** Slow gRPC calls block request threads.

**Solution:** Per-call deadline (3 seconds).

```java
return userStub
    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
    .getUser(request);
```

**Behavior:**
- If Identity Service responds in <3s → success
- If Identity Service takes >3s → `DEADLINE_EXCEEDED` error
- Client receives 504 Gateway Timeout

**Why 3 seconds?**
- Most gRPC calls complete in <100ms
- 3s accommodates slow database queries
- Prevents indefinite blocking

**Future:** Adaptive timeout based on percentiles (P99).

---

### 4. Database Query Optimization

**Identity Service:**

| Query                           | Index Used              | Performance      |
|---------------------------------|-------------------------|------------------|
| `findByEmail`                   | `idx_users_email`       | O(log N) fast    |
| `findById`                      | PK index                | O(log N) fast    |
| `findAllByIdIn` (batch)         | PK index + IN clause    | O(K log N) fast  |
| `findAllWithFilters` (pagination)| `idx_users_status`, `idx_users_role` | O(log N + K) fast |

**User-Group Service:**

| Query                           | Index Used                      | Performance      |
|---------------------------------|---------------------------------|------------------|
| `findById` (group)              | PK index                        | O(log N) fast    |
| `existsByGroupNameAndSemester`  | `uq_group_semester`             | O(log N) fast    |
| `existsByUserIdAndSemester`     | **NO INDEX** (needs composite)  | O(N) SLOW        |
| `findLeaderByGroupIdWithLock`   | **NO INDEX** on role            | O(N) SLOW        |

**Optimization Needed:**
- Add index on `user_groups(user_id, group_id)` (composite PK covers this)
- Add index on `user_groups(role)` for leader lookup
- Add computed column for semester + index (for one-group-per-semester check)

---

### 5. Soft Delete Performance

**Identity Service:**

```java
@Entity
@SQLRestriction("deleted_at IS NULL")
public class User { ... }
```

**Generated SQL:**

```sql
-- findById(123)
SELECT * FROM users WHERE id = 123 AND deleted_at IS NULL;

-- findByEmail('john@example.com')
SELECT * FROM users WHERE email = 'john@example.com' AND deleted_at IS NULL;
```

**Index:** `idx_users_deleted_at` supports `deleted_at IS NULL` filter.

**Performance:** O(log N) with index (same as without soft delete).

---

## Integration Checklist

### For New Services Integrating with Identity Service

- [ ] **gRPC Client Setup:**
  - [ ] Add gRPC dependency to `pom.xml`
  - [ ] Copy `user_service.proto` to `src/main/proto/`
  - [ ] Configure gRPC client in `application.yml`
  - [ ] Set deadline (recommended: 3 seconds)

- [ ] **Authentication:**
  - [ ] Copy `JwtAuthenticationFilter.java` from User-Group Service
  - [ ] Configure same `JWT_SECRET` as Identity Service (CRITICAL)
  - [ ] Extract userId + roles from JWT claims
  - [ ] Do NOT load user from local database (use gRPC)

- [ ] **Authorization:**
  - [ ] Implement role-based checks in service layer
  - [ ] Use gRPC `GetUserRole` to validate target user role
  - [ ] Use gRPC `VerifyUserExists` before foreign key writes

- [ ] **Error Handling:**
  - [ ] Catch `StatusRuntimeException` on all gRPC calls
  - [ ] Map gRPC status codes to HTTP status codes
  - [ ] Log gRPC errors with context (userId, operation)

- [ ] **Performance:**
  - [ ] Use `GetUsers` batch method (avoid N+1)
  - [ ] Set gRPC deadlines on all calls
  - [ ] Implement retry logic (exponential backoff)

- [ ] **Testing:**
  - [ ] Test with Identity Service down (verify 503 responses)
  - [ ] Test with gRPC timeout (verify 504 responses)
  - [ ] Test with deleted users (verify graceful handling)

---

### For Identity Service API Changes

- [ ] **Proto Changes:**
  - [ ] Update `user_service.proto`
  - [ ] Regenerate Java classes (`mvn clean compile`)
  - [ ] Update server implementation (`UserGrpcServiceImpl.java`)
  - [ ] Test backward compatibility (old clients still work)

- [ ] **Notify Consumers:**
  - [ ] User-Group Service team
  - [ ] Future services (Analysis Service, Notification Service)
  - [ ] Document breaking changes in changelog

- [ ] **Migration Plan:**
  - [ ] Deploy Identity Service first
  - [ ] Deploy User-Group Service second (after proto sync)
  - [ ] Rollback plan if gRPC compatibility breaks

---

### For User-Group Service Team

- [ ] **Before Creating New Endpoints:**
  - [ ] Check if user data needed (if yes, use gRPC)
  - [ ] Identify authorization rules (ADMIN, LECTURER, STUDENT)
  - [ ] Plan gRPC calls (batch when possible)

- [ ] **Before Database Changes:**
  - [ ] Check if new user field needed (if yes, add to Identity Service first)
  - [ ] Do NOT store user data locally (userId foreign keys only)

- [ ] **Monitoring:**
  - [ ] Log all gRPC errors (user ID, operation, status code)
  - [ ] Alert on high gRPC error rate (>5%)
  - [ ] Alert on gRPC timeout rate (>1%)

---

## Troubleshooting Guide

### Issue: 503 Service Unavailable

**Symptom:** All User-Group Service endpoints fail with 503.

**Diagnosis:**

```bash
# Check Identity Service health
curl http://localhost:8081/actuator/health

# Check gRPC port is listening
netstat -an | grep 9090

# Check gRPC connectivity
grpcurl -plaintext localhost:9090 list
```

**Resolution:**
1. Start Identity Service
2. Verify gRPC port 9090 is open
3. Check firewall rules
4. Verify `application.yml` has correct gRPC address

---

### Issue: 504 Gateway Timeout

**Symptom:** Intermittent 504 errors on User-Group Service.

**Diagnosis:**

```bash
# Check Identity Service logs for slow queries
grep "SlowQuery" /var/log/identity-service.log

# Check database performance
SELECT * FROM pg_stat_activity WHERE state = 'active';
```

**Resolution:**
1. Increase gRPC deadline (from 3s to 5s) - temporary fix
2. Optimize slow database queries (add indexes)
3. Increase database connection pool size

---

### Issue: Deleted User Shows Incorrect Data

**Symptom:** Group member list shows deleted user as `"<Deleted User>"` but email is not null.

**Diagnosis:**

```java
// Check gRPC response
GetUserResponse user = identityServiceClient.getUser(userId);
log.info("User deleted flag: {}", user.getDeleted());
log.info("User email: {}", user.getEmail());
```

**Root Cause:** User-Group Service not checking `deleted` flag.

**Resolution:**

```java
String email = (user != null && !user.getDeleted()) 
    ? user.getEmail() 
    : null;
```

---

**End of System Interaction Summary**
