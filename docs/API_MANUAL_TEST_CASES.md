# SAMT – Manual API Test Cases (Swagger UI)

Date: 2026-03-02  
Scope: Identity Service, User Group Service, Project Config Service, Sync Service

> This document is written for **manual execution via Swagger UI**. It includes per-endpoint test cases with realistic production expectations, including auth, validation, error handling, security, concurrency, and DB/Redis verification.

---

## 0) Common Test Setup (All Services)

### 0.1 Environments / Base URLs
- Swagger UI locations vary by deployment. In dev they are typically:
  - `http://<host>:<port>/swagger-ui/index.html`
  - OpenAPI JSON: `http://<host>:<port>/v3/api-docs`
- Authentication header for protected endpoints:
  - `Authorization: Bearer <accessToken>`

### 0.2 Auth model (JWT)
- **End-user JWT** (Bearer token)
  - `sub` (subject): user id (numeric string)
  - `roles`: list of role strings (e.g., `ROLE_ADMIN`, `ROLE_STUDENT`)
  - `jti`: required for replay protection/blacklist checks
- **Service-to-service JWT** (internal)
  - Used for internal endpoints (not user-facing)
  - Project Config internal HTTP endpoints enforce **single-use `jti`** (Redis replay cache)

### 0.3 Redis usage
- Access-token revocation uses Redis blacklist:
  - Key pattern: `blacklist:{<jti>}` → `revoked` (TTL ≈ remaining token lifetime)
- Internal service JWT replay protection (Project Config Service):
  - Key pattern: `internal-jti:{<jti>}` → `1` (TTL ≈ remaining token lifetime)

> Optional verification (if you have access): use `redis-cli` to check key existence after logout / internal calls.

### 0.4 PostgreSQL & Flyway
- Flyway migrations are assumed applied before testing.
- Prefer verifying by **querying tables** (read-only) after each mutation.

### 0.5 Test data (recommended)
Create/keep 3 accounts (via Identity Service or seeded env):
- `admin@samt.local` with role `ADMIN`
- `lecturer@samt.local` with role `LECTURER`
- `student1@samt.local` with role `STUDENT`
- `student2@samt.local` with role `STUDENT`

### 0.6 Standard security test variants (re-use across protected endpoints)
For any endpoint that requires JWT:
- Missing header → `401`
- Invalid signature / tampered token → `401`
- Expired token → `401`
- Revoked token (`jti` blacklisted) → `401` (or `503` if blacklist store unavailable in some services)
- Valid token but insufficient role → `403`

---

## 1) Identity Service – API Test Cases

### 1.1 Service overview
- Base paths:
  - Public auth: `/api/auth/*`
  - Admin: `/api/admin/*` (requires `ADMIN`)
- Primary tables (PostgreSQL):
  - `users` (with soft-delete columns `deleted_at`, `deleted_by`)
  - `refresh_tokens` (rotation + reuse detection)
  - `audit_logs`

### 1.2 Typical flows (CRUD + auth)
1. Register student → receive tokens
2. Login → receive tokens
3. Refresh token rotation → old refresh token revoked; new issued
4. Logout → refresh token revoked (idempotent)
5. Admin create/lock/unlock/soft-delete/restore users
6. Admin view audit history

---

### Endpoint: POST /api/auth/register

#### IDS-AUTH-REG-001 – Successful student registration
- **Endpoint**: `/api/auth/register`
- **HTTP Method**: `POST`
- **Preconditions**: Email not previously registered.
- **Request Body example**:
```json
{
  "email": "student1@samt.local",
  "password": "Str0ng@Pass!",
  "confirmPassword": "Str0ng@Pass!",
  "fullName": "Nguyen Van A",
  "role": "STUDENT"
}
```
- **Expected Status Code**: `201`
- **Expected Response**:
  - JSON contains `user`, `accessToken`, `refreshToken`, `tokenType=Bearer`, `expiresIn`.
  - `user.role=STUDENT`, `user.status=ACTIVE`.
- **Database verification**:
  - `users`: row exists with `email='student1@samt.local'`, `role='STUDENT'`, `status='ACTIVE'`.
  - `refresh_tokens`: at least 1 row for that `user_id` with `revoked=false` and future `expires_at`.

#### IDS-AUTH-REG-002 – Password mismatch
- **Endpoint**: `/api/auth/register`
- **HTTP Method**: `POST`
- **Preconditions**: None.
- **Request Body example**:
```json
{
  "email": "student_mismatch@samt.local",
  "password": "Str0ng@Pass!",
  "confirmPassword": "Wrong@Pass!",
  "fullName": "Mismatch User",
  "role": "STUDENT"
}
```
- **Expected Status Code**: `400`
- **Expected Response**:
```json
{ "error": "PASSWORD_MISMATCH", "message": "..." }
```
- **Database verification**: No row created in `users`.

#### IDS-AUTH-REG-003 – Invalid email format
- **Endpoint**: `/api/auth/register`
- **HTTP Method**: `POST`
- **Preconditions**: None.
- **Request Body example**:
```json
{
  "email": "not-an-email",
  "password": "Str0ng@Pass!",
  "confirmPassword": "Str0ng@Pass!",
  "fullName": "Invalid Email",
  "role": "STUDENT"
}
```
- **Expected Status Code**: `400`
- **Expected Response**:
```json
{ "error": "VALIDATION_ERROR", "message": "Invalid email format" }
```
- **Database verification**: No row created.

#### IDS-AUTH-REG-004 – Weak password policy rejection
- **Endpoint**: `/api/auth/register`
- **HTTP Method**: `POST`
- **Preconditions**: None.
- **Request Body example**:
```json
{
  "email": "weakpass@samt.local",
  "password": "password",
  "confirmPassword": "password",
  "fullName": "Weak Password",
  "role": "STUDENT"
}
```
- **Expected Status Code**: `400`
- **Expected Response**:
```json
{ "error": "VALIDATION_ERROR", "message": "Password does not meet requirements" }
```
- **Database verification**: No row created.

#### IDS-AUTH-REG-005 – Role not allowed for self-registration
- **Endpoint**: `/api/auth/register`
- **HTTP Method**: `POST`
- **Preconditions**: None.
- **Request Body example**:
```json
{
  "email": "badrole@samt.local",
  "password": "Str0ng@Pass!",
  "confirmPassword": "Str0ng@Pass!",
  "fullName": "Bad Role",
  "role": "ADMIN"
}
```
- **Expected Status Code**: `400`
- **Expected Response**:
```json
{ "error": "VALIDATION_ERROR", "message": "Invalid role specified" }
```
- **Database verification**: No row created.

