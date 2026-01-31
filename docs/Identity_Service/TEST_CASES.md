# TEST CASES – IDENTITY SERVICE

**Version:** 2.0  
**Last Updated:** January 30, 2026  
**Status:** Production-Ready

---

## Test Coverage Overview

| Use Case | Test Count | Priority |
|----------|------------|----------|
| UC-REGISTER | 18 | HIGH |
| UC-LOGIN | 15 | CRITICAL |
| UC-REFRESH-TOKEN | 12 | CRITICAL |
| UC-LOGOUT | 6 | HIGH |
| UC-ADMIN-CREATE-USER | 8 | CRITICAL |
| UC-ADMIN-SOFT-DELETE | 7 | HIGH |
| UC-ADMIN-RESTORE | 6 | HIGH |
| UC-ADMIN-LOCK-ACCOUNT | 8 | CRITICAL |
| UC-ADMIN-UNLOCK-ACCOUNT | 5 | HIGH |
| UC-ADMIN-UPDATE-EXTERNAL-ACCOUNTS | 5 | MEDIUM |
| JWT Validation | 10 | CRITICAL |
| Token Reuse Detection | 5 | CRITICAL |
| Role Format Validation | 6 | CRITICAL |
| Audit Logging | 8 | HIGH |
| **TOTAL** | **119** | - |

---

## 1. UC-REGISTER

### TC-AUTH-REG-001: Register successfully with valid STUDENT data

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-001 |
| **Title** | Register STUDENT account with all valid fields |
| **Priority** | CRITICAL |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Email "student@university.edu" does not exist in database
- Application is running

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "Ph@050204",
  "confirmPassword": "Ph@050204",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Steps:**
1. Send POST request to `/api/auth/register`
2. Include request body with test data
3. Verify response

**Expected Result:**
- HTTP Status: `201 CREATED`
- Response body:
```json
{
  "user": {
    "id": 1,
    "email": "student@university.edu",
    "fullName": "Nguyễn Văn An",
    "role": "STUDENT",
    "status": "ACTIVE",
    "createdAt": "2026-01-30T10:00:00Z"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- Database verification:
  - New record in `users` table with `status = ACTIVE`, `role = STUDENT`
  - `password_hash` starts with `$2a$10$` (BCrypt strength 10)
  - New record in `refresh_tokens` table with `revoked = false`
- Audit log: Event `USER_CREATED` with outcome `SUCCESS`

---

### TC-AUTH-REG-002: Register fails - Email already exists

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-002 |
| **Title** | Registration rejected when email already registered |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User with email "existing@university.edu" already exists in database

**Test Data:**
```json
{
  "email": "existing@university.edu",
  "password": "NewPassword@456",
  "confirmPassword": "NewPassword@456",
  "fullName": "Trần Thị Bình",
  "role": "STUDENT"
}
```

**Steps:**
1. Send POST request to `/api/auth/register`
2. Verify error response

**Expected Result:**
- HTTP Status: `409 CONFLICT`
- Response body:
```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email already registered",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- No new database records created

---

### TC-AUTH-REG-003: Register fails - Email case-insensitive duplicate

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-003 |
| **Title** | Registration rejected for case-insensitive duplicate email |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User with email "student@university.edu" exists in database

**Test Data:**
```json
{
  "email": "STUDENT@UNIVERSITY.EDU",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Lê Văn Cường",
  "role": "STUDENT"
}
```

**Steps:**
1. Send POST request to `/api/auth/register`
2. Verify email uniqueness check is case-insensitive

**Expected Result:**
- HTTP Status: `409 CONFLICT`
- Response: "Email already registered"
- Implementation uses: `LOWER(email) = LOWER(?)` comparison

---

### TC-AUTH-REG-004: Register fails - Invalid email format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-004 |
| **Title** | Registration rejected for invalid email format |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "not-an-email",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Steps:**
1. Send POST request to `/api/auth/register`

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response body:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-30T10:00:00Z",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    }
  ]
}
```

---

### TC-AUTH-REG-005: Register fails - Password too short

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-005 |
| **Title** | Registration rejected when password < 8 characters |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "Pass@1",
  "confirmPassword": "Pass@1",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must be 8-128 characters"

---

### TC-AUTH-REG-006: Register fails - Password missing uppercase

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-006 |
| **Title** | Registration rejected when password has no uppercase letter |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "securepass@123",
  "confirmPassword": "securepass@123",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 uppercase letter"

---

### TC-AUTH-REG-007: Register fails - Password missing lowercase

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-007 |
| **Title** | Registration rejected when password has no lowercase letter |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SECUREPASS@123",
  "confirmPassword": "SECUREPASS@123",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 lowercase letter"

---

### TC-AUTH-REG-008: Register fails - Password missing digit

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-008 |
| **Title** | Registration rejected when password has no digit |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@",
  "confirmPassword": "SecurePass@",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 digit"

---

### TC-AUTH-REG-009: Register fails - Password missing special character

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-009 |
| **Title** | Registration rejected when password has no special character |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass123",
  "confirmPassword": "SecurePass123",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 special character (@$!%*?&)"

---

### TC-AUTH-REG-010: Register fails - Password invalid special character

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-010 |
| **Title** | Registration rejected when password contains disallowed special character |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass#123",
  "confirmPassword": "SecurePass#123",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 special character (@$!%*?&)"
- Only `@`, `$`, `!`, `%`, `*`, `?`, `&` allowed

---

### TC-AUTH-REG-011: Register fails - Passwords do not match

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-011 |
| **Title** | Registration rejected when password and confirmPassword differ |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "DifferentPass@456",
  "fullName": "Nguyễn Văn An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response body:
```json
{
  "code": "PASSWORD_MISMATCH",
  "message": "Passwords do not match",
  "timestamp": "2026-01-30T10:00:00Z"
}
```

---

### TC-AUTH-REG-012: Register fails - Invalid role LECTURER

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-012 |
| **Title** | Public registration rejected for LECTURER role |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Test Data:**
```json
{
  "email": "lecturer@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Dr. Nguyễn Văn An",
  "role": "LECTURER"
}
```

**Steps:**
1. Send POST request to `/api/auth/register`

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid role specified"
- Only `STUDENT` role allowed in public registration

---

### TC-AUTH-REG-013: Register fails - Invalid role ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-013 |
| **Title** | Public registration rejected for ADMIN role |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Test Data:**
```json
{
  "email": "admin@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Administrator",
  "role": "ADMIN"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid role specified"
- ADMIN/LECTURER accounts must be created via `/api/admin/users`

---

### TC-AUTH-REG-014: Register fails - Full name too short

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-014 |
| **Title** | Registration rejected when full name < 2 characters |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "A",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Name must be 2-100 characters"

---

### TC-AUTH-REG-015: Register fails - Full name too long

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-015 |
| **Title** | Registration rejected when full name > 100 characters |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "A very long name that exceeds one hundred characters limit for validation testing purposes and should be rejected",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Name must be 2-100 characters"

---

### TC-AUTH-REG-016: Register fails - Full name contains numbers

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-016 |
| **Title** | Registration rejected when full name contains digits |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyen123 Van An",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Name contains invalid characters"
- Pattern: `^[\p{L}\s\-]{2,100}$` (only letters, spaces, hyphens)

---

### TC-AUTH-REG-017: Register success - Full name with hyphen

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-017 |
| **Title** | Registration accepts full name with hyphen |
| **Priority** | MEDIUM |
| **Type** | Positive - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Jean-Pierre Dubois",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `201 CREATED`
- Full name stored as "Jean-Pierre Dubois"

---

### TC-AUTH-REG-018: Register success - Unicode Vietnamese name

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REG-018 |
| **Title** | Registration accepts Vietnamese name with diacritics |
| **Priority** | HIGH |
| **Type** | Positive - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Trần Thị Bảo Châu",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `201 CREATED`
- Full name stored correctly with Unicode characters
- Pattern `\p{L}` matches Unicode letters

---

## 2. UC-LOGIN

### TC-AUTH-LOGIN-001: Login successfully with valid credentials

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-001 |
| **Title** | Login with correct email and password |
| **Priority** | CRITICAL |
| **Type** | Positive - Happy Path |

**Preconditions:**
- User exists: email = "student@university.edu", password = "SecurePass@123"
- User status = `ACTIVE`
- User role = `STUDENT`

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123"
}
```

**Steps:**
1. Send POST request to `/api/auth/login`
2. Verify response tokens
3. Decode JWT access token
4. Query database for refresh token

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- Access token JWT payload:
```json
{
  "sub": "1",
  "email": "student@university.edu",
  "roles": ["STUDENT"],
  "token_type": "ACCESS",
  "iat": 1738234800,
  "exp": 1738235700
}
```
- Refresh token: UUID format, stored in database with `revoked = false`
- Audit log: Event `LOGIN_SUCCESS` with outcome `SUCCESS`, IP address captured

---

### TC-AUTH-LOGIN-002: Login fails - Email not found (Generic error)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-002 |
| **Title** | Login with non-existent email returns generic error |
| **Priority** | CRITICAL |
| **Type** | Negative - Security (Anti-Enumeration) |

**Test Data:**
```json
{
  "email": "nonexistent@university.edu",
  "password": "AnyPassword@123"
}
```

**Steps:**
1. Send POST request to `/api/auth/login`
2. Verify generic error message

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response body:
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- **CRITICAL**: Must NOT reveal that email does not exist
- Same error as wrong password (anti-enumeration)
- Audit log: Event `LOGIN_FAILED` with outcome `FAILURE`

---

### TC-AUTH-LOGIN-003: Login fails - Wrong password (Generic error)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-003 |
| **Title** | Login with incorrect password returns generic error |
| **Priority** | CRITICAL |
| **Type** | Negative - Security (Anti-Enumeration) |

**Preconditions:**
- User exists: email = "student@university.edu", correct password = "SecurePass@123"
- User status = `ACTIVE`

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "WrongPassword@456"
}
```

