# Security Design - Identity Service

**Version:** 1.0  
**Last Updated:** February 9, 2026  
**Security Model:** Stateless JWT + Refresh Token Rotation

---

## Quick Reference

| Aspect | Specification |
|--------|--------------|
| **JWT Algorithm** | HS256 (HMAC-SHA256) |
| **Access Token TTL** | 15 minutes |
| **Refresh Token TTL** | 7 days |
| **Password Hashing** | BCrypt, strength 10 |
| **Session Model** | Stateless (JWT-based) |
| **Multi-device** | Supported (multiple refresh tokens per user) |

---

## 1. JWT Specification

### Token Types

**Access Token (JWT):**
- Algorithm: HS256
- Lifetime: 15 minutes
- Self-contained (no database lookup needed)
- Passed in `Authorization: Bearer <token>` header

**Refresh Token:**
- Format: Opaque UUID (256-bit random)
- Lifetime: 7 days  
- Stored in database (`refresh_tokens` table)
- Used to obtain new access tokens

### Access Token Claims

```json
{
  "sub": "1",
  "email": "admin@example.com",
  "roles": ["ADMIN"],
  "token_type": "ACCESS",
  "iat": 1706612400,
  "exp": 1706613300
}
```

| Claim | Description | Example |
|-------|-------------|---------|
| `sub` | User ID | `"1"` |
| `email` | User email | `"admin@example.com"` |
| `roles` | Array of roles (NO `ROLE_` prefix) | `["ADMIN"]` |
| `token_type` | Fixed value | `"ACCESS"` |
| `iat` | Issued at (Unix timestamp) | `1706612400` |
| `exp` | Expiration (Unix timestamp) | `1706613300` |

**⚠️ CRITICAL:** Do NOT include `ROLE_` prefix in JWT claims. Spring Security adds the prefix internally.

---

## 2. Authentication Flow

### Registration (Self-service for STUDENT only)

**Endpoint:** `POST /api/auth/register`

**Security Checks:**
1. ✅ Email uniqueness (database `UNIQUE` constraint)
2. ✅ Password strength validation (regex)
3. ✅ Role restricted to `STUDENT` only
4. ✅ Password hashed with BCrypt (strength 10)
5. ✅ No password returned in response

**Validation Rules:**

| Field | Rule | Regex/Constraint |
|-------|------|------------------|
| **Email** | RFC 5322 format, max 255 chars | `@Email` + `@Size(max=255)` |
| **Password** | 8-128 chars, 1 uppercase, 1 lowercase, 1 digit, 1 special (`@$!%*?&`) | `^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&]).{8,128}$` |
| **Full Name** | 2-100 chars, Unicode letters + space/hyphen | `^[\p{L}\s\-]{2,100}$` |
| **Role** | Must be `STUDENT` | `^STUDENT$` |

**Response:**
```json
{
  "user": {
    "id": 1,
    "email": "student@example.com",
    "fullName": "John Doe",
    "role": "STUDENT",
    "status": "ACTIVE"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

---

### Login

**Endpoint:** `POST /api/auth/login`

**Security Checks:**
1. ✅ Email lookup (case-sensitive)
2. ✅ Password verification with BCrypt
3. ✅ Account status check (reject if `LOCKED`)
4. ✅ Generic error message (no user enumeration)
5. ✅ Audit log created (IP, user agent, timestamp)

**Error Handling:**

| Scenario | HTTP Status | Error Message |
|----------|------------|---------------|
| Email not found | 401 Unauthorized | "Invalid credentials" |
| Password incorrect | 401 Unauthorized | "Invalid credentials" |
| Account locked | 403 Forbidden | "Account is locked. Contact administrator." |

**⚠️ Security Note:** Use same error message for "email not found" and "password incorrect" to prevent user enumeration.

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "user": { ... }
}
```

---

### Token Refresh (Rotation)

**Endpoint:** `POST /api/auth/refresh`

**Security Checks:**
1. ✅ Refresh token exists in database
2. ✅ Token not revoked (`revoked = false`)
3. ✅ Token not expired (`expiresAt > now`)
4. ✅ **User account status check (CRITICAL)** - Ensure user not locked
5. ✅ **Old token revoked immediately** (set `revoked = true`)
6. ✅ New refresh token generated and persisted
7. ✅ New access token issued

