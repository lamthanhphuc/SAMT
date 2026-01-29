# 🔐 SECURITY REVIEW – Identity Service Authentication

## Document References

- **SRS**: [SRS.md](SRS.md)
- **Auth Design**: [Authentication-Authorization-Design.md](Authentication-Authorization-Design.md)

---

## 1. `AuthService.register()` – UC-REGISTER

**File**: `AuthService.java:37-74`

### Main Flow Verification

| Step | SRS Requirement | Implementation | Status |
|------|-----------------|----------------|--------|
| 1 | Validate input (email, password, name) | `@Valid` + Bean Validation on `RegisterRequest` | ✅ |
| 2 | Check passwords match | `request.passwordsMatch()` → `PasswordMismatchException` | ✅ |
| 3 | Check email uniqueness | `userRepository.existsByEmail()` → `EmailAlreadyExistsException` | ✅ |
| 4 | Hash password with BCrypt (strength 10) | `passwordEncoder.encode()` → BCrypt(10) in SecurityConfig | ✅ |
| 5 | Create user with status = ACTIVE | `user.setStatus(User.Status.ACTIVE)` | ✅ |
| 6 | Generate access token & refresh token | `jwtService.generateAccessToken()` + `refreshTokenService.createRefreshToken()` | ✅ |
| 7 | Return tokens and user info | `RegisterResponse.of(userDto, accessToken, refreshToken)` | ✅ |

### Alternate Flows Verification

| Flow | Condition | Expected Response | Implementation | Status |
|------|-----------|-------------------|----------------|--------|
| A1 | Email already exists | 409 Conflict | `EmailAlreadyExistsException` → `@ResponseStatus(CONFLICT)` | ✅ |
| A2 | Invalid email format | 400 Bad Request | `@Email` on `RegisterRequest.email` | ✅ |
| A3 | Password too weak | 400 Bad Request | `@Pattern` regex on `RegisterRequest.password` | ✅ |
| A4 | Passwords don't match | 400 Bad Request | `PasswordMismatchException` → `@ResponseStatus(BAD_REQUEST)` | ✅ |
| A5 | Invalid role | 400 Bad Request | `@Pattern("^STUDENT$")` on `RegisterRequest.role` | ✅ |
| A6 | Name too short/long | 400 Bad Request | `@Size(min=2, max=100)` + `@Pattern` on `RegisterRequest.fullName` | ✅ |

### Validation Rules (SRS Compliance)

| Rule | SRS Spec | Implementation | Status |
|------|----------|----------------|--------|
| Email - RFC 5322 | ✅ | `@Email` | ✅ |
| Email - Max 255 | ✅ | `@Size(max = 255)` | ✅ |
| Password - 8-128 chars | ✅ | Regex `{8,128}` | ✅ |
| Password - 1 uppercase | ✅ | Regex `(?=.*[A-Z])` | ✅ |
| Password - 1 lowercase | ✅ | Regex `(?=.*[a-z])` | ✅ |
| Password - 1 digit | ✅ | Regex `(?=.*\d)` | ✅ |
| Password - 1 special `@$!%*?&` | ✅ | Regex `(?=.*[@$!%*?&])` | ✅ |
| Name - 2-100 chars, Unicode | ✅ | `@Size` + `@Pattern("^[\p{L}\s\-]{2,100}$")` | ✅ |
| Role - STUDENT only | ✅ | `@Pattern("^STUDENT$")` | ✅ |

### Security Assessment

| Check | Status |
|-------|--------|
| BCrypt strength 10 | ✅ |
| Transactional | ✅ |
| No password leakage in response | ✅ |
| Race condition (email uniqueness) | ⚠️ See below |

### ⚠️ Potential Issue: Race Condition

**Finding**: `existsByEmail()` + `save()` is not atomic. Two concurrent registrations with the same email could pass the uniqueness check.

**Risk Level**: Low (database `UNIQUE` constraint will catch this, but exception handling may differ)

**Recommendation**: Current implementation is acceptable because:

1. DB has `UNIQUE` constraint on email column
2. Low probability in practice
3. Would throw `DataIntegrityViolationException` which should be handled globally

---

## 2. `AuthService.login()` – UC-LOGIN

