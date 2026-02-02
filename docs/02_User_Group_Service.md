# User-Group Service - Technical Documentation

**Service Name:** User-Group Service  
**Version:** 1.0  
**Port:** 8082  
**Database:** PostgreSQL (usergroup_db)  
**Last Updated:** 2026-02-02

---

## Table of Contents

1. [Service Overview](#service-overview)
2. [Architecture & Package Structure](#architecture--package-structure)
3. [Data Model](#data-model)
4. [REST API Contracts](#rest-api-contracts)
5. [gRPC Integration](#grpc-integration)
6. [Security Design](#security-design)
7. [Transaction & Consistency](#transaction--consistency)
8. [Error Handling](#error-handling)
9. [Important Implementation Notes](#important-implementation-notes)

---

## Service Overview

### Primary Responsibilities

User-Group Service manages student groups and group memberships for academic project assignments.

**What it DOES:**
- ✅ Group CRUD operations (create, read, update, delete)
- ✅ Group membership management (add, remove, promote, demote)
- ✅ Business rules enforcement:
  - One LEADER per group
  - One group per student per semester
  - Lecturer validation (must be LECTURER role and ACTIVE)
  - Only STUDENT can be added to groups
- ✅ User profile proxy operations (UC21, UC22 - delegated to Identity Service)
- ✅ Role-based authorization (ADMIN, LECTURER, STUDENT)

**What it DOES NOT DO:**
- ❌ User authentication/registration (delegated to Identity Service)
- ❌ Password management (delegated to Identity Service)
- ❌ JWT generation (only validates JWT from Identity Service)
- ❌ Store user data (userId foreign keys only, fetch data via gRPC)
- ❌ Audit logging (not implemented yet - known tech debt)

### Dependencies

**Internal Services (via gRPC):**
- ✅ **Identity Service** (required)
  - User validation (VerifyUserExists)
  - Role validation (GetUserRole)
  - User profile data (GetUser, GetUsers)
  - Profile update proxy (UpdateUser)
  - User listing (ListUsers)

**External Services:**
- PostgreSQL database (usergroup_db)

---

## Architecture & Package Structure

### Maven Module Structure

```
user-group-service/
├── src/main/java/com/example/user_groupservice/
│   ├── config/               # Configuration classes
│   │   ├── SecurityConfig.java           # JWT filter chain (no auth, only validation)
│   │   └── OpenApiConfig.java            # Swagger/OpenAPI config
│   │
│   ├── controller/           # REST API endpoints
│   │   ├── GroupController.java          # /groups/** (group CRUD)
│   │   ├── GroupMemberController.java    # /groups/{id}/members/** (membership ops)
│   │   └── UserController.java           # /users/** (profile proxy)
│   │
│   ├── dto/                  # Data Transfer Objects
│   │   ├── request/          # Request DTOs
│   │   └── response/         # Response DTOs
│   │
│   ├── entity/               # JPA Entities
│   │   ├── Group.java                    # groups table (soft delete)
│   │   ├── UserGroup.java                # user_groups table (composite PK, soft delete)
│   │   ├── UserGroupId.java              # Composite key class
│   │   └── [enums]                       # GroupRole, SystemRole
│   │
│   ├── exception/            # Custom exceptions + global handler
│   │   ├── GlobalExceptionHandler.java   # REST error responses
│   │   └── [specific exceptions]         # ResourceNotFoundException, etc.
│   │
│   ├── grpc/                 # gRPC client integration
│   │   ├── IdentityServiceClient.java    # gRPC client to Identity Service
│   │   └── [proto generated classes]     # UserGrpcServiceGrpc, GetUserResponse, etc.
│   │
│   ├── mapper/               # DTO mappers
│   │   └── UserGrpcMapper.java           # Map gRPC responses to DTOs
│   │
│   ├── repository/           # Spring Data JPA repositories
│   │   ├── GroupRepository.java
│   │   └── UserGroupRepository.java
│   │
│   ├── security/             # Security infrastructure
│   │   ├── JwtAuthenticationFilter.java  # JWT validation only (no user loading)
│   │   └── CurrentUser.java              # UserDetails implementation (userId + roles)
│   │
│   └── service/              # Business logic
│       ├── GroupService.java             # Group operations
│       ├── GroupMemberService.java       # Membership operations
│       └── UserService.java              # User profile proxy operations
│
├── src/main/proto/
│   └── user_service.proto    # gRPC contract (copied from Identity Service)
│
└── src/main/resources/
    ├── application.yml       # Configuration (DB, gRPC client)
    └── [other configs]
```

### Request Flow Diagram

#### REST Request Flow

```
HTTP Request (e.g., POST /groups)
    ↓
┌───────────────────────────────────────┐
│ JwtAuthenticationFilter               │
│ - Extract & validate JWT signature    │
│ - Extract userId + roles from claims  │
│ - Set CurrentUser in SecurityContext  │
│ - NO database lookup                  │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ Spring Security Filter Chain          │
│ - CORS                                │
│ - @PreAuthorize checks                │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ GroupController / UserController      │
│ - Validate DTO (@Valid)               │
│ - Extract CurrentUser from context    │
└───────────────────────────────────────┘
    ↓
┌───────────────────────────────────────┐
│ GroupService / UserService            │
│ - Business logic                      │
│ - Authorization checks (role-based)   │
│ - gRPC calls to Identity Service      │
│ - Transaction management              │
└───────────────────────────────────────┘
    ↓                                    ↓
┌───────────────────────────┐   ┌───────────────────────────┐
│ GroupRepository           │   │ IdentityServiceClient     │
│ - JPA queries             │   │ - gRPC calls (3s timeout) │
│ - Soft delete filtering   │   │ - User validation         │
└───────────────────────────┘   └───────────────────────────┘
    ↓                                    ↓
PostgreSQL (usergroup_db)         Identity Service (gRPC)
```

**Key Difference from Identity Service:**
- JWT filter does NOT load user from database
- User data fetched via gRPC on-demand
- SecurityContext contains userId + roles only (from JWT claims)

---

## Data Model

### Entity Relationship Diagram

```
┌─────────────────────────────────┐
│          groups                 │
├─────────────────────────────────┤
│ PK id (UUID)                    │
│ UK group_name, semester         │ ← Composite unique constraint
│    lecturer_id (BIGINT)         │ ← No FK, validated via gRPC
│    semester (VARCHAR 20)        │
│    deleted_at (TIMESTAMP)       │ ← Soft delete
│    created_at (TIMESTAMP)       │
│    updated_at (TIMESTAMP)       │
└─────────────────────────────────┘
         ↑ 1
         │
         │ N
┌─────────────────────────────────┐
│      user_groups                │
├─────────────────────────────────┤
│ PK user_id (BIGINT)             │ ← Composite PK
│ PK group_id (UUID)              │ ← Composite PK
│    role (ENUM: LEADER, MEMBER)  │
│    deleted_at (TIMESTAMP)       │ ← Soft delete
│    created_at (TIMESTAMP)       │
└─────────────────────────────────┘
   user_id references Identity Service User (no FK constraint)
```

**CRITICAL DESIGN:** No foreign key constraints to Identity Service database. User validation via gRPC.

### Entity: `Group`

**File:** [`Group.java`](../user-group-service/src/main/java/com/example/user_groupservice/entity/Group.java)

**Key Fields:**

| Field        | Type    | Constraints                  | Description                        |
|--------------|---------|------------------------------|------------------------------------|
| `id`         | UUID    | PK, AUTO_GENERATED           | Group ID                           |
| `groupName`  | String  | NOT NULL, max 50 chars       | Group name (e.g., "Team A")        |
| `semester`   | String  | NOT NULL, max 20 chars       | Semester (e.g., "2024-FALL")       |
| `lecturerId` | Long    | NOT NULL                     | References Identity Service User   |
| `deletedAt`  | Instant | NULLABLE                     | Soft delete timestamp              |

**Unique Constraint:**
```java
@UniqueConstraint(name = "uq_group_semester", columnNames = {"group_name", "semester"})
```

**Business Rule:** Group name must be unique within a semester (e.g., "Team A" can exist in both "2024-FALL" and "2025-SPRING").

**Soft Delete Behavior:**

```java
@Entity
@SQLRestriction("deleted_at IS NULL")
public class Group {
    public void softDelete() {
        this.deletedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
```

**Cascade Soft Delete:** When a group is soft-deleted, all its memberships (`user_groups`) are also soft-deleted (handled at service layer, not database cascade).

---

### Entity: `UserGroup`

**File:** [`UserGroup.java`](../user-group-service/src/main/java/com/example/user_groupservice/entity/UserGroup.java)

**Composite Primary Key:** `(userId, groupId)`

**Key Fields:**

| Field      | Type      | Constraints                | Description                       |
|------------|-----------|----------------------------|-----------------------------------|
| `userId`   | Long      | PK, NOT NULL               | References Identity Service User  |
| `groupId`  | UUID      | PK, NOT NULL               | References local Group entity     |
| `role`     | GroupRole | NOT NULL (LEADER, MEMBER)  | Member role in this group         |
| `deletedAt`| Instant   | NULLABLE                   | Soft delete timestamp             |

**Business Rules:**

1. **One LEADER per group:**
   ```java
   // Enforced in service layer (no DB constraint)
   if (role == GroupRole.LEADER) {
       if (userGroupRepository.existsByGroupIdAndRole(groupId, GroupRole.LEADER)) {
           throw ConflictException.leaderAlreadyExists(groupId);
       }
   }
   ```

2. **One group per student per semester:**
   ```java
   // Enforced in service layer using GROUP.semester join
   if (userGroupRepository.existsByUserIdAndSemester(userId, semester)) {
       throw ConflictException.userAlreadyInGroupSameSemester(userId, semester);
   }
   ```

3. **Only STUDENT can be members:**
   ```java
   // Validated via gRPC before adding member
   GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(userId);
   if (roleResponse.getRole() != UserRole.STUDENT) {
       throw BadRequestException.invalidRole(...);
   }
   ```

**Soft Delete Behavior:**

```java
public void softDelete() {
    this.deletedAt = Instant.now();
}
```

**Restore:** Not supported (known limitation).

---

### Enum: `GroupRole`

**Values:**
- `LEADER` - Group leader (one per group)
- `MEMBER` - Regular member

**Used in:** `UserGroup.role`

---

### Enum: `SystemRole`

**Values:**
- `ADMIN` - Full access
- `LECTURER` - Can manage own groups
- `STUDENT` - Can view own groups only

**Used in:** Authorization checks (extracted from JWT claims)

**Mapping:** `SystemRole` ↔ `User.Role` in Identity Service (same values)

---

## REST API Contracts

### Group Management Endpoints

#### 1. Create Group

**Endpoint:** `POST /groups`  
**Authorization:** `ROLE_ADMIN` or `ROLE_LECTURER`  
**Use Case:** UC23

**Request Body:**

```json
{
  "groupName": "Team A",
  "semester": "2024-FALL",
  "lecturerId": 123
}
```

**Validation Rules:**
- `groupName`: Max 50 chars, not blank
- `semester`: Max 20 chars, not blank
- `lecturerId`: Must exist in Identity Service, LECTURER role, ACTIVE status

**Success Response (201 Created):**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "groupName": "Team A",
  "semester": "2024-FALL",
  "lecturerId": 123,
  "lecturerName": "Dr. Jane Smith"
}
```

**Error Responses:**

| Code | Error Code               | Condition                             |
|------|--------------------------|---------------------------------------|
| 409  | GROUP_NAME_DUPLICATE     | Group name already exists in semester |
| 404  | LECTURER_NOT_FOUND       | Lecturer ID not found                 |
| 409  | LECTURER_INACTIVE        | Lecturer account not ACTIVE           |
| 409  | INVALID_ROLE             | User is not LECTURER                  |
| 403  | FORBIDDEN                | Not ADMIN or LECTURER                 |

**gRPC Calls:**
1. `VerifyUserExists(lecturerId)` - Check exists + active
2. `GetUserRole(lecturerId)` - Validate LECTURER role
3. `GetUser(lecturerId)` - Fetch lecturer name for response

---

#### 2. Get Group Details

**Endpoint:** `GET /groups/{groupId}`  
**Authorization:** Authenticated  
**Use Case:** Group detail view

**Success Response (200 OK):**

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "groupName": "Team A",
  "semester": "2024-FALL",
  "lecturer": {
    "id": 123,
    "fullName": "Dr. Jane Smith",
    "email": "jane@example.com"
  },
  "members": [
    {
      "userId": 456,
      "fullName": "John Doe",
      "email": "john@example.com",
      "role": "LEADER"
    },
    {
      "userId": 789,
      "fullName": "Alice Smith",
      "email": "alice@example.com",
      "role": "MEMBER"
    }
  ],
  "memberCount": 2
}
```

**Deleted User Handling:**

```json
{
  "userId": 456,
  "fullName": "<Deleted User>",
  "email": null,
  "role": "LEADER"
}
```

**gRPC Calls:**
1. `GetUsers([456, 789])` - Batch fetch member info (avoid N+1)
2. `GetUser(123)` - Fetch lecturer info

---

#### 3. List Groups

**Endpoint:** `GET /groups?page=0&size=20&semester=2024-FALL&lecturerId=123`  
**Authorization:** Authenticated

**Query Parameters:**

| Param        | Type   | Description                    |
|--------------|--------|--------------------------------|
| `page`       | int    | Page number (0-indexed)        |
| `size`       | int    | Page size (default: 20)        |
| `semester`   | string | Filter by semester (optional)  |
| `lecturerId` | long   | Filter by lecturer (optional)  |

**Success Response (200 OK):**

```json
{
  "content": [
    {
      "id": "a1b2c3d4-...",
      "groupName": "Team A",
      "semester": "2024-FALL",
      "lecturerName": "Dr. Jane Smith",
      "memberCount": 2
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

**gRPC Calls:**
1. `GetUsers([123, 124, ...])` - Batch fetch all lecturer names

---

#### 4. Update Group

**Endpoint:** `PUT /groups/{groupId}`  
**Authorization:** ADMIN or group's lecturer  
**Use Case:** UC23 (update)

**Request Body:**

```json
{
  "groupName": "Team A - Updated",
  "semester": "2024-FALL"
}
```

**Success Response (200 OK):**

```json
{
  "id": "a1b2c3d4-...",
  "groupName": "Team A - Updated",
  "semester": "2024-FALL",
  "lecturerId": 123,
  "lecturerName": "Dr. Jane Smith"
}
```

**Authorization Logic:**
```java
boolean isAdmin = actorRoles.contains("ADMIN");
boolean isGroupLecturer = group.getLecturerId().equals(actorId);

if (!isAdmin && !isGroupLecturer) {
    throw ForbiddenException.insufficientPermission();
}
```

---

#### 5. Update Group Lecturer

**Endpoint:** `PUT /groups/{groupId}/lecturer`  
**Authorization:** ADMIN only

**Request Body:**

```json
{
  "lecturerId": 999
}
```

**Success Response (200 OK):**

```json
{
  "message": "Group lecturer updated successfully",
  "group": { ... }
}
```

**Validation:** New lecturer must exist, LECTURER role, ACTIVE status (via gRPC).

---

#### 6. Delete Group (Soft Delete)

**Endpoint:** `DELETE /groups/{groupId}`  
**Authorization:** ADMIN or group's lecturer

**Success Response (204 No Content):** Empty body

**Side Effects:**
- Sets `groups.deleted_at`
- Soft deletes ALL memberships (`user_groups.deleted_at`)

**Implementation:**

```java
@Transactional
public void deleteGroup(UUID groupId) {
    Group group = findById(groupId);
    
    // Soft delete all memberships first
    List<UserGroup> memberships = userGroupRepository.findAllByGroupId(groupId);
    memberships.forEach(UserGroup::softDelete);
    userGroupRepository.saveAll(memberships);
    
    // Soft delete group
    group.softDelete();
    groupRepository.save(group);
}
```

---

### Group Membership Endpoints

#### 7. Add Member to Group

**Endpoint:** `POST /groups/{groupId}/members`  
**Authorization:** ADMIN or group's lecturer  
**Use Case:** UC24

**Request Body:**

```json
{
  "userId": 456,
  "isLeader": false
}
```

**Validation Rules:**
- User must exist (via gRPC)
- User must be ACTIVE
- User must be STUDENT role (BR-UG-009)
- User not already in this group
- User not already in another group for same semester
- If `isLeader=true`, group must not have existing leader

**Success Response (201 Created):**

```json
{
  "userId": 456,
  "groupId": "a1b2c3d4-...",
  "fullName": "John Doe",
  "email": "john@example.com",
  "role": "MEMBER"
}
```

**Error Responses:**

| Code | Error Code                   | Condition                             |
|------|------------------------------|---------------------------------------|
| 404  | USER_NOT_FOUND               | User ID not found                     |
| 409  | USER_INACTIVE                | User status not ACTIVE                |
| 400  | INVALID_ROLE                 | User is not STUDENT                   |
| 409  | USER_ALREADY_IN_GROUP        | User already in this group            |
| 409  | USER_ALREADY_IN_GROUP_SEMESTER| User in different group, same semester|
| 409  | LEADER_ALREADY_EXISTS        | Group already has a leader            |

**gRPC Calls:**
1. `VerifyUserExists(userId)` - Check exists + active
2. `GetUserRole(userId)` - Validate STUDENT role
3. `GetUser(userId)` - Fetch user info for response

---

#### 8. Assign Role (Promote/Demote)

**Endpoint:** `PUT /groups/{groupId}/members/{userId}/role`  
**Authorization:** ADMIN or group's lecturer  
**Use Case:** UC25

**Request Body:**

```json
{
  "role": "LEADER"
}
```

**Validation:**
- `role`: Must be "LEADER" or "MEMBER"

**Success Response (200 OK):**

```json
{
  "userId": 456,
  "groupId": "a1b2c3d4-...",
  "fullName": "John Doe",
  "email": "john@example.com",
  "role": "LEADER"
}
```

**Business Logic (UC25):**

```java
// If assigning LEADER, demote old leader first
if (newRole == GroupRole.LEADER) {
    Optional<UserGroup> existingLeader = userGroupRepository.findLeaderByGroupIdWithLock(groupId);
    
    if (existingLeader.isPresent() && !existingLeader.get().getUserId().equals(userId)) {
        existingLeader.get().demoteToMember();
        userGroupRepository.save(existingLeader.get());
    }
}

membership.setRole(newRole);
userGroupRepository.save(membership);
```

**Concurrency Control:** Uses pessimistic locking + SERIALIZABLE isolation (see [Transaction & Consistency](#transaction--consistency)).

---

#### 9. Remove Member from Group

**Endpoint:** `DELETE /groups/{groupId}/members/{userId}`  
**Authorization:** ADMIN or group's lecturer

**Success Response (204 No Content):** Empty body

**Business Rule:** Cannot remove LEADER if group has other members.

**Validation:**

```java
if (membership.isLeader()) {
    long memberCount = userGroupRepository.countMembersByGroupId(groupId);
    if (memberCount > 0) {
        throw ConflictException.cannotRemoveLeader();
    }
}
```

**Alternative Flow:** If leader needs to be removed, promote another member first.

---

#### 10. Get Group Members

**Endpoint:** `GET /groups/{groupId}/members?role=LEADER`  
**Authorization:** Authenticated

**Query Parameters:**

| Param | Type      | Description                     |
|-------|-----------|---------------------------------|
| `role`| GroupRole | Filter by LEADER/MEMBER (optional)|

**Success Response (200 OK):**

```json
{
  "groupId": "a1b2c3d4-...",
  "members": [
    {
      "userId": 456,
      "fullName": "John Doe",
      "email": "john@example.com",
      "role": "LEADER"
    }
  ],
  "totalCount": 1
}
```

---

### User Profile Proxy Endpoints

**Design Pattern:** User-Group Service does NOT store user data. These endpoints proxy requests to Identity Service via gRPC.

#### 11. Get User Profile

**Endpoint:** `GET /users/{userId}`  
**Authorization:** Authenticated  
**Use Case:** UC21

**Authorization Rules:**
- ADMIN: can view any user
- LECTURER: can view students only
- STUDENT: can view self only

**Success Response (200 OK):**

```json
{
  "id": 123,
  "email": "user@example.com",
  "fullName": "John Doe",
  "roles": ["STUDENT"],
  "status": "ACTIVE"
}
```

**Implementation:**

```java
public UserResponse getUserById(Long userId, Long actorId, List<String> actorRoles) {
    // Authorization check
    checkGetUserAuthorization(userId, actorId, actorRoles);
    
    // Fetch from Identity Service via gRPC
    GetUserResponse user = identityServiceClient.getUser(userId);
    
    return UserGrpcMapper.toUserResponse(user);
}
```

---

#### 12. Update User Profile

**Endpoint:** `PUT /users/{userId}`  
**Authorization:** ADMIN or SELF (LECTURER explicitly EXCLUDED per spec)  
**Use Case:** UC22

**Request Body:**

```json
{
  "fullName": "John Doe - Updated"
}
```

**Authorization Logic:**

```java
boolean isAdmin = actorRoles.contains("ADMIN");
boolean isLecturer = actorRoles.contains("LECTURER");
boolean isSelf = userId.equals(actorId);

// LECTURER is explicitly EXCLUDED from this API per spec (UC22-AUTH)
if (isLecturer && !isAdmin) {
    throw ForbiddenException.lecturerCannotUpdateProfile();
}

if (!isAdmin && !isSelf) {
    throw ForbiddenException.insufficientPermission();
}
```

**Implementation (Proxy):**

```java
UpdateUserResponse grpcResponse = identityServiceClient.updateUser(userId, request.getFullName());
return UserGrpcMapper.toUserResponse(grpcResponse.getUser());
```

**Why Proxy?** User data is stored in Identity Service only. This endpoint forwards the request via gRPC.

---

#### 13. List Users

**Endpoint:** `GET /users?page=0&size=20&status=ACTIVE&role=STUDENT`  
**Authorization:** ADMIN only

**Success Response (200 OK):**

```json
{
  "content": [
    {
      "id": 123,
      "email": "user@example.com",
      "fullName": "John Doe",
      "roles": ["STUDENT"],
      "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

**Implementation:** Calls `identityServiceClient.listUsers()` gRPC method.

---

#### 14. Get User's Groups

**Endpoint:** `GET /users/{userId}/groups?semester=2024-FALL`  
**Authorization:** Authenticated

**Authorization Rules:** Same as UC21 (ADMIN: all, LECTURER: students, STUDENT: self).

**Success Response (200 OK):**

```json
{
  "userId": 456,
  "fullName": "John Doe",
  "groups": [
    {
      "groupId": "a1b2c3d4-...",
      "groupName": "Team A",
      "semester": "2024-FALL",
      "role": "LEADER",
      "memberCount": 2
    }
  ],
  "totalCount": 1
}
```

**Implementation:**

1. Verify user exists via gRPC
2. Query `user_groups` by `userId` (optionally filter by semester)
3. Join with `groups` to get group details
4. Return combined data

---

## gRPC Integration

### Client: `IdentityServiceClient`

**File:** [`IdentityServiceClient.java`](../user-group-service/src/main/java/com/example/user_groupservice/grpc/IdentityServiceClient.java)

**Configuration:**

```yaml
# application.yml
grpc:
  client:
    identity-service:
      address: 'static://localhost:9090'
      negotiation-type: plaintext
      deadline-seconds: 3
```

**Per-Call Deadline:** 3 seconds (configurable)

---

### gRPC Methods Used

| Method              | Purpose                          | Typical Usage                     |
|---------------------|----------------------------------|-----------------------------------|
| `GetUser`           | Fetch user profile               | Member list, user profile view    |
| `GetUserRole`       | Validate user role               | Group creation, add member        |
| `VerifyUserExists`  | Check exists + active            | Add member, get user groups       |
| `GetUsers`          | Batch fetch (avoid N+1)          | Group detail, group list          |
| `UpdateUser`        | Proxy profile update             | UC22 (update user)                |
| `ListUsers`         | List users with filters          | Admin user listing                |

---

### Error Handling (gRPC)

**Pattern:**

```java
try {
    GetUserResponse user = identityServiceClient.getUser(userId);
    // Process response
} catch (StatusRuntimeException e) {
    log.error("gRPC call failed: {}", e.getStatus());
    
    if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        throw ResourceNotFoundException.userNotFound(userId);
    }
    
    throw new ServiceUnavailableException("Identity Service unavailable");
}
```

**gRPC → HTTP Status Mapping:**

| gRPC Status        | HTTP Status | Error Code                |
|--------------------|-------------|---------------------------|
| `NOT_FOUND`        | 404         | USER_NOT_FOUND            |
| `INVALID_ARGUMENT` | 400         | BAD_REQUEST               |
| `DEADLINE_EXCEEDED`| 504         | GATEWAY_TIMEOUT           |
| `UNAVAILABLE`      | 503         | SERVICE_UNAVAILABLE       |
| `INTERNAL`         | 500         | INTERNAL_SERVER_ERROR     |

---

### Batch Fetching Pattern (Avoid N+1)

**BAD (N+1 gRPC calls):**

```java
for (UserGroup membership : memberships) {
    GetUserResponse user = identityServiceClient.getUser(membership.getUserId());
    // Build response
}
```

**GOOD (1 batch gRPC call):**

```java
List<Long> userIds = memberships.stream()
    .map(UserGroup::getUserId)
    .toList();

GetUsersResponse usersResponse = identityServiceClient.getUsers(userIds);

// Map users to memberships
for (UserGroup membership : memberships) {
    GetUserResponse user = usersResponse.getUsersList().stream()
        .filter(u -> Long.parseLong(u.getUserId()) == membership.getUserId())
        .findFirst()
        .orElse(null);
    // Build response
}
```

**Performance Impact:** Reduces 10 gRPC calls to 1 call for a 10-member group.

---

## Security Design

### Authentication Flow

**CRITICAL DIFFERENCE:** User-Group Service does NOT load user from database.

#### JWT Validation Filter

**File:** [`JwtAuthenticationFilter.java`](../user-group-service/src/main/java/com/example/user_groupservice/security/JwtAuthenticationFilter.java)

**Flow:**

```java
1. Extract "Authorization: Bearer <token>" header
2. Parse JWT and validate signature (same JWT_SECRET as Identity Service)
3. Check expiration
4. Extract userId from "sub" claim
5. Extract roles from "roles" claim (List<String>)
6. Create CurrentUser(userId, roles)
7. Set SecurityContext (NO database query)
8. Continue to controller
```

**CurrentUser:**

```java
public class CurrentUser implements UserDetails {
    private final Long userId;
    private final Collection<? extends GrantedAuthority> authorities;
    
    // No password, no username (JWT-only authentication)
}
```

**Key Points:**
- No user loading from database
- No status check (assumes Identity Service validated status during login)
- Trust JWT claims (signed by Identity Service)

**Trade-off:**
- ✅ Fast (no DB query on every request)
- ❌ User status changes take up to 15 min to propagate (JWT TTL)

---

### Authorization Rules

#### Endpoint Authorization Matrix

| Endpoint                      | Allowed Roles         | Additional Rules                  |
|-------------------------------|-----------------------|-----------------------------------|
| `POST /groups`                | ADMIN, LECTURER       | -                                 |
| `GET /groups/{id}`            | Authenticated         | -                                 |
| `PUT /groups/{id}`            | ADMIN, group lecturer | -                                 |
| `DELETE /groups/{id}`         | ADMIN, group lecturer | -                                 |
| `POST /groups/{id}/members`   | ADMIN, group lecturer | -                                 |
| `PUT /groups/{id}/members/*/role` | ADMIN, group lecturer | -                             |
| `DELETE /groups/{id}/members/*` | ADMIN, group lecturer | Cannot remove leader if has members |
| `GET /users/{id}`             | Authenticated         | ADMIN: all, LECTURER: students, STUDENT: self |
| `PUT /users/{id}`             | ADMIN, SELF           | LECTURER excluded (per spec)      |
| `GET /users`                  | ADMIN                 | -                                 |

#### Authorization Check Pattern

**Example: Update Group**

```java
boolean isAdmin = actorRoles.contains("ADMIN");
boolean isGroupLecturer = group.getLecturerId().equals(actorId);

if (!isAdmin && !isGroupLecturer) {
    throw ForbiddenException.insufficientPermission();
}
```

**Example: Get User Profile**

```java
boolean isAdmin = actorRoles.contains("ADMIN");
boolean isSelf = userId.equals(actorId);

if (isAdmin) {
    // Can view any user
} else if (actorRoles.contains("LECTURER")) {
    // Can view students only
    GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(userId);
    if (roleResponse.getRole() != UserRole.STUDENT) {
        throw ForbiddenException.lecturerCanViewStudentsOnly();
    }
} else if (isSelf) {
    // Can view self
} else {
    throw ForbiddenException.insufficientPermission();
}
```

---

### JWT Configuration

**Shared Secret:** Same `JWT_SECRET` as Identity Service (MUST be identical).

**Validation:**
- Signature verification (HS256)
- Expiration check
- No revocation check (stateless, same limitation as Identity Service)

**JWT Claims Used:**

```json
{
  "sub": "123",              // userId (extracted)
  "roles": ["STUDENT"],      // roles (extracted)
  "email": "...",            // NOT used (fetch via gRPC if needed)
  "token_type": "ACCESS"     // NOT checked
}
```

---

## Transaction & Consistency

### Transactional Boundaries

**Rule:** Service methods are `@Transactional` unless explicitly read-only.

**Examples:**

```java
@Transactional(rollbackFor = Exception.class)
public MemberResponse addMember(UUID groupId, AddMemberRequest request) {
    // All DB operations in same transaction
    // gRPC calls NOT in transaction
}

@Transactional(readOnly = true)
public GroupDetailResponse getGroupById(UUID groupId) {
    // Read-only transaction
}
```

---

### Race Conditions & Locking

#### 1. One Group per Student per Semester

**Scenario:** Two groups add same student concurrently.

**Current Solution:** Application-level check (NOT atomic).

```java
// NOT thread-safe
if (userGroupRepository.existsByUserIdAndSemester(userId, semester)) {
    throw ConflictException.userAlreadyInGroupSameSemester(...);
}

UserGroup membership = UserGroup.builder()...
userGroupRepository.save(membership);
```

**Risk:** Race condition window between check and save.

**Mitigation:** Low probability (requires exact timing). Transaction isolation (READ_COMMITTED) helps.

**Future Fix:** Add unique constraint `(user_id, semester)` via computed column (requires schema change).

---

#### 2. One Leader per Group (UC25)

**Scenario:** Two concurrent requests promote different members to LEADER.

**Solution:** Pessimistic locking + SERIALIZABLE isolation.

**Implementation:**

```java
@Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
public MemberResponse assignRole(UUID groupId, Long userId, AssignRoleRequest request) {
    GroupRole newRole = GroupRole.valueOf(request.getRole());
    
    if (newRole == GroupRole.LEADER) {
        // Use SELECT FOR UPDATE to lock leader row
        Optional<UserGroup> existingLeader = 
            userGroupRepository.findLeaderByGroupIdWithLock(groupId);
        
        if (existingLeader.isPresent() && !existingLeader.get().getUserId().equals(userId)) {
            existingLeader.get().demoteToMember();
            userGroupRepository.save(existingLeader.get());
        }
    }
    
    membership.setRole(newRole);
    userGroupRepository.save(membership);
}
```

**Repository Query:**

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ug FROM UserGroup ug WHERE ug.groupId = :groupId AND ug.role = 'LEADER'")
Optional<UserGroup> findLeaderByGroupIdWithLock(@Param("groupId") UUID groupId);
```

**Why SERIALIZABLE?**
- Prevents phantom reads (new leader inserted while holding lock)
- Ensures atomic leader transition

**Performance Impact:**
- Locks single row (not entire table)
- Pessimistic lock held until transaction commit (milliseconds)
- Acceptable for infrequent operation (promote leader)

---

#### 3. Group Name Uniqueness

**Scenario:** Two groups created with same name + semester concurrently.

**Solution:** Database unique constraint.

```java
@UniqueConstraint(name = "uq_group_semester", columnNames = {"group_name", "semester"})
```

**Outcome:** One transaction succeeds, other gets 409 Conflict.

---

### gRPC + Transaction Boundary

**CRITICAL:** gRPC calls are NOT part of database transaction.

**Example:**

```java
@Transactional
public GroupResponse createGroup(CreateGroupRequest request) {
    // Step 1: Validate lecturer via gRPC (NOT in transaction)
    VerifyUserResponse verification = identityServiceClient.verifyUserExists(request.getLecturerId());
    if (!verification.getExists()) {
        throw ResourceNotFoundException.lecturerNotFound(...);
    }
    
    // Step 2: Save group (IN transaction)
    Group group = groupRepository.save(...);
    
    return response;
}
```

**What Could Go Wrong:**

1. **Lecturer deleted between gRPC call and save:**
   - Risk: Group created with invalid lecturer
   - Mitigation: Referential integrity maintained at application level (gRPC check before operations)
   - Impact: Low (lecturer deletion is rare)

2. **gRPC call fails after DB commit:**
   - Risk: DB state changed but response not sent to client
   - Example: Group created, but `GetUser` call to fetch lecturer name fails
   - Mitigation: Retry logic at client side
   - Impact: Data is consistent, only response incomplete

**Future Enhancement:** Saga pattern for distributed transactions (not implemented).

---

## Error Handling

### Exception Hierarchy

```
RuntimeException
├── BaseException (abstract)
│   ├── ResourceNotFoundException (404)
│   ├── ConflictException (409)
│   ├── BadRequestException (400)
│   ├── ForbiddenException (403)
│   ├── UnauthorizedException (401)
│   ├── ServiceUnavailableException (503)
│   └── GatewayTimeoutException (504)
└── [Other Spring/JPA exceptions]
```

### HTTP Status Code Mapping

| HTTP Code | Error Type               | Example                           |
|-----------|--------------------------|-----------------------------------|
| 400       | Bad Request              | Invalid role, validation error    |
| 401       | Unauthorized             | JWT invalid/expired               |
| 403       | Forbidden                | Insufficient permission           |
| 404       | Not Found                | Group/user not found              |
| 409       | Conflict                 | Duplicate name, leader exists     |
| 503       | Service Unavailable      | Identity Service down             |
| 504       | Gateway Timeout          | gRPC call timeout                 |

### Error Response Format

```json
{
  "errorCode": "GROUP_NAME_DUPLICATE",
  "message": "Group name 'Team A' already exists in semester 2024-FALL",
  "timestamp": "2026-02-02T10:30:00Z"
}
```

### Common Error Codes

| Code                          | HTTP | Description                         |
|-------------------------------|------|-------------------------------------|
| `GROUP_NOT_FOUND`             | 404  | Group ID not found                  |
| `USER_NOT_FOUND`              | 404  | User ID not found (via gRPC)        |
| `MEMBER_NOT_FOUND`            | 404  | User not in this group              |
| `GROUP_NAME_DUPLICATE`        | 409  | Group name exists in semester       |
| `LEADER_ALREADY_EXISTS`       | 409  | Group already has leader            |
| `USER_ALREADY_IN_GROUP`       | 409  | User already in this group          |
| `USER_ALREADY_IN_GROUP_SEMESTER` | 409 | User in different group, same semester |
| `LECTURER_NOT_FOUND`          | 404  | Lecturer ID not found               |
| `LECTURER_INACTIVE`           | 409  | Lecturer account not ACTIVE         |
| `INVALID_ROLE`                | 400  | User is not STUDENT/LECTURER        |
| `CANNOT_REMOVE_LEADER`        | 409  | Leader has members                  |
| `INSUFFICIENT_PERMISSION`     | 403  | Not authorized                      |
| `SERVICE_UNAVAILABLE`         | 503  | Identity Service unavailable        |
| `GATEWAY_TIMEOUT`             | 504  | gRPC call timeout                   |

---

## Important Implementation Notes

### 1. No Foreign Key to Identity Service

**Rule:** User IDs in `groups.lecturer_id` and `user_groups.user_id` are NOT foreign keys.

**Rationale:**
- Identity Service and User-Group Service use separate databases
- Cross-database foreign keys not supported

**Enforcement:** Validate via gRPC before INSERT/UPDATE.

**Risk:** Orphaned records if user deleted in Identity Service.

**Mitigation:** Fetch user data via gRPC on-demand (display "<Deleted User>" if not found).

---

### 2. Soft Delete Cascade (Groups → Memberships)

**Rule:** Deleting a group must soft-delete all memberships.

**Implementation:**

```java
@Transactional
public void deleteGroup(UUID groupId) {
    Group group = groupRepository.findById(groupId)
        .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
    
    // Step 1: Soft delete all memberships
    List<UserGroup> memberships = userGroupRepository.findAllByGroupId(groupId);
    memberships.forEach(UserGroup::softDelete);
    userGroupRepository.saveAll(memberships);
    
    // Step 2: Soft delete group
    group.softDelete();
    groupRepository.save(group);
}
```

**Why NOT Database Cascade:**
- Soft delete is application-level (sets timestamp, not DELETE statement)
- JPA cascade does not support soft delete

**CRITICAL:** Do NOT use `groupRepository.delete(group)` - it bypasses soft delete.

---

### 3. Leader Transition Locking (UC25)

**Rule:** Promoting a new leader MUST demote the old leader atomically.

**Implementation:** See [Transaction & Consistency](#race-conditions--locking) section.

**DO NOT CHANGE:**
- Isolation level (must be SERIALIZABLE)
- Lock type (must be PESSIMISTIC_WRITE)
- Transaction boundary (must include both demote + promote)

**Why:** Prevents two concurrent leader promotions resulting in two leaders.

---

### 4. Batch Fetch via gRPC (N+1 Prevention)

**Rule:** Always batch fetch user data when displaying multiple users.

**Pattern:**

```java
// Extract all user IDs first
List<Long> userIds = memberships.stream()
    .map(UserGroup::getUserId)
    .toList();

// Single gRPC call
GetUsersResponse usersResponse = identityServiceClient.getUsers(userIds);

// Map back to memberships
...
```

**DO NOT:**

```java
for (UserGroup membership : memberships) {
    GetUserResponse user = identityServiceClient.getUser(membership.getUserId());
}
```

**Performance:** O(1) gRPC calls vs O(N) gRPC calls.

---

### 5. Deleted User Handling

**Rule:** Display "<Deleted User>" if user not found via gRPC.

**Implementation:**

```java
GetUserResponse user = usersResponse.getUsersList().stream()
    .filter(u -> Long.parseLong(u.getUserId()) == userId)
    .findFirst()
    .orElse(null);

String fullName = (user != null && !user.getDeleted()) 
    ? user.getFullName() 
    : "<Deleted User>";
    
String email = (user != null && !user.getDeleted()) 
    ? user.getEmail() 
    : null;
```

**Why:** User may be soft-deleted in Identity Service but memberships still exist.

**Behavior:**
- Membership remains in database
- User displayed as "<Deleted User>"
- Email hidden (null)

---

### 6. Authorization Checks Before gRPC Calls

**Rule:** Check authorization BEFORE making expensive gRPC calls.

**GOOD:**

```java
// Step 1: Check authorization (fast, local)
if (!isAdmin && !isGroupLecturer) {
    throw ForbiddenException.insufficientPermission();
}

// Step 2: Fetch data via gRPC (slow, network)
GetUserResponse user = identityServiceClient.getUser(userId);
```

**BAD:**

```java
// Step 1: Fetch data via gRPC (slow)
GetUserResponse user = identityServiceClient.getUser(userId);

// Step 2: Check authorization (fast)
if (!isAdmin && !isGroupLecturer) {
    throw ForbiddenException.insufficientPermission();
}
```

**Why:** Fail fast, reduce unnecessary gRPC calls.

---

### 7. gRPC Timeout Configuration

**Rule:** Always set deadlines on gRPC calls.

**Configuration:**

```yaml
grpc:
  client:
    identity-service:
      deadline-seconds: 3
```

**Implementation:**

```java
return userStub
    .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
    .getUser(request);
```

**Why:** Prevent indefinite blocking if Identity Service is slow/down.

**Behavior:** Throws `DEADLINE_EXCEEDED` status after 3 seconds.

---

## Known Issues & Tech Debt

### 1. No Group Restore

**Problem:** Soft-deleted groups cannot be restored (unlike users in Identity Service).

**Impact:** Accidental group deletion requires manual database fix.

**Future Fix:** Add `POST /groups/{id}/restore` endpoint.

---

### 2. No Audit Logging

**Problem:** User-Group Service does not log security events (only Identity Service has audit logs).

**Impact:** Cannot track who created/deleted groups or changed memberships.

**Future Fix:** Implement audit logging (similar to Identity Service).

---

### 3. Race Condition: One Group per Semester

**Problem:** Application-level check, not atomic (see [Transaction & Consistency](#race-conditions--locking)).

**Risk:** Two concurrent addMember calls might succeed for same user in same semester.

**Future Fix:** Add unique constraint `(user_id, semester)` via computed column.

---

### 4. No gRPC Retry Logic

**Problem:** If gRPC call fails, entire request fails (no retry).

**Impact:** Transient network errors cause 503 errors.

**Future Fix:** Implement retry with exponential backoff.

---

### 5. No gRPC Circuit Breaker

**Problem:** If Identity Service is down, all requests to User-Group Service fail.

**Impact:** Cascading failure (one service down → both services down).

**Future Fix:** Implement circuit breaker (Resilience4j, Hystrix).

---

### 6. No Caching

**Problem:** User data fetched via gRPC on every request (even if unchanged).

**Impact:** High latency, high load on Identity Service.

**Future Fix:** Cache user data in Redis (1-minute TTL).

---

### 7. No Pagination in gRPC GetUsers

**Problem:** `GetUsers` returns all requested users (no limit).

**Risk:** Fetching 1000+ members in large group causes performance issues.

**Future Fix:** Add pagination to gRPC proto.

---

**End of User-Group Service Documentation**
