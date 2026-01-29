# API Specification - User & Group Service

## Common Error Response Format

```json
{
  "code": "USER_NOT_FOUND",
  "message": "User not found",
  "timestamp": "2026-01-01T10:00:00Z"
}
```

### HTTP Status ↔ Error Code Mapping

| HTTP Status | Error Codes |
|-------------|-------------|
| 400 | BAD_REQUEST |
| 401 | UNAUTHORIZED |
| 403 | FORBIDDEN |
| 404 | USER_NOT_FOUND, GROUP_NOT_FOUND, LECTURER_NOT_FOUND |
| 409 | USER_ALREADY_IN_GROUP, USER_ALREADY_IN_GROUP_SAME_SEMESTER, USER_INACTIVE, LEADER_ALREADY_EXISTS, CANNOT_REMOVE_LEADER, GROUP_NAME_DUPLICATE |

---

## User Management APIs

### 1. Get User Profile

**Endpoint:** `GET /api/users/{userId}`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (all users), LECTURER (students), STUDENT (self only)

#### Response

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

### 2. Update User Profile

**Endpoint:** `PUT /api/users/{userId}`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (any user), STUDENT (self only)

#### Request

```json
{
  "fullName": "string"
}
```

#### Response

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

- `403 FORBIDDEN`
- `404 USER_NOT_FOUND`
- `409 USER_INACTIVE`

---

### 3. List Users

**Endpoint:** `GET /api/users`  
**Security:** JWT (ADMIN)

#### Query Parameters

- `page` (default: 0)
- `size` (default: 20)
- `status` (optional: ACTIVE, INACTIVE)
- `role` (optional: ADMIN, LECTURER, STUDENT)

#### Response

```json
{
  "content": [
    {
      "id": "uuid",
      "email": "string",
      "fullName": "string",
      "status": "ACTIVE",
      "roles": ["STUDENT"]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 100,
  "totalPages": 5
}
```

---

## Group Management APIs

### 4. Create Group

**Endpoint:** `POST /api/groups`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "uuid"
}
```

#### Response

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturerId": "uuid",
  "lecturerName": "Dr. Nguyen Van A"
}
```

#### Error Codes

- `409 GROUP_NAME_DUPLICATE`
- `404 LECTURER_NOT_FOUND`

---

### 5. Get Group Details

**Endpoint:** `GET /api/groups/{groupId}`  
**Security:** JWT (AUTHENTICATED)

