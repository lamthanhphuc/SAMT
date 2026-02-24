# ProjectConfig Service

**Version:** 1.0.0  
**Spring Boot:** 3.2+  
**Java:** 21  
**Port:** 8083

---

## 📖 Overview

ProjectConfig Service manages **Jira** and **GitHub** configurations for user groups in SAMT. Each configuration stores encrypted credentials for external integrations used by the Sync Service.

### Key Features

- ✅ **CRUD Operations** - Create, Read, Update, Delete configurations
- 🔐 **AES-256-GCM Encryption** - Secure token storage
- 🎭 **Token Masking** - Hide sensitive data in API responses
- ✅ **Credential Verification** - Test Jira/GitHub connectivity
- 🗑️ **Soft Delete** - 90-day retention before permanent deletion
- 🔄 **State Machine** - DRAFT → VERIFIED → INVALID → DELETED
- 🔒 **JWT Authentication** - Stateless authentication
- 🤝 **Service-to-Service Auth** - Internal API for Sync Service

---

## 🏗️ Architecture

### State Machine

```
DRAFT ──────► VERIFIED
  ▲               │
  │               │
  └──────────────┘
         │
         ▼
      INVALID
         │
         ▼
      DELETED (soft)
         │
         │ (90 days)
         ▼
    [HARD DELETED]
```

### Component Layers

```
Controllers (REST API)
    ↓
Services (Business Logic)
    ↓
gRPC Client → User-Group Service (validation)
    ↓
Repositories (JPA)
    ↓
PostgreSQL Database
```

---

## 🔌 gRPC Integration

ProjectConfig Service **consumes** gRPC APIs from User-Group Service for group validation and authorization.

**Connects to:** User-Group Service (port `9095`)

**Purpose:** Validate groups and authorize group leaders before config operations.

**RPCs Used:**
- `VerifyGroupExists` - Ensure group exists before creating/updating config
- `CheckGroupLeader` - Verify user is group LEADER for authorization

**Dependency:** Project-Config Service requires User-Group Service to be running for all config operations.

**📄 See:** **[docs/ProjectConfig/GRPC_CONTRACT.md](../docs/ProjectConfig/GRPC_CONTRACT.md)** for complete gRPC integration documentation, including error handling and retry strategies.

---

## 🚀 Quick Start

### Prerequisites

- Java 21+
- PostgreSQL 15+
- Maven 3.8+

### 1. Database Setup

```bash
# Create database
psql -U postgres
CREATE DATABASE projectconfig_db;
\q
```

### 2. Generate Encryption Key

```bash
# Run once to generate secure 256-bit key
cd project-config-service
mvn clean compile
mvn exec:java -Dexec.mainClass="com.samt.projectconfig.service.TokenEncryptionService" -Dexec.args="generateKey"
```

Copy the output key to environment variables.

### 3. Configure Environment Variables

```bash
# Windows PowerShell
$env:JWT_SECRET="yourJwtSecretMinimum64CharactersLong..."
$env:ENCRYPTION_SECRET_KEY="a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6..."
$env:SERVICE_TO_SERVICE_SYNC_KEY="yourServiceToServiceKeyForSyncService"
$env:DATABASE_URL="jdbc:postgresql://localhost:5432/projectconfig_db"
$env:DATABASE_USERNAME="postgres"
$env:DATABASE_PASSWORD="yourPassword"

# Linux/Mac
export JWT_SECRET="..."
export ENCRYPTION_SECRET_KEY="..."
```

### 4. Run Application

```bash
# Development mode
mvn spring-boot:run

# Build JAR
mvn clean package
java -jar target/project-config-service-0.0.1-SNAPSHOT.jar
```

Service starts at: http://localhost:8083

---

## 📡 API Endpoints

### Public API (Requires JWT)

#### 1. Create Configuration
```http
POST /api/project-configs
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "groupId": 1,
  "jiraHostUrl": "https://your-domain.atlassian.net",
  "jiraToken": "ATATT3xFfGF0...",
  "githubRepoUrl": "https://github.com/owner/repo",
  "githubToken": "ghp_1234567890..."
}
```

**Response:**
```json
{
  "data": {
    "id": "123e4567-e89b-12d3-a456-426614174000",
    "groupId": 1,
    "jiraHostUrl": "https://your-domain.atlassian.net",
    "jiraToken": "ATA***f0ab",
    "githubRepoUrl": "https://github.com/owner/repo",
    "githubToken": "ghp_***xyz9",
    "state": "DRAFT",
    "createdAt": "2026-02-09T10:30:00Z"
  },
  "timestamp": "2026-02-09T10:30:00Z"
}
```

#### 2. Get Configuration
```http
GET /api/project-configs/{id}
Authorization: Bearer <JWT>
```

#### 3. Update Configuration
```http
PUT /api/project-configs/{id}
Authorization: Bearer <JWT>
Content-Type: application/json

{
  "jiraToken": "ATATT3xFfGF0...",  // Optional
  "githubToken": "ghp_new..."       // Optional
}
```

#### 4. Verify Credentials
```http
POST /api/project-configs/{id}/verify
Authorization: Bearer <JWT>
```

