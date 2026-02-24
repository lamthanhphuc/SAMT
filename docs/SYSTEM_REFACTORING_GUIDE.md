# ❌ [DEPRECATED] SAMT System Refactoring Guide

**⚠️ THIS DOCUMENT IS DEPRECATED AS OF FEBRUARY 9, 2026**

**Status:** Historical Reference Only  
**Replaced By:**
- [00_SYSTEM_OVERVIEW.md](00_SYSTEM_OVERVIEW.md) - Current architecture
- [IMPLEMENTATION_GUIDE.md](IMPLEMENTATION_GUIDE.md) - Implementation guide
- [../ARCHITECTURE_ALIGNMENT_REPORT.md](../ARCHITECTURE_ALIGNMENT_REPORT.md) - Final alignment status
- [ADR-001](ADR-001-project-config-rest-grpc-hybrid.md), [ADR-002](ADR-002-api-gateway-routing-project-config.md), [ADR-003](ADR-003-package-naming-standardization.md) - Architecture decisions

**Deprecation Reason:**  
Refactoring work completed. All issues addressed. See ARCHITECTURE_ALIGNMENT_REPORT.md for completion status.

---

## Original Content (Historical Reference)

# SAMT System Refactoring Guide

**Document Version:** 2.0  
**Date:** February 9, 2026  
**Status:** Architecture Refactoring & Security Fixes

---

## Executive Summary

This document outlines critical architecture refactoring to address:
- **Critical:** Cross-database FK issues, gRPC client exposure, API Gateway integration
- **Major:** Race conditions, gRPC security, event-driven hard delete
- **Best Practices:** Database migrations, resilience patterns, documentation standards

**Impact:** Breaking changes - requires coordinated deployment across all services.

---

## Table of Contents

