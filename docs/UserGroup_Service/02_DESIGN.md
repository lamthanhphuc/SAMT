# Design - User & Group Service

## 1. Database Design

> **Important:** User/Group Service KHÔNG có bảng `users` riêng. Tất cả thông tin user được lấy từ Identity Service qua gRPC.

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

**UC24: Add User to Group (verify student exists)**

```java
public void addUserToGroup(UUID groupId, UUID userId, boolean isLeader) {
    // Verify user exists and is active
    VerifyUserResponse verification = userGrpcStub.verifyUserExists(
        VerifyUserRequest.newBuilder()
            .setUserId(userId.toString())
            .build()
    );
    
    if (!verification.getExists()) {
        throw new NotFoundException("User not found");
    }
    
    if (!verification.getActive()) {
        throw new BadRequestException(
            "Cannot add inactive user to group"
        );
    }
    
    // Verify user has STUDENT role
    GetUserRoleResponse roleResponse = userGrpcStub.getUserRole(
        GetUserRoleRequest.newBuilder()
            .setUserId(userId.toString())
            .build()
    );
    
    if (roleResponse.getRole() != UserRole.STUDENT) {
        throw new BadRequestException(
            "Only students can be added to groups"
        );
    }
    
    // Add to group
    UserGroup userGroup = new UserGroup();
    userGroup.setUserId(userId);
    userGroup.setGroupId(groupId);
    userGroup.setRole(isLeader ? GroupRole.LEADER : GroupRole.MEMBER);
    
    userGroupRepository.save(userGroup);
}
```

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

| gRPC Status | HTTP Status | User/Group Service Action |
|-------------|-------------|---------------------------|
| NOT_FOUND | 404 | Throw NotFoundException("User not found") |
| PERMISSION_DENIED | 403 | Throw ForbiddenException |
| UNAVAILABLE | 503 | Retry with exponential backoff (3 attempts) |
| DEADLINE_EXCEEDED | 504 | Throw ServiceUnavailableException |

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

---

### 6.2 Authorization Rules

- Mọi API phải kiểm tra quyền rõ ràng
- **System role check:** Gọi `getUserRole()` qua gRPC
- **User existence check:** Gọi `verifyUserExists()` qua gRPC trước khi add vào group
- **Lecturer verification:** Gọi `getUserRole()` để verify lecturerId có role LECTURER

---

### 6.3 Group Management Rules

#### Group Status

- Group không có trạng thái ARCHIVED
- Mọi group được coi là active

#### Leader Assignment

- **Assign LEADER mới:**
  - LEADER cũ tự động xuống MEMBER
  - Không cho remove LEADER nếu group còn ≥ 1 MEMBER

- **Concurrency Control:**
  - Sử dụng `@Lock(LockModeType.PESSIMISTIC_WRITE)` trên query `findLeaderByGroupId()`
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
