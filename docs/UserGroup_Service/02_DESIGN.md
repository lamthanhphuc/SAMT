# Design - User & Group Service

## 1. Database Design

> **Important:** User/Group Service KHÔNG có bảng `users` riêng. Tất cả thông tin user được lấy từ Identity Service qua gRPC.

### 1.0 Cross-Service Data Integrity Strategy

**Decision:** User/Group Service DOES NOT maintain a local `users` table. All user data fetched from Identity Service via gRPC.

**Foreign Key Handling:**

| Column | Type | Constraint | Validation |
|--------|------|------------|------------|
| `groups.lecturer_id` | UUID | NO FK CONSTRAINT | Service-layer gRPC validation |
| `user_groups.user_id` | UUID | NO FK CONSTRAINT | Service-layer gRPC validation |

**Rationale:**
- **Loose coupling:** No database-level dependency on Identity Service schema
- **Service autonomy:** Identity Service can modify schema independently
- **Fault tolerance:** User/Group Service remains available if Identity DB unavailable
- **Trade-off:** Data integrity enforced at service layer, not database layer

**Implementation Requirements:**

1. **MUST call gRPC validation before write operations:**
   ```java
   // Before creating group
   VerifyUserResponse lecturer = identityService.verifyUserExists(lecturerId);
   if (!lecturer.getExists()) throw NotFoundException("Lecturer not found");
   if (lecturer.getRole() != LECTURER) throw BadRequestException("User is not a lecturer");
   ```

2. **MUST handle deleted users gracefully in read operations:**
   ```java
   // When rendering group details
   GetUserResponse lecturer = identityService.getUser(group.getLecturerId());
   if (lecturer.getDeleted()) {
       return GroupResponse.builder()
           .lecturerName("<Deleted User>")
           .lecturerEmail(null)
           .build();
   }
   ```

3. **MUST NOT fail queries when Identity Service returns deleted users:**
   - Groups with deleted lecturer → Display `lecturerName = "<Deleted User>"`
   - User_groups with deleted user → Skip user in member list rendering

**Orphaned Reference Handling:**

| Scenario | Behavior |
|----------|----------|
| User soft-deleted in Identity Service | Group remains valid, lecturer info shows as deleted |
| User hard-deleted in Identity Service | gRPC returns NOT_FOUND, render as `<Deleted User>` |
| Group references non-existent lecturer | gRPC validation prevents creation, existing groups display deleted state |

---

### 1.1 groups

| Column | Type | Constraint |
|--------|------|------------|
| id | UUID | PK |
| group_name | varchar | NOT NULL |
| semester | varchar | NOT NULL |
| lecturer_id | UUID | FK → users.id |
| deleted_at | timestamp | nullable |

**Constraints:**
- UNIQUE (group_name, semester)

**Notes:**
- Soft delete: YES

---

### 1.3 user_groups

| Column | Type | Constraint |
|--------|------|------------|
| user_id | UUID | FK → users.id |
| group_id | UUID | FK → groups.id |
| role | enum | LEADER, MEMBER |
| deleted_at | timestamp | nullable |

**Constraints:**
- PRIMARY KEY (user_id, group_id)
- UNIQUE (group_id) WHERE role = 'LEADER'

**Notes:**
- Soft delete: YES
- Ensures only 1 LEADER per group

**Composite Primary Key JPA Mapping:**

```java
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupId implements Serializable {
    @Column(name = "user_id")
    private UUID userId;
    
    @Column(name = "group_id")
    private UUID groupId;
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UserGroupId)) return false;
        UserGroupId that = (UserGroupId) o;
        return Objects.equals(userId, that.userId) && 
               Objects.equals(groupId, that.groupId);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(userId, groupId);
    }
}

@Entity
@Table(name = "user_groups")
@Data
public class UserGroup {
    @EmbeddedId
    private UserGroupId id;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupRole role;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

---

### 1.4 Database Indexes

**groups table:**
```sql
CREATE INDEX idx_groups_lecturer_id ON groups(lecturer_id);
CREATE INDEX idx_groups_semester ON groups(semester);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);
CREATE INDEX idx_groups_name_semester ON groups(group_name, semester) 
    WHERE deleted_at IS NULL;
```

**user_groups table:**
```sql
CREATE INDEX idx_user_groups_user_id ON user_groups(user_id);
CREATE INDEX idx_user_groups_group_id ON user_groups(group_id);
CREATE INDEX idx_user_groups_role ON user_groups(role) 
    WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX uq_group_leader ON user_groups(group_id) 
    WHERE role = 'LEADER' AND deleted_at IS NULL;
```

**Rationale:**
- `lecturer_id`: Filter groups by lecturer (UC: List Groups)
- `semester`: Filter groups by semester (UC: List Groups)
- `user_id`: Get user's groups (UC: Get User's Groups)
- `role` partial index: Check leader existence efficiently (UC25: Assign Role)
- Composite `(group_name, semester)` with deleted_at filter: Enforce uniqueness check

---

## 2. Integration with Identity Service (gRPC)

### 2.1 Architecture

```
User/Group Service ──gRPC──> Identity Service
                              ├─ GetUser(userId)
                              ├─ GetUserRole(userId)
                              ├─ VerifyUserExists(userId)
                              └─ GetUserStatus(userId)
```

**Pattern:** Database-per-Service + gRPC Communication
- User/Group Service: Quản lý groups, user_groups (group membership)
- Identity Service: Quản lý users, authentication, system roles
- Communication: Synchronous gRPC calls

### 2.2 gRPC Service Contract

**File:** `identity-service/src/main/proto/user_service.proto`

```protobuf
syntax = "proto3";

package com.samt.identity;

option java_multiple_files = true;
option java_package = "com.samt.identity.grpc";

service UserGrpcService {
  // Get basic user info
  rpc GetUser(GetUserRequest) returns (GetUserResponse);
  
  // Get user's system role
  rpc GetUserRole(GetUserRoleRequest) returns (GetUserRoleResponse);
  
  // Verify user exists and is active
  rpc VerifyUserExists(VerifyUserRequest) returns (VerifyUserResponse);
  
  // Batch get users (for group member list)
  rpc GetUsers(GetUsersRequest) returns (GetUsersResponse);
}

message GetUserRequest {
  string user_id = 1; // UUID as string
}

message GetUserResponse {
  string user_id = 1;
  string email = 2;
  string full_name = 3;
  UserStatus status = 4;
  UserRole role = 5;
  bool deleted = 6;
}

message GetUserRoleRequest {
  string user_id = 1;
}

message GetUserRoleResponse {
  UserRole role = 1;
}

message VerifyUserRequest {
  string user_id = 1;
}

message VerifyUserResponse {
  bool exists = 1;
  bool active = 2; // status == ACTIVE
  string message = 3;
}

message GetUsersRequest {
  repeated string user_ids = 1;
}

message GetUsersResponse {
  repeated GetUserResponse users = 1;
}

enum UserStatus {
  ACTIVE = 0;
  INACTIVE = 1;
  LOCKED = 2;
}

enum UserRole {
  ADMIN = 0;
  LECTURER = 1;
  STUDENT = 2;
}
```

### 2.3 Usage in User/Group Service

**UC21: Get User Profile**

```java
@Service
public class UserService {
    
