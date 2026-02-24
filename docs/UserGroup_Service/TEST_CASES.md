# TEST CASES – USER & GROUP SERVICE

## Test Case Overview

| Category | Test Count | Priority |
|----------|------------|----------|
| Get User Profile (UC21) | 8 | HIGH |
| Update User Profile (UC22) | 7 | HIGH |
| List Users | 4 | MEDIUM |
| Create Group (UC23) | 8 | HIGH |
| Get Group Details | 5 | HIGH |
| List Groups | 5 | MEDIUM |
| Update Group | 6 | HIGH |
| Delete Group | 5 | MEDIUM |
| Add Member to Group (UC24) | 11 | HIGH |
| Promote Member to Leader (UC25) | 6 | CRITICAL |
| Demote Leader to Member (UC25) | 2 | HIGH |
| Remove Member (UC26) | 6 | HIGH |
| Get Group Members | 4 | MEDIUM |
| Get User's Groups | 5 | MEDIUM |
| Authorization & Security | 12 | CRITICAL |
| Validation Rules | 15 | HIGH |
| Soft Delete | 8 | HIGH |
| **TOTAL** | **117** | - |

---

## 1. GET USER PROFILE (UC21)

### TC-UG-001: Lấy user profile thành công - ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-001 |
| **Tên Test Case** | Get user profile successfully - ADMIN |
| **Mô tả** | ADMIN lấy thông tin bất kỳ user nào |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User đã login với role ADMIN
- Target user U1 tồn tại (userId: 456)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/{userId}`
2. userId = 456 (Long)
3. Header: `Authorization: Bearer <valid_admin_jwt_token>`

**Dữ liệu test:**
```
userId: 456
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response body:
```json
{
  "id": 456,
  "email": "student@example.com",
  "fullName": "Nguyen Van A",
  "status": "ACTIVE",
  "roles": ["STUDENT"]
}
```

---

### TC-UG-002: Lấy user profile thành công - STUDENT (self)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-002 |
| **Tên Test Case** | Get user profile successfully - STUDENT (self) |
| **Mô tả** | STUDENT lấy thông tin chính mình |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User S1 login với role STUDENT
- userId trong JWT = 456

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/456`
2. Header: `Authorization: Bearer <valid_student_jwt_token>`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa thông tin của chính user S1

---

### TC-UG-003: Lấy user profile thất bại - STUDENT xem user khác

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-003 |
| **Tên Test Case** | Get user profile fails - STUDENT views other user |
| **Mô tả** | STUDENT cố xem thông tin user khác |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User S1 login với role STUDENT (userId: 456)
- Target user S2 tồn tại (userId: 789)

**Các bước thực hiện:**
1. User S1 gửi GET request đến `/api/users/789`
2. Header: `Authorization: Bearer <student_s1_jwt_token>`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "You can only view your own profile",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-004: Lấy user profile thành công - LECTURER xem STUDENT

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-004 |
| **Tên Test Case** | Get user profile successfully - LECTURER views STUDENT |
| **Mô tả** | LECTURER xem thông tin user có role STUDENT |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Target user có role STUDENT (userId: 456)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/456`
2. Header: `Authorization: Bearer <valid_lecturer_jwt_token>`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa thông tin STUDENT

---

### TC-UG-005: Lấy user profile thất bại - LECTURER xem LECTURER/ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-005 |
| **Tên Test Case** | Get user profile fails - LECTURER views non-STUDENT |
| **Mô tả** | LECTURER cố xem thông tin user không phải STUDENT (vi phạm UC21-AUTH) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Target user có role ADMIN hoặc LECTURER (không phải STUDENT)

**Các bước thực hiện:**
1. Lecturer gửi GET request đến `/api/users/{userId}` (target = ADMIN/LECTURER)

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Lecturers can only view student profiles",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-006: Lấy user profile thất bại - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-006 |
| **Tên Test Case** | Get user profile fails - User not found |
| **Mô tả** | Lấy thông tin user không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Điều kiện tiên quyết:**
- User login với role ADMIN

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/99999`

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-007: Lấy user profile thất bại - Unauthorized

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-007 |
| **Tên Test Case** | Get user profile fails - Unauthorized |
| **Mô tả** | Gọi API không có JWT token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Điều kiện tiên quyết:**
- None (no authentication)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/{userId}`
2. KHÔNG gửi header Authorization

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Authentication required",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-008: Không lấy được soft deleted user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-008 |
| **Tên Test Case** | Cannot get soft deleted user |
| **Mô tả** | User đã bị soft delete không thể lấy được (deleted_at IS NOT NULL) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Soft Delete |

**Điều kiện tiên quyết:**
- User U1 đã bị soft delete (deleted_at có giá trị)
- Admin login

**Các bước thực hiện:**
1. Admin gửi GET request đến `/api/users/{userId}` (U1 đã deleted)

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Soft deleted users bị filter bởi `@SQLRestriction`

---

## 2. UPDATE USER PROFILE (UC22)

