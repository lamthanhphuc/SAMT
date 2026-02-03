# Project Config Service - Service Overview

**Service Name:** Project Config Service  
**Version:** 1.0  
**Port:** 8083  
**Database:** PostgreSQL (projectconfig_db)  
**Generated:** 2026-02-02  
**Status:** Ready for Implementation

---

## Service Responsibility

### What ProjectConfig Service DOES

Project Config Service manages integration configurations for student groups to connect with external project management systems (Jira Software and GitHub).

**Core Responsibilities:**
- ✅ Store encrypted API credentials (Jira API tokens, GitHub Personal Access Tokens)
- ✅ Configuration lifecycle management (CRUD operations)
- ✅ Connection verification (test credentials against Jira/GitHub APIs)
- ✅ State machine for config validity (DRAFT → VERIFIED → INVALID → DELETED)
- ✅ Soft delete with restoration capability
- ✅ Secure token provisioning for Sync Service (internal API)
- ✅ Role-based authorization (ADMIN, LECTURER, STUDENT-LEADER)

---

### What ProjectConfig Service DOES NOT DO

**Explicitly OUT OF SCOPE:**
- ❌ User authentication (delegated to Identity Service)
- ❌ JWT generation (only validates JWT from Identity Service)
- ❌ Group management (delegated to User-Group Service)
- ❌ Data synchronization from Jira/GitHub (delegated to Sync Service)
- ❌ User profile management (delegated to Identity Service)
- ❌ Storage of synchronized data (Jira issues, GitHub commits - delegated to Sync Service)
- ❌ Audit logging (not implemented - known tech debt)

---

## Service Boundaries

### Upstream Dependencies (Services This Service CALLS)

1. **Identity Service** (via gRPC)
   - **Why:** Validate user existence and roles
   - **When:** 
     - Soft delete config → validate `deletedBy` user exists
     - Restore config → validate actor is ADMIN
   - **Methods Used:**
     - `VerifyUserExists(userId)` → Check user exists and ACTIVE
     - `GetUserRole(userId)` → Verify ADMIN role for admin operations
   
2. **User-Group Service** (via gRPC)
   - **Why:** Validate group existence and membership
   - **When:**
     - Create config → validate group exists
     - All operations → validate group not soft-deleted
     - Authorization → check if user is LEADER of the group
   - **gRPC Methods Used:**
     - `VerifyGroupExists(groupId)` → Check group exists and not deleted
     - `CheckGroupLeader(groupId, userId)` → Verify user is LEADER
     - `CheckGroupMember(groupId, userId)` → Verify user is member

### Downstream Consumers (Services That CALL This Service)

1. **Sync Service** (via REST API - Internal)
   - **Why:** Fetch decrypted credentials to sync data from Jira/GitHub
   - **Endpoints Provided:**
     - `GET /internal/project-configs/{configId}/tokens` → Return decrypted tokens
   - **Security:** Service-to-service authentication with `X-Service-Name` + `X-Service-Key` headers

2. **Frontend** (via REST API - Public)
   - **Why:** CRUD operations for group leaders
   - **Endpoints Provided:**
     - `POST /api/project-configs` → Create config
     - `GET /api/project-configs/{id}` → Get masked config
     - `PUT /api/project-configs/{id}` → Update config
     - `DELETE /api/project-configs/{id}` → Soft delete
     - `POST /api/project-configs/{id}/verify` → Test connection

---

## Data Ownership

### Primary Entity

**`project_configs` table:**
- One-to-one relationship with Group (each group has EXACTLY 1 config or 0 configs)
- Group validation via REST call to User-Group Service (no FK constraint)
- Deleted configs retained for 90 days before hard delete (per soft delete policy)

### Security-Critical Fields

| Field | Type | Encryption | Masking |
|-------|------|-----------|---------|
| `jira_api_token_encrypted` | TEXT | AES-256-GCM | Yes (return `***...last4chars`) |
| `github_token_encrypted` | TEXT | AES-256-GCM | Yes (return `ghp_***...last4chars`) |
| `jira_host_url` | VARCHAR | None | No (public domain) |
| `github_repo_url` | VARCHAR | None | No (public GitHub URL) |

**Encryption Key Management:**
- Environment variable: `ENCRYPTION_KEY` (32-byte hex string, 256-bit AES)
- IV (Initialization Vector) generated per-token
- Stored format: `{iv}:{encrypted_data}:{auth_tag}` (GCM authenticated encryption)

---

## State Machine

