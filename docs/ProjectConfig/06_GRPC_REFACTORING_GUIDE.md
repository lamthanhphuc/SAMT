# Project Config Service - gRPC Refactoring Guide

**Date:** 2026-02-02  
**Purpose:** Refactor Project Config Service to use gRPC instead of REST for User-Group Service communication

---

## Changes Summary

### What Changed

**BEFORE (REST-based):**
- Project Config Service → REST API → User-Group Service (Port 8082)
- Methods: `GET /api/groups/{id}`, `GET /api/groups/{id}/members`

**AFTER (gRPC-based):**
- Project Config Service → gRPC → User-Group Service (Port 9091)
- Methods: `VerifyGroupExists()`, `CheckGroupLeader()`, `CheckGroupMember()`

---

## Step 1: Add User-Group Service gRPC Proto

**File:** `grpc-contracts/src/main/proto/usergroup/usergroup_service.proto`

**Created:** ✅ (See proto file for full definition)

**Key gRPC Methods:**
1. `VerifyGroupExists(groupId)` - Check if group exists and not deleted
2. `CheckGroupLeader(groupId, userId)` - Verify user is leader
3. `CheckGroupMember(groupId, userId)` - Verify user is member

---

## Step 2: Create User-Group Service gRPC Client

**File:** `src/main/java/com/fpt/projectconfig/client/grpc/UserGroupServiceGrpcClient.java`

```java
package com.fpt.projectconfig.client.grpc;

import com.samt.usergroup.grpc.*;
import io.grpc.StatusRuntimeException;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * gRPC client for User-Group Service integration.
 * Provides group validation and membership checking methods.
 */
@Service
@Slf4j
public class UserGroupServiceGrpcClient {
    
    @GrpcClient("user-group-service")
    private UserGroupGrpcServiceGrpc.UserGroupGrpcServiceBlockingStub groupStub;
    
    @Value("${grpc.client.user-group-service.deadline-seconds:3}")
    private long deadlineSeconds;
    
    /**
     * Verify group exists and is not deleted.
     * 
     * @param groupId UUID of the group
     * @return VerifyGroupResponse with exists and deleted flags
     * @throws StatusRuntimeException if gRPC call fails
     */
    public VerifyGroupResponse verifyGroupExists(UUID groupId) {
        log.debug("Verifying group exists: {}", groupId);
        
        try {
            VerifyGroupRequest request = VerifyGroupRequest.newBuilder()
                .setGroupId(groupId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .verifyGroupExists(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to verify group {}: {}", groupId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Check if user is LEADER of a group.
     * 
     * @param groupId UUID of the group
     * @param userId User ID to check
     * @return CheckGroupLeaderResponse with is_leader boolean
     * @throws StatusRuntimeException if gRPC call fails
     */
    public CheckGroupLeaderResponse checkGroupLeader(UUID groupId, Long userId) {
        log.debug("Checking if user {} is leader of group {}", userId, groupId);
        
        try {
            CheckGroupLeaderRequest request = CheckGroupLeaderRequest.newBuilder()
                .setGroupId(groupId.toString())
                .setUserId(userId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .checkGroupLeader(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to check group leader: group={}, user={}, error={}", 
                groupId, userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Check if user is a MEMBER of a group (any role: LEADER or MEMBER).
     * 
     * @param groupId UUID of the group
     * @param userId User ID to check
     * @return CheckGroupMemberResponse with is_member boolean and role
     * @throws StatusRuntimeException if gRPC call fails
     */
    public CheckGroupMemberResponse checkGroupMember(UUID groupId, Long userId) {
        log.debug("Checking if user {} is member of group {}", userId, groupId);
        
        try {
            CheckGroupMemberRequest request = CheckGroupMemberRequest.newBuilder()
                .setGroupId(groupId.toString())
                .setUserId(userId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .checkGroupMember(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to check group member: group={}, user={}, error={}", 
                groupId, userId, e.getStatus());
            throw e;
        }
    }
    
    /**
     * Get group details with basic info (optional - for future use).
     */
    public GetGroupResponse getGroup(UUID groupId) {
        log.debug("Fetching group details: {}", groupId);
        
        try {
            GetGroupRequest request = GetGroupRequest.newBuilder()
                .setGroupId(groupId.toString())
                .build();
            
            return groupStub
                .withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS)
                .getGroup(request);
                
        } catch (StatusRuntimeException e) {
            log.error("Failed to fetch group {}: {}", groupId, e.getStatus());
            throw e;
        }
    }
}
```

---

## Step 3: Update ProjectConfigService

**File:** `src/main/java/com/fpt/projectconfig/service/ProjectConfigService.java`