### TC-UG-009: Update user profile thành công - STUDENT (self)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-009 |
| **Tên Test Case** | Update user profile successfully - STUDENT (self) |
| **Mô tả** | STUDENT update thông tin chính mình |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User S1 login với role STUDENT
- userId trong JWT = `456`
- User status = ACTIVE

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/users/456`
2. Header: `Authorization: Bearer <valid_student_jwt_token>`
3. Body: Update request

**Dữ liệu test:**
```json
{
  "fullName": "Nguyen Van B Updated"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "id": 456,
  "email": "student@example.com",
  "fullName": "Nguyen Van B Updated",
  "status": "ACTIVE",
  "roles": ["STUDENT"]
}
```
- Database: fullName đã được update

---

### TC-UG-010: Update user profile thành công - ADMIN update bất kỳ user

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-010 |
| **Tên Test Case** | Update user profile successfully - ADMIN updates any user |
| **Mô tả** | ADMIN có thể update bất kỳ user nào |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Target user U1 tồn tại (status = ACTIVE)

**Các bước thực hiện:**
1. Admin gửi PUT request đến `/api/users/{userId}`
2. Body: Update request

**Dữ liệu test:**
```json
{
  "fullName": "Admin Changed Name"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa updated fullName

---

### TC-UG-011: Update user profile thất bại - STUDENT update user khác

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-011 |
| **Tên Test Case** | Update user profile fails - STUDENT updates other user |
| **Mô tả** | STUDENT cố update user khác (không phải chính mình) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User S1 login với role STUDENT (userId: `456`)
- Target user S2 tồn tại (userId: `789`)

**Các bước thực hiện:**
1. User S1 gửi PUT request đến `/api/users/789`
2. Body: Update data

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "You can only update your own profile",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-012: Update user profile thất bại - LECTURER update (vi phạm UC22-AUTH)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-012 |
| **Tên Test Case** | Update user profile fails - LECTURER explicitly excluded |
| **Mô tả** | LECTURER không thể update profile (kể cả chính mình) qua API này |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Lecturer cố update chính mình hoặc user khác

**Các bước thực hiện:**
1. Lecturer gửi PUT request đến `/api/users/{userId}`
2. Body: Update data

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Lecturers cannot update profiles via this API",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-013: Update user profile thất bại - User inactive

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-013 |
| **Tên Test Case** | Update user profile fails - User inactive |
| **Mô tả** | Không thể update user có status = INACTIVE |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 tồn tại
- User U1 status = INACTIVE
- Admin login

**Các bước thực hiện:**
1. Admin gửi PUT request đến `/api/users/{userId}` (U1 inactive)
2. Body: Update data

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "USER_INACTIVE",
  "message": "Cannot update inactive user",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-014: Update user profile thất bại - Invalid fullName

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-014 |
| **Tên Test Case** | Update user profile fails - Invalid fullName |
| **Mô tả** | Update với fullName không hợp lệ (có số, quá ngắn) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Điều kiện tiên quyết:**
- User login với role STUDENT (self update)

**Dữ liệu test:**
```json
{
  "fullName": "A"
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
      "field": "fullName",
      "message": "Full name must be between 2 and 100 characters"
    }
  ]
}
```

---

### TC-UG-015: Update user profile thất bại - Invalid characters

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-015 |
| **Tên Test Case** | Update user profile fails - Invalid characters |
| **Mô tả** | Update với fullName chứa số hoặc ký tự đặc biệt |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "fullName": "Nguyen123"
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
      "field": "fullName",
      "message": "Full name contains invalid characters"
    }
  ]
}
```

---

## 3. LIST USERS

### TC-UG-016: List users thành công - ADMIN with pagination

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-016 |
| **Tên Test Case** | List users successfully - ADMIN with pagination |
| **Mô tả** | ADMIN lấy danh sách users với phân trang |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Database có ít nhất 50 users

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users?page=0&size=20`
2. Header: `Authorization: Bearer <valid_admin_jwt_token>`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "content": [
    {
      "id": 456,
      "email": "user1@example.com",
      "fullName": "User 1",
      "status": "ACTIVE",
      "roles": ["STUDENT"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

### TC-UG-017: List users with status filter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-017 |
| **Tên Test Case** | List users with status filter |
| **Mô tả** | Lọc users theo status (ACTIVE/INACTIVE) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Filtering |

**Điều kiện tiên quyết:**
- Admin login
- Database có users với cả 2 status

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users?status=ACTIVE`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa users có status = ACTIVE

---

### TC-UG-018: List users với role filter (deferred - ignored)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-018 |
| **Tên Test Case** | List users with role filter - Parameter ignored |
| **Mô tả** | Filter by role được accept nhưng IGNORED (roles ở identity-service) |
| **Độ ưu tiên** | LOW |
| **Loại test** | Positive - Feature Deferred |

**Điều kiện tiên quyết:**
- Admin login

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users?role=STUDENT`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response trả về TẤT CẢ users (không filter by role)
- Warning log: "Role filter is not implemented yet"

---

### TC-UG-019: List users thất bại - Non-ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-019 |
| **Tên Test Case** | List users fails - Non-ADMIN |
| **Mô tả** | STUDENT/LECTURER không thể list tất cả users |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only admins can list all users",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

## 4. CREATE GROUP (UC23)

### TC-UG-020: Create group thành công với dữ liệu hợp lệ

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-020 |
| **Tên Test Case** | Create group successfully with valid data |
| **Mô tả** | ADMIN tạo group mới với tất cả fields hợp lệ |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Lecturer L1 (ID: `123`) tồn tại và có role LECTURER
- Group name "SE1705-G1" chưa tồn tại trong semesterId 1 (Spring 2026)

**Các bước thực hiện:**
1. Gửi POST request đến `/api/groups`
2. Header: `Authorization: Bearer <valid_admin_jwt_token>`
3. Body: Valid group data

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "lecturerId": 123
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Response:
```json
{
  "id": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "semesterCode": "SPRING2026",
  "lecturerId": 123,
  "lecturerName": "Dr. Nguyen Van A"
}
```
- Database: 1 record mới trong `groups` table
- deleted_at = NULL

---

### TC-UG-021: Create group thất bại - Invalid group name format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-021 |
| **Tên Test Case** | Create group fails - Invalid group name format |
| **Mô tả** | Tạo group với group name không đúng pattern |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Điều kiện tiên quyết:**
- User login với role ADMIN

**Dữ liệu test:**
```json
{
  "groupName": "Group 1",
  "semesterId": 1,
  "lecturerId": 123
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
      "field": "groupName",
      "message": "Invalid group name format"
    }
  ]
}
```

---

### TC-UG-022: Create group thất bại - Invalid semester ID

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-022 |
| **Tên Test Case** | Create group fails - Invalid semester ID |
| **Mô tả** | Tạo group với semesterId không hợp lệ (null or negative) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": null,
  "lecturerId": 123
}
```

⚠️ **Field Types:**
- `semesterId`: Long (BIGINT) - FK to semesters.id - REQUIRED, must be positive
- `lecturerId`: Long (BIGINT) - Logical reference to Identity Service

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
      "field": "semesterId",
      "message": "Semester ID is required"
    }
  ]
}
```

---

### TC-UG-023: Create group thất bại - Lecturer not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-023 |
| **Tên Test Case** | Create group fails - Lecturer not found |
| **Mô tả** | Tạo group với lecturerId không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "lecturerId": 99999
}
```

⚠️ **Field Type:** `lecturerId` is Long (BIGINT)

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "LECTURER_NOT_FOUND",
  "message": "Lecturer not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-024: Create group thất bại - Duplicate group name

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-024 |
| **Tén Test Case** | Create group fails - Duplicate group name in same semester |
| **Mô tả** | Tạo group với (groupName + semester) đã tồn tại (vi phạm UNIQUE constraint) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Group "SE1705-G1" đã tồn tại trong semester with semesterId = 1

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "lecturerId": 123
}
```

⚠️ **Business Rule:** Group name must be unique per semester (enforced by unique index on `(group_name, semester_id)`)

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "GROUP_NAME_DUPLICATE",
  "message": "Group name already exists in this semester",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-025: Create group thành công - Same name different semester

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-025 |
| **Tên Test Case** | Create group successfully - Same name different semester |
| **Mô tả** | Tạo group với tên giống nhau nhưng semester khác nhau (ALLOWED) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Điều kiện tiên quyết:**
- Group "SE1705-G1" đã tồn tại trong semesterId 1 (Spring 2026)
- Admin login

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": 2,
  "lecturerId": 123
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Group được tạo thành công với semesterId 2 (Fall 2026)

---

### TC-UG-026: Create group thất bại - Non-ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-026 |
| **Tên Test Case** | Create group fails - Non-ADMIN |
| **Mô tả** | STUDENT/LECTURER không thể tạo group |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi POST request đến `/api/groups`
2. Body: Valid group data

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only admins can create groups",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-027: Create group thất bại - Missing required fields

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-027 |
| **Tên Test Case** | Create group fails - Missing required fields |
| **Mô tả** | Tạo group với request thiếu fields bắt buộc |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semester": null,
  "lecturerId": null
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response chứa errors cho cả 2 fields (semester, lecturerId)

---

## 5. GET GROUP DETAILS

### TC-UG-028: Get group details thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-028 |
| **Tên Test Case** | Get group details successfully |
| **Mô tả** | Lấy chi tiết group bao gồm lecturer và members |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login (AUTHENTICATED)
- Group G1 tồn tại với lecturer và members

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups/{groupId}`
2. groupId = 1 (Long)

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "id": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "semesterCode": "SPRING2026",
  "lecturer": {
    "id": 123,
    "fullName": "Dr. Nguyen Van A",
    "email": "lecturer@example.com"
  },
  "members": [
    {
      "userId": 456,
      "fullName": "Student Name",
      "email": "student@example.com",
      "role": "LEADER"
    }
  ],
  "memberCount": 5
}
```

---

### TC-UG-029: Get group details thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-029 |
| **Tên Test Case** | Get group details fails - Group not found |
| **Mô tả** | Lấy thông tin group không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi GET request với groupId không tồn tại: `99999`

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-030: Get group details không return soft deleted groups

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-030 |
| **Tên Test Case** | Get group details excludes soft deleted groups |
| **Mô tả** | Group đã bị soft delete không thể get |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Soft Delete |

**Điều kiện tiên quyết:**
- Group G1 đã bị soft delete (deleted_at IS NOT NULL)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups/{groupId}` (G1 đã deleted)

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- `@SQLRestriction` filter soft deleted groups

---

### TC-UG-031: Get group details with empty members

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-031 |
| **Tên Test Case** | Get group details with empty members |
| **Mô tả** | Lấy thông tin group chưa có member nào |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Edge Case |

**Điều kiện tiên quyết:**
- Group G1 tồn tại
- Group G1 CHƯA CÓ member nào

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "id": 1,
  "groupName": "SE1705-G1",
  "semesterId": 1,
  "semesterCode": "SPRING2026",
  "lecturer": {...},
  "members": [],
  "memberCount": 0
}
```

---

### TC-UG-032: Get group details thất bại - Unauthorized

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-032 |
| **Tên Test Case** | Get group details fails - Unauthorized |
| **Mô tả** | Gọi API không có JWT token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups/{groupId}`
2. KHÔNG gửi header Authorization

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`

---

## 6. LIST GROUPS

### TC-UG-033: List groups thành công với pagination

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-033 |
| **Tên Test Case** | List groups successfully with pagination |
| **Mô tả** | Lấy danh sách groups với phân trang |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login (AUTHENTICATED)
- Database có ít nhất 50 groups

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups?page=0&size=20`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "content": [
    {
      "id": 1,
      "groupName": "SE1705-G1",
      "semesterId": 1,
      "semesterCode": "SPRING2026",
      "lecturerName": "Dr. Nguyen Van A",
      "memberCount": 5
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 50,
  "totalPages": 3
}
```

---

### TC-UG-034: List groups với semester filter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-034 |
| **Tên Test Case** | List groups with semester filter |
| **Mô tả** | Lọc groups theo semester |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Filtering |

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups?semesterId=1`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa groups có semesterId = 1

---

### TC-UG-035: List groups với lecturerId filter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-035 |
| **Tên Test Case** | List groups with lecturerId filter |
| **Mô tả** | Lọc groups theo lecturer |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Filtering |

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups?lecturerId=123`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa groups của lecturer đó

---

### TC-UG-036: List groups không return soft deleted

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-036 |
| **Tên Test Case** | List groups excludes soft deleted groups |
| **Mô tả** | Danh sách groups không bao gồm soft deleted groups |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Soft Delete |

**Điều kiện tiên quyết:**
- Database có 10 groups (3 đã soft delete)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- totalElements = 7 (chỉ active groups)
- Soft deleted groups bị filter bởi `@SQLRestriction`

---

### TC-UG-037: List groups với multiple filters

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-037 |
| **Tên Test Case** | List groups with multiple filters |
| **Mô tả** | Áp dụng nhiều filters cùng lúc |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Combined Filtering |

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups?semesterId=1&lecturerId=123`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa groups thỏa CẢ 2 điều kiện

---

## 7. UPDATE GROUP

### TC-UG-038: Update group thành công - Update group name

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-038 |
| **Tên Test Case** | Update group successfully - Update group name |
| **Mô tả** | ADMIN update group name |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Group G1 tồn tại

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}`
2. Body: Update request

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1-Updated",
  "lecturerId": 123
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "id": 1,
  "groupName": "SE1705-G1-Updated",
  "semesterId": 1,
  "semesterCode": "SPRING2025",
  "lecturerId": 123,
  "lecturerName": "Dr. Nguyen Van A"
}
```
- semester KHÔNG thay đổi (immutable)

---

### TC-UG-039: Update group thành công - Change lecturer

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-039 |
| **Tên Test Case** | Update group successfully - Change lecturer |
| **Mô tả** | ADMIN thay đổi lecturer của group |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Admin login
- Group G1 có lecturer L1
- Lecturer L2 tồn tại

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "lecturerId": 124
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa lecturerId mới và lecturerName mới

---

### TC-UG-040: Update group thất bại - Semester immutable

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-040 |
| **Tên Test Case** | Update group fails - Semester is immutable |
| **Mô tả** | Không thể update semester sau khi tạo |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Admin login
- Group G1 tồn tại với semesterId 1 (Spring 2026)

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": 2,
  "lecturerId": 123
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response:
```json
{
  "code": "IMMUTABLE_FIELD",
  "message": "Semester cannot be changed after creation",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- hoặc semesterId field bị ignore (tùy implementation)

---

### TC-UG-041: Update group thất bại - New lecturer not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-041 |
| **Tên Test Case** | Update group fails - New lecturer not found |
| **Mô tả** | Update với lecturerId không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "lecturerId": 99999
}
```

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "LECTURER_NOT_FOUND",
  "message": "Lecturer not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-042: Update group thất bại - Non-ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-042 |
| **Tên Test Case** | Update group fails - Non-ADMIN |
| **Mô tả** | STUDENT/LECTURER không thể update group |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only admins can update groups",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-043: Update group thất bại - Duplicate name in semester

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-043 |
| **Tên Test Case** | Update group fails - Duplicate name in semester |
| **Mô tả** | Update group name thành tên đã tồn tại trong cùng semester |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 tồn tại: "SE1705-G1" trong semesterId 1 (Spring 2026)
- Group G2 tồn tại: "SE1705-G2" trong semesterId 1 (Spring 2026)

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "lecturerId": 123
}
```