**🔴 CRITICAL SECURITY REQUIREMENT:**

During token refresh, the service MUST check the user's account status:

```java
// REQUIRED CHECK:
User user = refreshToken.getUser();
if (user.getStatus() == User.Status.LOCKED) {
    revokeAllByUser(user);  // Revoke ALL tokens
    throw new AccountLockedException("Account is locked");
}
```

**Rationale:** Without this check, a locked user can still obtain new access tokens using a refresh token issued before the lock. This bypasses the account lockout mechanism.

**Reuse Detection:**

If a **revoked** refresh token is reused:
- ⚠️ **Revoke ALL refresh tokens** for that user
- 🔴 Force re-login
- Rationale: Indicates potential token theft

**Response:**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "660e8400-e29b-41d4-a716-446655440001"
}
```

---

### Logout

**Endpoint:** `POST /api/auth/logout`

**Actions:**
1. Extract refresh token from request
2. Mark token as revoked in database
3. Client discards access token (stateless, no server action needed)

**Response:** `204 No Content`

---

## 3. Authorization

### Role Hierarchy

| Role | Permissions | Can Self-Register? |
|------|-------------|-------------------|
| **ADMIN** | Full system access, user management, audit logs | ❌ No (created by admin only) |
| **LECTURER** | Manage groups, project configs | ❌ No (created by admin only) |
| **STUDENT** | Limited access to own data | ✅ Yes (self-registration) |

### Access Control Mechanisms

**REST API:**
```java
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")  // ← Use plain role name
public class AdminController {
    
    @GetMapping("/users")
    public ResponseEntity<?> getAllUsers() {
        // Only ADMIN can access
    }
}
```

**Programmatic Check:**
```java
@Service
public class UserService {
    
    public void updateUser(Long userId, UpdateRequest request) {
        User currentUser = SecurityContextHelper.getCurrentUser();
        
        if (currentUser.getRole() != Role.ADMIN && 
            !currentUser.getId().equals(userId)) {
            throw new ForbiddenException("Cannot update other users");
        }
        
        // Proceed with update
    }
}
```

**gRPC API:**
- ❌ No authorization (internal trusted traffic only)
- Assumes caller service (e.g., User-Group Service) has already validated request
- All gRPC methods bypass soft delete filter (use `findByIdIgnoreDeleted()`)

---

## 4. Password Security

### Hashing

**Algorithm:** BCrypt  
**Strength:** 10 (2^10 = 1024 iterations)  
**Salt:** Automatically generated per password (embedded in hash)

**Configuration:**
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder(10);
}
```

**Hash Example:**
```
$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
|||   |||                      Salt (22 chars)                 |Hash (31 chars)|
||    |└─ Cost factor (10)
||    └─ BCrypt version (2a)
|└─ Algorithm identifier
```

### Password Validation Rules

**Regex:**
```regex
^(?=.*[A-Z])(?=.*[a-z])(?=.*\d)(?=.*[@$!%*?&]).{8,128}$
```

**Requirements:**
- ✅ 8-128 characters
- ✅ At least 1 uppercase letter (A-Z)
- ✅ At least 1 lowercase letter (a-z)
- ✅ At least 1 digit (0-9)
- ✅ At least 1 special character (`@$!%*?&`)

**Examples:**
- ✅ `MyP@ssw0rd` - Valid
- ✅ `Test1234!` - Valid
- ❌ `password` - Missing uppercase, digit, special char
- ❌ `PASSWORD` - Missing lowercase, digit, special char
- ❌ `Pass123` - Missing special char
- ❌ `Pass@` - Too short (< 8 chars)

---

## 5. JWT Security

### Signing Key Management

**Environment Variable:**
```bash
JWT_SECRET=your-256-bit-secret-key-here-min-43-chars
```

**⚠️ CRITICAL REQUIREMENTS:**
1. Minimum length: **43 characters** (256 bits for HS256)
2. High entropy (use random generator, not dictionary words)
3. **Must be identical** across all services (Gateway, Identity, User-Group)
4. Never commit to version control (use `.env` or secrets manager)

**Startup Validation:**

The system validates JWT secret at startup:
- ✅ Not null/empty
- ✅ Length ≥ 43 characters
- ✅ Not in known insecure defaults list
- ⚠️ Warns if weak (lowercase only, no special chars)

