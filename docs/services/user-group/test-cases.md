# User-Group Service – Code-Driven Validation Checklist

Minimal checklist aligned to the current implementation.

## Authentication & JWT
- Missing `Authorization` on protected endpoints returns `401`.
- Invalid/expired JWT returns `401`.
- Valid JWT roles enforce `@PreAuthorize` correctly.

## Groups
- `POST /api/groups` is ADMIN-only.
- Duplicate `(group_name, semester_id)` (active) → `409`.
- Deleting a group with active memberships → `409`.

## Memberships
- Add member requires Identity role `STUDENT`.
- One group per user per semester enforced → `409` on second add.
- Promote leader demotes prior leader; exactly one leader per group.
- Removing leader blocked when active members exist; allowed when group empty.

## User profile authorization
- ADMIN can view any user.
- STUDENT can view self only.
- LECTURER can view only supervised students.

## gRPC
- Existence/member/leader checks reflect DB.
- Non-numeric IDs → `INVALID_ARGUMENT`.

## Kafka (when enabled)
- `user.deleted` event triggers membership cleanup per the configured consumer(s).
