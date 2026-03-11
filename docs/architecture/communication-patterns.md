# Communication Patterns

## External Ingress

- Clients call the platform through API Gateway.
- API Gateway validates the external JWT against Identity JWKS.
- After successful validation, API Gateway forwards requests downstream with a short-lived internal JWT.

## Internal Authentication

- Downstream services trust only the gateway-issued internal JWT.
- Shared claim rules are defined in [../contracts/jwt-claims.md](../contracts/jwt-claims.md).
- Legacy header-based trust patterns are deprecated and retained only in migration history.

## Synchronous Calls

| Caller | Callee | Protocol | Typical Use |
| --- | --- | --- | --- |
| API Gateway | All user-facing services | HTTP | External request routing |
| User Group | Identity | gRPC | User validation and profile lookup |
| Project Config | User Group | gRPC | Group and leader validation |
| Sync | Project Config | gRPC | Decrypted credential retrieval |
| Report | Sync | gRPC or internal APIs | Report generation input |

## Asynchronous Flow

- Kafka appears in the repository as an infrastructure and integration mechanism.
- Event-driven behavior exists for selected workflows, but the documented event model is not yet the dominant integration style.
- Treat Kafka support as targeted rather than platform-wide until service-specific docs say otherwise.

## Error and Resilience Principles

- Service-to-service calls should use deadlines and explicit error mapping.
- Authentication failures terminate at the gateway or the receiving service resource server.
- Business ownership conflicts should return explicit domain errors instead of silent fallbacks.

## Related Documents

- Service topology: [service-topology.md](service-topology.md)
- ADRs: [../adr](../adr)
- Security migration background: [../guides/security-migration.md](../guides/security-migration.md)