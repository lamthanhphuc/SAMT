# User-Group Service – Security (code-accurate)

This file documents **the security behavior implemented in `user-group-service`**, without re-explaining JWT theory.

## Authentication (implemented)

- Input: `Authorization: Bearer <internal-jwt>` issued by API Gateway.
- The service validates the token as an OAuth2 Resource Server (RS256 via JWKS).
- The authenticated user id is taken from JWT `sub`; roles are taken from JWT claim `roles`.

If authentication fails:
- Protected endpoints are rejected by Spring Security with `401` using `JwtAuthenticationEntryPoint`.

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

Note: In production, API docs should be disabled via `application-prod.yml`.

## Service-to-service security
- gRPC traffic is plaintext by default.
- Under profile `mtls`, gRPC server/client are configured for TLS/mTLS (see `application-mtls.yml`).
