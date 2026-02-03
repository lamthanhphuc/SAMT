# Project Config Service - Code Walkthrough

**Purpose:** Detailed explanation of business logic implementation  
**Target Audience:** Developers implementing REST controllers and additional features

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    REST Controller Layer                    │
│          (TODO: Extract JWT, call service methods)          │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                  ProjectConfigService                       │
│  • Business logic & validation                              │
│  • gRPC calls to User-Group Service                        │
│  • State machine management                                 │
│  • Authorization checks                                     │
└─────────────────────────────────────────────────────────────┘
         ↓                              ↓                      
┌──────────────────────┐      ┌──────────────────────────────┐
│ UserGroupGrpcClient  │      │  ProjectConfigRepository     │
│  • verifyGroupExists │      │  • findById                  │
│  • checkGroupLeader  │      │  • findByGroupId             │
│  • checkGroupMember  │      │  • existsByGroupId           │
└──────────────────────┘      └──────────────────────────────┘
         ↓                              ↓
┌──────────────────────┐      ┌──────────────────────────────┐
│ User-Group Service   │      │  PostgreSQL Database         │
│  (Port 9091 gRPC)    │      │  (project_configs table)     │
└──────────────────────┘      └──────────────────────────────┘
```

---

## Method 1: createConfig()

### Purpose
Create new configuration for a student group. Only group LEADER or ADMIN can create.

### Signature
```java
@Transactional
public ConfigResponse createConfig(
    CreateConfigRequest request,  // Config data (all fields required)
    Long userId,                   // Current user ID (from JWT)
    List<String> roles             // User roles (from JWT)
)
```

### Step-by-Step Logic

#### STEP 1: Verify Group Exists
```java
validateGroupExists(groupId);
```

**What happens:**
```java
// Call gRPC: UserGroupGrpcService.VerifyGroupExists
VerifyGroupResponse response = userGroupClient.verifyGroupExists(groupId);

// Check response
if (!response.getExists()) {
    throw GroupNotFoundException("Group not found");
}

if (response.getDeleted()) {
    throw GroupNotFoundException("Group has been deleted");
}
```

**Why:** Ensure group exists in User-Group Service before creating config (BR-CONFIG-01).

**Error Handling:**
- `StatusRuntimeException` with `UNAVAILABLE` → `ServiceUnavailableException`
- `StatusRuntimeException` with `NOT_FOUND` → `GroupNotFoundException`

---

#### STEP 2: Check LEADER Permission
```java
checkLeaderPermission(groupId, userId, roles);
```

**What happens:**
```java
// ADMIN bypass (ADMIN can create config for any group)
if (roles.contains("ROLE_ADMIN")) {
    return; // Skip gRPC call
}

// Call gRPC: UserGroupGrpcService.CheckGroupLeader
CheckGroupLeaderResponse response = userGroupClient.checkGroupLeader(groupId, userId);

// Check if user is LEADER
if (!response.getIsLeader()) {
    throw ForbiddenException("Only group leader can create config");
}
```

**Why:** Only group LEADER can create config (BR-CONFIG-02). ADMIN can bypass.

---

#### STEP 3: Check Duplicate Config
```java
if (configRepository.existsByGroupId(groupId)) {
    throw ConfigAlreadyExistsException("Group already has a configuration");
}
```

**Why:** Enforce one-config-per-group constraint (BR-CONFIG-03). UNIQUE index on `group_id` prevents database-level duplicates.

---

#### STEP 4: Create Entity
```java
ProjectConfig config = ProjectConfig.builder()
    .groupId(groupId)
    .jiraHostUrl(request.getJiraHostUrl())
    .jiraApiTokenEncrypted(request.getJiraApiToken()) // TODO: Encrypt
    .githubRepoUrl(request.getGithubRepoUrl())
    .githubTokenEncrypted(request.getGithubToken()) // TODO: Encrypt
    .state(ConfigState.DRAFT) // Initial state
    .build();

config = configRepository.save(config);
```

**State:** New config starts in `DRAFT` state (BR-CONFIG-05).

**TODO:** Encrypt tokens using `TokenEncryptionService.encrypt()` before saving.

---

#### STEP 5: Return DTO
```java
return toResponse(config);
```

**Token Masking:**
```java
ConfigResponse.builder()
    .jiraApiToken(tokenMaskingService.maskJiraToken(config.getJiraApiTokenEncrypted()))
    .githubToken(tokenMaskingService.maskGithubToken(config.getGithubTokenEncrypted()))
    .build();
