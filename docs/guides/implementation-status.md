# Implementation Status

## Implemented and Documented

- API Gateway validates external JWTs and issues short-lived internal JWTs.
- Identity owns authentication, user lifecycle, refresh tokens, and audit logs.
- User Group owns semesters, groups, and memberships.
- Project Config owns encrypted Jira and GitHub credentials.
- Sync exists as the integration and normalization layer for external project data.

## Implemented but Still Needing Documentation Consolidation

- Analysis, report, and notification services have repository modules but only concise documentation at this stage.
- Some historical audit and migration files are preserved as archive material rather than current architecture guidance.

## Accepted but Not Fully Closed Out

- Hybrid REST and gRPC behavior for Project Config is the accepted model. See [../adr/adr-001-rest-grpc-hybrid.md](../adr/adr-001-rest-grpc-hybrid.md).
- API Gateway routing expectations for Project Config remain tracked in [../adr/adr-002-api-gateway-routing.md](../adr/adr-002-api-gateway-routing.md).
- Package naming standardization remains tracked in [../adr/adr-003-package-naming.md](../adr/adr-003-package-naming.md).

## Deferred or Archived

- OAuth-related work is documented as deferred in [../audits/deferred-features.md](../audits/deferred-features.md).
- Historical audit reports remain available under [../audits](../audits) for traceability.