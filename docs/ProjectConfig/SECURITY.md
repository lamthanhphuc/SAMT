# Security Design - Project Config Service

**Version:** 1.0  
**Last Updated:** February 23, 2026  
**Security Model:** JWT Validation + AES-256-GCM Encryption + Service-to-Service Auth

---

## Quick Reference

| Aspect | Specification |
|--------|--------------|
| **Authentication (Public API)** | JWT validation (HS256) |
| **Authentication (Internal API)** | Shared service key (header-based) |
| **Token Encryption** | AES-256-GCM |
| **IV Length** | 96 bits (12 bytes) |
| **Auth Tag Length** | 128 bits |
| **Session Model** | Stateless |
| **Authorization** | Role-based (ADMIN, LECTURER, STUDENT) |
| **CORS** | Handled by API Gateway |

---

## 1. Authentication Model

Project Config Service uses **dual authentication** depending on endpoint type.

### Public API (`/api/**`)

**Authentication:** JWT Bearer token (stateless)

**Flow:**
1. User authenticates via Identity Service (login)
2. Identity Service issues JWT token
3. API Gateway validates JWT and forwards request
4. Project Config Service validates JWT signature and expiration
5. Extracts `userId`, `email`, `role` from JWT claims
6. Sets `CurrentUser` in `SecurityContext`

**Required Header:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**JWT Claims Used:**

```json
{
  "sub": "1",
  "email": "lecturer@example.com",
  "roles": ["LECTURER"],
  "token_type": "ACCESS",
  "iat": 1706612400,
  "exp": 1706613300
}
```

**Implementation:** `JwtAuthenticationFilter.java`

### Internal API (`/internal/**`)

**Authentication:** Service-to-service shared key

**Flow:**
1. Sync Service includes service authentication headers
2. Project Config Service validates headers via `ServiceToServiceAuthFilter`
3. Checks `X-Service-Name` and `X-Service-Key` headers
4. Allows access if credentials match

**Required Headers:**
```
X-Service-Name: sync-service
X-Service-Key: <shared_secret>
```

**Implementation:** `ServiceToServiceAuthFilter.java`

```java
// SEC-INTERNAL-01: Validate service name
if (!"sync-service".equals(serviceName)) {
    return 401 UNAUTHORIZED;
}

// SEC-INTERNAL-02: Validate service key
if (!syncServiceKey.equals(serviceKey)) {
    return 401 UNAUTHORIZED;
}
```

**Configuration:**
```yaml
service-to-service:
  sync-service:
    key: ${SYNC_SERVICE_KEY:default_insecure_key}
```

### Public Endpoints

The following endpoints do NOT require authentication:

| Path | Purpose |
|------|---------|
| `GET /actuator/health` | Health check |

---

## 2. Authorization Strategy

### Role-Based Access Control

| Operation | ADMIN | LECTURER | STUDENT | Sync Service |
|-----------|-------|----------|---------|--------------|
| Create Config | ✅ | ✅ | ❌ | ❌ |
| Get Config (masked) | ✅ | ✅ (own only) | ✅ (own group) | ❌ |
| Update Config | ✅ | ✅ (own only) | ❌ | ❌ |
| Delete Config | ✅ | ✅ (own only) | ❌ | ❌ |
| Verify Config | ✅ | ✅ (own only) | ❌ | ❌ |
| Get Decrypted Tokens | ❌ | ❌ | ❌ | ✅ |

**Authorization Logic:**

**LECTURER (own groups only):**
```java
// Check if lecturer owns the group
GroupLeaderResponse response = groupServiceClient.checkGroupLeader(groupId, userId);
if (!response.isLeader()) {
    throw new ForbiddenException("LECTURER can only manage own groups");
}
```

**STUDENT (group member only):**
```java
// Check if student is member of the group
GroupMemberResponse response = groupServiceClient.checkGroupMember(groupId, userId);
if (!response.isMember()) {
    throw new ForbiddenException("STUDENT can only view own group config");
}
```

---

## 3. Encryption Strategy

### AES-256-GCM Encryption

**Purpose:** Protect sensitive API tokens at rest in database

**Implementation:** `TokenEncryptionService.java`

**Algorithm:** `AES/GCM/NoPadding`
- Key size: 256 bits (32 bytes)
- IV size: 96 bits (12 bytes)
- Auth tag size: 128 bits (16 bytes)

**Encrypted Fields:**
- `jira_api_token_encrypted` (TEXT column)
- `github_token_encrypted` (TEXT column)

**Storage Format:**
```
{iv_base64}:{ciphertext_with_tag_base64}
```

Example:
```
kR3mK9xJ8fL2nP5q:zY8vX7wU6tS5rQ4pO3nM2lK1jI0hG9fE8dC7bA6==
```

**Encryption Flow:**

