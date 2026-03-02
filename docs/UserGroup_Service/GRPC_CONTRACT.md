# User-Group Service – gRPC Contract (code-accurate)

Ground truth:
- Proto: `user-group-service/src/main/proto/usergroup_service.proto`
- Implementation: `UserGroupGrpcServiceImpl`

## Exposed service: `UserGroupGrpcService`

- Port: `${GRPC_SERVER_PORT:9095}`
- No auth interceptor is implemented.

### ID encoding
Proto uses `string` IDs. Implementation expects:
- `group_id`: string parseable as Java `Long`
- `user_id`: string parseable as Java `Long`

(Proto comments mentioning UUID do not match the implementation.)

## RPCs

### `VerifyGroupExists`
- Input: `group_id`
- Output: `exists`, `deleted`, `message`

Behavior:
- Uses `findByIdAndNotDeleted`.
- `deleted` is always `false` (soft-deleted groups are treated as not found).

### `CheckGroupLeader`
- Input: `group_id`, `user_id`
- Output: `is_leader`, `message`

### `CheckGroupMember`
- Input: `group_id`, `user_id`
- Output: `is_member`, `role`, `message`

### `GetGroup`
- Input: `group_id`
- Output: group metadata (includes semester code)
- Errors: `NOT_FOUND` if missing/soft-deleted

## Consumed gRPC (Identity Service)
This service calls Identity Service via `IdentityServiceClient` for user existence/role/details and profile updates.