**Insecure Examples (DO NOT USE):**
- ❌ `secret` - Too short, too weak
- ❌ `mySecretKey123` - Too short, predictable
- ❌ `your-256-bit-secret-key-here-min-43-chars` - Example from docs

**Secure Example:**
- ✅ `7Kf!9mP#qR2&tU$vW8xY*zAB3cD5eF@gH1iJ4kL6nM0oP` (50 chars, mixed case, numbers, symbols)

### Token Transmission

**REST API:**
```http
GET /api/users/profile HTTP/1.1
Host: localhost:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**❌ DO NOT:**
- Send token in URL query parameters (logged in server logs)
- Store token in localStorage if XSS risk exists (prefer httpOnly cookie)
- Include sensitive data in JWT payload (it's only base64-encoded, not encrypted)

**✅ DO:**
- Use `Authorization: Bearer <token>` header
- Use HTTPS in production
- Set short expiration (15 minutes)
- Implement refresh token rotation

---

## 6. Account Lifecycle Security

### Soft Delete

**Behavior:**
- User not physically deleted from database
- `deleted_at` timestamp set to current time
- `deleted_by` field records admin who performed deletion
- Standard JPA queries automatically exclude soft-deleted users (via `@SQLRestriction`)

**Security Implications:**
1. ✅ Audit trail preserved (who deleted, when)
2. ✅ Can restore user if needed
3. ⚠️ Email remains unique (cannot re-register same email until restored or hard deleted)
4. ✅ All refresh tokens revoked on soft delete

**gRPC Bypass:**
```java
// gRPC service MUST bypass soft delete filter
@Query("SELECT u FROM User u WHERE u.id = :id")
Optional<User> findByIdIgnoreDeleted(@Param("id") Long id);
```

### Account Locking

**Trigger:** Admin sets `status = LOCKED`

**Effects:**
1. ✅ Cannot login (rejected at password validation step)
2. ✅ Existing access tokens remain valid until expiration (stateless JWT limitation)
3. ✅ Cannot refresh tokens (status checked during refresh)
4. ✅ All refresh tokens revoked when locked

**Unlock:** Admin sets `status = ACTIVE`, user can login again

---

## 7. Audit Logging

### Events Logged

| Event | Trigger | Fields Captured |
|-------|---------|-----------------|
| **Login Success** | Successful authentication | User ID, email, IP, user agent, timestamp |
| **Login Failure** | Invalid credentials | Email (attempted), IP, user agent, timestamp, reason |
| **User Created** | Admin creates user | New user ID, creator admin ID, timestamp |
| **User Updated** | Admin updates user | User ID, admin ID, old values, new values, timestamp |
| **User Deleted** | Admin soft deletes user | User ID, admin ID, timestamp |
| **User Restored** | Admin restores user | User ID, admin ID, timestamp |
| **Account Locked** | Admin locks account | User ID, admin ID, reason (optional), timestamp |
| **Account Unlocked** | Admin unlocks account | User ID, admin ID, timestamp |

### Audit Log Schema

```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    entity_type VARCHAR(100) NOT NULL,      -- 'USER', 'REFRESH_TOKEN', etc.
    entity_id BIGINT,                       -- User ID
    action VARCHAR(100) NOT NULL,           -- 'LOGIN_SUCCESS', 'CREATE_USER', etc.
    actor_id BIGINT,                        -- Admin who performed action
    actor_email VARCHAR(255),               -- Denormalized for performance
    timestamp TIMESTAMP NOT NULL,           -- When action occurred
    ip_address VARCHAR(45),                 -- IPv4/IPv6
    user_agent VARCHAR(500),                -- Browser/client info
    old_value TEXT,                         -- JSON snapshot before change
    new_value TEXT,                         -- JSON snapshot after change
    outcome VARCHAR(50) NOT NULL            -- 'SUCCESS', 'FAILURE', 'DENIED'
);
```

**Retention:** Audit logs are **immutable** and **never deleted** (compliance requirement).

---

## 8. Security Best Practices

### ✅ Implemented

- [x] Passwords hashed with BCrypt (strength 10)
- [x] JWT signed with HS256 (secret key validation at startup)
- [x] Refresh token rotation (old token revoked immediately)
- [x] Reuse detection (revoke all tokens on suspicious activity)
- [x] Account status check during refresh (prevent locked user bypass)
- [x] Generic error messages (prevent user enumeration)
- [x] Soft delete (preserve audit trail)
- [x] Comprehensive audit logging
- [x] Bean Validation on all DTOs
- [x] No password leakage in responses
- [x] Role format consistency (no `ROLE_` prefix in DB/JWT)

### 🔄 Recommended Enhancements (Future)

- [ ] **Rate Limiting:** Limit login attempts per IP (prevent brute force)
- [ ] **CAPTCHA:** Add after N failed login attempts
- [ ] **2FA/MFA:** Two-factor authentication for admin accounts
- [ ] **Password History:** Prevent reuse of last N passwords
- [ ] **Session Management Dashboard:** Show active sessions to user
- [ ] **IP Whitelist:** Restrict admin access to known IPs
- [ ] **JWT Blacklist:** For immediate token revocation (requires Redis)
- [ ] **Automated Account Lockout:** Lock after N failed login attempts
- [ ] **Password Expiration:** Force password change every 90 days
- [ ] **HTTPS Only:** Enforce secure transport (production)

---

## 9. Security Testing

### Unit Tests

```bash
cd identity-service
mvn test -Dtest=AuthServiceTest
mvn test -Dtest=JwtServiceTest
mvn test -Dtest=RefreshTokenServiceTest
```

### Integration Tests

```bash
# Test full auth flow
mvn verify -Dit.test=AuthControllerIT

