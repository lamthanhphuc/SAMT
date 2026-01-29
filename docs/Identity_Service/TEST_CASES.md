# TEST CASES – IDENTITY SERVICE

## Test Case Overview

| Category | Test Count | Priority |
|----------|------------|----------|
| Register (UC-REGISTER) | 15 | HIGH |
| Login (UC-LOGIN) | 12 | CRITICAL |
| Refresh Token (UC-REFRESH-TOKEN) | 10 | CRITICAL |
| Logout (UC-LOGOUT) | 6 | HIGH |
| Admin Soft Delete User (UC-SOFT-DELETE) | 8 | CRITICAL |
| Admin Restore User (UC-RESTORE) | 6 | HIGH |
| Admin Lock Account (UC-LOCK-ACCOUNT) | 7 | CRITICAL |
| Admin Unlock Account (UC-UNLOCK-ACCOUNT) | 5 | HIGH |
| JWT Validation | 8 | CRITICAL |
| Token Reuse Detection | 5 | CRITICAL |
| Audit Logging | 10 | HIGH |
| Validation Rules | 12 | HIGH |
| Security Tests | 10 | CRITICAL |
| **TOTAL** | **114** | - |

---

## 1. REGISTER (UC-REGISTER)

### TC-ID-001: Register thành công với dữ liệu hợp lệ

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-001 |
| **Tên Test Case** | Register successfully with valid data |
| **Mô tả** | Guest đăng ký tài khoản STUDENT với tất cả fields hợp lệ |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Email "student@university.edu" chưa tồn tại trong database

**Các bước thực hiện:**
1. Gửi POST request đến `/api/auth/register`
2. Body: Valid registration data

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Response:
```json
{
  "user": {
    "id": 1,
    "email": "student@university.edu",
    "fullName": "Nguyen Van A",
    "role": "STUDENT",
    "status": "ACTIVE",
    "createdAt": "2026-01-29T10:00:00Z"
  },
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- Database: 
  - 1 record mới trong `users` table với `status = ACTIVE`
  - Password được hash với BCrypt (strength 10)
  - 1 refresh token mới trong `refresh_tokens` table
- Audit log: Event `CREATE` được ghi

---

### TC-ID-002: Register thất bại - Email already exists

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-002 |
| **Tên Test Case** | Register fails - Email already exists |
| **Mô tả** | Đăng ký với email đã tồn tại trong database |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Email "existing@university.edu" đã tồn tại trong database

**Dữ liệu test:**
```json
{
  "email": "existing@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyen Van B",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "EMAIL_ALREADY_EXISTS",
  "message": "Email already registered",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Database: Không có record mới

---

### TC-ID-003: Register thất bại - Invalid email format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-003 |
| **Tên Test Case** | Register fails - Invalid email format |
| **Mô tả** | Đăng ký với email không đúng RFC 5322 format |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "invalid-email",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "email",
      "message": "Invalid email format"
    }
  ]
}
```

---

### TC-ID-004: Register thất bại - Password too weak

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-004 |
| **Tên Test Case** | Register fails - Password too weak |
| **Mô tả** | Đăng ký với password không đáp ứng yêu cầu (thiếu uppercase, special char) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "weakpass",
  "confirmPassword": "weakpass",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "password",
      "message": "Password does not meet requirements"
    }
  ]
}
```

---