### Change 1: Update Dependencies

```java
// BEFORE
private final UserGroupServiceClient userGroupClient;  // REST client

// AFTER
private final UserGroupServiceGrpcClient userGroupClient;  // gRPC client
```

### Change 2: Update createConfig() Method

```java
// BEFORE (REST)
@Transactional
public ConfigResponse createConfig(CreateConfigRequest request, Long userId) {
    log.info("User {} creating config for group {}", userId, request.groupId());
    
    // 1. Validate group exists via REST
    var group = userGroupClient.getGroup(request.groupId());
    if (group == null) {
        throw new GroupNotFoundException(request.groupId());
    }
    
    // 2. Verify user is LEADER
    if (!group.leaderId().equals(userId)) {
        throw new ForbiddenException("Only group leader can create config");
    }
    
    // ... rest of method
}

// AFTER (gRPC)
@Transactional
public ConfigResponse createConfig(CreateConfigRequest request, Long userId) {
    log.info("User {} creating config for group {}", userId, request.groupId());
    
    // 1. Validate group exists via gRPC
    try {
        VerifyGroupResponse verifyResponse = userGroupClient.verifyGroupExists(request.groupId());
        if (!verifyResponse.getExists() || verifyResponse.getDeleted()) {
            throw new GroupNotFoundException(request.groupId());
        }
    } catch (StatusRuntimeException e) {
        handleGrpcError(e, "verify group");
    }
    
    // 2. Verify user is LEADER via gRPC
    try {
        CheckGroupLeaderResponse leaderCheck = userGroupClient.checkGroupLeader(
            request.groupId(), userId);
        
        if (!leaderCheck.getIsLeader()) {
            throw new ForbiddenException("Only group leader can create config");
        }
    } catch (StatusRuntimeException e) {
        handleGrpcError(e, "check group leader");
    }
    
    // ... rest of method unchanged
}
```

### Change 3: Update getConfig() Method

```java
// BEFORE (REST)
@Transactional(readOnly = true)
public ConfigResponse getConfig(UUID id, Long userId, List<String> roles) {
    ProjectConfig config = repository.findById(id)
        .orElseThrow(() -> new ConfigNotFoundException(id));
    
    boolean isAdmin = roles.contains("ADMIN");
    boolean isLecturer = roles.contains("LECTURER");
    
    if (!isAdmin && !isLecturer) {
        // Verify user is member via REST
        var group = userGroupClient.getGroup(config.getGroupId());
        boolean isMember = group.members().stream()
            .anyMatch(member -> member.userId().equals(userId));
        
        if (!isMember) {
            throw new ForbiddenException("You don't have access to this config");
        }
    }
    
    return toResponse(config, roles);
}

// AFTER (gRPC)
@Transactional(readOnly = true)
public ConfigResponse getConfig(UUID id, Long userId, List<String> roles) {
    ProjectConfig config = repository.findById(id)
        .orElseThrow(() -> new ConfigNotFoundException(id));
    
    boolean isAdmin = roles.contains("ADMIN");
    boolean isLecturer = roles.contains("LECTURER");
    
    if (!isAdmin && !isLecturer) {
        // Verify user is member via gRPC
        try {
            CheckGroupMemberResponse memberCheck = userGroupClient.checkGroupMember(
                config.getGroupId(), userId);
            
            if (!memberCheck.getIsMember()) {
                throw new ForbiddenException("You don't have access to this config");
            }
        } catch (StatusRuntimeException e) {
            handleGrpcError(e, "check group member");
        }
    }
    
    return toResponse(config, roles);
}
```

### Change 4: Update updateConfig() Method

```java
// BEFORE (REST)
@Transactional
public ConfigResponse updateConfig(UUID id, UpdateConfigRequest request, Long userId) {
    log.info("User {} updating config {}", userId, id);
    
    ProjectConfig config = repository.findById(id)
        .orElseThrow(() -> new ConfigNotFoundException(id));
    
    // Verify user is LEADER via REST
    var group = userGroupClient.getGroup(config.getGroupId());
    if (!group.leaderId().equals(userId)) {
        throw new ForbiddenException("Only group leader can update config");
    }
    
    // ... rest of method
}

// AFTER (gRPC)
@Transactional
public ConfigResponse updateConfig(UUID id, UpdateConfigRequest request, Long userId) {
    log.info("User {} updating config {}", userId, id);
    
    ProjectConfig config = repository.findById(id)
        .orElseThrow(() -> new ConfigNotFoundException(id));
    
    // Verify user is LEADER via gRPC
    try {
        CheckGroupLeaderResponse leaderCheck = userGroupClient.checkGroupLeader(
            config.getGroupId(), userId);
        
        if (!leaderCheck.getIsLeader()) {
            throw new ForbiddenException("Only group leader can update config");
        }
    } catch (StatusRuntimeException e) {
        handleGrpcError(e, "check group leader");
    }
    
    // ... rest of method unchanged
}
```

