# Project Config Service - Database Design

**Service:** Project Config Service  
**Database:** PostgreSQL (projectconfig_db)  
**Version:** 1.0  
**Generated:** 2026-02-02

---

## Database Schema

### Table: `project_configs`

**Purpose:** Store encrypted Jira and GitHub API credentials for student groups

```sql
CREATE TABLE project_configs (
    -- Primary Key
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    
    -- Group Reference (NO FK constraint - microservices pattern)
    group_id UUID NOT NULL UNIQUE,  -- One config per group
    
    -- Jira Configuration
    jira_host_url VARCHAR(255) NOT NULL,
    jira_api_token_encrypted TEXT NOT NULL,  -- AES-256-GCM encrypted
    
    -- GitHub Configuration
    github_repo_url VARCHAR(255) NOT NULL,
    github_token_encrypted TEXT NOT NULL,  -- AES-256-GCM encrypted
    
    -- State Machine
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT' 
        CHECK (state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED')),
    
    -- Verification Tracking
    last_verified_at TIMESTAMP NULL,
    invalid_reason TEXT NULL,
    
    -- Soft Delete Fields
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- User ID from Identity Service (no FK)
    
    -- Audit Timestamps
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT chk_state_deleted CHECK (
        (state = 'DELETED' AND deleted_at IS NOT NULL) OR
        (state != 'DELETED' AND deleted_at IS NULL)
    )
);
```

---

## Indexes

**Critical for Performance:**

```sql
-- Primary lookup by config ID
CREATE INDEX idx_configs_id ON project_configs(id);

-- Lookup by group (one-to-one relationship)
CREATE UNIQUE INDEX idx_configs_group_id ON project_configs(group_id) 
    WHERE deleted_at IS NULL;

-- Soft delete filtering
CREATE INDEX idx_configs_deleted_at ON project_configs(deleted_at);

-- State machine queries (find DRAFT/INVALID configs for admin dashboard)
CREATE INDEX idx_configs_state ON project_configs(state) 
    WHERE deleted_at IS NULL;

-- Cleanup job (find configs older than 90 days for hard delete)
CREATE INDEX idx_configs_deleted_retention 
    ON project_configs(deleted_at) 
    WHERE deleted_at IS NOT NULL;
```

---

## Field Specifications

### Primary Key

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `id` | UUID | PK, NOT NULL, DEFAULT gen_random_uuid() | Unique identifier for config |

**Rationale:** UUID instead of BIGINT auto-increment:
- No sequential ID leakage
- Distributed database-friendly
- Consistent with User-Group Service (groups.id is UUID)

---

### Group Reference

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `group_id` | UUID | NOT NULL, UNIQUE | Foreign key to User-Group Service (no DB constraint) |

**Cross-Service Validation:**
- **NO database foreign key** (microservices pattern)
- **Validation:** Call `GET /api/groups/{groupId}` to User-Group Service before INSERT/UPDATE
- **Error Handling:** 404 from User-Group Service → Return 404 CONFIG_NOT_FOUND

**Unique Constraint:**
- One config per group enforced by UNIQUE index
- Soft-deleted configs excluded from uniqueness (partial index: `WHERE deleted_at IS NULL`)

---