### TC-ID-005: Register thất bại - Passwords don't match

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-005 |
| **Tên Test Case** | Register fails - Passwords don't match |
| **Mô tả** | Đăng ký với password và confirmPassword không giống nhau |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "DifferentPass@123",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response:
```json
{
  "code": "PASSWORD_MISMATCH",
  "message": "Passwords do not match",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-ID-006: Register thất bại - Invalid role (LECTURER/ADMIN)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-006 |
| **Tên Test Case** | Register fails - Invalid role (LECTURER/ADMIN not allowed) |
| **Mô tả** | Guest cố đăng ký với role LECTURER hoặc ADMIN (chỉ ADMIN mới tạo được) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Dữ liệu test:**
```json
{
  "email": "lecturer@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Dr. Nguyen Van A",
  "role": "LECTURER"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response:
```json
{
  "code": "VALIDATION_ERROR",
  "message": "Validation failed",
  "timestamp": "2026-01-29T10:00:00Z",
  "errors": [
    {
      "field": "role",
      "message": "Invalid role specified"
    }
  ]
}
```

---

### TC-ID-007: Register thất bại - Full name too short

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-007 |
| **Tên Test Case** | Register fails - Full name too short |
| **Mô tả** | Đăng ký với fullName < 2 characters |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Name must be 2-100 characters"

---

### TC-ID-008: Register thất bại - Full name too long

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-008 |
| **Tên Test Case** | Register fails - Full name too long |
| **Mô tả** | Đăng ký với fullName > 100 characters |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Name must be 2-100 characters"

---

### TC-ID-009: Register thành công - Vietnamese name với dấu

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-009 |
| **Tên Test Case** | Register successfully - Vietnamese name with accents |
| **Mô tả** | Đăng ký với tên tiếng Việt có dấu (Unicode) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyễn Văn Ánh",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- fullName = "Nguyễn Văn Ánh" (Unicode được accept)
- Pattern `\p{L}` match Unicode letters

---

### TC-ID-010: Register thất bại - Full name với số

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-010 |
| **Tên Test Case** | Register fails - Full name with numbers |
| **Mô tả** | Đăng ký với fullName chứa số |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyen123",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Name contains invalid characters"

---

### TC-ID-011: Register thất bại - Password min length

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-011 |
| **Tên Test Case** | Register fails - Password too short (< 8 chars) |
| **Mô tả** | Đăng ký với password < 8 characters |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "Pass@1",
  "confirmPassword": "Pass@1",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must be at least 8 characters"

---

### TC-ID-012: Register thất bại - Password missing uppercase

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-012 |
| **Tên Test Case** | Register fails - Password missing uppercase |
| **Mô tả** | Đăng ký với password không có chữ hoa |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "securepass@123",
  "confirmPassword": "securepass@123",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 uppercase letter"

---

### TC-ID-013: Register thất bại - Password missing special char

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-013 |
| **Tên Test Case** | Register fails - Password missing special character |
| **Mô tả** | Đăng ký với password không có ký tự đặc biệt |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass123",
  "confirmPassword": "SecurePass123",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 special character (@$!%*?&)"

---

### TC-ID-014: Verify password được hash với BCrypt

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-014 |
| **Tên Test Case** | Verify password is hashed with BCrypt (strength 10) |
| **Mô tả** | Kiểm tra password được hash bằng BCrypt strength 10 |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Encryption |

**Điều kiện tiên quyết:**
- User đã register thành công (TC-ID-001)

**Các bước thực hiện:**
1. Register user với password "SecurePass@123"
2. Query trực tiếp database: `SELECT password_hash FROM users WHERE email = ?`
3. Verify password_hash format

**Kết quả mong đợi:**
- Database `password_hash` ≠ plaintext password
- `password_hash` format: `$2a$10$...` (BCrypt với strength 10)
- Length ≈ 60 characters
- Verify với `BCryptPasswordEncoder.matches("SecurePass@123", hash)` → true

---

### TC-ID-015: Register thất bại - Email max length

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-015 |
| **Tên Test Case** | Register fails - Email exceeds max length (255 chars) |
| **Mô tả** | Đăng ký với email > 255 characters |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "verylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddressverylongemailaddress@university.edu",
  "password": "SecurePass@123",
  "confirmPassword": "SecurePass@123",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Email must not exceed 255 characters"

---

## 2. LOGIN (UC-LOGIN)

### TC-ID-016: Login thành công với credentials hợp lệ

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-016 |
| **Tên Test Case** | Login successfully with valid credentials |
| **Mô tả** | User login với email và password đúng |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User tồn tại với email "student@university.edu" và password "SecurePass@123"
- User status = ACTIVE

**Các bước thực hiện:**
1. Gửi POST request đến `/api/auth/login`
2. Body: Valid credentials

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "SecurePass@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- Access token: JWT với TTL = 15 minutes
- Refresh token: UUID được lưu vào database với TTL = 7 days
- Audit log: Event `LOGIN_SUCCESS` với outcome = SUCCESS

---

### TC-ID-017: Login thất bại - Email not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-017 |
| **Tên Test Case** | Login fails - Email not found |
| **Mô tả** | Login với email không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Dữ liệu test:**
```json
{
  "email": "nonexistent@university.edu",
  "password": "SecurePass@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- **Security:** Không tiết lộ "Email not found" (prevent enumeration)
- Audit log: Event `LOGIN_FAILED` với outcome = FAILURE

---

### TC-ID-018: Login thất bại - Password incorrect

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-018 |
| **Tên Test Case** | Login fails - Password incorrect |
| **Mô tả** | Login với password sai |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Điều kiện tiên quyết:**
- User tồn tại với email "student@university.edu"

**Dữ liệu test:**
```json
{
  "email": "student@university.edu",
  "password": "WrongPassword@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "Invalid credentials",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- **Security:** Same error message như email not found (prevent enumeration)
- Audit log: Event `LOGIN_FAILED` với outcome = FAILURE

---

### TC-ID-019: Login thất bại - Account locked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-019 |
| **Tén Test Case** | Login fails - Account locked |
| **Mô tả** | Login với account có status = LOCKED |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User tồn tại với email "locked@university.edu"
- User status = LOCKED
- Password đúng

**Dữ liệu test:**
```json
{
  "email": "locked@university.edu",
  "password": "SecurePass@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "ACCOUNT_LOCKED",
  "message": "Account is locked",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- **Note:** Error khác với invalid credentials (cho phép hiển thị locked status sau khi xác thực password)
- Audit log: Event `LOGIN_DENIED` với outcome = DENIED

---

### TC-ID-020: Login với password validation trước status check

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-020 |
| **Tên Test Case** | Login validates password BEFORE status check (prevent enumeration) |
| **Mô tả** | Verify password được validate TRƯỚC khi check account locked |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Account Enumeration Prevention |

**Điều kiện tiên quyết:**
- User tồn tại với status = LOCKED

**Dữ liệu test:**
```json
{
  "email": "locked@university.edu",
  "password": "WrongPassword@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid credentials" (KHÔNG phải "Account is locked")
- **Security:** Attacker không thể enumerate locked accounts bằng cách thử password sai
- Flow: Find user → Validate password (fail) → Return invalid credentials (KHÔNG check status)

---

### TC-ID-021: Login thành công - Multiple devices

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-021 |
| **Tên Test Case** | Login successfully - Multiple devices/sessions |
| **Mô tả** | User có thể login nhiều lần (multiple devices) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Các bước thực hiện:**
1. User login lần 1 từ device A
2. User login lần 2 từ device B (cùng credentials)

**Kết quả mong đợi:**
- Cả 2 login đều thành công (200 OK)
- Database có 2 refresh tokens khác nhau cho cùng user_id
- Cả 2 sessions đều active

---

### TC-ID-022: Verify JWT claims structure

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-022 |
| **Tên Test Case** | Verify JWT access token claims structure |
| **Mô tả** | Kiểm tra JWT access token chứa đúng claims |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Token Structure |

**Điều kiện tiên quyết:**
- User đã login thành công

**Các bước thực hiện:**
1. Login user
2. Decode access token (JWT)
3. Verify claims

**Kết quả mong đợi:**
- JWT header: `{"alg": "HS256", "typ": "JWT"}`
- JWT claims:
```json
{
  "sub": "1",
  "email": "student@university.edu",
  "roles": ["ROLE_STUDENT"],
  "iat": 1738148400,
  "exp": 1738149300,
  "token_type": "ACCESS"
}
```
- Signature valid với JWT_SECRET
- TTL = 15 minutes (900 seconds)

---

### TC-ID-023: Verify refresh token format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-023 |
| **Tên Test Case** | Verify refresh token format (UUID, not JWT) |
| **Mô tả** | Kiểm tra refresh token là UUID opaque string, KHÔNG phải JWT |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Token Structure |

**Điều kiện tiên quyết:**
- User đã login thành công

**Các bước thực hiện:**
1. Login user
2. Extract refresh token từ response
3. Verify format

**Kết quả mong đợi:**
- Refresh token format: UUID v4 (e.g., `550e8400-e29b-41d4-a716-446655440000`)
- KHÔNG phải JWT (không có 3 parts separated by dots)
- Stored in database `refresh_tokens` table
- TTL = 7 days

---

### TC-ID-024: Login thất bại - Missing email

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-024 |
| **Tên Test Case** | Login fails - Missing email field |
| **Mô tả** | Login với request thiếu email |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "password": "SecurePass@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Email is required"

---

### TC-ID-025: Login thất bại - Missing password

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-025 |
| **Tên Test Case** | Login fails - Missing password field |
| **Mô tả** | Login với request thiếu password |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "student@university.edu"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password is required"

---

### TC-ID-026: Login không thể xóa soft deleted user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-026 |
| **Tên Test Case** | Login fails - Soft deleted user cannot login |
| **Mô tả** | User đã bị soft delete không thể login |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Soft Delete |

**Điều kiện tiên quyết:**
- User tồn tại với email "deleted@university.edu"
- User đã bị soft delete (deleted_at IS NOT NULL)

**Dữ liệu test:**
```json
{
  "email": "deleted@university.edu",
  "password": "SecurePass@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid credentials"
- `@SQLRestriction("deleted_at IS NULL")` filter soft deleted users
- Soft deleted user không tìm thấy trong query

---

### TC-ID-027: Login audit log capture IP và User-Agent

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-027 |
| **Tên Test Case** | Login audit log captures IP address and User-Agent |
| **Mô tả** | Kiểm tra audit log ghi lại IP và User-Agent |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Audit Logging |

**Các bước thực hiện:**
1. Login với IP address = 192.168.1.100
2. User-Agent = "Mozilla/5.0..."
3. Query audit_logs table

**Kết quả mong đợi:**
- Audit log record:
  - `action = LOGIN_SUCCESS`
  - `ip_address = "192.168.1.100"`
  - `user_agent = "Mozilla/5.0..."`
  - `actor_email = "student@university.edu"`
  - `outcome = SUCCESS`

---

## 3. REFRESH TOKEN (UC-REFRESH-TOKEN)

### TC-ID-028: Refresh token thành công với valid token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-028 |
| **Tên Test Case** | Refresh token successfully with valid token |
| **Mô tả** | Client refresh access token với valid refresh token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User đã login
- Có valid refresh token (chưa revoked, chưa expired)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/auth/refresh`
2. Body: Valid refresh token

**Dữ liệu test:**
```json
{
  "refreshToken": "550e8400-e29b-41d4-a716-446655440000"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "refreshToken": "a1b2c3d4-e5f6-47g8-h9i0-j1k2l3m4n5o6",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- Old refresh token bị revoke (revoked = true)
- New refresh token được tạo và lưu vào database
- New access token với TTL = 15 minutes
- Audit log: Event `REFRESH_SUCCESS`

---

### TC-ID-029: Refresh token thất bại - Token expired

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-029 |
| **Tên Test Case** | Refresh token fails - Token expired |
| **Mô tả** | Refresh với token đã hết hạn (> 7 days) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Refresh token tồn tại
- Token `expires_at` < NOW()

**Các bước thực hiện:**
1. Gửi POST request với expired token

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "TOKEN_EXPIRED",
  "message": "Token expired",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-ID-030: Refresh token thất bại - Token not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-030 |
| **Tên Test Case** | Refresh token fails - Token not found |
| **Mô tả** | Refresh với token không tồn tại trong database |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Dữ liệu test:**
```json
{
  "refreshToken": "99999999-9999-9999-9999-999999999999"
}
```

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "TOKEN_INVALID",
  "message": "Token invalid",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-ID-031: Refresh token thất bại - Token already revoked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-031 |
| **Tên Test Case** | Refresh token fails - Token already revoked |
| **Mô tả** | Refresh với token đã bị revoke |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Điều kiện tiên quyết:**
- Refresh token tồn tại
- Token `revoked = true`

**Các bước thực hiện:**
1. Gửi POST request với revoked token

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token invalid"

---

### TC-ID-032: Token rotation - Old token bị revoke

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-032 |
| **Tên Test Case** | Token rotation - Old token is revoked after refresh |
| **Mô tả** | Verify old token bị revoke ngay sau khi refresh thành công |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Các bước thực hiện:**
1. Refresh token lần 1 với token T1 → nhận token T2
2. Query database: `SELECT revoked FROM refresh_tokens WHERE token = T1`
3. Cố refresh lại với token T1

**Kết quả mong đợi:**
- Database: T1.revoked = true
- Refresh với T1 lần 2 → 401 UNAUTHORIZED "Token invalid"
- Chỉ T2 valid

---

### TC-ID-033: Token reuse detection - Revoke all tokens

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-033 |
| **Tên Test Case** | Token reuse detection - Revoke ALL tokens on reuse |
| **Mô tả** | Khi detect reuse của revoked token, revoke ALL tokens của user |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Điều kiện tiên quyết:**
- User có 3 active refresh tokens (3 devices)
- Token T1 đã được sử dụng để refresh → đã revoked

**Các bước thực hiện:**
1. Attacker cố reuse token T1 (đã revoked)
2. System detect reuse attack
3. Query database: `SELECT revoked FROM refresh_tokens WHERE user_id = ?`

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token invalid"
- **Security action:** TẤT CẢ 3 tokens bị revoke (revoked = true)
- User bị force logout khỏi tất cả devices
- Audit log: Event `REFRESH_REUSE` với alert level CRITICAL

---

### TC-ID-034: Refresh token kiểm tra account status

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-034 |
| **Tén Test Case** | Refresh token checks account status (LOCKED users cannot refresh) |
| **Mô tả** | User bị lock không thể refresh token (even với valid token) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Điều kiện tiên quyết:**
- User login → nhận refresh token T1
- Admin lock account → status = LOCKED
- Token T1 vẫn chưa expired

**Các bước thực hiện:**
1. User cố refresh với token T1

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Account is locked"
- Token T1 bị revoke
- Audit log: Event với action = REFRESH_DENIED

---

### TC-ID-035: Refresh token thành công - Soft deleted user KHÔNG refresh được

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-035 |
| **Tên Test Case** | Refresh token fails - Soft deleted user cannot refresh |
| **Mô tả** | User đã bị soft delete không thể refresh token |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Soft Delete |

**Điều kiện tiên quyết:**
- User login → nhận refresh token T1
- Admin soft delete user → deleted_at set
- Token T1 vẫn chưa expired

**Các bước thực hiện:**
1. User cố refresh với token T1

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token invalid"
- `@SQLRestriction` filter user → user not found
- Token không thể refresh

---

### TC-ID-036: Refresh token TTL = 7 days

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-036 |
| **Tén Test Case** | Verify refresh token TTL = 7 days |
| **Mô tả** | Kiểm tra refresh token hết hạn sau 7 ngày |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Token Lifecycle |

**Các bước thực hiện:**
1. User login → nhận refresh token T1
2. Query database: `SELECT expires_at FROM refresh_tokens WHERE token = T1`
3. Verify expires_at

**Kết quả mong đợi:**
- `expires_at = created_at + 7 days`
- Token valid trong 7 ngày
- Sau 7 ngày → token expired, cannot refresh

---

### TC-ID-037: Refresh token audit log async

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-037 |
| **Tên Test Case** | Refresh token audit logging is asynchronous |
| **Mô tả** | Verify audit logging không block refresh request |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Performance - Async |

**Các bước thực hiện:**
1. Refresh token
2. Measure response time
3. Verify audit log được ghi

**Kết quả mong đợi:**
- Response time không bị delay bởi audit logging
- Audit log được ghi async (`@Async` annotation)
- Audit log persist trong separate transaction

---

## 4. LOGOUT (UC-LOGOUT)

### TC-ID-038: Logout thành công với valid tokens

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-038 |
| **Tên Test Case** | Logout successfully with valid tokens |
| **Mô tả** | User logout thành công, refresh token bị revoke |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User đã login
- Có valid access token và refresh token

**Các bước thực hiện:**
1. Gửi POST request đến `/api/auth/logout`
2. Header: `Authorization: Bearer <accessToken>`
3. Body: `{"refreshToken": "550e8400-..."}`

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- Database: refresh token bị revoke (revoked = true)
- Audit log: Event `LOGOUT` được ghi

---

### TC-ID-039: Logout thất bại - Missing access token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-039 |
| **Tên Test Case** | Logout fails - Missing access token |
| **Mô tả** | Logout không có JWT token trong header |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Authentication |

**Các bước thực hiện:**
1. Gửi POST request đến `/api/auth/logout`
2. KHÔNG gửi header Authorization
3. Body: `{"refreshToken": "550e8400-..."}`

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Unauthorized"

---

### TC-ID-040: Logout idempotent - Token not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-040 |
| **Tên Test Case** | Logout is idempotent - Token not found returns 204 |
| **Mô tả** | Logout với refresh token không tồn tại vẫn trả về 204 (silent success) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Idempotency |

**Các bước thực hiện:**
1. Gửi logout request với refresh token không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- **Design:** Idempotent - không leak information về token existence

---

### TC-ID-041: Logout idempotent - Token already revoked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-041 |
| **Tên Test Case** | Logout is idempotent - Already revoked token returns 204 |
| **Mô tả** | Logout lần 2 với token đã revoked vẫn trả về 204 |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Idempotency |

**Các bước thực hiện:**
1. Logout lần 1 → token revoked
2. Logout lần 2 với cùng token

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT` (cả 2 lần)
- **Design:** Calling logout multiple times has same effect

---

### TC-ID-042: Logout với expired access token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-042 |
| **Tên Test Case** | Logout fails - Expired access token |
| **Mô tả** | Logout với access token đã hết hạn (> 15 minutes) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Token Expiration |

**Điều kiện tiên quyết:**
- Access token đã expired (> 15 minutes since issued)

**Các bước thực hiện:**
1. Gửi logout request với expired access token

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token expired"
- Refresh token KHÔNG bị revoke (do không authenticate được)

---

### TC-ID-043: Logout revoke chỉ 1 token (not all)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-043 |
| **Tén Test Case** | Logout revokes only specified token (not all tokens) |
| **Mô tả** | Logout chỉ revoke token được chỉ định, không ảnh hưởng tokens khác |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Multi-Device |

**Điều kiện tiên quyết:**
- User có 3 refresh tokens (3 devices): T1, T2, T3

**Các bước thực hiện:**
1. Logout device 1 với token T1
2. Query database: `SELECT revoked FROM refresh_tokens WHERE user_id = ?`

**Kết quả mong đợi:**
- T1: revoked = true
- T2, T3: revoked = false (still active)
- User vẫn logged in trên device 2 và 3

---

## 5. ADMIN SOFT DELETE USER (UC-SOFT-DELETE)

### TC-ID-044: Admin soft delete user thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-044 |
| **Tên Test Case** | Admin soft delete user successfully |
| **Mô tả** | ADMIN soft delete user, revoke all tokens |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Admin login với role ADMIN
- Target user U1 tồn tại (status = ACTIVE, chưa deleted)

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/admin/users/{userId}`
2. userId = U1
3. Header: `Authorization: Bearer <admin_access_token>`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "message": "User deleted successfully",
  "userId": "123"
}
```
- Database:
  - `users.deleted_at = NOW()`
  - `users.deleted_by = admin_id`
  - Tất cả refresh tokens của U1: revoked = true
- Audit log: Event `SOFT_DELETE` với actor = admin

---

### TC-ID-045: Admin soft delete thất bại - Not ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-045 |
| **Tén Test Case** | Admin soft delete fails - Non-ADMIN cannot delete |
| **Mô tả** | STUDENT/LECTURER không thể delete user |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/admin/users/{userId}`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ID-046: Admin soft delete thất bại - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-046 |
| **Tên Test Case** | Admin soft delete fails - User not found |
| **Mô tả** | Delete user không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Logic |

**Các bước thực hiện:**
1. Admin gửi DELETE request với userId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

### TC-ID-047: Admin soft delete thất bại - User already deleted

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-047 |
| **Tên Test Case** | Admin soft delete fails - User already deleted |
| **Mô tả** | Delete user đã bị soft delete trước đó |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 đã bị soft delete (deleted_at IS NOT NULL)

**Các bước thực hiện:**
1. Admin cố delete U1 lần nữa

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "User already deleted"

---

### TC-ID-048: Admin soft delete thất bại - Cannot delete self

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-048 |
| **Tên Test Case** | Admin soft delete fails - Cannot delete own account |
| **Mô tả** | Admin không thể delete chính mình (prevent lockout) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Admin A1 login
- Target userId = A1's own ID

**Các bước thực hiện:**
1. Admin A1 cố delete chính mình

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "Cannot delete own account"

---

### TC-ID-049: Soft delete cascade revoke all tokens

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-049 |
| **Tên Test Case** | Soft delete cascades to revoke ALL refresh tokens |
| **Mô tả** | Khi soft delete user, tất cả refresh tokens bị revoke |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Cascade |

**Điều kiện tiên quyết:**
- User U1 có 3 active refresh tokens (3 devices)

**Các bước thực hiện:**
1. Admin soft delete U1
2. Query database: `SELECT revoked FROM refresh_tokens WHERE user_id = U1`

**Kết quả mong đợi:**
- Tất cả 3 tokens: revoked = true
- User bị force logout khỏi tất cả devices

---

### TC-ID-050: Soft delete audit log ghi deleted_by

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-050 |
| **Tén Test Case** | Soft delete audit log records deleted_by (admin ID) |
| **Mô tả** | Audit log và users table ghi admin ID thực hiện delete |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Audit |

**Các bước thực hiện:**
1. Admin A1 (userId = 5) soft delete user U1 (userId = 10)
2. Query database:
   - `SELECT deleted_by FROM users WHERE id = 10`
   - `SELECT actor_id FROM audit_logs WHERE action = 'SOFT_DELETE' AND entity_id = 10`

**Kết quả mong đợi:**
- `users.deleted_by = 5`
- Audit log: `actor_id = 5`, `action = SOFT_DELETE`

---

### TC-ID-051: Soft deleted user không thể login

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-051 |
| **Tén Test Case** | Soft deleted user cannot login |
| **Mô tả** | User đã bị soft delete không thể login |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Security |

**Điều kiện tiên quyết:**
- User U1 đã bị soft delete

**Các bước thực hiện:**
1. User U1 cố login với credentials đúng

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid credentials"
- `@SQLRestriction` filter user → user not found

---

## 6. ADMIN RESTORE USER (UC-RESTORE)

### TC-ID-052: Admin restore user thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-052 |
| **Tên Test Case** | Admin restore user successfully |
| **Mô tả** | ADMIN restore user đã bị soft delete |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Admin login với role ADMIN
- User U1 đã bị soft delete (deleted_at IS NOT NULL)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/admin/users/{userId}/restore`
2. userId = U1
3. Header: `Authorization: Bearer <admin_access_token>`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "message": "User restored successfully",
  "userId": "123"
}
```
- Database:
  - `users.deleted_at = NULL`
  - `users.deleted_by = NULL`
- User có thể login lại (nếu status = ACTIVE)
- Audit log: Event `RESTORE` với actor = admin

---

### TC-ID-053: Admin restore thất bại - Not ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-053 |
| **Tên Test Case** | Admin restore fails - Non-ADMIN cannot restore |
| **Mô tả** | STUDENT/LECTURER không thể restore user |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi POST request đến `/api/admin/users/{userId}/restore`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ID-054: Admin restore thất bại - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-054 |
| **Tên Test Case** | Admin restore fails - User not found |
| **Mô tả** | Restore user không tồn tại (kể cả trong soft deleted) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Logic |

**Các bước thực hiện:**
1. Admin gửi POST request với userId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

### TC-ID-055: Admin restore thất bại - User not deleted

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-055 |
| **Tên Test Case** | Admin restore fails - User is not deleted |
| **Mô tả** | Restore user chưa bị delete (deleted_at = NULL) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 tồn tại
- User U1 CHƯA bị soft delete (deleted_at = NULL)

**Các bước thực hiện:**
1. Admin cố restore U1

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "User is not deleted"

---

### TC-ID-056: Restore user query bypass @SQLRestriction

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-056 |
| **Tên Test Case** | Restore user query bypasses @SQLRestriction to find deleted users |
| **Mô tả** | Restore query sử dụng native query để bypass soft delete filter |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Implementation |

**Các bước thực hiện:**
1. User U1 bị soft delete
2. Admin restore U1
3. Verify query implementation

**Kết quả mong đợi:**
- Restore service sử dụng native query hoặc `@Query` with explicit filter bypass
- Có thể find user đã soft delete
- Example: `@Query("SELECT u FROM User u WHERE u.id = :id")`

---

### TC-ID-057: Restored user có thể login lại

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-057 |
| **Tén Test Case** | Restored user can login again |
| **Mô tả** | User sau khi restore có thể login lại bình thường |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Integration |

**Các bước thực hiện:**
1. User U1 bị soft delete
2. Admin restore U1
3. User U1 login

**Kết quả mong đợi:**
- Login thành công (200 OK)
- Nhận được access token và refresh token
- User visible trong queries (không bị filter)

---

## 7. ADMIN LOCK ACCOUNT (UC-LOCK-ACCOUNT)

### TC-ID-058: Admin lock account thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-058 |
| **Tén Test Case** | Admin lock account successfully |
| **Mô tả** | ADMIN lock user account, revoke all tokens |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Admin login với role ADMIN
- User U1 tồn tại (status = ACTIVE)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/admin/users/{userId}/lock?reason=Suspicious%20activity`
2. userId = U1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "message": "User locked successfully",
  "userId": "123"
}
```
- Database:
  - `users.status = LOCKED`
  - Tất cả refresh tokens của U1: revoked = true
- Audit log: Event `ACCOUNT_LOCKED` với reason = "Suspicious activity"

---

### TC-ID-059: Admin lock account thất bại - Not ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-059 |
| **Tên Test Case** | Admin lock account fails - Non-ADMIN cannot lock |
| **Mô tả** | STUDENT/LECTURER không thể lock account |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Các bước thực hiện:**
1. User với role STUDENT/LECTURER gửi POST request đến `/api/admin/users/{userId}/lock`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ID-060: Admin lock account thất bại - Cannot lock self

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-060 |
| **Tên Test Case** | Admin lock account fails - Cannot lock own account |
| **Mô tả** | Admin không thể lock chính mình (prevent lockout) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Admin A1 login
- Target userId = A1's own ID

**Các bước thực hiện:**
1. Admin A1 cố lock chính mình

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "Cannot lock own account"

---

### TC-ID-061: Admin lock account idempotent

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-061 |
| **Tén Test Case** | Admin lock account is idempotent |
| **Mô tả** | Lock account đã locked trả về 200 OK (no error) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Idempotency |

**Điều kiện tiên quyết:**
- User U1 đã bị lock (status = LOCKED)

**Các bước thực hiện:**
1. Admin lock U1 lần nữa

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response: "User locked successfully"
- **Design:** Idempotent operation
- Không tạo duplicate audit log

---

### TC-ID-062: Locked user không thể login

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-062 |
| **Tén Test Case** | Locked user cannot login |
| **Mô tả** | User bị lock không thể login |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Điều kiện tiên quyết:**
- User U1 bị lock (status = LOCKED)

**Các bước thực hiện:**
1. User U1 cố login với credentials đúng

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Account is locked"
- Audit log: Event `LOGIN_DENIED` với outcome = DENIED

---

### TC-ID-063: Locked user không thể refresh token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-063 |
| **Tên Test Case** | Locked user cannot refresh token |
| **Mô tả** | User bị lock không thể refresh token (even với valid token trước đó) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Điều kiện tiên quyết:**
- User U1 login → nhận refresh token T1
- Admin lock U1

**Các bước thực hiện:**
1. User U1 cố refresh với token T1

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Account is locked"
- Token T1 bị revoke

---

### TC-ID-064: Lock account với optional reason

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-064 |
| **Tén Test Case** | Lock account with optional reason parameter |
| **Mô tả** | Admin có thể lock account với/không có reason |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Optional Parameter |

**Các bước thực hiện:**
1. Lock user với reason: `/api/admin/users/{userId}/lock?reason=Brute%20force%20attempt`
2. Lock user không có reason: `/api/admin/users/{userId}/lock`

**Kết quả mong đợi:**
- Cả 2 request đều thành công (200 OK)
- Audit log với reason nếu có, NULL nếu không

---

## 8. ADMIN UNLOCK ACCOUNT (UC-UNLOCK-ACCOUNT)

### TC-ID-065: Admin unlock account thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-065 |
| **Tên Test Case** | Admin unlock account successfully |
| **Mô tả** | ADMIN unlock user account |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Admin login với role ADMIN
- User U1 bị lock (status = LOCKED)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/admin/users/{userId}/unlock`
2. userId = U1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "message": "User unlocked successfully",
  "userId": "123"
}
```
- Database: `users.status = ACTIVE`
- User có thể login lại
- Audit log: Event `ACCOUNT_UNLOCKED`

---

### TC-ID-066: Admin unlock thất bại - Not ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-066 |
| **Tên Test Case** | Admin unlock fails - Non-ADMIN cannot unlock |
| **Mô tả** | STUDENT/LECTURER không thể unlock account |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Các bước thực hiện:**
1. User với role STUDENT/LECTURER gửi POST request đến `/api/admin/users/{userId}/unlock`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response: "Access denied"

---

### TC-ID-067: Admin unlock thất bại - User not locked

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-067 |
| **Tén Test Case** | Admin unlock fails - User is not locked |
| **Mô tả** | Unlock user chưa bị lock (status = ACTIVE) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 tồn tại
- User U1 status = ACTIVE (not locked)

**Các bước thực hiện:**
1. Admin cố unlock U1

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response: "User is not locked"

---

### TC-ID-068: Unlocked user có thể login lại

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-068 |
| **Tén Test Case** | Unlocked user can login again |
| **Mô tả** | User sau khi unlock có thể login lại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Integration |

**Các bước thực hiện:**
1. User U1 bị lock
2. Admin unlock U1
3. User U1 login

**Kết quả mong đợi:**
- Login thành công (200 OK)
- Nhận được tokens

---

### TC-ID-069: Admin unlock thất bại - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-069 |
| **Tén Test Case** | Admin unlock fails - User not found |
| **Mô tả** | Unlock user không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Logic |

**Các bước thực hiện:**
1. Admin gửi POST request với userId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response: "User not found"

---

## 9. JWT VALIDATION

### TC-ID-070: JWT validation - Valid token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-070 |
| **Tên Test Case** | JWT validation - Valid token accepted |
| **Mô tả** | System accept valid JWT token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Các bước thực hiện:**
1. User login → nhận access token
2. Gửi request đến protected endpoint với token

**Kết quả mong đợi:**
- Request thành công
- SecurityContext chứa user info

---

### TC-ID-071: JWT validation - Expired token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-071 |
| **Tên Test Case** | JWT validation - Expired token rejected |
| **Mô tả** | System reject expired JWT token (> 15 minutes) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Mock expired token (issued > 15 minutes ago)
2. Gửi request với expired token

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Token expired"

---

### TC-ID-072: JWT validation - Invalid signature

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-072 |
| **Tén Test Case** | JWT validation - Invalid signature rejected |
| **Mô tả** | System reject token với signature bị modify |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Lấy valid token
2. Modify payload (change user ID)
3. Gửi request với modified token

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token signature"

---

### TC-ID-073: JWT validation - Missing token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-073 |
| **Tén Test Case** | JWT validation - Missing token rejected |
| **Mô tả** | System reject request không có token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi request đến protected endpoint
2. KHÔNG gửi header Authorization

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Unauthorized"

---

### TC-ID-074: JWT validation - Malformed token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-074 |
| **Tén Test Case** | JWT validation - Malformed token rejected |
| **Mô tả** | System reject token không đúng format |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi request với token không đúng format (e.g., "invalid_token_string")

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token format"

---

### TC-ID-075: JWT validation - Wrong algorithm

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-075 |
| **Tén Test Case** | JWT validation - Wrong algorithm rejected |
| **Mô tả** | System reject token signed với algorithm khác HS256 |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Tạo token signed với RS256 instead of HS256
2. Gửi request với token này

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token"

---

### TC-ID-076: JWT validation - Invalid token_type claim

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-076 |
| **Tén Test Case** | JWT validation - Invalid token_type claim |
| **Mô tả** | System reject token không có claim token_type = ACCESS |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Tạo token với token_type = REFRESH (not ACCESS)
2. Gửi request với token này

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response: "Invalid token type"

---

### TC-ID-077: JWT validation - Extract roles correctly

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-077 |
| **Tén Test Case** | JWT validation - Extract roles correctly |
| **Mô tả** | System extract roles từ JWT và populate SecurityContext |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Các bước thực hiện:**
1. User với role STUDENT login
2. Extract roles từ SecurityContext

**Kết quả mong đợi:**
- SecurityContext chứa `ROLE_STUDENT`
- `@PreAuthorize("hasRole('STUDENT')")` work correctly

---

## 10. TOKEN REUSE DETECTION

### TC-ID-078: Token reuse detection - Revoke all tokens

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-078 |
| **Tên Test Case** | Token reuse detection - Revoke all tokens on reuse |
| **Mô tả** | System detect reuse và revoke tất cả tokens |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Attack Detection |

(Đã cover ở TC-ID-033)

---

### TC-ID-079: Token reuse audit log CRITICAL

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-079 |
| **Tén Test Case** | Token reuse audit log with CRITICAL alert level |
| **Mô tả** | Token reuse event được log với alert level CRITICAL |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Audit |

**Các bước thực hiện:**
1. Trigger token reuse attack (TC-ID-033)
2. Query audit_logs table

**Kết quả mong đợi:**
- Audit log record:
  - `action = REFRESH_REUSE`
  - Alert level = CRITICAL
  - IP address captured
  - User agent captured

---

### TC-ID-080: Token reuse không ảnh hưởng other users

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-080 |
| **Tên Test Case** | Token reuse detection only affects target user |
| **Mô tả** | Token reuse chỉ revoke tokens của user bị attack, không ảnh hưởng users khác |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - Isolation |

**Các bước thực hiện:**
1. User U1 có token reuse attack
2. User U2 (khác user) vẫn active
3. Verify U2 tokens

**Kết quả mong đợi:**
- U1: All tokens revoked
- U2: Tokens still active (không bị ảnh hưởng)

---

### TC-ID-081: Token reuse IP tracking

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-081 |
| **Tên Test Case** | Token reuse detection tracks attacker IP |
| **Mô tả** | System ghi lại IP address của attacker khi detect reuse |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - Forensics |

**Các bước thực hiện:**
1. Trigger token reuse từ IP = 192.168.1.100
2. Query audit log

**Kết quả mong đợi:**
- Audit log chứa `ip_address = "192.168.1.100"`
- Admin có thể track attacker

---

### TC-ID-082: Token reuse force re-login

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-082 |
| **Tén Test Case** | Token reuse forces user to re-login on all devices |
| **Mô tả** | Sau khi detect reuse, user phải login lại trên tất cả devices |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Session Management |

**Các bước thực hiện:**
1. User có 3 active sessions
2. Token reuse detected
3. User cố access API từ bất kỳ device nào

**Kết quả mong đợi:**
- Tất cả 3 devices: 401 UNAUTHORIZED "Token invalid"
- User phải login lại để nhận tokens mới

---

## 11. AUDIT LOGGING

### TC-ID-083: Audit log async execution

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-083 |
| **Tên Test Case** | Audit logging is asynchronous (non-blocking) |
| **Mô tả** | Audit logging không block main request |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Performance - Async |

**Các bước thực hiện:**
1. Login user
2. Measure response time
3. Verify audit log persist

**Kết quả mong đợi:**
- Login response time không bị delay bởi audit logging
- Audit log được ghi async (`@Async`)
- Audit log persist trong separate transaction

---

### TC-ID-084: Audit log capture IP address

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-084 |
| **Tén Test Case** | Audit log captures client IP address |
| **Mô tả** | Audit log ghi lại IP address của client |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Audit - Context Capture |

**Các bước thực hiện:**
1. Login từ IP = 192.168.1.100
2. Query audit_logs table

**Kết quả mong đợi:**
- Audit log: `ip_address = "192.168.1.100"`
- IP extracted from request before async call

---

### TC-ID-085: Audit log capture User-Agent

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-085 |
| **Tén Test Case** | Audit log captures User-Agent header |
| **Mô tả** | Audit log ghi lại User-Agent string |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Audit - Context Capture |

**Các bước thực hiện:**
1. Login với User-Agent = "Mozilla/5.0..."
2. Query audit_logs

**Kết quả mong đợi:**
- Audit log: `user_agent = "Mozilla/5.0..."`

---

### TC-ID-086: Audit log LOGIN_FAILED vs LOGIN_DENIED

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-086 |
| **Tén Test Case** | Audit log distinguishes LOGIN_FAILED vs LOGIN_DENIED |
| **Mô tả** | System ghi LOGIN_FAILED (wrong password) khác LOGIN_DENIED (account locked) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Audit - Action Types |

**Các bước thực hiện:**
1. Login với wrong password → query audit log
2. Login với locked account (correct password) → query audit log

**Kết quả mong đợi:**
- Wrong password: `action = LOGIN_FAILED`, `outcome = FAILURE`
- Locked account: `action = LOGIN_DENIED`, `outcome = DENIED`

---

### TC-ID-087: Audit log old_value và new_value

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-087 |
| **Tén Test Case** | Audit log records old_value and new_value for changes |
| **Mô tả** | Audit log ghi state before/after change |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Audit - State Tracking |

**Các bước thực hiện:**
1. Admin lock user (status: ACTIVE → LOCKED)
2. Query audit log

**Kết quả mong đợi:**
- Audit log:
  - `old_value = {"status": "ACTIVE"}`
  - `new_value = {"status": "LOCKED"}`
  - Format: JSON

---

### TC-ID-088: Audit log actor resolution

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-088 |
| **Tén Test Case** | Audit log resolves actor from SecurityContext |
| **Mô tả** | Audit log extract actor_id và actor_email từ JWT |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Audit - Actor Tracking |

**Các bước thực hiện:**
1. Admin A1 (userId = 5, email = admin@university.edu) soft delete user U1
2. Query audit log

**Kết quả mong đợi:**
- Audit log:
  - `actor_id = 5`
  - `actor_email = "admin@university.edu"`

---

### TC-ID-089: Audit log separate transaction

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-089 |
| **Tén Test Case** | Audit log persists even if main transaction rolls back |
| **Mô tả** | Audit log sử dụng `REQUIRES_NEW` transaction |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Audit - Transactional |

**Các bước thực hiện:**
1. Trigger operation có exception sau khi audit log
2. Verify audit log persist

**Kết quả mong đợi:**
- Main transaction rollback
- Audit log VẪN persist (separate transaction)
- `@Transactional(propagation = Propagation.REQUIRES_NEW)`

---

### TC-ID-090: Audit query endpoint - Get by entity

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-090 |
| **Tén Test Case** | Admin can query audit logs by entity |
| **Mô tả** | Admin query audit history của 1 entity cụ thể |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Admin API |

**Các bước thực hiện:**
1. Admin gửi GET request đến `/api/admin/audit/entity/User/123`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa tất cả audit logs cho User ID = 123
- Sorted by timestamp DESC

---

### TC-ID-091: Audit query endpoint - Get by actor

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-091 |
| **Tén Test Case** | Admin can query audit logs by actor |
| **Mô tả** | Admin query tất cả actions của 1 user/admin |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Admin API |

**Các bước thực hiện:**
1. Admin gửi GET request đến `/api/admin/audit/actor/5`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa tất cả actions performed by user ID = 5

---

### TC-ID-092: Audit query endpoint - Security events

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-092 |
| **Tén Test Case** | Admin can query security-related events |
| **Mô tả** | Admin query các security events (token reuse, login failures, etc.) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Admin API |

**Các bước thực hiện:**
1. Admin gửi GET request đến `/api/admin/audit/security-events`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa:
  - `REFRESH_REUSE` events
  - Multiple `LOGIN_FAILED` from same IP
  - `LOGIN_DENIED` events
  - Sorted by priority/timestamp

---

## 12. VALIDATION RULES

### TC-ID-093: Validation - Email RFC 5322

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-093 |
| **Tén Test Case** | Validation - Email complies with RFC 5322 |
| **Mô tả** | Email validation theo RFC 5322 standard |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Validation |

**Dữ liệu test:**
```
✅ valid@example.com
✅ user.name+tag@company.co.uk
✅ user_name@sub.domain.com
❌ invalid@
❌ @example.com
❌ no-at-sign.com
```

**Kết quả mong đợi:**
- Valid emails: accepted
- Invalid emails: 400 BAD_REQUEST

---

### TC-ID-094: Validation - Password complexity regex

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-094 |
| **Tén Test Case** | Validation - Password complexity regex |
| **Mô tả** | Password validation với regex pattern |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Pattern:** `^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&])[A-Za-z\d@$!%*?&]{8,128}$`

**Dữ liệu test:**
```
✅ SecurePass@123
✅ MyP@ssw0rd
❌ alllowercase (no uppercase, no special)
❌ ALLUPPERCASE (no lowercase, no special)
❌ NoSpecial123 (no special char)
❌ Short@1 (< 8 chars)
```

---

### TC-ID-095: Validation - Full name Unicode support

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-095 |
| **Tén Test Case** | Validation - Full name supports Unicode (Vietnamese) |
| **Mô tả** | Full name accept Unicode characters (tiếng Việt có dấu) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Validation |

**Pattern:** `^[\p{L}\s\-]{2,100}$`

**Dữ liệu test:**
```
✅ Nguyễn Văn Ánh
✅ Trần Thị Bảo Châu
✅ Jean-Pierre (hyphen allowed)
❌ Nguyen123 (no numbers)
❌ User@Name (no special chars except hyphen)
```

---

### TC-ID-096: Validation - Multiple errors returned

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-096 |
| **Tén Test Case** | Validation - Multiple validation errors returned |
| **Mô tả** | System trả về TẤT CẢ validation errors (not just first) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "invalid",
  "password": "weak",
  "confirmPassword": "different",
  "fullName": "A",
  "role": "INVALID"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response chứa errors cho TẤT CẢ 5 fields

---

### TC-ID-097: Validation - NotBlank vs NotNull

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-097 |
| **Tén Test Case** | Validation - NotBlank rejects empty string |
| **Mô tả** | NotBlank reject cả null và empty string |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "  ",
  "password": ""
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Errors: "Email is required", "Password is required"

---

### TC-ID-098: Validation - Trim whitespace

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-098 |
| **Tén Test Case** | Validation - Trim leading/trailing whitespace |
| **Mô tả** | System tự động trim whitespace |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Validation |

**Dữ liệu test:**
```json
{
  "email": "  student@university.edu  ",
  "fullName": "  Nguyen Van A  "
}
```

**Kết quả mong đợi:**
- Email và fullName được trim
- Stored without leading/trailing spaces

---

### TC-ID-099: Validation - Password max length

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-099 |
| **Tén Test Case** | Validation - Password max length = 128 |
| **Mô tả** | Password không vượt quá 128 characters |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "password": "VeryLongPassword@123VeryLongPassword@123VeryLongPassword@123VeryLongPassword@123VeryLongPassword@123VeryLongPassword@123VeryLongPassword@123VeryLongPassword@123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must not exceed 128 characters"

---

### TC-ID-100: Validation - Special char subset

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-100 |
| **Tén Test Case** | Validation - Password special chars limited to @$!%*?& |
| **Mô tả** | Password chỉ chấp nhận specific special chars |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "password": "SecurePass#123"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Password must contain at least 1 special character (@$!%*?&)"
- `#` not allowed

