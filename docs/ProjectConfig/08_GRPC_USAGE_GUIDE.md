# Quick Start: Using gRPC in Project Config Service

This guide shows how to use the newly implemented gRPC integration with User-Group Service.

---

## Auto-Injected Client

The gRPC client is automatically available via Spring dependency injection:

```java
@Service
@RequiredArgsConstructor
public class YourService {
    
    private final UserGroupServiceGrpcClient userGroupClient;
    
    // Use the client methods...
}
```

---

## Available Methods

### 1. Verify Group Exists

**Use Case:** Before creating a project config, verify the group exists and is not deleted.

```java
try {
    VerifyGroupResponse response = userGroupClient.verifyGroupExists(groupId);
    
    if (!response.getExists()) {
        throw new ResourceNotFoundException("GROUP_NOT_FOUND", "Group does not exist");
    }
    
    if (response.getDeleted()) {
        throw new ResourceNotFoundException("GROUP_DELETED", "Group has been deleted");
    }
    
    log.info("Group verified: {}", response.getMessage());
    
} catch (StatusRuntimeException e) {
    handleGrpcError(e);
}
```

**Response Fields:**
- `exists` (boolean) - true if group found in database
- `deleted` (boolean) - true if group has deletedAt != null
- `message` (string) - Descriptive message

---

### 2. Check Group Leader

**Use Case:** Verify user has LEADER role before allowing create/update operations.

```java
try {
    CheckGroupLeaderResponse response = userGroupClient.checkGroupLeader(groupId, userId);
    
    if (!response.getIsLeader()) {
        throw new ForbiddenException("LEADER_REQUIRED", "Only group leader can perform this action");
    }
    
    log.info("User {} is leader of group {}", userId, groupId);
    
} catch (StatusRuntimeException e) {
    handleGrpcError(e);
}
```

**Response Fields:**
- `is_leader` (boolean) - true if user has LEADER role
- `message` (string) - Descriptive message

---

### 3. Check Group Member

**Use Case:** Verify user is a member (any role) before allowing read operations.

```java
try {
    CheckGroupMemberResponse response = userGroupClient.checkGroupMember(groupId, userId);
    
    if (!response.getIsMember()) {
        throw new ForbiddenException("MEMBER_REQUIRED", "You must be a group member to view this");
    }
    
    String role = response.getRole(); // "LEADER" or "MEMBER"
    log.info("User {} is {} of group {}", userId, role, groupId);
    
} catch (StatusRuntimeException e) {
    handleGrpcError(e);
}
```

**Response Fields:**
- `is_member` (boolean) - true if user is in group (any role)
- `role` (string) - "LEADER" or "MEMBER" if is_member=true, empty otherwise
- `message` (string) - Descriptive message

---

### 4. Get Group Details (Optional)

**Use Case:** Fetch full group information (rarely needed, prefer specific checks above).

```java
try {
    GetGroupResponse response = userGroupClient.getGroup(groupId);
    
    String groupName = response.getGroupName();
    String semester = response.getSemester();
    Long lecturerId = Long.parseLong(response.getLecturerId());
    
    log.info("Group: {} (Semester: {}, Lecturer: {})", groupName, semester, lecturerId);
    
} catch (StatusRuntimeException e) {
    if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
        throw new ResourceNotFoundException("GROUP_NOT_FOUND", "Group not found");
    }
    handleGrpcError(e);
}
```

**Response Fields:**
- `group_id` (string) - UUID
- `group_name` (string) - Group name
- `semester` (string) - Semester code
- `lecturer_id` (string) - Lecturer ID (Long as string)
- `created_at` (string) - ISO-8601 timestamp
- `updated_at` (string) - ISO-8601 timestamp

---

## Error Handling

### Standard Error Handler

Create a utility method to map gRPC errors to domain exceptions:

