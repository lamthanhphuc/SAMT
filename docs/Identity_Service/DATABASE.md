# Database Design – Identity Service

## 1. Table: `users`

```sql
CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  full_name VARCHAR(100) NOT NULL,
  role VARCHAR(50) NOT NULL,
  status VARCHAR(20) NOT NULL, -- ACTIVE, LOCKED
  jira_account_id VARCHAR(100) NULL UNIQUE, -- External account mapping for Jira
  github_username VARCHAR(100) NULL UNIQUE, -- External account mapping for GitHub
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_jira_account ON users(jira_account_id);
CREATE INDEX idx_users_github_username ON users(github_username);
```

### Columns

| Column         | Type          | Constraints         | Description                     |
|----------------|---------------|---------------------|---------------------------------|
| `id`           | BIGSERIAL     | PRIMARY KEY         | Auto-increment user ID          |
| `email`        | VARCHAR(255)  | NOT NULL, UNIQUE    | User email (login username)     |
| `password_hash`| VARCHAR(255)  | NOT NULL            | BCrypt hashed password          |
| `full_name`    | VARCHAR(100)  | NOT NULL            | User full name                  |
| `role`         | VARCHAR(50)   | NOT NULL            | User role: `ADMIN`, `STUDENT`, `LECTURER` (no `ROLE_` prefix) |
| `status`       | VARCHAR(20)   | NOT NULL            | Account status (ACTIVE, LOCKED) |
| `jira_account_id` | VARCHAR(100) | NULL, UNIQUE     | Jira Account ID for assignee mapping. Set by: (1) Auto-sync process, (2) Admin via UC-MAP-EXTERNAL-ACCOUNTS |
| `github_username` | VARCHAR(100) | NULL, UNIQUE     | GitHub Username for commit author mapping. Set by: (1) Auto-sync process, (2) Admin via UC-MAP-EXTERNAL-ACCOUNTS |
| `created_at`   | TIMESTAMP     | NOT NULL, DEFAULT   | Account creation timestamp      |
| `updated_at`   | TIMESTAMP     | NOT NULL, DEFAULT   | Last update timestamp           |

---

## 2. Table: `refresh_tokens`

```sql
CREATE TABLE refresh_tokens (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL,
  token VARCHAR(255) NOT NULL UNIQUE,
  expires_at TIMESTAMP NOT NULL,
  revoked BOOLEAN NOT NULL DEFAULT FALSE,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_refresh_user FOREIGN KEY (user_id)
    REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_user_id ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_expires_at ON refresh_tokens(expires_at);
```

### Columns

| Column       | Type         | Constraints           | Description                        |
|--------------|--------------|----------------------|------------------------------------|
| `id`         | BIGSERIAL    | PRIMARY KEY          | Auto-increment token ID            |
| `user_id`    | BIGINT       | NOT NULL, FK         | Reference to users.id              |
| `token`      | VARCHAR(255) | NOT NULL, UNIQUE     | UUID refresh token string          |
| `expires_at` | TIMESTAMP    | NOT NULL             | Token expiration timestamp         |
| `revoked`    | BOOLEAN      | NOT NULL, DEFAULT FALSE | Token revocation status         |
| `created_at` | TIMESTAMP    | NOT NULL, DEFAULT    | Token creation timestamp           |

---

## 3. Constraints & Rules

### Business Rules

- ✅ **One user can have multiple refresh tokens** (multiple devices/sessions)
- ⚠️ **Revoked token must never be accepted**
- 🔒 **Cascade delete refresh tokens when user is deleted**

### Security Rules

1. **Token Uniqueness**: Each refresh token must be globally unique
2. **Expiration**: Tokens expire after 7 days
3. **Revocation**: Old tokens are revoked immediately upon refresh
4. **Reuse Detection**: Using a revoked token triggers security event

---

## 4. Entity Relationship Diagram

