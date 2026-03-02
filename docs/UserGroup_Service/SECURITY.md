# User-Group Service – Security (code-accurate)

This file documents **the security behavior implemented in `user-group-service`**, without re-explaining JWT theory.

## Authentication & JWT validation (implemented)

- Input: `Authorization: Bearer <jwt>`
- `JwtAuthenticationFilter` parses the header and calls `JwtService`.
- `JwtService` validates:
  - **HS256 signature** using `jwt.secret` (HMAC key)
  - **expiration** (`exp`), when present
- Claims consumed:
  - `userId` claim as string, or fallback to `sub`
  - `roles` claim as list of strings

If JWT validation fails:
- The filter logs and continues **without** setting authentication.
- Protected endpoints are then rejected by Spring Security with `401` using `JwtAuthenticationEntryPoint`.

## Authorization (implemented)

### Controller-level RBAC
Endpoints use `@PreAuthorize` for coarse-grained RBAC.

### Service-level authorization (user profile access)
`UserServiceImpl` implements additional rules for `GET /api/users/{userId}` and `GET /api/users/{userId}/groups`:
- `ADMIN`: can view any user.
- `STUDENT`: can view self only.
- `LECTURER`: can view only users whose system role is `STUDENT` and who are in any group taught by that lecturer.

Implementation notes:
- “Target user is STUDENT” is checked via Identity gRPC `getUserRole`.
- “Lecturer supervises student” is checked in local DB via `GroupRepository.existsByLecturerAndStudent`.
- If role lookup fails with an unexpected runtime exception, code falls back to **allow** with a warning log.

## Public endpoints (permitAll)
As configured in `SecurityConfig`:
- `/actuator/health`, `/actuator/info`
- `/swagger-ui/**`, `/swagger-ui.html`, `/v3/api-docs/**`

All other requests require authentication.

## Service-to-service security
- Identity gRPC client uses plaintext negotiation by default (config).
- The gRPC server exposed by this service does not implement authentication/authorization interceptors.