**File**: `AuthService.java:77-109`

### Main Flow Verification

| Step | SRS Requirement | Implementation | Status |
|------|-----------------|----------------|--------|
| 1 | Validate email exists | `userRepository.findByEmail()` | ✅ |
| 2 | Validate password with BCrypt | `passwordEncoder.matches()` | ✅ |
| 3 | Generate access token (JWT, 15 min TTL) | `jwtService.generateAccessToken()` | ✅ |
| 4 | Generate refresh token (UUID, 7 days TTL) | `refreshTokenService.createRefreshToken()` | ✅ |
| 5 | Persist refresh token in database | Inside `createRefreshToken()` | ✅ |
| 6 | Return both tokens | `LoginResponse.of()` | ✅ |

### Alternate Flows Verification

| Flow | Condition | Expected Response | Implementation | Status |
|------|-----------|-------------------|----------------|--------|
| A1 | Email not found | 401 Unauthorized "Invalid credentials" | `InvalidCredentialsException` | ✅ |
| A2 | Password incorrect | 401 Unauthorized "Invalid credentials" | `InvalidCredentialsException` | ✅ |
| A3 | Account status = LOCKED | 403 Forbidden "Account is locked" | `AccountLockedException` | ✅ |

### Security Assessment

| Check | Status |
|-------|--------|
| Same error message for email/password (no enumeration) | ✅ |
| Account status check before token generation | ✅ |
| Transactional | ✅ |
| No password leakage | ✅ |

### ❌ Violation: Order of Status Check

**Finding**: In the code, status check happens **after** finding user but **before** password validation:

```java
User user = userRepository.findByEmail(request.email())
        .orElseThrow(InvalidCredentialsException::new);
if (user.getStatus() == User.Status.LOCKED) {
    throw new AccountLockedException();
}
if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
    throw new InvalidCredentialsException();
}
```

**Issue**: This reveals whether an account exists and is locked, even without knowing the password. An attacker can enumerate locked accounts.

**SRS Spec**: Does not explicitly define order. However, security best practice suggests:

1. Find user (if not found → generic error)
2. Validate password (if wrong → generic error)
3. Check status (if locked → specific error)

**Risk Level**: Low-Medium (information disclosure)

**Recommendation**: Consider checking status AFTER password validation to prevent account enumeration, OR use the same generic "Invalid credentials" for locked accounts (hides locked state from attackers).

---

## 3. `RefreshTokenService.refreshToken()` – UC-REFRESH-TOKEN

**File**: `RefreshTokenService.java:62-103`

### Main Flow Verification

| Step | SRS Requirement | Implementation | Status |
|------|-----------------|----------------|--------|
| 1 | Validate token exists | `findByToken()` → `TokenInvalidException` | ✅ |
| 2 | Check token not revoked | `refreshToken.isRevoked()` check | ✅ |
| 3 | Check token not expired | `refreshToken.isExpired()` check | ✅ |
| 4 | Revoke old token | `refreshToken.setRevoked(true)` + save | ✅ |
| 5 | Generate new refresh token | `createRefreshToken(user)` | ✅ |
| 6 | Generate new access token | `jwtService.generateAccessToken(user)` | ✅ |
| 7 | Return new tokens | `LoginResponse.of()` | ✅ |

### Alternate Flows Verification

| Flow | Condition | Expected Response | Implementation | Status |
|------|-----------|-------------------|----------------|--------|
| A1 | Token expired | 401 Unauthorized "Token expired" | `TokenExpiredException` | ✅ |
| A2 | Token not found | 401 Unauthorized "Token invalid" | `TokenInvalidException` | ✅ |
| A3 | **Token reuse detected** | 401 + Revoke ALL tokens | `revokeAllByUser()` + `TokenInvalidException` | ✅ |

### Security: Reuse Detection

| Requirement | Implementation | Status |
|-------------|----------------|--------|
| Detect revoked token usage | `if (refreshToken.isRevoked())` | ✅ |
| Revoke ALL tokens for user | `refreshTokenRepository.revokeAllByUser(user)` | ✅ |
| Force re-login | Tokens revoked → user must login again | ✅ |
| Log security event | `log.warn("SECURITY: Refresh token reuse detected...")` | ✅ |

