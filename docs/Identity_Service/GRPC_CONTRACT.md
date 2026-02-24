# gRPC Contract - Identity Service

## 1. Overview

Identity Service exposes gRPC APIs for inter-service communication, providing user data provisioning to other microservices in the SAMT system.

**Purpose:** Enable other services to retrieve user information, verify user existence, validate roles, and manage user profiles without direct database access.

**Framework:** `net.devh:grpc-server-spring-boot-starter`

---

## 2. gRPC Server Configuration

- **Port:** `9091`
- **Protocol:** gRPC (HTTP/2)
- **Framework:** net.devh boot grpc
- **Implementation:** `UserGrpcServiceImpl.java`
- **Proto File:** `src/main/proto/user_service.proto`
- **Java Package:** `com.example.identityservice.grpc`

**Configuration (application.yml):**
```yaml
grpc:
  server:
    port: ${GRPC_SERVER_PORT:9091}
```

---

## 3. Exposed RPCs

### 3.1 GetUser

**Definition:**
```protobuf
rpc GetUser(GetUserRequest) returns (GetUserResponse);
```

**Request:**
```protobuf
message GetUserRequest {
  string user_id = 1; // User ID as string
}
```

**Response:**
```protobuf
message GetUserResponse {
  string user_id = 1;
  string email = 2;
  string full_name = 3;
  UserStatus status = 4;  // ACTIVE = 0, LOCKED = 1
  UserRole role = 5;      // ADMIN = 0, LECTURER = 1, STUDENT = 2
  bool deleted = 6;
}
```

**Business Purpose:**
- Retrieve basic user information for display purposes
- Used by User-Group Service when building group member lists
- Used for displaying user details in external service UIs

**Called by:**
- User-Group Service (when fetching group member details)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `NOT_FOUND` | User does not exist or is soft-deleted | User not found in database |
| `INVALID_ARGUMENT` | Invalid user ID format | User ID must be valid Long as string |
| `INTERNAL` | Database error or unexpected exception | Internal server error |

---

### 3.2 GetUserRole

**Definition:**
```protobuf
rpc GetUserRole(GetUserRoleRequest) returns (GetUserRoleResponse);
```

**Request:**
```protobuf
message GetUserRoleRequest {
  string user_id = 1;
}
```

**Response:**
```protobuf
message GetUserRoleResponse {
  UserRole role = 1;  // ADMIN = 0, LECTURER = 1, STUDENT = 2
}
```

**Business Purpose:**
- Verify user's system role for authorization decisions
- Used by Project-Config Service to validate ADMIN role for privileged operations
- Used by User-Group Service to validate LECTURER role

**Called by:**
- User-Group Service (lecturer validation)
- Project-Config Service (admin authorization)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `NOT_FOUND` | User does not exist | User not found in database |
| `INVALID_ARGUMENT` | Invalid user ID format | User ID must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

---

### 3.3 VerifyUserExists

**Definition:**
```protobuf
rpc VerifyUserExists(VerifyUserRequest) returns (VerifyUserResponse);
```

**Request:**
```protobuf
message VerifyUserRequest {
  string user_id = 1;
}
```

**Response:**
```protobuf
message VerifyUserResponse {
  bool exists = 1;
  bool active = 2;     // true if status == ACTIVE
  string message = 3;  // Descriptive message
}
```

**Possible response combinations:**
- `exists=true, active=true`: "User exists and is active"
- `exists=true, active=false`: "User exists but not active" (LOCKED status)
- `exists=false, active=false`: "User not found"

**Business Purpose:**
- Quick validation that a user exists and has ACTIVE status
- Used before creating associations (e.g., assigning lecturer to group)
- Prevents invalid foreign key references across services

**Called by:**
- User-Group Service (lecturer validation, member addition)
- Project-Config Service (user validation)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `INVALID_ARGUMENT` | Invalid user ID format | User ID must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

**Note:** This RPC returns success (status=OK) even if user not found. Check `exists` field in response.

---

### 3.4 GetUsers (Batch)

**Definition:**
```protobuf
rpc GetUsers(GetUsersRequest) returns (GetUsersResponse);
```

**Request:**
```protobuf
message GetUsersRequest {
  repeated string user_ids = 1;  // List of user IDs
}
```

**Response:**
```protobuf
message GetUsersResponse {
  repeated GetUserResponse users = 1;
}
```

**Business Purpose:**
- Batch fetch user information to avoid N+1 query problems
- Used by User-Group Service when listing group members
- Optimizes performance for displaying user details in lists

**Called by:**
- User-Group Service (batch fetch members for group list)

**Behavior:**
- Returns only users that exist and are not soft-deleted
- If some user IDs don't exist, they are silently omitted from response
- Empty list returned if no users found

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `INVALID_ARGUMENT` | Any user ID has invalid format | All user IDs must be valid Long as string |
| `INTERNAL` | Database error | Internal server error |

---

### 3.5 UpdateUser