```
┌─────────────────┐
│     users       │
├─────────────────┤
│ id (PK)         │
│ email           │
│ password_hash   │
│ full_name       │
│ role            │
│ status          │
│ created_at      │
│ updated_at      │
└────────┬────────┘
         │ 1
         │
         │ has many
         │
         │ n
┌────────▼────────────┐
│  refresh_tokens     │
├─────────────────────┤
│ id (PK)             │
│ user_id (FK)        │
│ token (UNIQUE)      │
│ expires_at          │
│ revoked             │
│ created_at          │
└─────────────────────┘
```

---

## ✅ Status

**READY FOR JPA MAPPING**

---

## 5. Soft Delete – `users` Table Extension

### 5.1 Additional Columns

```sql
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMP NULL;
ALTER TABLE users ADD COLUMN deleted_by BIGINT NULL;

CREATE INDEX idx_users_deleted_at ON users(deleted_at);

-- Add foreign key constraint for deleted_by
ALTER TABLE users ADD CONSTRAINT fk_users_deleted_by 
    FOREIGN KEY (deleted_by) REFERENCES users(id) ON DELETE SET NULL;
```

### Column Definitions

| Column       | Type      | Constraints       | Description                          |
|--------------|-----------|-------------------|--------------------------------------|
| `deleted_at` | TIMESTAMP | NULL              | Soft delete timestamp (NULL = active)|
| `deleted_by` | BIGINT    | NULL, FK          | Admin user who performed deletion    |

### 5.2 Soft Delete Rules

| Rule | Description |
|------|-------------|
| **No Hard Delete** | `DELETE` statements are prohibited on `users` table |
| **Soft Delete** | Set `deleted_at = NOW()` and `deleted_by = admin_user_id` |
| **Restore** | Set `deleted_at = NULL` and `deleted_by = NULL` |
| **Query Filter** | All default queries must filter `WHERE deleted_at IS NULL` |
| **Admin Override** | Admin queries can include deleted records via native SQL |

### 5.3 Hibernate Annotation

```java
@SQLRestriction("deleted_at IS NULL")
```

> Hibernate 6.3+ automatically appends this condition to all queries.

---

## 6. Table: `audit_logs`

```sql
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    
    -- What was affected
    entity_type VARCHAR(50) NOT NULL,
    entity_id BIGINT NOT NULL,
    
    -- What happened
    action VARCHAR(50) NOT NULL,
    outcome VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    
    -- Who did it
    actor_id BIGINT NULL,
    actor_email VARCHAR(255) NULL,
    
    -- When it happened
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    -- Request context
    ip_address VARCHAR(45) NULL,
    user_agent VARCHAR(500) NULL,
    
    -- Before/After state
    old_value JSONB NULL,
    new_value JSONB NULL
);

-- Indexes for common queries
CREATE INDEX idx_audit_entity ON audit_logs(entity_type, entity_id);
CREATE INDEX idx_audit_actor ON audit_logs(actor_id);
CREATE INDEX idx_audit_action ON audit_logs(action);
CREATE INDEX idx_audit_timestamp ON audit_logs(timestamp);
CREATE INDEX idx_audit_outcome ON audit_logs(outcome);
```

### 6.1 Column Definitions

| Column        | Type          | Constraints         | Description                               |
|---------------|---------------|---------------------|-------------------------------------------|
| `id`          | BIGSERIAL     | PRIMARY KEY         | Auto-increment log ID                     |
| `entity_type` | VARCHAR(50)   | NOT NULL            | Entity name (e.g., "User", "RefreshToken")|
| `entity_id`   | BIGINT        | NOT NULL            | ID of affected entity                     |
| `action`      | VARCHAR(50)   | NOT NULL            | Action performed (see Action Types)       |
| `outcome`     | VARCHAR(20)   | NOT NULL, DEFAULT   | SUCCESS, FAILURE, DENIED                  |
| `actor_id`    | BIGINT        | NULL                | User ID who performed action (from JWT)   |
| `actor_email` | VARCHAR(255)  | NULL                | Denormalized email for query performance  |
| `timestamp`   | TIMESTAMP     | NOT NULL, DEFAULT   | When action occurred                      |
| `ip_address`  | VARCHAR(45)   | NULL                | Client IP (supports IPv6)                 |
| `user_agent`  | VARCHAR(500)  | NULL                | Client user agent string                  |
| `old_value`   | JSONB         | NULL                | State before change (for UPDATE/DELETE)   |
| `new_value`   | JSONB         | NULL                | State after change (for CREATE/UPDATE)    |