**Các bước thực hiện:**
1. Admin update G2 thành "SE1705-G1" (trùng với G1)

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "GROUP_NAME_DUPLICATE",
  "message": "Group name already exists in this semester",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

## 8. DELETE GROUP

### TC-UG-044: Delete group thành công - Soft delete empty group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-044 |
| **Tên Test Case** | Delete empty group successfully - Soft delete |
| **Mô tả** | ADMIN soft delete group KHÔNG CÓ members |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Group G1 tồn tại
- Group G1 KHÔNG CÓ members (member count = 0)

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/groups/{groupId}`
2. groupId = G1

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- Database: G1.deleted_at được set timestamp (không bị xóa khỏi DB)
- Query `findById(G1)` sẽ trả về empty (do `@SQLRestriction`)

---

### TC-UG-045: Delete group thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-045 |
| **Tên Test Case** | Delete group fails - Group not found |
| **Mô tả** | Xóa group không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi DELETE request với groupId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-046: Delete group thất bại - Non-ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-046 |
| **Tên Test Case** | Delete group fails - Non-ADMIN |
| **Mô tả** | STUDENT/LECTURER không thể delete group |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/groups/{groupId}`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only admins can delete groups",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-047: Delete group thất bại - Group has members

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-047 |
| **Tên Test Case** | Delete group fails - Group has members |
| **Mô tả** | Không thể xóa group khi còn members (phải remove all members trước) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 tồn tại với 5 members
- Admin login

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/groups/{groupId}`
2. groupId = G1 (có 5 members)

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "CANNOT_DELETE_GROUP_WITH_MEMBERS",
  "message": "Group has 5 members. Remove all members first.",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Database: Group G1 và members KHÔNG bị xóa

---

### TC-UG-048: Không thể delete group đã bị soft delete

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-048 |
| **Tên Test Case** | Cannot delete already soft deleted group |
| **Mô tả** | Delete group đã bị soft delete trả về 404 |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Soft Delete |

**Điều kiện tiên quyết:**
- Group G1 đã bị soft delete (deleted_at IS NOT NULL)

**Các bước thực hiện:**
1. Admin gửi DELETE request đến `/api/groups/{groupId}` (G1)

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- `@SQLRestriction` filter đã loại bỏ G1

---

## 9. ADD MEMBER TO GROUP (UC24)

### TC-UG-049: Add member thành công - MEMBER role

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-049 |
| **Tên Test Case** | Add member successfully - MEMBER role |
| **Mô tả** | ADMIN thêm user vào group với role MEMBER |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Group G1 tồn tại trong semester "Spring2026"
- User U1 tồn tại (status = ACTIVE)
- User U1 CHƯA thuộc group nào trong semester "Spring2026"

**Các bước thực hiện:**
1. Gửi POST request đến `/api/groups/{groupId}/members`
2. groupId = G1
3. Body: Add member request

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": false
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Response:
```json
{
  "userId": 456,
  "groupId": 1,
  "fullName": "Student Name",
  "email": "student@example.com",
  "role": "MEMBER"
}
```
- Database: 1 record mới trong `user_groups` table với role = MEMBER

---

### TC-UG-050: Add member thành công - LEADER role

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-050 |
| **Tên Test Case** | Add member successfully - LEADER role |
| **Mô tả** | ADMIN thêm user vào group với role LEADER |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- Admin login
- Group G1 tồn tại
- Group G1 CHƯA CÓ leader
- User U1 tồn tại (status = ACTIVE)

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": true
}
```

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- Response chứa role = "LEADER"
- Database: role column = LEADER