#### IDS-AUTH-REG-006 – Duplicate email
- **Endpoint**: `/api/auth/register`
- **HTTP Method**: `POST`
- **Preconditions**: `student1@samt.local` already exists.
- **Request Body example**: same as IDS-AUTH-REG-001.
- **Expected Status Code**: `409`
- **Expected Response**:
```json
{ "error": "EMAIL_EXISTS", "message": "..." }
```
- **Database verification**: No additional `users` row created.

---

### Endpoint: POST /api/auth/login

#### IDS-AUTH-LOGIN-001 – Successful login
- **Endpoint**: `/api/auth/login`
- **HTTP Method**: `POST`
- **Preconditions**: User exists and is `ACTIVE`.
- **Request Body example**:
```json
{ "email": "student1@samt.local", "password": "Str0ng@Pass!" }
```
- **Expected Status Code**: `200`
- **Expected Response**:
```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<uuid>",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```
- **Database verification**:
  - `refresh_tokens`: new row created for the user, `revoked=false`.
  - `audit_logs`: contains `LOGIN_SUCCESS` for the user (if enabled).

#### IDS-AUTH-LOGIN-002 – Invalid credentials
- **Endpoint**: `/api/auth/login`
- **HTTP Method**: `POST`
- **Preconditions**: User exists.
- **Request Body example**:
```json
{ "email": "student1@samt.local", "password": "WrongPass!" }
```
- **Expected Status Code**: `401`
- **Expected Response**:
```json
{ "error": "INVALID_CREDENTIALS", "message": "..." }
```
- **Database verification**:
  - No new `refresh_tokens` row created.
  - `audit_logs`: `LOGIN_FAILED` (if enabled).

#### IDS-AUTH-LOGIN-003 – Locked account cannot login
- **Endpoint**: `/api/auth/login`
- **HTTP Method**: `POST`
- **Preconditions**: Admin locks user (see IDS-ADMIN-LOCK-001).
- **Request Body example**:
```json
{ "email": "student1@samt.local", "password": "Str0ng@Pass!" }
```
- **Expected Status Code**: `403`
- **Expected Response**:
```json
{ "error": "ACCOUNT_LOCKED", "message": "..." }
```
- **Database verification**: No new refresh token issued.

---

### Endpoint: POST /api/auth/refresh

#### IDS-AUTH-REFRESH-001 – Successful refresh (rotation)
- **Endpoint**: `/api/auth/refresh`
- **HTTP Method**: `POST`
- **Preconditions**: Have a valid `refreshToken` from login.
- **Request Body example**:
```json
{ "refreshToken": "<refresh-token-from-login>" }
```
- **Expected Status Code**: `200`
- **Expected Response**: New `accessToken` and **new** `refreshToken`.
- **Database verification**:
  - Old `refresh_tokens.token=<old>` is now `revoked=true`.
  - New refresh token exists with `revoked=false`.

#### IDS-AUTH-REFRESH-002 – Unknown refresh token
- **Endpoint**: `/api/auth/refresh`
- **HTTP Method**: `POST`
- **Preconditions**: None.
- **Request Body example**:
```json
{ "refreshToken": "00000000-0000-0000-0000-000000000000" }
```
- **Expected Status Code**: `401`
- **Expected Response**:
```json
{ "error": "TOKEN_INVALID", "message": "Token invalid" }
```
- **Database verification**: No changes.

#### IDS-AUTH-REFRESH-003 – Reuse detection: refresh twice with same token
- **Endpoint**: `/api/auth/refresh`
- **HTTP Method**: `POST`
- **Preconditions**:
  1) Refresh once successfully using a token `T1` (IDS-AUTH-REFRESH-001).
  2) Immediately call refresh again with the same `T1`.
- **Request Body example**:
```json
{ "refreshToken": "<T1>" }
```
- **Expected Status Code**: `401`
- **Expected Response**:
```json
{ "error": "TOKEN_INVALID", "message": "Token invalid" }
```
- **Database verification**:
  - All rows in `refresh_tokens` for that `user_id` are `revoked=true` (security containment).
  - `audit_logs`: contains `REFRESH_REUSE` (if enabled).

#### IDS-AUTH-REFRESH-004 – Locked account cannot refresh
- **Endpoint**: `/api/auth/refresh`
- **HTTP Method**: `POST`
- **Preconditions**: User is locked.
- **Request Body example**:
```json
{ "refreshToken": "<valid-refresh-token>" }
```
- **Expected Status Code**: `403`
- **Expected Response**:
```json
{ "error": "ACCOUNT_LOCKED", "message": "..." }
```
- **Database verification**:
  - All refresh tokens for the user become `revoked=true`.

---

### Endpoint: POST /api/auth/logout

#### IDS-AUTH-LOGOUT-001 – Successful logout (revokes refresh token, idempotent)
- **Endpoint**: `/api/auth/logout`
- **HTTP Method**: `POST`
- **Preconditions**: Valid access token in `Authorization` header.
- **Request Body example**:
```json
{ "refreshToken": "<refresh-token>" }
```
- **Expected Status Code**: `200`
- **Expected Response**: Standard API response envelope with `success=true`.
- **Database verification**:
  - `refresh_tokens.token=<refresh-token>` is `revoked=true` if it exists.
  - `audit_logs`: contains `LOGOUT` (if enabled).

#### IDS-AUTH-LOGOUT-002 – Logout with unknown refresh token still returns 200
- **Endpoint**: `/api/auth/logout`
- **HTTP Method**: `POST`
- **Preconditions**: Valid access token.
- **Request Body example**:
```json
{ "refreshToken": "00000000-0000-0000-0000-000000000000" }
```
- **Expected Status Code**: `200`
- **Expected Response**: Standard API response envelope with `success=true`.
- **Database verification**: No changes.

#### IDS-AUTH-LOGOUT-003 – Missing/invalid access token
- **Endpoint**: `/api/auth/logout`
- **HTTP Method**: `POST`
- **Preconditions**: None.
- **Request Body example**:
```json
{ "refreshToken": "<anything>" }
```
- **Expected Status Code**: `401`
- **Expected Response**: Standard security unauthorized response.
- **Database verification**: No changes.

---

### Endpoint: POST /api/admin/users (ADMIN)