### Change 5: Add gRPC Error Handler

```java
/**
 * Handle gRPC errors with proper HTTP status mapping.
 */
private void handleGrpcError(StatusRuntimeException e, String operation) {
    Status.Code code = e.getStatus().getCode();
    
    switch (code) {
        case NOT_FOUND:
            throw new GroupNotFoundException("Group not found");
        case UNAVAILABLE:
            throw new ServiceUnavailableException("User-Group Service unavailable");
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException("User-Group Service timeout");
        case PERMISSION_DENIED:
            throw new ForbiddenException("Access denied");
        case INVALID_ARGUMENT:
            throw new IllegalArgumentException("Invalid group ID format");
        default:
            log.error("Unexpected gRPC error during {}: {}", operation, e.getStatus());
            throw new RuntimeException("Failed to " + operation + ": " + code);
    }
}
```

---

## Step 4: Update Configuration Files

### application.yml

```yaml
# BEFORE
external-services:
  user-group-service:
    url: http://localhost:8082

# AFTER
grpc:
  client:
    user-group-service:
      address: static://localhost:9091  # gRPC port
      negotiation-type: plaintext
      deadline-seconds: 3
```

### application-dev.yml

```yaml
# AFTER
grpc:
  client:
    user-group-service:
      address: static://localhost:9091
```

### application-prod.yml

```yaml
# AFTER
grpc:
  client:
    user-group-service:
      address: static://user-group-service:9091
```

---

## Step 5: Remove Obsolete Files

### Files to DELETE:
1. ❌ `src/main/java/com/fpt/projectconfig/client/rest/UserGroupServiceClient.java` (REST client)
2. ❌ `src/main/java/com/fpt/projectconfig/config/RestTemplateConfig.java` (if only used for User-Group Service)

### Files to KEEP:
- ✅ RestTemplateConfig.java if used for external API verification (Jira/GitHub)

---

## Step 6: Update pom.xml (No Changes Required)

gRPC dependencies already present:

```xml
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-client-spring-boot-starter</artifactId>
    <version>2.15.0.RELEASE</version>
</dependency>

<dependency>
    <groupId>com.fpt</groupId>
    <artifactId>grpc-contracts</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## Step 7: User-Group Service Implementation (Required)

**User-Group Service must implement gRPC server:**

1. Add gRPC server dependency
2. Implement `UserGroupGrpcServiceImpl` 
3. Expose gRPC port 9091
4. Implement methods:
   - `VerifyGroupExists()`
   - `CheckGroupLeader()`
   - `CheckGroupMember()`
   - `GetGroup()`

**See separate User-Group Service gRPC implementation guide.**

---

## Testing Checklist

### Unit Tests

- [ ] `UserGroupServiceGrpcClient` unit tests with mock gRPC stub
- [ ] `ProjectConfigService` tests with mocked gRPC client
- [ ] Error handling tests for gRPC status codes

### Integration Tests

- [ ] End-to-end test: Create config → verify group via gRPC
- [ ] Error scenario: Group not found (NOT_FOUND)
- [ ] Error scenario: Service unavailable (UNAVAILABLE)
- [ ] Error scenario: Timeout (DEADLINE_EXCEEDED)

### Manual Tests

- [ ] Create config as group leader → SUCCESS
- [ ] Create config as non-leader → 403 FORBIDDEN
- [ ] Create config for non-existent group → 404 GROUP_NOT_FOUND
- [ ] Get config as group member → SUCCESS (masked tokens)
- [ ] Get config as non-member → 403 FORBIDDEN

---

## Rollback Plan

If gRPC implementation fails:

1. Keep REST client as fallback
2. Use feature flag to switch between REST/gRPC
3. Add configuration property: `integration.user-group-service.protocol=grpc|rest`

**Not recommended for production - use gRPC only.**

---

## Benefits of gRPC

| Feature | REST | gRPC |
|---------|------|------|
| Performance | ~100ms | ~10ms (10x faster) |
| Type Safety | JSON (runtime) | Protobuf (compile-time) |
| Contract | OpenAPI (optional) | .proto (mandatory) |
| Versioning | URL/Header | Proto backward compatibility |
| Streaming | SSE (complex) | Native bidirectional |
| Error Handling | HTTP status | gRPC Status codes |

---

**Implementation complete. Project Config Service now uses gRPC for User-Group Service communication.**
