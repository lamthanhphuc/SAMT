# Service Topology

The repository entry point is [../../README.md](../../README.md). The documentation entry point is [../README.md](../README.md).

## Overview

SAMT is a microservice platform organized around a single HTTP ingress, service-owned databases, and a small set of internal service-to-service integrations.

## Topology Diagram

```text
Clients
  -> API Gateway
      -> Identity Service
      -> User Group Service
      -> Project Config Service
      -> Sync Service
      -> Analysis Service
      -> Report Service
      -> Notification Service

Identity Service --Kafka:user.deleted--> User Group Service
Report Service --HTTP--> Analysis Service
Report Service --gRPC--> Sync Service
Sync Service --logical config lookup--> Project Config Service
```

```mermaid
flowchart LR
    Client[Clients] --> Gateway[API Gateway]
    Gateway --> Identity[Identity Service]
    Gateway --> UserGroup[User Group Service]
    Gateway --> ProjectConfig[Project Config Service]
    Gateway --> Sync[Sync Service]
    Gateway --> Analysis[Analysis Service]
    Gateway --> Report[Report Service]
    Gateway --> Notification[Notification Service]
    Identity -. user.deleted .-> UserGroup
    Report -->|HTTP| Analysis
    Report -->|gRPC| Sync
    Sync -->|config lookup| ProjectConfig
```

## Service Roles

| Service | Role | Interfaces | Persistence |
| --- | --- | --- | --- |
| API Gateway | Public ingress, routing, external JWT validation, internal JWT issuance | HTTP, JWKS | None |
| Identity Service | Authentication, user lifecycle, audit, internal user lookup | REST, gRPC, Kafka producer | Identity database |
| User Group Service | Semesters, groups, memberships, selected user profile queries | REST, gRPC, Kafka consumer | Core database |
| Project Config Service | Group-scoped Jira and GitHub integration configuration | REST, gRPC, internal HTTP | Core database |
| Sync Service | Manual and scheduled external data synchronization | REST, gRPC, schedulers | Sync database |
| Analysis Service | Internal AI-backed requirement and SRS processing | Internal HTTP | Minimal local schema marker |
| Report Service | Report generation and export orchestration | REST, gRPC client, internal HTTP client | Report database |
| Notification Service | Reserved notification boundary with security and replay validation scaffolding | No business API implemented yet | Shared core datasource configured |

## Boundary Rules

- The API Gateway is the only intended external ingress.
- Each service owns its own schema and must not read another service database directly.
- Identity Service is the source of truth for users and roles.
- Cross-service relationships are logical references resolved through HTTP, gRPC, or events.
- Internal service calls rely on gateway-issued internal JWT validation.

## Related Documents

- [data-ownership.md](data-ownership.md)
- [communication-patterns.md](communication-patterns.md)
- [../services/README.md](../services/README.md)