#### IDS-ADMIN-CREATE-001 – Admin creates LECTURER user
- **Endpoint**: `/api/admin/users`
- **HTTP Method**: `POST`
- **Preconditions**: Admin access token; email not used.
- **Request Body example**:
```json
{
  "email": "lecturer@samt.local",
  "password": "Str0ng@Pass!",
  "fullName": "Le Thi Lecturer",
  "role": "LECTURER"
}
```
- **Expected Status Code**: `201`
- **Expected Response**: `AdminCreateUserResponse` with `user` and `temporaryPassword`.
- **Database verification**:
  - `users`: row exists with `role='LECTURER'`.
  - `audit_logs`: action `CREATE` by actor admin.

#### IDS-ADMIN-CREATE-002 – Non-admin forbidden
- **Endpoint**: `/api/admin/users`
- **HTTP Method**: `POST`
- **Preconditions**: Lecturer or student token.
- **Request Body example**: same as above.
- **Expected Status Code**: `403`
- **Expected Response**: Access denied.
- **Database verification**: No row created.

#### IDS-ADMIN-CREATE-003 – Duplicate email
- **Endpoint**: `/api/admin/users`
- **HTTP Method**: `POST`
- **Preconditions**: `lecturer@samt.local` already exists.
- **Request Body example**: same as IDS-ADMIN-CREATE-001.
- **Expected Status Code**: `409`
- **Expected Response**:
```json
{ "error": "EMAIL_EXISTS", "message": "..." }
```
- **Database verification**: No duplicate row.

---

### Endpoint: DELETE /api/admin/users/{userId} (ADMIN)

#### IDS-ADMIN-DEL-001 – Soft delete user
- **Endpoint**: `/api/admin/users/{userId}`
- **HTTP Method**: `DELETE`
- **Preconditions**: Admin token; target user exists and not deleted.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**:
```json
{ "message": "User deleted successfully", "userId": 123 }
```
- **Database verification**:
  - `users.id=<userId>` has `deleted_at IS NOT NULL`.
  - `refresh_tokens.user_id=<userId>` are revoked.
  - `audit_logs`: `SOFT_DELETE`.

#### IDS-ADMIN-DEL-002 – User not found
- **Endpoint**: `/api/admin/users/99999999`
- **HTTP Method**: `DELETE`
- **Preconditions**: Admin token.
- **Expected Status Code**: `404`
- **Expected Response**:
```json
{ "error": "USER_NOT_FOUND", "message": "..." }
```
- **Database verification**: No changes.

#### IDS-ADMIN-DEL-003 – Self-delete rejected
- **Endpoint**: `/api/admin/users/{adminUserId}`
- **HTTP Method**: `DELETE`
- **Preconditions**: Admin token for same user id.
- **Expected Status Code**: `400`
- **Expected Response**:
```json
{ "error": "SELF_ACTION_DENIED", "message": "..." }
```
- **Database verification**: Admin user row unchanged.

---

### Endpoint: POST /api/admin/users/{userId}/restore (ADMIN)

#### IDS-ADMIN-RESTORE-001 – Restore soft-deleted user
- **Endpoint**: `/api/admin/users/{userId}/restore`
- **HTTP Method**: `POST`
- **Preconditions**: Admin token; user was deleted.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**: `{"message":"User restored successfully","userId":<id>}`
- **Database verification**:
  - `users.deleted_at` becomes `NULL`.
  - `audit_logs`: `RESTORE`.

---

### Endpoint: POST /api/admin/users/{userId}/lock (ADMIN)

#### IDS-ADMIN-LOCK-001 – Lock user account (idempotent)
- **Endpoint**: `/api/admin/users/{userId}/lock?reason=...`
- **HTTP Method**: `POST`
- **Preconditions**: Admin token; user exists.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**: `{"message":"User locked successfully","userId":<id>}`
- **Database verification**:
  - `users.status` becomes `LOCKED`.
  - All `refresh_tokens` for user become revoked.
  - `audit_logs`: `ACCOUNT_LOCKED`.

---

### Endpoint: POST /api/admin/users/{userId}/unlock (ADMIN)

#### IDS-ADMIN-UNLOCK-001 – Unlock locked user
- **Endpoint**: `/api/admin/users/{userId}/unlock`
- **HTTP Method**: `POST`
- **Preconditions**: Admin token; user is locked.
- **Expected Status Code**: `200`
- **Expected Response**: `{"message":"User unlocked successfully","userId":<id>}`
- **Database verification**:
  - `users.status` becomes `ACTIVE`.
  - `audit_logs`: `ACCOUNT_UNLOCKED`.

---

### Endpoint: PUT /api/admin/users/{userId}/external-accounts (ADMIN)

#### IDS-ADMIN-EXT-001 – Map external accounts
- **Endpoint**: `/api/admin/users/{userId}/external-accounts`
- **HTTP Method**: `PUT`
- **Preconditions**: Admin token; user exists and not deleted.
- **Request Body example**:
```json
{ "jiraAccountId": "abc123:xyz", "githubUsername": "octo-user" }
```
- **Expected Status Code**: `200`
- **Expected Response**: `ExternalAccountsResponse` with updated user.
- **Database verification**:
  - `users.jira_account_id` / `users.github_username` updated.
  - Uniqueness: mapping cannot collide with another user.

#### IDS-ADMIN-EXT-002 – Conflict: external account already mapped
- **Endpoint**: `/api/admin/users/{userId}/external-accounts`
- **HTTP Method**: `PUT`
- **Preconditions**: Another user already mapped `githubUsername=octo-user`.
- **Request Body example**: same as above.
- **Expected Status Code**: `409`
- **Expected Response**:
```json
{ "error": "CONFLICT", "message": "..." }
```
- **Database verification**: Target user mapping unchanged.

---

### Endpoint: GET /api/admin/audit/* (ADMIN)

#### IDS-ADMIN-AUDIT-001 – Audit by entity
- **Endpoint**: `/api/admin/audit/entity/USER/{entityId}`
- **HTTP Method**: `GET`
- **Preconditions**: Admin token; entity exists.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**: Page of `AuditLog` entries ordered by timestamp desc.
- **Database verification**: N/A (read-only).

#### IDS-ADMIN-AUDIT-002 – Audit by actor
- **Endpoint**: `/api/admin/audit/actor/{actorId}`
- **HTTP Method**: `GET`
- **Preconditions**: Admin token.
- **Expected Status Code**: `200`