```java
public String encrypt(String plaintext) {
    // 1. Generate random IV (12 bytes)
    byte[] iv = new byte[12];
    secureRandom.nextBytes(iv);
    
    // 2. Initialize cipher with GCM mode
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
    
    // 3. Encrypt plaintext (auth tag automatically appended)
    byte[] ciphertext = cipher.doFinal(plaintext.getBytes(UTF_8));
    
    // 4. Return base64: {iv}:{ciphertext_with_tag}
    return Base64.encode(iv) + ":" + Base64.encode(ciphertext);
}
```

**Decryption Flow:**

```java
public String decrypt(String encrypted) {
    // 1. Parse format: {iv}:{ciphertext}
    String[] parts = encrypted.split(":");
    byte[] iv = Base64.decode(parts[0]);
    byte[] ciphertext = Base64.decode(parts[1]);
    
    // 2. Initialize cipher for decryption
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    GCMParameterSpec spec = new GCMParameterSpec(128, iv);
    cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
    
    // 3. Decrypt and verify auth tag
    byte[] plaintext = cipher.doFinal(ciphertext);
    
    return new String(plaintext, UTF_8);
}
```

**Key Management:**

**Configuration:**
```yaml
encryption:
  secret-key: ${ENCRYPTION_SECRET_KEY}
```

**Format:** Hex string (64 characters = 32 bytes = 256 bits)

Example:
```
0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef
```

**⚠️ CRITICAL:** 
- Never commit encryption key to version control
- Store in environment variable or secrets manager (AWS Secrets Manager, HashiCorp Vault)
- Rotate key periodically (requires re-encryption of all tokens)

---

## 4. Validation Rules

### Configuration Creation

| Field | Validation |
|-------|-----------|
| `groupId` | Must exist in User-Group Service (via gRPC) |
| `jiraUrl` | Valid URL format, HTTPS only |
| `jiraProjectKey` | 2-10 uppercase letters |
| `jiraEmail` | Valid email format |
| `jiraApiToken` | 1-500 characters, encrypted before storage |
| `githubRepoUrl` | Valid GitHub URL format |
| `githubToken` | 1-500 characters, encrypted before storage |

**Group Validation Flow:**
1. Extract `groupId` from request
2. Make gRPC call to User-Group Service: `VerifyGroupExists(groupId)`
3. If group does not exist or is soft-deleted, return `404 NOT FOUND`

### Token Masking

**Public API Response:**
- Tokens are MASKED in all responses
- Format: First 4 chars + `***` + last 4 chars

Example:
```json
{
  "jiraApiToken": "ATATT***xyz9",
  "githubToken": "ghp_***7890"
}
```

**Internal API Response:**
- Tokens are DECRYPTED and returned in full
- Only accessible via service-to-service authentication

---

## 5. Internal Communication

### Service-to-Service Authentication

**Endpoints:** `/internal/**`

**Allowed Services:**
- `sync-service` (for fetching decrypted tokens)

**Authentication Method:**
```java
// ServiceToServiceAuthFilter.java
String serviceName = request.getHeader("X-Service-Name");
String serviceKey = request.getHeader("X-Service-Key");

// Validate
if (!"sync-service".equals(serviceName) || !syncServiceKey.equals(serviceKey)) {
    return 401 UNAUTHORIZED;
}
```

**Security Concerns:**
- Shared key is static (no key rotation mechanism)
- No request signing or replay protection
- No audit log of which service accessed which config

**Mitigation:**
- Deploy in private network
- Use network policies to restrict access
- Future: Implement mutual TLS or JWT-based service tokens

### gRPC to User-Group Service

**Purpose:** Validate groups and check authorization

**Authentication:** None (trusted internal network)

**RPCs Used:**
- `VerifyGroupExists(groupId)` - Check if group exists
- `CheckGroupLeader(groupId, userId)` - Verify if user is group leader
- `CheckGroupMember(groupId, userId)` - Verify if user is group member

**Security Concerns:**
- No authentication on gRPC calls
- Assumes trusted network
- If network is compromised, attacker can bypass authorization checks

---

## 6. Public Endpoint Protection

### JWT Token Validation

**Filter:** `JwtAuthenticationFilter.java`

**Validation Steps:**
1. Extract `Authorization: Bearer <token>` header
2. Validate JWT signature using shared secret
3. Check expiration (`exp` claim)
4. Extract `sub` (userId), `email`, `roles` claims
5. Set `CurrentUser` in `SecurityContext`

