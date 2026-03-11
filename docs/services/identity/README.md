# Identity Service

## Responsibilities

- Authenticate users and issue access and refresh tokens.
- Manage user lifecycle operations including create, soft delete, restore, lock, unlock, and external account mapping.
- Publish public JWKS material for token verification.
- Expose internal gRPC user lookup for downstream services.
- Record security and administrative audit trails.

## APIs

- Authentication API: `/api/auth/register`, `/api/auth/login`, `/api/auth/refresh`, `/api/auth/logout`.
- Administration API: `/api/admin/users`, `/api/admin/users/{userId}`, `/api/admin/users/{userId}/restore`, `/api/admin/users/{userId}/lock`, `/api/admin/users/{userId}/unlock`, `/api/admin/users/{userId}/external-accounts`.
- Audit API: `/api/admin/audit/entity/{entityType}/{entityId}`, `/api/admin/audit/actor/{actorId}`, `/api/admin/audit/range`, `/api/admin/audit/security-events`.
- JWKS endpoint: `GET /.well-known/jwks.json`.
- gRPC endpoint: `UserGrpcServiceImpl` for internal user queries.

## Database

- `users`
- `refresh_tokens`
- `audit_logs`

## Events

- Publishes `user.deleted` Kafka events when user deletion must be propagated to downstream services.

## Dependencies

- PostgreSQL for identity persistence.
- Kafka for user deletion propagation.
- API Gateway as the external ingress layer.