    @Autowired
    private UserGrpcServiceBlockingStub userGrpcStub;
    
    public UserProfileDTO getUserProfile(UUID userId, User currentUser) {
        // Call Identity Service via gRPC
        GetUserResponse user = userGrpcStub.getUser(
            GetUserRequest.newBuilder()
                .setUserId(userId.toString())
                .build()
        );
        
        // Check if user is soft-deleted
        if (user.getDeleted()) {
            throw new NotFoundException("User not found");
        }
        
        // Authorization: LECTURER can only view STUDENT
        if (currentUser.getRole() == UserRole.LECTURER) {
            if (user.getRole() != UserRole.STUDENT) {
                throw new ForbiddenException(
                    "Lecturer can only view student profiles"
                );
            }
        }
        
        // Authorization: STUDENT can only view self
        if (currentUser.getRole() == UserRole.STUDENT) {
            if (!user.getUserId().equals(currentUser.getId().toString())) {
                throw new ForbiddenException(
                    "Students can only view their own profile"
                );
            }
        }
        
        return mapToDTO(user);
    }
}
```

**UC23: Create Group (verify lecturer exists)**

```java
public Group createGroup(CreateGroupRequest request) {
    // Verify lecturer exists and has LECTURER role
    VerifyUserResponse verification = userGrpcStub.verifyUserExists(
        VerifyUserRequest.newBuilder()
            .setUserId(request.getLecturerId().toString())
            .build()
    );
    
    if (!verification.getExists()) {
        throw new NotFoundException("Lecturer not found");
    }
    
    if (!verification.getActive()) {
        throw new BadRequestException("Lecturer account is not active");
    }
    
    // Get lecturer role to verify
    GetUserRoleResponse roleResponse = userGrpcStub.getUserRole(
        GetUserRoleRequest.newBuilder()
            .setUserId(request.getLecturerId().toString())
            .build()
    );
    
    if (roleResponse.getRole() != UserRole.LECTURER) {
        throw new BadRequestException(
            "User is not a lecturer (role: " + roleResponse.getRole() + ")"
        );
    }
    
    // Create group
    Group group = new Group();
    group.setGroupName(request.getGroupName());
    group.setSemester(request.getSemester());
    group.setLecturerId(request.getLecturerId());
    
    return groupRepository.save(group);
}
```

#### Delete Group - Business Rule (IMPLEMENTED)

**Current Implementation:** ✅ **PREVENT DELETE IF HAS MEMBERS**

When ADMIN attempts to delete a group, system validates members first:

```java
@Transactional
public void deleteGroup(UUID groupId) {
    Group group = groupRepository.findById(groupId)
        .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
    
    // Business Rule: Prevent deletion if group has members
    long memberCount = userGroupRepository.countAllMembersByGroupId(groupId);
    if (memberCount > 0) {
        throw new ConflictException("CANNOT_DELETE_GROUP_WITH_MEMBERS",
                "Group has " + memberCount + " members. Remove all members first.");
    }
    
    // Soft delete the group (only if empty)
    group.softDelete();
    groupRepository.save(group);
}
```

**Business Rule:**
- ❌ Delete group PREVENTED if has any members (LEADER or MEMBER)
- ✅ Delete group ALLOWED only if empty (0 members)
- ⚠️ Admin must explicitly remove all members first

**Behavior:**
- Group with 0 members → 200 SUCCESS (soft delete)
- Group with 1+ members → 409 CANNOT_DELETE_GROUP_WITH_MEMBERS

**Error Response:**
```json
{
  "code": "CANNOT_DELETE_GROUP_WITH_MEMBERS",
  "message": "Group has 5 members. Remove all members first.",
  "timestamp": "2026-01-31T10:00:00Z"
}
```

**Rationale:**
- ✅ Prevents accidental data loss
- ✅ Follows "least surprise" principle
- ✅ Requires explicit member removal (intentional workflow)
- ✅ Safer for production use

**Workflow:**
1. Admin wants to delete group
2. System checks member count
3. If has members → 409 CONFLICT
4. Admin removes all members one by one
5. Admin deletes empty group → 200 SUCCESS

**UC24: Add User to Group (verify student exists)**

```java
public void addUserToGroup(UUID groupId, UUID userId, boolean isLeader) {
    // Step 1: Verify user exists and is active
    VerifyUserResponse verification = userGrpcStub.verifyUserExists(
        VerifyUserRequest.newBuilder()
            .setUserId(userId.toString())
            .build()
    );
    
    if (!verification.getExists()) {
        throw new NotFoundException("USER_NOT_FOUND", "User not found");
    }
    
    if (!verification.getActive()) {
        throw new ConflictException("USER_INACTIVE", "Cannot add inactive user to group");
    }
    
    // Step 2: Verify user has STUDENT role (CRITICAL)
    try {
        GetUserRoleResponse roleResponse = userGrpcStub.getUserRole(
            GetUserRoleRequest.newBuilder()
                .setUserId(userId.toString())
                .build()
        );
        
        if (roleResponse.getRole() != UserRole.STUDENT) {
            throw new ConflictException(
                "INVALID_ROLE", 
                "Only students can be added to groups. User role: " + roleResponse.getRole()
            );
        }
        
    } catch (StatusRuntimeException e) {
        log.error("gRPC call failed when verifying user role: {}", e.getStatus());
        
        if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
            throw new ServiceUnavailableException("Identity Service unavailable");
        }
        if (e.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
            throw new GatewayTimeoutException("Identity Service timeout");
        }
        
        throw new RuntimeException("Failed to verify user role: " + e.getStatus().getCode());
    }
    
    // Step 3: Add to group
    UserGroup userGroup = new UserGroup();
    userGroup.setUserId(userId);
    userGroup.setGroupId(groupId);
    userGroup.setRole(isLeader ? GroupRole.LEADER : GroupRole.MEMBER);
    
    userGroupRepository.save(userGroup);
}
```

**Business Rule:** `BR-UG-009: Only STUDENT role can be group members`

**Test Coverage:**
- TC-UG-024: Add non-STUDENT to group → 409 CONFLICT
- TC-UG-025: Add LECTURER to group → 409 CONFLICT
- TC-UG-026: Add ADMIN to group → 409 CONFLICT

### 2.4 gRPC Configuration

**application.yml (User/Group Service)**

```yaml
grpc:
  client:
    identity-service:
      address: static://identity-service:9090
      negotiationType: PLAINTEXT
      # For production: use TLS
      # negotiationType: TLS
      # security:
      #   certChainFile: /path/to/cert.pem
```

**Dependencies (pom.xml)**

```xml
<dependencies>
    <!-- gRPC -->
    <dependency>
        <groupId>net.devh</groupId>
        <artifactId>grpc-spring-boot-starter</artifactId>
        <version>2.15.0.RELEASE</version>
    </dependency>
    
    <!-- Protobuf -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>3.25.1</version>
    </dependency>
