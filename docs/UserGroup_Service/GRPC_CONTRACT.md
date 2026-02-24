# gRPC Contract - User-Group Service

## 1. Overview

User-Group Service **exposes** and **consumes** gRPC APIs for inter-service communication.

**Exposes:** Group validation and membership checking APIs for other services  
**Consumes:** Identity Service APIs for user validation

**Framework:** `net.devh:grpc-server-spring-boot-starter` + `net.devh:grpc-client-spring-boot-starter`

---

## 2. gRPC Server Configuration

- **Port:** `9095`
- **Protocol:** gRPC (HTTP/2)
- **Framework:** net.devh boot grpc
- **Implementation:** `UserGroupGrpcServiceImpl.java`
- **Proto File:** `src/main/proto/usergroup_service.proto`
- **Java Package:** `com.example.user_groupservice.grpc`

**Configuration (application.yml):**
```yaml
grpc:
  server:
    port: ${GRPC_SERVER_PORT:9095}
```

---

## 3. Exposed RPCs

### 3.1 VerifyGroupExists

**Definition:**
```protobuf
rpc VerifyGroupExists(VerifyGroupRequest) returns (VerifyGroupResponse);
```

**Request:**
```protobuf
message VerifyGroupRequest {
  string group_id = 1; // Group ID (Long as string)
}
```

**Response:**
```protobuf
message VerifyGroupResponse {
  bool exists = 1;     // true if group found in database
  bool deleted = 2;    // true if group has deletedAt != null
  string message = 3;  // Descriptive message
}
```

**Business Purpose:**
- Verify that a group exists and is active (not soft-deleted)
- Used by Project-Config Service before creating project configurations
- Prevents creating configs for non-existent or deleted groups

**Called by:**
- Project-Config Service (when creating/updating project configurations)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `INVALID_ARGUMENT` | Invalid group ID format | Group ID must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

**Note:** This RPC returns `OK` even if group not found. Check `exists` and `deleted` fields in response.

---

### 3.2 CheckGroupLeader

**Definition:**
```protobuf
rpc CheckGroupLeader(CheckGroupLeaderRequest) returns (CheckGroupLeaderResponse);
```

**Request:**
```protobuf
message CheckGroupLeaderRequest {
  string group_id = 1; // Group ID (Long as string)
  string user_id = 2;  // User ID (Long as string)
}
```

**Response:**
```protobuf
message CheckGroupLeaderResponse {
  bool is_leader = 1;  // true if user has LEADER role in this group
  string message = 2;  // Descriptive message
}
```

**Possible messages:**
- "User is the leader" - User is LEADER in this group
- "User is a member but not the leader" - User is MEMBER in this group
- "User is not a member of this group" - User not found in group

**Business Purpose:**
- Authorization check: Verify user is the leader of a group before allowing privileged operations
- Used by Project-Config Service to ensure only group leaders can create/modify project configurations
- Used by Report Service to filter reports by group leadership (future)

**Called by:**
- Project-Config Service (authorization for create/update/delete config)
- Report Service (future - filter by leadership)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `INVALID_ARGUMENT` | Invalid group ID or user ID format | Both IDs must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

**Note:** This RPC returns `OK` even if user is not a member. Check `is_leader` field in response.

---

### 3.3 CheckGroupMember

**Definition:**
```protobuf
rpc CheckGroupMember(CheckGroupMemberRequest) returns (CheckGroupMemberResponse);
```

**Request:**
```protobuf
message CheckGroupMemberRequest {
  string group_id = 1; // Group ID (Long as string)
  string user_id = 2;  // User ID (Long as string)
}
```

**Response:**
```protobuf
message CheckGroupMemberResponse {
  bool is_member = 1;   // true if user is in group (any role)
  string role = 2;      // "LEADER" or "MEMBER" if is_member=true, empty otherwise
  string message = 3;   // Descriptive message
}
```

**Business Purpose:**
- Check if user belongs to a group (any role: LEADER or MEMBER)
- Used for authorization checks where any group member has access
- Distinguish between LEADER and MEMBER roles