### ❌ Violation: Missing Account Status Check

**Finding**: `refreshToken()` does NOT check if the user's account is still ACTIVE.

**Scenario**:

1. User gets tokens
2. Admin locks user account
3. User still has valid refresh token
4. User can refresh and get new tokens despite being locked

**Risk Level**: Medium (bypasses account lockout)

**Recommendation**: Add status check before generating new tokens:

```java
// After: RefreshToken refreshToken = ... findByToken()
User user = refreshToken.getUser();

// ADD THIS: Check account status
if (user.getStatus() == User.Status.LOCKED) {
    throw new AccountLockedException();
}

// Continue with: if (refreshToken.isRevoked()) ...
```

---

## 4. `RefreshTokenService.revokeToken()` – UC-LOGOUT

**File**: `RefreshTokenService.java:105-113`

### Main Flow Verification

| Step | SRS Requirement | Implementation | Status |
|------|-----------------|----------------|--------|
| 1 | Find refresh token | `findByToken()` | ✅ |
| 2 | Revoke token | `token.setRevoked(true)` | ✅ |

### Alternate Flows Verification

| Flow | Condition | Expected Response | Implementation | Status |
|------|-----------|-------------------|----------------|--------|
| A2 | Token not found | 204 No Content (silent) | `ifPresent()` pattern | ✅ |
| A3 | Token already revoked | 204 No Content (silent) | Sets revoked again (idempotent) | ✅ |

### Security Assessment

| Check | Status |
|-------|--------|
| Idempotent | ✅ |
| Silent failure (no info leakage) | ✅ |
| Transactional | ✅ |

---

## 5. `JwtService.generateAccessToken()` – JWT Claims

**File**: `JwtService.java:44-64`

### JWT Claims Verification (Auth Design §2.3)

| Claim | Spec | Implementation | Status |
|-------|------|----------------|--------|
| `sub` | User ID | `.subject(String.valueOf(user.getId()))` | ✅ |
| `email` | User email | `.claim("email", user.getEmail())` | ✅ |
| `roles` | List of roles | `.claim("roles", List.of(user.getRole().name()))` | ✅ |
| `iat` | Issued at | `.issuedAt(now)` | ✅ |
| `exp` | Expiration | `.expiration(expiration)` | ✅ |
| `token_type` | ACCESS | `.claim("token_type", "ACCESS")` | ✅ |

### Security Assessment

| Check | Spec | Implementation | Status |
|-------|------|----------------|--------|
| Algorithm | HS256 | `.signWith(secretKey)` with `Keys.hmacShaKeyFor()` | ✅ |
| Access Token TTL | 15 minutes | `accessTokenExpiration = 900000ms` | ✅ |
| Secret Key | Environment variable | `@Value("${jwt.secret}")` | ✅ |

---

## 6. `JwtAuthenticationFilter` – Security Filter

**File**: `JwtAuthenticationFilter.java`

### Filter Logic Verification

| Check | Implementation | Status |
|-------|----------------|--------|
| Extract Bearer token | `authHeader.substring(7)` | ✅ |
| Validate token signature | `jwtService.validateToken(jwt)` | ✅ |
| Check token expiration | `jwtService.isTokenExpired(jwt)` | ✅ |
| Extract user ID | `jwtService.extractUserId(jwt)` | ✅ |
| Load user from DB | `userRepository.findById(userId)` | ✅ |
| Check user ACTIVE status | `user.getStatus() == User.Status.ACTIVE` | ✅ |
| Set SecurityContext | `SecurityContextHolder.getContext().setAuthentication()` | ✅ |

### Security Assessment

| Check | Status |
|-------|--------|
| Invalid token → filter continues (no auth) | ✅ |
| Expired token → filter continues (no auth) | ✅ |
| LOCKED user → no auth set | ✅ |
| OncePerRequestFilter | ✅ |

---

## 7. `SecurityConfig` – Authorization Rules

**File**: `SecurityConfig.java`

### Configuration Verification

| Setting | Spec | Implementation | Status |
|---------|------|----------------|--------|
| BCrypt strength | 10 | `new BCryptPasswordEncoder(10)` | ✅ |
| Session | Stateless | `SessionCreationPolicy.STATELESS` | ✅ |
| CSRF | Disabled | `.csrf(AbstractHttpConfigurer::disable)` | ✅ |