</dependencies>
```

### 2.5 Error Handling

**gRPC Status Mapping:**

| gRPC Status | HTTP Status | User/Group Service Action | Exception Class |
|-------------|-------------|---------------------------|------------------|
| OK | 200 | Success | - |
| NOT_FOUND | 404 | Throw NotFoundException("User not found") | NotFoundException |
| PERMISSION_DENIED | 403 | Throw ForbiddenException | ForbiddenException |
| UNAVAILABLE | 503 | Throw ServiceUnavailableException | ServiceUnavailableException |
| DEADLINE_EXCEEDED | 504 | Throw GatewayTimeoutException | GatewayTimeoutException |
| INVALID_ARGUMENT | 400 | Throw BadRequestException | BadRequestException |
| FAILED_PRECONDITION | 409 | Throw ConflictException | ConflictException |
| INTERNAL | 500 | Log + throw RuntimeException | RuntimeException |

**Exception Classes Required:**

```java
@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
public class ServiceUnavailableException extends BaseException {
    public ServiceUnavailableException(String message) {
        super("SERVICE_UNAVAILABLE", message);
    }
}

@ResponseStatus(HttpStatus.GATEWAY_TIMEOUT)
public class GatewayTimeoutException extends BaseException {
    public GatewayTimeoutException(String message) {
        super("GATEWAY_TIMEOUT", message);
    }
}
```

**Retry Configuration:**

```java
@Configuration
public class GrpcConfig {
    
    @Bean
    public UserGrpcServiceBlockingStub userGrpcStub(
            @GrpcClient("identity-service") Channel channel) {
        return UserGrpcServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(5, TimeUnit.SECONDS);
    }
}
```

---

## 3. Authorization Design

### 2.1 Model

**Hybrid RBAC + Group Role**

```
User (from Identity Service via gRPC)
 ├─ System Role (ADMIN / LECTURER / STUDENT)
 └─ Group Role (LEADER / MEMBER) - managed by User/Group Service
```

**Authorization Flow:**

```
1. Request arrives with JWT token
2. JwtFilter extracts userId from token
3. Service calls Identity gRPC: getUserRole(userId)
4. Service checks authorization rules
5. Service proceeds or throws 403 Forbidden
```

### 2.2 Endpoint → Role Mapping

| Endpoint | Required Role | Notes |
|----------|---------------|-------|
| GET /users/{id} | AUTHENTICATED | LECTURER: only STUDENT targets |
| PUT /users/{id} | SELF or ADMIN | LECTURER explicitly excluded |
| GET /users | ADMIN | role filter deferred |
| POST /groups | ADMIN | |
| ADD MEMBER | ADMIN | |
| ASSIGN ROLE | ADMIN | pessimistic lock required |

### 3.3 Special Rules

- ADMIN không được xóa ADMIN khác
- STUDENT không sửa role
- **LECTURER không được update profile qua API này** (phải qua Identity Service)
- **All user data fetched from Identity Service** - không cache locally
- **Authorization checks require gRPC call** để lấy current user role

---

## 4. Soft Delete Behavior

### 4.1 Entity-level Filtering

**Groups và User_Groups** use `@SQLRestriction("deleted_at IS NULL")` which automatically filters soft-deleted records on:
- `findById()`
- `findAll()`
- All JPQL queries on that entity

### 4.2 User Soft Delete Handling

**User soft delete managed by Identity Service:**

```java
// When checking if user exists
VerifyUserResponse response = userGrpcStub.verifyUserExists(
    VerifyUserRequest.newBuilder().setUserId(userId.toString()).build()
);

if (!response.getExists()) {
    // User not found or soft-deleted
    throw new NotFoundException("User not found");
}
```

**GetUserResponse includes `deleted` flag:**

```java
GetUserResponse user = userGrpcStub.getUser(request);

if (user.getDeleted()) {
    throw new NotFoundException("User not found");
}
```

### 4.3 Critical: `existsById()` Does NOT Filter

**BUG RISK:** JPA's `existsById()` method bypasses `@SQLRestriction` and may return `true` for soft-deleted records.

**Correct Pattern:**
```java
// ❌ WRONG - may return true for deleted records
if (!groupRepository.existsById(groupId)) { ... }

// ✅ CORRECT - uses @SQLRestriction filter
if (groupRepository.findById(groupId).isEmpty()) { ... }
```

**Affected Methods:**
- `GroupRepository.existsById()`

**Solution:** Always use `findById().isPresent()` OR custom JPQL query with explicit `deleted_at IS NULL` check.

---

## 5. Performance Considerations

### 4.1 Indexes

Add index hỗ trợ check:
- `user_groups(user_id, group_id)`
- `groups(id, semester)`

### 5.2 Constraint Enforcement

- Constraint enforce ở **SERVICE layer** (not DB)

### 5.3 gRPC Call Optimization

**Batch Operations:**

```java
// ❌ WRONG - N+1 gRPC calls
for (UUID userId : userIds) {
    GetUserResponse user = userGrpcStub.getUser(...);
}

// ✅ CORRECT - 1 batch gRPC call
GetUsersResponse users = userGrpcStub.getUsers(
    GetUsersRequest.newBuilder()
        .addAllUserIds(userIds.stream().map(UUID::toString).toList())
        .build()
);
```

**Caching Strategy (Optional):**

```java
@Cacheable(value = "users", key = "#userId")
public GetUserResponse getUserCached(UUID userId) {
    return userGrpcStub.getUser(...);
}

// Cache TTL: 5 minutes
// Evict on: User update events from Identity Service
```

**Connection Pooling:**
- gRPC channels are reused (managed by grpc-spring-boot-starter)
- Max concurrent streams: 100 (configurable)
- Keepalive: 30 seconds

---

## 6. Business Rules

### 6.1 Core Rules

1. **No Local User Storage:** User data chỉ tồn tại trong Identity Service
2. **gRPC Required:** Mọi operation liên quan user phải gọi Identity Service
3. **Soft Delete Only:** Không hard delete group/user_groups
4. **Single Leader:** 1 group chỉ có 1 leader
5. **No Self-Join:** Student không tự join group
6. **Active Users Only:** Inactive user không được add vào group (verify qua gRPC)
7. **Role Separation:** System role (từ Identity) ≠ Group role (local)
8. **BR-UG-009 - STUDENT Only Members:** Only users with role `STUDENT` can be added to groups (UC24)
9. **BR-UG-010 - LECTURER Authorization Fallback:** LECTURER can view user IF target is STUDENT. If gRPC fails, ALLOW with warning (UC21)
10. **BR-UG-011 - Lecturer Role Verification:** When updating group lecturer, new lecturer MUST have role `LECTURER` (UC27)
8. **BR-UG-009 - STUDENT Only Members:** Only users with role `STUDENT` can be added to groups (UC24)
9. **BR-UG-010 - LECTURER Authorization Fallback:** LECTURER can view user IF target is STUDENT. If gRPC fails, ALLOW with warning (UC21)
10. **BR-UG-011 - Lecturer Role Verification:** When updating group lecturer, new lecturer MUST have role `LECTURER` (UC27)

---

### 6.2 Semester Uniqueness Rule

**Rule:** User MUST belong to at most ONE active group per semester.

**Validation Query:**
```sql
SELECT COUNT(*) FROM user_groups ug
JOIN groups g ON ug.group_id = g.id
WHERE ug.user_id = :userId 
  AND g.semester = :semester
  AND ug.deleted_at IS NULL  -- Only count active memberships
  AND g.deleted_at IS NULL
