# Security Design - User-Group Service

**Version:** 1.0  
**Last Updated:** February 23, 2026  
**Security Model:** Stateless JWT Validation (Delegated Authentication)

---

## Quick Reference

| Aspect | Specification |
|--------|--------------|
| **Authentication** | Delegated to API Gateway |
| **JWT Algorithm** | HS256 (HMAC-SHA256) |
| **JWT Validation** | Signature + expiration check |
| **Session Model** | Stateless (no session storage) |
| **Authorization** | Role-based (ADMIN, LECTURER, STUDENT) |
| **CORS** | Handled by API Gateway |
| **Internal Communication** | gRPC to Identity Service |

---

## 1. Authentication Model

### Delegated JWT Validation

User-Group Service does NOT authenticate users. Authentication is performed by API Gateway.

**Flow:**
1. User authenticates via Identity Service (login)
2. Identity Service issues JWT token
3. API Gateway validates JWT signature and expiration
4. API Gateway forwards request with `Authorization` header
5. User-Group Service extracts claims from JWT (no signature validation)
6. User-Group Service sets `CurrentUser` in `SecurityContext`

**JWT Claims Used:**

```json
{
  "sub": "1",
  "email": "student@example.com",
  "roles": ["STUDENT"],
  "token_type": "ACCESS",
  "iat": 1706612400,
  "exp": 1706613300
}
```

| Claim | Usage |
|-------|-------|
| `sub` | User ID (extracted as `Long userId`) |
| `roles` | Array of roles (NO `ROLE_` prefix in JWT) |
| `email` | Not used in authorization |
| `exp` | Token expiration (validated) |

**Implementation:** `JwtAuthenticationFilter.java`

```java
// Extract claims
Long userId = jwtService.extractUserId(jwt);
List<String> roles = jwtService.extractRoles(jwt);

// Add ROLE_ prefix for Spring Security
List<SimpleGrantedAuthority> authorities = roles.stream()
    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
    .toList();

// Set authentication in SecurityContext
CurrentUser currentUser = new CurrentUser(userId, authorities);
UsernamePasswordAuthenticationToken authToken = 
    new UsernamePasswordAuthenticationToken(currentUser, null, authorities);
SecurityContextHolder.getContext().setAuthentication(authToken);
```

### Public Endpoints

The following endpoints do NOT require JWT authentication:

| Path | Purpose |
|------|---------|
| `GET /actuator/health` | Health check |
| `GET /actuator/info` | Service information |
| `GET /swagger-ui/**` | API documentation |
| `GET /v3/api-docs/**` | OpenAPI specs |

All other endpoints require valid JWT token in `Authorization: Bearer <token>` header.

---

## 2. Authorization Strategy

### Role-Based Access Control

User-Group Service enforces authorization at the service layer (NOT using `@PreAuthorize`).

**Supported Roles:**
- `ADMIN` - Full access to all operations
- `LECTURER` - Create groups, manage memberships
- `STUDENT` - View groups, manage own profile

### Authorization Rules

#### Group Management

| Operation | ADMIN | LECTURER | STUDENT |
|-----------|-------|----------|---------|
| Create Group | ✅ | ✅ | ❌ |
| List Groups | ✅ | ✅ | ✅ |
| Get Group Details | ✅ | ✅ | ✅ |
| Update Group | ✅ | ✅ (own groups) | ❌ |
| Delete Group | ✅ | ✅ (own groups) | ❌ |

**Implementation:**
```java
// Service-layer authorization check
if (!actorRoles.contains("ADMIN") && !actorRoles.contains("LECTURER")) {
    throw new ForbiddenException("Only ADMIN or LECTURER can create groups");
}
```

#### Membership Management

| Operation | ADMIN | LECTURER | GROUP LEADER |
|-----------|-------|----------|--------------|
| Add Member | ✅ | ✅ | ✅ (own group) |
| Remove Member | ✅ | ✅ | ✅ (own group) |
| Promote to Leader | ✅ | ✅ | ❌ |
| Demote Leader | ✅ | ✅ | ❌ |

**Special Case - Group Leader:**
- STUDENT with `LEADER` role in a group can add/remove members in their own group
- Verified via database query: check `user_semester_membership` table
- Cannot promote/demote other members

