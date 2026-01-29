# DATABASE DESIGN – PROJECT CONFIG SERVICE

## 1. Database Tables

### 1.1 project_configs

**Source:** SRS Section 6.3.4

| Column | Type | Constraint |
|--------|------|------------|
| config_id | UUID | PK |
| group_id | UUID | FK → groups.id, UNIQUE, NOT NULL |
| jira_host_url | VARCHAR(255) | NOT NULL |
| jira_api_token | TEXT | NOT NULL |
| github_repo_url | VARCHAR(255) | NOT NULL |
| github_token | TEXT | NOT NULL |
| deleted_at | TIMESTAMP | nullable |
| created_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |
| updated_at | TIMESTAMP | NOT NULL, DEFAULT CURRENT_TIMESTAMP |

**Constraints:**
- UNIQUE (group_id) - Mỗi group chỉ có 1 config
- Foreign Key: group_id references groups(id)

**Notes:**
- Soft delete: YES
- Tokens được mã hóa AES-256-GCM trước khi lưu
- jira_api_token và github_token lưu dạng encrypted text

**Indexes:**
```sql
CREATE INDEX idx_project_configs_group_id ON project_configs(group_id);
CREATE INDEX idx_project_configs_deleted_at ON project_configs(deleted_at);
CREATE UNIQUE INDEX uq_project_configs_group 
ON project_configs(group_id) 
WHERE deleted_at IS NULL;
```

---

## 2. Enums

**Note:** Service này không định nghĩa enum riêng. Status của config được kiểm tra qua việc verify connection dynamically.

---

## 3. Relationships

### 3.1 project_configs → groups (N-1)

- Mỗi config thuộc về 1 group
- Mỗi group có tối đa 1 config (UNIQUE constraint)
- Cascade behavior: 
  - ON DELETE: SET NULL hoặc CASCADE (tùy business rule - xem OPEN QUESTIONS)
  - ON UPDATE: CASCADE

### 3.2 Implicit Relationships

- project_configs liên quan đến users qua groups:
  - Để xác định ai là leader (có quyền tạo/sửa config)
  - Để xác định lecturer nào phụ trách (có quyền xem config)

---

## 4. Data Integrity Rules

### 4.1 Application-Level Constraints

Các constraint này được enforce ở SERVICE layer:

1. **URL Format Validation:**
   - jira_host_url phải bắt đầu với `https://` và kết thúc bằng `.atlassian.net`
   - github_repo_url phải match pattern: `https://github.com/[owner]/[repo]`

2. **Token Format Validation:**
   - Jira API token: Bắt đầu với `ATATT` (Atlassian Token)
   - GitHub token: Bắt đầu với `ghp_` (Personal Access Token) hoặc `github_pat_` (Fine-grained PAT)

3. **Group Ownership:**
   - Chỉ leader của group mới được tạo/sửa config
   - Phải verify qua User/Group Service

### 4.2 Soft Delete Rules

- Khi xóa config: Set deleted_at = CURRENT_TIMESTAMP
- Queries phải luôn filter `WHERE deleted_at IS NULL`
- UNIQUE constraint chỉ áp dụng với records chưa bị xóa

---

## 5. Encryption Strategy

### 5.1 Fields Cần Mã Hóa

- `jira_api_token`
- `github_token`

### 5.2 Encryption Algorithm

**Algorithm:** AES-256-GCM  
**Mode:** Galois/Counter Mode (provides authentication)  
**Key Size:** 256 bits

### 5.3 Implementation Details

```
Plaintext Token → AES-256-GCM Encrypt → Base64 Encode → Store in DB

Retrieve from DB → Base64 Decode → AES-256-GCM Decrypt → Plaintext Token
```

**Encryption Key Storage:**
- Stored in environment variable: `CONFIG_ENCRYPTION_KEY`
- Never hardcoded in source code
- Rotated every 90 days (policy)

### 5.4 IV (Initialization Vector)

