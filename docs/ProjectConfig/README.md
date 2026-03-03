# Project Config Service

**Version:** 1.0  
**Port:** 8083 (REST)  
**Database:** PostgreSQL (projectconfig_db)  
**Last Updated:** February 18, 2026

---

## Quick Links

- **[API Contract](API_CONTRACT.md)** - REST endpoints
- **[gRPC Contract](GRPC_CONTRACT.md)** - gRPC client integration
- **[Database Design](DATABASE.md)** - Schema & migrations
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions

---

## Overview

Project Config Service manages external integration credentials for student groups to connect with Jira and GitHub.

### Core Responsibilities

✅ **Configuration Management:**
- Store encrypted API credentials (Jira tokens, GitHub tokens)
- CRUD operations with role-based authorization
- One config per group (1:1 mapping with groups)

✅ **Security:**
- AES-256-GCM encryption for sensitive tokens
- Token masking in API responses
- Service-to-service authentication for internal APIs

✅ **Verification:**
- Test credentials against Jira/GitHub APIs
- State machine: DRAFT ↔ VERIFIED ↔ INVALID (bidirectional)
- Connection validation before syncing

✅ **Integration:**
- Provide decrypted credentials to Sync Service
- Validate group existence via User-Group Service

### What It Does NOT Do

❌ User authentication/JWT generation → Identity Service  
❌ Group management → User-Group Service  
❌ Data synchronization from Jira/GitHub → Sync Service  
❌ Audit logging → Not implemented (known tech debt)

---

## Architecture

### Service Communication

```
Client (REST/JSON)
      ↓
API Gateway (JWT Auth, port 8080)
      ↓
/api/project-configs/** → Project Config Service (REST, port 8083)
      ↓
gRPC Client → User-Group Service (gRPC, port 9095)
      ↑
Sync Service ← Internal REST API (decrypted tokens)
```

### Package Structure

```
project-config-service/
├── src/main/java/com/example/projectconfig/
│   ├── controller/           # REST endpoints (public + internal)
│   ├── service/              # Business logic, encryption, verification
│   ├── repository/           # JPA repositories
│   ├── entity/               # ProjectConfig entity
│   ├── dto/                  # Request/Response DTOs
│   ├── security/             # JWT filter, service auth
│   ├── client/grpc/          # User-Group Service gRPC client
│   ├── exception/            # Global error handling
│   └── config/               # Security, gRPC, encryption config
│
├── src/main/resources/       # Config, migrations
└── src/test/                 # Unit & integration tests
```

---

## Data Model

### Core Entity

**project_configs**
- Primary key: `id` (UUID)
- Unique key: `group_id` (BIGINT - references User-Group Service groups.id)
- Encrypted fields: `jira_api_token_encrypted`, `github_token_encrypted`
- State: `DRAFT`, `VERIFIED`, `INVALID`, `DELETED`
- Soft delete: `deleted_at`, `deleted_by`

**Key Fields:**

| Field | Type | Encryption | Masking |
|-------|------|-----------|---------|
| `jira_api_token_encrypted` | TEXT | AES-256-GCM | `***ab12` |
| `github_token_encrypted` | TEXT | AES-256-GCM | `ghp_***xyz9` |
| `jira_host_url` | VARCHAR | None | No |
| `github_repo_url` | VARCHAR | None | No |
| `state` | VARCHAR(20) | None | No |

---

## Authorization Model

### Role-Based Access Control

| Operation | ADMIN | STUDENT with LEADER role | STUDENT with MEMBER role |
|-----------|-------|---------------------------|-------------------------|
| Create config | ✅ Any group | ✅ Own group | ❌ |
| Read config (masked) | ✅ Any group | ✅ Own group | ❌ |
| Update config | ✅ Any group | ✅ Own group | ❌ |
| Delete config | ✅ Any group | ✅ Own group | ❌ |
| Verify connection | ✅ Any group | ✅ Own group | ❌ |
| Restore deleted | ✅ Only | ❌ | ❌ |

> **Note:** LEADER is a group membership attribute (stored in User-Group Service), not a system-wide role. System roles are: ADMIN, LECTURER, STUDENT (stored in Identity Service).

**Authorization Logic:**
- LEADER validation via gRPC call to User-Group Service
- Group existence check before operations
- ADMIN bypass for all operations

---

## State Machine

```
[CREATE]
   ↓
┌─────────┐   Verify Success    ┌──────────┐
│  DRAFT  │ ──────────────────→ │ VERIFIED │
└─────────┘                     └──────────┘
   ↑                                  │
   │                                  │ Update tokens/URLs
   │                                  ↓
   └──────────────────────────────────┘
   
   ↓ Verify Failed
┌─────────┐
│ INVALID │ (Cannot sync)
└─────────┘

   ↓ Soft Delete
┌─────────┐
│ DELETED │ (Can restore, 90-day retention)
└─────────┘
```