---

### TC-ID-101: Validation - Role enum constraint

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-101 |
| **Tén Test Case** | Validation - Role must be valid enum value |
| **Mô tả** | Role chỉ accept STUDENT (for self-register) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "role": "SUPERUSER"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid role specified"

---

### TC-ID-102: Validation - Email uniqueness at DB level

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-102 |
| **Tén Test Case** | Validation - Email uniqueness enforced by database UNIQUE constraint |
| **Mô tả** | Database UNIQUE constraint as final defense |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - Defense in Depth |

**Các bước thực hiện:**
1. Concurrent registrations với same email (race condition)
2. Both pass application check
3. Database constraint catches duplicate

**Kết quả mong đợi:**
- 1 registration succeeds
- 1 registration fails with `DataIntegrityViolationException`
- Database UNIQUE constraint prevents duplicate

---

### TC-ID-103: Validation - Email case insensitive uniqueness

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-103 |
| **Tén Test Case** | Validation - Email uniqueness is case-insensitive |
| **Mô tả** | Email "user@example.com" === "User@Example.COM" |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Dữ liệu test:**
```
User 1: student@university.edu
User 2: Student@University.EDU (same email, different case)
```

**Kết quả mong đợi:**
- User 2 registration fails: 409 CONFLICT "Email already registered"
- Case-insensitive check: `LOWER(email) = LOWER(?)`