#### IDS-ADMIN-AUDIT-003 – Audit by date range (invalid range)
- **Endpoint**: `/api/admin/audit/range?startDate=...&endDate=...`
- **HTTP Method**: `GET`
- **Preconditions**: Admin token.
- **Request example**: `startDate` after `endDate`
- **Expected Status Code**: `400` or `200` with empty results depending on repository behavior.
- **Database verification**: N/A.

#### IDS-ADMIN-AUDIT-004 – Security events
- **Endpoint**: `/api/admin/audit/security-events`
- **HTTP Method**: `GET`
- **Preconditions**: Admin token.
- **Expected Status Code**: `200`

---

## 2) User Group Service – API Test Cases

### 2.1 Service overview
- Base paths:
  - Users: `/api/users/*`
  - Groups: `/api/groups/*`
  - Semesters: `/api/semesters/*`
- Primary tables (PostgreSQL):
  - `semesters`
  - `groups` (soft delete + optimistic locking `version`)
  - `user_semester_membership` (unique user per semester; unique leader per group; optimistic locking `version`)

### 2.2 Typical flows
1. Admin creates semester → activates semester
2. Admin creates group for a semester
3. Admin/Lecturer adds members → promote a leader
4. Student views own profile & group membership
5. Admin changes lecturer assignment
6. Admin soft-deletes group

---

### Endpoint: GET /api/users/{userId}

#### UGS-USER-GET-001 – Admin can fetch any user
- **Endpoint**: `/api/users/{userId}`
- **HTTP Method**: `GET`
- **Preconditions**: Admin token.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**: `UserResponse` with `id,email,fullName,status,roles`.
- **Database verification**: N/A

#### UGS-USER-GET-002 – Student can fetch self, not others
- **Endpoint**: `/api/users/{userId}`
- **HTTP Method**: `GET`
- **Preconditions**: Student token.
- **Steps**:
  1) Call with own `userId` → expect `200`.
  2) Call with different `userId` → expect `403`.
- **Expected Status Code**: `200` / `403`
- **Database verification**: N/A

#### UGS-USER-GET-003 – Not found
- **Endpoint**: `/api/users/99999999`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `404`
- **Expected Response**: ErrorResponse `code` from business exception.

---

### Endpoint: PUT /api/users/{userId}

#### UGS-USER-PUT-001 – Self update fullName
- **Endpoint**: `/api/users/{userId}`
- **HTTP Method**: `PUT`
- **Preconditions**: Student token; `userId` equals current user.
- **Request Body example**:
```json
{ "fullName": "Nguyen Van A Updated" }
```
- **Expected Status Code**: `200`
- **Expected Response**: Updated `UserResponse`.
- **Database verification**: user profile record updated in User Group Service DB (if stored) or cached mapping.

#### UGS-USER-PUT-002 – Validation error (invalid characters)
- **Endpoint**: `/api/users/{userId}`
- **HTTP Method**: `PUT`
- **Preconditions**: Authenticated.
- **Request Body example**:
```json
{ "fullName": "Robert'); DROP TABLE users;--" }
```
- **Expected Status Code**: `400`
- **Expected Response**: `VALIDATION_ERROR` with `errors.fullName`.

---

### Endpoint: GET /api/users (ADMIN)

#### UGS-USER-LIST-001 – List users (admin)
- **Endpoint**: `/api/users?page=0&size=20&status=&role=`
- **HTTP Method**: `GET`
- **Preconditions**: Admin token.
- **Expected Status Code**: `200`
- **Expected Response**: `PageResponse<UserResponse>` with pagination metadata.

#### UGS-USER-LIST-002 – Non-admin forbidden
- **Endpoint**: `/api/users`
- **HTTP Method**: `GET`
- **Preconditions**: Lecturer/student token.
- **Expected Status Code**: `403`

---

### Endpoint: GET /api/users/{userId}/groups

#### UGS-USER-GROUPS-001 – Get groups for user with optional semester filter
- **Endpoint**: `/api/users/{userId}/groups?semester=SPRING2025`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated; caller authorized per user visibility rules.
- **Expected Status Code**: `200`
- **Expected Response**: `UserGroupsResponse`.

---

### Endpoint: POST /api/semesters (ADMIN)

#### UGS-SEM-POST-001 – Create semester
- **Endpoint**: `/api/semesters`
- **HTTP Method**: `POST`
- **Preconditions**: Admin token.
- **Request Body example**:
```json
{
  "semesterCode": "SUMMER2026",
  "semesterName": "Summer Semester 2026",
  "startDate": "2026-06-01",
  "endDate": "2026-08-31"
}
```
- **Expected Status Code**: `201`
- **Expected Response**: `SemesterResponse`.
- **Database verification**:
  - `semesters.semester_code='SUMMER2026'` exists.

#### UGS-SEM-POST-002 – Duplicate semesterCode
- **Endpoint**: `/api/semesters`
- **HTTP Method**: `POST`
- **Preconditions**: Semester with code exists.
- **Expected Status Code**: `409`

---

### Endpoint: GET /api/semesters/{id}

#### UGS-SEM-GET-001 – Get semester by id
- **Endpoint**: `/api/semesters/{id}`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token; semester exists.
- **Expected Status Code**: `200`
- **Expected Response**: `SemesterResponse`.

#### UGS-SEM-GET-002 – Not found
- **Endpoint**: `/api/semesters/99999999`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `404`
- **Expected Response**:
```json
{ "code": "SEMESTER_NOT_FOUND", "message": "...", "timestamp": "..." }
```

---

### Endpoint: GET /api/semesters/code/{code}

#### UGS-SEM-CODE-001 – Get semester by code
- **Endpoint**: `/api/semesters/code/SUMMER2026`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token; code exists.
- **Expected Status Code**: `200`
- **Expected Response**: `SemesterResponse`.

#### UGS-SEM-CODE-002 – Invalid/unknown code
- **Endpoint**: `/api/semesters/code/UNKNOWN`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `404`

---

### Endpoint: GET /api/semesters/active

#### UGS-SEM-ACTIVE-001 – Get active semester
- **Endpoint**: `/api/semesters/active`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token; at least one active semester.
- **Expected Status Code**: `200`

#### UGS-SEM-ACTIVE-002 – No active semester
- **Endpoint**: `/api/semesters/active`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token; no active semester.
- **Expected Status Code**: `404` (typically `SEMESTER_NOT_FOUND`)

---