**Steps:**
1. Send POST request to `/api/auth/login`

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response body:
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- **CRITICAL**: Same error message as email not found (anti-enumeration)
- Audit log: Event `LOGIN_FAILED` with outcome `FAILURE`

---

### TC-AUTH-LOGIN-004: Login denied - Account locked with CORRECT password

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-004 |
| **Title** | Login denied when account is locked (password correct) |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- User exists: email = "locked@university.edu", password = "SecurePass@123"
- User status = `LOCKED`
- Password in test data is CORRECT

**Test Data:**
```json
{
  "email": "locked@university.edu",
  "password": "SecurePass@123"
}
```

**Steps:**
1. Send POST request to `/api/auth/login`
2. Verify account locked error (NOT generic error)

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response body:
```json
{
  "code": "ACCOUNT_LOCKED",
  "message": "Account is locked. Contact admin.",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- **SECURITY RATIONALE**: Account locked error ONLY shown when password correct
  - User proved identity with correct password
  - System may reveal account status after authentication
- No tokens generated
- Audit log: Event `LOGIN_DENIED` with outcome `DENIED`, reason "Account is locked"

---

### TC-AUTH-LOGIN-005: Login fails - Account locked with WRONG password (Anti-Enumeration)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-005 |
| **Title** | Login with wrong password on locked account returns generic error |
| **Priority** | CRITICAL |
| **Type** | Negative - Security (Anti-Enumeration) |

**Preconditions:**
- User exists: email = "locked@university.edu", correct password = "SecurePass@123"
- User status = `LOCKED`
- Password in test data is WRONG

**Test Data:**
```json
{
  "email": "locked@university.edu",
  "password": "WrongPassword@456"
}
```

**Steps:**
1. Send POST request to `/api/auth/login`
2. Verify generic error (NOT account locked error)

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED` (NOT 403!)
- Response body:
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- **CRITICAL SECURITY**: Must return generic error (same as TC-AUTH-LOGIN-003)
- **MUST NOT** reveal that account is locked
- **RATIONALE**: User did NOT prove identity (wrong password) → cannot know account status
- Audit log: Event `LOGIN_FAILED` with outcome `FAILURE`
- **Implementation check order**:
  1. Find user by email
  2. **Validate password FIRST** (BCrypt comparison)
  3. If password wrong → return "Invalid credentials"
  4. If password correct → check status → return "Account locked" if needed

---

### TC-AUTH-LOGIN-006: Login fails - Soft deleted user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-006 |
| **Title** | Login fails for soft deleted user |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User was created: email = "deleted@university.edu"
- User has been soft deleted: `deleted_at IS NOT NULL`

**Test Data:**
```json
{
  "email": "deleted@university.edu",
  "password": "SecurePass@123"
}
```

**Steps:**
1. Send POST request to `/api/auth/login`

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid credentials"
- Soft deleted users filtered by `@SQLRestriction("deleted_at IS NULL")`
- User not found in query (appears as non-existent)

---

### TC-AUTH-LOGIN-007: JWT access token structure validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-007 |
| **Title** | Verify JWT access token contains correct claims |
| **Priority** | CRITICAL |
| **Type** | Positive - Token Structure |

**Preconditions:**
- User logged in successfully (TC-AUTH-LOGIN-001)

**Steps:**
1. Extract access token from login response
2. Decode JWT at jwt.io or with library
3. Verify header and payload