---

### TC-ID-104: Validation - Password not in response

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-104 |
| **Tén Test Case** | Validation - Password never returned in API responses |
| **Mô tả** | Password hash không bao giờ hiển thị trong response |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Data Leakage |

**Các bước thực hiện:**
1. Register user
2. Login user
3. Verify response không chứa password/password_hash

**Kết quả mong đợi:**
- Response KHÔNG chứa password hoặc password_hash
- DTO không expose password field

---

## 13. SECURITY TESTS

### TC-ID-105: Security - BCrypt strength 10

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-105 |
| **Tén Test Case** | Security - BCrypt configured with strength 10 |
| **Mô tả** | Password encoder sử dụng BCrypt strength 10 |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Configuration |

**Các bước thực hiện:**
1. Check `SecurityConfig.passwordEncoder()` bean
2. Verify `BCryptPasswordEncoder(10)`

**Kết quả mong đợi:**
- BCryptPasswordEncoder với strength = 10
- Password hash format: `$2a$10$...`

---

### TC-ID-106: Security - JWT secret from environment

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-106 |
| **Tén Test Case** | Security - JWT secret loaded from environment variable |
| **Mô tả** | JWT_SECRET không hardcode trong code |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Configuration |

**Các bước thực hiện:**
1. Check JWT configuration
2. Verify secret loaded from `${JWT_SECRET}`