### Endpoint: GET /api/semesters

#### UGS-SEM-LIST-001 – List all semesters
- **Endpoint**: `/api/semesters`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `200`
- **Expected Response**: JSON array of `SemesterResponse`.

---

### Endpoint: PUT /api/semesters/{id} (ADMIN)

#### UGS-SEM-PUT-001 – Update semester
- **Endpoint**: `/api/semesters/{id}`
- **HTTP Method**: `PUT`
- **Preconditions**: Admin token; semester exists.
- **Request Body example**:
```json
{
  "semesterName": "Summer Semester 2026 (Updated)",
  "startDate": "2026-06-02",
  "endDate": "2026-08-30"
}
```
- **Expected Status Code**: `200`
- **Expected Response**: `SemesterResponse`.
- **Database verification**:
  - `semesters` row updated.

#### UGS-SEM-PUT-002 – Non-admin forbidden
- **Endpoint**: `/api/semesters/{id}`
- **HTTP Method**: `PUT`
- **Preconditions**: Lecturer/student token.
- **Expected Status Code**: `403`

---

### Endpoint: PATCH /api/semesters/{id}/activate (ADMIN)

#### UGS-SEM-ACT-001 – Activate semester
- **Endpoint**: `/api/semesters/{id}/activate`
- **HTTP Method**: `PATCH`
- **Preconditions**: Admin token; semester exists.
- **Expected Status Code**: `204`
- **Database verification**:
  - Target semester `is_active=true`.
  - Previously active semesters become inactive (if business rule enforced).

---

### Endpoint: POST /api/groups (ADMIN)

#### UGS-GRP-POST-001 – Create group (valid naming format)
- **Endpoint**: `/api/groups`
- **HTTP Method**: `POST`
- **Preconditions**: Admin token; semester exists.
- **Request Body example**:
```json
{ "groupName": "SE1705-G1", "semesterId": 1, "lecturerId": 2001 }
```
- **Expected Status Code**: `201`
- **Expected Response**: `GroupResponse`.
- **Database verification**:
  - `groups` row exists with `deleted_at IS NULL`.

#### UGS-GRP-POST-002 – Invalid groupName format
- **Endpoint**: `/api/groups`
- **HTTP Method**: `POST`
- **Preconditions**: Admin token.
- **Request Body example**:
```json
{ "groupName": "group-1", "semesterId": 1, "lecturerId": 2001 }
```
- **Expected Status Code**: `400`
- **Expected Response**: `VALIDATION_ERROR`.

#### UGS-GRP-POST-003 – Conflict: duplicate name in same semester (soft-delete aware)
- **Endpoint**: `/api/groups`
- **HTTP Method**: `POST`
- **Preconditions**: Group `SE1705-G1` exists in semester 1 with `deleted_at NULL`.
- **Expected Status Code**: `409`
- **Database verification**: No duplicate row.

---

### Endpoint: GET /api/groups/{groupId}

#### UGS-GRP-GET-001 – Get group details
- **Endpoint**: `/api/groups/{groupId}`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token; group exists.
- **Expected Status Code**: `200`
- **Expected Response**: `GroupDetailResponse` including members (if implemented) and lecturer/semester fields.

#### UGS-GRP-GET-002 – Not found
- **Endpoint**: `/api/groups/99999999`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `404`
- **Expected Response**:
```json
{ "code": "GROUP_NOT_FOUND", "message": "...", "timestamp": "..." }
```

---

### Endpoint: GET /api/groups

#### UGS-GRP-LIST-001 – List groups with defaults
- **Endpoint**: `/api/groups?page=0&size=20`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `200`
- **Expected Response**: `PageResponse<GroupListResponse>`.

#### UGS-GRP-LIST-002 – List groups filtered by semesterId and lecturerId
- **Endpoint**: `/api/groups?page=0&size=20&semesterId=1&lecturerId=2001`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `200`

#### UGS-GRP-LIST-003 – Boundary: invalid pagination
- **Endpoint**: `/api/groups?page=-1&size=0`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `400`
- **Expected Response**: ErrorResponse `code=BAD_REQUEST` (or similar).

---

### Endpoint: PUT /api/groups/{groupId} (ADMIN)

#### UGS-GRP-PUT-001 – Update group name and lecturer
- **Endpoint**: `/api/groups/{groupId}`
- **HTTP Method**: `PUT`
- **Preconditions**: Admin token.
- **Request Body example**:
```json
{ "groupName": "SE1705-G2", "lecturerId": 2002 }
```
- **Expected Status Code**: `200`
- **Database verification**:
  - `groups.group_name` updated.

#### UGS-GRP-PUT-002 – Concurrency: optimistic locking conflict
- **Endpoint**: `/api/groups/{groupId}`
- **HTTP Method**: `PUT`
- **Preconditions**:
  - Two admins (or two browser sessions) load group at same time.
  - Perform update in session A, then immediately update in session B.
- **Request Body example**: any valid update.
- **Expected Status Code**: `409`
- **Expected Response**:
  - `code=CONFLICT`, message indicates resource modified.
- **Database verification**: Only first update persisted.

---

### Endpoint: PATCH /api/groups/{groupId}/lecturer (ADMIN)

#### UGS-GRP-LECT-001 – Update lecturer
- **Endpoint**: `/api/groups/{groupId}/lecturer`
- **HTTP Method**: `PATCH`
- **Preconditions**: Admin token; group exists.
- **Request Body example**:
```json
{ "lecturerId": 2002 }
```
- **Expected Status Code**: `200`
- **Expected Response**: `GroupResponse` with updated lecturer.
- **Database verification**:
  - `groups.lecturer_id=2002`.

#### UGS-GRP-LECT-002 – Validation error (missing lecturerId)
- **Endpoint**: `/api/groups/{groupId}/lecturer`
- **HTTP Method**: `PATCH`
- **Preconditions**: Admin token.
- **Request Body example**:
```json
{}
```
- **Expected Status Code**: `400`
- **Expected Response**: `VALIDATION_ERROR` with `errors.lecturerId`.

---

### Endpoint: DELETE /api/groups/{groupId} (ADMIN)

#### UGS-GRP-DEL-001 – Soft delete group
- **Endpoint**: `/api/groups/{groupId}`
- **HTTP Method**: `DELETE`
- **Preconditions**: Admin token.
- **Expected Status Code**: `204`
- **Database verification**:
  - `groups.deleted_at IS NOT NULL`, `deleted_by` set.