**Expected Result:**
- JWT Header:
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```
- JWT Payload:
```json
{
  "sub": "1",
  "email": "student@university.edu",
  "roles": ["STUDENT"],
  "token_type": "ACCESS",
  "iat": 1738234800,
  "exp": 1738235700
}
```
- **CRITICAL**: `roles` array contains plain string `"STUDENT"` (NO `ROLE_` prefix)
- TTL = 900 seconds (15 minutes)
- Signature valid with secret key (HS256 algorithm)

---

### TC-AUTH-LOGIN-008: Refresh token structure validation

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-008 |
| **Title** | Verify refresh token is UUID (not JWT) |
| **Priority** | HIGH |
| **Type** | Positive - Token Structure |

**Preconditions:**
- User logged in successfully

**Steps:**
1. Extract refresh token from login response
2. Verify format

**Expected Result:**
- Refresh token format: UUID v4 (e.g., `550e8400-e29b-41d4-a716-446655440000`)
- NOT a JWT (no three parts separated by dots)
- Database record:
  - `refresh_tokens.token` = UUID value
  - `refresh_tokens.user_id` = user ID
  - `refresh_tokens.revoked` = `false`
  - `refresh_tokens.expires_at` = current time + 7 days
  - `refresh_tokens.issued_at` = current time

---

### TC-AUTH-LOGIN-009: Login creates audit log with IP and User-Agent

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-009 |
| **Title** | Login success creates audit log with request metadata |
| **Priority** | HIGH |
| **Type** | Positive - Audit Logging |

**Preconditions:**
- User exists and can login

**Steps:**
1. Send login request with:
   - IP address: `192.168.1.100`
   - User-Agent: `Mozilla/5.0 (Windows NT 10.0; Win64; x64)`
2. Query `audit_logs` table after login

**Expected Result:**
- Audit log record created:
  - `action` = `LOGIN_SUCCESS`
  - `entity_type` = `User`
  - `entity_id` = user ID
  - `actor_id` = user ID
  - `actor_email` = user email
  - `ip_address` = `"192.168.1.100"`
  - `user_agent` = `"Mozilla/5.0 (Windows NT 10.0; Win64; x64)"`
  - `outcome` = `SUCCESS`
  - `timestamp` = current time

---

### TC-AUTH-LOGIN-010: Multiple concurrent logins allowed

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-010 |
| **Title** | User can login from multiple devices simultaneously |
| **Priority** | HIGH |
| **Type** | Positive - Business Rule |

**Preconditions:**
- User exists: email = "student@university.edu"

**Steps:**
1. Login from Device A (User-Agent: Chrome)
2. Login from Device B (User-Agent: Firefox)
3. Verify both sessions active

**Expected Result:**
- Both login requests return `200 OK`
- Database has 2 active refresh tokens for same user:
  - Token A: `revoked = false`, different UUID
  - Token B: `revoked = false`, different UUID
- Both access tokens valid
- No existing session terminated

---

### TC-AUTH-LOGIN-011: Login fails - Missing email field

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-011 |
| **Title** | Login rejected when email field missing |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "password": "SecurePass@123"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Email is required"

---

### TC-AUTH-LOGIN-012: Login fails - Missing password field

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-012 |
| **Title** | Login rejected when password field missing |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password is required"

---

### TC-AUTH-LOGIN-013: Login fails - Empty email string

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-013 |
| **Title** | Login rejected when email is empty string |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "",
  "password": "SecurePass@123"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Email is required"
- `@NotBlank` validation rejects empty strings

---

### TC-AUTH-LOGIN-014: Login fails - Empty password string

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-014 |
| **Title** | Login rejected when password is empty string |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": ""
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password is required"

---

### TC-AUTH-LOGIN-015: Login response time constant for enumeration prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGIN-015 |
| **Title** | Login response time similar for valid/invalid credentials |
| **Priority** | MEDIUM |
| **Type** | Security - Timing Attack Prevention |

**Steps:**
1. Measure response time for non-existent email: T1
2. Measure response time for wrong password: T2
3. Calculate difference: |T1 - T2|

**Expected Result:**
- Response time difference < 100ms
- BCrypt password comparison is constant-time
- Cannot enumerate accounts via timing attacks

---

## 3. UC-REFRESH-TOKEN

### TC-AUTH-REFRESH-001: Refresh token successfully with valid token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-001 |
| **Title** | Refresh access token with valid refresh token |
| **Priority** | CRITICAL |
| **Type** | Positive - Happy Path |

**Preconditions:**
- User logged in and has valid refresh token
- Refresh token not revoked, not expired
- User status = `ACTIVE`

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Steps:**
1. Send POST request to `/api/auth/refresh`
2. Verify old token revoked
3. Verify new tokens generated

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "660f9511-f30c-52e5-b827-557766551111",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- Database changes:
  - OLD refresh token: `revoked = true`
  - NEW refresh token: `revoked = false`, different UUID
- Audit log: Event `REFRESH_SUCCESS` with outcome `SUCCESS`
- Token rotation implemented (new refresh token on each use)

---

### TC-AUTH-REFRESH-002: Refresh fails - Invalid token format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-002 |
| **Title** | Refresh rejected with invalid UUID format |
| **Priority** | HIGH |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "refreshToken": "not-a-valid-uuid"
}
```

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "INVALID_TOKEN",
  "message": "Invalid token",
  "timestamp": "2026-01-30T10:00:00Z"
}
```

---

### TC-AUTH-REFRESH-003: Refresh fails - Token not found in database

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-003 |
| **Title** | Refresh rejected when token does not exist in database |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Test Data:**
```json
{
  "refreshToken": "999e9999-e99b-99d9-a999-999999999999"
}
```

**Steps:**
1. Send POST request with non-existent UUID

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token"

---

### TC-AUTH-REFRESH-004: Refresh fails - Token already revoked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-004 |
| **Title** | Refresh rejected when token already revoked |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- Refresh token exists in database
- Token has `revoked = true`

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token"

---

### TC-AUTH-REFRESH-005: Refresh fails - Token expired

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-005 |
| **Title** | Refresh rejected when token expired (> 7 days old) |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- Refresh token exists in database
- Token `expires_at < NOW()`

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "TOKEN_EXPIRED",
  "message": "Token expired",
  "timestamp": "2026-01-30T10:00:00Z"
}
```

---

### TC-AUTH-REFRESH-006: Refresh fails - User account locked (CRITICAL SECURITY FIX)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-006 |
| **Title** | Refresh rejected when user account locked |
| **Priority** | CRITICAL |
| **Type** | Negative - Security (Account Lock Bypass Prevention) |

**Preconditions:**
- User logged in at T0 → received valid refresh token (expires in 7 days)
- Admin locked user account at T1 (status = `LOCKED`)
- Refresh token still valid (not expired, not revoked)
- Current time T2 < T0 + 7 days

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Steps:**
1. Send POST request to `/api/auth/refresh`
2. Verify all user tokens revoked

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response body:
```json
{
  "code": "ACCOUNT_LOCKED",
  "message": "Account is locked. Contact admin.",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- **CRITICAL SECURITY**: Status check BEFORE generating new tokens
- Database changes:
  - ALL refresh tokens for user: `revoked = true`
- Audit log: Event `REFRESH_DENIED` with outcome `DENIED`, reason "Account is locked"
- **Implementation requirement**:
```java
// Step 5: CRITICAL - Check status BEFORE generating new tokens
if (user.getStatus() != User.Status.ACTIVE) {
    refreshTokenRepository.revokeAllByUser(user);
    throw new AccountLockedException();
}
```

---

### TC-AUTH-REFRESH-007: Refresh fails - User soft deleted

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-007 |
| **Title** | Refresh rejected when user soft deleted |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User has valid refresh token
- User has been soft deleted (`deleted_at IS NOT NULL`)

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token"
- Soft deleted users filtered by `@SQLRestriction`
- User not found → token appears invalid

---

### TC-AUTH-REFRESH-008: Token reuse detection - Revoke all tokens

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-008 |
| **Title** | Token reuse detected and all user tokens revoked |
| **Priority** | CRITICAL |
| **Type** | Security - Token Reuse Detection |

**Preconditions:**
- User U1 logged in at T0 → received refresh token R1
- User U1 refreshed at T1 → R1 revoked, received new token R2
- Attacker has copy of R1 (old revoked token)

**Steps:**
1. Attacker attempts to reuse R1 at T2
2. System detects reuse (token is revoked)
3. Verify all tokens revoked

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token reuse detected. All sessions revoked."
- Database changes:
  - ALL refresh tokens for user U1: `revoked = true`
- Legitimate user with R2 also logged out (must re-login)
- Audit log: Event `REFRESH_REUSE` with:
  - Outcome = `SECURITY_ALERT`
  - Alert level = `CRITICAL`
  - IP address captured
  - User agent captured
- **Security benefit**: Forces attacker and legitimate user to re-authenticate

---

### TC-AUTH-REFRESH-009: Token reuse detection only affects target user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-009 |
| **Title** | Token reuse revokes only affected user's tokens |
| **Priority** | HIGH |
| **Type** | Security - Isolation |

**Preconditions:**
- User U1 has token reuse detected
- User U2 (different user) has active sessions

**Steps:**
1. Trigger token reuse for U1
2. Verify U2 sessions unaffected

**Expected Result:**
- User U1: All tokens revoked
- User U2: All tokens remain active
- Token reuse detection isolated per user

---

### TC-AUTH-REFRESH-010: Refresh token rotation - Old token revoked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-010 |
| **Title** | Refresh token rotation revokes old token |
| **Priority** | HIGH |
| **Type** | Positive - Security |

**Preconditions:**
- User has valid refresh token R1

**Steps:**
1. Refresh with R1 → receive new token R2
2. Attempt to reuse R1

**Expected Result:**
- First refresh: `200 OK`, new token R2
- Second attempt with R1: `401 UNAUTHORIZED` "Token reuse detected"
- Database: R1 has `revoked = true`

---

### TC-AUTH-REFRESH-011: Refresh creates new access token with same user data

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-011 |
| **Title** | Refreshed access token contains current user data |
| **Priority** | HIGH |
| **Type** | Positive - Token Generation |

**Preconditions:**
- User logged in
- User role = `STUDENT`

**Steps:**
1. Refresh token
2. Decode new access token
3. Verify claims

**Expected Result:**
- New access token JWT payload contains:
  - `sub` = user ID
  - `email` = current email
  - `roles` = `["STUDENT"]` (current role)
  - `token_type` = `"ACCESS"`
  - `exp` = now + 15 minutes

---

### TC-AUTH-REFRESH-012: Refresh fails - Missing refreshToken field

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-REFRESH-012 |
| **Title** | Refresh rejected when refreshToken field missing |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Refresh token is required"

---

## 4. UC-LOGOUT

### TC-AUTH-LOGOUT-001: Logout successfully revokes refresh token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGOUT-001 |
| **Title** | Logout revokes refresh token in database |
| **Priority** | HIGH |
| **Type** | Positive - Happy Path |

**Preconditions:**
- User logged in with valid refresh token
- User authenticated (JWT access token in Authorization header)

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Steps:**
1. Send POST request to `/api/auth/logout`
2. Include `Authorization: Bearer <access_token>` header
3. Query database for token status

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "message": "Logout successful"
}
```
- Database: `refresh_tokens.revoked = true` for specified token
- Audit log: Event `LOGOUT` with outcome `SUCCESS`