---

### TC-UG-051: Add member thất bại - User already in group same semester

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-051 |
| **Tên Test Case** | Add member fails - User already in group same semester |
| **Mô tả** | User đã thuộc 1 group trong semester này (vi phạm rule 1 group/semester) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 tồn tại trong semesterId 1 (Spring 2026)
- Group G2 tồn tại trong semesterId 1 (Spring 2026)
- User U1 ĐÃ THUỘC group G2

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": false
}
```

**Các bước thực hiện:**
1. Admin cố thêm U1 vào group G1 (cùng semester với G2)

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "USER_ALREADY_IN_GROUP_SAME_SEMESTER",
  "message": "User is already in another group in this semester",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-052: Add member thành công - User in group different semester

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-052 |
| **Tên Test Case** | Add member successfully - User in group different semester |
| **Mô tả** | User có thể tham gia group ở semester khác |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 tồn tại trong semesterId 1 (Spring 2026)
- Group G2 tồn tại trong semesterId 2 (Fall 2025)
- User U1 ĐÃ THUỘC group G2 (Fall 2025)

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": false
}
```

**Các bước thực hiện:**
1. Admin thêm U1 vào group G1 (semester khác)

**Kết quả mong đợi:**
- HTTP Status: `201 CREATED`
- User được thêm thành công

---

### TC-UG-053: Add member thất bại - User inactive

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-053 |
| **Tên Test Case** | Add member fails - User inactive |
| **Mô tả** | Không thể thêm user có status = INACTIVE vào group |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 tồn tại
- User U1 status = INACTIVE

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": false
}
```

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "USER_INACTIVE",
  "message": "Cannot add inactive user to group",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-054: Add member thất bại - Leader already exists

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-054 |
| **Tên Test Case** | Add member fails - Leader already exists |
| **Mô tả** | Không thể add member với isLeader=true nếu group đã có leader |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 tồn tại
- Group G1 ĐÃ CÓ leader (User L1)
- User U2 chưa thuộc group nào

**Dữ liệu test:**
```json
{
  "userId": 789,
  "isLeader": true
}
```

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "LEADER_ALREADY_EXISTS",
  "message": "Group already has a leader",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-055: Add member thất bại - User not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-055 |
| **Tên Test Case** | Add member fails - User not found |
| **Mô tả** | Thêm user không tồn tại vào group |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Dữ liệu test:**
```json
{
  "userId": 99999,
  "isLeader": false
}
```

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-056: Add member thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-056 |
| **Tên Test Case** | Add member fails - Group not found |
| **Mô tả** | Thêm member vào group không tồn tại |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Các bước thực hiện:**
1. Gửi POST request đến `/api/groups/99999/members`

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-057: Add member thất bại - Duplicate add

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-057 |
| **Tên Test Case** | Add member fails - Duplicate add |
| **Mô tả** | Thêm user đã thuộc group vào chính group đó |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 ĐÃ thuộc group G1

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": false
}
```

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "USER_ALREADY_IN_GROUP",
  "message": "User is already in this group",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-058: Add member thất bại - Non-ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-058 |
| **Tên Test Case** | Add member fails - Non-ADMIN |
| **Mô tả** | STUDENT/LECTURER không thể add member |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi POST request đến `/api/groups/{groupId}/members`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only admins can add members to groups",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-058b: Add member thất bại - Race condition prevented by database trigger

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-058b |
| **Tên Test Case** | Add member fails - Concurrent add to different groups same semester (DB trigger) |
| **Mô tả** | Database trigger ngăn race condition khi 2 requests concurrent add cùng user vào 2 groups khác nhau cùng semester |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Concurrency / Database Constraint |

**Điều kiện tiên quyết:**
- Group G1 tồn tại trong semester "Spring2026"
- Group G2 tồn tại trong semester "Spring2026"
- User U1 CHƯA thuộc group nào trong semester "Spring2026"
- Admin login

**Các bước thực hiện (concurrent):**
1. Thread 1: POST `/api/groups/{G1}/members` với userId = U1
2. Thread 2: POST `/api/groups/{G2}/members` với userId = U1 (cùng lúc)
3. Cả 2 requests pass application-level check (vì query chưa thấy record)
4. Database trigger `check_user_semester_uniqueness()` chạy trước khi INSERT
5. Request thứ 2 đến database bị trigger reject

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": false
}
```

**Kết quả mong đợi:**
- Request 1: HTTP Status `201 CREATED` (thành công)
- Request 2: HTTP Status `409 CONFLICT`
- Response (Request 2):
```json
{
  "code": "USER_ALREADY_IN_GROUP_SAME_SEMESTER",
  "message": "User already in a group for semester Spring2026",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Database: Chỉ có 1 record được insert (user U1 trong group G1)
- PostgreSQL error 23505 (unique_violation) được catch và map thành 409 CONFLICT

**Ghi chú:**
- Test case này kiểm tra defense-in-depth: application check + database trigger
- Database trigger là lớp bảo vệ cuối cùng chống race condition
- Production-ready: trigger đảm bảo BR-UG-009 không thể bị vi phạm

---

## 10. PROMOTE MEMBER TO LEADER (UC25)

### TC-UG-059: Promote to leader thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-059 |
| **Tên Test Case** | Promote member to leader successfully |
| **Mô tả** | ADMIN/LECTURER thăng MEMBER lên LEADER, old LEADER tự động demote |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN hoặc LECTURER
- Group G1 tồn tại
- User U1 là LEADER của G1
- User U2 là MEMBER của G1

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}/members/{userId}/promote`
2. groupId = G1, userId = U2
3. No request body needed

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "userId": 789,
  "groupId": 1,
  "fullName": "User 2 Name",
  "email": "user2@example.com",
  "role": "LEADER"
}
```
- Database:
  - U2 role changed to LEADER
  - U1 role auto-demoted to MEMBER
  - Transaction ACID guaranteed