1. [Critical Fixes](#1-critical-fixes)
2. [Major Fixes](#2-major-fixes)  
3. [Best Practices](#3-best-practices)
4. [Migration Plan](#4-migration-plan)
5. [Testing Strategy](#5-testing-strategy)

---

# 1. CRITICAL FIXES

## 1.1 Remove Cross-Database Foreign Keys

### Problem

Current architecture violates database-per-service principle với foreign keys xuyên database:

```sql
-- BAD: User-Group Service references Identity Service database
CREATE TABLE groups (
    lecturer_id BIGINT NOT NULL REFERENCES users(id)  -- CROSS-DB FK
);

-- BAD: Sync Service references Project Config Service database  
CREATE TABLE unified_activities (
    project_config_id BIGINT NOT NULL REFERENCES project_configs(id)  -- CROSS-DB FK
);
```

**Issues:**
- Tight coupling giữa databases
- Không thể deploy services độc lập
- Không scale được (sharding/replication)
- Referential integrity không enforce được

### Solution: Logical References + gRPC Validation

#### Step 1: Remove FK Constraints

**Migration:** `V2.0__remove_cross_db_foreign_keys.sql`

```sql
-- User-Group Service
ALTER TABLE groups DROP CONSTRAINT IF EXISTS groups_lecturer_id_fkey;
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS user_groups_user_id_fkey;

COMMENT ON COLUMN groups.lecturer_id IS 
'Logical reference to users(id) in Identity Service. Validated via gRPC VerifyUserExists().';

COMMENT ON COLUMN user_groups.user_id IS
'Logical reference to users(id) in Identity Service. Validated via gRPC VerifyUserExists().';

-- Sync Service
ALTER TABLE unified_activities DROP CONSTRAINT IF EXISTS unified_activities_config_id_fkey;
ALTER TABLE jira_issues DROP CONSTRAINT IF EXISTS jira_issues_config_id_fkey;
ALTER TABLE github_commits DROP CONSTRAINT IF EXISTS github_commits_config_id_fkey;

COMMENT ON COLUMN unified_activities.project_config_id IS
'Logical reference to project_configs(id) in Project Config Service. Validated via gRPC.';
```

#### Step 2: Implement gRPC Validation

**Identity Service - Add Validation Endpoint**

**File:** `identity-service/src/main/proto/user_service.proto`

```protobuf
service UserService {
  // Existing methods...
  
  // NEW: Bulk validation for performance
  rpc VerifyUsersExist(VerifyUsersExistRequest) returns (VerifyUsersExistResponse);
}

message VerifyUsersExistRequest {
  repeated int64 user_ids = 1;
}

message VerifyUsersExistResponse {
  message UserValidation {
    int64 user_id = 1;
    bool exists = 2;
    bool is_deleted = 3;
    string role = 4;  // ADMIN, LECTURER, STUDENT
  }
  
  repeated UserValidation validations = 1;
}
```

**Implementation:**

```java
@Override
public void verifyUsersExist(
        VerifyUsersExistRequest request,
        StreamObserver<VerifyUsersExistResponse> responseObserver) {
    
    List<Long> userIds = request.getUserIdsList();
    
    // Batch query for performance
    List<User> users = userRepository.findAllById(userIds);
    Map<Long, User> userMap = users.stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));
    
    List<UserValidation> validations = userIds.stream()
            .map(userId -> {
                User user = userMap.get(userId);
                return UserValidation.newBuilder()
                        .setUserId(userId)
                        .setExists(user != null)
                        .setIsDeleted(user != null && user.getDeletedAt() != null)
                        .setRole(user != null ? user.getRole().name() : "")
                        .build();
            })
            .collect(Collectors.toList());
    
    VerifyUsersExistResponse response = VerifyUsersExistResponse.newBuilder()
            .addAllValidations(validations)
            .build();
    
    responseObserver.onNext(response);
    responseObserver.onCompleted();
}
```

#### Step 3: Application-Level Validation

**User-Group Service - Validate Before Insert/Update**

```java
@Service
@RequiredArgsConstructor
public class GroupValidationService {
    
    private final UserServiceGrpcClient userServiceClient;
    
    /**
     * Validate lecturer exists and has LECTURER role
     */
    public void validateLecturer(Long lecturerId) {
        VerifyUsersExistResponse response = userServiceClient.verifyUsersExist(
            List.of(lecturerId)
        );
        
        UserValidation validation = response.getValidations(0);
        
        if (!validation.getExists()) {
            throw new ResourceNotFoundException("USER_NOT_FOUND", 
                "Lecturer with ID " + lecturerId + " not found");
        }
        
        if (validation.getIsDeleted()) {
            throw new InvalidOperationException("USER_DELETED",
                "Lecturer has been deleted");
        }
        
        if (!"LECTURER".equals(validation.getRole())) {
            throw new ForbiddenException("INVALID_ROLE",
                "User is not a LECTURER");
        }
    }
    
    /**
     * Validate students exist before adding to group
     */
    public void validateStudents(List<Long> studentIds) {
        if (studentIds.isEmpty()) return;
        
        VerifyUsersExistResponse response = userServiceClient.verifyUsersExist(studentIds);
        
        List<Long> notFound = new ArrayList<>();
        List<Long> deleted = new ArrayList<>();
        List<Long> wrongRole = new ArrayList<>();
        
        for (UserValidation v : response.getValidationsList()) {
            if (!v.getExists()) {
                notFound.add(v.getUserId());
            } else if (v.getIsDeleted()) {
                deleted.add(v.getUserId());
            } else if (!"STUDENT".equals(v.getRole())) {
                wrongRole.add(v.getUserId());
            }
        }
        
        if (!notFound.isEmpty()) {
            throw new ResourceNotFoundException("USERS_NOT_FOUND",
                "Users not found: " + notFound);
        }
        
        if (!deleted.isEmpty()) {
            throw new InvalidOperationException("USERS_DELETED",
                "Users have been deleted: " + deleted);
        }
        
        if (!wrongRole.isEmpty()) {
            throw new ForbiddenException("INVALID_ROLE",
                "Users are not STUDENT: " + wrongRole);
        }
    }
}
```

**Usage in Service Layer:**

```java
@Service
@Transactional
@RequiredArgsConstructor
public class GroupService {
    
    private final GroupRepository groupRepository;
    private final GroupValidationService validationService;
    
    public GroupDto createGroup(CreateGroupRequest request) {
        // VALIDATE BEFORE INSERT
        validationService.validateLecturer(request.getLecturerId());
        
        Group group = new Group();
        group.setGroupName(request.getGroupName());
        group.setSemester(request.getSemester());
        group.setLecturerId(request.getLecturerId());  // Logical reference, no FK
        
        Group saved = groupRepository.save(group);
        
        return mapToDto(saved);
    }
    
    public void addMember(Long groupId, Long userId, GroupRole role) {
        // VALIDATE USER EXISTS
        validationService.validateStudents(List.of(userId));
        
        // Check existing membership
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GROUP_NOT_FOUND"));
        
        // Business logic...
    }
}
```

**Benefits:**
- ✅ Service independence (no database coupling)
- ✅ Validation at application layer (explicit, testable)
- ✅ Performance: bulk validation reduces gRPC calls
- ✅ Graceful degradation: can cache validation results

---

## 1.2 API Gateway Integration với gRPC Transcoding

### Problem

Hiện tại:
- Frontend gọi trực tiếp gRPC (Project Config Service port 9093)
- Không có centralized authentication/authorization
- Frontend phải implement gRPC-web (complexity)

### Solution: Envoy API Gateway với gRPC-JSON Transcoding

#### Architecture

```
┌─────────────┐
│  Frontend   │
│  (React)    │
└──────┬──────┘
       │ HTTP/JSON
       │ POST /api/v1/project-configs
       ▼
┌─────────────────────────────────────────┐
│       Envoy API Gateway                 │
│                                         │
│  - JWT Validation                       │
│  - Rate Limiting                        │
│  - gRPC-JSON Transcoding               │
│  - Request/Response Transformation     │
└──────┬──────────────────────────────────┘
       │
       ├─ HTTP/REST ──────────────────┐
       │                              │
       ▼                              ▼
┌──────────────┐            ┌──────────────┐
│  Identity    │            │  User-Group  │
│  Service     │            │  Service     │
│  (REST)      │            │  (REST)      │
└──────────────┘            └──────────────┘
       │
       │ gRPC (internal) ─────────────┐
       │                              │
       ▼                              ▼
┌──────────────┐            ┌──────────────┐
│ Project      │            │ Sync         │
│ Config Svc   │            │ Service      │
│ (gRPC)       │            │ (gRPC)       │
└──────────────┘            └──────────────┘
```

#### Step 1: Envoy Configuration

**File:** `api-gateway/envoy.yaml`

```yaml
static_resources:
  listeners:
  - name: listener_0
    address:
      socket_address:
        address: 0.0.0.0
        port_value: 8080
    filter_chains:
    - filters:
      - name: envoy.filters.network.http_connection_manager
        typed_config:
          "@type": type.googleapis.com/envoy.extensions.filters.network.http_connection_manager.v3.HttpConnectionManager
          stat_prefix: ingress_http
          codec_type: AUTO
          route_config:
            name: local_route
            virtual_hosts:
            - name: backend
              domains: ["*"]
              routes:
              # REST endpoints (passthrough)
              - match:
                  prefix: "/api/auth"
                route:
                  cluster: identity_service
              
              - match:
                  prefix: "/api/users"
                route:
                  cluster: user_group_service
              
              - match:
                  prefix: "/api/groups"
                route:
                  cluster: user_group_service
              
              # gRPC-JSON Transcoding
              - match:
                  prefix: "/api/v1/project-configs"
                route:
                  cluster: project_config_service_grpc
                  timeout: 30s
          
          http_filters:
          # JWT Authentication
          - name: envoy.filters.http.jwt_authn
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.filters.http.jwt_authn.v3.JwtAuthentication
              providers:
                jwt_provider:
                  issuer: samt-identity-service
                  audiences:
                  - samt-api
                  remote_jwks:
                    http_uri:
                      uri: http://identity-service:8081/.well-known/jwks.json
                      cluster: identity_service
                      timeout: 5s
                    cache_duration:
                      seconds: 300
                  forward: true
                  payload_in_metadata: jwt_payload
              rules:
              - match:
                  prefix: "/api/auth"
                # Public endpoints - no JWT required
              - match:
                  prefix: "/api"
                requires:
                  provider_name: jwt_provider
          
          # gRPC-JSON Transcoding
          - name: envoy.filters.http.grpc_json_transcoder
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.filters.http.grpc_json_transcoder.v3.GrpcJsonTranscoder
              proto_descriptor: "/etc/envoy/proto.pb"
              services:
              - "projectconfig.ProjectConfigService"
              print_options:
                add_whitespace: true
                always_print_primitive_fields: true
                always_print_enums_as_ints: false
              auto_mapping: true
              convert_grpc_status: true
          
          # Rate Limiting
          - name: envoy.filters.http.ratelimit
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.filters.http.ratelimit.v3.RateLimit
              domain: samt_api
              rate_limit_service:
                grpc_service:
                  envoy_grpc:
                    cluster_name: ratelimit_service
          
          # Router (must be last)
          - name: envoy.filters.http.router
            typed_config:
              "@type": type.googleapis.com/envoy.extensions.filters.http.router.v3.Router
  
  clusters:
  - name: identity_service
    connect_timeout: 5s
    type: STRICT_DNS
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: identity_service
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: identity-service
                port_value: 8081
  
  - name: user_group_service
    connect_timeout: 5s
    type: STRICT_DNS
    lb_policy: ROUND_ROBIN
    load_assignment:
      cluster_name: user_group_service
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: user-group-service
                port_value: 8082
  
  - name: project_config_service_grpc
    connect_timeout: 5s
    type: STRICT_DNS
    lb_policy: ROUND_ROBIN
    http2_protocol_options: {}  # Enable HTTP/2 for gRPC
    load_assignment:
      cluster_name: project_config_service_grpc
      endpoints:
      - lb_endpoints:
        - endpoint:
            address:
              socket_address:
                address: project-config-service
                port_value: 9093
```

#### Step 2: Generate Proto Descriptor

```bash
# Generate proto descriptor for Envoy
protoc \
  --include_imports \
  --include_source_info \
  --descriptor_set_out=/etc/envoy/proto.pb \
  project_config.proto
```

#### Step 3: REST API Mapping (Google API HTTP Annotations)

**File:** `project_config.proto`

```protobuf
syntax = "proto3";

package projectconfig;

import "google/api/annotations.proto";

service ProjectConfigService {
  // Create project config
  rpc CreateProjectConfig(CreateProjectConfigRequest) returns (CreateProjectConfigResponse) {
    option (google.api.http) = {
      post: "/api/v1/project-configs"
      body: "*"
    };
  }
  
  // Get project config
  rpc GetProjectConfig(GetProjectConfigRequest) returns (GetProjectConfigResponse) {
    option (google.api.http) = {
      get: "/api/v1/project-configs/{config_id}"
    };
  }
  
  // Update project config
  rpc UpdateProjectConfig(UpdateProjectConfigRequest) returns (UpdateProjectConfigResponse) {
    option (google.api.http) = {
      put: "/api/v1/project-configs/{config_id}"
      body: "*"
    };
  }
  
  // Delete project config
  rpc DeleteProjectConfig(DeleteProjectConfigRequest) returns (DeleteProjectConfigResponse) {
    option (google.api.http) = {
      delete: "/api/v1/project-configs/{config_id}"
    };
  }
  
  // List project configs
  rpc ListProjectConfigs(ListProjectConfigsRequest) returns (ListProjectConfigsResponse) {
    option (google.api.http) = {
      get: "/api/v1/project-configs"
    };
  }
}
```

#### Step 4: Extract JWT Claims in gRPC Service

**Gateway adds metadata từ JWT:**

```yaml
# Envoy config - Forward JWT claims as metadata
- name: envoy.filters.http.jwt_authn
  typed_config:
    forward: true
    forward_payload_header: "x-jwt-payload"
```

**gRPC Service reads metadata:**

```java
@Component
public class JwtMetadataInterceptor implements ServerInterceptor {
    
    private static final Metadata.Key<String> JWT_PAYLOAD_KEY = 
        Metadata.Key.of("x-jwt-payload", Metadata.ASCII_STRING_MARSHALLER);
    
    private static final Context.Key<JwtClaims> JWT_CLAIMS_CONTEXT_KEY = 
        Context.key("jwt-claims");
    
    @Override
    public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
            ServerCall<ReqT, RespT> call,
            Metadata headers,
            ServerCallHandler<ReqT, RespT> next) {
        
        String jwtPayload = headers.get(JWT_PAYLOAD_KEY);
        
        if (jwtPayload == null) {
            // Internal service call (no JWT) - use service auth
            return next.startCall(call, headers);
        }
        
        try {
            // Parse JWT payload (base64-encoded JSON)
            String decoded = new String(Base64.getDecoder().decode(jwtPayload));
            ObjectMapper mapper = new ObjectMapper();
            JwtClaims claims = mapper.readValue(decoded, JwtClaims.class);
            
            // Store in context for service methods to access
            Context context = Context.current().withValue(JWT_CLAIMS_CONTEXT_KEY, claims);
            
            return Contexts.interceptCall(context, call, headers, next);
            
        } catch (Exception e) {
            log.error("Failed to parse JWT payload", e);
            call.close(Status.UNAUTHENTICATED
                .withDescription("Invalid JWT payload"), new Metadata());
            return new ServerCall.Listener<ReqT>() {};
        }
    }
    
    public static Long getUserId() {
        JwtClaims claims = JWT_CLAIMS_CONTEXT_KEY.get();
        return claims != null ? claims.getUserId() : null;
    }
    
    public static String getRole() {
        JwtClaims claims = JWT_CLAIMS_CONTEXT_KEY.get();
        return claims != null ? claims.getRole() : null;
    }
}

@Data
public class JwtClaims {
    private Long userId;
    private String username;
    private String email;
    private String role;
    private List<String> permissions;
}
```

**Usage in gRPC service:**

```java
@Override
public void createProjectConfig(
        CreateProjectConfigRequest request,
        StreamObserver<CreateProjectConfigResponse> responseObserver) {
    
    // Extract user info from JWT (via API Gateway)
    Long userId = JwtMetadataInterceptor.getUserId();
    String role = JwtMetadataInterceptor.getRole();
    
    log.info("User {} (role: {}) creating project config", userId, role);
    
    // Business logic...
}
```

#### Step 5: Frontend API Calls (Standard REST)

**BEFORE (gRPC-web):**
```javascript
// BAD: Frontend calls gRPC directly
import { ProjectConfigServiceClient } from './proto/project_config_grpc_web_pb';

const client = new ProjectConfigServiceClient('http://localhost:9093');
const request = new CreateProjectConfigRequest();
request.setJiraHostUrl('https://example.atlassian.net');

client.createProjectConfig(request, {}, (err, response) => {
  // Handle response
});
```

**AFTER (REST/JSON via Gateway):**
```javascript
// GOOD: Frontend calls REST API via Gateway
const createProjectConfig = async (data) => {
  const response = await fetch('/api/v1/project-configs', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${getAccessToken()}`
    },
    body: JSON.stringify({
      groupId: data.groupId,
      jiraHostUrl: data.jiraHostUrl,
      jiraApiToken: data.jiraApiToken,
      jiraProjectKey: data.jiraProjectKey,
      githubRepoUrl: data.githubRepoUrl,
      githubAccessToken: data.githubAccessToken
    })
  });
  
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}: ${await response.text()}`);
  }
  
  return await response.json();
};
```

**Benefits:**
- ✅ Frontend chỉ dùng standard HTTP/REST (no gRPC-web complexity)
- ✅ Centralized authentication/authorization tại Gateway
- ✅ Rate limiting, logging, monitoring tại một điểm
- ✅ Dễ test với curl/Postman
- ✅ Backward compatible (REST clients existing)

---

# 2. MAJOR FIXES

## 2.1 Fix Race Condition - One Group Per Semester

### Problem

Hiện tại constraint không đủ:

```sql
-- BAD: Chỉ prevent duplicate trong cùng group
CREATE UNIQUE INDEX idx_user_groups_unique ON user_groups(user_id, group_id);
```

**Race condition:**
```
Thread 1: User joins Group A (Semester: Fall 2024)
Thread 2: User joins Group B (Semester: Fall 2024)  -- Should fail but doesn't
```

### Solution: Semester-Based Membership Table

#### Step 1: Create Mapping Table

**Migration:** `V2.1__create_user_semester_membership.sql`

```sql
-- New table to enforce one group per semester per user
CREATE TABLE user_semester_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    semester VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL,  -- LEADER, MEMBER
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- CRITICAL: Prevent multiple groups per semester
    CONSTRAINT uq_user_semester UNIQUE(user_id, semester)
);

CREATE INDEX idx_usm_user ON user_semester_memberships(user_id);
CREATE INDEX idx_usm_group ON user_semester_memberships(group_id);
CREATE INDEX idx_usm_semester ON user_semester_memberships(semester);

COMMENT ON TABLE user_semester_memberships IS
'Enforces business rule: one user can only join one group per semester.
Unique constraint on (user_id, semester) prevents race conditions.';

-- Migrate existing data
INSERT INTO user_semester_memberships (user_id, group_id, semester, role, joined_at)
SELECT 
    ug.user_id,
    ug.group_id,
    g.semester,
    ug.role,
    ug.created_at
FROM user_groups ug
JOIN groups g ON ug.group_id = g.id
WHERE ug.deleted_at IS NULL
  AND g.deleted_at IS NULL;

-- Keep user_groups table for backward compatibility
-- Add semester column for denormalization (performance)
ALTER TABLE user_groups ADD COLUMN semester VARCHAR(50);

UPDATE user_groups ug
SET semester = g.semester
FROM groups g
WHERE ug.group_id = g.id;
```

#### Step 2: Service Layer Implementation

```java
@Service
@Transactional
@RequiredArgsConstructor
public class GroupMembershipService {
    
    private final UserSemesterMembershipRepository membershipRepository;
    private final UserGroupRepository userGroupRepository;
    private final GroupRepository groupRepository;
    
    /**
     * Add member to group - Atomic operation
     */
    public void addMember(Long groupId, Long userId, GroupRole role) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("GROUP_NOT_FOUND"));
        
        String semester = group.getSemester();
        
        try {
            // CRITICAL: Insert into semester membership table
            // Unique constraint will prevent duplicates
            UserSemesterMembership membership = new UserSemesterMembership();
            membership.setUserId(userId);
            membership.setGroupId(groupId);
            membership.setSemester(semester);
            membership.setRole(role);
            membershipRepository.save(membership);
            
            // Also insert into user_groups for backward compatibility
            UserGroup userGroup = new UserGroup();
            userGroup.setUserId(userId);
            userGroup.setGroupId(groupId);
            userGroup.setSemester(semester);
            userGroup.setRole(role);
            userGroupRepository.save(userGroup);
            
            log.info("User {} added to group {} (semester: {})", userId, groupId, semester);
            
        } catch (DataIntegrityViolationException e) {
            // Unique constraint violated
            if (e.getMessage().contains("uq_user_semester")) {
                throw new BusinessRuleViolationException("ALREADY_IN_GROUP_THIS_SEMESTER",
                    String.format("User %d already belongs to a group in semester %s", userId, semester));
            }
            throw e;
        }
    }
    
    /**
     * Transfer student to different group (same semester)
     */
    @Transactional
    public void transferMember(Long userId, Long fromGroupId, Long toGroupId) {
        // Validate both groups in same semester
        Group fromGroup = groupRepository.findById(fromGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("FROM_GROUP_NOT_FOUND"));
        Group toGroup = groupRepository.findById(toGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("TO_GROUP_NOT_FOUND"));
        
        if (!fromGroup.getSemester().equals(toGroup.getSemester())) {
            throw new BusinessRuleViolationException("DIFFERENT_SEMESTER",
                "Cannot transfer between different semesters");
        }
        
        // Update membership atomically
        UserSemesterMembership membership = membershipRepository
                .findByUserIdAndSemester(userId, fromGroup.getSemester())
                .orElseThrow(() -> new ResourceNotFoundException("MEMBERSHIP_NOT_FOUND"));
        
        membership.setGroupId(toGroupId);
        membershipRepository.save(membership);
        
        // Update user_groups
        UserGroup userGroup = userGroupRepository.findByUserIdAndGroupId(userId, fromGroupId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_GROUP_NOT_FOUND"));
        userGroup.setGroupId(toGroupId);
        userGroupRepository.save(userGroup);
        
        log.info("User {} transferred from group {} to group {}", userId, fromGroupId, toGroupId);
    }
}
```

**Benefits:**
- ✅ Database-level enforcement (no race conditions)
- ✅ Atomic operation (unique constraint)
- ✅ Clear error messages
- ✅ Audit trail (who joined when)

---

## 2.2 gRPC Security - Internal Service Trust

### Problem

Hiện tại internal gRPC services không có authentication:
- Bất kỳ ai trong network đều có thể gọi gRPC endpoints
- Service-to-service auth chỉ là simple key check (insecure)

### Solution: mTLS + Service Mesh (Istio)

#### Architecture

```
┌─────────────────────────────────────────────┐
│          Istio Service Mesh                 │
│                                             │
│  - Automatic mTLS between services          │
│  - AuthorizationPolicy (RBAC)              │
│  - JWT validation at ingress               │
└─────────────────────────────────────────────┘
          │
          ├─ mTLS ───────────────┐
          │                      │
          ▼                      ▼
┌──────────────────┐   ┌──────────────────┐
│ Identity Service │   │ User-Group Svc   │
│                  │   │                  │
│ Cert: identity.  │   │ Cert: usergroup. │
│       samt.svc   │   │       samt.svc   │
└──────────────────┘   └──────────────────┘
```

#### Step 1: Enable Istio Sidecar

**File:** `k8s/identity-service-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: identity-service
  namespace: samt
spec:
  template:
    metadata:
      labels:
        app: identity-service
        version: v1
      annotations:
        # Enable Istio sidecar injection
        sidecar.istio.io/inject: "true"
    spec:
      serviceAccountName: identity-service
      containers:
      - name: identity-service
        image: samt/identity-service:latest
        ports:
        - containerPort: 8081
          name: http
        - containerPort: 9091
          name: grpc
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "production"
```

#### Step 2: Configure mTLS Policy

**File:** `k8s/istio-mtls-policy.yaml`

```yaml
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: default
  namespace: samt
spec:
  mtls:
    mode: STRICT  # Enforce mTLS for all services
---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: identity-service-authz
  namespace: samt
spec:
  selector:
    matchLabels:
      app: identity-service
  action: ALLOW
  rules:
  # Allow API Gateway to call REST endpoints
  - from:
    - source:
        principals: ["cluster.local/ns/samt/sa/api-gateway"]
    to:
    - operation:
        ports: ["8081"]
        methods: ["GET", "POST", "PUT", "DELETE"]
  
  # Allow User-Group Service to call gRPC endpoints
  - from:
    - source:
        principals: ["cluster.local/ns/samt/sa/user-group-service"]
    to:
    - operation:
        ports: ["9091"]
        paths: ["/userservice.UserService/*"]
---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: project-config-service-authz
  namespace: samt
spec:
  selector:
    matchLabels:
      app: project-config-service
  action: ALLOW
  rules:
  # Only allow Sync Service to call InternalGetDecryptedConfig
  - from:
    - source:
        principals: ["cluster.local/ns/samt/sa/sync-service"]
    to:
    - operation:
        ports: ["9093"]
        paths: ["/projectconfig.ProjectConfigInternalService/InternalGetDecryptedConfig"]
  
  # Only allow API Gateway to call public methods
  - from:
    - source:
        principals: ["cluster.local/ns/samt/sa/api-gateway"]
    to:
    - operation:
        ports: ["9093"]
        paths: ["/projectconfig.ProjectConfigService/*"]
```

#### Step 3: JWT Validation at Ingress Gateway

**File:** `k8s/istio-gateway-jwt.yaml`

```yaml
apiVersion: security.istio.io/v1beta1
kind: RequestAuthentication
metadata:
  name: jwt-authentication
  namespace: istio-system
spec:
  selector:
    matchLabels:
      istio: ingressgateway
  jwtRules:
  - issuer: "samt-identity-service"
    audiences:
    - "samt-api"
    jwksUri: "http://identity-service.samt.svc.cluster.local:8081/.well-known/jwks.json"
    forwardOriginalToken: true
    outputPayloadToHeader: "x-jwt-payload"
---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: require-jwt
  namespace: istio-system
spec:
  selector:
    matchLabels:
      istio: ingressgateway
  action: ALLOW
  rules:
  # Public endpoints (no JWT required)
  - to:
    - operation:
        paths: ["/api/auth/*", "/health", "/metrics"]
  
  # Protected endpoints (JWT required)
  - from:
    - source:
        requestPrincipals: ["*"]
    to:
    - operation:
        paths: ["/api/*"]
```

#### Step 4: Service Account Setup

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: identity-service
  namespace: samt
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: user-group-service
  namespace: samt
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: project-config-service
  namespace: samt
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: sync-service
  namespace: samt
---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: api-gateway
  namespace: samt
```

**Benefits:**
- ✅ Automatic mTLS encryption (no code changes)
- ✅ Service identity verification (certificates)
- ✅ Fine-grained authorization (method-level RBAC)
- ✅ Centralized policy management
- ✅ Audit logs (who called what service)

---

## 2.3 Event-Driven Hard Delete

### Problem

Hiện tại hard delete là manual job chạy trong Identity Service:
- Các services khác không biết khi user bị hard delete
- Orphaned records (user_groups, project_configs, etc.)
- Inconsistent data

### Solution: Event-Driven với Kafka

#### Architecture

```
┌──────────────────┐
│ Identity Service │
│                  │
│ Hard delete user │
└────────┬─────────┘
         │
         │ Publish: UserDeletedEvent
         ▼
┌─────────────────────────────────┐
│     Kafka Topic                 │
│     samt.users.deleted          │
└─────────────────────────────────┘
         │
         ├─────────────┬──────────────┐
         │             │              │
         ▼             ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ User-Group   │ │ Project      │ │ Sync         │
│ Service      │ │ Config Svc   │ │ Service      │
│              │ │              │ │              │
│ Delete       │ │ Delete       │ │ Delete       │
│ memberships  │ │ configs      │ │ activities   │
└──────────────┘ └──────────────┘ └──────────────┘
```

#### Step 1: Define Event Schema

**File:** `common-events/src/main/avro/user_deleted_event.avsc`

```json
{
  "type": "record",
  "name": "UserDeletedEvent",
  "namespace": "com.fpt.swp391.events",
  "fields": [
    {
      "name": "userId",
      "type": "long",
      "doc": "ID of the user that was hard deleted"
    },
    {
      "name": "email",
      "type": "string",
      "doc": "Email of the deleted user"
    },
    {
      "name": "role",
      "type": "string",
      "doc": "Role of the deleted user (ADMIN, LECTURER, STUDENT)"
    },
    {
      "name": "deletedAt",
      "type": "long",
      "logicalType": "timestamp-millis",
      "doc": "Timestamp when user was hard deleted"
    },
    {
      "name": "reason",
      "type": ["null", "string"],
      "default": null,
      "doc": "Optional reason for deletion"
    }
  ]
}
```

#### Step 2: Publisher (Identity Service)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserHardDeleteService {
    
    private final UserRepository userRepository;
    private final KafkaTemplate<String, UserDeletedEvent> kafkaTemplate;
    
    @Value("${kafka.topics.user-deleted}")
    private String userDeletedTopic;
    
    /**
     * Hard delete user after 90-day retention
     * Publishes event for other services to handle
     */
    @Scheduled(cron = "0 0 2 * * *")  // 2 AM daily
    public void hardDeleteExpiredUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(90);
        
        List<User> expiredUsers = userRepository
                .findByDeletedAtBeforeAndDeletedAtIsNotNull(threshold);
        
        log.info("Found {} users for hard deletion", expiredUsers.size());
        
        for (User user : expiredUsers) {
            hardDeleteUser(user);
        }
    }
    
    @Transactional
    public void hardDeleteUser(User user) {
        Long userId = user.getId();
        String email = user.getEmail();
        String role = user.getRole().name();
        
        try {
            // 1. Publish event BEFORE deleting (important for event delivery)
            UserDeletedEvent event = UserDeletedEvent.newBuilder()
                    .setUserId(userId)
                    .setEmail(email)
                    .setRole(role)
                    .setDeletedAt(System.currentTimeMillis())
                    .setReason("90-day retention expired")
                    .build();
            
            kafkaTemplate.send(userDeletedTopic, userId.toString(), event)
                    .get(5, TimeUnit.SECONDS);  // Wait for acknowledgment
            
            log.info("Published UserDeletedEvent for user {}", userId);
            
            // 2. Delete from database
            userRepository.delete(user);
            
            log.info("Hard deleted user {} ({})", userId, email);
            
        } catch (Exception e) {
            log.error("Failed to hard delete user {}: {}", userId, e.getMessage(), e);
            throw new RuntimeException("Hard delete failed for user " + userId, e);
        }
    }
}
```

#### Step 3: Subscriber (User-Group Service)

```java
@Service
@Slf4j
@RequiredArgsConstructor
public class UserDeletedEventHandler {
    
    private final UserGroupRepository userGroupRepository;
    private final GroupRepository groupRepository;
    private final UserSemesterMembershipRepository membershipRepository;
    
    @KafkaListener(
        topics = "${kafka.topics.user-deleted}",
        groupId = "user-group-service",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleUserDeleted(UserDeletedEvent event) {
        Long userId = event.getUserId();
        String role = event.getRole();
        
        log.info("Received UserDeletedEvent: userId={}, role={}", userId, role);
        
        try {
            if ("STUDENT".equals(role)) {
                handleStudentDeleted(userId);
            } else if ("LECTURER".equals(role)) {
                handleLecturerDeleted(userId);
            }
            
            log.info("Successfully processed UserDeletedEvent for user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to process UserDeletedEvent for user {}: {}", 
                userId, e.getMessage(), e);
            // Will retry based on Kafka consumer config
            throw e;
        }
    }
    
    private void handleStudentDeleted(Long userId) {
        // Hard delete memberships
        List<UserGroup> memberships = userGroupRepository.findByUserId(userId);
        log.info("Deleting {} memberships for student {}", memberships.size(), userId);
        
        userGroupRepository.deleteAll(memberships);
        
        // Delete semester memberships
        membershipRepository.deleteByUserId(userId);
        
        // Note: Don't delete groups - other members still exist
    }
    
    private void handleLecturerDeleted(Long userId) {
        // Find groups where this lecturer is assigned
        List<Group> groups = groupRepository.findByLecturerId(userId);
        log.info("Found {} groups for lecturer {}", groups.size(), userId);
        
        for (Group group : groups) {
            // Option 1: Reassign to admin
            group.setLecturerId(1L);  // Default admin ID
            groupRepository.save(group);
            
            // Option 2: Soft delete group (if business requires)
            // group.setDeletedAt(LocalDateTime.now());
            // group.setDeletedBy(0L);  // System
            // groupRepository.save(group);
        }
        
        log.info("Reassigned {} groups for deleted lecturer {}", groups.size(), userId);
    }
}
```

#### Step 4: Kafka Configuration

```yaml
# application.yml (all services)
spring:
  kafka:
    bootstrap-servers: kafka:9092
    consumer:
      group-id: ${spring.application.name}
      auto-offset-reset: earliest
      enable-auto-commit: false
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: io.confluent.kafka.serializers.KafkaAvroDeserializer
      properties:
        schema.registry.url: http://schema-registry:8081
        specific.avro.reader: true
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: io.confluent.kafka.serializers.KafkaAvroSerializer
      properties:
        schema.registry.url: http://schema-registry:8081
    listener:
      ack-mode: manual  # Manual acknowledgment for reliability

kafka:
  topics:
    user-deleted: samt.users.deleted
```

**Configuration Bean:**

```java
@Configuration
@EnableKafka
public class KafkaConfig {
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent> 
            kafkaListenerContainerFactory(
                ConsumerFactory<String, UserDeletedEvent> consumerFactory) {
        
        ConcurrentKafkaListenerContainerFactory<String, UserDeletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        
        factory.setConsumerFactory(consumerFactory);
        factory.setConcurrency(3);  // 3 concurrent consumers
        
        // Error handling
        factory.setCommonErrorHandler(new DefaultErrorHandler(
            new FixedBackOff(1000L, 3L)  // Retry 3 times with 1s delay
        ));
        
        return factory;
    }
}
```

**Benefits:**
- ✅ Decoupled services (no direct dependencies)
- ✅ Async processing (non-blocking)
- ✅ Reliability (Kafka guarantees delivery)
- ✅ Scalable (add more consumers)
- ✅ Audit trail (events stored in Kafka)

---

# 3. BEST PRACTICES

## 3.1 Database Migrations với Flyway

### Problem

Hiện tại schema changes không versioned:
- Manual SQL scripts
- Không có rollback mechanism
- Inconsistent state giữa environments

### Solution: Flyway Versioned Migrations

#### Step 1: Add Flyway Dependency

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
```

#### Step 2: Configure Flyway

```yaml
# application.yml
spring:
  flyway:
    enabled: true
    baseline-on-migrate: true
    baseline-version: 1.0
    locations: classpath:db/migration
    schemas: public
    table: flyway_schema_history
    validate-on-migrate: true
```

#### Step 3: Create Migrations

**File Structure:**
```
src/
└── main/
    └── resources/
        └── db/
            └── migration/
                ├── V1.0__initial_schema.sql
                ├── V1.1__add_audit_columns.sql
                ├── V2.0__remove_cross_db_fk.sql
                ├── V2.1__create_user_semester_membership.sql
                └── V2.2__add_github_username.sql
```

**Example Migration:**

**File:** `V2.0__remove_cross_db_fk.sql`

```sql
-- Remove cross-database foreign keys
-- User-Group Service database

-- Drop FK constraints
ALTER TABLE groups DROP CONSTRAINT IF EXISTS groups_lecturer_id_fkey;
ALTER TABLE user_groups DROP CONSTRAINT IF EXISTS user_groups_user_id_fkey;

-- Add comments to document logical references
COMMENT ON COLUMN groups.lecturer_id IS 
'Logical reference to users(id) in Identity Service. 
Validated via gRPC VerifyUserExists(). No DB-level FK constraint.';

COMMENT ON COLUMN user_groups.user_id IS
'Logical reference to users(id) in Identity Service.
Validated via gRPC VerifyUserExists(). No DB-level FK constraint.';

-- Add indexes for performance (no FK, but still need lookups)
CREATE INDEX IF NOT EXISTS idx_groups_lecturer_id ON groups(lecturer_id) 
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_user_groups_user_id ON user_groups(user_id)
WHERE deleted_at IS NULL;
```

**File:** `V2.1__create_user_semester_membership.sql`

```sql
-- Create semester membership table to prevent race conditions

CREATE TABLE user_semester_memberships (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    semester VARCHAR(50) NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('LEADER', 'MEMBER')),
    joined_at TIMESTAMP NOT NULL DEFAULT NOW(),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- CRITICAL: Enforce one group per semester
    CONSTRAINT uq_user_semester UNIQUE(user_id, semester)
);