---

### TC-AUTH-LOGOUT-002: Logout fails - Unauthenticated request

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGOUT-002 |
| **Title** | Logout rejected without authentication |
| **Priority** | HIGH |
| **Type** | Negative - Authentication |

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Steps:**
1. Send POST request to `/api/auth/logout`
2. Do NOT include Authorization header

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Unauthorized"
- Token not revoked in database

---

### TC-AUTH-LOGOUT-003: Logout fails - Token not owned by user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGOUT-003 |
| **Title** | Logout rejected when token belongs to different user |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- User U1 authenticated with access token
- Refresh token R1 belongs to User U2

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Steps:**
1. User U1 sends logout request with U2's refresh token

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Token does not belong to user"
- Token NOT revoked (still owned by U2)

---

### TC-AUTH-LOGOUT-004: Logout idempotent - Already revoked token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGOUT-004 |
| **Title** | Logout succeeds even if token already revoked |
| **Priority** | MEDIUM |
| **Type** | Positive - Idempotency |

**Preconditions:**
- User authenticated
- Refresh token already revoked (`revoked = true`)

**Test Data:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Expected Result:**
- HTTP Status: `200 OK`
- Response: "Logout successful"
- Idempotent operation (no error)

---

### TC-AUTH-LOGOUT-005: Logout fails - Missing refreshToken field

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGOUT-005 |
| **Title** | Logout rejected when refreshToken missing |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Refresh token is required"

---

### TC-AUTH-LOGOUT-006: Access token still valid after logout until expiration

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUTH-LOGOUT-006 |
| **Title** | Access token remains valid after logout (stateless JWT) |
| **Priority** | MEDIUM |
| **Type** | Positive - JWT Behavior |

**Preconditions:**
- User logged out (refresh token revoked)
- Access token not expired yet (< 15 minutes since issue)

**Steps:**
1. User logs out
2. User makes authenticated request with access token

**Expected Result:**
- Request succeeds (access token still valid)
- **Note**: JWT is stateless, cannot be invalidated server-side
- Access token expires after 15 minutes (TTL)
- User cannot get new access token (refresh token revoked)

---

## 5. UC-ADMIN-CREATE-USER

### TC-ADMIN-CREATE-001: Admin creates LECTURER account successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-001 |
| **Title** | ADMIN creates LECTURER account via admin endpoint |
| **Priority** | HIGH |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- Email "lecturer@university.edu" does not exist

**Test Data:**
```json
{
  "email": "lecturer@university.edu",
  "password": "LecturerPass@123",
  "confirmPassword": "LecturerPass@123",
  "fullName": "Dr. Nguyễn Văn Bình",
  "role": "LECTURER"
}
```

**Steps:**
1. Send POST request to `/api/admin/users`
2. Include `Authorization: Bearer <admin_access_token>` header

**Expected Result:**
- HTTP Status: `201 CREATED`
- Response body:
```json
{
  "message": "User created successfully",
  "user": {
    "id": 2,
    "email": "lecturer@university.edu",
    "fullName": "Dr. Nguyễn Văn Bình",
    "role": "LECTURER",
    "status": "ACTIVE",
    "createdAt": "2026-01-30T10:00:00Z"
  }
}
```
- Database: New user with `role = LECTURER`, `status = ACTIVE`
- Audit log: Event `USER_CREATED` with actor = admin ID

---

### TC-ADMIN-CREATE-002: Admin creates ADMIN account successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-002 |
| **Title** | ADMIN creates another ADMIN account |
| **Priority** | HIGH |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`

**Test Data:**
```json
{
  "email": "admin2@university.edu",
  "password": "AdminPass@456",
  "confirmPassword": "AdminPass@456",
  "fullName": "Administrator Two",
  "role": "ADMIN"
}
```

**Expected Result:**
- HTTP Status: `201 CREATED`
- New user created with `role = ADMIN`

---

### TC-ADMIN-CREATE-003: Admin creates STUDENT account successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-003 |
| **Title** | ADMIN creates STUDENT account via admin endpoint |
| **Priority** | MEDIUM |
| **Type** | Positive - Edge Case |

**Preconditions:**
- Authenticated user with role = `ADMIN`

**Test Data:**
```json
{
  "email": "student@university.edu",
  "password": "StudentPass@789",
  "confirmPassword": "StudentPass@789",
  "fullName": "Trần Thị Cúc",
  "role": "STUDENT"
}
```

**Expected Result:**
- HTTP Status: `201 CREATED`
- STUDENT account created successfully
- **Note**: ADMIN can create any role type

---

### TC-ADMIN-CREATE-004: Non-admin cannot create users

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-004 |
| **Title** | STUDENT/LECTURER cannot access admin user creation |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- Authenticated user with role = `STUDENT` or `LECTURER`

**Test Data:**
```json
{
  "email": "newuser@university.edu",
  "password": "Password@123",
  "confirmPassword": "Password@123",
  "fullName": "New User",
  "role": "LECTURER"
}
```

**Steps:**
1. Send POST request to `/api/admin/users`
2. Include access token of STUDENT/LECTURER user

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"
- No user created

---

### TC-ADMIN-CREATE-005: Admin user creation fails - Email exists

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-005 |
| **Title** | Admin user creation rejected for duplicate email |
| **Priority** | HIGH |
| **Type** | Negative - Business Rule |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- Email "existing@university.edu" already exists

**Test Data:**
```json
{
  "email": "existing@university.edu",
  "password": "Password@123",
  "confirmPassword": "Password@123",
  "fullName": "Duplicate User",
  "role": "LECTURER"
}
```

**Expected Result:**
- HTTP Status: `409 CONFLICT`
- Response: "Email already registered"

---

### TC-ADMIN-CREATE-006: Admin user creation fails - Passwords don't match

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-006 |
| **Title** | Admin user creation rejected when passwords mismatch |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "lecturer@university.edu",
  "password": "Password@123",
  "confirmPassword": "DifferentPass@456",
  "fullName": "Dr. Nguyen",
  "role": "LECTURER"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "Passwords do not match"

---

### TC-ADMIN-CREATE-007: Admin user creation fails - Invalid role

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-007 |
| **Title** | Admin user creation rejected for invalid role value |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Test Data:**
```json
{
  "email": "user@university.edu",
  "password": "Password@123",
  "confirmPassword": "Password@123",
  "fullName": "User Name",
  "role": "SUPERUSER"
}
```

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid role specified"

---

### TC-ADMIN-CREATE-008: Unauthenticated user cannot create accounts

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-CREATE-008 |
| **Title** | Admin endpoint rejected without authentication |
| **Priority** | CRITICAL |
| **Type** | Negative - Authentication |

**Steps:**
1. Send POST request to `/api/admin/users`
2. Do NOT include Authorization header

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Unauthorized"

---

## 6. UC-ADMIN-SOFT-DELETE

### TC-ADMIN-DELETE-001: Admin soft deletes user successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-001 |
| **Title** | ADMIN soft deletes active user account |
| **Priority** | HIGH |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- Target user U1 exists with `deleted_at = NULL`

**Steps:**
1. Send DELETE request to `/api/admin/users/{userId}`
2. Include admin access token

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "message": "User deleted successfully",
  "userId": "123"
}
```
- Database changes:
  - `users.deleted_at` = current timestamp
  - `users.deleted_by` = admin user ID