**Kết quả mong đợi:**
- JWT_SECRET từ environment variable
- KHÔNG hardcode trong source code
- Different secret per environment (dev/staging/prod)

---

### TC-ID-107: Security - HTTPS only (production)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-107 |
| **Tén Test Case** | Security - HTTPS enforced in production |
| **Mô tả** | Production environment chỉ accept HTTPS |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Transport |

**Các bước thực hiện:**
1. Try HTTP request in production
2. Verify redirect to HTTPS

**Kết quả mong đợi:**
- HTTP requests redirected to HTTPS
- HSTS header present

---

### TC-ID-108: Security - CORS configuration

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-108 |
| **Tén Test Case** | Security - CORS properly configured |
| **Mô tả** | CORS headers configured correctly |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - CORS |

**Các bước thực hiện:**
1. Send OPTIONS request từ allowed origin
2. Send OPTIONS request từ disallowed origin

**Kết quả mong đợi:**
- Allowed origin: CORS headers present
- Disallowed origin: Request blocked

---

### TC-ID-109: Security - SQL injection prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-109 |
| **Tén Test Case** | Security - SQL injection prevention |
| **Mô tả** | System chống SQL injection |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Injection |

**Dữ liệu test:**
```json
{
  "email": "' OR '1'='1",
  "password": "anything"
}
```