```

**Rationale:**
- Prevent scheduling conflicts (students cannot attend multiple groups simultaneously)
- Simplify group-based operations (each student has clear primary group per semester)
- Historical data preserved via soft delete (students can change groups mid-semester)

**Implementation:**

**Scenario 1: User joins Group A (Spring2026)**
- Check passes → User added to Group A
- `user_groups` record created with `deleted_at = NULL`

**Scenario 2: Admin removes user from Group A**
- Soft delete membership: `SET deleted_at = NOW()`
- Check query no longer counts this membership

**Scenario 3: Admin adds user to Group B (Spring2026)**
- Check passes (Group A membership soft deleted)
- User successfully added to Group B
- **Result:** User can rejoin different group same semester after removal

---

### 6.3 Group Deletion Cascade Behavior

**Rule:** When group is soft-deleted, ALL active memberships MUST be soft-deleted automatically.

**Implementation:**
```java
@Transactional
public void softDeleteGroup(UUID groupId) {
    // Verify group exists
    Group group = groupRepository.findById(groupId)
        .orElseThrow(() -> new NotFoundException("Group not found"));
    
    // Cascade soft delete all active memberships
    List<UserGroup> memberships = userGroupRepository
        .findByGroupIdAndDeletedAtIsNull(groupId);
    
    memberships.forEach(membership -> {
        membership.setDeletedAt(Instant.now());
        userGroupRepository.save(membership);
    });
    
    // Soft delete group
    group.setDeletedAt(Instant.now());
    groupRepository.save(group);
    
    // Audit log
    auditEventPublisher.publish(AuditEvent.builder()
        .action("SOFT_DELETE_GROUP")
        .resourceId(groupId.toString())
        .metadata(Map.of("memberCount", memberships.size()))
        .build());
}
```

**Rationale:**
- **Data consistency:** Deleted group should not have visible members
- **Referential integrity:** Prevent orphaned active memberships
- **Restore capability:** If group restored (future UC), memberships can be restored atomically

**Query Behavior After Deletion:**
- `GET /api/groups/{groupId}` → `404 NOT_FOUND` (filtered by `@SQLRestriction`)
- `GET /api/groups/{groupId}/members` → `404 NOT_FOUND` (group not found)
- `GET /api/users/{userId}/groups` → Deleted group NOT included in list

---

### 6.4 Authorization Rules

- Mọi API phải kiểm tra quyền rõ ràng
- **System role check:** Gọi `getUserRole()` qua gRPC
- **User existence check:** Gọi `verifyUserExists()` qua gRPC trước khi add vào group
- **Lecturer verification:** Gọi `getUserRole()` để verify lecturerId có role LECTURER

**Lecturer Group Viewing Rights:**

| API Endpoint | ADMIN | LECTURER | STUDENT |
|--------------|-------|----------|---------|
| `GET /api/groups` | All groups | Only groups where `lecturer_id = self` | Only groups where user is member |
| `GET /api/groups/{id}` | Any group | Only if `group.lecturer_id = self` | Only if user is member |
| `GET /api/groups/{id}/members` | Any group | Only if `group.lecturer_id = self` | Only if user is member |

**Implementation:**
```java
@PreAuthorize("isAuthenticated()")
public GroupResponse getGroup(UUID groupId) {
    Group group = findGroupOrThrow(groupId);
    UUID currentUserId = securityUtils.getCurrentUserId();
    String currentRole = securityUtils.getCurrentRole();
    
    if ("LECTURER".equals(currentRole)) {
        if (!group.getLecturerId().equals(currentUserId)) {
            throw new ForbiddenException("Lecturers can only view their own groups");
        }
    }
    
    if ("STUDENT".equals(currentRole)) {
        boolean isMember = userGroupRepository
            .existsByUserIdAndGroupIdAndDeletedAtIsNull(currentUserId, groupId);
        if (!isMember) {
            throw new ForbiddenException("Students can only view groups they belong to");
        }
    }
    
    return mapToResponse(group);
}
```

---

### 6.5 Audit Logging Requirements

**Scope:** User/Group Service MUST emit audit events for UC27 (Update Group Lecturer) only.

**Decision:** Other UCs (UC21-UC26) audit logging will be handled by centralized Audit Service in Phase 2.

**UC27 Audit Event Structure:**

```json
{
  "actorId": "admin-uuid",
  "actorType": "USER",
  "actorEmail": "admin@university.edu",
  "action": "UPDATE_GROUP_LECTURER",
  "outcome": "SUCCESS",
  "resourceType": "Group",
  "resourceId": "group-uuid",
  "timestamp": "2026-01-31T10:30:00Z",
  "serviceName": "UserGroupService",
  "requestId": "trace-uuid",
  "ipAddress": "192.168.1.100",
  "oldValue": {
    "lecturerId": "old-lecturer-uuid",
    "lecturerEmail": "old-lecturer@university.edu"
  },
  "newValue": {
    "lecturerId": "new-lecturer-uuid",
    "lecturerEmail": "new-lecturer@university.edu"
  }
}
```

**Implementation (Phase 1 - Simple Logging):**

```java
@Slf4j
@Service
public class GroupService {
    
    @Transactional
    public GroupResponse updateGroupLecturer(UUID groupId, UUID newLecturerId) {
        Group group = findGroupOrThrow(groupId);
        UUID oldLecturerId = group.getLecturerId();
        
        // Verify new lecturer via gRPC
        verifyLecturerExists(newLecturerId);
        
        // Update
        group.setLecturerId(newLecturerId);
        groupRepository.save(group);
        
        // Audit log (structured JSON logging)
        log.info("ACTION=UPDATE_GROUP_LECTURER groupId={} oldLecturerId={} newLecturerId={} actorId={}", 
            groupId, oldLecturerId, newLecturerId, securityUtils.getCurrentUserId());
        
        return mapToResponse(group);
    }
}
```

**Future (Phase 2 - Event-Driven):**
- Replace structured logging with RabbitMQ/Kafka event publish
- Centralized Audit Service consumes and stores events
- See: [Audit Service Design](../Audit_Service/01_DESIGN.md)

---

### 6.6 Group Management Rules

#### Group Status

- Group không có trạng thái ARCHIVED
- Mọi group được coi là active

#### Leader Assignment

- **Assign LEADER mới:**
  - LEADER cũ tự động xuống MEMBER
  - Không cho remove LEADER nếu group còn ≥ 1 MEMBER

- **Concurrency Control (UC25-LOCK):**
  - **Problem:** 2+ concurrent requests assign LEADER → race condition → multiple leaders
  - **Solution:** Pessimistic write lock + serializable transaction

#### UC25: Concurrency Control - Race Condition Prevention

**Implementation Strategy:**

Two-layer protection against concurrent LEADER assignment:

**Layer 1: Transaction Isolation**
```java
@Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
```
- Highest isolation level (SERIALIZABLE)
- Prevents phantom reads and non-repeatable reads
- Ensures serializable execution of concurrent transactions

**Layer 2: Pessimistic Locking**
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ug FROM UserGroup ug WHERE ug.groupId = :groupId AND ug.role = 'LEADER'")
Optional<UserGroup> findLeaderByGroupIdWithLock(@Param("groupId") UUID groupId);
```
- Database-level row lock (SELECT ... FOR UPDATE)
- Blocks concurrent reads until transaction commits
- Prevents race condition when demoting old leader