---

### Endpoint: POST /api/groups/{groupId}/members (ADMIN or LECTURER)

#### UGS-MEM-ADD-001 – Add member to group
- **Endpoint**: `/api/groups/{groupId}/members`
- **HTTP Method**: `POST`
- **Preconditions**: Admin or lecturer token; user not already in a group for the semester.
- **Request Body example**:
```json
{ "userId": 3001 }
```
- **Expected Status Code**: `201`
- **Expected Response**: `MemberResponse` with `groupRole=MEMBER`.
- **Database verification**:
  - `user_semester_membership` has `(user_id=3001, semester_id=<group.semester_id>)` with `group_id=<groupId>`.

#### UGS-MEM-ADD-002 – Constraint: user already in another group same semester
- **Endpoint**: `/api/groups/{groupId}/members`
- **HTTP Method**: `POST`
- **Preconditions**: User already has membership row for the same `semester_id`.
- **Expected Status Code**: `409`
- **Database verification**: membership unchanged.

#### UGS-MEM-ADD-003 – Concurrency: double-add same member
- **Endpoint**: `/api/groups/{groupId}/members`
- **HTTP Method**: `POST`
- **Preconditions**: Fire two requests concurrently adding same `userId`.
- **Expected Status Code**: One `201`, one `409` (or `400`) depending on constraint handling.
- **Database verification**: only one membership row exists.

---

### Endpoint: GET /api/groups/{groupId}/members

#### UGS-MEM-LIST-001 – Get all members of group
- **Endpoint**: `/api/groups/{groupId}/members`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token; group exists.
- **Expected Status Code**: `200`
- **Expected Response**: JSON array of `MemberResponse`.

#### UGS-MEM-LIST-002 – Group not found
- **Endpoint**: `/api/groups/99999999/members`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated token.
- **Expected Status Code**: `404`
- **Expected Response**:
```json
{ "code": "GROUP_NOT_FOUND", "message": "...", "timestamp": "..." }
```

---

### Endpoint: PUT /api/groups/{groupId}/members/{userId}/promote

#### UGS-MEM-PROMOTE-001 – Promote member to leader
- **Endpoint**: `/api/groups/{groupId}/members/{userId}/promote`
- **HTTP Method**: `PUT`
- **Preconditions**: Admin or lecturer; user is member of group.
- **Expected Status Code**: `200`
- **Database verification**:
  - `user_semester_membership.group_role='LEADER'` for the user.
  - Unique leader constraint holds.

#### UGS-MEM-PROMOTE-002 – Conflict: leader already exists
- **Endpoint**: `/api/groups/{groupId}/members/{userId}/promote`
- **HTTP Method**: `PUT`
- **Preconditions**: Another leader already exists.
- **Expected Status Code**: `409`

---

### Endpoint: PUT /api/groups/{groupId}/members/{userId}/demote

#### UGS-MEM-DEMOTE-001 – Demote leader to member
- **Endpoint**: `/api/groups/{groupId}/members/{userId}/demote`
- **HTTP Method**: `PUT`
- **Preconditions**: Admin or lecturer; user currently leader.
- **Expected Status Code**: `200`
- **Database verification**: `group_role='MEMBER'`.

---

### Endpoint: DELETE /api/groups/{groupId}/members/{userId} (ADMIN)

#### UGS-MEM-DEL-001 – Remove member
- **Endpoint**: `/api/groups/{groupId}/members/{userId}`
- **HTTP Method**: `DELETE`
- **Preconditions**: Admin token.
- **Expected Status Code**: `204`
- **Database verification**:
  - membership row soft-deleted (`deleted_at` set) or removed depending on implementation.

---

## 3) Project Config Service – API Test Cases

### 3.1 Service overview
- Base paths:
  - Public async API: `/api/project-configs/*`
  - Internal HTTP API: `/internal/project-configs/*` (service JWT + replay protection)
- Primary table:
  - `project_configs` (UUID PK, state machine, soft delete, optimistic locking)

### 3.2 Typical flows
1. Create config for group (unique per group)
2. Fetch config by id
3. Update config (partial) → if creds change, state transitions to `DRAFT`
4. Verify config (calls Jira/GitHub) → `VERIFIED` or `INVALID`
5. Soft delete and admin restore

---