**Kết quả mong đợi:**
- SQL injection KHÔNG thực thi
- Prepared statements protect database

---

### TC-ID-110: Security - XSS prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-110 |
| **Tén Test Case** | Security - XSS prevention |
| **Mô tả** | System chống XSS attacks |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - XSS |

**Dữ liệu test:**
```json
{
  "fullName": "<script>alert('XSS')</script>"
}
```

**Kết quả mong đợi:**
- Data được sanitize/escape
- Response không chứa executable script

---

### TC-ID-111: Security - Rate limiting (if implemented)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-111 |
| **Tén Test Case** | Security - Rate limiting on login endpoint |
| **Mô tả** | Login endpoint có rate limiting chống brute force |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - Brute Force Prevention |

**Các bước thực hiện:**
1. Gửi 100+ login requests trong 1 phút

**Kết quả mong đợi:**
- HTTP Status: `429 TOO_MANY_REQUESTS` sau threshold
- Response: "Rate limit exceeded"

---

### TC-ID-112: Security - Password timing attack prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-112 |
| **Tén Test Case** | Security - Constant time password comparison |
| **Mô tả** | Password comparison sử dụng constant-time để prevent timing attacks |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - Timing Attack |

**Các bước thực hiện:**
1. Measure response time for correct vs incorrect password
2. Verify no significant timing difference

