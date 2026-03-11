# User-Group Service – Database Schema (code-accurate)

Ground truth:
- Flyway migrations in `user-group-service/src/main/resources/db/migration/`.
- JPA entities: `Semester`, `Group`, `UserSemesterMembership`.

## Tables

### `semesters`
Key columns:
- `id` BIGSERIAL PK
- `semester_code` UNIQUE
- `is_active` boolean

### `groups`
Key columns:
- `id` BIGSERIAL PK
- `group_name` (unique per semester for active rows)
- `semester_id` BIGINT FK → `semesters(id)`
- `lecturer_id` BIGINT (logical reference to Identity user)
- soft delete: `deleted_at`, `deleted_by`
- optimistic lock: `version` (added in V5)

Indexes/constraints:
- partial unique index `(group_name, semester_id) WHERE deleted_at IS NULL`

### `user_semester_membership`
Encodes the user ↔ group relationship scoped by semester.

Key columns:
- composite PK: `(user_id, semester_id)`
- `group_id` BIGINT FK → `groups(id)`
- `group_role` in `{LEADER, MEMBER}`
- soft delete: `deleted_at`, `deleted_by`
- optimistic lock: `version` (added in V5)

Enforced constraints:
- one group per user per semester: composite PK + trigger guard
- at most one leader per group: partial unique index on `(group_id)` where role=LEADER and not deleted

## Triggers / Functions

### `check_user_semester_uniqueness` (V2)
Prevents inserting/updating a membership if an active membership already exists for `(user_id, semester_id)`.

### `check_group_has_leader` (V3)
Prevents removing/demoting the last leader only when other active members exist.
- If the group becomes empty, removing the last leader is allowed.

## Soft delete semantics
- `Group` and `UserSemesterMembership` use `@SQLRestriction("deleted_at IS NULL")`.
- Service methods soft-delete by setting `deleted_at` and `deleted_by`.

## Cross-service references
No DB-level FK exists to Identity Service.
- `groups.lecturer_id`, `user_semester_membership.user_id`, `deleted_by` are logical references.
- Validity is enforced via Identity gRPC in service logic.
