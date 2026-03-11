# User Group Service

## Responsibilities

- Manage semester lifecycle and active semester selection.
- Manage groups and lecturer assignment.
- Manage group memberships, promotions, demotions, and removals.
- Provide selected user-to-group query flows.
- React to deleted users from the Identity Service.

## APIs

- User API: `/api/users/{userId}`, `/api/users`, `/api/users/{userId}/groups`.
- Semester API: `/api/semesters`, `/api/semesters/{id}`, `/api/semesters/code/{code}`, `/api/semesters/active`, `/api/semesters/{id}/activate`.
- Group API: `/api/groups`, `/api/groups/{groupId}`, `/api/groups/{groupId}/lecturer`.
- Membership API: `/api/groups/{groupId}/members`, `/api/groups/{groupId}/members/{userId}/promote`, `/api/groups/{groupId}/members/{userId}/demote`, `/api/groups/{groupId}/members/{userId}`.
- gRPC endpoint: `UserGroupGrpcServiceImpl` for internal service queries.

## Database

- `semesters`
- `groups`
- `user_semester_membership`

## Events

- Consumes `user.deleted` Kafka events to remove or soft delete memberships and handle leadership reassignment.

## Dependencies

- PostgreSQL for semester, group, and membership data.
- Identity Service gRPC for user validation and enrichment.
- Kafka for downstream identity deletion handling.