---

### TC-UG-060: Promote with pessimistic lock (race condition)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-060 |
| **Tên Test Case** | Promote leader with pessimistic lock - Race condition |
| **Mô tả** | 2 requests đồng thời promote 2 members, chỉ 1 thành công (UC25-LOCK) |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Concurrency |

**Điều kiện tiên quyết:**
- Group G1 có LEADER (U1)
- Group G1 có 2 MEMBER (U2, U3)

**Các bước thực hiện:**
1. Thread 1: Promote U2 to LEADER
2. Thread 2: Promote U3 to LEADER (đồng thời)
3. System dùng `@Lock(PESSIMISTIC_WRITE)` on `findLeaderByGroupId()`

**Kết quả mong đợi:**
- 1 request thành công (200 OK)
- 1 request wait → sau đó thấy U2/U3 đã là LEADER → success
- Không có tình trạng 2 leaders
- Database: Chỉ có 1 LEADER cuối cùng

---

### TC-UG-061: Promote thất bại - User not in group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-061 |
| **Tên Test Case** | Promote fails - User not in group |
| **Mô tả** | Promote user không phải member của group |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Điều kiện tiên quyết:**
- Group G1 tồn tại
- User U1 KHÔNG thuộc group G1

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}/members/{userId}/promote`
2. groupId = G1, userId = U1

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "MEMBERSHIP_NOT_FOUND",
  "message": "User not found in group",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-062: Promote thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-062 |
| **Tên Test Case** | Promote fails - Group not found |
| **Mô tả** | Promote trong group không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Logic |

**Các bước thực hiện:**
1. Gửi PUT request với groupId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-063: Promote thất bại - Non-ADMIN/LECTURER

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-063 |
| **Tên Test Case** | Promote fails - Non-ADMIN/LECTURER |
| **Mô tả** | STUDENT không thể promote member |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}/members/{userId}/promote`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Access denied",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-064: Promote member already LEADER (idempotent)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-064 |
| **Tên Test Case** | Promote member already LEADER - Idempotent operation |
| **Mô tả** | Promote user đã là LEADER (no-op) |
| **Độ ưu tiên** | LOW |
| **Loại test** | Positive - Edge Case |

**Điều kiện tiên quyết:**
- User U1 là LEADER của group G1

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}/members/{userId}/promote`
2. userId = U1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa role = "LEADER"
- Không có thay đổi trong database

---

## 10B. DEMOTE LEADER TO MEMBER

### TC-UG-065: Demote to member thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-065 |
| **Tên Test Case** | Demote leader to member successfully |
| **Mô tả** | ADMIN/LECTURER hạ LEADER xuống MEMBER (group không có leader) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN hoặc LECTURER
- User U1 là LEADER của group G1

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}/members/{userId}/demote`
2. groupId = G1, userId = U1
3. No request body needed

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "userId": 456,
  "groupId": 1,
  "fullName": "User 1 Name",
  "email": "user1@example.com",
  "role": "MEMBER"
}
```
- Database: U1 role = MEMBER
- Group G1 không còn leader

---

### TC-UG-066: Demote thất bại - User not LEADER

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-066 |
| **Tên Test Case** | Demote fails - User is not LEADER |
| **Mô tả** | Demote user không phải LEADER |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Logic |

**Điều kiện tiên quyết:**
- User U1 là MEMBER (not LEADER) của group G1

**Các bước thực hiện:**
1. Gửi PUT request đến `/api/groups/{groupId}/members/{userId}/demote`
2. userId = U1

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Response:
```json
{
  "code": "BAD_REQUEST",
  "message": "User is not a leader",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

## 11. REMOVE MEMBER FROM GROUP (UC26)

### TC-UG-067: Remove member thành công - MEMBER role

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-067 |
| **Tên Test Case** | Remove member successfully - MEMBER role |
| **Mô tả** | ADMIN xóa MEMBER khỏi group |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login với role ADMIN
- Group G1 tồn tại
- User U1 là MEMBER của G1

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/groups/{groupId}/members/{userId}`
2. groupId = G1, userId = U1

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- Database: user_groups record bị soft delete (deleted_at set)
- User U1 không còn trong G1

---

### TC-UG-068: Remove member thất bại - Cannot remove leader

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-068 |
| **Tên Test Case** | Remove member fails - Cannot remove leader if group has members |
| **Mô tả** | Không thể xóa LEADER khi group còn members khác |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 có LEADER (U1)
- Group G1 có 3 MEMBER khác

**Các bước thực hiện:**
1. Admin cố xóa U1 (leader) khỏi group G1

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "CANNOT_REMOVE_LEADER",
  "message": "Cannot remove leader while group has other members",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-069: Remove member thành công - LEADER when alone

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-069 |
| **Tên Test Case** | Remove member successfully - LEADER when alone |
| **Mô tả** | LEADER có thể bị xóa nếu là member duy nhất |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Business Rule |

**Điều kiện tiên quyết:**
- Group G1 chỉ có 1 member: LEADER (U1)
- Không có MEMBER nào khác

**Các bước thực hiện:**
1. Admin xóa U1 khỏi group G1

**Kết quả mong đợi:**
- HTTP Status: `204 NO_CONTENT`
- User U1 bị xóa khỏi group
- Group G1 trống (không có member)

---

### TC-UG-070: Remove member thất bại - User not in group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-070 |
| **Tên Test Case** | Remove member fails - User not in group |
| **Mô tả** | Xóa user không phải member của group |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Logic |

**Điều kiện tiên quyết:**
- Group G1 tồn tại
- User U1 KHÔNG thuộc group G1

**Các bước thực hiện:**
1. Admin gửi DELETE request đến `/api/groups/{groupId}/members/{userId}`

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "MEMBERSHIP_NOT_FOUND",
  "message": "User not found in group",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-071: Remove member thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-071 |
| **Tên Test Case** | Remove member fails - Group not found |
| **Mô tả** | Xóa member khỏi group không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Business Logic |

**Các bước thực hiện:**
1. Gửi DELETE request với groupId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-072: Remove member thất bại - Non-ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-072 |
| **Tên Test Case** | Remove member fails - Non-ADMIN |
| **Mô tả** | STUDENT/LECTURER không thể remove member |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User login với role STUDENT hoặc LECTURER

**Các bước thực hiện:**
1. Gửi DELETE request đến `/api/groups/{groupId}/members/{userId}`

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Only admins can remove members from groups",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

## 12. GET GROUP MEMBERS

### TC-UG-073: Get group members thành công

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-073 |
| **Tên Test Case** | Get group members successfully |
| **Mô tả** | Lấy danh sách members của group |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User login (AUTHENTICATED)
- Group G1 tồn tại với 5 members (1 LEADER, 4 MEMBER)

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups/{groupId}/members`
2. groupId = G1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "groupId": 1,
  "groupName": "SE1705-G1",
  "members": [
    {
      "userId": 456,
      "fullName": "Leader Name",
      "email": "leader@example.com",
      "role": "LEADER"
    },
    {
      "userId": 789,
      "fullName": "Member Name",
      "email": "member@example.com",
      "role": "MEMBER"
    }
  ],
  "totalMembers": 5
}
```

---

### TC-UG-074: Get group members với role filter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-074 |
| **Tên Test Case** | Get group members with role filter |
| **Mô tả** | Lọc members theo role (LEADER/MEMBER) |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Filtering |

**Các bước thực hiện:**
1. Gửi GET request đến `/api/groups/{groupId}/members?role=LEADER`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa members có role = LEADER
- totalMembers = 1

---

### TC-UG-075: Get group members - Empty group

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-075 |
| **Tên Test Case** | Get group members - Empty group |
| **Mô tả** | Lấy members của group chưa có member nào |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Edge Case |

**Điều kiện tiên quyết:**
- Group G1 tồn tại
- Group G1 CHƯA CÓ member nào

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "groupId": 1,
  "groupName": "SE1705-G1",
  "members": [],
  "totalMembers": 0
}
```