### 6.2 Action Types

| Action             | Description                              | Logged When                    |
|--------------------|------------------------------------------|--------------------------------|
| `CREATE`           | Entity created                           | User registration              |
| `UPDATE`           | Entity modified                          | Profile update, status change  |
| `SOFT_DELETE`      | Entity soft-deleted                      | Admin deletes user             |
| `RESTORE`          | Entity restored from soft-delete         | Admin restores user            |
| `LOGIN_SUCCESS`    | Successful authentication                | User logs in                   |
| `LOGIN_FAILED`     | Failed authentication (wrong password)   | Invalid credentials            |
| `LOGIN_DENIED`     | Authentication denied (locked account)   | Account locked                 |
| `LOGOUT`           | User logged out                          | User logs out                  |
| `REFRESH_SUCCESS`  | Token refresh successful                 | Token refreshed                |
| `REFRESH_REUSE`    | Security event: token reuse detected     | Revoked token reused           |
| `ACCOUNT_LOCKED`   | Account locked by admin                  | Admin locks user               |
| `ACCOUNT_UNLOCKED` | Account unlocked by admin                | Admin unlocks user             |

> **Implementation Note:** Action names in code (`AuditAction` enum) must match these values exactly. Use `SOFT_DELETE` (not `DELETE`), `ACCOUNT_LOCKED` (not `LOCK_ACCOUNT`).

### 6.3 Outcome Values

| Outcome   | Description                          |
|-----------|--------------------------------------|
| `SUCCESS` | Action completed successfully        |
| `FAILURE` | Action failed (e.g., wrong password) |
| `DENIED`  | Action denied (e.g., account locked) |

### 6.4 Immutability Rules

| Rule | Description | Enforcement |
|------|-------------|-------------|
| **Append-Only** | `audit_logs` is INSERT-only, no UPDATE/DELETE | Application: no setter methods on entity |
| **No FK Constraint** | No foreign key to `users` | Preserves audit trail for deleted users |
| **Actor Email Denormalized** | Email stored directly | Avoids JOIN, preserves email even if user deleted |
| **old_value Timing** | Capture BEFORE entity modification | Ensures accurate change history |

> **Critical:** `old_value` must be captured BEFORE the entity is modified. Capturing after modification will store the new state incorrectly.

---

## 7. Table: `oauth_providers`

```sql
CREATE TABLE oauth_providers (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id BIGINT NOT NULL,
  provider VARCHAR(20) NOT NULL,  -- 'GOOGLE' or 'GITHUB'
  provider_user_id VARCHAR(255) NOT NULL,  -- Google sub or GitHub user ID
  provider_email VARCHAR(255) NULL,
  provider_username VARCHAR(255) NULL,  -- GitHub username
  access_token TEXT NULL,  -- Encrypted with AES-256-GCM
  refresh_token TEXT NULL,  -- Encrypted (Google only)
  token_expires_at TIMESTAMP NULL,
  linked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at TIMESTAMP NULL,  -- Updated on each OAuth login
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  CONSTRAINT fk_oauth_user FOREIGN KEY (user_id) 
    REFERENCES users(id) ON DELETE CASCADE,
  
  UNIQUE(provider, provider_user_id),
  UNIQUE(provider, provider_email)
);

CREATE INDEX idx_oauth_user ON oauth_providers(user_id);
CREATE INDEX idx_oauth_provider ON oauth_providers(provider);
CREATE INDEX idx_oauth_provider_user ON oauth_providers(provider, provider_user_id);
```