**State Transitions:**

| From | To | Trigger |
|------|-----|---------|
| N/A | DRAFT | Create config |
| DRAFT | VERIFIED | Verification success |
| DRAFT | INVALID | Verification failed || INVALID | VERIFIED | Verification success after fix || VERIFIED | DRAFT | Update credentials |
| ANY | DELETED | Soft delete |

---

## Security

### JWT Authentication

**Token Source:** Identity Service  
**Validation:** RS256 via JWKS at API Gateway (`JWT_JWKS_URI`)  
**Claims:** `sub` (userId), `roles` (array)

**No database lookup:** JWT validation is stateless.

### Token Encryption

**Algorithm:** AES-256-GCM  
**Key Storage:** Environment variable `ENCRYPTION_KEY`  
**Format:** `{iv_base64}:{ciphertext_with_auth_tag_base64}`

**Note:** GCM mode embeds authentication tag in ciphertext output (not stored separately).

**Masking Rules:**
- Jira: `***ab12` (last 4 chars, or `****` if token < 4 chars)
- GitHub: `ghp_***xyz9` (prefix + last 4 chars, or `ghp_****` if < 4 chars)

**Key Management:**

| Task | Procedure | Downtime Required |
|------|-----------|-------------------|
| **Initial Setup** | Generate 32-byte hex key: `openssl rand -hex 32` | No |
| **Key Rotation** | Deploy new key as `ENCRYPTION_KEY_NEW`, run migration script, swap keys, restart | Yes (~5 min) |
| **Key Compromise** | Immediately rotate key, re-encrypt all tokens, invalidate old tokens | Yes (emergency) |

**Key Rotation Process:**
1. Generate new key: `openssl rand -hex 32`
2. Set `ENCRYPTION_KEY_NEW=<new_key>` in environment (keep old key as `ENCRYPTION_KEY`)
3. Run migration script (re-encrypts all tokens with new key):
   ```bash
   java -jar project-config-service.jar --migrate-encryption-keys
   ```
4. Verify all tokens migrated successfully (check logs for failures)
5. Update `ENCRYPTION_KEY=<new_key>`, remove `ENCRYPTION_KEY_NEW`
6. Restart service (downtime: applying config change)

**Recommended Schedule:** Rotate encryption key every 12 months or on security incident.

**Storage Security:**
- ✅ Use secret management system (AWS Secrets Manager, Azure Key Vault, HashiCorp Vault)
- ❌ Never commit keys to Git
- ❌ Never log keys (even in debug mode)

### Service-to-Service Auth

**Internal API** (for Sync Service):
- Auth: Gateway-issued internal JWT forwarded as `Authorization: Bearer <internal-jwt>`
- Validation: RS256 via gateway JWKS (`GATEWAY_INTERNAL_JWKS_URI`)
- Transport: enable profile `mtls` to require mTLS
- Returns decrypted tokens

---

## gRPC Integration

Project Config Service **consumes** User-Group Service gRPC APIs.

**Connects to:** User-Group Service (port 9095)

**RPCs Used:**
- `VerifyGroupExists` - Check group exists before create/update
- `CheckGroupLeader` - Verify user is LEADER for authorization

**Error Handling:**
- `UNAVAILABLE` → 503 Service Unavailable
- `DEADLINE_EXCEEDED` → 504 Gateway Timeout
- `NOT_FOUND` → 404 Group Not Found

**📄 See:** [GRPC_CONTRACT.md](GRPC_CONTRACT.md) for complete details.

---

## API Overview

### Public REST Endpoints

**Configuration CRUD:**
- `POST /api/project-configs` - Create config (LEADER)
- `GET /api/project-configs/{id}` - Get config (masked tokens)
- `PUT /api/project-configs/{id}` - Update config (LEADER)
- `DELETE /api/project-configs/{id}` - Soft delete (LEADER/ADMIN)
- `POST /api/admin/project-configs/{id}/restore` - Restore deleted (ADMIN)

**Verification:**
- `POST /api/project-configs/{id}/verify` - Test Jira/GitHub connection

### Internal REST Endpoints

**For Sync Service:**
- `GET /internal/project-configs/{id}/tokens` - Get decrypted tokens

**Authentication:** Service-to-service headers

**📄 See:** [API_CONTRACT.md](API_CONTRACT.md) for full specifications.

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

**For detailed API specifications, see [API_CONTRACT.md](API_CONTRACT.md).**