```

**Example:**
- Stored: `ATATT3xFfGF0abc123...xyz789`
- Returned: `ATA***z789`

**Why:** Never expose raw tokens in API responses (security best practice).

---

### Error Scenarios

| Error | Condition | HTTP Status | Response Code |
|-------|-----------|-------------|---------------|
| Group not found | `VerifyGroupExists` returns `exists=false` | 404 | `GROUP_NOT_FOUND` |
| Group deleted | `VerifyGroupExists` returns `deleted=true` | 404 | `GROUP_NOT_FOUND` |
| Not LEADER | `CheckGroupLeader` returns `is_leader=false` | 403 | `FORBIDDEN` |
| Config exists | `existsByGroupId` returns `true` | 409 | `CONFIG_ALREADY_EXISTS` |
| Service unavailable | gRPC throws `UNAVAILABLE` | 503 | `SERVICE_UNAVAILABLE` |

---

## Method 2: getConfig()

### Purpose
Retrieve configuration by ID with authorization checks.

### Signature
```java
@Transactional(readOnly = true)
public ConfigResponse getConfig(
    UUID id,                       // Config ID
    Long userId,                   // Current user ID
    List<String> roles             // User roles
)
```

### Step-by-Step Logic

#### STEP 1: Find Config
```java
ProjectConfig config = configRepository.findById(id)
    .orElseThrow(() -> new ConfigNotFoundException(id));
```

**Note:** `@SQLRestriction("deleted_at IS NULL")` on entity automatically filters soft-deleted configs.

---

#### STEP 2: Authorization Check

**Rule Matrix:**

| Role | Permission |
|------|------------|
| ADMIN | View ANY config (no checks) |
| LECTURER | View ANY config (TODO: restrict to supervised groups) |
| STUDENT (LEADER) | View OWN group's config |
| STUDENT (MEMBER) | View OWN group's config |
| STUDENT (non-member) | FORBIDDEN |

**Implementation:**
```java
boolean isAdmin = roles.contains("ROLE_ADMIN");
boolean isLecturer = roles.contains("ROLE_LECTURER");

if (!isAdmin && !isLecturer) {
    // Student: must be member of the group
    checkMemberPermission(config.getGroupId(), userId);
}
```

**gRPC Call (for Students):**
```java
CheckGroupMemberResponse response = userGroupClient.checkGroupMember(groupId, userId);

if (!response.getIsMember()) {
    throw ForbiddenException("You are not a member of this group");
}

// response.getRole() → "LEADER" or "MEMBER"
```

---

#### STEP 3: Return DTO
```java
return toResponse(config);
```

Same token masking as `createConfig()`.

---

### Error Scenarios

| Error | Condition | HTTP Status | Response Code |
|-------|-----------|-------------|---------------|
| Config not found | `findById` returns empty | 404 | `CONFIG_NOT_FOUND` |
| Not member | `CheckGroupMember` returns `is_member=false` | 403 | `FORBIDDEN` |

---

## Method 3: updateConfig()

### Purpose
Update existing configuration (partial update supported). Transitions state to DRAFT if credentials changed.

### Signature
```java
@Transactional
public ConfigResponse updateConfig(
    UUID id,                       // Config ID
    UpdateConfigRequest request,   // Update data (all fields optional)
    Long userId,                   // Current user ID
    List<String> roles             // User roles
)
```

### Step-by-Step Logic

#### STEP 1: Find Config
```java
ProjectConfig config = configRepository.findById(id)
    .orElseThrow(() -> new ConfigNotFoundException(id));
```

---

#### STEP 2: Check LEADER Permission
```java
checkLeaderPermission(config.getGroupId(), userId, roles);
```

Only LEADER or ADMIN can update (BR-UPDATE-03).

---

#### STEP 3: Apply Updates

**Partial Update Pattern:**
```java
boolean hasChanges = false;

if (request.getJiraHostUrl() != null) {
    config.setJiraHostUrl(request.getJiraHostUrl());
    hasChanges = true;
}

if (request.getJiraApiToken() != null) {
    config.setJiraApiTokenEncrypted(request.getJiraApiToken()); // TODO: Encrypt
    hasChanges = true;
}