### Jira Configuration

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `jira_host_url` | VARCHAR(255) | NOT NULL | Jira instance URL (e.g., https://domain.atlassian.net) |
| `jira_api_token_encrypted` | TEXT | NOT NULL | Encrypted Jira API token (AES-256-GCM) |

**jira_host_url Format:**
- Valid URL starting with `https://`
- Must end with `.atlassian.net` OR custom domain (e.g., `jira.company.com`)
- No trailing slash
- Max 255 characters

**jira_api_token_encrypted Format:**
- Stored as: `{iv_base64}:{ciphertext_base64}:{auth_tag_base64}`
- Example: `rT5gH3kL9pQ2:Aw3Rt5Y7uI0pL1kJ2hN4mB5vC6xZ8:wE3rT5y7U9i0O1p2`
- Length: ~200-500 characters depending on token length

**Validation Before Encryption:**
- Raw token format: `^ATATT[A-Za-z0-9+/=_-]{100,500}$`
- Jira Cloud API tokens start with "ATATT" prefix

---

### GitHub Configuration

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `github_repo_url` | VARCHAR(255) | NOT NULL | GitHub repository URL |
| `github_token_encrypted` | TEXT | NOT NULL | Encrypted GitHub Personal Access Token (AES-256-GCM) |

**github_repo_url Format:**
- Valid GitHub URL: `https://github.com/{owner}/{repo}`
- Owner and repo must be valid GitHub identifiers (alphanumeric, hyphens, underscores)
- Example: `https://github.com/facebook/react`
- Max 255 characters

**github_token_encrypted Format:**
- Stored as: `{iv_base64}:{ciphertext_base64}:{auth_tag_base64}`
- Same format as Jira token encryption

**Validation Before Encryption:**
- Raw token format: `^ghp_[A-Za-z0-9]{36,}$`
- GitHub Personal Access Tokens start with "ghp_" prefix
- Length: 40-255 characters

---

### State Machine Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `state` | VARCHAR(20) | NOT NULL, CHECK constraint, DEFAULT 'DRAFT' | Current state of config |
| `last_verified_at` | TIMESTAMP | NULL | Last successful verification timestamp |
| `invalid_reason` | TEXT | NULL | Error message if state = INVALID |

**State Values:**

| State | Meaning | last_verified_at | invalid_reason |
|-------|---------|------------------|----------------|
| `DRAFT` | New config OR updated config not verified | NULL | NULL or "Configuration updated..." |
| `VERIFIED` | Connection tested successfully | NOT NULL | NULL |
| `INVALID` | Verification failed | NULL | NOT NULL (error message) |
| `DELETED` | Soft deleted | Any | Any |

**State Transition Logic:**

```sql
-- Transition to VERIFIED (verification success)
UPDATE project_configs SET
    state = 'VERIFIED',
    last_verified_at = NOW(),
    invalid_reason = NULL,
    updated_at = NOW()
WHERE id = ? AND state IN ('DRAFT', 'INVALID');

-- Transition to INVALID (verification failed)
UPDATE project_configs SET
    state = 'INVALID',
    last_verified_at = NULL,
    invalid_reason = ?,
    updated_at = NOW()
WHERE id = ? AND state IN ('DRAFT', 'VERIFIED');

-- Transition to DRAFT (update critical fields)
UPDATE project_configs SET
    state = 'DRAFT',
    last_verified_at = NULL,
    invalid_reason = 'Configuration updated, verification required',
    updated_at = NOW()
WHERE id = ? AND state IN ('VERIFIED', 'INVALID');

-- Transition to DELETED (soft delete)
UPDATE project_configs SET
    state = 'DELETED',
    deleted_at = NOW(),
    deleted_by = ?,
    updated_at = NOW()
WHERE id = ? AND deleted_at IS NULL;
```

---

### Soft Delete Fields

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `deleted_at` | TIMESTAMP | NULL | Soft delete timestamp (NULL = not deleted) |
| `deleted_by` | BIGINT | NULL | User ID who performed soft delete |

**Soft Delete Strategy:**

**Pattern:** Same as Identity Service and User-Group Service

**Hibernate Implementation:**
```java
@Entity
@Table(name = "project_configs")
@SQLRestriction("deleted_at IS NULL")  // Auto-filter in queries
public class ProjectConfig {
    // ...
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;  // User ID from Identity Service
}
```

**Retention Policy:**
- Soft-deleted configs retained for **90 days** (per Soft Delete Retention Policy)
- Cleanup job runs daily: `DELETE FROM project_configs WHERE deleted_at < NOW() - INTERVAL '90 days'`

**Cross-Service Reference:**
- `deleted_by` references `users.id` in Identity Service (NO FK constraint)
- Validation: Call `VerifyUserExists(deletedBy)` via gRPC before soft delete

**Restore Logic:**
```sql
-- Restore soft-deleted config (ADMIN only)
UPDATE project_configs SET
    deleted_at = NULL,
    deleted_by = NULL,
    state = 'DRAFT',  -- Requires re-verification
    updated_at = NOW()
WHERE id = ? AND deleted_at IS NOT NULL;
```

---

### Audit Timestamps

| Field | Type | Constraints | Description |
|-------|------|-------------|-------------|
| `created_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Config creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL, DEFAULT NOW() | Last modification timestamp |

**Trigger for updated_at:**

```sql
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_configs_updated_at
BEFORE UPDATE ON project_configs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
```

**Alternative (JPA @PreUpdate):**
```java
@PreUpdate
protected void onUpdate() {
    this.updatedAt = Instant.now();
}
```

---

## Data Encryption

### AES-256-GCM Encryption

**Algorithm:** AES-256-GCM (Galois/Counter Mode with Authentication)

**Key Source:** Environment variable `ENCRYPTION_KEY` (64-character hex string = 256 bits)

**Encryption Format:**
```
{iv_base64}:{ciphertext_base64}:{auth_tag_base64}
```

**Example:**
```
rT5gH3kL9pQ2wE3rT5y7U9i0O1p2:Aw3Rt5Y7uI0pL1kJ2hN4mB5vC6xZ8aS1dF2gH3jK4:wE3rT5y7U9i0O1p2
```

**Java Implementation:**

```java
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

public class TokenEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12; // 96 bits
    private static final int GCM_TAG_LENGTH = 128; // 128 bits
    
    private final SecretKey secretKey;
    
    public TokenEncryptionService(String keyHex) {
        byte[] keyBytes = hexToBytes(keyHex);
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
    }
    
    public String encrypt(String plaintext) throws Exception {
        // Generate random IV
        byte[] iv = new byte[GCM_IV_LENGTH];
        SecureRandom random = new SecureRandom();
        random.nextBytes(iv);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
        
        // Encrypt
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes("UTF-8"));
        
        // Format: {iv}:{ciphertext_with_tag}
        String ivBase64 = Base64.getEncoder().encodeToString(iv);
        String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
        
        return ivBase64 + ":" + ciphertextBase64;
    }
    
    public String decrypt(String encrypted) throws Exception {
        String[] parts = encrypted.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid encrypted format");
        }
        
        byte[] iv = Base64.getDecoder().decode(parts[0]);
        byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
        
        // Initialize cipher
        Cipher cipher = Cipher.getInstance(ALGORITHM);
        GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
        
        // Decrypt
        byte[] plaintext = cipher.doFinal(ciphertext);
        return new String(plaintext, "UTF-8");
    }
    
    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i+1), 16));
        }
        return data;
    }
}
```

**Usage:**
```java
// Encryption before saving
String encryptedToken = encryptionService.encrypt(rawJiraToken);
config.setJiraApiTokenEncrypted(encryptedToken);

