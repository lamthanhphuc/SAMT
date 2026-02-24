# gRPC Contract - Project Config Service

## 1. Overview

Project Config Service **consumes** gRPC APIs from other services for validation and authorization purposes.

**Exposes:** None (gRPC server configured but not actively used)  
**Consumes:** User-Group Service gRPC APIs

**Framework:** `net.devh:grpc-client-spring-boot-starter`

---

## 2. gRPC Server Configuration

- **Port:** `9090` (configured but not actively used)
- **Status:** gRPC server dependency exists in pom.xml but **no @GrpcService implementation**
- **Configuration (application-projectconfig.yml):**
  ```yaml
  grpc:
    server:
      port: ${GRPC_SERVER_PORT:9090}
  ```

**Note:** Project Config Service currently does **NOT expose** any gRPC endpoints. Port 9090 is reserved but unused. The service only acts as a gRPC **client** to User-Group Service (port 9095).

---

## 3. Exposed RPCs

**None.**

Project Config Service does not expose any gRPC services at this time. If future requirements necessitate gRPC endpoints (e.g., "GetProjectConfig" for Sync Service), they will be documented here.

---

## 4. Consumed gRPC Services

Project Config Service **consumes** User-Group Service gRPC APIs for group validation and authorization.

### 4.1 User-Group Service gRPC Client

**Service:** User-Group Service  
**Target Address:** `user-group-service:9095` (Docker) or `localhost:9095` (local dev)

**Configuration (application.yml):**
```yaml
grpc:
  client:
    user-group-service:
      address: static://${USER_GROUP_SERVICE_GRPC_HOST:localhost}:${USER_GROUP_SERVICE_GRPC_PORT:9095}
      negotiation-type: plaintext
```

**RPCs Used:**

#### 4.1.1 VerifyGroupExists

**Purpose:** Validate that a group exists and is active before creating or updating project configuration  
**Used in:** 
- `createProjectConfig()` - Verify group exists before creating config
- `updateProjectConfig()` - Verify group still exists before updating config

**Example:**
```java
VerifyGroupResponse response = userGroupClient.verifyGroupExists(groupId);
if (!response.getExists()) {
    throw new ResourceNotFoundException("Group not found");
}
if (response.getDeleted()) {
    throw new ConflictException("Cannot create config for deleted group");
}
```

**Error Handling:**
```java
try {
    VerifyGroupResponse response = userGroupClient.verifyGroupExists(groupId);
    // Check response fields
} catch (StatusRuntimeException e) {
    if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
        throw new ServiceUnavailableException("User-Group Service unavailable");
    }
    throw new RuntimeException("Failed to verify group: " + e.getMessage());
}
```

#### 4.1.2 CheckGroupLeader

**Purpose:** Authorization - Verify that the requesting user is the LEADER of the group before allowing config operations  
**Used in:** 
- `createProjectConfig()` - Only group leader can create config
- `updateProjectConfig()` - Only group leader can update config
- `deleteProjectConfig()` - Only group leader (or ADMIN) can delete config

**Example:**
```java
CheckGroupLeaderResponse response = userGroupClient.checkGroupLeader(groupId, userId);
if (!response.getIsLeader()) {
    throw new ForbiddenException("Only group leader can create project configuration");
}
```

**Error Handling:**
```java
try {
    CheckGroupLeaderResponse response = userGroupClient.checkGroupLeader(groupId, userId);
    if (!response.getIsLeader()) {
        String message = response.getMessage(); // "User is not a member" or "User is a member but not the leader"
        throw new ForbiddenException("Access denied: " + message);
    }
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case UNAVAILABLE:
            throw new ServiceUnavailableException("User-Group Service unavailable");
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException("User-Group Service timeout");
        case INVALID_ARGUMENT:
            throw new BadRequestException("Invalid group ID or user ID format");
        default:
            throw new RuntimeException("Failed to check group leader: " + e.getMessage());
    }
}
```

---

## 5. Service Dependencies

### Upstream Dependencies (Services Project-Config Calls)

1. **User-Group Service** (gRPC port 9095)
   - RPCs used: `VerifyGroupExists`, `CheckGroupLeader`
   - Purpose: Group validation and authorization
   - Critical dependency: Project-Config Service requires User-Group Service for all config operations

### Downstream Consumers (Services That Call Project-Config)

**None via gRPC.**

Project-Config Service is consumed via REST APIs:
- Sync Service (internal REST API for decrypted tokens)
- Frontend (public REST API for CRUD operations)

---

## 6. Error Handling Strategy

### gRPC Error Mapping

Project Config Service maps gRPC errors to HTTP exceptions for consistent API responses:

| gRPC Status | HTTP Status | Error Code | Business Meaning |
|-------------|-------------|------------|------------------|
| `OK` | 200 | - | Group verification succeeded |
| `INVALID_ARGUMENT` | 400 | `BAD_REQUEST` | Invalid group ID or user ID format |
| `NOT_FOUND` | 404 | `GROUP_NOT_FOUND` | Group does not exist (from GetGroup RPC) |
| `UNAVAILABLE` | 503 | `SERVICE_UNAVAILABLE` | User-Group Service is down |
| `DEADLINE_EXCEEDED` | 504 | `GATEWAY_TIMEOUT` | User-Group Service timeout (>5s) |
| `INTERNAL` | 500 | `INTERNAL_ERROR` | Unexpected error in User-Group Service |