**Kết quả mong đợi:**
- BCrypt's `matches()` uses constant-time comparison
- Cannot infer password correctness from timing

---

### TC-ID-113: Security - Soft delete prevents data exposure

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-113 |
| **Tén Test Case** | Security - Soft deleted users not exposed in queries |
| **Mô tả** | `@SQLRestriction` automatically filter soft deleted users |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - Data Protection |

**Các bước thực hiện:**
1. Soft delete user
2. Try to query user via JPA
3. Verify user not found

**Kết quả mong đợi:**
- Soft deleted users không visible trong standard queries
- `@SQLRestriction("deleted_at IS NULL")` apply automatically

---

### TC-ID-114: Security - Admin self-action prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-ID-114 |
| **Tén Test Case** | Security - Admin cannot perform destructive actions on self |
| **Mô tả** | Admin không thể delete/lock chính mình |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Account Protection |

**Các bước thực hiện:**
1. Admin cố delete own account
2. Admin cố lock own account

**Kết quả mong đợi:**
- Both actions fail: 400 BAD_REQUEST
- Error: "Cannot perform this action on own account"
- Prevent admin lockout

---

## Test Execution Guidelines

### Prerequisites
1. **Database:** PostgreSQL với schema đầy đủ (users, refresh_tokens, audit_logs)
2. **Environment Variables:** JWT_SECRET configured
3. **Test Data:** Seed users với different roles (ADMIN, LECTURER, STUDENT)
4. **Authentication:** JWT token generator for testing