**Called by:**
- Project-Config Service (future - validate group member access)
- Report Service (future - filter by group membership)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `INVALID_ARGUMENT` | Invalid group ID or user ID format | Both IDs must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

**Note:** This RPC returns `OK` even if user is not a member. Check `is_member` field in response.

---

### 3.4 GetGroup

**Definition:**
```protobuf
rpc GetGroup(GetGroupRequest) returns (GetGroupResponse);
```

**Request:**
```protobuf
message GetGroupRequest {
  string group_id = 1; // Group ID (Long as string)
}
```

**Response:**
```protobuf
message GetGroupResponse {
  string group_id = 1;
  string group_name = 2;
  string semester = 3;      // Semester code (e.g., "SPRING2025")
  string lecturer_id = 4;   // Lecturer user ID (Long as string)
  string created_at = 5;    // ISO-8601 timestamp
  string updated_at = 6;    // ISO-8601 timestamp
}
```

**Business Purpose:**
- Retrieve full group details for external services
- Used when other services need group metadata (name, semester, lecturer)
- **Currently not actively used** (reserved for future use)

**Called by:**
- Reserved for future use

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `NOT_FOUND` | Group does not exist or is soft-deleted | Group not found in database |
| `INVALID_ARGUMENT` | Invalid group ID format | Group ID must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

---

## 4. Consumed gRPC Services

User-Group Service **calls** Identity Service via gRPC to validate users.

### 4.1 Identity Service gRPC Client

**Service:** Identity Service  
**Target Address:** `identity-service:9091` (Docker) or `localhost:9091` (local dev)  
**Configuration:**
```yaml
grpc:
  client:
    identity-service:
      address: static://${IDENTITY_SERVICE_GRPC_HOST:localhost}:${IDENTITY_SERVICE_GRPC_PORT:9091}
      negotiation-type: plaintext
```

**RPCs Used:**

#### 4.1.1 VerifyUserExists

**Purpose:** Validate that a user exists and is ACTIVE before adding to group or assigning as lecturer  
**Used in:** 
- `createGroup()` - Validate lecturer exists and is ACTIVE
- `addMember()` - Validate student exists and is ACTIVE

**Example:**
```java
VerifyUserResponse response = identityServiceClient.verifyUserExists(lecturerId);
if (!response.getExists() || !response.getActive()) {
    throw new ResourceNotFoundException("Lecturer not found or not active");
}
```

#### 4.1.2 GetUsers (Batch)

**Purpose:** Fetch user details (email, fullName) for group members when listing groups  
**Used in:** 
- `listGroups()` - Batch fetch lecturer details for all groups in page
- `getGroupMembers()` - Fetch member details for display

**Example:**
```java
List<String> lecturerIds = groups.stream()
    .map(Group::getLecturerId)
    .map(String::valueOf)
    .distinct()
    .toList();

GetUsersResponse response = identityServiceClient.getUsers(lecturerIds);
```

#### 4.1.3 UpdateUser (Proxy Pattern)

**Purpose:** Implement UC22 - Update User Profile via proxy  
**Used in:** 
- `UserController.updateUser()` - Proxy REST endpoint to Identity Service gRPC

**Example:**
```java
UpdateUserResponse response = identityServiceClient.updateUser(userId, fullName);
return UserResponse.from(response.getUser());
```

---

## 5. Service Dependencies

### Upstream Dependencies (Services User-Group Calls)

1. **Identity Service** (gRPC port 9091)
   - RPCs used: `VerifyUserExists`, `GetUsers`, `UpdateUser`
   - Purpose: User validation and profile management
   - Critical dependency: User-Group Service **cannot start** without Identity Service

### Downstream Consumers (Services That Call User-Group)

1. **Project-Config Service** (planned)
   - RPCs used: `VerifyGroupExists`, `CheckGroupLeader`
   - Purpose: Validate groups and authorize leaders before config operations
   - Configuration:
     ```yaml
     grpc:
       client:
         user-group-service:
           address: static://user-group-service:9095
     ```

2. **Report Service** (future)
   - RPCs used: `CheckGroupLeader`, `CheckGroupMember`, `GetGroup`
   - Purpose: Filter reports by group membership and leadership