-- Indexes
CREATE INDEX idx_usm_user ON user_semester_memberships(user_id);
CREATE INDEX idx_usm_group ON user_semester_memberships(group_id);
CREATE INDEX idx_usm_semester ON user_semester_memberships(semester);

-- Comments
COMMENT ON TABLE user_semester_memberships IS
'Enforces business rule: one user can only join one group per semester.
Unique constraint (user_id, semester) prevents race conditions at DB level.';

COMMENT ON CONSTRAINT uq_user_semester ON user_semester_memberships IS
'Prevents user from joining multiple groups in the same semester.
Database-level enforcement prevents race conditions.';

-- Migrate existing data from user_groups
INSERT INTO user_semester_memberships (user_id, group_id, semester, role, joined_at, created_at)
SELECT 
    ug.user_id,
    ug.group_id,
    g.semester,
    ug.role,
    ug.created_at,
    ug.created_at
FROM user_groups ug
JOIN groups g ON ug.group_id = g.id
WHERE ug.deleted_at IS NULL
  AND g.deleted_at IS NULL
ON CONFLICT (user_id, semester) DO NOTHING;  -- Skip duplicates (data cleanup needed)

-- Add semester to user_groups for denormalization (performance)
ALTER TABLE user_groups ADD COLUMN IF NOT EXISTS semester VARCHAR(50);