- User not visible in queries (filtered by `@SQLRestriction`)
- Audit log: Event `USER_DELETED` with actor = admin ID

---

### TC-ADMIN-DELETE-002: Admin cannot soft delete self

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-002 |
| **Title** | ADMIN cannot soft delete own account |
| **Priority** | CRITICAL |
| **Type** | Negative - Business Rule |

**Preconditions:**
- Authenticated ADMIN user with ID = 1

**Steps:**
1. Send DELETE request to `/api/admin/users/1` (self)

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response body:
```json
{
  "code": "INVALID_USER_STATE",
  "message": "Cannot delete own account",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- User NOT deleted

---

### TC-ADMIN-DELETE-003: Admin soft delete fails - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-003 |
| **Title** | Soft delete rejected for non-existent user |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Steps:**
1. Send DELETE request to `/api/admin/users/99999` (non-existent ID)

**Expected Result:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

### TC-ADMIN-DELETE-004: Admin soft delete fails - User already deleted

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-004 |
| **Title** | Soft delete rejected when user already deleted |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User U1 already soft deleted (`deleted_at IS NOT NULL`)

**Steps:**
1. Send DELETE request to `/api/admin/users/{userId}`

**Expected Result:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"
- `@SQLRestriction` filters soft deleted users

---

### TC-ADMIN-DELETE-005: Non-admin cannot soft delete users

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-005 |
| **Title** | STUDENT/LECTURER cannot soft delete users |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- Authenticated user with role = `STUDENT` or `LECTURER`

**Steps:**
1. Send DELETE request to `/api/admin/users/{userId}`

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ADMIN-DELETE-006: Soft deleted user cannot login

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-006 |
| **Title** | Soft deleted user cannot authenticate |
| **Priority** | HIGH |
| **Type** | Positive - Security |

**Preconditions:**
- User U1 soft deleted

**Steps:**
1. Attempt login with U1 credentials

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid credentials"
- User not found due to `@SQLRestriction`

---

### TC-ADMIN-DELETE-007: Soft deleted user tokens still work until expiration

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-DELETE-007 |
| **Title** | Soft deleted user's JWT access token remains valid |
| **Priority** | MEDIUM |
| **Type** | Positive - JWT Behavior |

**Preconditions:**
- User U1 has valid access token
- Admin soft deletes U1

**Steps:**
1. U1 makes authenticated request with access token

**Expected Result:**
- Request succeeds while token not expired (< 15 min)
- **Note**: JWT is stateless, cannot be invalidated
- U1 cannot refresh token (user not found)

---

## 7. UC-ADMIN-RESTORE

### TC-ADMIN-RESTORE-001: Admin restores soft deleted user successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-RESTORE-001 |
| **Title** | ADMIN restores soft deleted user account |
| **Priority** | HIGH |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- User U1 soft deleted (`deleted_at IS NOT NULL`)

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/restore`

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "message": "User restored successfully",
  "userId": "123"
}
```
- Database changes:
  - `users.deleted_at` = `NULL`
  - `users.deleted_by` = `NULL`
- User visible in queries again
- User can login (if status = `ACTIVE`)
- Audit log: Event `USER_RESTORED` with actor = admin ID

---

### TC-ADMIN-RESTORE-002: Admin restore fails - User not deleted

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-RESTORE-002 |
| **Title** | Restore rejected when user not deleted |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User U1 exists with `deleted_at = NULL` (not deleted)

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/restore`

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "User is not deleted"

---

### TC-ADMIN-RESTORE-003: Admin restore fails - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-RESTORE-003 |
| **Title** | Restore rejected for non-existent user |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Steps:**
1. Send POST request to `/api/admin/users/99999/restore`

**Expected Result:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