// Decryption when needed
String decryptedToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
```

---

## Sample Data

### Valid Config (VERIFIED)

```sql
INSERT INTO project_configs (
    id, 
    group_id, 
    jira_host_url, 
    jira_api_token_encrypted,
    github_repo_url, 
    github_token_encrypted,
    state, 
    last_verified_at,
    created_at, 
    updated_at
) VALUES (
    '7c9e6679-7425-40de-944b-e07fc1f90ae7',
    '550e8400-e29b-41d4-a716-446655440000',
    'https://swp391-fpt.atlassian.net',
    'rT5gH3kL9pQ2:Aw3Rt5Y7uI0pL1kJ2hN4mB5vC6xZ8:wE3rT5y7U9i0O1p2',
    'https://github.com/fpt-university/swp391-project',
    'pL9kJ2hN:Zm5vC6xZ8aS1dF2gH3jK4lM5nB6vC7xZ9:uI0pL1kJ2hN4mB5v',
    'VERIFIED',
    '2026-02-02 15:00:00',
    '2026-02-02 10:30:00',
    '2026-02-02 15:00:00'
);
```

### Soft-Deleted Config

```sql
INSERT INTO project_configs (
    id, 
    group_id, 
    jira_host_url, 
    jira_api_token_encrypted,
    github_repo_url, 
    github_token_encrypted,
    state, 
    deleted_at,
    deleted_by,
    created_at, 
    updated_at
) VALUES (
    'f47ac10b-58cc-4372-a567-0e02b2c3d479',
    '6ba7b810-9dad-11d1-80b4-00c04fd430c8',
    'https://old-team.atlassian.net',
    'aB1cD2eF:Gh3IjK4lM5nO6pQ7rS8tU9vW0xY1zA2bC:3dE4fG5hI6jK7lM8',
    'https://github.com/old-team/deprecated-repo',
    'xY1zA2bC:3dE4fG5hI6jK7lM8nO9pQ0rS1tU2vW3xY:4zA5bC6dE7fG8hI9',
    'DELETED',
    '2026-01-15 14:00:00',
    123,  -- Admin user ID who deleted
    '2025-12-01 09:00:00',
    '2026-01-15 14:00:00'
);
```

---

## Database Queries

### Common Queries (JPA)

#### Find Config by Group ID (excluding soft-deleted)
```java
@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
    
    // @SQLRestriction auto-filters deleted_at IS NULL
    Optional<ProjectConfig> findByGroupId(UUID groupId);
}
```

**Generated SQL:**
```sql
SELECT * FROM project_configs 
WHERE group_id = ? AND deleted_at IS NULL
LIMIT 1;
```

---

#### Find All DRAFT Configs (Admin Dashboard)
```java
List<ProjectConfig> findByStateAndDeletedAtIsNull(String state);
```

**Usage:**
```java
List<ProjectConfig> draftConfigs = repository.findByStateAndDeletedAtIsNull("DRAFT");
```

**Generated SQL:**
```sql
SELECT * FROM project_configs 
WHERE state = 'DRAFT' AND deleted_at IS NULL;
```

---

#### Find Configs for Cleanup (90 days retention)
```sql
SELECT id, group_id, deleted_at 
FROM project_configs
WHERE deleted_at < NOW() - INTERVAL '90 days'
ORDER BY deleted_at ASC;
```

**Cleanup Job (Spring @Scheduled):**
```java
@Scheduled(cron = "0 0 2 * * *")  // Run at 2 AM daily
public void cleanupExpiredConfigs() {
    Instant cutoffDate = Instant.now().minus(90, ChronoUnit.DAYS);
    int deleted = repository.hardDeleteByDeletedAtBefore(cutoffDate);
    logger.info("Hard deleted {} expired configs", deleted);
}
```

---

#### Update State to VERIFIED
```java
@Transactional
public void markAsVerified(UUID configId, Instant verifiedAt) {
    ProjectConfig config = repository.findById(configId)
        .orElseThrow(() -> new ConfigNotFoundException());
    
    config.setState("VERIFIED");
    config.setLastVerifiedAt(verifiedAt);
    config.setInvalidReason(null);
    
    repository.save(config);
}
```

**Generated SQL:**
```sql
UPDATE project_configs SET
    state = 'VERIFIED',
    last_verified_at = ?,
    invalid_reason = NULL,
    updated_at = NOW()