**Execution Flow:**

| Step | Request A | Request B |
|------|-----------|-----------||
| 1 | Acquire lock on old leader | Waits for lock |
| 2 | Demote old leader to MEMBER | Still waiting |
| 3 | Assign new user as LEADER | Still waiting |
| 4 | Commit transaction (release lock) | Acquires lock |
| 5 | - | Sees no old leader (already demoted) |
| 6 | - | Assigns user as LEADER → SUCCESS |

**Race Condition Scenario (Without Lock):**
- 2 concurrent requests could both:
  1. Read same old leader
  2. Demote to MEMBER (duplicate operation)
  3. Assign different users as LEADER
  4. Result: 2 LEADERS in same group ❌

**With Lock (Current Implementation):**
- Request B blocks on step 1 until Request A completes
- Only one demote operation executes
- Second request sees correct state
- Result: 1 LEADER in group ✅

**Trade-offs:**
- ✅ Strong consistency guarantee
- ⚠️ Reduced concurrency (requests serialize)
- ⚠️ Potential deadlock if not careful with transaction boundaries

**Alternative Considered:** Application-level synchronized block - rejected due to horizontal scaling concerns.

**Lock Strategy:**

```java
// Repository
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT ug FROM UserGroup ug WHERE ug.groupId = :groupId AND ug.role = 'LEADER'")
Optional<UserGroup> findLeaderByGroupIdWithLock(@Param("groupId") UUID groupId);
```

**Service Transaction:**

```java
@Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
public MemberResponse assignRole(UUID groupId, UUID userId, AssignRoleRequest request) {
    // ... validation ...
    
    if (newRole == GroupRole.LEADER) {
        // Acquire lock on group's leader record
        Optional<UserGroup> oldLeader = userGroupRepository.findLeaderByGroupIdWithLock(groupId);
        
        // Demote old leader if exists and not same user
        if (oldLeader.isPresent() && !oldLeader.get().getUserId().equals(userId)) {
            oldLeader.get().setRole(GroupRole.MEMBER);
            userGroupRepository.saveAndFlush(oldLeader.get());
        }
        
        // Defensive check (should not happen with proper locking)
        if (userGroupRepository.existsByGroupIdAndRole(groupId, GroupRole.LEADER)) {
            throw new ConflictException("LEADER_ALREADY_EXISTS", "Race condition detected");
        }
    }
    
    membership.setRole(newRole);
    userGroupRepository.saveAndFlush(membership);
    
    return buildResponse(membership);
}
```

#### UC26: Business Rule - Remove Leader Validation

**Implementation Detail:**

When removing a user with LEADER role, system checks if group has other active members:

```java
// Query counts MEMBER role only (excludes LEADER being removed)
long memberCount = userGroupRepository.countMembersByGroupId(groupId);
if (memberCount > 0) {
    throw ConflictException.cannotRemoveLeader();
}
```

**Query Specification:**
```sql
-- countMembersByGroupId() implementation
SELECT COUNT(ug) FROM UserGroup ug 
WHERE ug.groupId = :groupId AND ug.role = 'MEMBER'
-- Note: Excludes LEADER role
```

**Business Logic:**
- LEADER can be removed if group has 0 MEMBER (only LEADER exists)
- LEADER cannot be removed if group has 1+ MEMBER
- Method name `countMembersByGroupId()` refers to MEMBER role specifically, not all members

**Test Cases:**
- Remove LEADER from empty group (0 MEMBER) → 204 SUCCESS
- Remove LEADER from group with 1 MEMBER → 409 CANNOT_REMOVE_LEADER
- Remove MEMBER from group → 204 SUCCESS (no validation)

**Recommendation (Optional):** Consider renaming to `countNonLeaderMembersByGroupId()` for clarity.

**Lock Behavior:**

| Scenario | Request A | Request B | Outcome |
|----------|-----------|-----------|----------|
| Concurrent assign | Acquire lock → demote → assign | Wait for lock → see new leader → skip demote | ✅ Only A's leader persists |
| Concurrent to same user | Acquire lock → assign self as leader | Wait → see user is leader → no-op | ✅ Idempotent |
| Lock timeout | Processing | Wait 10s → `PessimisticLockException` | ⚠️ Return 409 CONFLICT |

**Lock Timeout Configuration:**

```java
// application.yml
spring:
  jpa:
    properties:
      jakarta.persistence.lock.timeout: 10000  # 10 seconds
```

**Error Handling:**

```java
@ExceptionHandler(PessimisticLockException.class)
public ResponseEntity<ErrorResponse> handleLockTimeout(PessimisticLockException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(new ErrorResponse("LOCK_TIMEOUT", "Operation timed out due to concurrent access"));
}
```

**Alternative: Application-Level Lock (Not Recommended)**

```java
// Option B: Synchronized block (works only in single-instance deployment)
synchronized (("group_leader_" + groupId).intern()) {
    // Critical section
}
```

**Chosen Approach:** Database pessimistic lock + serializable isolation
- ✅ Works in clustered environment
- ✅ Database-enforced integrity
- ✅ Handles concurrent requests across multiple service instances
  - Đảm bảo chỉ 1 request thành công khi có nhiều request đồng thời assign LEADER

#### Transaction Management

- **Assign Group Role phải chạy trong 1 transaction:**
  - Nếu update LEADER mới fail → rollback toàn bộ
  - **Lock order:** Lock existing leader record TRƯỚC khi update

#### API cần @Transactional

- `assignGroupRole()`
- `addMember(isLeader=true)`

#### Rollback Conditions

| Condition | Action |
|-----------|--------|
| ❌ Không cho remove LEADER nếu group còn ≥ 1 MEMBER | Throw exception và rollback |
| ✔ Chỉ remove khi group còn 1 người | Allow removal |

---

## 7. Package Structure

```
com.samt.usergroup
 ├── controller
 ├── service
 │    └── impl
 ├── repository
 ├── entity
 ├── dto
 │    ├── request
 │    └── response
 ├── grpc
 │    ├── client (gRPC client stubs)
 │    └── mapper (Proto ↔ DTO mappers)
 ├── exception
 └── security
```

### Package Descriptions

| Package | Purpose |
|---------|---------|
| controller | REST API endpoints |
| service | Business logic layer |
| service.impl | Service implementations |
| repository | Data access layer (JPA) |
| entity | JPA entities (Group, UserGroup) |
| dto.request | Request DTOs |
| dto.response | Response DTOs |
| grpc.client | gRPC client beans và configuration |
| grpc.mapper | Mappers giữa Protobuf messages và DTOs |
| exception | Custom exceptions |
| security | Security configurations |

---

## 8. API Standards & Validation

### 8.1 Pagination & Sorting

**Standard Query Parameters:**