---

## 6. Error Handling Strategy

### Standard gRPC Status Codes

User-Group Service uses standard gRPC status codes:

| gRPC Status | HTTP Equivalent | Use Case |
|-------------|-----------------|----------|
| `OK` | 200 | Successful operation |
| `INVALID_ARGUMENT` | 400 | Invalid request format |
| `NOT_FOUND` | 404 | Group does not exist (only for GetGroup) |
| `INTERNAL` | 500 | Database error or unexpected exception |
| `UNAVAILABLE` | 503 | Service temporarily unavailable |

### Important Notes

- `VerifyGroupExists`, `CheckGroupLeader`, `CheckGroupMember` **always return OK** (even if group/user not found)
- Clients must check boolean fields (`exists`, `is_leader`, `is_member`) in response
- Only `GetGroup` returns `NOT_FOUND` status

### Client-Side Error Handling

Consuming services should implement proper error mapping:

```java
try {
    CheckGroupLeaderResponse response = userGroupServiceClient.checkGroupLeader(groupId, userId);
    if (!response.getIsLeader()) {
        throw new ForbiddenException("User is not the group leader");
    }
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case INVALID_ARGUMENT:
            throw new BadRequestException("Invalid group ID or user ID");
        case UNAVAILABLE:
            throw new ServiceUnavailableException("User-Group Service unavailable");
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException("User-Group Service timeout");
        default:
            throw new RuntimeException("User-Group Service error: " + e.getMessage());
    }
}
```

---

## 7. Performance Considerations

### Timeouts

**Default timeout:** 5 seconds (configured in client)

**Recommendations:**
- `VerifyGroupExists`, `CheckGroupLeader`, `CheckGroupMember`: 2 seconds (single query)
- `GetGroup`: 2 seconds (single query with join)

### Retry Strategy

**Recommended retry policy:**
- Retry on: `UNAVAILABLE`, `DEADLINE_EXCEEDED`
- Max retries: 3
- Backoff: Exponential (100ms, 200ms, 400ms)

**Do NOT retry:**
- `INVALID_ARGUMENT` - Request is malformed
- `INTERNAL` - May indicate data corruption

---

## 8. Security

### Transport Security

- **Development:** Plaintext gRPC (localhost)
- **Production:** TLS encryption recommended
- **Authentication:** Currently none (rely on network isolation)

**Future Enhancement:**
- Add mutual TLS (mTLS) for service-to-service authentication
- Add metadata-based service authentication tokens

### Authorization

- Currently NO authorization checks in gRPC layer
- All services are trusted (internal network isolation)
- REST layer handles user authorization via JWT

---

## 9. Testing

### Sample gRPC Requests

**Test with grpcurl:**

```bash
# 1. VerifyGroupExists
grpcurl -plaintext -d '{"group_id": "1"}' \
  localhost:9095 UserGroupGrpcService/VerifyGroupExists

# 2. CheckGroupLeader
grpcurl -plaintext -d '{"group_id": "1", "user_id": "2"}' \
  localhost:9095 UserGroupGrpcService/CheckGroupLeader

# 3. CheckGroupMember
grpcurl -plaintext -d '{"group_id": "1", "user_id": "3"}' \
  localhost:9095 UserGroupGrpcService/CheckGroupMember

# 4. GetGroup
grpcurl -plaintext -d '{"group_id": "1"}' \
  localhost:9095 UserGroupGrpcService/GetGroup
```

---

## 10. Changelog

| Version | Date | Changes |
|---------|------|---------|
| 1.0 | 2026-02-09 | Initial gRPC service implementation |
| 1.1 | 2026-02-16 | Documentation standardization |

---

## 11. Related Documentation

- **[API Contract](API_CONTRACT.md)** - REST endpoints
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions
- **[Test Cases](TEST_CASES.md)** - Test specifications
- **Proto Files:** 
  - `user-group-service/src/main/proto/usergroup_service.proto` (exposed APIs)
  - `user-group-service/src/main/proto/user_service.proto` (Identity Service client)

---

**Maintained by:** Backend Team  
**Contact:** See project README for team contacts