UPDATE user_groups ug
SET semester = g.semester
FROM groups g
WHERE ug.group_id = g.id
  AND ug.semester IS NULL;
```

#### Step 4: Rollback Strategy

**File:** `V2.1__create_user_semester_membership_rollback.sql` (manual, for emergencies)

```sql
-- Rollback migration V2.1 (manual only, not automatic)
-- Use with caution in production

-- Drop table
DROP TABLE IF EXISTS user_semester_memberships;

-- Remove semester column from user_groups
ALTER TABLE user_groups DROP COLUMN IF EXISTS semester;
```

**Benefits:**
- ✅ Versioned schema changes
- ✅ Repeatable (idempotent)
- ✅ Automatic on startup
- ✅ Audit trail (flyway_schema_history table)
- ✅ Team collaboration (Git tracking)

---

## 3.2 Resilience Patterns với Resilience4j

### Step 1: Add Dependencies

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
```

### Step 2: Configuration

```yaml
# application.yml
resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowSize: 100
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 10
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - io.grpc.StatusRuntimeException
          - java.net.ConnectException
    instances:
      identityService:
        baseConfig: default
      userGroupService:
        baseConfig: default
      projectConfigService:
        baseConfig: default
  
  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - io.grpc.StatusRuntimeException
          - java.net.SocketTimeoutException
        ignoreExceptions:
          - com.fpt.swp391.exception.ResourceNotFoundException
    instances:
      identityService:
        baseConfig: default
      userGroupService:
        baseConfig: default
  
  timelimiter:
    configs:
      default:
        timeoutDuration: 5s
    instances:
      identityService:
        baseConfig: default
```