### Centralized Error Handler (Recommended)

**Issue identified in ARCHITECTURE_ANALYSIS.md:** Current implementation catches `StatusRuntimeException` and wraps it in generic `RuntimeException`, resulting in HTTP 500 for all gRPC failures.

**Fix Recommendation:** Use centralized `GrpcExceptionHandler` (see User-Group Service ARCHITECTURE_ANALYSIS Issue 7.1).

---

## 7. Performance Considerations

### Timeouts

**Current default:** 5 seconds (configured in gRPC client)

**Recommendations:**
- `VerifyGroupExists`: 2 seconds (single query)
- `CheckGroupLeader`: 2 seconds (single query)

**Configuration:**
```yaml
grpc:
  client:
    user-group-service:
      address: static://user-group-service:9095
      negotiation-type: plaintext
      deadline: 2000  # 2 seconds
```

### Retry Strategy

**Recommended retry policy:**
- Retry on: `UNAVAILABLE`, `DEADLINE_EXCEEDED`
- Max retries: 3
- Backoff: Exponential (100ms, 200ms, 400ms)

**Do NOT retry:**
- `INVALID_ARGUMENT` - Request is malformed (fix client code)
- Success responses where `exists=false` or `is_leader=false` (business logic, not error)

**Implementation (using Resilience4j):**
```java
@Retry(name = "userGroupService", fallbackMethod = "fallbackCheckGroupLeader")
public CheckGroupLeaderResponse checkGroupLeader(Long groupId, Long userId) {
    return userGroupClient.checkGroupLeader(groupId.toString(), userId.toString());
}
```

---

## 8. Security

### Transport Security

- **Development:** Plaintext gRPC (localhost)
- **Production:** TLS encryption recommended
- **Authentication:** Currently none (rely on network isolation)

**Future Enhancement:**
- Add mutual TLS (mTLS) for service-to-service authentication
- Add metadata-based service authentication tokens

### Network Isolation

- User-Group Service gRPC port (9095) should **NOT be exposed** to external networks
- Only accessible within Docker network or VPC
- API Gateway does **NOT** route gRPC traffic

---

## 9. Testing

### Integration Test with User-Group Service

**Test scenario:** Create config with group validation

```java
@Test
void shouldCreateConfigWhenGroupExistsAndUserIsLeader() {
    // Given
    Long groupId = 1L;
    Long userId = 2L;
    
    // Mock gRPC responses
    when(userGroupClient.verifyGroupExists(groupId.toString()))
        .thenReturn(VerifyGroupResponse.newBuilder()
            .setExists(true)
            .setDeleted(false)
            .setMessage("Group exists and is active")
            .build());
    
    when(userGroupClient.checkGroupLeader(groupId.toString(), userId.toString()))
        .thenReturn(CheckGroupLeaderResponse.newBuilder()
            .setIsLeader(true)
            .setMessage("User is the leader")
            .build());
    
    // When
    CreateProjectConfigRequest request = new CreateProjectConfigRequest(
        groupId, "https://jira.example.com", "token123", "ghp_token456"
    );
    ProjectConfigResponse response = projectConfigService.createConfig(request, userId);
    
    // Then
    assertThat(response.getGroupId()).isEqualTo(groupId);
    verify(userGroupClient).verifyGroupExists(groupId.toString());
    verify(userGroupClient).checkGroupLeader(groupId.toString(), userId.toString());
}
```

### Manual Testing with grpcurl

**Test User-Group Service connectivity:**

```bash
# From project-config-service container
grpcurl -plaintext -d '{"group_id": "1"}' \
  user-group-service:9095 UserGroupGrpcService/VerifyGroupExists

grpcurl -plaintext -d '{"group_id": "1", "user_id": "2"}' \
  user-group-service:9095 UserGroupGrpcService/CheckGroupLeader
```

---

## 10. Known Issues

### Issue 1: Generic gRPC Error Handling

**Problem:** gRPC exceptions are caught and wrapped in generic `RuntimeException`, causing all gRPC failures to return HTTP 500.

**Impact:** Clients cannot distinguish between:
- User-Group Service unavailable (should be 503)
- Invalid group ID (should be 400)
- Group not found (should be 404)

**Status:** Documented in User-Group Service ARCHITECTURE_ANALYSIS Issue 7.1. Fix recommendation: Implement centralized `GrpcExceptionHandler`.

---

## 11. Related Documentation

- **[README](README.md)** - Service overview and architecture
- **[API Contract](API_CONTRACT.md)** - REST endpoints
- **[Database Design](DATABASE.md)** - Schema and migrations
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions
- **Proto File:** `user-group-service/src/main/proto/usergroup.proto` (consumed service)

---
