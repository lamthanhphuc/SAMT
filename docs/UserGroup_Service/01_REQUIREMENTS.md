# Requirements - User & Group Service

## 1. Actors

| Actor | Mô tả |
|-------|-------|
| ADMIN | Quản trị hệ thống |
| LECTURER | Giảng viên |
| STUDENT | Sinh viên |

---

## 2. Use Case List

> **Note:** CHỈ các UC được liệt kê dưới đây được phép implement

---

### UC21 – Get User Profile

**Actor:** ADMIN, LECTURER, STUDENT  
**Description:** Lấy thông tin user

#### Preconditions

- User đã đăng nhập
- Access Token hợp lệ

#### Main Flow

1. Actor gọi API lấy user
2. System kiểm tra quyền
3. Trả về thông tin user

#### Authorization Rules

- **ADMIN:** xem mọi user
- **LECTURER:** xem student
- **STUDENT:** chỉ xem chính mình

#### API

**Endpoint:** `GET /api/users/{userId}`

#### Response DTO

```json
{
  "id": "uuid",
  "email": "string",
  "fullName": "string",
  "status": "ACTIVE",
  "roles": ["STUDENT"]
}
```

#### Error Codes

- `401 UNAUTHORIZED`
- `403 FORBIDDEN`
- `404 USER_NOT_FOUND`

---

### UC22 – Update User Profile

**Actor:** ADMIN, STUDENT

#### Preconditions

- User tồn tại
- status = ACTIVE

#### Main Flow

1. Actor gửi request update
2. System kiểm tra quyền
3. Update user

#### Rules

- **STUDENT:** chỉ update chính mình
- **ADMIN:** update mọi user
- Không update role tại UC này

#### API

**Endpoint:** `PUT /api/users/{userId}`

#### Request DTO

```json
{
  "fullName": "string"
}
```

#### Error Codes

- `403 FORBIDDEN`
- `404 USER_NOT_FOUND`
- `409 USER_INACTIVE`

---

### UC23 – Create Group

**Actor:** ADMIN

#### Preconditions

- Admin đăng nhập

#### Main Flow

1. Admin gửi thông tin group
2. System tạo group
3. Trả groupId

#### API

**Endpoint:** `POST /api/groups`

#### Request DTO

```json
{
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "uuid"
}
```

#### Error Codes

- `409 GROUP_NAME_DUPLICATE`
- `404 LECTURER_NOT_FOUND`

---

### UC24 – Add User to Group

**Actor:** ADMIN

#### Main Flow

1. Admin thêm user vào group
2. System tạo user_groups record

#### Rules

- Mỗi user chỉ thuộc **1 group / semester**
- Không thêm user INACTIVE

#### API

**Endpoint:** `POST /api/groups/{groupId}/members`

#### Request DTO

```json
{
  "userId": "uuid",
  "isLeader": false
}
```

#### Error Codes

- `409 USER_ALREADY_IN_GROUP`
- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`

---

### UC25 – Assign Group Role

**Actor:** ADMIN  
**Description:** Gán role trong group (LEADER / MEMBER)

#### API

**Endpoint:** `PUT /api/groups/{groupId}/members/{userId}/role`

#### Request DTO

```json
{
  "role": "LEADER"
}
```

#### Rules

- 1 group chỉ có 1 LEADER

#### Error Codes

- `409 LEADER_ALREADY_EXISTS`

---

### UC26 – Remove User from Group

**Actor:** ADMIN

#### API

**Endpoint:** `DELETE /api/groups/{groupId}/members/{userId}`

#### Rules

- Không remove LEADER nếu group còn active

---

## 3. Security Architecture

### 3.1 JWT Claims

```json
{
  "userId": "uuid",
  "roles": ["ADMIN", "LECTURER", "STUDENT"]
}
```

### 3.2 Authentication Flow

```
JwtFilter → Extract Token → Validate → Set SecurityContext
```

### 3.3 Role Mapping

**Important:** System Role ≠ Group Role

- **System Role:** ADMIN, LECTURER, STUDENT (stored in JWT)
- **Group Role:** LEADER, MEMBER (stored in user_groups table)

---

## 4. Open Questions & Answers

### Q1: Lecturer có thể đổi leader không?

**Answer:** NO (ADMIN only)

### Q2: Student có thể tự rời group không?

**Answer:** NO

### Q3: User có thể tham gia nhiều group ở các semester khác nhau không?

**Answer:** YES (1 group / semester)