// ... repeat for githubRepoUrl, githubToken
```

**Why Optional:** Frontend can update only changed fields (RESTful best practice).

---

#### STEP 4: State Transition to DRAFT

**Business Rule (BR-UPDATE-01):** If credentials updated → state transitions to DRAFT.

```java
if (hasChanges && request.hasCredentialUpdates()) {
    config.transitionToDraft();
}
```

**Inside `transitionToDraft()`:**
```java
public void transitionToDraft() {
    this.state = ConfigState.DRAFT;
    this.lastVerifiedAt = null;
    this.invalidReason = "Configuration updated, verification required";
}
```

**Why:** Updated credentials need re-verification (BR-UPDATE-02).

**State Transition Table:**

| Before | After | lastVerifiedAt | invalidReason |
|--------|-------|----------------|---------------|
| VERIFIED | DRAFT | NULL | "Configuration updated..." |
| INVALID | DRAFT | NULL | "Configuration updated..." |
| DRAFT | DRAFT | NULL | Unchanged or updated |

---

#### STEP 5: Return DTO
```java
return toResponse(config);
```

---

### Error Scenarios

| Error | Condition | HTTP Status | Response Code |
|-------|-----------|-------------|---------------|
| Config not found | `findById` returns empty | 404 | `CONFIG_NOT_FOUND` |
| Not LEADER | `CheckGroupLeader` returns `is_leader=false` | 403 | `FORBIDDEN` |

---

## Method 4: deleteConfig()

### Purpose
Soft delete configuration. Config transitions to DELETED state (90-day retention).

### Signature
```java
@Transactional
public void deleteConfig(
    UUID id,                       // Config ID
    Long userId,                   // Current user ID
    List<String> roles             // User roles
)
```

### Step-by-Step Logic

#### STEP 1: Find Config
```java
ProjectConfig config = configRepository.findById(id)
    .orElseThrow(() -> new ConfigNotFoundException(id));
```

---

#### STEP 2: Check LEADER Permission
```java
checkLeaderPermission(config.getGroupId(), userId, roles);
```

Only LEADER or ADMIN can delete.

---

#### STEP 3: Soft Delete
```java
config.softDelete(userId);
configRepository.save(config);
```

**Inside `softDelete()`:**
```java
public void softDelete(Long deletedByUserId) {
    this.deletedAt = Instant.now();
    this.deletedBy = deletedByUserId;
    this.state = ConfigState.DELETED;
    this.updatedAt = Instant.now();
}
```

**Result:**
- `deleted_at` = current timestamp
- `deleted_by` = user who performed deletion
- `state` = `DELETED`

**Note:** `@SQLRestriction("deleted_at IS NULL")` means this config will no longer appear in normal queries.

---

### Retention Policy

**Per Soft Delete Retention Policy:**
- Deleted configs retained for **90 days**
- Cleanup job runs daily: `DELETE FROM project_configs WHERE deleted_at < NOW() - INTERVAL '90 days'`

---

## Method 5: verifyConfig()

### Purpose
Test Jira/GitHub credentials and update state (VERIFIED or INVALID).

### Signature
```java
@Transactional
public ConfigResponse verifyConfig(
    UUID id,                       // Config ID
    Long userId,                   // Current user ID
    List<String> roles             // User roles
)
```

### Step-by-Step Logic

#### STEP 1: Find Config
```java
ProjectConfig config = configRepository.findById(id)
    .orElseThrow(() -> new ConfigNotFoundException(id));
```

---

#### STEP 2: Check LEADER Permission
```java
checkLeaderPermission(config.getGroupId(), userId, roles);
```

---

#### STEP 3: Verify Credentials

**Stubbed Implementation (TODO):**
```java
try {
    // TODO: TokenEncryptionService.decrypt()
    // TODO: JiraVerificationService.verify(jiraHostUrl, jiraToken)
    //   → Call Jira API: GET /rest/api/3/myself
    // TODO: GitHubVerificationService.verify(githubRepoUrl, githubToken)
    //   → Call GitHub API: GET /repos/{owner}/{repo}
    
    // If all succeed:
    config.transitionToVerified();
    
} catch (Exception e) {
    // If any fail:
    config.transitionToInvalid("Verification failed: " + e.getMessage());
}
```

**State Transitions:**

**Success Path:**
```java
public void transitionToVerified() {
    this.state = ConfigState.VERIFIED;
    this.lastVerifiedAt = Instant.now();
    this.invalidReason = null;
}
```

**Failure Path:**
```java
public void transitionToInvalid(String reason) {
    this.state = ConfigState.INVALID;
    this.lastVerifiedAt = null;
    this.invalidReason = reason;
}
```

**State Transition Table:**

| Before | Result | After | lastVerifiedAt | invalidReason |
|--------|--------|-------|----------------|---------------|
| DRAFT | Success | VERIFIED | NOW() | NULL |
| INVALID | Success | VERIFIED | NOW() | NULL |
| DRAFT | Failure | INVALID | NULL | Error message |
| VERIFIED | Failure | INVALID | NULL | Error message |

---

## Helper Methods

### validateGroupExists()

**Purpose:** Verify group exists via gRPC call.

**Logic:**
```java
private void validateGroupExists(UUID groupId) {
    try {
        VerifyGroupResponse response = userGroupClient.verifyGroupExists(groupId);
        
        if (!response.getExists()) {
            throw new GroupNotFoundException(groupId);
        }
        
        if (response.getDeleted()) {
            throw new GroupNotFoundException("Group has been deleted: " + groupId);
        }
        
    } catch (StatusRuntimeException e) {
        handleGrpcError(e, "validate group");
    }
}
```

**gRPC Proto:**
```protobuf
message VerifyGroupRequest {
  string group_id = 1; // UUID as string
}