**Definition:**
```protobuf
rpc UpdateUser(UpdateUserRequest) returns (UpdateUserResponse);
```

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
  GetUserResponse user = 1;  // Updated user object
}
```

**Business Purpose:**
- **Proxy Pattern Implementation (UC22)**
- Allows User-Group Service to expose "Update Profile" endpoint without direct Identity Service dependency
- Maintains single source of truth for user data in Identity Service
- Enables future audit logging centralized in Identity Service

**Called by:**
- User-Group Service (proxying `PUT /api/users/{id}` REST endpoint)

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `NOT_FOUND` | User does not exist or is soft-deleted | User not found in database |
| `INVALID_ARGUMENT` | Invalid user ID or empty full_name | Validation failed |
| `INTERNAL` | Database error | Internal server error |

---

### 3.6 ListUsers

**Definition:**
```protobuf
rpc ListUsers(ListUsersRequest) returns (ListUsersResponse);
```

**Request:**
```protobuf
message ListUsersRequest {
  int32 page = 1;     // Page number (0-indexed)
  int32 size = 2;     // Page size
  string status = 3;  // Filter: "ACTIVE" or "LOCKED" (optional)
  string role = 4;    // Filter: "ADMIN", "LECTURER", "STUDENT" (optional)
}
```

**Response:**
```protobuf
message ListUsersResponse {
  repeated GetUserResponse users = 1;
  int64 total_elements = 2;
}
```

**Business Purpose:**
- Support paginated user listing with filtering
- Used for admin interfaces to browse users
- Enables filtering by status and role

**Called by:**
- User-Group Service (when proxying admin user list endpoint)
- Future admin dashboards

**Error Mapping:**
| gRPC Status | Condition | Description |
|-------------|-----------|-------------|
| `INVALID_ARGUMENT` | Invalid page/size or unknown status/role | Validation failed |
| `INTERNAL` | Database error | Internal server error |

---

## 4. Consumed gRPC Services

**Identity Service does NOT consume any gRPC services.**

It is fully autonomous and has no dependencies on other internal microservices. This design ensures:
- ✅ High availability (no circular dependencies)
- ✅ Fast startup (no service discovery required)
- ✅ Single source of truth for user data

---

## 5. Service Dependencies

### Consumers of Identity Service gRPC API

1. **User-Group Service** (`localhost:9091` or `identity-service:9091`)
   - RPCs used: `GetUser`, `GetUserRole`, `VerifyUserExists`, `GetUsers`, `UpdateUser`
   - Configuration:
     ```yaml
     grpc:
       client:
         identity-service:
           address: static://identity-service:9091
     ```

2. **Project-Config Service** (planned)
   - RPCs used: `VerifyUserExists`, `GetUserRole`
   - Purpose: Validate users before config operations

---

## 6. Error Handling Strategy

### Standard gRPC Status Codes

Identity Service uses standard gRPC status codes following best practices:

| gRPC Status | HTTP Equivalent | Use Case |
|-------------|-----------------|----------|
| `OK` | 200 | Successful operation |
| `INVALID_ARGUMENT` | 400 | Invalid request format |
| `NOT_FOUND` | 404 | User does not exist |
| `PERMISSION_DENIED` | 403 | Authorization failed (future use) |
| `INTERNAL` | 500 | Database error or unexpected exception |
| `UNAVAILABLE` | 503 | Service temporarily unavailable |

### Client-Side Error Handling

Consuming services should map gRPC exceptions to HTTP exceptions:

```java
try {
    GetUserResponse response = identityServiceClient.getUser(userId);
} catch (StatusRuntimeException e) {
    switch (e.getStatus().getCode()) {
        case NOT_FOUND:
            throw new ResourceNotFoundException("User not found");
        case INVALID_ARGUMENT:
            throw new BadRequestException("Invalid user ID");
        case UNAVAILABLE:
            throw new ServiceUnavailableException("Identity Service unavailable");
        case DEADLINE_EXCEEDED:
            throw new GatewayTimeoutException("Identity Service timeout");
        default:
            throw new RuntimeException("Identity Service error: " + e.getMessage());
    }
}
```

---

## 7. Performance Considerations

### Timeouts

**Default timeout:** 5 seconds (configured in client)

**Recommendations:**
- `GetUser`, `GetUserRole`, `VerifyUserExists`: 2 seconds (single query)
- `GetUsers` (batch): 5 seconds (may fetch multiple users)
- `ListUsers`: 10 seconds (pagination query with filters)

### Retry Strategy

**Recommended retry policy:**
- Retry on: `UNAVAILABLE`, `DEADLINE_EXCEEDED`
- Max retries: 3
- Backoff: Exponential (100ms, 200ms, 400ms)

**Do NOT retry:**
- `NOT_FOUND` - User genuinely doesn't exist
- `INVALID_ARGUMENT` - Request is malformed
- `INTERNAL` - May indicate data corruption (investigate first)

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
# 1. GetUser
grpcurl -plaintext -d '{"user_id": "1"}' \
  localhost:9091 UserGrpcService/GetUser

# 2. VerifyUserExists
grpcurl -plaintext -d '{"user_id": "2"}' \
  localhost:9091 UserGrpcService/VerifyUserExists

# 3. GetUsers (batch)
grpcurl -plaintext -d '{"user_ids": ["1", "2", "3"]}' \
  localhost:9091 UserGrpcService/GetUsers

# 4. ListUsers
grpcurl -plaintext -d '{"page": 0, "size": 10, "role": "STUDENT"}' \
  localhost:9091 UserGrpcService/ListUsers
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
- **[Database Design](DATABASE.md)** - Schema definitions
- **[Security Design](SECURITY.md)** - JWT authentication
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions
- **Proto File:** `identity-service/src/main/proto/user_service.proto`

---

**Maintained by:** Backend Team  
**Contact:** See project README for team contacts
