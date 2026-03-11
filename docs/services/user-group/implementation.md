# User-Group Service – Implementation Notes (code-accurate)

This document lists only configuration and runtime behavior that exists in the current code.

## Runtime configuration

### REST
- `server.port`: `${SERVER_PORT:8082}`

### Gateway trust (internal JWT)
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri`: `${GATEWAY_INTERNAL_JWKS_URI:http://api-gateway:8080/.well-known/internal-jwks.json}`
- `security.internal-jwt.issuer`: `${GATEWAY_INTERNAL_JWT_ISSUER:samt-gateway}`
- `security.internal-jwt.expected-service`: `${GATEWAY_INTERNAL_JWT_EXPECTED_SERVICE:api-gateway}`
- `security.internal-jwt.clock-skew-seconds`: `${INTERNAL_JWT_CLOCK_SKEW_SECONDS:30}`

Note: External JWT validation is performed at the API Gateway (RS256 via JWKS). This service validates only the gateway-issued internal JWT.

### gRPC server
- `grpc.server.port`: `${GRPC_SERVER_PORT:9095}`

### Identity Service gRPC client
```yaml
grpc:
  client:
    identity-service:
      address: static://${IDENTITY_SERVICE_GRPC_HOST:localhost}:${IDENTITY_SERVICE_GRPC_PORT:9091}
      negotiationType: TLS
      security:
        trustCertCollection: ${GRPC_TRUST_CERT:file:/etc/certs/ca.crt}
        clientCertChain: ${GRPC_CERT_CHAIN:file:/etc/certs/tls.crt}
        clientPrivateKey: ${GRPC_PRIVATE_KEY:file:/etc/certs/tls.key}
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