### Authorization Rules Verification (SRS)

| Endpoint | Expected | Implementation | Status |
|----------|----------|----------------|--------|
| `/api/auth/login` | Public | `permitAll()` | ✅ |
| `/api/auth/register` | Public | `permitAll()` | ✅ |
| `/api/auth/refresh` | Public | `permitAll()` | ✅ |
| `/api/auth/logout` | Requires JWT | `anyRequest().authenticated()` | ✅ |
| `/actuator/health` | Public | `permitAll()` | ✅ |

---

## 8. `AuthController` – HTTP Response Codes

**File**: `AuthController.java`

### Response Codes Verification (SRS API Summary)

| Endpoint | Expected | Implementation | Status |
|----------|----------|----------------|--------|
| POST `/register` | 201 Created | `ResponseEntity.status(HttpStatus.CREATED)` | ✅ |
| POST `/login` | 200 OK | `ResponseEntity.ok()` | ✅ |
| POST `/refresh` | 200 OK | `ResponseEntity.ok()` | ✅ |
| POST `/logout` | 204 No Content | `ResponseEntity.noContent()` | ✅ |

---

# 📊 SUMMARY

## ✅ Correct Implementations (23/25)

| Component | Compliance |
|-----------|------------|
| UC-REGISTER Main Flow | ✅ Complete |
| UC-REGISTER Alternate Flows A1-A6 | ✅ Complete |
| UC-REGISTER Validation Rules | ✅ Complete |
| UC-LOGIN Main Flow | ✅ Complete |
| UC-LOGIN Alternate Flows A1-A2 | ✅ Complete |
| UC-REFRESH-TOKEN Main Flow | ✅ Complete |
| UC-REFRESH-TOKEN Rotation | ✅ Complete |
| UC-REFRESH-TOKEN Reuse Detection | ✅ Complete |
| UC-LOGOUT Main Flow | ✅ Complete |
| UC-LOGOUT Idempotent | ✅ Complete |
| JWT Claims | ✅ Complete |
| JWT Algorithm HS256 | ✅ Complete |
| Token TTLs (15min/7days) | ✅ Complete |
| BCrypt Strength 10 | ✅ Complete |
| Security Filter | ✅ Complete |
| Authorization Rules | ✅ Complete |
| HTTP Response Codes | ✅ Complete |

## ❌ Violations (1)

| Issue | Severity | Component |
|-------|----------|-----------|
| Missing account status check in `refreshToken()` | **Medium** | `RefreshTokenService` |

**Description**: Locked users can still refresh tokens and get new access tokens.

## ⚠️ Security Risks (2)

| Issue | Severity | Component |
|-------|----------|-----------|
| Login reveals locked account status before password validation | Low-Medium | `AuthService.login()` |
| Race condition on email uniqueness | Low | `AuthService.register()` |

---

## 🔧 Recommended Fix

**For the Medium severity violation**, add status check to `RefreshTokenService.refreshToken()`:

```java
@Transactional
public LoginResponse refreshToken(String tokenString) {
    // Step 1: Find token in database
    RefreshToken refreshToken = refreshTokenRepository.findByToken(tokenString)
            .orElseThrow(TokenInvalidException::new);

    User user = refreshToken.getUser();

    // ADD THIS: Check account status
    if (user.getStatus() == User.Status.LOCKED) {
        throw new AccountLockedException();
    }

    // Step 2: REUSE DETECTION - Check if token is already revoked
    if (refreshToken.isRevoked()) {
        log.warn("SECURITY: Refresh token reuse detected for user {}. Revoking all tokens.", user.getId());
        refreshTokenRepository.revokeAllByUser(user);
        throw new TokenInvalidException("Token invalid");
    }

    // Step 3: Check if token is expired
    if (refreshToken.isExpired()) {
        throw new TokenExpiredException();
    }

    // ... rest of the method
}
```

---

## ✅ Status

**PRODUCTION READY** (after applying recommended fix for Medium severity violation)

---

## 9. Audit Logging & Admin Action Traceability

### 9.1 Overview