#### User Profile Operations

| Operation | ADMIN | LECTURER | STUDENT |
|-----------|-------|----------|---------|
| Get User Profile | All users | STUDENT role users only | Own profile only |
| Update User Profile | All users | ❌ | Own profile only (STUDENT_EDITABLE fields) |

**Authorization Logic:**

```java
// UC21: Get User Profile
private void checkGetUserAuthorization(Long targetUserId, Long actorId, 
                                       List<String> actorRoles) {
    if (actorRoles.contains("ADMIN")) {
        return; // ADMIN can view all users
    }
    
    if (actorRoles.contains("LECTURER")) {
        // LECTURER can only view STUDENT role users
        // Verified via gRPC call to Identity Service
        return;
    }
    
    if (actorRoles.contains("STUDENT")) {
        // STUDENT can only view own profile
        if (!targetUserId.equals(actorId)) {
            throw new ForbiddenException("STUDENT can only view own profile");
        }
        return;
    }
    
    throw new ForbiddenException("Insufficient permissions");
}
```

---

## 3. Validation Rules

### Group Creation

| Field | Validation |
|-------|-----------|
| `groupName` | 1-100 characters, alphanumeric + space/hyphen |
| `semesterId` | Must reference active semester in database |
| `lecturerId` | Must exist in Identity Service, role=LECTURER, status=ACTIVE |

**Lecturer Validation Flow:**
1. User-Group Service receives `lecturerId` in request
2. Makes gRPC call to Identity Service: `VerifyUserExists(lecturerId)`
3. Identity Service returns: `exists`, `role`, `status`
4. Validates: `exists == true && role == LECTURER && status == ACTIVE`
5. If validation fails, returns `400 BAD REQUEST` or `404 NOT FOUND`

### Membership Management

| Field | Validation |
|-------|-----------|
| `userId` | Must exist in Identity Service, role=STUDENT, status=ACTIVE |
| `groupId` | Must exist and not be soft-deleted |
| One group per semester | Enforced by database constraint: PK `(user_id, semester_id)` |
| One leader per group | Enforced by database constraint: unique index on `(group_id)` WHERE `group_role='LEADER'` |

---

## 4. Internal Communication

### gRPC to Identity Service

**Purpose:** Validate users and fetch user details

**Authentication:** None (trusted internal network)

**RPCs Used:**
- `VerifyUserExists(userId)` - Check if user exists and get role/status
- `GetUsers(userIds)` - Batch fetch user details for group member lists
- `UpdateUser(userId, request)` - Proxy for UC22 (Update User Profile)

**Security Concerns:**
- No authentication on gRPC calls (assumes trusted network)
- No request signing or mutual TLS
- Service-to-service communication not encrypted

**Mitigation:**
- Deploy in private network (no public access to gRPC port 9091)
- Use network policies to restrict access
- Future: Implement mutual TLS for gRPC

---

## 5. Public Endpoint Protection

### JWT Token Validation

**Filter:** `JwtAuthenticationFilter.java`

**Validation Steps:**
1. Extract `Authorization: Bearer <token>` header
2. Validate JWT signature using shared secret
3. Check expiration (`exp` claim)
4. Extract `sub` (userId) and `roles` claims
5. Set `CurrentUser` in `SecurityContext`

**Failure Behavior:**
- Invalid token → Log warning, continue without authentication
- Spring Security will return `401 UNAUTHORIZED` when accessing protected endpoint
- Handled by `JwtAuthenticationEntryPoint`