| Parameter | Type | Default | Validation | Description |
|-----------|------|---------|------------|-------------|
| `page` | int | 0 | ≥ 0 | Page number (zero-indexed) |
| `size` | int | 20 | 1-100 | Page size |
| `sort` | string | - | See below | Sort specification |

**Sort Format:** `field,direction`
- Example: `sort=groupName,asc`
- Example: `sort=createdAt,desc`
- Multiple sorts: `sort=semester,desc&sort=groupName,asc`

**Supported Sort Fields:**

**List Users (`GET /api/users`):**
- `email` (default)
- `fullName`
- `status`
- `createdAt`

**List Groups (`GET /api/groups`):**
- `groupName` (default)
- `semester`
- `createdAt`

**Validation:**
```java
@GetMapping("/groups")
public Page<GroupResponse> listGroups(
    @RequestParam(defaultValue = "0") @Min(0) Integer page,
    @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
    @RequestParam(required = false) String sort,
    @RequestParam(required = false) String semester,
    @RequestParam(required = false) UUID lecturerId
) {
    // Parse and validate sort parameter
    Sort sortObj = parseSortParameter(sort, 
        List.of("groupName", "semester", "createdAt"));
    
    Pageable pageable = PageRequest.of(page, size, sortObj);
    return groupService.listGroups(pageable, semester, lecturerId);
}
```

---

### 8.2 Filter Parameter Validation

**Rule:** All filter parameters MUST be validated before query execution.

**UUID Filters:**
```java
@GetMapping("/groups")
public Page<GroupResponse> listGroups(
    @RequestParam(required = false) @ValidUUID String lecturerId
) {
    UUID lecturerUUID = lecturerId != null ? UUID.fromString(lecturerId) : null;
    // ...
}
```

**Custom Validator:**
```java
@Constraint(validatedBy = UUIDValidator.class)
@Target({ElementType.PARAMETER, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidUUID {
    String message() default "Invalid UUID format";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

**Semester Filter:**
```java
@Pattern(regexp = "^(Spring|Summer|Fall|Winter)[0-9]{4}$", 
         message = "Invalid semester format")
@RequestParam(required = false) String semester
```

**Error Response for Invalid Filter:**
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Invalid request parameters",
  "timestamp": "2026-01-31T10:00:00Z",
  "errors": [
    {
      "field": "lecturerId",
      "message": "Invalid UUID format",
      "rejectedValue": "not-a-uuid"
    }
  ]
}
```

**Not Found vs Empty Result:**
- Invalid UUID → `400 BAD_REQUEST`
- Valid UUID but lecturer not found → Return empty list (NOT `404 NOT_FOUND`)

---

### 8.3 Multi-Field Validation Error Response

**Standard Format:**

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-31T10:00:00Z",
  "errors": [
    {
      "field": "groupName",
      "message": "Group name must be between 3 and 50 characters",
      "rejectedValue": "SE"
    },
    {
      "field": "semester",
      "message": "Invalid semester format (expected: Spring2026)",
      "rejectedValue": "Q1-2026"
    },
    {
      "field": "lecturerId",
      "message": "Lecturer ID is required",
      "rejectedValue": null
    }
  ]
}
```

**GlobalExceptionHandler Implementation:**

```java
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ErrorResponse> handleValidationException(
        MethodArgumentNotValidException ex) {
    
    List<FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
        .map(error -> FieldError.builder()
            .field(error.getField())
            .message(error.getDefaultMessage())
            .rejectedValue(error.getRejectedValue())
            .build())
        .toList();
    
    ErrorResponse response = ErrorResponse.builder()
        .code("VALIDATION_ERROR")
        .message("Validation failed")
        .timestamp(Instant.now())
        .errors(fieldErrors)
        .build();
    
    return ResponseEntity.badRequest().body(response);
}
```

---

### 8.4 Update Group Semantics

**Endpoint:** `PUT /api/groups/{groupId}`

**Request DTO:**
```java
@Data
public class UpdateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Z]{2,4}[0-9]{2,4}-G[0-9]+$")
    private String groupName;
    
    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerId;
    
    // semester field MUST NOT be present (immutable)
}
```

**Immutability Enforcement:**

**Option 1: @JsonIgnore (Recommended)**
```java
// If client sends "semester" field, Jackson ignores it silently
// No error thrown, semester not updated
```

**Option 2: Explicit Validation**
```java
@PutMapping("/{groupId}")
public ResponseEntity<GroupResponse> updateGroup(
    @PathVariable UUID groupId,
    @Valid @RequestBody Map<String, Object> requestBody
) {
    if (requestBody.containsKey("semester")) {
        throw new BadRequestException("Semester field is immutable and cannot be updated");
    }
    // Map to UpdateGroupRequest...
}
```

**Decision:** Use Option 1 (@JsonIgnore) for backward compatibility and simpler client implementation.

---

## 9. Security Architecture

### 9.1 JWT Validation Requirements

**Required JWT Claims:**

```json
{
  "sub": "user-uuid",              // REQUIRED: User ID
  "email": "user@example.com",     // REQUIRED: User email
  "roles": ["ADMIN"],              // REQUIRED: Array of roles (non-empty)
  "token_type": "ACCESS",          // REQUIRED: Must be "ACCESS"
  "iat": 1738234800,               // REQUIRED: Issued at timestamp
  "exp": 1738235700                // REQUIRED: Expiration timestamp
}
```

**Validation Logic:**

```java
@Component
public class JwtValidator {
    
    public void validateToken(Claims claims) {
        // Check required claims
        if (claims.getSubject() == null) {
            throw new InvalidTokenException("Missing 'sub' claim");
        }
        
        if (claims.get("email") == null) {
            throw new InvalidTokenException("Missing 'email' claim");
        }
        
        @SuppressWarnings("unchecked")
        List<String> roles = (List<String>) claims.get("roles");
        if (roles == null || roles.isEmpty()) {
            throw new InvalidTokenException("Missing or empty 'roles' claim");
        }
        
        String tokenType = (String) claims.get("token_type");
        if (!"ACCESS".equals(tokenType)) {
            throw new InvalidTokenException("Invalid token type: " + tokenType);
        }
        
        // Check expiration
        if (claims.getExpiration().before(new Date())) {
            throw new TokenExpiredException("Token has expired");
        }
    }
}
```

**Error Mapping:**

| Validation Failure | HTTP Status | Response Code |
|-------------------|-------------|---------------|
| Missing required claim | 401 | INVALID_TOKEN |
| Wrong token_type | 401 | INVALID_TOKEN_TYPE |
| Token expired | 401 | TOKEN_EXPIRED |
| Invalid signature | 401 | INVALID_TOKEN_SIGNATURE |

---

### 9.2 Authorization Layers

**Layer 1: Controller (Coarse-Grained)**

```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/groups")
public ResponseEntity<GroupResponse> createGroup(...) { }
```

**Layer 2: Service (Fine-Grained)**

```java
@Service
@RequiredArgsConstructor
public class UserService {
    
    private final SecurityUtils securityUtils;
    private final IdentityServiceClient identityClient;
    