WHERE id = ? AND deleted_at IS NULL;
```

---

## Performance Considerations

### Index Usage Analysis

**Query:** `SELECT * FROM project_configs WHERE group_id = ?`
- **Index Used:** `idx_configs_group_id` (UNIQUE index)
- **Scan Type:** Index Scan (O(log N))
- **Expected Rows:** 0 or 1 (UNIQUE constraint)

**Query:** `SELECT * FROM project_configs WHERE state = 'DRAFT'`
- **Index Used:** `idx_configs_state`
- **Scan Type:** Index Scan (O(log N + M) where M = matching rows)
- **Expected Rows:** ~10-100 (small subset)

**Query:** `SELECT * FROM project_configs WHERE deleted_at < ?`
- **Index Used:** `idx_configs_deleted_retention`
- **Scan Type:** Index Range Scan
- **Expected Rows:** ~0-50 (cleanup job)

---

### Expected Data Volume

**Assumptions:**
- University: 1000 students
- Average group size: 5 students
- Groups per semester: 200
- Total configs: 200 (one config per group)

**Storage Estimate:**
- Row size: ~1 KB (including encrypted tokens)
- Total storage: 200 KB
- With indexes: ~500 KB

**Scalability:** Database can handle 100,000+ configs without performance issues

---

## Database Migrations

### Initial Schema (Flyway V1__initial_schema.sql)

```sql
-- V1__initial_schema.sql
CREATE TABLE project_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL UNIQUE,
    jira_host_url VARCHAR(255) NOT NULL,
    jira_api_token_encrypted TEXT NOT NULL,
    github_repo_url VARCHAR(255) NOT NULL,
    github_token_encrypted TEXT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT' 
        CHECK (state IN ('DRAFT', 'VERIFIED', 'INVALID', 'DELETED')),
    last_verified_at TIMESTAMP NULL,
    invalid_reason TEXT NULL,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT chk_state_deleted CHECK (
        (state = 'DELETED' AND deleted_at IS NOT NULL) OR
        (state != 'DELETED' AND deleted_at IS NULL)
    )
);

CREATE INDEX idx_configs_group_id ON project_configs(group_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_configs_deleted_at ON project_configs(deleted_at);
CREATE INDEX idx_configs_state ON project_configs(state) WHERE deleted_at IS NULL;
CREATE INDEX idx_configs_deleted_retention ON project_configs(deleted_at) WHERE deleted_at IS NOT NULL;

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trigger_update_configs_updated_at
BEFORE UPDATE ON project_configs
FOR EACH ROW
EXECUTE FUNCTION update_updated_at_column();
```

---

**Next Document:** Security Design
