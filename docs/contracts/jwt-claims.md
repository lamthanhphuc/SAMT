# JWT Claim Contract (Canonical)

This repository standardizes JWT claims across:
- Identity Service (issuer)
- API Gateway (external JWT validator + internal JWT issuer)
- Downstream services (internal JWT resource servers)

## Canonical schema

### External access token (Identity Service → Client → API Gateway)

```json
{
  "sub": "user-id",
  "email": "user@example.com",
  "roles": ["ADMIN"],
  "token_type": "ACCESS",
  "iss": "identity-service",
  "aud": ["api-gateway"],
  "jti": "uuid",
  "iat": 1700000000,
  "exp": 1700003600
}
```

## Rules

- `sub` is the unique user identifier (string). Services must derive identity strictly from `sub`.
- `roles` is a JSON array of role names (e.g. `ADMIN`, `LECTURER`, `STUDENT`).
  - When a Spring Security `GrantedAuthority` is needed, the gateway (and downstream services) treat role `ADMIN` as authority `ROLE_ADMIN`.
- `jti` is required and is the standard JWT ID claim.
- `iat`/`exp` are required standard timestamp claims.
- `iss` must be `identity-service`.
- `aud` must include `api-gateway`.
- `token_type` must be `ACCESS`.

## Forbidden / legacy

- No `userId` claim.
- No singular `role` claim.
- No silent fallbacks (e.g., "try `userId` else `sub`"). Missing required claims should fail authentication.

## Internal token (API Gateway → Downstream services)

Downstream services MUST NOT validate external JWTs directly. The API Gateway validates the external JWT, then mints a short-lived internal JWT and forwards it as:

- `Authorization: Bearer <internal-jwt>`

Internal JWT schema (minimum):

```json
{
  "sub": "user-id",
  "roles": ["ADMIN"],
  "iss": "samt-gateway",
  "service": "api-gateway",
  "jti": "uuid",
  "iat": 1700000000,
  "exp": 1700000015
}
```

Internal token rules:

- TTL MUST be very short (15–30s). This repo defaults to ~20s.
- `iss` MUST match the gateway issuer configured in downstream services.
- `service` MUST be `api-gateway` (or whatever you configured as the expected gateway identity).
- `jti`, `iat`, `exp` are required and validated with clock skew bounded to 30s max.

Legacy/forbidden in the new flow:

- No `X-User-Id` / `X-User-Role` forwarding for authorization.
- No `X-Internal-*` HMAC signature headers.