### Test Execution Phases

#### Phase 1: Core Authentication (Priority: CRITICAL)
- Execute tests TC-ID-001 to TC-ID-027 (Register, Login)
- Focus: Happy path và password security
- Duration: 2-3 hours

#### Phase 2: Token Management (Priority: CRITICAL)
- Execute tests TC-ID-028 to TC-ID-043 (Refresh, Logout, Token Reuse)
- Focus: Token rotation, reuse detection
- Duration: 2-3 hours

#### Phase 3: Admin Operations (Priority: CRITICAL)
- Execute tests TC-ID-044 to TC-ID-069 (Soft Delete, Restore, Lock/Unlock)
- Focus: Admin authorization, soft delete cascade
- Duration: 2-3 hours

#### Phase 4: Security & Validation (Priority: CRITICAL + HIGH)
- Execute tests TC-ID-070 to TC-ID-114 (JWT, Security, Validation)
- Focus: JWT validation, BCrypt, SQL injection, XSS
- Duration: 3-4 hours

#### Phase 5: Audit Logging (Priority: HIGH)
- Execute tests TC-ID-083 to TC-ID-092
- Focus: Async logging, context capture
- Duration: 1-2 hours

### Test Tools Recommendation
1. **Manual Testing:** Postman / Insomnia
2. **Automated Testing:** REST Assured + JUnit 5 + Mockito
3. **Security Testing:** OWASP ZAP (for injection tests)
4. **Load Testing:** JMeter (for rate limiting, concurrent registrations)
5. **JWT Tools:** jwt.io for token inspection

### Priority Summary
- **CRITICAL:** 42 tests (Authentication, Token security, Admin operations, JWT validation)
- **HIGH:** 60 tests (Validation, Audit logging, Security)
- **MEDIUM:** 12 tests (Edge cases, Optional features)
- **LOW:** 0 tests

### Expected Test Coverage
- **Positive Tests:** 45 tests (39%)
- **Negative Tests:** 55 tests (48%)
- **Security Tests:** 14 tests (12%)

---

## Export to Excel/TestRail Format

Để export file này sang Excel hoặc TestRail, copy từng test case theo format:

| Test Case ID | Name | Description | Prerequisites | Steps | Test Data | Expected Results | Priority | Type |
|--------------|------|-------------|---------------|-------|-----------|------------------|----------|------|
| TC-ID-001 | Register successfully | Guest đăng ký tài khoản STUDENT... | Email chưa tồn tại... | 1. POST /api/auth/register... | JSON... | 201 CREATED... | HIGH | Positive |

---

**Document Version:** 1.0  
**Last Updated:** 2026-01-29  
**Total Test Cases:** 114  
**Service:** Identity Service (Authentication & Authorization)