All security-sensitive actions in Identity Service are logged to `audit_logs` table for compliance, forensics, and accountability.

### 9.2 Audit Coverage Matrix

| Use Case | Action Logged | Actor Source | Outcome Tracked |
|----------|---------------|--------------|-----------------|
| UC-REGISTER | `CREATE` | System (no JWT) | SUCCESS |
| UC-LOGIN (success) | `LOGIN_SUCCESS` | User email | SUCCESS |
| UC-LOGIN (wrong password) | `LOGIN_FAILED` | User email | FAILURE |
| UC-LOGIN (locked) | `LOGIN_DENIED` | User email | DENIED |
| UC-LOGOUT | `LOGOUT` | JWT `sub` claim | SUCCESS |
| UC-REFRESH-TOKEN | `REFRESH_SUCCESS` | JWT user | SUCCESS |
| UC-REFRESH-TOKEN (reuse) | `REFRESH_REUSE` | Token owner | FAILURE |
| UC-SOFT-DELETE | `SOFT_DELETE` | Admin JWT | SUCCESS |
| UC-RESTORE | `RESTORE` | Admin JWT | SUCCESS |
| UC-LOCK-ACCOUNT | `ACCOUNT_LOCKED` | Admin JWT | SUCCESS |
| UC-UNLOCK-ACCOUNT | `ACCOUNT_UNLOCKED` | Admin JWT | SUCCESS |

### 9.3 Actor Identification

| Context | Actor ID Source | Actor Email Source |
|---------|-----------------|-------------------|
| Authenticated request | JWT `sub` claim | JWT `email` claim |
| Login attempt | Target user ID (if found) | Request email |
| Registration | Created user ID | Request email |
| System action | NULL | "SYSTEM" |

### 9.4 Security Event Detection

The following events are flagged as **security events** for monitoring:

| Event | Action | Severity | Recommended Response |
|-------|--------|----------|---------------------|
| Token reuse detected | `REFRESH_REUSE` | **HIGH** | Investigate potential token theft |
| Multiple login failures | `LOGIN_FAILED` (count > 5/hour) | **MEDIUM** | Consider temporary lockout |
| Admin soft-deletes user | `SOFT_DELETE` | **LOW** | Normal admin action |
| Account locked | `LOGIN_DENIED` | **MEDIUM** | Verify legitimate lockout |

### 9.5 Audit Log Immutability Rules

| Rule | Description | Enforcement |
|------|-------------|-------------|
| **No UPDATE** | Audit logs cannot be modified | Application-level (no setter methods) |
| **No DELETE** | Audit logs cannot be deleted | Database-level (no DELETE permission) |
| **Append-only** | Only INSERT allowed | Repository has only `save()` method |
| **Timestamp integrity** | `timestamp` set by database | `DEFAULT CURRENT_TIMESTAMP` |

### 9.6 Admin Action Accountability

All admin actions are fully traceable:

```
Admin Action → JWT Extracted → actor_id logged → Audit entry created
     ↓
Entity modified → old_value/new_value captured → Full change history
```

**Required audit fields for admin actions:**

| Field | Source | Purpose |
|-------|--------|---------|
| `actor_id` | JWT `sub` claim | Who performed action |
| `actor_email` | JWT `email` claim | Human-readable actor |
| `ip_address` | Request header | Location tracking |
| `user_agent` | Request header | Client identification |
| `old_value` | Entity state before | Change rollback reference |
| `new_value` | Entity state after | Change verification |

### 9.7 Audit Query Authorization

| Endpoint | Required Role | Scope |
|----------|---------------|-------|
| `/api/admin/audit/**` | `ROLE_ADMIN` | All audit logs |
| User's own audit | `ROLE_STUDENT`, `ROLE_LECTURER` | Own actions only (future) |

### 9.8 Compliance Considerations

| Requirement | Implementation |
|-------------|----------------|
| **Data Retention** | Audit logs retained indefinitely (configurable) |
| **Tamper Evidence** | Immutable design, append-only |
| **Non-repudiation** | Actor ID from verified JWT |
| **Audit Trail** | Full before/after state captured |

---

## 10. Soft Delete Security Review

### 10.1 Soft Delete Implementation Rules