- Generate random IV for each encryption
- Store IV with ciphertext (prepended or in separate column)
- Format: `[IV]:[Ciphertext]` or use separate column

**Recommended Schema Enhancement:**
```sql
ALTER TABLE project_configs 
ADD COLUMN jira_token_iv VARCHAR(32),
ADD COLUMN github_token_iv VARCHAR(32);
```

---

## 6. Migration Files (Flyway)

### V1__create_project_configs_table.sql

```sql
CREATE TABLE project_configs (
    config_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id UUID NOT NULL,
    jira_host_url VARCHAR(255) NOT NULL,
    jira_api_token TEXT NOT NULL,
    github_repo_url VARCHAR(255) NOT NULL,
    github_token TEXT NOT NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    CONSTRAINT fk_project_configs_group 
        FOREIGN KEY (group_id) 
        REFERENCES groups(id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
);

-- Indexes
CREATE INDEX idx_project_configs_group_id 
ON project_configs(group_id);

CREATE INDEX idx_project_configs_deleted_at 
ON project_configs(deleted_at);

-- Unique constraint: 1 config per active group
CREATE UNIQUE INDEX uq_project_configs_group 
ON project_configs(group_id) 
WHERE deleted_at IS NULL;

-- Trigger to update updated_at
CREATE OR REPLACE FUNCTION update_project_configs_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_update_project_configs_updated_at
BEFORE UPDATE ON project_configs
FOR EACH ROW
EXECUTE FUNCTION update_project_configs_updated_at();
```

---

## 7. Sample Queries

### 7.1 Get Active Config by Group

```sql
SELECT 
    config_id,
    group_id,
    jira_host_url,
    jira_api_token,
    github_repo_url,
    github_token,
    created_at,
    updated_at
FROM project_configs
WHERE group_id = :groupId 
  AND deleted_at IS NULL;
```

### 7.2 Check if Group Has Config

```sql
SELECT EXISTS (
    SELECT 1 
    FROM project_configs
    WHERE group_id = :groupId 
      AND deleted_at IS NULL
) AS has_config;
```

### 7.3 Get Configs by Lecturer

```sql
SELECT 
    pc.config_id,
    pc.group_id,
    g.group_name,
    pc.jira_host_url,
    pc.github_repo_url,
    pc.created_at
FROM project_configs pc
JOIN groups g ON pc.group_id = g.id
WHERE g.lecturer_id = :lecturerId
  AND pc.deleted_at IS NULL
  AND g.deleted_at IS NULL
ORDER BY g.group_name;
```

### 7.4 Soft Delete Config

```sql
UPDATE project_configs
SET deleted_at = CURRENT_TIMESTAMP
WHERE config_id = :configId
  AND deleted_at IS NULL;
```

---

## 8. Performance Considerations

### 8.1 Query Optimization

- Index trên `group_id` để tìm kiếm nhanh
- Index trên `deleted_at` để filter soft-deleted records
- Partial unique index để enforce business rule

### 8.2 Encryption/Decryption Performance

- Encrypt/decrypt chỉ khi cần thiết
- Cache decrypted tokens trong memory ngắn hạn (với proper security)
- Không log tokens ra file

### 8.3 Connection Pool

- Service này không kết nối trực tiếp đến Jira/GitHub
- Chỉ lưu trữ credentials
- Sync Service sẽ lấy credentials và thực hiện connection

---

## 9. Backup & Recovery

### 9.1 Backup Strategy

- Regular database backup (encrypted)
- Store encryption keys separately
- Test restore procedure regularly

### 9.2 Key Rotation

Khi rotate encryption key:
1. Decrypt all tokens with old key
2. Re-encrypt with new key
3. Update all records
4. Archive old key (for potential rollback)

**Migration Script Template:**
```sql
-- V2__rotate_encryption_key.sql
-- Manual execution required due to encryption key dependency
-- Run as part of key rotation procedure
```