    public UserResponse getUser(UUID userId) {
        UUID currentUserId = securityUtils.getCurrentUserId();
        String currentRole = securityUtils.getCurrentRole();
        
        // STUDENT: Only self
        if ("STUDENT".equals(currentRole) && !userId.equals(currentUserId)) {
            throw ForbiddenException.cannotAccessOtherUser();
        }
        
        // LECTURER: Only STUDENT targets
        if ("LECTURER".equals(currentRole)) {
            GetUserRoleResponse targetRole = identityClient.getUserRole(userId);
            if (targetRole.getRole() != UserRole.STUDENT) {
                throw new ForbiddenException("Lecturers can only view student profiles");
            }
        }
        
        // Proceed with fetching user data...
    }
}
```

**SecurityUtils Helper:**

```java
@Component
public class SecurityUtils {
    
    public UUID getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String userIdStr = (String) auth.getPrincipal();
        return UUID.fromString(userIdStr);
    }
    
    public String getCurrentRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(a -> a.replace("ROLE_", ""))
            .findFirst()
            .orElseThrow(() -> new UnauthorizedException("No role found"));
    }
    
    public boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_" + role));
    }
    
    public String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
```

---

### 9.3 CORS Configuration

```java
@Configuration
public class CorsConfig {
    
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        
        // Allowed origins
        configuration.setAllowedOrigins(Arrays.asList(
            "http://localhost:3000",      // React dev
            "http://localhost:4200",      // Angular dev
            "https://samt.university.edu" // Production
        ));
        
        // Allowed methods
        configuration.setAllowedMethods(Arrays.asList(
            "GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"
        ));
        
        // Allowed headers
        configuration.setAllowedHeaders(Arrays.asList(
            "Authorization", "Content-Type", "X-Request-ID"
        ));
        
        // Allow credentials (cookies, authorization headers)
        configuration.setAllowCredentials(true);
        
        // Max age (preflight cache)
        configuration.setMaxAge(3600L);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
```

---

## 10. gRPC Integration Strategy

### 10.1 Connection Configuration

**application.yml:**

```yaml
grpc:
  client:
    identity-service:
      address: static://identity-service:9090
      negotiationType: PLAINTEXT
      
      # Timeouts
      deadline: 5000  # 5 seconds per call
      
      # Message size limits
      maxInboundMessageSize: 4194304  # 4MB
      maxOutboundMessageSize: 4194304 # 4MB
      
      # Connection pooling
      keepAliveTime: 30s
      keepAliveTimeout: 10s
      keepAliveWithoutCalls: true
      maxConcurrentCallsPerConnection: 100
      
      # Connection management
      idleTimeout: 300s  # 5 minutes
      maxConnectionIdle: 300s
      maxConnectionAge: 1800s  # 30 minutes
      maxConnectionAgeGrace: 30s
```

**Java Configuration:**

```java
@Configuration
public class GrpcClientConfig {
    
    @Value("${grpc.client.identity-service.address}")
    private String identityServiceAddress;
    