---

### TC-UG-076: Get group members thất bại - Group not found

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-076 |
| **Tên Test Case** | Get group members fails - Group not found |
| **Mô tả** | Lấy members của group không tồn tại |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Not Found |

**Các bước thực hiện:**
1. Gửi GET request với groupId không tồn tại

**Kết quả mong đợi:**
- HTTP Status: `404 NOT_FOUND`
- Response:
```json
{
  "code": "GROUP_NOT_FOUND",
  "message": "Group not found",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

## 13. GET USER'S GROUPS

### TC-UG-077: Get user's groups thành công - STUDENT (self)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-077 |
| **Tên Test Case** | Get user's groups successfully - STUDENT (self) |
| **Mô tả** | STUDENT lấy danh sách groups của chính mình |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Happy Path |

**Điều kiện tiên quyết:**
- User S1 login với role STUDENT
- User S1 thuộc 3 groups ở các semester khác nhau

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/{userId}/groups`
2. userId = S1

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "userId": 456,
  "groups": [
    {
      "groupId": 1,
      "groupName": "SE1705-G1",
      "semesterId": 1,
      "semesterCode": "SPRING2026",
      "role": "MEMBER",
      "lecturerName": "Dr. Nguyen Van A"
    },
    {
      "groupId": 2,
      "groupName": "SE1704-G2",
      "semesterId": 2,
      "semesterCode": "FALL2025",
      "role": "LEADER",
      "lecturerName": "Dr. Tran Van B"
    }
  ]
}
```

---

### TC-UG-078: Get user's groups với semester filter

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-078 |
| **Tên Test Case** | Get user's groups with semester filter |
| **Mô tả** | Lọc groups theo semester |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Filtering |

**Các bước thực hiện:**
1. Gửi GET request đến `/api/users/{userId}/groups?semesterId=1`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chỉ chứa groups có semesterId = 1

---

### TC-UG-079: Get user's groups - LECTURER views STUDENT

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-079 |
| **Tên Test Case** | Get user's groups - LECTURER views STUDENT |
| **Mô tả** | LECTURER xem groups của student |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Authorization |

**Điều kiện tiên quyết:**
- User login với role LECTURER
- Target user có role STUDENT

**Các bước thực hiện:**
1. Lecturer gửi GET request đến `/api/users/{userId}/groups`

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response chứa danh sách groups của student

---

### TC-UG-080: Get user's groups thất bại - STUDENT views other

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-080 |
| **Tên Test Case** | Get user's groups fails - STUDENT views other user |
| **Mô tả** | STUDENT cố xem groups của user khác |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Authorization |

**Điều kiện tiên quyết:**
- User S1 login với role STUDENT
- Target user S2 khác S1

**Các bước thực hiện:**
1. S1 gửi GET request đến `/api/users/{userId}/groups` (userId = S2)

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "You can only view your own groups",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-081: Get user's groups - Empty result

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-081 |
| **Tên Test Case** | Get user's groups - Empty result |
| **Mô tả** | User chưa thuộc group nào |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Edge Case |

**Điều kiện tiên quyết:**
- User U1 tồn tại
- User U1 CHƯA thuộc group nào

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response:
```json
{
  "userId": 456,
  "groups": []
}
```

---

## 14. AUTHORIZATION & SECURITY

### TC-UG-082: JWT token validation - Valid token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-082 |
| **Tên Test Case** | JWT token validation - Valid token |
| **Mô tả** | System validate JWT token hợp lệ |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Security |

**Các bước thực hiện:**
1. Gửi request với valid JWT token trong header
2. Token chứa userId và roles

**Kết quả mong đợi:**
- Request được xử lý thành công
- SecurityContext được set với user info

---

### TC-UG-083: JWT token validation - Expired token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-083 |
| **Tên Test Case** | JWT token validation - Expired token |
| **Mô tả** | System reject expired JWT token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi request với expired JWT token

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "TOKEN_EXPIRED",
  "message": "Token has expired",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-084: JWT token validation - Invalid signature

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-084 |
| **Tên Test Case** | JWT token validation - Invalid signature |
| **Mô tả** | System reject token với signature không hợp lệ |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi request với JWT token có signature bị modify

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "INVALID_TOKEN",
  "message": "Invalid token signature",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-085: JWT token validation - Missing token

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-085 |
| **Tên Test Case** | JWT token validation - Missing token |
| **Mô tả** | System reject request không có token |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Negative - Security |

**Các bước thực hiện:**
1. Gửi request đến protected endpoint
2. KHÔNG gửi header Authorization

**Kết quả mong đợi:**
- HTTP Status: `401 UNAUTHORIZED`
- Response:
```json
{
  "code": "UNAUTHORIZED",
  "message": "Authentication required",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-086: Role-based access - ADMIN full access

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-086 |
| **Tên Test Case** | Role-based access - ADMIN full access |
| **Mô tả** | ADMIN có quyền truy cập tất cả endpoints |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Các bước thực hiện:**
1. Test tất cả protected endpoints với ADMIN token
2. Verify tất cả thành công

**Kết quả mong đợi:**
- ADMIN có quyền: Create/Update/Delete groups, Add/Remove members, Assign roles, View all users/groups

---

### TC-UG-087: Role-based access - LECTURER limited access

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-087 |
| **Tên Test Case** | Role-based access - LECTURER limited access |
| **Mô tả** | LECTURER chỉ có quyền đọc (view students, view groups) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Các bước thực hiện:**
1. Test read endpoints với LECTURER token
2. Test write endpoints với LECTURER token

**Kết quả mong đợi:**
- LECTURER allowed: View STUDENT profiles, View groups
- LECTURER forbidden: Update profile, Create/Update/Delete groups, Add/Remove members

---

### TC-UG-088: Role-based access - STUDENT self-only

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-088 |
| **Tên Test Case** | Role-based access - STUDENT self-only |
| **Mô tả** | STUDENT chỉ có quyền với own resources |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Authorization |

**Các bước thực hiện:**
1. Test self-access endpoints với STUDENT token
2. Test other-user-access endpoints với STUDENT token

**Kết quả mong đợi:**
- STUDENT allowed: View/Update own profile, View own groups
- STUDENT forbidden: View/Update other users, Manage groups

---

### TC-UG-089: ADMIN cannot delete ADMIN

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-089 |
| **Tên Test Case** | ADMIN cannot delete other ADMIN |
| **Mô tả** | Prevent ADMIN xóa ADMIN khác (business rule) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- Admin A1 login
- Admin A2 tồn tại

**Các bước thực hiện:**
1. A1 cố xóa A2

**Kết quả mong đợi:**
- HTTP Status: `403 FORBIDDEN`
- Response:
```json
{
  "code": "FORBIDDEN",
  "message": "Cannot delete another admin",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-090: SQL Injection prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-090 |
| **Tên Test Case** | SQL Injection prevention |
| **Mô tả** | System chống SQL injection attacks |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - Injection |

**Các bước thực hiện:**
1. Gửi request với SQL injection payload trong parameters

**Dữ liệu test:**
```json
{
  "fullName": "' OR '1'='1"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST` hoặc validation error
- SQL injection KHÔNG được thực thi
- Prepared statements protect database

---

### TC-UG-091: XSS prevention

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-091 |
| **Tên Test Case** | XSS prevention |
| **Mô tả** | System chống XSS attacks |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Security - XSS |

**Các bước thực hiện:**
1. Gửi request với XSS payload

**Dữ liệu test:**
```json
{
  "fullName": "<script>alert('XSS')</script>"
}
```

**Kết quả mong đợi:**
- Data được sanitize/escape
- Response không chứa executable script
- Content-Type headers secure

---

### TC-UG-092: CORS configuration

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-092 |
| **Tên Test Case** | CORS configuration |
| **Mô tả** | Verify CORS headers được config đúng |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Security - CORS |

**Các bước thực hiện:**
1. Gửi OPTIONS request từ allowed origin
2. Gửi OPTIONS request từ disallowed origin

**Kết quả mong đợi:**
- Allowed origin: CORS headers present
- Disallowed origin: Request blocked
- Headers: Access-Control-Allow-Origin, Access-Control-Allow-Methods

---

### TC-UG-093: Rate limiting (nếu có)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-093 |
| **Tên Test Case** | Rate limiting protection |
| **Mô tả** | System có rate limiting để chống abuse |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Security - Rate Limiting |

**Các bước thực hiện:**
1. Gửi 100+ requests liên tục trong 1 phút

**Kết quả mong đợi:**
- HTTP Status: `429 TOO_MANY_REQUESTS` sau threshold
- Response:
```json
{
  "code": "RATE_LIMIT_EXCEEDED",
  "message": "Rate limit exceeded",
  "timestamp": "2026-01-29T10:00:00Z"
}
```
- Retry-After header present

---

## 15. VALIDATION RULES

### TC-UG-094: Validation - Email format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-094 |
| **Tên Test Case** | Validation - Email format |
| **Mô tả** | Kiểm tra validation email format |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "email": "invalid-email"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid email format"

---

### TC-UG-095: Validation - FullName length

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-095 |
| **Tên Test Case** | Validation - FullName length constraints |
| **Mô tả** | Kiểm tra validation fullName min/max length |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "fullName": "A"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Full name must be between 2 and 100 characters"

---

### TC-UG-096: Validation - FullName pattern

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-096 |
| **Tên Test Case** | Validation - FullName pattern (letters only) |
| **Mô tả** | Kiểm tra fullName chỉ chấp nhận letters và spaces |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "fullName": "Nguyen123@"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Full name contains invalid characters"

---

### TC-UG-097: Validation - Group name format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-097 |
| **Tên Test Case** | Validation - Group name format pattern |
| **Mô tả** | Kiểm tra group name phải match pattern |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "invalid-group"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid group name format (expected: SE1705-G1)"

---

### TC-UG-098: Validation - Semester ID must be positive

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-098 |
| **Tên Test Case** | Validation - Semester ID must be positive |
| **Mô tả** | Kiểm tra semesterId phải là số nguyên dương |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "SE1705-G1",
  "semesterId": -1,
  "lecturerId": 123
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
      "field": "semesterId",
      "message": "Semester ID must be a positive number"
    }
  ]
}
```

---

### TC-UG-099: Validation - Long ID format

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-099 |
| **Tén Test Case** | Validation - Long ID format |
| **Mô tả** | Kiểm tra ID fields phải là số nguyên dương hợp lệ |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "lecturerId": "not-a-number"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Invalid ID format - must be a positive Long"

---

### TC-UG-100: Validation - NotNull constraints

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-100 |
| **Tên Test Case** | Validation - NotNull constraints |
| **Mô tả** | Kiểm tra required fields không được null |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": null,
  "semesterId": 1,
  "lecturerId": 123
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Group name is required"

---

### TC-UG-101: Validation - NotBlank vs NotNull

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-101 |
| **Tên Test Case** | Validation - NotBlank vs NotNull |
| **Mô tả** | Kiểm tra NotBlank reject empty string |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "  ",
  "semesterId": 1,
  "lecturerId": 123
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Group name is required"

---

### TC-UG-102: Validation - Multiple errors

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-102 |
| **Tên Test Case** | Validation - Multiple validation errors |
| **Mô tả** | Kiểm tra system trả về TẤT CẢ validation errors |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "a",
  "semesterId": null,
  "lecturerId": null
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
      "field": "groupName",
      "message": "Group name must be between 3 and 50 characters"
    },
    {
      "field": "semesterId",
      "message": "Semester ID is required"
    },
    {
      "field": "lecturerId",
      "message": "Lecturer ID is required"
    }
  ]
}
```

---

### TC-UG-103: Validation - Trim whitespace

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-103 |
| **Tên Test Case** | Validation - Trim leading/trailing whitespace |
| **Mô tả** | Kiểm tra system tự động trim whitespace |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Validation |

**Dữ liệu test:**
```json
{
  "fullName": "  Nguyen Van A  "
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Response fullName = "Nguyen Van A" (trimmed)

---

### TC-UG-104: Validation - Special characters

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-104 |
| **Tên Test Case** | Validation - Special characters in fullName |
| **Mô tả** | Kiểm tra fullName accept Vietnamese characters |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Validation |

**Dữ liệu test:**
```json
{
  "fullName": "Nguyễn Văn Á"
}
```

**Kết quả mong đợi:**
- HTTP Status: `200 OK`
- Vietnamese characters được accept

---

### TC-UG-105: Validation - Email uniqueness

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-105 |
| **Tên Test Case** | Validation - Email uniqueness constraint |
| **Mô tả** | Kiểm tra email phải unique |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Validation |

**Điều kiện tiên quyết:**
- User U1 tồn tại với email "existing@example.com"

**Dữ liệu test:**
```json
{
  "email": "existing@example.com"
}
```

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "EMAIL_DUPLICATE",
  "message": "Email already exists",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-106: Validation - Group name max length

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-106 |
| **Tên Test Case** | Validation - Group name max length |
| **Mô tả** | Kiểm tra group name không vượt quá 50 characters |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "groupName": "VERYLONGGROUPNAME12345678901234567890123456789012345"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Group name must be between 3 and 50 characters"

---

### TC-UG-107: Validation - Semester min length

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-107 |
| **Tên Test Case** | Validation - Semester min length |
| **Mô tả** | Kiểm tra semesterId không được null |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "semesterId": null
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "Semester ID is required"

---

### TC-UG-108: Validation - isLeader boolean

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-108 |
| **Tén Test Case** | Validation - isLeader must be boolean |
| **Mô tả** | Kiểm tra isLeader chỉ accept true/false |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Negative - Validation |

**Dữ liệu test:**
```json
{
  "userId": 456,
  "isLeader": "yes"
}
```

**Kết quả mong đợi:**
- HTTP Status: `400 BAD_REQUEST`
- Error: "isLeader must be a boolean value"

---

## 16. SOFT DELETE

### TC-UG-109: Soft delete filters existsById

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-109 |
| **Tén Test Case** | Soft delete - existsById may return true for deleted |
| **Mô tả** | Verify `existsById()` bypass `@SQLRestriction` (documented bug risk) |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Bug Risk |

**Điều kiện tiên quyết:**
- Group G1 đã bị soft delete (deleted_at IS NOT NULL)

**Các bước thực hiện:**
1. Call `groupRepository.existsById(G1)`
2. Call `groupRepository.findById(G1)`

**Kết quả mong đợi:**
- `existsById()` MAY return `true` (bug risk)
- `findById()` returns `Optional.empty()` (correct)
- **Solution:** Always use `findById().isPresent()` instead of `existsById()`

---

### TC-UG-110: Soft delete trong findAll

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-110 |
| **Tên Test Case** | Soft delete - findAll excludes deleted records |
| **Mô tả** | Kiểm tra `findAll()` không trả về soft deleted records |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Soft Delete |

**Điều kiện tiên quyết:**
- Database có 10 groups (3 đã soft delete)

**Các bước thực hiện:**
1. Gọi `/api/groups` (findAll)

**Kết quả mong đợi:**
- Response chỉ chứa 7 groups (active only)
- `@SQLRestriction("deleted_at IS NULL")` apply automatically

---

### TC-UG-111: Soft delete trong JPQL queries

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-111 |
| **Tên Test Case** | Soft delete - JPQL queries auto-filter |
| **Mô tả** | Kiểm tra JPQL queries tự động filter soft deleted |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Soft Delete |

**Các bước thực hiện:**
1. Execute JPQL: `SELECT g FROM Group g WHERE g.semester = 'Spring2026'`

**Kết quả mong đợi:**
- Kết quả KHÔNG chứa soft deleted groups
- `@SQLRestriction` apply to JPQL

---

### TC-UG-112: Soft delete không hard delete

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-112 |
| **Tén Test Case** | Soft delete - Records remain in database |
| **Mô tả** | Kiểm tra soft delete KHÔNG xóa khỏi database |
| **Độ ưu tiên** | CRITICAL |
| **Loại test** | Positive - Soft Delete |

**Các bước thực hiện:**
1. Delete group G1
2. Query database trực tiếp: `SELECT * FROM groups WHERE id = G1`

**Kết quả mong đợi:**
- Record vẫn tồn tại trong database
- Column `deleted_at` có timestamp value
- Data audit trail preserved

---

### TC-UG-113: Soft delete user inactive constraint

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-113 |
| **Tén Test Case** | Soft delete - Cannot change LEADER to INACTIVE |
| **Mô tả** | Không thể đổi status sang INACTIVE nếu user đang là LEADER |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Negative - Business Rule |

**Điều kiện tiên quyết:**
- User U1 là LEADER của group G1

**Các bước thực hiện:**
1. Admin cố update U1 status = INACTIVE

**Kết quả mong đợi:**
- HTTP Status: `409 CONFLICT`
- Response:
```json
{
  "code": "CANNOT_DEACTIVATE_LEADER",
  "message": "Cannot set status to INACTIVE while user is a group leader",
  "timestamp": "2026-01-29T10:00:00Z"
}
```

---

### TC-UG-114: Soft delete cascade to members

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-114 |
| **Tén Test Case** | Soft delete - Cascade to user_groups |
| **Mô tả** | Delete group cũng soft delete các memberships |
| **Độ ưu tiên** | HIGH |
| **Loại test** | Positive - Cascade |

**Điều kiện tiên quyết:**
- Group G1 có 5 members

**Các bước thực hiện:**
1. Delete group G1
2. Query `user_groups` table

**Kết quả mong đợi:**
- `groups.deleted_at` = timestamp
- Tất cả 5 records trong `user_groups` có `deleted_at` = timestamp
- Soft delete cascade correctly

---

### TC-UG-115: Restore soft deleted record (nếu có feature)

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-115 |
| **Tên Test Case** | Restore soft deleted record |
| **Mô tả** | Kiểm tra khả năng restore soft deleted record (nếu implement) |
| **Độ ưu tiên** | LOW |
| **Loại test** | Positive - Feature |

**Điều kiện tiên quyết:**
- Group G1 đã soft delete

**Các bước thực hiện:**
1. Gọi restore API (nếu có): `POST /api/groups/{groupId}/restore`

**Kết quả mong đợi:**
- `deleted_at` set to NULL
- Group G1 visible again in queries

---

### TC-UG-116: Soft delete trong nested entities

| Field | Value |
|-------|-------|
| **Test Case ID** | TC-UG-116 |
| **Tên Test Case** | Soft delete - Nested entity queries |
| **Mô tả** | Kiểm tra soft delete filter apply đến nested entities |
| **Độ ưu tiên** | MEDIUM |
| **Loại test** | Positive - Soft Delete |

**Các bước thực hiện:**
1. Get group details (includes members)
2. Database có 1 member đã soft delete

**Kết quả mong đợi:**
- Response không chứa soft deleted member
- Nested `@OneToMany` relationships respect `@SQLRestriction`

---

## Test Execution Guidelines

### Prerequisites
1. **Database:** PostgreSQL instance với schema đầy đủ
2. **Test Data:** Seed data cho users, groups, memberships
3. **Authentication:** Valid JWT tokens cho mỗi role (ADMIN, LECTURER, STUDENT)
4. **Environment:** Development/Staging environment

### Test Execution Phases

#### Phase 1: Core Functionality (Priority: HIGH + CRITICAL)
- Execute tests TC-UG-001 to TC-UG-027 (User/Group CRUD)
- Focus: Happy path và critical security tests
- Duration: 2-3 hours

#### Phase 2: Authorization & Security (Priority: CRITICAL)
- Execute tests TC-UG-082 to TC-UG-093
- Focus: JWT, RBAC, SQL injection, XSS
- Duration: 1-2 hours

#### Phase 3: Business Rules (Priority: HIGH)
- Execute tests TC-UG-049 to TC-UG-072 (Group membership, roles)
- Focus: Single leader, 1 group/semester, soft delete
- Duration: 2-3 hours

#### Phase 4: Validation (Priority: HIGH + MEDIUM)
- Execute tests TC-UG-094 to TC-UG-108
- Focus: Input validation, constraints
- Duration: 1-2 hours

#### Phase 5: Edge Cases & Soft Delete (Priority: MEDIUM + LOW)
- Execute tests TC-UG-109 to TC-UG-116
- Focus: Soft delete behavior, edge cases
- Duration: 1-2 hours

### Test Tools Recommendation
1. **Manual Testing:** Postman / Insomnia
2. **Automated Testing:** REST Assured + JUnit 5
3. **Load Testing:** JMeter / Gatling (for rate limiting tests)
4. **Security Testing:** OWASP ZAP (for injection tests)

### Priority Summary
- **CRITICAL:** 12 tests (Security, Authorization, Race conditions)
- **HIGH:** 60 tests (Core functionality, Business rules)
- **MEDIUM:** 42 tests (Edge cases, Validation)
- **LOW:** 2 tests (Feature deferred, Optional)

### Expected Test Coverage
- **Positive Tests:** 50 tests (43%)
- **Negative Tests:** 58 tests (50%)
- **Security Tests:** 12 tests (10%)
- **Performance Tests:** 2 tests (2%)

---

## Export to Excel/TestRail Format

Để export file này sang Excel hoặc TestRail, copy từng test case theo format:

| Test Case ID | Name | Description | Prerequisites | Steps | Test Data | Expected Results | Priority | Type |
|--------------|------|-------------|---------------|-------|-----------|------------------|----------|------|
| TC-UG-001 | Get user profile successfully - ADMIN | ADMIN lấy thông tin bất kỳ user nào | User đã login với role ADMIN... | 1. Gửi GET request... | userId: 456 | HTTP 200, Response body... | HIGH | Positive |

---

**Document Version:** 1.0  
**Last Updated:** 2026-01-29  
**Total Test Cases:** 116  
**Service:** User & Group Service