#### Response

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1",
  "semester": "Spring2026",
  "lecturer": {
    "id": "uuid",
    "fullName": "Dr. Nguyen Van A",
    "email": "lecturer@example.com"
  },
  "members": [
    {
      "userId": "uuid",
      "fullName": "Student Name",
      "email": "student@example.com",
      "role": "LEADER"
    }
  ],
  "memberCount": 5
}
```

#### Error Codes

- `404 GROUP_NOT_FOUND`

---

### 6. List Groups

**Endpoint:** `GET /api/groups`  
**Security:** JWT (AUTHENTICATED)

#### Query Parameters

- `page` (default: 0)
- `size` (default: 20)
- `semester` (optional)
- `lecturerId` (optional)

#### Response

```json
{
  "content": [
    {
      "id": "uuid",
      "groupName": "SE1705-G1",
      "semester": "Spring2026",
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

### 7. Update Group

**Endpoint:** `PUT /api/groups/{groupId}`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "groupName": "SE1705-G1-Updated",
  "lecturerId": "uuid"
}
```

**Note:** Semester is immutable

#### Response

```json
{
  "id": "uuid",
  "groupName": "SE1705-G1-Updated",
  "semester": "Spring2026",
  "lecturerId": "uuid",
  "lecturerName": "Dr. Nguyen Van B"
}
```

---

### 8. Delete Group

**Endpoint:** `DELETE /api/groups/{groupId}`  
**Security:** JWT (ADMIN)

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `404 GROUP_NOT_FOUND`

---

## Group Member Management APIs

### 9. Add Member to Group

**Endpoint:** `POST /api/groups/{groupId}/members`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "userId": "uuid",
  "isLeader": false
}
```

#### Response

**Status:** `201 CREATED`

```json
{
  "userId": "uuid",
  "groupId": "uuid",
  "fullName": "Student Name",
  "email": "student@example.com",
  "role": "MEMBER"
}
```

#### Error Codes

- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`
- `409 USER_ALREADY_IN_GROUP`
- `409 USER_ALREADY_IN_GROUP_SAME_SEMESTER`
- `409 USER_INACTIVE`
- `409 LEADER_ALREADY_EXISTS`

---

### 10. Assign Group Role

**Endpoint:** `PUT /api/groups/{groupId}/members/{userId}/role`  
**Security:** JWT (ADMIN)

#### Request

```json
{
  "role": "LEADER"
}
```

#### Response

**Status:** `200 OK`

```json
{
  "userId": "uuid",
  "groupId": "uuid",
  "fullName": "Student Name",
  "email": "student@example.com",
  "role": "LEADER"
}
```

#### Error Codes

- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`
- `409 LEADER_ALREADY_EXISTS`

**Note:** When assigning new LEADER, old LEADER auto-demotes to MEMBER

---

### 11. Remove Member from Group

**Endpoint:** `DELETE /api/groups/{groupId}/members/{userId}`  
**Security:** JWT (ADMIN)

#### Response

**Status:** `204 NO_CONTENT`

#### Error Codes

- `404 USER_NOT_FOUND`
- `404 GROUP_NOT_FOUND`
- `409 CANNOT_REMOVE_LEADER`

---

### 12. Get Group Members

**Endpoint:** `GET /api/groups/{groupId}/members`  
**Security:** JWT (AUTHENTICATED)

#### Query Parameters

- `role` (optional: LEADER, MEMBER)

#### Response

```json
{
  "groupId": "uuid",
  "groupName": "SE1705-G1",
  "members": [
    {
      "userId": "uuid",
      "fullName": "Student Name",
      "email": "student@example.com",
      "role": "LEADER"
    }
  ],
  "totalMembers": 5
}
```

---

### 13. Get User's Groups

**Endpoint:** `GET /api/users/{userId}/groups`  
**Security:** JWT (AUTHENTICATED)  
**Roles:** ADMIN (any user), LECTURER (students), STUDENT (self only)

#### Query Parameters

- `semester` (optional)

#### Response

```json
{
  "userId": "uuid",
  "groups": [
    {
      "groupId": "uuid",
      "groupName": "SE1705-G1",
      "semester": "Spring2026",
      "role": "MEMBER",
      "lecturerName": "Dr. Nguyen Van A"
    }
  ]
}
```

---

## Health Check

### 14. Health Check

**Endpoint:** `GET /actuator/health`  
**Security:** Public

#### Response

```json
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP"
    }
  }
}
```

---

## API Summary Table

| Method | Endpoint | Role | Description |
|--------|----------|------|-------------|
| GET | /api/users/{userId} | AUTHENTICATED | Get user profile |
| PUT | /api/users/{userId} | ADMIN, SELF | Update user profile |
| GET | /api/users | ADMIN | List all users |
| POST | /api/groups | ADMIN | Create group |
| GET | /api/groups/{groupId} | AUTHENTICATED | Get group details |
| GET | /api/groups | AUTHENTICATED | List groups |
| PUT | /api/groups/{groupId} | ADMIN | Update group |
| DELETE | /api/groups/{groupId} | ADMIN | Delete group (soft) |
| POST | /api/groups/{groupId}/members | ADMIN | Add member |
| PUT | /api/groups/{groupId}/members/{userId}/role | ADMIN | Assign role |
| DELETE | /api/groups/{groupId}/members/{userId} | ADMIN | Remove member |
| GET | /api/groups/{groupId}/members | AUTHENTICATED | Get members |
| GET | /api/users/{userId}/groups | AUTHENTICATED | Get user's groups |
