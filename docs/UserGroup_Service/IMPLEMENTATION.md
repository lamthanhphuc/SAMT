# User-Group Service – Implementation Notes (code-accurate)

This document lists only configuration and runtime behavior that exists in the current code.

## Runtime configuration

### REST
- `server.port`: `${SERVER_PORT:8082}`

### JWT
- `jwt.secret`: `${JWT_SECRET:...}`
- Algorithm: HS256

### gRPC server
- `grpc.server.port`: `${GRPC_SERVER_PORT:9095}`

### Identity Service gRPC client
```yaml
grpc:
  client:
    identity-service:
      address: static://${IDENTITY_SERVICE_GRPC_HOST:localhost}:${IDENTITY_SERVICE_GRPC_PORT:9091}
      negotiationType: plaintext
```

Deadline:
- `grpc.client.identity-service.deadline-seconds` (default `3`)

### Resilience4j
`ResilientIdentityServiceClient` wraps some identity calls with circuit breaker + retry.

### Database & Flyway
- PostgreSQL datasource in `application.yml`
- Flyway enabled
- `spring.flyway.locations` is configured as `classpath:db/migration/usergroup`

### Kafka (optional)
Enabled only when `spring.kafka.enabled=true`.

Consumer factory:
- `userDeletedEventKafkaListenerContainerFactory`

Note: There are two consumers on `user.deleted` with different container factory names; behavior depends on which one is effectively wired.

## Error handling
`GlobalExceptionHandler` returns structured `ErrorResponse` for business + validation + auth + optimistic locking errors.

## No Redis
No Redis dependency/config is present in this module.