### Step 3: Apply to gRPC Client

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceGrpcClient {
    
    private final UserServiceGrpc.UserServiceBlockingStub stub;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    
    /**
     * Get user with resilience patterns
     */
    @CircuitBreaker(name = "identityService", fallbackMethod = "getUserFallback")
    @Retry(name = "identityService")
    @TimeLimiter(name = "identityService")
    public CompletableFuture<UserDto> getUser(Long userId) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                GetUserRequest request = GetUserRequest.newBuilder()
                        .setUserId(userId)
                        .build();
                
                GetUserResponse response = stub.getUser(request);
                
                return mapToDto(response);
                
            } catch (StatusRuntimeException e) {
                log.error("gRPC call failed: {}", e.getMessage());
                throw new ServiceUnavailableException(
                    "Identity Service unavailable", e);
            }
        });
    }
    
    /**
     * Fallback method when circuit is open
     */
    private CompletableFuture<UserDto> getUserFallback(Long userId, Throwable t) {
        log.warn("Circuit breaker fallback triggered for user {}: {}", 
            userId, t.getMessage());
        
        // Return cached data or default response
        UserDto fallbackUser = new UserDto();
        fallbackUser.setId(userId);
        fallbackUser.setUsername("Unknown");
        fallbackUser.setEmail("unavailable@example.com");
        
        return CompletableFuture.completedFuture(fallbackUser);
    }
}
```

### Step 4: Monitor Circuit Breaker State

```java
@RestController
@RequestMapping("/actuator/circuit-breaker")
@RequiredArgsConstructor
public class CircuitBreakerController {
    
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    
    @GetMapping("/status")
    public Map<String, String> getCircuitBreakerStatus() {
        Map<String, String> statuses = new HashMap<>();
        
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(cb -> {
            statuses.put(cb.getName(), cb.getState().toString());
        });
        
        return statuses;
    }
    