**Response on Auth Failure (401):**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Full authentication is required to access this resource"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**Response on Access Denied (403):**
```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "You do not have permission to access this resource"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

---

## 6. CORS Policy

CORS is NOT configured in User-Group Service.

**Rationale:**
- API Gateway handles all CORS configuration
- Frontend only communicates with API Gateway (port 8080)
- User-Group Service is NOT directly accessible from browsers

**If Direct Access Required:**
```java
// NOT IMPLEMENTED - for reference only
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("http://localhost:3000"));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE"));
    configuration.setAllowedHeaders(List.of("*"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

---

## 7. Security Limitations

### Known Gaps

**1. No Service-to-Service Authentication**
- gRPC calls to Identity Service are unauthenticated
- Assumes trusted network environment
- Risk: If network is compromised, attacker can call Identity Service directly

**2. No Audit Logging**
- Security events (unauthorized access, permission denied) are logged via SLF4J
- No centralized audit trail database
- Logs may be lost if not aggregated to external system (ELK, CloudWatch)

**3. No Rate Limiting**
- No per-user or per-IP rate limiting
- Risk: Brute force attacks, DoS attacks
- Mitigation: API Gateway implements rate limiting

**4. No Request Signing**
- JWT token is validated but request body is not signed
- Risk: Man-in-the-middle can modify request body after JWT validation
- Mitigation: Use HTTPS/TLS in production

**5. No Multi-Device Session Management**
- JWT is stateless, no way to revoke token before expiration
- If user account is compromised, attacker can use token until expiration (15 minutes)
- Mitigation: Short token TTL (15 minutes), refresh token rotation in Identity Service

**6. No Field-Level Authorization**
- User profile update (UC22) restricts STUDENT to editable fields via `STUDENT_EDITABLE_FIELDS` list
- No fine-grained permission system (e.g., "can edit fullName but not role")

**7. No CSRF Protection**
- CSRF disabled (`csrf().disable()`)
- Acceptable for stateless JWT API (no cookies)
- If session cookies are added in future, CSRF protection must be enabled

---

## 8. Best Practices

### For Developers

**1. Always Extract Current User from SecurityContext:**
```java
@Service
public class GroupService {
    
    public void createGroup(CreateGroupRequest request) {
        // Get current user from SecurityContext
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        CurrentUser currentUser = (CurrentUser) auth.getPrincipal();
        Long actorId = currentUser.getUserId();
        List<String> actorRoles = currentUser.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .map(role -> role.replace("ROLE_", ""))
            .toList();
        
        // Use actorId and actorRoles for authorization checks
    }
}
```

**2. Never Trust User Input:**
- Validate all input fields (size, format, allowed values)
- Use `@Valid` annotation with custom validators
- Sanitize input to prevent SQL injection (JPA handles this automatically)

**3. Log Security Events:**
```java
log.warn("Unauthorized access attempt: userId={}, targetResource={}", 
    actorId, groupId);
```

**4. Use Parameterized Queries:**
- Always use JPA repositories (prevents SQL injection)
- Avoid native queries with string concatenation
- If native query is required, use `@Query` with named parameters

**5. Never Log Sensitive Data:**
```java
// ❌ BAD - logs JWT token
log.info("Request with token: {}", request.getHeader("Authorization"));

// ✅ GOOD - logs only metadata
log.info("Authenticated request from userId={}", currentUser.getUserId());
```

### For Operators

**1. Protect JWT Secret:**
- Store in environment variable: `JWT_SECRET`
- Never commit to version control
- Rotate secret periodically (requires re-authentication of all users)

**2. Deploy in Private Network:**
- User-Group Service should NOT be directly accessible from internet
- Only API Gateway should be publicly accessible
- Use network policies to restrict service-to-service communication

**3. Enable TLS:**
- Use HTTPS in production (handled by API Gateway)
- Consider mutual TLS for gRPC communication

**4. Monitor Security Logs:**
- Aggregate logs to centralized system (ELK, CloudWatch, Splunk)
- Set up alerts for:
  - High rate of 401/403 responses
  - Repeated authorization failures from same user
  - Unusual access patterns

**5. Regular Security Audits:**
- Review access logs monthly
- Check for orphaned accounts (users not in Identity Service but referenced in groups)
- Validate that soft-deleted users cannot access system

---

## References

- **[API Contract](API_CONTRACT.md)** - Endpoint-level authorization rules
- **[Implementation Guide](IMPLEMENTATION.md)** - JWT secret configuration
- **[Identity Service Security](../Identity_Service/SECURITY.md)** - JWT generation and validation
- **[API Gateway Security](../api-gateway/SECURITY.md)** - Gateway-level security (CORS, rate limiting)

---

**Questions?** Contact Backend Security Team