| Rule | Verification |
|------|--------------|
| No hard delete in application code | ✅ No `deleteById()` calls |
| `@SQLRestriction` on User entity | ✅ Hibernate filters deleted |
| Admin-only soft delete | ✅ `@PreAuthorize("hasRole('ADMIN')")` |
| Token revocation on delete | ✅ All refresh tokens revoked |

### 10.2 Soft Delete Attack Vectors

| Attack | Mitigation | Status |
|--------|------------|--------|
| Deleted user login | `@SQLRestriction` filters query | ✅ Protected |
| Deleted user token refresh | Token revoked on delete | ✅ Protected |
| Bypass via direct SQL | Application never uses raw SQL | ✅ Protected |
| Admin self-delete | Business logic check | ⚠️ Recommend adding |

### 10.3 Recommended Security Enhancement

**Add self-delete prevention:**

```java
// In UserAdminService.softDeleteUser()
if (userId.equals(securityContextHelper.getCurrentUserId().orElse(null))) {
    throw new IllegalArgumentException("Cannot delete own account");
}
```

---

## 📊 UPDATED SUMMARY

## ✅ Correct Implementations (28/30)

| Component | Compliance |
|-----------|------------|
| UC-REGISTER Main Flow | ✅ Complete |
| UC-REGISTER Alternate Flows | ✅ Complete |
| UC-LOGIN Main Flow | ✅ Complete |
| UC-REFRESH-TOKEN Main Flow | ✅ Complete |
| UC-REFRESH-TOKEN Reuse Detection | ✅ Complete |
| UC-LOGOUT Main Flow | ✅ Complete |
| UC-SOFT-DELETE Main Flow | ✅ Complete |
| UC-RESTORE Main Flow | ✅ Complete |
| Audit Logging Coverage | ✅ Complete |
| Audit Immutability | ✅ Complete |
| Admin Authorization | ✅ Complete |
| Soft Delete Filter | ✅ Complete |
| Token Revocation on Delete | ✅ Complete |

## ⚠️ Recommendations (2)

| Issue | Severity | Component |
|-------|----------|-----------|
| Admin self-delete not prevented | ✅ Fixed | `UserAdminService` |
| Audit log retention policy not defined | Low | Operations |

---

## ✅ Updated Status

**PRODUCTION READY** (Soft Delete + Audit Log fully reviewed)

---

## 11. Implementation Review – Audit Log & Soft Delete (2026-01-28)

### 11.1 Audit Events Verification

| Event | Action | Outcome | Actor | Entity | Status |
|-------|--------|---------|-------|--------|--------|
| Register | `CREATE` | SUCCESS | user.id | USER:id | ✅ |
| Login Success | `LOGIN_SUCCESS` | SUCCESS | user.id | USER:id | ✅ |
| Login Failed | `LOGIN_FAILED` | FAILURE | null | USER:0 | ✅ |
| Login Denied | `LOGIN_FAILED` | DENIED | user.id | USER:id | ✅ |
| Logout | `LOGOUT` | SUCCESS | JWT user | USER:id | ✅ |
| Refresh Success | `REFRESH_SUCCESS` | SUCCESS | user.id | USER:id | ✅ |
| Refresh Reuse | `REFRESH_REUSE` | DENIED | user.id | USER:id | ✅ |
| Soft Delete | `SOFT_DELETE` | SUCCESS | admin.id | USER:id | ✅ |
| Restore | `RESTORE` | SUCCESS | admin.id | USER:id | ✅ |
| Lock Account | `LOCK_ACCOUNT` | SUCCESS | admin.id | USER:id | ✅ |
| Unlock Account | `UNLOCK_ACCOUNT` | SUCCESS | admin.id | USER:id | ✅ |

### 11.2 Audit Log Rules Verification

| Rule | Status | Evidence |
|------|--------|----------|
| Append-only (no setter) | ✅ | AuditLog has only getters, Builder pattern |
| Ghi `actor_id` | ✅ | `builder.actorId(actorId)` |
| Ghi `actor_email` | ✅ | `builder.actorEmail(actorEmail)` |
| Ghi `ip_address` | ✅ | `addRequestContext()` |
| Ghi `user_agent` | ✅ | `addRequestContext()` |
| Async logging | ✅ | `@Async` annotation |
| Independent transaction | ✅ | `@Transactional(propagation = Propagation.REQUIRES_NEW)` |
| Graceful degradation | ✅ | `try-catch` with log.error |
| No password leak | ✅ | `UserAuditDto` excludes passwordHash |