    @Bean
    public ManagedChannel identityServiceChannel() {
        String[] addressParts = identityServiceAddress.replace("static://", "").split(":");
        String host = addressParts[0];
        int port = Integer.parseInt(addressParts[1]);
        
        return ManagedChannelBuilder
            .forAddress(host, port)
            .usePlaintext()
            .keepAliveTime(30, TimeUnit.SECONDS)
            .keepAliveTimeout(10, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
            .maxInboundMessageSize(4 * 1024 * 1024)
            .build();
    }
    
    @Bean
    public UserGrpcServiceBlockingStub userGrpcStub(ManagedChannel channel) {
        return UserGrpcServiceGrpc.newBlockingStub(channel)
            .withDeadlineAfter(5, TimeUnit.SECONDS);
    }
    
    @PreDestroy
    public void shutdownChannel() throws InterruptedException {
        identityServiceChannel().shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
}
```

---

### 10.2 Error Handling & Retry

**gRPC Status Mapping:**

| gRPC Status | HTTP Status | User/Group Service Action |
|-------------|-------------|---------------------------|
| `NOT_FOUND` | 404 | Throw `NotFoundException("User not found")` |
| `PERMISSION_DENIED` | 403 | Throw `ForbiddenException` |
| `INVALID_ARGUMENT` | 400 | Throw `BadRequestException` |
| `UNAVAILABLE` | 503 | Retry with exponential backoff (3 attempts) |
| `DEADLINE_EXCEEDED` | 504 | Throw `ServiceUnavailableException` |
| `INTERNAL` | 500 | Throw `InternalServerErrorException` |
| `UNAUTHENTICATED` | 401 | Throw `UnauthorizedException` |

**Error Mapper Implementation:**

```java
@Component
@Slf4j
public class GrpcErrorMapper {
    
    public RuntimeException mapGrpcException(StatusRuntimeException e, String operation) {
        log.error("gRPC call failed: operation={} status={} message={}", 
            operation, e.getStatus().getCode(), e.getMessage());
        
        return switch (e.getStatus().getCode()) {
            case NOT_FOUND -> 
                new ResourceNotFoundException("USER_NOT_FOUND", "User not found");
            case PERMISSION_DENIED -> 
                new ForbiddenException("Access denied from identity service");
            case INVALID_ARGUMENT -> 
                new BadRequestException("Invalid request: " + e.getMessage());
            case UNAVAILABLE -> 
                new ServiceUnavailableException("Identity service temporarily unavailable");
            case DEADLINE_EXCEEDED -> 
                new ServiceUnavailableException("Identity service timeout (5s exceeded)");
            case UNAUTHENTICATED -> 
                new UnauthorizedException("Authentication failed with identity service");
            default -> 
                new InternalServerErrorException(
                    "Identity service error: " + e.getStatus().getCode()
                );
        };
    }
}
```

**Retry Configuration (Resilience4j):**

```yaml
resilience4j:
  retry:
    instances:
      identity-service:
        maxAttempts: 3
        waitDuration: 100ms
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - io.grpc.StatusRuntimeException
        retryOnResultPredicate: io.grpc.Status.Code::UNAVAILABLE
```

```java
@Service
public class IdentityServiceClient {
    
    @Autowired
    private UserGrpcServiceBlockingStub stub;
    
    @Autowired
    private GrpcErrorMapper errorMapper;
    
    @Retry(name = "identity-service", fallbackMethod = "getUserFallback")
    public GetUserResponse getUser(UUID userId) {
        try {
            return stub.getUser(
                GetUserRequest.newBuilder()
                    .setUserId(userId.toString())
                    .build()
            );
        } catch (StatusRuntimeException e) {
            throw errorMapper.mapGrpcException(e, "getUser");
        }
    }
    
    private GetUserResponse getUserFallback(UUID userId, Exception ex) {
        log.error("All retry attempts failed for getUser({})", userId, ex);
        throw new ServiceUnavailableException(
            "Identity service unavailable after 3 retry attempts"
        );
    }
}
```

---

### 10.3 Circuit Breaker

**Configuration:**

```yaml
resilience4j:
  circuitbreaker:
    instances:
      identity-service:
        registerHealthIndicator: true
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50
        waitDurationInOpenState: 10s
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
```

**Usage:**

```java
@CircuitBreaker(name = "identity-service", fallbackMethod = "getUserFallback")
@Retry(name = "identity-service")
public GetUserResponse getUser(UUID userId) {
    // gRPC call
}
```

**Circuit Breaker States:**
- **CLOSED:** Normal operation, calls pass through
- **OPEN:** Failure rate > 50%, all calls fail fast (return fallback)
- **HALF_OPEN:** After 10s wait, test with 3 calls to check if service recovered

**Fallback Response:**

```json
{
  "code": "SERVICE_UNAVAILABLE",
  "message": "Identity service temporarily unavailable (circuit breaker open)",
  "timestamp": "2026-01-31T10:00:00Z",
  "retryAfter": 10
}
```

---

### 10.4 Batch gRPC Call Optimization

**Use Case: Get Group Members with User Details**

**❌ ANTI-PATTERN (N+1 calls):**

```java
public List<MemberResponse> getGroupMembers(UUID groupId) {
    List<UserGroup> memberships = userGroupRepository.findByGroupId(groupId);
    
    return memberships.stream()
        .map(ug -> {
            // N gRPC calls!
            GetUserResponse user = identityClient.getUser(ug.getUserId());
            return MemberResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(ug.getRole().name())
                .build();
        })
        .toList();
}
```

**✅ BEST PRACTICE (1 batch call):**

```java
public List<MemberResponse> getGroupMembers(UUID groupId) {
    List<UserGroup> memberships = userGroupRepository.findByGroupId(groupId);
    
    if (memberships.isEmpty()) {
        return Collections.emptyList();
    }
    
    // Collect all user IDs
    List<String> userIds = memberships.stream()
        .map(ug -> ug.getUserId().toString())
        .toList();
    
    // Single batch gRPC call
    GetUsersResponse usersResponse = identityClient.getUsers(
        GetUsersRequest.newBuilder()
            .addAllUserIds(userIds)
            .build()
    );
    
    // Build lookup map
    Map<UUID, GetUserResponse> userMap = usersResponse.getUsersList().stream()
        .collect(Collectors.toMap(
            u -> UUID.fromString(u.getUserId()),
            u -> u
        ));
    
    // Map to response
    return memberships.stream()
        .map(ug -> {
            GetUserResponse user = userMap.get(ug.getUserId());
            if (user == null || user.getDeleted()) {
                // Skip deleted users
                return null;
            }
            return MemberResponse.builder()
                .userId(user.getUserId())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .role(ug.getRole().name())
                .build();
        })
        .filter(Objects::nonNull)
        .toList();
}
```

**Performance Impact:**
- N+1 calls: `5 users × 50ms = 250ms`
- Batch call: `1 call × 80ms = 80ms` (3x faster)

**APIs Requiring Batch Optimization:**
- `GET /api/groups/{groupId}/members`
- `GET /api/groups` (with member count)
- `GET /api/users/{userId}/groups` (with lecturer info)

---

### 10.5 Proto File Sharing

**Decision:** Use shared Maven artifact for proto files (NOT copy-paste).

**Project Structure:**

```
samt-grpc-proto/
  pom.xml  (packaging: jar)
  src/main/proto/
    user_service.proto
    audit_service.proto
```

**samt-grpc-proto/pom.xml:**

```xml
<project>
    <groupId>com.samt</groupId>
    <artifactId>samt-grpc-proto</artifactId>
    <version>1.0.0</version>
    <packaging>jar</packaging>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.xolstice.maven.plugins</groupId>
                <artifactId>protobuf-maven-plugin</artifactId>
                <version>0.6.1</version>
                <configuration>
                    <protocArtifact>
                        com.google.protobuf:protoc:3.25.1:exe:${os.detected.classifier}
                    </protocArtifact>
                    <pluginId>grpc-java</pluginId>
                    <pluginArtifact>
                        io.grpc:protoc-gen-grpc-java:1.60.0:exe:${os.detected.classifier}
                    </pluginArtifact>
                </configuration>
                <executions>
                    <execution>
                        <goals>
                            <goal>compile</goal>
                            <goal>compile-custom</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**Consumer Services (Identity, UserGroup, etc.):**

```xml
<dependency>
    <groupId>com.samt</groupId>
    <artifactId>samt-grpc-proto</artifactId>
    <version>1.0.0</version>
</dependency>
```

**Versioning Strategy:**
- Proto changes → Bump `samt-grpc-proto` version
- Breaking changes → Major version bump (1.0.0 → 2.0.0)
- Backward-compatible additions → Minor version bump (1.0.0 → 1.1.0)
- All services MUST update to new proto version before deployment

---

## 11. Transaction Management

### 11.1 Transaction Isolation Level

**Decision:** Use default `READ_COMMITTED` isolation level for all transactions.

**Rationale:**
- PostgreSQL default is `READ_COMMITTED`
- Prevents dirty reads (uncommitted data)
- Allows concurrent transactions without excessive locking
- Pessimistic locks sufficient for leader assignment race condition

**Configuration:**

```java
@Transactional(isolation = Isolation.READ_COMMITTED)
public void assignGroupRole(UUID groupId, UUID userId, String role) {
    // Implementation
}
```

**Lock Wait Timeout:**

```yaml
spring:
  jpa:
    properties:
      javax.persistence.lock.timeout: 5000  # 5 seconds
```

**Timeout Handling:**

```java
@ExceptionHandler(PessimisticLockException.class)
public ResponseEntity<ErrorResponse> handleLockTimeout(PessimisticLockException ex) {
    ErrorResponse response = ErrorResponse.builder()
        .code("OPERATION_IN_PROGRESS")
        .message("Another operation is in progress. Please retry in a few seconds.")
        .timestamp(Instant.now())
        .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
}
```

---

### 11.2 Rollback Strategy

**Rule:** All write operations MUST rollback on any exception.

```java
@Transactional(rollbackFor = Exception.class)
public void addMemberToGroup(UUID groupId, UUID userId, boolean isLeader) {
    // All operations in single transaction
    // If any step fails → full rollback
}
```

**Read-Only Transactions:**

```java
@Transactional(readOnly = true)
public GroupResponse getGroup(UUID groupId) {
    // No write operations
    // Better performance (no transaction log overhead)
}
```

---

## 12. Implementation Checklist

**Before Starting Development:**

- [ ] Confirm FK strategy with team (no DB constraints, service-layer validation)
- [ ] Set up `samt-grpc-proto` shared artifact
- [ ] Configure gRPC connection pooling and circuit breaker
- [ ] Implement `SecurityUtils` helper class
- [ ] Define all error response DTOs
- [ ] Set up CORS for frontend domains

**During Development:**

- [ ] Use `findById().isEmpty()` instead of `existsById()` for soft-delete entities
- [ ] Always use batch `getUsers()` for multiple user fetches
- [ ] Implement pessimistic lock for leader assignment
- [ ] Add indexes per section 1.4
- [ ] Validate all filter parameters
- [ ] Log UC27 (Update Group Lecturer) with structured format

**Before Deployment:**

- [ ] Test gRPC retry and circuit breaker under failure scenarios
- [ ] Verify transaction rollback on exceptions
- [ ] Load test batch gRPC calls (100+ users)
- [ ] Confirm JWT validation rejects invalid token_type
- [ ] Test multi-field validation error response format