### Columns

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| `id` | UUID | PRIMARY KEY | Unique OAuth provider link ID |
| `user_id` | BIGINT | NOT NULL, FK | Reference to users.id |
| `provider` | VARCHAR(20) | NOT NULL | OAuth provider: 'GOOGLE' or 'GITHUB' |
| `provider_user_id` | VARCHAR(255) | NOT NULL | Google `sub` claim or GitHub user ID |
| `provider_email` | VARCHAR(255) | NULL | Email from OAuth provider |
| `provider_username` | VARCHAR(255) | NULL | GitHub username (NULL for Google) |
| `access_token` | TEXT | NULL | Encrypted OAuth access token |
| `refresh_token` | TEXT | NULL | Encrypted OAuth refresh token (Google only) |
| `token_expires_at` | TIMESTAMP | NULL | Token expiration (Google: 1 hour, GitHub: never) |
| `linked_at` | TIMESTAMP | NOT NULL | When provider was linked to account |
| `last_used_at` | TIMESTAMP | NULL | Last OAuth login timestamp |
| `created_at` | TIMESTAMP | NOT NULL | Record creation timestamp |
| `updated_at` | TIMESTAMP | NOT NULL | Last update timestamp |

### Business Rules

- ✅ **One user can have multiple OAuth providers** (Google + GitHub)
- ⚠️ **One OAuth account can link to only one SAMT user** (UNIQUE constraints)
- 🔒 **Cascade delete when user is deleted** (ON DELETE CASCADE)
- 🔐 **OAuth tokens encrypted at rest** (AES-256-GCM, same as project config tokens)

### Security Rules

1. **Token Encryption**: All OAuth access/refresh tokens encrypted before storage
2. **Unique Provider Accounts**: One Google/GitHub account → one SAMT user
3. **CSRF Protection**: OAuth state token validated in callback
4. **Token Expiration**:
   - Google access tokens expire after 1 hour (auto-refreshed)
   - GitHub tokens never expire (valid until manually revoked)

---

## 8. Updated Entity Relationship Diagram

```
┌─────────────────────────────────┐
│            users                │
├─────────────────────────────────┤
│ id (PK)                         │
│ email                           │
│ password_hash                   │
│ full_name                       │
│ role                            │
│ status                          │
│ jira_account_id (UNIQUE)        │
│ github_username (UNIQUE)        │
│ created_at                      │
│ updated_at                      │
│ deleted_at                      │
│ deleted_by (FK→users.id)        │
└──────────┬──────────────────────┘
           │ 1
           │
           ├──────────────────────┐
           │ has many             │ has many
           │                      │
           │ n                    │ n
┌──────────▼──────────────┐  ┌───▼──────────────────┐
│    refresh_tokens       │  │   oauth_providers    │ ← NEW TABLE
├─────────────────────────┤  ├──────────────────────┤
│ id (PK)                 │  │ id (PK)              │
│ user_id (FK)            │  │ user_id (FK)         │
│ token (UNIQUE)          │  │ provider             │
│ expires_at              │  │ provider_user_id     │
│ revoked                 │  │ provider_email       │
│ created_at              │  │ provider_username    │
└─────────────────────────┘  │ access_token (enc)   │
                             │ refresh_token (enc)  │
                             │ token_expires_at     │
                             │ linked_at            │
                             │ last_used_at         │
                             └──────────────────────┘


┌─────────────────────────────────┐
│          audit_logs             │
├─────────────────────────────────┤
│ id (PK)                         │
│ entity_type                     │
│ entity_id                       │
│ action                          │
│ outcome                         │
│ actor_id                        │
│ actor_email                     │
│ timestamp                       │
│ ip_address                      │
│ user_agent                      │
│ old_value (JSONB)               │
│ new_value (JSONB)               │
└─────────────────────────────────┘
       ↑
       │ (No FK - intentional for audit integrity)
       │
       │ Logs actions on: users, refresh_tokens, oauth_providers
```

---

## ✅ Updated Status

**READY FOR JPA MAPPING** (including OAuth Providers + Soft Delete + Audit Log)