# Test with locked account
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"locked@test.com","password":"Test1234!"}'
# Expected: 403 Forbidden

# Test token refresh after account lock
# 1. Login normally
# 2. Admin locks account
# 3. Try to refresh token
# Expected: 403 Forbidden, all tokens revoked
```

### Security Audit Checklist

- [ ] JWT secret ≥ 43 characters, high entropy
- [ ] BCrypt strength = 10 (not lower)
- [ ] Access token TTL = 15 minutes (not longer)
- [ ] Refresh token TTL = 7 days (not longer)
- [ ] Account status checked during token refresh
- [ ] Generic error messages for auth failures
- [ ] Passwords never logged or returned in responses
- [ ] Audit logs capture all security events
- [ ] Soft delete preserves audit trail
- [ ] Role format consistent (no `ROLE_` prefix in DB/JWT)

---

## 10. Security Incident Response

### Suspected Token Theft

**Actions:**
1. Identify affected user (from JWT `sub` claim or audit logs)
2. Revoke all refresh tokens: `DELETE FROM refresh_tokens WHERE user_id = ?`
3. Lock account: `UPDATE users SET status = 'LOCKED' WHERE id = ?`
4. Notify user (via email/notification service)
5. Investigate audit logs for suspicious activity

### Compromised JWT Secret

**Actions:**
1. ⚠️ **CRITICAL EMERGENCY:** All issued JWTs become invalid
2. Generate new secret (50+ chars, high entropy)
3. Update `JWT_SECRET` in all services (Gateway, Identity, User-Group)
4. Restart all services
5. Force all users to re-login (revoke all refresh tokens)
6. Investigate how secret was compromised

### Brute Force Attack Detected

**Actions:**
1. Check audit logs for repeated failures from same IP
2. Manually block IP at firewall/load balancer level
3. Implement rate limiting (see Future Enhancements)
4. Add CAPTCHA to login form

---

## References

- **Deprecated Docs (merged into this file):**
  - [Authentication-Authorization-Design.md](Authentication-Authorization-Design.md)
  - [Security-Review.md](Security-Review.md)
- **Related Docs:**
  - [API_CONTRACT.md](API_CONTRACT.md) - API endpoints
  - [DATABASE.md](DATABASE.md) - Schema details
  - [IMPLEMENTATION.md](IMPLEMENTATION.md) - Setup guide
- **External Standards:**
  - [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
  - [JWT Best Practices (RFC 8725)](https://datatracker.ietf.org/doc/html/rfc8725)
  - [BCrypt Algorithm](https://en.wikipedia.org/wiki/Bcrypt)

---

**Security Review:** ✅ Reviewed February 9, 2026  
**Next Review Due:** May 9, 2026 (quarterly)