### TC-ADMIN-RESTORE-004: Non-admin cannot restore users

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-RESTORE-004 |
| **Title** | STUDENT/LECTURER cannot restore users |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- Authenticated user with role = `STUDENT` or `LECTURER`

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/restore`

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ADMIN-RESTORE-005: Restore query bypasses soft delete filter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-RESTORE-005 |
| **Title** | Restore implementation finds soft deleted users |
| **Priority** | HIGH |
| **Type** | Positive - Implementation |

**Preconditions:**
- User U1 soft deleted

**Steps:**
1. Admin restores U1
2. Verify query implementation

**Expected Result:**
- Restore service uses native query or explicit filter bypass
- Example: `@Query("SELECT u FROM User u WHERE u.id = :id")` (no `@SQLRestriction`)
- Can find and restore soft deleted users

---

### TC-ADMIN-RESTORE-006: Restored user can login again

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-RESTORE-006 |
| **Title** | Restored user can authenticate successfully |
| **Priority** | HIGH |
| **Type** | Positive - Integration |

**Preconditions:**
- User U1 soft deleted then restored
- User status = `ACTIVE`

**Steps:**
1. Attempt login with U1 credentials

**Expected Result:**
- HTTP Status: `200 OK`
- Login successful, tokens generated
- User visible in all queries

---

## 8. UC-ADMIN-LOCK-ACCOUNT

### TC-ADMIN-LOCK-001: Admin locks account successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-001 |
| **Title** | ADMIN locks user account and revokes all tokens |
| **Priority** | CRITICAL |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- Target user U1 exists with status = `ACTIVE`

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/lock?reason=Suspicious%20activity`

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "message": "User locked successfully",
  "userId": "123"
}
```
- Database changes:
  - `users.status` = `LOCKED`
  - ALL refresh tokens for U1: `revoked = true`
- Audit log: Event `ACCOUNT_LOCKED` with reason = "Suspicious activity"

---

### TC-ADMIN-LOCK-002: Admin cannot lock self

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-002 |
| **Title** | ADMIN cannot lock own account (prevent lockout) |
| **Priority** | CRITICAL |
| **Type** | Negative - Business Rule |

**Preconditions:**
- Authenticated ADMIN user with ID = 1

**Steps:**
1. Send POST request to `/api/admin/users/1/lock` (self)

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response body:
```json
{
  "code": "INVALID_USER_STATE",
  "message": "Cannot lock own account",
  "timestamp": "2026-01-30T10:00:00Z"
}
```
- User NOT locked

---

### TC-ADMIN-LOCK-003: Locked user cannot login

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-003 |
| **Title** | Locked user cannot authenticate |
| **Priority** | CRITICAL |
| **Type** | Positive - Security |

**Preconditions:**
- User U1 locked (status = `LOCKED`)

**Steps:**
1. Attempt login with U1 credentials (CORRECT password)

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Account is locked. Contact admin."
- No tokens generated

---

### TC-ADMIN-LOCK-004: Locked user cannot refresh token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-004 |
| **Title** | Locked user cannot refresh token |
| **Priority** | CRITICAL |
| **Type** | Negative - Security |

**Preconditions:**
- User U1 logged in at T0 → has valid refresh token
- Admin locks U1 at T1

**Steps:**
1. U1 attempts to refresh token at T2

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Account is locked. Contact admin."
- Covered by TC-AUTH-REFRESH-006

---

### TC-ADMIN-LOCK-005: Admin lock with optional reason parameter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-005 |
| **Title** | Lock account with and without reason parameter |
| **Priority** | MEDIUM |
| **Type** | Positive - Optional Parameter |

**Steps:**
1. Lock user WITH reason: `/api/admin/users/{id}/lock?reason=Brute%20force`
2. Lock another user WITHOUT reason: `/api/admin/users/{id2}/lock`

**Expected Result:**
- Both requests succeed (200 OK)
- Audit log with reason if provided, NULL if not

---

### TC-ADMIN-LOCK-006: Admin lock idempotent

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-006 |
| **Title** | Lock already locked account succeeds |
| **Priority** | MEDIUM |
| **Type** | Positive - Idempotency |

**Preconditions:**
- User U1 already locked (status = `LOCKED`)

**Steps:**
1. Admin locks U1 again

**Expected Result:**
- HTTP Status: `200 OK`
- Response: "User locked successfully"
- Idempotent operation (no error)

---

### TC-ADMIN-LOCK-007: Non-admin cannot lock accounts

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-007 |
| **Title** | STUDENT/LECTURER cannot lock accounts |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- Authenticated user with role = `STUDENT` or `LECTURER`

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/lock`

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ADMIN-LOCK-008: Admin lock fails - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-LOCK-008 |
| **Title** | Lock rejected for non-existent user |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Steps:**
1. Send POST request to `/api/admin/users/99999/lock`

**Expected Result:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

## 9. UC-ADMIN-UNLOCK-ACCOUNT

### TC-ADMIN-UNLOCK-001: Admin unlocks account successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-UNLOCK-001 |
| **Title** | ADMIN unlocks locked user account |
| **Priority** | HIGH |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- User U1 locked (status = `LOCKED`)

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/unlock`

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "message": "User unlocked successfully",
  "userId": "123"
}
```
- Database: `users.status` = `ACTIVE`
- User can login again
- Audit log: Event `ACCOUNT_UNLOCKED`

---

### TC-ADMIN-UNLOCK-002: Admin unlock fails - User not locked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-UNLOCK-002 |
| **Title** | Unlock rejected when user not locked |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User U1 exists with status = `ACTIVE` (not locked)

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/unlock`

**Expected Result:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "User is not locked"

---

### TC-ADMIN-UNLOCK-003: Unlocked user can login

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-UNLOCK-003 |
| **Title** | Unlocked user can authenticate successfully |
| **Priority** | HIGH |
| **Type** | Positive - Integration |

**Preconditions:**
- User U1 was locked, then unlocked

**Steps:**
1. Attempt login with U1 credentials

**Expected Result:**
- HTTP Status: `200 OK`
- Login successful, tokens generated

---

### TC-ADMIN-UNLOCK-004: Non-admin cannot unlock accounts

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-UNLOCK-004 |
| **Title** | STUDENT/LECTURER cannot unlock accounts |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- Authenticated user with role = `STUDENT` or `LECTURER`

**Steps:**
1. Send POST request to `/api/admin/users/{userId}/unlock`

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ADMIN-UNLOCK-005: Admin unlock fails - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-UNLOCK-005 |
| **Title** | Unlock rejected for non-existent user |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Steps:**
1. Send POST request to `/api/admin/users/99999/unlock`

**Expected Result:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

## 10. UC-ADMIN-UPDATE-EXTERNAL-ACCOUNTS

### TC-ADMIN-EXTERNAL-001: Admin updates external accounts successfully

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-EXTERNAL-001 |
| **Title** | ADMIN updates Jira and GitHub account IDs |
| **Priority** | MEDIUM |
| **Type** | Positive - Happy Path |

**Preconditions:**
- Authenticated user with role = `ADMIN`
- User U1 exists

**Test Data:**
```json
{
  "jiraAccountId": "jira123456",
  "githubUsername": "user_github"
}
```

**Steps:**
1. Send PUT request to `/api/admin/users/{userId}/external-accounts`

**Expected Result:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "message": "External accounts updated successfully",
  "jiraAccountId": "jira123456",
  "githubUsername": "user_github"
}
```
- Database: User record updated with new values
- Audit log: Event `EXTERNAL_ACCOUNTS_UPDATED`

---

### TC-ADMIN-EXTERNAL-002: Admin update fails - Duplicate Jira account ID

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-EXTERNAL-002 |
| **Title** | Update rejected when Jira ID already used |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User U2 already has `jiraAccountId = "jira123456"`

**Test Data:**
```json
{
  "jiraAccountId": "jira123456",
  "githubUsername": "user1_github"
}
```

**Steps:**
1. Send PUT request for User U1 with U2's Jira ID

**Expected Result:**
- HTTP Status: `409 CONFLICT`
- Response: "Jira account ID already in use"

---

### TC-ADMIN-EXTERNAL-003: Admin update fails - Duplicate GitHub username

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-EXTERNAL-003 |
| **Title** | Update rejected when GitHub username already used |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Preconditions:**
- User U2 already has `githubUsername = "user_github"`

**Test Data:**
```json
{
  "jiraAccountId": "jira999999",
  "githubUsername": "user_github"
}
```

**Expected Result:**
- HTTP Status: `409 CONFLICT`
- Response: "GitHub username already in use"

---

### TC-ADMIN-EXTERNAL-004: Non-admin cannot update external accounts

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-EXTERNAL-004 |
| **Title** | STUDENT/LECTURER cannot update external accounts |
| **Priority** | HIGH |
| **Type** | Negative - Authorization |

**Preconditions:**
- Authenticated user with role = `STUDENT` or `LECTURER`

**Steps:**
1. Send PUT request to `/api/admin/users/{userId}/external-accounts`

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ADMIN-EXTERNAL-005: Admin update fails - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ADMIN-EXTERNAL-005 |
| **Title** | Update rejected for non-existent user |
| **Priority** | MEDIUM |
| **Type** | Negative - Business Rule |

**Steps:**
1. Send PUT request to `/api/admin/users/99999/external-accounts`

**Expected Result:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

## 11. JWT Validation

### TC-JWT-001: Valid JWT accepted by protected endpoints

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-001 |
| **Title** | Protected endpoint accepts valid JWT token |
| **Priority** | CRITICAL |
| **Type** | Positive - Security |

**Preconditions:**
- User logged in with valid access token
- Token not expired (< 15 minutes old)

**Steps:**
1. Send request to protected endpoint (e.g., `/api/admin/users`)
2. Include `Authorization: Bearer <access_token>` header

**Expected Result:**
- Request accepted
- SecurityContext populated with user details

---