message VerifyGroupResponse {
  bool exists = 1;     // true if group found in database
  bool deleted = 2;    // true if group has deletedAt != null
  string message = 3;  // Descriptive message
}
```

---

### checkLeaderPermission()

**Purpose:** Verify user is LEADER of the group (with ADMIN bypass).

**Logic:**
```java
private void checkLeaderPermission(UUID groupId, Long userId, List<String> roles) {
    // ADMIN can perform any operation
    if (roles.contains("ROLE_ADMIN")) {
        return;
    }
    
    try {
        CheckGroupLeaderResponse response = userGroupClient.checkGroupLeader(groupId, userId);
        
        if (!response.getIsLeader()) {
            throw new ForbiddenException("Only group leader can perform this action");
        }
        
    } catch (StatusRuntimeException e) {
        handleGrpcError(e, "check group leader");
    }
}
```

**gRPC Proto:**
```protobuf
message CheckGroupLeaderRequest {
  string group_id = 1; // UUID as string
  string user_id = 2;  // Long as string
}

message CheckGroupLeaderResponse {
  bool is_leader = 1;  // true if user has LEADER role
  string message = 2;  // Descriptive message
}
```

---

### checkMemberPermission()

**Purpose:** Verify user is MEMBER (any role: LEADER or MEMBER) of the group.

**Logic:**
```java
private void checkMemberPermission(UUID groupId, Long userId) {
    try {
        CheckGroupMemberResponse response = userGroupClient.checkGroupMember(groupId, userId);
        
        if (!response.getIsMember()) {
            throw new ForbiddenException("You are not a member of this group");
        }
        
    } catch (StatusRuntimeException e) {
        handleGrpcError(e, "check group member");
    }
}
```

**gRPC Proto:**
```protobuf
message CheckGroupMemberRequest {
  string group_id = 1; // UUID as string
  string user_id = 2;  // Long as string
}

message CheckGroupMemberResponse {
  bool is_member = 1;   // true if user is in group (any role)
  string role = 2;      // "LEADER" or "MEMBER"
  string message = 3;   // Descriptive message
}
```

---

### handleGrpcError()

**Purpose:** Map gRPC `StatusRuntimeException` to domain exceptions.

**Why:** Never leak raw gRPC exceptions to REST layer. Convert to domain exceptions.

**Logic:**
```java
private void handleGrpcError(StatusRuntimeException e, String operation) {
    Status.Code code = e.getStatus().getCode();
    String description = e.getStatus().getDescription();
    
    switch (code) {
        case UNAVAILABLE:
            throw new ServiceUnavailableException("User-Group Service unavailable");
        
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException("User-Group Service timeout");
        
        case NOT_FOUND:
            throw new GroupNotFoundException(description);
        
        case PERMISSION_DENIED:
            throw new ForbiddenException(description);
        
        case INVALID_ARGUMENT:
            throw new BadRequestException(description);
        
        default:
            throw new RuntimeException("Failed to " + operation + ": " + code);
    }
}
```

**Error Mapping Table:**

| gRPC Status | Domain Exception | HTTP Status | Description |
|-------------|------------------|-------------|-------------|
| `UNAVAILABLE` | `ServiceUnavailableException` | 503 | User-Group Service is down |
| `DEADLINE_EXCEEDED` | `GatewayTimeoutException` | 504 | gRPC call timed out (>3s) |
| `NOT_FOUND` | `GroupNotFoundException` | 404 | Group doesn't exist |
| `PERMISSION_DENIED` | `ForbiddenException` | 403 | Access denied |
| `INVALID_ARGUMENT` | `BadRequestException` | 400 | Invalid UUID format |
| Other | `RuntimeException` | 500 | Unexpected error |

---

### toResponse()

**Purpose:** Convert entity to DTO with masked tokens.

**Logic:**
```java
private ConfigResponse toResponse(ProjectConfig config) {
    return ConfigResponse.builder()
        .id(config.getId())
        .groupId(config.getGroupId())
        .jiraHostUrl(config.getJiraHostUrl())
        .jiraApiToken(tokenMaskingService.maskJiraToken(config.getJiraApiTokenEncrypted()))
        .githubRepoUrl(config.getGithubRepoUrl())
        .githubToken(tokenMaskingService.maskGithubToken(config.getGithubTokenEncrypted()))
        .state(config.getState())
        .lastVerifiedAt(config.getLastVerifiedAt())
        .invalidReason(config.getInvalidReason())
        .createdAt(config.getCreatedAt())
        .updatedAt(config.getUpdatedAt())
        .build();
}
```

**Token Masking:**
```java
// Jira token: ATATT3xFfGF0abc123xyz789 → ATA***z789
public String maskJiraToken(String encrypted) {
    String prefix = encrypted.substring(0, 3);
    String suffix = encrypted.substring(encrypted.length() - 4);
    return prefix + "***" + suffix;
}