```java
private void handleGrpcError(StatusRuntimeException e) {
    Status.Code code = e.getStatus().getCode();
    String description = e.getStatus().getDescription();
    
    switch (code) {
        case UNAVAILABLE:
            throw new ServiceUnavailableException(
                "USER_GROUP_SERVICE_UNAVAILABLE", 
                "User-Group Service is currently unavailable");
        
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException(
                "USER_GROUP_SERVICE_TIMEOUT", 
                "User-Group Service request timed out");
        
        case NOT_FOUND:
            throw new ResourceNotFoundException(
                "GROUP_NOT_FOUND", 
                "Group not found: " + description);
        
        case PERMISSION_DENIED:
            throw new ForbiddenException(
                "ACCESS_DENIED", 
                "Access denied: " + description);
        
        case INVALID_ARGUMENT:
            throw new BadRequestException(
                "INVALID_REQUEST", 
                "Invalid request: " + description);
        
        default:
            log.error("Unexpected gRPC error: code={}, description={}", code, description);
            throw new InternalServerErrorException(
                "INTERNAL_ERROR", 
                "An unexpected error occurred");
    }
}
```

---

## Complete Example: Create Project Config

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigService {
    
    private final UserGroupServiceGrpcClient userGroupClient;
    private final ProjectConfigRepository configRepository;
    
    @Transactional
    public ConfigResponse createConfig(CreateConfigRequest request, Long userId) {
        UUID groupId = request.getGroupId();
        
        log.info("User {} creating config for group {}", userId, groupId);
        
        // STEP 1: Verify group exists and is not deleted
        try {
            VerifyGroupResponse verifyResponse = userGroupClient.verifyGroupExists(groupId);
            
            if (!verifyResponse.getExists() || verifyResponse.getDeleted()) {
                throw new ResourceNotFoundException("GROUP_NOT_FOUND", 
                    "Group not found or has been deleted");
            }
        } catch (StatusRuntimeException e) {
            handleGrpcError(e);
        }
        
        // STEP 2: Verify user is LEADER of the group
        try {
            CheckGroupLeaderResponse leaderCheck = userGroupClient.checkGroupLeader(groupId, userId);
            
            if (!leaderCheck.getIsLeader()) {
                throw new ForbiddenException("LEADER_REQUIRED", 
                    "Only group leader can create config");
            }
        } catch (StatusRuntimeException e) {
            handleGrpcError(e);
        }
        
        // STEP 3: Check if config already exists (business rule)
        if (configRepository.existsByGroupId(groupId)) {
            throw new ConflictException("CONFIG_ALREADY_EXISTS", 
                "Group already has a configuration");
        }
        
        // STEP 4: Create and save config
        ProjectConfig config = ProjectConfig.builder()
            .groupId(groupId)
            .jiraHostUrl(request.getJiraHostUrl())
            .jiraApiToken(encrypt(request.getJiraApiToken()))
            .githubRepoUrl(request.getGithubRepoUrl())
            .githubToken(encrypt(request.getGithubToken()))
            .state(ConfigState.DRAFT)
            .build();
        
        config = configRepository.save(config);
        
        log.info("Config created successfully: {}", config.getId());
        
        return toResponse(config);
    }
    
    private void handleGrpcError(StatusRuntimeException e) {
        // Implementation from "Error Handling" section above
    }
}
```

---

## Authorization Patterns

### Pattern 1: LEADER-only operation (Create/Update/Delete)

```java
// Only group LEADER can perform this action
CheckGroupLeaderResponse leaderCheck = userGroupClient.checkGroupLeader(groupId, userId);
if (!leaderCheck.getIsLeader()) {
    throw new ForbiddenException("LEADER_REQUIRED", "Only group leader allowed");
}
```

### Pattern 2: MEMBER or LEADER operation (Read)

```java
// Any group member (LEADER or MEMBER) can perform this action
CheckGroupMemberResponse memberCheck = userGroupClient.checkGroupMember(groupId, userId);
if (!memberCheck.getIsMember()) {
    throw new ForbiddenException("MEMBER_REQUIRED", "You must be a group member");
}
```

### Pattern 3: ADMIN bypass

```java
// Skip group checks if user is ADMIN
if (!userRoles.contains("ROLE_ADMIN")) {
    CheckGroupLeaderResponse leaderCheck = userGroupClient.checkGroupLeader(groupId, userId);
    if (!leaderCheck.getIsLeader()) {
        throw new ForbiddenException("LEADER_REQUIRED", "Only group leader or admin allowed");
    }
}
```

---

## Configuration

### Environment Variables

**Local Development (application.yml):**
```yaml
grpc:
  client:
    user-group-service:
      address: static://localhost:9091
      negotiationType: plaintext
      deadline-seconds: 3