```
┌─────────────────────────────────────────────────────┐
│                  Config Lifecycle                    │
└─────────────────────────────────────────────────────┘

 [CREATE]
    ↓
┌─────────┐   Verify Success    ┌──────────┐
│  DRAFT  │ ──────────────────→ │ VERIFIED │
└─────────┘                     └──────────┘
    ↑                                 │
    │                                 │ Update critical fields
    │                                 │ (tokens, URLs)
    │                                 ↓
    └─────────────────────────────────┘
    
    ↓ Verify Failed
┌─────────┐
│ INVALID │ (Connection test failed, token revoked)
└─────────┘

    ↓ Soft Delete
┌─────────┐
│ DELETED │ (Soft deleted, can be restored)
└─────────┘
```

**State Definitions:**

| State | Meaning | Sync Service Behavior |
|-------|---------|----------------------|
| `DRAFT` | New config OR updated config not yet verified | Cannot sync (verification required) |
| `VERIFIED` | Credentials tested successfully | Can sync |
| `INVALID` | Verification failed OR token revoked | Cannot sync (must update & re-verify) |
| `DELETED` | Soft deleted (deleted_at != NULL) | Cannot sync (must restore first) |

**State Transition Rules:**

| From | To | Trigger | Actor |
|------|-----|---------|-------|
| N/A | DRAFT | `POST /api/project-configs` | LEADER |
| DRAFT | VERIFIED | `POST /project-configs/{id}/verify` success | LEADER |
| DRAFT | INVALID | `POST /project-configs/{id}/verify` failed | LEADER |
| VERIFIED | DRAFT | `PUT /project-configs/{id}` (update tokens/URLs) | LEADER |
| VERIFIED | INVALID | Periodic token validation failed | SYSTEM |
| ANY | DELETED | `DELETE /project-configs/{id}` | LEADER or ADMIN |

---

## Multi-Tenancy & Isolation

**Tenant Boundary:** Group (group_id)

**Isolation Rules:**
1. **One config per group:** UNIQUE constraint on `project_configs.group_id`
2. **Leader-only access:** Only group LEADER can create/update/delete config (except ADMIN)
3. **No cross-group visibility:** Users cannot see configs of other groups
4. **ADMIN override:** ADMIN can view/update/delete ANY config

**Authorization Matrix:**

| Operation | ADMIN | LECTURER | STUDENT (LEADER) | STUDENT (MEMBER) |
|-----------|-------|----------|------------------|------------------|
| Create config | ✅ Any group | ❌ | ✅ Own group only | ❌ |
| Read config (masked) | ✅ Any group | ✅ Supervised groups | ✅ Own group only | ❌ |
| Update config | ✅ Any group | ❌ | ✅ Own group only | ❌ |
| Delete config | ✅ Any group | ❌ | ✅ Own group only | ❌ |
| Verify connection | ✅ Any group | ❌ | ✅ Own group only | ❌ |
| Restore deleted | ✅ Only | ❌ | ❌ | ❌ |

---

## Key Design Decisions

### Decision 1: Separate Database per Service

**Rationale:** Microservices best practice (Database-per-Service pattern)

**Implementation:**
- ProjectConfig Service has its own PostgreSQL database: `projectconfig_db`
- No direct foreign keys to `users` or `groups` tables
- Cross-service validation via gRPC/REST API calls

**Trade-off:**
- ✅ Service independence (can deploy/scale independently)
- ✅ Schema evolution freedom
- ❌ No ACID guarantees across services (eventual consistency)
- ❌ Orphaned records possible (if Group deleted but config not cleaned)

**Mitigation:**
- Periodic cleanup job to detect orphaned configs
- Soft delete strategy allows recovery from accidental deletions

---

### Decision 2: Encryption at Rest for Tokens

**Rationale:** Compliance with security requirements (SRS Section 4.2.5)

**Implementation:**
- AES-256-GCM authenticated encryption
- Unique IV per token (prevents pattern analysis)
- Auth tag validates integrity (tampering detection)

**Key Rotation:** NOT IMPLEMENTED (known tech debt)
- Current limitation: Changing `ENCRYPTION_KEY` environment variable will make existing tokens unreadable
- Future: Key versioning with graceful migration

---

### Decision 3: Synchronous Verification

**Rationale:** User experience (immediate feedback on config validity)

**Implementation:**
- `POST /project-configs/{id}/verify` makes real HTTP calls to Jira/GitHub APIs
- Timeout: 10 seconds per external API
- Transaction: Verification result saved in SEPARATE transaction (no rollback on failure)

**Trade-off:**
- ✅ Immediate feedback (user knows if credentials work)
- ❌ Slow endpoint (10s worst case if both APIs timeout)
- ❌ External dependency during config creation flow

**Alternative Considered (REJECTED):**
- Async verification with callback → Poor UX, frontend polling complexity

---

### Decision 4: Masking vs Full Encryption in Response

**Rationale:** Balance security and usability

**Implementation:**
- Public API (`GET /project-configs/{id}`): Return masked tokens
  - Jira: `***...last4` (e.g., `***ab12`)
  - GitHub: `ghp_***...last4` (e.g., `ghp_***xyz9`)