**Response:**
```json
{
  "data": {
    "configId": "123e4567-e89b-12d3-a456-426614174000",
    "jiraResult": {
      "status": "SUCCESS",
      "userEmail": "user@example.com",
      "testedAt": "2026-02-09T10:35:00Z"
    },
    "githubResult": {
      "status": "SUCCESS",
      "repoName": "owner/repo",
      "hasWriteAccess": true,
      "testedAt": "2026-02-09T10:35:00Z"
    }
  },
  "timestamp": "2026-02-09T10:35:00Z"
}
```

#### 5. Soft Delete
```http
DELETE /api/project-configs/{id}
Authorization: Bearer <JWT>
```

#### 6. Restore (ADMIN only)
```http
POST /api/admin/project-configs/{id}/restore
Authorization: Bearer <JWT>
```

### Internal API (Service-to-Service)

#### Get Decrypted Tokens
```http
GET /internal/project-configs/{id}/tokens
X-Service-Name: sync-service
X-Service-Key: <SERVICE_KEY>
```

**Response:**
```json
{
  "configId": "123e4567-e89b-12d3-a456-426614174000",
  "jiraToken": "ATATT3xFfGF0abc123...",
  "githubToken": "ghp_1234567890abcdef..."
}
```

---

## 🔐 Security

### JWT Authentication

All `/api/**` endpoints require JWT with:
- **Algorithm:** HS256
- **Claims:** `sub` (userId), `roles` (array)
- **Header:** `Authorization: Bearer <JWT>`

### Token Encryption

- **Algorithm:** AES-256-GCM (authenticated encryption)
- **IV:** 12 bytes (96 bits), unique per encryption
- **Format:** `{iv_base64}:{ciphertext_base64}`

### Token Masking

Public API responses mask tokens:
- Jira: `ATA***ab12` (first 3 + last 4 chars)
- GitHub: `ghp_***xyz9` (prefix + last 4 chars)

---

## 🗄️ Database Schema

### project_configs Table

| Column | Type | Description |
|--------|------|-------------|
| id | UUID | Primary key |
| group_id | BIGINT | Foreign reference to User-Group Service |
| jira_host_url | VARCHAR(255) | Jira instance URL |
| jira_token | TEXT | Encrypted Jira token |
| github_repo_url | VARCHAR(255) | GitHub repository URL |
| github_token | TEXT | Encrypted GitHub token |
| state | VARCHAR(20) | DRAFT, VERIFIED, INVALID, DELETED |
| last_verified_at | TIMESTAMP | Last verification timestamp |
| invalid_reason | TEXT | Error message if INVALID |
| deleted_at | TIMESTAMP | Soft delete timestamp |
| deleted_by | BIGINT | User who deleted |
| created_at | TIMESTAMP | Creation timestamp |
| created_by | BIGINT | Creator user ID |
| updated_at | TIMESTAMP | Last update timestamp |
| updated_by | BIGINT | Last updater user ID |
| version | INTEGER | Optimistic locking version |

### Indexes

- `idx_project_configs_group_id` - Fast lookup by group
- `idx_project_configs_group_id_unique` - UNIQUE constraint (WHERE deleted_at IS NULL)
- `idx_project_configs_state` - Filter by state
- `idx_project_configs_deleted_at` - Cleanup job performance

---

## 📋 Business Rules

### Create Operation (BR-CONFIG-01 to BR-CONFIG-05)

1. Group MUST exist in User-Group Service
2. User MUST be LEADER of the group
3. Group can have ONLY ONE configuration (including soft-deleted)
4. State initialized to DRAFT
5. Tokens encrypted before storage

### Update Operation (BR-UPDATE-01 to BR-UPDATE-04)

1. User MUST be LEADER
2. Only DRAFT/VERIFIED/INVALID states can be updated
3. State transitions to DRAFT if credentials changed
4. Config NOT FOUND includes soft-deleted configs

### Delete Operation (BR-DELETE-01 to BR-DELETE-05)

1. User MUST be LEADER
2. Soft delete only (sets deleted_at, deleted_by)
3. State transitions to DELETED
4. Hard delete after 90 days (automatic)
5. Idempotent (multiple deletes allowed)

### Verification Rules (BR-VERIFY-01 to BR-VERIFY-04)

1. Only LEADER or LECTURER can verify
2. Tests Jira API: GET /rest/api/3/myself
3. Tests GitHub API: GET /repos/{owner}/{repo}
4. Updates state: VERIFIED (success) or INVALID (failure)

### Restore Rules (BR-RESTORE-01 to BR-RESTORE-04)

1. ADMIN only
2. Restores soft-deleted configs
3. State transitions to DRAFT
4. Clears deleted_at and deleted_by

---

## ⚙️ Configuration (application.yml)