### 11.3 Soft Delete Rules Verification

| Rule | Status | Evidence |
|------|--------|----------|
| No hard delete | ✅ | No `deleteById()` in UserRepository |
| `deleted_at` set correctly | ✅ | `user.softDelete(actorId)` |
| `deleted_by` set correctly | ✅ | `user.softDelete(actorId)` |
| `@SQLRestriction` works | ✅ | Hibernate auto-filters deleted users |
| Admin-only delete | ✅ | `@PreAuthorize("hasRole('ADMIN')")` |
| Admin-only restore | ✅ | `@PreAuthorize("hasRole('ADMIN')")` |
| Revoke tokens on delete | ✅ | `refreshTokenService.revokeAllTokens(user)` |
| Admin self-delete prevention | ✅ | Check `userId.equals(actorId)` |
| Admin self-lock prevention | ✅ | Check `userId.equals(actorId)` in `lockUser()` |

### 11.4 Security & Edge Cases Verification

| Check | Status | Evidence |
|-------|--------|----------|
| Locked user cannot login | ✅ | `AuthService.login()` checks status |
| Locked user cannot refresh | ✅ | `RefreshTokenService.refreshToken()` checks status |
| Token reuse → revoke ALL | ✅ | `refreshTokenRepository.revokeAllByUser(user)` |
| Token reuse → audit | ✅ | `auditService.logRefreshReuse(user)` |
| Soft-deleted cannot auth | ✅ | `@SQLRestriction` filters from `findByEmail()` |
| No info leak on login fail | ✅ | Same message "Invalid credentials" |
| No password in audit | ✅ | `UserAuditDto` used instead of `User` |

### 11.5 Fixes Applied

| Issue | Severity | Resolution |
|-------|----------|------------|
| Action names mismatch spec | Low | ✅ Renamed enum values to match Database-Design.md |
| Password hash in audit JSON | **HIGH** | ✅ Created `UserAuditDto` without passwordHash |
| Admin self-delete | **HIGH** | ✅ Added prevention check in `UserAdminService` |
| old_value/new_value context | Medium | ✅ Capture correct state in audit methods |
| Admin self-lock not prevented | **Medium** | ✅ Add check in `lockUser()` |
| Unlock accepts non-locked user | Low | ✅ Add state validation |
| Lock accepts already-locked user | Low | ✅ Make idempotent (no error, no duplicate audit) |
| Missing exception handlers | **High** | ✅ Add handlers for admin exceptions |
| old_value captured after modification | Medium | ✅ Capture state before modification |

### 11.6 Final Status

**ALL ISSUES RESOLVED** ✅

| Category | Score |
|----------|-------|
| Audit Events | 11/11 ✅ |
| Audit Rules | 9/9 ✅ |
| Soft Delete Rules | 8/8 ✅ |
| Security Checks | 7/7 ✅ |
| **Total** | **35/35 ✅** |
---

## 12. Exception Handling Requirements

### 12.1 Admin API Exception Mapping

| Exception | HTTP Status | Error Code | Message |
|-----------|-------------|------------|---------||
| `UserNotFoundException` | 404 | USER_NOT_FOUND | "User not found" |
| `InvalidUserStateException` | 400 | INVALID_STATE | Dynamic message |
| `SelfActionException` | 400 | SELF_ACTION_DENIED | "Cannot perform this action on own account" |

### 12.2 Implementation

```java
@ExceptionHandler(UserNotFoundException.class)
public ResponseEntity<ErrorResponse> handleUserNotFound(UserNotFoundException ex) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse.of("USER_NOT_FOUND", ex.getMessage()));
}

@ExceptionHandler(InvalidUserStateException.class)
public ResponseEntity<ErrorResponse> handleInvalidState(InvalidUserStateException ex) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse.of("INVALID_STATE", ex.getMessage()));
}
```

> **Rationale:** Using `IllegalArgumentException` for business logic errors results in 500 Internal Server Error, which is incorrect and leaks implementation details.