### TC-JWT-002: Expired JWT rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-002 |
| **Title** | Protected endpoint rejects expired JWT token |
| **Priority** | CRITICAL |
| **Type** | Negative - Security |

**Preconditions:**
- JWT token issued > 15 minutes ago

**Steps:**
1. Send request with expired token

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token expired"

---

### TC-JWT-003: JWT with invalid signature rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-003 |
| **Title** | Protected endpoint rejects tampered JWT |
| **Priority** | CRITICAL |
| **Type** | Negative - Security |

**Steps:**
1. Take valid JWT token
2. Modify payload (e.g., change user ID)
3. Send request with modified token

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token signature"

---

### TC-JWT-004: Request without JWT rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-004 |
| **Title** | Protected endpoint rejects request without token |
| **Priority** | CRITICAL |
| **Type** | Negative - Security |

**Steps:**
1. Send request to protected endpoint
2. Do NOT include Authorization header

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Unauthorized"

---

### TC-JWT-005: Malformed JWT rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-005 |
| **Title** | Protected endpoint rejects malformed JWT |
| **Priority** | HIGH |
| **Type** | Negative - Security |

**Steps:**
1. Send request with `Authorization: Bearer invalid_token_string`

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token format"

---

### TC-JWT-006: JWT with wrong algorithm rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-006 |
| **Title** | System rejects JWT signed with wrong algorithm |
| **Priority** | CRITICAL |
| **Type** | Negative - Security |

**Steps:**
1. Create JWT signed with RS256 instead of HS256
2. Send request with this token

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token"

---

### TC-JWT-007: JWT with wrong token_type claim rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-007 |
| **Title** | Access endpoint rejects JWT with token_type=REFRESH |
| **Priority** | HIGH |
| **Type** | Negative - Security |

**Steps:**
1. Create JWT with `token_type = "REFRESH"`
2. Use it for protected endpoint (not refresh endpoint)

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token type"

---

### TC-JWT-008: JWT roles extracted to Spring Security authorities

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-008 |
| **Title** | JWT roles correctly mapped to Spring Security |
| **Priority** | HIGH |
| **Type** | Positive - Authorization |

**Preconditions:**
- User with role = `STUDENT` logged in

**Steps:**
1. JWT payload contains: `"roles": ["STUDENT"]`
2. System extracts roles and creates authorities
3. Check SecurityContext

**Expected Result:**
- SecurityContext contains `GrantedAuthority` with value `ROLE_STUDENT`
- `@PreAuthorize("hasRole('STUDENT')")` evaluates to true
- Spring Security automatically adds `ROLE_` prefix to authorities

---

### TC-JWT-009: JWT with missing required claims rejected

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-009 |
| **Title** | JWT rejected when missing required claims |
| **Priority** | HIGH |
| **Type** | Negative - Security |

**Steps:**
1. Create JWT without `sub` claim
2. Send request with this token

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token"

---

### TC-JWT-010: JWT bearer token format validated

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-JWT-010 |
| **Title** | Authorization header requires Bearer scheme |
| **Priority** | MEDIUM |
| **Type** | Negative - Validation |

**Steps:**
1. Send request with `Authorization: <token>` (without "Bearer")

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Required format: `Authorization: Bearer <token>`

---

## 12. Token Reuse Detection

### TC-REUSE-001: Token reuse detected and all user tokens revoked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-REUSE-001 |
| **Title** | Reused refresh token triggers revocation of all user tokens |
| **Priority** | CRITICAL |
| **Type** | Security - Attack Detection |

**Preconditions:**
- User U1 logged in at T0 → received refresh token R1
- User U1 refreshed at T1 → R1 revoked, received R2
- Attacker has copy of R1

**Steps:**
1. Attacker attempts to use R1 at T2
2. Verify all tokens revoked

**Expected Result:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token reuse detected. All sessions revoked."
- Database: ALL refresh tokens for U1 have `revoked = true`
- Legitimate user with R2 also logged out
- Audit log: Event `REFRESH_REUSE` with alert level `CRITICAL`

---

### TC-REUSE-002: Token reuse audit log captures attacker metadata

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-REUSE-002 |
| **Title** | Token reuse event logs IP and User-Agent |
| **Priority** | HIGH |
| **Type** | Security - Forensics |

**Steps:**
1. Trigger token reuse from IP = 192.168.1.100
2. Query audit log

**Expected Result:**
- Audit log record:
  - `action` = `REFRESH_REUSE`
  - `outcome` = `SECURITY_ALERT`
  - `ip_address` = "192.168.1.100"
  - `user_agent` captured
  - Alert level = `CRITICAL`

---

### TC-REUSE-003: Token reuse isolated per user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-REUSE-003 |
| **Title** | Token reuse only affects victim user |
| **Priority** | HIGH |
| **Type** | Security - Isolation |

**Preconditions:**
- User U1 has token reuse attack
- User U2 (different user) has active sessions

**Steps:**
1. Trigger token reuse for U1
2. Verify U2 sessions unaffected

**Expected Result:**
- User U1: All tokens revoked
- User U2: All tokens remain active

---

### TC-REUSE-004: Token reuse forces re-authentication

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-REUSE-004 |
| **Title** | Token reuse requires user to login again on all devices |
| **Priority** | CRITICAL |
| **Type** | Security - Session Management |

**Preconditions:**
- User has 3 active sessions (3 devices)
- Token reuse detected

**Steps:**
1. User attempts API call from any device with existing tokens

**Expected Result:**
- All 3 devices: `401 UNAUTHORIZED` "Token invalid"
- User must login again to get new tokens

---

### TC-REUSE-005: Token reuse detection does not affect other users

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-REUSE-005 |
| **Title** | Global service availability maintained during token reuse |
| **Priority** | MEDIUM |
| **Type** | Security - Availability |

**Steps:**
1. User U1 triggers token reuse
2. User U2 attempts refresh with valid token

**Expected Result:**
- User U2 refresh succeeds (200 OK)
- System remains available for other users

---

## 13. Role Format Validation

### TC-ROLE-001: Database stores roles without ROLE_ prefix

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ROLE-001 |
| **Title** | Database users.role column contains plain enum values |
| **Priority** | HIGH |
| **Type** | Positive - Data Format |

**Preconditions:**
- User registered with role = `STUDENT`

**Steps:**
1. Query database: `SELECT role FROM users WHERE email = ?`

**Expected Result:**
- Database value: `STUDENT` (NOT `ROLE_STUDENT`)
- Column type: `VARCHAR(50)`
- Allowed values: `ADMIN`, `LECTURER`, `STUDENT`

---

### TC-ROLE-002: JWT contains roles without ROLE_ prefix

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ROLE-002 |
| **Title** | JWT payload roles claim has plain strings |
| **Priority** | CRITICAL |
| **Type** | Positive - Token Format |

**Preconditions:**
- User with role = `ADMIN` logged in

**Steps:**
1. Extract access token
2. Decode JWT payload at jwt.io

**Expected Result:**
- JWT payload contains:
```json
{
  "roles": ["ADMIN"]
}
```
- NOT `["ROLE_ADMIN"]`

---

### TC-ROLE-003: Spring Security authorities have ROLE_ prefix

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ROLE-003 |
| **Title** | Internal Spring Security authorities use ROLE_ prefix |
| **Priority** | HIGH |
| **Type** | Positive - Security Context |

**Preconditions:**
- User with role = `LECTURER` logged in

**Steps:**
1. Extract authorities from SecurityContext
2. Verify format

**Expected Result:**
- SecurityContext authorities: `[ROLE_LECTURER]`
- Spring Security adds `ROLE_` prefix internally
- `JwtAuthenticationFilter` converts `"LECTURER"` → `"ROLE_LECTURER"`

---