**Failure Response (401 UNAUTHORIZED):**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired JWT token"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**Failure Response (403 FORBIDDEN):**
```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "You do not have permission to access this resource"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

---

## 7. CORS Policy

CORS is NOT configured in Project Config Service.

**Rationale:**
- API Gateway handles all CORS configuration
- Frontend only communicates with API Gateway (port 8080)
- Project Config Service is NOT directly accessible from browsers

---

## 8. Security Limitations

### Known Gaps

**1. Static Service-to-Service Key**
- Shared key is configured in `application.yml`
- No key rotation mechanism
- If key is compromised, must manually update all services

**2. No Encryption Key Rotation**
- Encryption key cannot be rotated without re-encrypting all tokens
- No migration strategy for key rotation
- If key is compromised, all historical tokens are exposed

**3. No Request Replay Protection**
- Service-to-service requests have no timestamp validation
- Attacker can replay captured requests
- Mitigation: Use HTTPS/TLS to prevent capture

**4. No Audit Logging**
- Token decryption events not logged
- Cannot track which service accessed which config
- No centralized audit trail

**5. No Rate Limiting**
- No per-user or per-service rate limiting
- Risk: Brute force attacks on internal API
- Mitigation: API Gateway implements rate limiting for public API

**6. Plaintext Tokens in Logs**
- Decrypted tokens may appear in application logs during debugging
- Risk: Token leakage via log aggregation systems
- Mitigation: Sanitize logs, restrict log access

**7. No Field-Level Encryption**
- Only `jiraApiToken` and `githubToken` are encrypted
- Other fields (`jiraUrl`, `jiraEmail`, `githubRepoUrl`) stored in plaintext
- If database is compromised, attacker can see configuration metadata

**8. Weak IV Uniqueness**
- IV generated using `SecureRandom` (good)
- No global IV tracking (risk of duplicate IV with same key)
- GCM mode requires IV to NEVER repeat with same key
- Mitigation: Use 256-bit encryption key (2^96 IVs before collision)

---

## 9. Best Practices

### For Developers

**1. Never Log Decrypted Tokens:**
```java
// ❌ BAD - logs plaintext token
log.info("Decrypted token: {}", decryptedToken);

// ✅ GOOD - logs only masked token
log.info("Token verified: {}***{}", 
    decryptedToken.substring(0, 4), 
    decryptedToken.substring(decryptedToken.length() - 4));
```

**2. Always Encrypt Before Saving:**
```java
// Create new config
ProjectConfig config = new ProjectConfig();
config.setJiraApiTokenEncrypted(encryptionService.encrypt(request.getJiraApiToken()));
config.setGithubTokenEncrypted(encryptionService.encrypt(request.getGithubToken()));
repository.save(config);
```

**3. Decrypt Only When Needed:**
```java
// ❌ BAD - decrypts for display
public ConfigResponse getConfig(UUID id) {
    ProjectConfig config = repository.findById(id);
    return new ConfigResponse(
        encryptionService.decrypt(config.getJiraApiTokenEncrypted()) // ❌ exposes plaintext
    );
}

// ✅ GOOD - masks for display
public ConfigResponse getConfig(UUID id) {
    ProjectConfig config = repository.findById(id);
    return new ConfigResponse(
        maskToken(config.getJiraApiTokenEncrypted()) // ✅ shows masked version
    );
}
```

**4. Validate Authorization Before Decryption:**
```java
// Always check authorization BEFORE decrypting tokens
if (!isAuthorizedToAccessConfig(userId, groupId)) {
    throw new ForbiddenException("Access denied");
}
// Only decrypt if authorized
DecryptedTokensResponse tokens = service.getDecryptedTokens(configId);
```

**5. Use Correlation ID for Audit:**
```java
// CorrelationIdFilter adds X-Correlation-Id header
log.info("Config accessed: configId={}, correlationId={}", 
    configId, request.getHeader("X-Correlation-Id"));
```

### For Operators

**1. Protect Encryption Key:**
```bash
# Use secrets manager (AWS Secrets Manager, HashiCorp Vault)
export ENCRYPTION_SECRET_KEY=$(aws secretsmanager get-secret-value \
    --secret-id projectconfig/encryption-key --query SecretString --output text)
```

**2. Rotate Service Keys Regularly:**
```bash
# Update sync-service key every 90 days
# 1. Generate new key
NEW_KEY=$(openssl rand -hex 32)

# 2. Update Project Config Service
kubectl set env deployment/project-config-service SYNC_SERVICE_KEY=$NEW_KEY

# 3. Update Sync Service
kubectl set env deployment/sync-service PROJECT_CONFIG_SERVICE_KEY=$NEW_KEY

# 4. Restart both services
kubectl rollout restart deployment/project-config-service
kubectl rollout restart deployment/sync-service
```

**3. Monitor Decryption Errors:**
```bash
# Set up alerts for encryption/decryption failures
# Indicates potential key mismatch or data corruption
kubectl logs -f deployment/project-config-service | grep "EncryptionException"
```

**4. Restrict Database Access:**
- Encrypt database backups
- Restrict access to `project_configs` table
- Use read-only replicas for reporting

**5. Enable TLS for Postgres:**
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/projectconfig_db?ssl=true&sslmode=require
```

---

## References

- **[API Contract](API_CONTRACT.md)** - Authorization rules per endpoint
- **[Database Design](DATABASE.md)** - Encrypted column specifications
- **[Implementation Guide](IMPLEMENTATION.md)** - Encryption key configuration
- **[Identity Service Security](../Identity_Service/SECURITY.md)** - JWT generation
- **[API Gateway Security](../api-gateway/SECURITY.md)** - Gateway-level security

---

**Questions?** Contact Backend Security Team
