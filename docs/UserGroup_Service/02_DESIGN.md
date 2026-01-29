# Design - User & Group Service

## 1. Database Design

### 1.1 users

| Column | Type | Constraint |
|--------|------|------------|
| id | UUID | PK |
| email | varchar | UNIQUE, NOT NULL |
| full_name | varchar | NOT NULL |
| status | enum | ACTIVE, INACTIVE |
| deleted_at | timestamp | nullable |

**Notes:**
- Soft delete: YES

---

### 1.2 groups

| Column | Type | Constraint |
|--------|------|------------|
| id | UUID | PK |
| group_name | varchar | NOT NULL |
| semester | varchar | NOT NULL |
| lecturer_id | UUID | FK → users.id |
| deleted_at | timestamp | nullable |

**Constraints:**
- UNIQUE (group_name, semester)

**Notes:**
- Soft delete: YES

---

### 1.3 user_groups

| Column | Type | Constraint |
|--------|------|------------|
| user_id | UUID | FK → users.id |
| group_id | UUID | FK → groups.id |
| role | enum | LEADER, MEMBER |
| deleted_at | timestamp | nullable |

**Constraints:**
- PRIMARY KEY (user_id, group_id)
- UNIQUE (group_id) WHERE role = 'LEADER'

**Notes:**
- Soft delete: YES
- Ensures only 1 LEADER per group

---

### 1.4 roles (SYSTEM ROLES)

| Column | Type | Constraint |
|--------|------|------------|
| id | UUID | PK |
| name | enum | ADMIN, LECTURER, STUDENT |

**Notes:**
- System-level roles (different from group roles)

---

## 2. Authorization Design

### 2.1 Model

**Hybrid RBAC + Group Role**

```
User
 ├─ System Role (ADMIN / LECTURER / STUDENT)
 └─ Group Role (LEADER / MEMBER)
```

### 2.2 Endpoint → Role Mapping

| Endpoint | Required Role |
|----------|---------------|
| GET /users | AUTHENTICATED |
| PUT /users/{id} | SELF or ADMIN |
| POST /groups | ADMIN |
| ADD MEMBER | ADMIN |
| ASSIGN ROLE | ADMIN |

### 2.3 Special Rules

- ADMIN không được xóa ADMIN khác
- STUDENT không sửa role
- Soft delete bắt buộc

---

## 3. Performance Considerations

### 3.1 Indexes

Add index hỗ trợ check:
- `user_groups(user_id, group_id)`
- `groups(id, semester)`

### 3.2 Constraint Enforcement

- Constraint enforce ở **SERVICE layer** (not DB)

---

## 4. Business Rules

### 4.1 Core Rules

1. **Soft Delete Only:** Không hard delete user/group
2. **Single Leader:** 1 group chỉ có 1 leader
3. **No Self-Join:** Student không tự join group
4. **Active Users Only:** Inactive user không được add vào group
5. **Role Separation:** Role system ≠ role group

---

### 4.2 Authorization Rules

- Mọi API phải kiểm tra quyền rõ ràng

---

### 4.3 Group Management Rules

#### Group Status

- Group không có trạng thái ARCHIVED
- Mọi group được coi là active

#### Leader Assignment

- **Assign LEADER mới:**
  - LEADER cũ tự động xuống MEMBER
  - Không cho remove LEADER nếu group còn ≥ 1 MEMBER

#### Transaction Management

- **Assign Group Role phải chạy trong 1 transaction:**
  - Nếu update LEADER mới fail → rollback toàn bộ

#### API cần @Transactional

- `assignGroupRole()`
- `addMember(isLeader=true)`

#### Rollback Conditions

| Condition | Action |
|-----------|--------|
| ❌ Không cho remove LEADER nếu group còn ≥ 1 MEMBER | Throw exception và rollback |
| ✔ Chỉ remove khi group còn 1 người | Allow removal |

---

## 5. Package Structure

```
com.samt.usergroup
 ├── controller
 ├── service
 │    └── impl
 ├── repository
 ├── entity
 ├── dto
 │    ├── request
 │    └── response
 ├── exception
 └── security
```

### Package Descriptions

| Package | Purpose |
|---------|---------|
| controller | REST API endpoints |
| service | Business logic layer |
| service.impl | Service implementations |
| repository | Data access layer (JPA) |
| entity | JPA entities |
| dto.request | Request DTOs |
| dto.response | Response DTOs |
| exception | Custom exceptions |
| security | Security configurations |