```yaml
server:
  port: 8083

spring:
  application:
    name: project-config-service
  
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/projectconfig_db}
    username: ${DATABASE_USERNAME:postgres}
    password: ${DATABASE_PASSWORD:password}
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    show-sql: false
  
  flyway:
    enabled: true
    baseline-on-migrate: true

jwt:
  secret: ${JWT_SECRET:defaultSecretChangeInProduction}

encryption:
  secret-key: ${ENCRYPTION_SECRET_KEY:a1b2c3d4e5f6g7h8...}

service-to-service:
  sync-service:
    key: ${SERVICE_TO_SERVICE_SYNC_KEY:defaultKeyChangeInProduction}

verification:
  jira:
    timeout-seconds: 10
  github:
    timeout-seconds: 10

cleanup:
  retention-days: 90
  cron: "0 0 2 * * *"  # Daily at 2 AM
```

---

## 🧪 Testing

### Manual Test with cURL

```bash
# 1. Create JWT (use Identity Service)
JWT="eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# 2. Create configuration
curl -X POST http://localhost:8083/api/project-configs \
  -H "Authorization: Bearer $JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "groupId": 1,
    "jiraHostUrl": "https://test.atlassian.net",
    "jiraToken": "ATATT3xFfGF0...",
    "githubRepoUrl": "https://github.com/test/repo",
    "githubToken": "ghp_1234567890..."
  }'

# 3. Verify credentials
CONFIG_ID="123e4567-e89b-12d3-a456-426614174000"
curl -X POST http://localhost:8083/api/project-configs/$CONFIG_ID/verify \
  -H "Authorization: Bearer $JWT"

# 4. Get configuration (tokens masked)
curl http://localhost:8083/api/project-configs/$CONFIG_ID \
  -H "Authorization: Bearer $JWT"
```

### Integration Test

Full test requires:
- Identity Service (JWT generation)
- User-Group Service (gRPC permission checks)
- Valid Jira/GitHub credentials

---

## 🐛 Known Issues

### ⚠️ CRITICAL: gRPC Client Not Implemented

Authorization checks (LEADER, MEMBER) currently **SKIPPED**. See [ISSUES.md](../docs/ProjectConfig/ISSUES.md#issue-001-grpc-client-to-user-group-service).

**Impact:** 
- Any user can create configs for any group
- Authorization rules not enforced

**TODO:** Implement `UserGroupServiceGrpcClient` before production.

### Other Issues
- No key rotation support (tech debt)
- No audit logging (tech debt)
- No circuit breaker for external APIs

---

## 📂 Project Structure

```
project-config-service/
├── src/
│   ├── main/
│   │   ├── java/com/samt/projectconfig/
│   │   │   ├── ProjectConfigServiceApplication.java
│   │   │   ├── entity/
│   │   │   │   └── ProjectConfig.java
│   │   │   ├── repository/
│   │   │   │   └── ProjectConfigRepository.java
│   │   │   ├── dto/
│   │   │   │   ├── CreateConfigRequest.java
│   │   │   │   ├── UpdateConfigRequest.java
│   │   │   │   ├── ConfigResponse.java
│   │   │   │   ├── DecryptedTokensResponse.java
│   │   │   │   ├── VerificationResponse.java
│   │   │   │   └── ErrorResponse.java
│   │   │   ├── exception/
│   │   │   │   ├── ConfigNotFoundException.java
│   │   │   │   ├── GroupNotFoundException.java
│   │   │   │   ├── ForbiddenException.java
│   │   │   │   ├── ConfigAlreadyExistsException.java
│   │   │   │   ├── EncryptionException.java
│   │   │   │   ├── VerificationException.java
│   │   │   │   ├── ServiceUnavailableException.java
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   ├── service/
│   │   │   │   ├── TokenEncryptionService.java
│   │   │   │   ├── TokenMaskingService.java
│   │   │   │   ├── JiraVerificationService.java
│   │   │   │   ├── GitHubVerificationService.java
│   │   │   │   └── ProjectConfigService.java
│   │   │   ├── security/
│   │   │   │   ├── JwtAuthenticationFilter.java
│   │   │   │   ├── ServiceToServiceAuthFilter.java
│   │   │   │   └── SecurityConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── ProjectConfigController.java
│   │   │   │   └── InternalConfigController.java
│   │   │   ├── config/
│   │   │   │   ├── RestTemplateConfig.java
│   │   │   │   └── ConfigCleanupScheduler.java
│   │   │   └── grpc/
│   │   │       └── UserGroupServiceGrpcClient.java  [TODO]
│   │   ├── resources/
│   │   │   ├── application.yml
│   │   │   └── db/migration/
│   │   │       └── V1__initial_schema.sql
│   │   └── proto/
│   │       ├── identity.proto
│   │       └── usergroup.proto
│   └── test/
│       └── java/com/samt/projectconfig/
│           └── [Tests TODO]
├── Dockerfile
├── pom.xml
└── README.md
```

---

## 📞 Support

- **Documentation:** `docs/ProjectConfig/`
- **Issues:** `docs/ProjectConfig/ISSUES.md`
- **API Contract:** `docs/ProjectConfig/02_API_Contract.md`
- **Security Design:** `docs/ProjectConfig/SECURITY.md`

---

**License:** MIT  
**Author:** Backend Engineer  
**Last Updated:** 2026-02-09
