# Documentation Refactor Audit

## SECTION 1 — DOCUMENT AUDIT

### Root documentation

| File | Classification | Reason |
| --- | --- | --- |
| `README.md` | REWRITE | Replace mixed overview with concise project index and links into the new docs tree. |
| `DEPLOYMENT.md` | KEEP | Retain as an operational entry point while the new guides become the primary navigation path. |

### Legacy top-level docs

| Original File | Classification | Reason |
| --- | --- | --- |
| `docs/00_SYSTEM_OVERVIEW.md` | MERGE INTO `docs/archive/architecture/service-topology-legacy.md` | Duplicated by later architecture material. |
| `docs/01_Identity_Service.md` | DELETE | Replaced by the service folder and newer service documentation. |
| `docs/02_User_Group_Service.md` | DELETE | Replaced by the service folder and newer service documentation. |
| `docs/03_System_Interaction.md` | MERGE INTO `docs/architecture/communication-patterns.md` | Useful concepts, but duplicated and partially outdated. |
| `docs/SYSTEM_ARCHITECTURE.md` | MERGE INTO `docs/archive/architecture/service-topology-legacy.md` | Was the most complete architecture doc but overlapped heavily with older material. |
| `docs/IMPLEMENTATION_GUIDE.md` | MERGE INTO `docs/guides/local-development.md` | Mixed implementation and onboarding guidance; split into cleaner guide documents. |
| `docs/SRS_REFACTORED.md` | DELETE | Mixed requirements and implementation claims; not suitable as current authoritative documentation. |
| `docs/SYSTEM_REFACTORING_GUIDE.md` | DELETE | Explicitly deprecated. |
| `docs/JWT_CLAIM_CONTRACT.md` | KEEP | Moved to `docs/contracts/jwt-claims.md`. |
| `docs/Soft_Delete_Retention_Policy.md` | KEEP | Moved to `docs/contracts/soft-delete-policy.md`. |
| `docs/ZERO_TRUST_MIGRATION.md` | KEEP | Moved to `docs/guides/security-migration.md` as migration history. |
| `docs/FRONTEND_INTEGRATION_ARCHITECTURE.md` | KEEP | Moved to `docs/guides/frontend-integration.md`. |
| `docs/API_MANUAL_test-cases.md` | KEEP | Moved to `docs/guides/testing.md`. |
| `docs/AUDIT_ISSUES.md` | KEEP | Moved to `docs/audits/issue-register.md`. |
| `docs/DOCUMENTATION_AUDIT_REPORT_V2.md` | KEEP | Archived as `docs/audits/documentation-audit-2026-02-22.md`. |
| `docs/AUDIT_REPORT_report-analysis-services_2026-03-10.md` | KEEP | Archived as `docs/audits/security-audit-2026-03-10.md`. |
| `docs/AUTONOMOUS_MAINTENANCE_REPORT_2026-03-11.md` | KEEP | Archived as `docs/audits/maintenance-log-2026-03-11.md`. |
| `docs/OAUTH_NOT_IMPLEMENTED.md` | KEEP | Archived as `docs/audits/deferred-features.md`. |
| `docs/adr-001-project-config-rest-grpc-hybrid.md` | KEEP | Moved to `docs/adr/adr-001-rest-grpc-hybrid.md`. |
| `docs/adr-002-api-gateway-routing-project-config.md` | KEEP | Moved to `docs/adr/adr-002-api-gateway-routing.md`. |
| `docs/adr-003-package-naming-standardization.md` | KEEP | Moved to `docs/adr/adr-003-package-naming.md`. |

### Service documentation