### TC-ROLE-004: @PreAuthorize works with hasRole() without prefix

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ROLE-004 |
| **Title** | Controller @PreAuthorize uses hasRole without prefix |
| **Priority** | CRITICAL |
| **Type** | Positive - Authorization |

**Preconditions:**
- ADMIN user authenticated
- Controller has `@PreAuthorize("hasRole('ADMIN')")`

**Steps:**
1. ADMIN user calls endpoint

**Expected Result:**
- Access granted
- Spring Security automatically adds `ROLE_` prefix when evaluating
- `hasRole('ADMIN')` checks for `ROLE_ADMIN` authority

---

### TC-ROLE-005: STUDENT user cannot access ADMIN endpoints

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ROLE-005 |
| **Title** | Role-based access control enforced correctly |
| **Priority** | CRITICAL |
| **Type** | Negative - Authorization |

**Preconditions:**
- STUDENT user authenticated
- Endpoint has `@PreAuthorize("hasRole('ADMIN')")`

**Steps:**
1. STUDENT user calls ADMIN endpoint

**Expected Result:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"
- SecurityContext has `ROLE_STUDENT` but needs `ROLE_ADMIN`

---

### TC-ROLE-006: Role format documented in API responses

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ROLE-006 |
| **Title** | API responses show role without prefix |
| **Priority** | MEDIUM |
| **Type** | Positive - API Contract |

**Steps:**
1. User registers or logs in
2. Check response body

**Expected Result:**
- Response JSON:
```json
{
  "user": {
    "role": "STUDENT"
  }
}
```
- NOT `"ROLE_STUDENT"`

---

## 14. Audit Logging

### TC-AUDIT-001: Audit logging is asynchronous

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-001 |
| **Title** | Audit logging does not block main request |
| **Priority** | HIGH |
| **Type** | Performance - Async |

**Steps:**
1. User logs in
2. Measure response time
3. Verify audit log persisted

**Expected Result:**
- Login response time NOT delayed by audit logging
- Audit log method has `@Async` annotation
- Audit persisted in separate thread

---

### TC-AUDIT-002: Audit log uses separate transaction

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-002 |
| **Title** | Audit log persists even if main transaction rolls back |
| **Priority** | HIGH |
| **Type** | Positive - Transactional |

**Steps:**
1. Trigger operation that throws exception after audit log
2. Verify audit log persisted

**Expected Result:**
- Main transaction rolled back
- Audit log STILL persisted
- Audit method has `@Transactional(propagation = Propagation.REQUIRES_NEW)`

---

### TC-AUDIT-003: Audit log distinguishes LOGIN_FAILED vs LOGIN_DENIED

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-003 |
| **Title** | Different audit events for wrong password vs locked account |
| **Priority** | HIGH |
| **Type** | Positive - Audit Classification |

**Steps:**
1. Login with wrong password → check audit
2. Login with locked account (correct password) → check audit

**Expected Result:**
- Wrong password:
  - `action` = `LOGIN_FAILED`
  - `outcome` = `FAILURE`
- Locked account:
  - `action` = `LOGIN_DENIED`
  - `outcome` = `DENIED`

---

### TC-AUDIT-004: Audit log captures old_value and new_value

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-004 |
| **Title** | State changes logged with before/after values |
| **Priority** | HIGH |
| **Type** | Positive - Audit Detail |

**Steps:**
1. Admin locks user (status: ACTIVE → LOCKED)
2. Query audit log

**Expected Result:**
- Audit record:
  - `old_value` = `{"status": "ACTIVE"}`
  - `new_value` = `{"status": "LOCKED"}`
  - Format: JSON

---

### TC-AUDIT-005: Audit log resolves actor from SecurityContext

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-005 |
| **Title** | Audit log identifies actor from JWT claims |
| **Priority** | HIGH |
| **Type** | Positive - Actor Tracking |

**Steps:**
1. Admin (ID=5, email="admin@univ.edu") soft deletes user
2. Query audit log

**Expected Result:**
- Audit record:
  - `actor_id` = 5
  - `actor_email` = "admin@univ.edu"

---

### TC-AUDIT-006: Audit query by entity

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-006 |
| **Title** | Admin can query audit history of specific entity |
| **Priority** | MEDIUM |
| **Type** | Positive - Admin API |

**Steps:**
1. Send GET request to `/api/admin/audit/entity/User/123`

**Expected Result:**
- HTTP Status: `200 OK`
- Response contains all audit logs for User ID 123
- Sorted by timestamp DESC

---

### TC-AUDIT-007: Audit query by actor

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-007 |
| **Title** | Admin can query all actions performed by user |
| **Priority** | MEDIUM |
| **Type** | Positive - Admin API |

**Steps:**
1. Send GET request to `/api/admin/audit/actor/5`

**Expected Result:**
- HTTP Status: `200 OK`
- Response contains all actions performed by user ID 5

---

### TC-AUDIT-008: Audit query for security events

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-AUDIT-008 |
| **Title** | Admin can filter security-critical events |
| **Priority** | HIGH |
| **Type** | Positive - Admin API |

**Steps:**
1. Send GET request to `/api/admin/audit/security-events`

**Expected Result:**
- HTTP Status: `200 OK`
- Response contains:
  - `REFRESH_REUSE` events
  - Multiple `LOGIN_FAILED` from same IP
  - `LOGIN_DENIED` events
- Sorted by priority/timestamp

---

## Appendix: Test Data Management

### Recommended Test Users

| Email | Password | Role | Status |
|-------|----------|------|--------|
| admin@university.edu | Admin@123456 | ADMIN | ACTIVE |
| lecturer@university.edu | Lecturer@123 | LECTURER | ACTIVE |
| student@university.edu | Student@123 | STUDENT | ACTIVE |
| locked@university.edu | Locked@123 | STUDENT | LOCKED |
| deleted@university.edu | Deleted@123 | STUDENT | ACTIVE (soft deleted) |

### Test Environment Setup

```sql
-- Reset database
TRUNCATE TABLE refresh_tokens CASCADE;
TRUNCATE TABLE audit_logs CASCADE;
TRUNCATE TABLE users CASCADE;

-- Insert test users (password = BCrypt hash of specified password)
INSERT INTO users (email, password_hash, full_name, role, status, created_at) VALUES
('admin@university.edu', '$2a$10$...', 'System Admin', 'ADMIN', 'ACTIVE', NOW()),
('lecturer@university.edu', '$2a$10$...', 'Dr. Nguyễn Văn An', 'LECTURER', 'ACTIVE', NOW()),
('student@university.edu', '$2a$10$...', 'Trần Thị Bình', 'STUDENT', 'ACTIVE', NOW()),
('locked@university.edu', '$2a$10$...', 'Locked User', 'STUDENT', 'LOCKED', NOW());
```

---

## Document Control

**Version History:**

| Version | Date | Changes |
|---------|------|---------|
| 2.0 | 2026-01-30 | Complete rewrite based on latest security fixes and design documents |
| 1.0 | 2026-01-29 | Initial draft |

**References:**
- [API_CONTRACT.md](API_CONTRACT.md)
- [SRS-Auth.md](SRS-Auth.md)
- [Authentication-Authorization-Design.md](Authentication-Authorization-Design.md)
- [Security-Review.md](Security-Review.md)
- [SECURITY_FIXES_SUMMARY.md](SECURITY_FIXES_SUMMARY.md)
- [ROLE_FORMAT_CLARIFICATION.md](ROLE_FORMAT_CLARIFICATION.md)
- [Database-Design.md](Database-Design.md)

**Approval:**
- QA Lead: _____________________
- Security Reviewer: _____________________
- Date: _____________________
