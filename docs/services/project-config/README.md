# Project Config Service

## Responsibilities

- Store group-scoped Jira and GitHub integration configuration.
- Verify external integration credentials before use.
- Expose trusted internal token lookup for downstream services.
- Support soft delete and restore of project configuration records.

## APIs

- Public API: `/api/project-configs`, `/api/project-configs/{id}`, `/api/project-configs/group/{groupId}`, `/api/project-configs/{id}/verify`, `/api/project-configs/admin/{id}/restore`.
- Internal HTTP API: `/internal/project-configs/{id}/tokens`.
- gRPC endpoint: `ProjectConfigInternalGrpcService`.

## Database

- `project_configs`

## Events

- None.

## Dependencies

- PostgreSQL for persistent project configuration records.
- Redis for runtime support concerns configured in service settings.
- Trusted internal callers such as Sync Service and Report Service.

### Internal REST Endpoints

**For Sync Service:**
- `GET /internal/project-configs/{id}/tokens` - Get decrypted tokens

**Authentication:** Service-to-service headers

**📄 See:** [api-contract.md](api-contract.md) for full specifications.

---

## Known Technical Debt

**P1 - Missing Features:**
- No audit logging for config changes
- No encryption key rotation support
- No proactive token expiration tracking

**P2 - Resilience:**
- No circuit breaker for Jira/GitHub API calls
- No retry policy for external verification

---

## Success Criteria

**Must Have:**
- ✅ CRUD operations with RBAC
- ✅ AES-256-GCM token encryption
- ✅ State machine transitions
- ✅ Soft delete (90-day retention)
- ✅ Connection verification
- ✅ Internal API with service auth

**Should Have:**
- ⏳ Token masking in responses
- ⏳ Periodic cleanup of deleted configs
- 📋 Audit logging

---

**For detailed API specifications, see [api-contract.md](api-contract.md).**