    @PostMapping("/{name}/reset")
    public void resetCircuitBreaker(@PathVariable String name) {
        circuitBreakerRegistry.circuitBreaker(name)
                .transitionToClosedState();
    }
}
```

**Benefits:**
- ✅ Prevent cascade failures
- ✅ Automatic retry với exponential backoff
- ✅ Fallback mechanisms
- ✅ Monitoring và metrics
- ✅ Manual circuit control

---

## 3.3 Documentation Standards

### Step 1: Mark Legacy Documents

**File:** `docs/SOFTWARE REQUIREMENTS SPECIFICATION (SRS) (5).md`

Add header:

```markdown
# ⚠️ DEPRECATED - See SRS_REFACTORED.md

**Status:** DEPRECATED  
**Date:** February 9, 2026  
**Reason:** Contains outdated OAuth references and REST-only architecture

**Migration Path:**
- For system requirements, see: [SRS_REFACTORED.md](SRS_REFACTORED.md)
- For API specifications, see: [API Gateway Swagger](http://api-gateway:8080/swagger-ui.html)
- For service details, see individual service documentation folders

---

# [LEGACY] SOFTWARE REQUIREMENTS SPECIFICATION (SRS)

...existing content...
```

### Step 2: Centralized Swagger at API Gateway

**File:** `api-gateway/src/main/java/com/fpt/swp391/gateway/config/SwaggerConfig.java`

```java
@Configuration
@EnableSwagger2
public class SwaggerConfig {
    
    @Bean
    public Docket api() {
        return new Docket(DocumentationType.SWAGGER_2)
                .select()
                .apis(RequestHandlerSelectors.basePackage("com.fpt.swp391"))
                .paths(PathSelectors.ant("/api/**"))
                .build()
                .apiInfo(apiInfo())
                .securitySchemes(Collections.singletonList(apiKey()))
                .securityContexts(Collections.singletonList(securityContext()));
    }
    
    private ApiInfo apiInfo() {
        return new ApiInfoBuilder()
                .title("SAMT API Gateway")
                .description("Student Assignment Management Tool - Unified REST API")
                .version("2.0.0")
                .contact(new Contact("SAMT Team", "https://samt.edu.vn", "support@samt.edu.vn"))
                .build();
    }
    
    private ApiKey apiKey() {
        return new ApiKey("JWT", "Authorization", "header");
    }
    
    private SecurityContext securityContext() {
        return SecurityContext.builder()
                .securityReferences(defaultAuth())
                .forPaths(PathSelectors.regex("/api/.*"))
                .build();
    }
    
    private List<SecurityReference> defaultAuth() {
        AuthorizationScope authorizationScope = new AuthorizationScope(
            "global", "accessEverything");
        AuthorizationScope[] authorizationScopes = new AuthorizationScope[1];
        authorizationScopes[0] = authorizationScope;
        return Collections.singletonList(
            new SecurityReference("JWT", authorizationScopes));
    }
}
```

**Access Swagger UI:**
- Development: `http://localhost:8080/swagger-ui.html`
- Production: `https://api.samt.edu.vn/swagger-ui.html`

**Benefits:**
- ✅ Single source of truth for API docs
- ✅ Interactive testing (try APIs directly)
- ✅ Auto-generated from code annotations
- ✅ JWT authentication built-in
- ✅ Export to OpenAPI 3.0 format

---

# 4. MIGRATION PLAN

## Phase 1: Infrastructure (Week 1)

### Tasks
- [ ] Deploy Kafka cluster (3 brokers + ZooKeeper)
- [ ] Deploy Schema Registry
- [ ] Setup Istio service mesh
- [ ] Configure mTLS policies
- [ ] Deploy Envoy API Gateway

### Rollback Strategy
- Keep existing services running
- API Gateway routes to old services initially
- Gradual traffic migration (10% → 50% → 100%)

---

## Phase 2: Database Refactoring (Week 2)

### Tasks
- [ ] Run Flyway migrations on all databases:
  - `V2.0__remove_cross_db_fk.sql`
  - `V2.1__create_user_semester_membership.sql`
  - `V2.2__add_github_username.sql`
- [ ] Verify data migration success
- [ ] Update application code to use new tables

### Verification
```sql
-- Check migration status
SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;

-- Verify unique constraint works
INSERT INTO user_semester_memberships (user_id, group_id, semester, role)
VALUES (1, 1, 'Fall2024', 'MEMBER');
-- Should fail: ERROR: duplicate key violates unique constraint "uq_user_semester"
```

---

## Phase 3: Code Refactoring (Week 3-4)

### Identity Service
- [ ] Implement `VerifyUsersExist` gRPC method
- [ ] Add Kafka producer for UserDeletedEvent
- [ ] Deploy with backward compatibility

### User-Group Service
- [ ] Update to use `UserSemesterMembership` table
- [ ] Implement gRPC validation calls
- [ ] Add Kafka consumer for UserDeletedEvent
- [ ] Deploy with backward compatibility

### Project Config Service
- [ ] Add Google API HTTP annotations to proto
- [ ] Implement JWT metadata extraction
- [ ] Deploy with Envoy transcoding
- [ ] Verify REST API works via Gateway

### Sync Service
- [ ] Update to call `ListVerifiedConfigs` gRPC
- [ ] Add Resilience4j annotations
- [ ] Deploy and test

---

## Phase 4: Testing & Cutover (Week 5)

### Integration Testing
- [ ] Test API Gateway → gRPC transcoding
- [ ] Test mTLS between services
- [ ] Test circuit breaker failures
- [ ] Test event-driven hard delete
- [ ] Load testing (1000 concurrent requests)

### Production Cutover
1. Deploy all services to staging
2. Run smoke tests
3. Deploy to production (blue-green)
4. Monitor for 24 hours
5. Rollback old infrastructure if stable

---

# 5. TESTING STRATEGY

## Unit Tests
- Service layer validation logic
- Event handlers (Kafka consumers)
- gRPC client resilience

## Integration Tests
- API Gateway → gRPC transcoding
- Database constraints (unique index)
- Kafka event flow (end-to-end)

## Performance Tests
```bash
# Load test API Gateway
k6 run --vus 100 --duration 5m load-test.js

# Test circuit breaker
# Simulate Identity Service failure
kubectl scale deployment identity-service --replicas=0
# Verify fallback works
curl http://api-gateway:8080/api/users/123
```

## Security Tests
- mTLS certificate validation
- JWT token expiration
- Authorization policy enforcement

---

# Summary

**Critical Fixes:**
- ✅ Removed cross-database FKs (logical references + gRPC validation)
- ✅ API Gateway với gRPC transcoding (Envoy)
- ✅ Frontend chỉ dùng REST/JSON (no gRPC-web)

**Major Fixes:**
- ✅ Race condition fixed (unique constraint on user_semester)
- ✅ gRPC security (mTLS + Istio AuthorizationPolicy)
- ✅ Event-driven hard delete (Kafka + Avro)

**Best Practices:**
- ✅ Flyway versioned migrations
- ✅ Resilience4j (Circuit Breaker + Retry)
- ✅ Centralized Swagger tại Gateway

**Migration Timeline:** 5 weeks  
**Risk Level:** Medium (breaking changes, coordinated deployment)  
**Rollback Plan:** Keep old infrastructure running, gradual cutover

---

**Document Owner:** Senior Software Engineer  
**Review Date:** February 9, 2026  
**Next Review:** Post-deployment retrospective
