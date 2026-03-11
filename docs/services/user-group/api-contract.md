# User-Group Service – Implemented REST API Contract (code-accurate)

This contract documents **only the endpoints implemented in `user-group-service`**.

Authentication:
- Send `Authorization: Bearer <jwt>`.
- Public paths (permitAll) are limited to actuator + swagger as configured in security.

Error format (implemented):
```json
{
  "code": "SOME_CODE",
  "message": "Human readable message",
  "timestamp": "2026-03-02T12:34:56Z",
  "errors": {
    "field": "validation message"
  }
}
```

## 1) Groups
Base path: `/api/groups`

### POST `/api/groups` (ADMIN)
Creates a group.

Notes from implementation:
- Enforces **group name uniqueness within a semester** for non-deleted groups.
- Validates `lecturerId` via Identity gRPC:
  - must exist
  - must be active
  - must have system role `LECTURER`
- `semesterId` existence is enforced by the DB FK to `semesters`.

### GET `/api/groups/{groupId}` (authenticated)
Returns group details including members.

Notes:
- Member identities (name/email) are fetched in batch from Identity.
- If a user was deleted in Identity, the response uses placeholders like `<Deleted User>`.

### GET `/api/groups?page&size&semesterId&lecturerId` (authenticated)
Lists groups with optional filters.

Query params (implemented):
- `page` (default `0`)
- `size` (default `20`)
- `semesterId` (optional)
- `lecturerId` (optional)

### PUT `/api/groups/{groupId}` (ADMIN)
Updates `groupName` and `lecturerId` (semester is treated as immutable).

### PATCH `/api/groups/{groupId}/lecturer` (ADMIN)
Updates only the lecturer.

### DELETE `/api/groups/{groupId}` (ADMIN)
Soft-deletes a group.

Business rule enforced in service:
- Group cannot be deleted if it has **any active memberships** (MEMBER or LEADER).

---

## 2) Group Members
Base path: `/api/groups/{groupId}/members`

### POST `/api/groups/{groupId}/members` (ADMIN or LECTURER)
Adds a student to a group.

Rules (implemented):
- Calls Identity gRPC `verifyUserExists(userId)`.
- Calls Identity gRPC `getUserRole(userId)` and requires `STUDENT`.
- Enforces **one group per user per semester** by checking existing membership.

### GET `/api/groups/{groupId}/members` (authenticated)
Returns membership rows (userId, groupId, semesterId, groupRole, timestamps).

### PUT `/api/groups/{groupId}/members/{userId}/promote` (ADMIN or LECTURER)
Promotes a membership to `LEADER`.

Rules (implemented):
- Demotes existing leader (if any) using a pessimistic lock query to avoid races.

### PUT `/api/groups/{groupId}/members/{userId}/demote` (ADMIN or LECTURER)
Demotes a `LEADER` to `MEMBER`.

### DELETE `/api/groups/{groupId}/members/{userId}` (ADMIN)
Soft-deletes a membership.

Rule (implemented):
- If removing the `LEADER`, the removal is blocked when there are active `MEMBER` rows remaining.

---

## 3) Users (Proxy to Identity)
Base path: `/api/users`

### GET `/api/users/{userId}` (authenticated)
Authorization is implemented in `UserServiceImpl`:
- `ADMIN`: any user
- `STUDENT`: self only
- `LECTURER`: only supervised students

### PUT `/api/users/{userId}` (authenticated)
Updates a user’s `fullName` by delegating to Identity gRPC.

Authorization rules (implemented):
- `ADMIN`: allowed
- `STUDENT`: self only
- `LECTURER`: forbidden

### GET `/api/users` (ADMIN)
Delegates to Identity `ListUsers(page,size,status,role)`.

Implementation detail:
- The service also applies optional in-memory filters and manual pagination.

### GET `/api/users/{userId}/groups` (authenticated)
Returns a user’s memberships and group metadata.

---

## 4) Semesters
Base path: `/api/semesters`

- `POST /api/semesters` — ADMIN
- `GET /api/semesters/{id}` — authenticated
- `GET /api/semesters/code/{code}` — authenticated
- `GET /api/semesters/active` — authenticated
- `GET /api/semesters` — authenticated
- `PUT /api/semesters/{id}` — ADMIN
- `PATCH /api/semesters/{id}/activate` — ADMIN

---

## 5) gRPC → HTTP mapping (implemented helper)
For many Identity gRPC calls routed through `GrpcExceptionHandler`:
- NOT_FOUND → 404
- PERMISSION_DENIED → 403
- UNAVAILABLE → 503
- DEADLINE_EXCEEDED → 504
- INVALID_ARGUMENT → 400
- FAILED_PRECONDITION → 409
- UNAUTHENTICATED → 401

(Calls not routed through `GrpcExceptionHandler` may surface as generic 500s.)