```

**Docker/Production (.env):**
```bash
USER_GROUP_SERVICE_GRPC_HOST=user-group-service
USER_GROUP_SERVICE_GRPC_PORT=9091
```

### Timeout Configuration

Default deadline is 3 seconds. To change:

```yaml
grpc:
  client:
    user-group-service:
      deadline-seconds: 5  # Increase timeout
```

---

## Testing Tips

### 1. Unit Tests (Mock gRPC Client)

```java
@ExtendWith(MockitoExtension.class)
class ProjectConfigServiceTest {
    
    @Mock
    private UserGroupServiceGrpcClient userGroupClient;
    
    @InjectMocks
    private ProjectConfigService service;
    
    @Test
    void createConfig_Success() {
        // Given
        UUID groupId = UUID.randomUUID();
        Long userId = 123L;
        
        VerifyGroupResponse verifyResponse = VerifyGroupResponse.newBuilder()
            .setExists(true)
            .setDeleted(false)
            .build();
        
        CheckGroupLeaderResponse leaderResponse = CheckGroupLeaderResponse.newBuilder()
            .setIsLeader(true)
            .build();
        
        when(userGroupClient.verifyGroupExists(groupId)).thenReturn(verifyResponse);
        when(userGroupClient.checkGroupLeader(groupId, userId)).thenReturn(leaderResponse);
        
        // When
        ConfigResponse response = service.createConfig(request, userId);
        
        // Then
        assertNotNull(response);
        verify(userGroupClient).verifyGroupExists(groupId);
        verify(userGroupClient).checkGroupLeader(groupId, userId);
    }
    
    @Test
    void createConfig_NotLeader_ThrowsForbidden() {
        // Given
        CheckGroupLeaderResponse leaderResponse = CheckGroupLeaderResponse.newBuilder()
            .setIsLeader(false)
            .build();
        
        when(userGroupClient.checkGroupLeader(any(), any())).thenReturn(leaderResponse);
        
        // When/Then
        assertThrows(ForbiddenException.class, 
            () -> service.createConfig(request, userId));
    }
}
```

### 2. Integration Tests (Requires Running Services)

Start services:
```bash
# Terminal 1: User-Group Service
cd user-group-service
mvn spring-boot:run

# Terminal 2: Project Config Service
cd project-config-service
mvn spring-boot:run
```

Test via REST API:
```bash
curl -X POST http://localhost:8087/api/project-configs \
  -H "Authorization: Bearer <JWT_TOKEN>" \
  -H "Content-Type: application/json" \
  -d '{
    "groupId": "550e8400-e29b-41d4-a716-446655440000",
    "jiraHostUrl": "https://example.atlassian.net",
    "jiraApiToken": "ATATT3xFfGF0...",
    "githubRepoUrl": "https://github.com/user/repo",
    "githubToken": "ghp_..."
  }'
```

---

## Common Issues

### Issue 1: UNAVAILABLE Error

**Symptom:**
```
StatusRuntimeException: UNAVAILABLE: io exception
```

**Solution:**
- Verify User-Group Service is running
- Check gRPC server port (should be 9091)
- Verify network connectivity

### Issue 2: DEADLINE_EXCEEDED

**Symptom:**
```
StatusRuntimeException: DEADLINE_EXCEEDED: deadline exceeded after 3s
```

**Solution:**
- Increase timeout in configuration
- Check User-Group Service performance
- Verify database connection pool is not exhausted

### Issue 3: NOT_FOUND

**Symptom:**
```
StatusRuntimeException: NOT_FOUND: Group not found or deleted
```

**Solution:**
- This is expected if group doesn't exist
- Handle gracefully by catching and converting to domain exception
- Verify group UUID format is correct

---

## Summary

✅ **Auto-configured:** Client injected via Spring  
✅ **Type-safe:** Generated gRPC stubs with protobuf messages  
✅ **Timeout-protected:** 3-second deadline prevents hanging requests  
✅ **Error-aware:** Maps gRPC Status codes to domain exceptions  
✅ **Production-ready:** Follows existing codebase patterns

**Remember:** Always wrap gRPC calls in try-catch blocks and handle `StatusRuntimeException`!