- Internal API (`GET /internal/project-configs/{id}/tokens`): Return decrypted tokens (for Sync Service)

**Security Justification:**
- Frontend never receives full tokens (prevent accidental logging/leakage)
- Sync Service uses service-to-service auth (X-Service-Name + X-Service-Key headers)

---

## Known Technical Debt

1. **No Audit Logging**
   - Current: No audit trail for config changes
   - Impact: Cannot track who changed credentials or when
   - Proposed: Add `config_audit_logs` table mirroring Identity Service's AuditLog

2. **No Key Rotation**
   - Current: Single encryption key for all tokens
   - Impact: Cannot rotate encryption keys without data migration
   - Proposed: Key versioning with `key_id` field

3. **No Token Expiration Tracking**
   - Current: No proactive alerting when Jira/GitHub tokens expire
   - Impact: Sync failures due to expired credentials
   - Proposed: Background job to test configs periodically

4. **No Circuit Breaker for External APIs**
   - Current: Each verification calls Jira/GitHub directly
   - Impact: Service degradation if Jira/GitHub APIs are down
   - Proposed: Resilience4j circuit breaker pattern

---

## Integration Points Summary

```
┌─────────────────────────────────────────────────────────┐
│              ProjectConfig Service                      │
│              (Port 8083)                                │
└──────┬──────────────────────────────────────────┬──────┘
       │                                           │
       │ gRPC                                      │ gRPC
       ↓                                           ↓
┌─────────────────┐                      ┌──────────────────┐
│ Identity Service│                      │ User-Group Service│
│ (Port 8081)     │                      │ (Port 9091 gRPC)  │
│ - VerifyUser    │                      │ - VerifyGroupExists│
│ - GetUserRole   │                      │ - CheckGroupLeader│
└─────────────────┘                      │ - CheckGroupMember│
                                         └──────────────────┘

       ↑                                           ↑
       │ REST (Internal API)                       │
       │                                           │
┌──────────────────┐                               │
│  Sync Service    │───────────────────────────────┘
│  - Get Tokens    │ (uses configs to sync)
└──────────────────┘

       ↑
       │ REST (Public API)
       │
┌──────────────────┐
│    Frontend      │
│  - CRUD Configs  │
└──────────────────┘
```

---

## Success Criteria for Implementation

**Must Have (P0):**
1. ✅ All CRUD operations work with role-based authorization
2. ✅ Tokens encrypted with AES-256-GCM
3. ✅ State machine transitions work correctly
4. ✅ Soft delete with 90-day retention
5. ✅ Connection verification tests Jira + GitHub APIs
6. ✅ Internal API for Sync Service with service auth

**Should Have (P1):**
1. ⏳ Masking tokens in public API responses
2. ⏳ Periodic cleanup of soft-deleted configs (90 days)
3. ⏳ Pessimistic locking for concurrent updates

**Nice to Have (P2):**
1. 📋 Audit logging
2. 📋 Key rotation support
3. 📋 Circuit breaker for external APIs
4. 📋 Proactive token expiration checks

---

## Assumptions

**CRITICAL - Implementer must validate these:**

1. **Group Leadership Validation:**
   - ASSUMPTION: User-Group Service gRPC method `CheckGroupLeader(groupId, userId)` returns boolean is_leader
   - IMPLEMENTATION: Must call this gRPC method to verify LEADER status before CREATE/UPDATE/DELETE

2. **Service-to-Service Authentication:**
   - ASSUMPTION: Sync Service will authenticate with headers: `X-Service-Name: sync-service`, `X-Service-Key: <shared-secret>`
   - IMPLEMENTATION: Shared secret stored in environment variable `INTERNAL_SERVICE_KEY`

3. **JWT Structure:**
   - ASSUMPTION: JWT contains claims: `sub` (userId), `roles` (array of "ADMIN", "LECTURER", "STUDENT")
   - IMPLEMENTATION: JWT filter extracts these claims into SecurityContext

4. **Jira API Version:**
   - ASSUMPTION: Jira Cloud REST API v3 (`https://{jira_host_url}/rest/api/3/myself`)
   - IMPLEMENTATION: Verification endpoint tests: GET /rest/api/3/myself with Bearer token

5. **GitHub API Version:**
   - ASSUMPTION: GitHub REST API v3 (`https://api.github.com/repos/{owner}/{repo}`)
   - IMPLEMENTATION: Verification endpoint tests: GET /repos/{owner}/{repo} with Bearer token

6. **Encryption Library:**
   - ASSUMPTION: Java Cryptography Extension (JCE) Unlimited Strength available
   - IMPLEMENTATION: Use `javax.crypto.Cipher` with "AES/GCM/NoPadding"

---

**Next Steps:** Proceed to API Contract, Database Design, Security Design, Implementation Guide