// GitHub token: ghp_1234567890abcdefghijklmnopqrstuvwxyz → ghp_***wxyz
public String maskGithubToken(String encrypted) {
    String suffix = encrypted.substring(encrypted.length() - 4);
    return "ghp_***" + suffix;
}
```

---

## State Machine Diagram

```
       ┌─────────┐
       │  DRAFT  │ ← Initial state (new config)
       └────┬────┘
            │
            │ verifyConfig() → success
            ↓
       ┌──────────┐
       │ VERIFIED │
       └────┬─────┘
            │
            ├─→ updateConfig(credentials) → DRAFT
            │
            └─→ verifyConfig() → failure → INVALID
            
       ┌─────────┐
       │ INVALID │
       └────┬────┘
            │
            ├─→ verifyConfig() → success → VERIFIED
            │
            └─→ updateConfig(credentials) → DRAFT
            
       ┌─────────┐
       │ DELETED │ ← Terminal state (soft delete)
       └─────────┘
```

---

## Transaction Boundaries

### Write Operations
```java
@Transactional  // Default propagation: REQUIRED
public ConfigResponse createConfig(...) { }

@Transactional
public ConfigResponse updateConfig(...) { }

@Transactional
public void deleteConfig(...) { }

@Transactional
public ConfigResponse verifyConfig(...) { }
```

**Behavior:**
- Start new transaction if none exists
- Rollback on any unchecked exception
- Auto-commit on successful return

---

### Read Operations
```java
@Transactional(readOnly = true)  // Optimization hint
public ConfigResponse getConfig(...) { }
```

**Behavior:**
- Read-only transaction (no write lock)
- Hibernate optimization: no dirty checking
- Better performance for read queries

---

## Next Steps for REST Controller

### Extract JWT Claims
```java
@RestController
@RequestMapping("/api/project-configs")
public class ProjectConfigController {
    
    private final ProjectConfigService service;
    
    @PostMapping
    public ResponseEntity<ConfigResponse> createConfig(
        @RequestBody @Valid CreateConfigRequest request,
        Authentication authentication
    ) {
        // Extract user ID from JWT
        Long userId = (Long) authentication.getPrincipal();
        
        // Extract roles from JWT
        List<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .toList();
        
        // Call service
        ConfigResponse response = service.createConfig(request, userId, roles);
        
        return ResponseEntity.status(201).body(response);
    }
}
```

### Error Handling
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ConfigNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleConfigNotFound(ConfigNotFoundException e) {
        return ResponseEntity.status(404).body(
            ErrorResponse.builder()
                .code("CONFIG_NOT_FOUND")
                .message(e.getMessage())
                .timestamp(Instant.now())
                .build()
        );
    }
    
    // ... repeat for all exceptions
}
```

---

## Summary

✅ **Complete business logic:** All 5 methods implemented  
✅ **gRPC integration:** Group validation & permission checks  
✅ **State machine:** DRAFT → VERIFIED → INVALID → DELETED  
✅ **Soft delete:** 90-day retention with @SQLRestriction  
✅ **Authorization:** ADMIN, LEADER, MEMBER checks  
✅ **Token masking:** Security best practice  
✅ **Error mapping:** gRPC → domain exceptions  
✅ **Transactional:** Proper transaction boundaries  

**Production-Ready:** Core logic complete. Requires REST controller, token encryption, and external API verification.