### 3.3 Response/Errors (important for assertions)
- **Success wrapper** (all public and internal endpoints):
```json
{ "data": { }, "timestamp": "2026-03-02T00:00:00Z" }
```
- **Unauthorized** (Spring Security entry point for missing/invalid token):
```json
{ "error": "unauthorized" }
```
- **Validation/business errors** (GlobalExceptionHandler):
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "...",
    "field": "jiraHostUrl",
    "details": {
      "jiraHostUrl": "...",
      "githubRepoUrl": "..."
    }
  },
  "timestamp": "2026-03-02T00:00:00Z"
}
```

### 3.4 Security-specific cases (Project Config)
- If JWT **has no `jti`**: filter returns `401` and may return an **empty body** (status only).
- If JWT **is blacklisted** (`blacklist:{jti}` exists): filter returns `401` and may return an **empty body** (status only).
- If Redis blacklist cannot be checked (Redis outage): filter **fails closed** with `503`.

---

### Endpoint: POST /api/project-configs

#### PCS-CFG-POST-001 – Create config (happy path)
- **Endpoint**: `/api/project-configs`
- **HTTP Method**: `POST`
- **Preconditions**: Valid JWT with subject userId; user has permission to create config for group (per service rules).
- **Request Body example**:
```json
{
  "groupId": 1,
  "jiraHostUrl": "https://example.atlassian.net",
  "jiraApiToken": "ATATTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
  "githubRepoUrl": "https://github.com/example-org/example-repo",
  "githubToken": "ghp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
}
```
> Notes:
> - Jira token must be **100–500** chars and match `^ATATT...`.
> - GitHub token must be **40–255** chars and match `^ghp_...`.

- **Expected Status Code**: `201`
- **Expected Response**:
  - Wrapper: `{ "data": <ConfigResponse>, "timestamp": "..." }`
  - `ConfigResponse.jiraApiToken` and `githubToken` are **masked**.
- **Database verification**:
  - `project_configs` row exists with `group_id=<groupId>`, `state='DRAFT'`, encrypted token columns non-empty.

#### PCS-CFG-POST-002 – Validation: invalid Jira host URL
- **Endpoint**: `/api/project-configs`
- **HTTP Method**: `POST`
- **Preconditions**: Authenticated.
- **Request Body example**:
```json
{
  "groupId": 1,
  "jiraHostUrl": "http://evil.example.com",
  "jiraApiToken": "ATATT...",
  "githubRepoUrl": "https://github.com/org/repo",
  "githubToken": "ghp_aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
}
```
- **Expected Status Code**: `400`
- **Expected Response**: ErrorResponse with `error.code=VALIDATION_ERROR` and `error.field=jiraHostUrl`.

#### PCS-CFG-POST-003 – Conflict: config already exists for group
- **Endpoint**: `/api/project-configs`
- **HTTP Method**: `POST`
- **Preconditions**: A non-deleted config already exists for the same `groupId`.
- **Expected Status Code**: `409`
- **Expected Response**: ErrorResponse with `error.code=CONFIG_ALREADY_EXISTS` (or `CONFLICT` for unique constraint path).
- **Database verification**: Only one active config row for that group.

---

### Endpoint: GET /api/project-configs/{id}

#### PCS-CFG-GET-001 – Get config (masked tokens)
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `GET`
- **Preconditions**: Valid JWT; config exists and caller authorized.
- **Expected Status Code**: `200`
- **Expected Response**: Wrapper with `data` containing masked token fields.
- **Database verification**: N/A

#### PCS-CFG-GET-002 – Not found
- **Endpoint**: `/api/project-configs/00000000-0000-0000-0000-000000000000`
- **HTTP Method**: `GET`
- **Preconditions**: Authenticated.
- **Expected Status Code**: `404`
- **Expected Response**: ErrorResponse with `error.code=CONFIG_NOT_FOUND`.

---

### Endpoint: PUT /api/project-configs/{id}

#### PCS-CFG-PUT-001 – Partial update URLs only
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `PUT`
- **Preconditions**: Authorized; config exists.
- **Request Body example**:
```json
{ "githubRepoUrl": "https://github.com/org/new-repo" }
```
- **Expected Status Code**: `200`
- **Expected Response**: Updated config with masked tokens.
- **Database verification**: `github_repo_url` updated.

#### PCS-CFG-PUT-002 – Concurrency: optimistic locking conflict
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `PUT`
- **Preconditions**: Two sessions update same config quickly.
- **Expected Status Code**: `409`
- **Expected Response**: ErrorResponse with `error.code=CONFLICT` and message like "Configuration was modified by another user...".
- **Database verification**: Only one update applied.

---

### Endpoint: POST /api/project-configs/{id}/verify

#### PCS-CFG-VERIFY-001 – Verify success → state VERIFIED
- **Endpoint**: `/api/project-configs/{id}/verify`
- **HTTP Method**: `POST`
- **Preconditions**: Authorized leader/admin; valid credentials stored.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**: `VerificationResponse` with `jira.status=SUCCESS`, `github.status=SUCCESS`, `state=VERIFIED`.
- **Database verification**:
  - `project_configs.state='VERIFIED'`
  - `last_verified_at` updated.

#### PCS-CFG-VERIFY-002 – External timeout or downstream failure
- **Endpoint**: `/api/project-configs/{id}/verify`
- **HTTP Method**: `POST`
- **Preconditions**: Force Jira/GitHub to timeout (bad DNS / blocked network / invalid token).
- **Expected Status Code**: `504`
- **Expected Response**: ErrorResponse with `error.code=GATEWAY_TIMEOUT`.
- **Database verification**:
  - `project_configs.state` updated according to failure policy (commonly remains `DRAFT`/`INVALID`).

#### PCS-CFG-VERIFY-003 – Overload: executor rejects tasks
- **Endpoint**: `/api/project-configs/{id}/verify`
- **HTTP Method**: `POST`
- **Preconditions**: Trigger many concurrent verify requests to exhaust executor.
- **Expected Status Code**: `503`
- **Expected Response**: ErrorResponse with `error.code=SERVICE_OVERLOADED`.

---

### Endpoint: DELETE /api/project-configs/{id}

#### PCS-CFG-DEL-001 – Soft delete
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `DELETE`
- **Preconditions**: Authorized.
- **Expected Status Code**: `200`
- **Expected Response**: `DeleteResponse` wrapped in `{data,timestamp}`.
- **Database verification**:
  - `deleted_at IS NOT NULL`, `state='DELETED'`.

---

### Endpoint: POST /api/project-configs/admin/{id}/restore (ADMIN)

#### PCS-CFG-RESTORE-001 – Restore deleted config
- **Endpoint**: `/api/project-configs/admin/{id}/restore`
- **HTTP Method**: `POST`
- **Preconditions**: Admin role; config is deleted.
- **Expected Status Code**: `200`
- **Expected Response**: `RestoreResponse` wrapped.
- **Database verification**:
  - `deleted_at` cleared; state transitions away from `DELETED` per service rules.

#### PCS-CFG-RESTORE-002 – Non-admin forbidden
- **Endpoint**: `/api/project-configs/admin/{id}/restore`
- **HTTP Method**: `POST`
- **Preconditions**: Student/lecturer token.
- **Expected Status Code**: `403`

---

### Endpoint: GET /internal/project-configs/{id}/tokens (Service-to-service)

#### PCS-INT-TOKENS-001 – Valid internal service JWT (first-use jti)
- **Endpoint**: `/internal/project-configs/{id}/tokens`
- **HTTP Method**: `GET`
- **Preconditions**:
  - Internal JWT signed with `internal-auth.jwt.secret`
  - `iss=samt-internal`, `aud=project-config-service`, `sub=sync-service`
  - Includes `jti`, `iat`, `exp` within max lifetime
  - `jti` not used before
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**:
  - Wrapper: `{ "data": <InternalCredentialsMetadataResponse>, "timestamp": "..." }` (NO secrets).
- **Redis verification (optional)**:
  - `internal-jti:{<jti>}` exists.

#### PCS-INT-TOKENS-002 – Replay attack: reuse same internal token
- **Endpoint**: `/internal/project-configs/{id}/tokens`
- **HTTP Method**: `GET`
- **Preconditions**: Re-send the exact same internal JWT as PCS-INT-TOKENS-001.
- **Expected Status Code**: `401`
- **Expected Response**:
```json
{ "error": "unauthorized" }
```

#### PCS-INT-TOKENS-003 – Missing jti
- **Endpoint**: `/internal/project-configs/{id}/tokens`
- **HTTP Method**: `GET`
- **Preconditions**: Internal JWT without `jti`.
- **Expected Status Code**: `401`
- **Expected Response**: `{"error":"unauthorized"}`

#### PCS-INT-TOKENS-004 – Missing Authorization header
- **Endpoint**: `/internal/project-configs/{id}/tokens`
- **HTTP Method**: `GET`
- **Preconditions**: None.
- **Expected Status Code**: `401`
- **Expected Response**:
```json
{ "error": "unauthorized" }
```

---

### Endpoint: (Auth edge) /api/project-configs/*

#### PCS-AUTH-001 – Missing Authorization header
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `GET`
- **Preconditions**: None.
- **Expected Status Code**: `401`
- **Expected Response**:
```json
{ "error": "unauthorized" }
```

#### PCS-AUTH-002 – JWT missing jti (request rejected by filter)
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `GET`
- **Preconditions**: Use a JWT without `jti` claim.
- **Expected Status Code**: `401`
- **Expected Response**: Response body may be empty (status-only).

#### PCS-AUTH-003 – JWT revoked (blacklisted jti)
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `GET`
- **Preconditions**: Use a JWT whose `jti` key exists in Redis as `blacklist:{jti}`.
- **Expected Status Code**: `401`
- **Expected Response**: Response body may be empty (status-only).

#### PCS-AUTH-004 – Redis outage (fail-closed)
- **Endpoint**: `/api/project-configs/{id}`
- **HTTP Method**: `GET`
- **Preconditions**: Redis unavailable or `hasKey` fails.
- **Expected Status Code**: `503`
- **Expected Response**: Response body may be empty (status-only).

---

## 4) Sync Service – API & Operational Test Cases

### 4.1 Service overview
- Sync Service is primarily **scheduler-driven** (background jobs) and uses:
  - PostgreSQL tables: `sync_jobs`, `shedlock`
  - Redis (present in config; may be used for shared concerns)
  - Resilience4j: retry/circuit breaker/rate limiter for Jira/GitHub and gRPC

### 4.2 Exposed HTTP endpoints
Based on current code layout, Sync Service has **no public REST controllers** under `/api/*`.
- It does include Spring Boot Actuator, with management server bound to `127.0.0.1` and separate port.

#### Endpoint: GET (management) /actuator/health

##### SSS-ACT-HEALTH-001 – Health check accessible locally
- **Endpoint**: `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/health` (default `18084`)
- **HTTP Method**: `GET`
- **Preconditions**: Service running.
- **Request Body example**: N/A
- **Expected Status Code**: `200`
- **Expected Response**: `{"status":"UP"...}` (details may be limited).
- **Database verification**: N/A

#### Endpoint: GET (management) /actuator/info

##### SSS-ACT-INFO-001 – Info endpoint accessible locally
- **Endpoint**: `http://127.0.0.1:<MANAGEMENT_SERVER_PORT>/actuator/info`
- **HTTP Method**: `GET`
- **Preconditions**: Service running.
- **Expected Status Code**: `200`

### 4.3 Scheduler / retry / circuit breaker scenarios (operational)

> These are validated via logs + DB state, not Swagger UI.

#### SSS-SCHED-001 – ShedLock prevents double execution (multi-replica)
- **Endpoint**: N/A (scheduled job)
- **HTTP Method**: N/A
- **Preconditions**:
  - Run 2 instances of Sync Service against same database.
  - Scheduler enabled.
- **Steps**:
  1) Wait for scheduled window (Jira/GitHub cron).
  2) Observe only one instance acquires lock.
- **Expected Status Code**: N/A
- **Expected Response**: N/A
- **Database verification**:
  - `shedlock` row for job name shows single `locked_by` at any time.
  - No duplicate `sync_jobs` entries for the same run window/correlation.

#### SSS-RES-RETRY-001 – Retry on transient Jira failure
- **Endpoint**: N/A
- **HTTP Method**: N/A
- **Preconditions**: Make Jira unreachable (network block / invalid host) for a short period.
- **Expected Result**:
  - Resilience4j `jiraRetry` attempts up to configured max.
  - Job may end `FAILED` with error message if retries exhausted.
- **Database verification**:
  - `sync_jobs.status='FAILED'` and `error_message` populated.

#### SSS-RES-CB-001 – Circuit breaker opens after repeated failures
- **Endpoint**: N/A
- **HTTP Method**: N/A
- **Preconditions**: Keep Jira/GitHub consistently failing.
- **Expected Result**:
  - Circuit breaker transitions to OPEN; subsequent calls are short-circuited.
  - Reduced load and faster failure.
- **Database verification**:
  - Multiple `sync_jobs` entries show fast failures with consistent error.

#### SSS-GRPC-RES-001 – gRPC retry/circuit breaker for Project Config gRPC
- **Endpoint**: N/A
- **HTTP Method**: N/A
- **Preconditions**: Make Project Config gRPC unavailable.
- **Expected Result**:
  - gRPC call retries per `grpcRetry`.
  - Circuit breaker opens per `grpcCircuitBreaker`.
- **Database verification**:
  - Sync job failures recorded; no partial data persisted for that run if transactional boundaries enforced.

---

## Appendix A – Suggested DB verification queries

> Adjust schema/database name per environment.

### Identity Service
```sql
SELECT id, email, role, status, deleted_at FROM users WHERE email = 'student1@samt.local';
SELECT id, user_id, token, expires_at, revoked FROM refresh_tokens WHERE user_id = <id> ORDER BY id DESC;
SELECT action, outcome, actor_id, timestamp FROM audit_logs WHERE entity_type='USER' AND entity_id=<id> ORDER BY timestamp DESC;
```

### User Group Service
```sql
SELECT id, semester_code, is_active FROM semesters ORDER BY id DESC;
SELECT id, group_name, semester_id, lecturer_id, deleted_at, version FROM groups WHERE id=<groupId>;
SELECT user_id, semester_id, group_id, group_role, deleted_at, version FROM user_semester_membership WHERE user_id=<userId>;
```

### Project Config Service
```sql
SELECT id, group_id, state, last_verified_at, invalid_reason, deleted_at, version
FROM project_configs WHERE group_id=<groupId>;
```

### Sync Service
```sql
SELECT id, project_config_id, job_type, status, items_processed, error_message, started_at, completed_at
FROM sync_jobs ORDER BY id DESC LIMIT 20;
SELECT name, lock_until, locked_at, locked_by FROM shedlock;
```
