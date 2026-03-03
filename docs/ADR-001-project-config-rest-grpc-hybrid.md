# ADR-001: Project Config Service REST+gRPC Hybrid Architecture

**Date:** February 9, 2026  
**Status:** ✅ Accepted  
**Deciders:** Tech Lead, Backend Team  

---

## Context

Project Config Service was initially documented in SRS_REFACTORED.md (line 35) as "gRPC-only, NO REST API". However:

1. ✅ **Implementation reality:** Service has fully functional REST API with controllers:
   - `ProjectConfigController.java` - Public CRUD endpoints
   - `InternalConfigController.java` - Internal endpoints for Sync Service
   
2. ✅ **Documentation exists:** ProjectConfig/02_API_Contract.md documents complete REST API (668 lines) with:
   - UC30-UC35 use cases
   - Request/response schemas
   - Authentication requirements
   - Error handling

3. ❌ **Architecture mismatch:** SRS stated gRPC-only while implementation has REST+gRPC

### Problem Statement

**Critical deviation identified:** Documentation conflicts exist across multiple files:
- SRS_REFACTORED.md: "gRPC-only"
- ProjectConfig/02_API_Contract.md: Full REST API documentation
- 00_SYSTEM_OVERVIEW.md: Initially stated gRPC emphasis but showed HTTP port
- Implementation: Has both REST and gRPC

This creates confusion for:
- New developers joining the project
- System integrators planning API calls
- Security audit (REST API bypasses Gateway currently)

---

## Decision

**We ACCEPT and FORMALIZE the REST+gRPC Hybrid Architecture for Project Config Service.**

### Rationale

**Option A (Selected):** Update documentation to reflect REST+gRPC hybrid
- ✅ REST API is fully implemented and tested
- ✅ API Contract documentation (668 lines) is comprehensive
- ✅ Use Cases (UC30-UC35) are well-defined
- ✅ Less implementation work (only doc updates + Gateway routing)
- ✅ Consistent with Identity and User-Group Services architecture
- ✅ Better developer experience (REST for clients, gRPC for inter-service)

**Option B (Rejected):** Remove REST API, enforce gRPC-only
- ❌ Requires significant code deletion
- ❌ Forces all clients to use gRPC (steeper learning curve)
- ❌ Loses documented REST API contract
- ❌ Breaks existing integrations
- ❌ More work for uncertain benefit

---

## Communication Patterns (Formalized)

### 1. Client → Project Config Service: **REST via API Gateway**

```
Client (Browser/Mobile)
    ↓ HTTPS/REST
API Gateway (port 8080)
   ↓ JWT validation, mint internal JWT (RS256)
    ↓ Route: /api/project-configs/** → http://project-config-service:8083
Project Config Service (REST Controller)
```

**Endpoints:**
- `POST /api/project-configs` - Create config (LEADER only)
- `GET /api/project-configs/{id}` - Get config (masked tokens)
- `PUT /api/project-configs/{id}` - Update config
- `DELETE /api/project-configs/{id}` - Soft delete
- `POST /api/project-configs/{id}/verify` - Test Jira/GitHub connection

**Authentication:** External JWT → Gateway validates → Gateway forwards a short-lived internal JWT (`Authorization: Bearer <internal-jwt>`)

---

### 2. Project Config → User-Group Service: **gRPC**

```
Project Config Service
    ↓ gRPC (port 9092)
User-Group Service gRPC Server
```

**RPCs:**
- `VerifyGroupExists(groupId)` - Check group exists and not deleted
- `CheckGroupLeader(groupId, userId)` - Verify user is group leader

**Authentication:** Metadata-based (gRPC interceptor)

---

### 3. Sync Service → Project Config: **gRPC** (Planned)

```
Sync Service
    ↓ gRPC (port 9093)
Project Config gRPC Server
```

**RPCs:**
- `GetDecryptedTokens(configId)` - Get decrypted Jira/GitHub tokens for sync
- Internal service-to-service authentication

---

## Consequences

### ✅ Positive

1. **Consistency:** All 3 services (Identity, User-Group, Project Config) now have same pattern:
   - REST API for client access (via Gateway)
   - gRPC for inter-service communication

2. **Better DX:** Developers can use familiar REST/JSON for client integration

3. **Security:** REST API will be routed through Gateway (after PHASE 2 implementation):
   - Centralized JWT validation
   - Rate limiting
   - Logging & monitoring
   - CORS handling

4. **Flexibility:** 
   - Clients choose REST (easier)
   - Internal services use gRPC (efficient)

5. **Documentation:** Single source of truth established

### ⚠️ Trade-offs

1. **Dual API maintenance:** Must keep REST and gRPC in sync (acceptable, they serve different purposes)

2. **Performance:** REST has slight overhead vs gRPC (negligible for client use cases)

3. **Gateway dependency:** REST clients must go through Gateway (this is by design)

### 🔧 Required Actions (PHASE 2)

1. ✅ Documentation updated (PHASE 1 - COMPLETE)
   - [x] SRS_REFACTORED.md line 35-50
   - [x] 00_SYSTEM_OVERVIEW.md
   - [x] IMPLEMENTATION_GUIDE.md line 132
   - [x] README_IMPLEMENTATION.md

2. ⏳ Code changes (PHASE 2 - PENDING)
   - [ ] Add Gateway route in `GatewayRoutesConfig.java`:
     ```java
     .route("project-config-service", r -> r
         .path("/api/project-configs/**")
         .filters(f -> f.stripPrefix(2))
         .uri("http://project-config-service:8083")
     )
     ```
   - [ ] Test JWT flow: Gateway → Project Config
   - [ ] Update Swagger aggregation at Gateway

3. ⏳ Testing (PHASE 2)
   - [ ] Integration test: JWT issued by Identity Service works in Project Config via Gateway
   - [ ] E2E test: Create config → Verify Jira connection → Get masked tokens

---

## Compliance

**Standards:**
- ✅ REST API follows OpenAPI 3.0 spec (documented in 02_API_Contract.md)
- ✅ gRPC uses Protobuf 3.0 (usergroup_service.proto, projectconfig_service.proto)
- ✅ Authentication: JWT (RS256 via JWKS at API Gateway) for REST, Metadata for gRPC
- ✅ Database: PostgreSQL with Flyway migrations

**Security:**
- ✅ Token encryption: AES-256-GCM for stored Jira/GitHub tokens
- ✅ Token masking: Sensitive data hidden in responses
- ✅ Authorization: Role-based (ADMIN, LECTURER, STUDENT/LEADER)

---

## References

- [Project Config API Contract](ProjectConfig/02_API_Contract.md)
- [Project Config Implementation Guide](ProjectConfig/05_Implementation_Guide.md)
- [System Refactoring Guide](SYSTEM_REFACTORING_GUIDE.md)
- [Architecture Compliance Report](../ARCHITECTURE_COMPLIANCE_REPORT.md)

---

## Alternatives Considered

### Alternative: gRPC-Web for Browser Clients
**Status:** Rejected for now, may revisit

**Pros:**
- Native gRPC performance
- Strong typing with protobuf

**Cons:**
- Requires proxy (envoy)
- Limited browser support
- Steeper learning curve
- More complex infrastructure

**Decision:** REST is sufficient for current needs. gRPC-Web can be added later if performance becomes critical.

---

## Sign-off

- [x] Tech Lead: Approved - Feb 9, 2026
- [x] Backend Team: Approved - Feb 9, 2026
- [ ] QA Team: Pending verification after PHASE 2
- [ ] DevOps: Pending Gateway deployment update

**Next Review:** After PHASE 2 implementation complete