| Original File | Classification | Reason |
| --- | --- | --- |
| `docs/api-gateway/README.md` | KEEP | Moved to `docs/services/api-gateway/README.md`. |
| `docs/api-gateway/api-contract.md` | KEEP | Moved to `docs/services/api-gateway/api-contract.md`. |
| `docs/api-gateway/implementation.md` | KEEP | Moved to `docs/services/api-gateway/implementation.md`. |
| `docs/api-gateway/security.md` | KEEP | Moved to `docs/services/api-gateway/security.md`. |
| `docs/Identity_Service/README.md` | KEEP | Moved to `docs/services/identity/README.md`. |
| `docs/Identity_Service/api-contract.md` | KEEP | Moved to `docs/services/identity/api-contract.md`. |
| `docs/Identity_Service/database.md` | KEEP | Moved to `docs/services/identity/database.md`. |
| `docs/Identity_Service/grpc-contract.md` | KEEP | Moved to `docs/services/identity/grpc-contract.md`. |
| `docs/Identity_Service/implementation.md` | KEEP | Moved to `docs/services/identity/implementation.md`. |
| `docs/Identity_Service/security.md` | KEEP | Moved to `docs/services/identity/security.md`. |
| `docs/Identity_Service/test-cases.md` | KEEP | Moved to `docs/services/identity/test-cases.md`. |
| `docs/UserGroup_Service/README.md` | KEEP | Moved to `docs/services/user-group/README.md`. |
| `docs/UserGroup_Service/api-contract.md` | KEEP | Moved to `docs/services/user-group/api-contract.md`. |
| `docs/UserGroup_Service/database.md` | KEEP | Moved to `docs/services/user-group/database.md`. |
| `docs/UserGroup_Service/grpc-contract.md` | KEEP | Moved to `docs/services/user-group/grpc-contract.md`. |
| `docs/UserGroup_Service/implementation.md` | KEEP | Moved to `docs/services/user-group/implementation.md`. |
| `docs/UserGroup_Service/security.md` | KEEP | Moved to `docs/services/user-group/security.md`. |
| `docs/UserGroup_Service/test-cases.md` | KEEP | Moved to `docs/services/user-group/test-cases.md`. |
| `docs/ProjectConfig/README.md` | KEEP | Moved to `docs/services/project-config/README.md`. |
| `docs/ProjectConfig/api-contract.md` | KEEP | Moved to `docs/services/project-config/api-contract.md`. |
| `docs/ProjectConfig/database.md` | KEEP | Moved to `docs/services/project-config/database.md`. |
| `docs/ProjectConfig/grpc-contract.md` | KEEP | Moved to `docs/services/project-config/grpc-contract.md`. |
| `docs/ProjectConfig/implementation.md` | KEEP | Moved to `docs/services/project-config/implementation.md`. |
| `docs/ProjectConfig/security.md` | KEEP | Moved to `docs/services/project-config/security.md`. |
| `docs/sync-service/README.md` | KEEP | Moved to `docs/services/sync/README.md`. |
| `docs/sync-service/api-contract.md` | KEEP | Moved to `docs/services/sync/api-contract.md`. |
| `docs/sync-service/database.md` | KEEP | Moved to `docs/services/sync/database.md`. |
| `docs/sync-service/implementation.md` | KEEP | Moved to `docs/services/sync/implementation.md`. |

### Contradictions identified during audit

- Project Config was described as gRPC-only in historical requirement material, while the accepted design and current documentation show a hybrid REST plus gRPC model.
- Older system interaction material still described direct downstream JWT validation instead of the current gateway-issued internal JWT model.
- The old flat architecture set duplicated service responsibilities across multiple files and used different terminology for the same trust boundary.

## SECTION 2 — NEW DOCUMENT STRUCTURE

```text
README.md
DEPLOYMENT.md
docs/
  README.md
  architecture/
    data-ownership.md
    communication-patterns.md
    service-topology.md
  archive/
    architecture/
      service-topology-legacy.md
  services/
    README.md
    api-gateway/
    identity/
    user-group/
    project-config/
    sync/
    analysis/
    report/
    notification/
  adr/
    adr-001-rest-grpc-hybrid.md
    adr-002-api-gateway-routing.md
    adr-003-package-naming.md
  guides/
    quick-start.md
    local-development.md
    implementation-status.md
    frontend-integration.md
    security-migration.md
    testing.md
  api/
    README.md
    openapi-reference.md
  contracts/
    jwt-claims.md
    soft-delete-policy.md
  audits/
    README.md
    issue-register.md
    documentation-refactor-2026-03-11.md
    documentation-audit-2026-02-22.md
    security-audit-2026-03-10.md
    maintenance-log-2026-03-11.md
    deferred-features.md
  decisions/
    README.md
```

## SECTION 3 — MERGE PLAN

| Source | Target | Rationale |
| --- | --- | --- |
| `docs/00_SYSTEM_OVERVIEW.md` + `docs/SYSTEM_ARCHITECTURE.md` | `docs/archive/architecture/service-topology-legacy.md` | One authoritative architecture overview instead of duplicated narratives. |
| `docs/03_System_Interaction.md` | `docs/architecture/communication-patterns.md` | Keep only the durable interaction model. |
| `docs/IMPLEMENTATION_GUIDE.md` + practical parts of root onboarding | `docs/guides/local-development.md` and `docs/guides/quick-start.md` | Separate fast start from deeper operational guidance. |
| Flat service folders under `docs/` | `docs/services/<service>/...` | Normalize service documentation layout. |
| `docs/JWT_CLAIM_CONTRACT.md` | `docs/contracts/jwt-claims.md` | Move shared token schema to contracts. |
| `docs/Soft_Delete_Retention_Policy.md` | `docs/contracts/soft-delete-policy.md` | Move shared data policy to contracts. |
| Audit and maintenance snapshots | `docs/audits/` | Isolate archival material from active architecture docs. |

## SECTION 4 — REFACTORED DOCUMENTATION

- The active documentation set now starts at `README.md` and `docs/README.md`.
- Architecture material is reduced to topology, ownership, and communication instead of multiple overlapping system descriptions.
- Detailed service references remain available under `docs/services`, while missing service areas now have concise placeholders instead of being undocumented.
- Shared rules such as JWT claims and soft-delete policy now live under `docs/contracts`.
- Historical audit content is preserved under `docs/audits` and no longer competes with active design documents.