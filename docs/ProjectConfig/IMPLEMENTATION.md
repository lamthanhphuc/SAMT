# Implementation Guide – Project Config Service

**Framework:** Spring Boot 3.2+  
**Java Version:** 17+  
**Database:** PostgreSQL 15+  
**Date:** February 18, 2026

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- PostgreSQL 15+
- Docker (optional)

### Environment Variables

```bash
# Application
SERVER_PORT=8083
SPRING_PROFILES_ACTIVE=dev

# Database
DB_HOST=localhost
DB_PORT=5434
DB_NAME=projectconfig_db
DB_USERNAME=postgres
DB_PASSWORD=postgres123

# Security
JWT_SECRET=your-256-bit-secret-key-here-must-be-same-across-all-services
ENCRYPTION_KEY=0123456789abcdef0123456789abcdef  # 32-byte hex (256-bit AES)
INTERNAL_SERVICE_KEY=shared-secret-for-sync-service

# gRPC Clients
USER_GROUP_SERVICE_GRPC_HOST=localhost
USER_GROUP_SERVICE_GRPC_PORT=9095

# External API Timeouts
JIRA_API_TIMEOUT_SECONDS=6
GITHUB_API_TIMEOUT_SECONDS=6
```

### Run Locally

```bash
# Build
mvn clean install

# Run
mvn spring-boot:run

# Or with profile
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### Docker

```bash
# Build
docker build -t project-config-service:1.0 .

# Run
docker run -p 8083:8083 \
  -e DB_HOST=postgres \
  -e JWT_SECRET=your-secret \
  project-config-service:1.0
```

---

## Configuration

### application.yml

```yaml
spring:
  application:
    name: project-config-service
    
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5434}/${DB_NAME:projectconfig_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      connection-timeout: 30000  # 30 seconds
      idle-timeout: 600000       # 10 minutes
      max-lifetime: 1800000      # 30 minutes
      leak-detection-threshold: 60000  # 60 seconds
    
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

server:
  port: ${SERVER_PORT:8083}

# Security
jwt:
  secret: ${JWT_SECRET}
  
# Encryption
encryption:
  key: ${ENCRYPTION_KEY}
  
# Service-to-Service Auth
internal:
  service:
    key: ${INTERNAL_SERVICE_KEY}
    
# gRPC Clients
grpc:
  client:
    user-group-service:
      address: static://${USER_GROUP_SERVICE_GRPC_HOST:localhost}:${USER_GROUP_SERVICE_GRPC_PORT:9095}
      negotiation-type: plaintext
      deadline: 2000  # 2 seconds
      retry:
        max-attempts: 3
        initial-backoff: 1000  # 1 second
        max-backoff: 3000      # 3 seconds
        backoff-multiplier: 2.0
        retryable-status-codes:
          - UNAVAILABLE
          - DEADLINE_EXCEEDED

# External APIs
external:
  api:
    jira:
      timeout: ${JIRA_API_TIMEOUT_SECONDS:6}
    github:
      timeout: ${GITHUB_API_TIMEOUT_SECONDS:6}
```

---

## Database Setup

### Flyway Migration

**Location:** `src/main/resources/db/migration/V1__initial_schema.sql`

```sql
CREATE TABLE project_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    group_id BIGINT NOT NULL UNIQUE,
    jira_host_url VARCHAR(255) NOT NULL,
    jira_api_token_encrypted TEXT NOT NULL,
    github_repo_url VARCHAR(255) NOT NULL,
    github_token_encrypted TEXT NOT NULL,
    state VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    last_verified_at TIMESTAMP,
    invalid_reason TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    deleted_at TIMESTAMP,
    deleted_by BIGINT,
    version INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX idx_project_configs_group_id ON project_configs(group_id);
CREATE INDEX idx_project_configs_state ON project_configs(state);
CREATE INDEX idx_project_configs_deleted_at ON project_configs(deleted_at);
```

---

## Token Encryption

### AES-256-GCM Implementation

**Storage Format:** `{iv_base64}:{ciphertext_with_auth_tag_base64}`

**Key Points:**
- Unique IV per token (prevents pattern analysis)
- GCM mode provides authentication (tampering detection)
- **Auth tag embedded in ciphertext** (not stored separately)
- 256-bit key from environment variable

**Encryption:**
```java
// Pseudocode
String encrypt(String plaintext) {
    byte[] key = hex2bytes(ENCRYPTION_KEY);
    byte[] iv = generateRandomIV(12);  // 96-bit IV for GCM
    
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
    
    byte[] ciphertextWithTag = cipher.doFinal(plaintext.getBytes(UTF_8));
    // Note: cipher.doFinal() returns ciphertext WITH embedded auth tag
    
    return base64(iv) + ":" + base64(ciphertextWithTag);
}
```

**Decryption:**
```java
// Pseudocode
String decrypt(String encrypted) {
    String[] parts = encrypted.split(":");
    if (parts.length != 2) {
        throw new IllegalArgumentException("Invalid format: expected {iv}:{ciphertext}");
    }
    
    byte[] iv = base64decode(parts[0]);
    byte[] ciphertextWithTag = base64decode(parts[1]);
    
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
    
    // GCM automatically validates auth tag during decryption
    return new String(cipher.doFinal(ciphertextWithTag), UTF_8);
}
```

---

## Security Configuration

### JWT Filter

**SecurityFilterChain:**
```java
http
    .csrf(csrf -> csrf.disable())
    .authorizeHttpRequests(auth -> auth
        .requestMatchers("/actuator/health").permitAll()
        .requestMatchers("/internal/**").permitAll()  // Service auth filter
        .requestMatchers("/api/**").authenticated()
    )
    .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
    .addFilterBefore(serviceAuthFilter, JwtAuthenticationFilter.class);
```

### Service-to-Service Auth Filter

**Validates:**
- `X-Service-Name` header
- `X-Service-Key` header

**Applied to:** `/internal/**` endpoints only

---

## gRPC Client Configuration

### User-Group Service Client

**Proto Definition:** Copy from `user-group-service/src/main/proto/usergroup.proto`

**Client Bean:**
```java
@Component
public class UserGroupServiceGrpcClient {
    
    @GrpcClient("user-group-service")
    private UserGroupServiceGrpc.UserGroupServiceBlockingStub stub;
    
    public boolean verifyGroupExists(Long groupId) {
        VerifyGroupRequest request = VerifyGroupRequest.newBuilder()
            .setGroupId(groupId.toString())
            .build();
            
        try {
            VerifyGroupResponse response = stub
                .withDeadlineAfter(2, TimeUnit.SECONDS)
                .verifyGroupExists(request);
            return response.getExists() && !response.getDeleted();
        } catch (StatusRuntimeException e) {
            throw mapGrpcException(e);
        }
    }
    
    public boolean checkGroupLeader(Long groupId, Long userId) {
        // Similar implementation
    }
}
```

**Error Mapping:**
- `UNAVAILABLE` → 503 ServiceUnavailableException
- `DEADLINE_EXCEEDED` → 504 GatewayTimeoutException
- `NOT_FOUND` → 404 GroupNotFoundException

**Retry Policy:**
- Max attempts: 3
- Initial backoff: 1 second
- Backoff multiplier: 2x (1s → 2s → 4s)
- Max backoff: 3 seconds
- Retryable errors: UNAVAILABLE, DEADLINE_EXCEEDED

**Critical Note:** Only idempotent operations (VerifyGroupExists, CheckGroupLeader) are retried. See [GRPC_CONTRACT.md](GRPC_CONTRACT.md) for full retry implementation.

---

## External API Integration

### Jira Verification

**Endpoint:** `GET {jiraHostUrl}/rest/api/3/myself`  
**Headers:** `Authorization: Bearer {token}`  
**Timeout:** 6 seconds  
**Success:** 200 OK + user email in response

### GitHub Verification

**Endpoint:** `GET https://api.github.com/repos/{owner}/{repo}`  
**Headers:** `Authorization: Bearer {token}`  
**Timeout:** 6 seconds  
**Success:** 200 OK + `permissions.push = true`

**RestTemplate Config:**
```java
@Bean
public RestTemplate restTemplate() {
    return new RestTemplateBuilder()
        .setConnectTimeout(Duration.ofSeconds(5))
        .setReadTimeout(Duration.ofSeconds(6))
        .build();
}
```

---

## Soft Delete & Cleanup

### Soft Delete Strategy

**On Delete:**
- Set `deleted_at = NOW()`
- Set `deleted_by = currentUserId`
- Set `state = 'DELETED'`

**Retention:** 90 days (configurable)

### Scheduled Cleanup Job

```java
@Scheduled(cron = "0 0 2 * * *")  // Daily at 2 AM
public void cleanupExpiredConfigs() {
    LocalDateTime cutoff = LocalDateTime.now().minusDays(90);
    repository.deleteHardWhereDeletedAtBefore(cutoff);
}
```

---

## State Machine Implementation

**State Transitions:**

```java
public enum ConfigState {
    DRAFT,
    VERIFIED,
    INVALID,
    DELETED
}

// Transition logic in service layer
public void updateConfig(UUID id, UpdateRequest request) {
    ProjectConfig config = repository.findById(id);
    
    if (isCredentialUpdate(request)) {
        config.setState(ConfigState.DRAFT);
        config.setLastVerifiedAt(null);
        config.setInvalidReason("Configuration updated, verification required");
    }
    
    repository.save(config);
}
```

---

## Concurrency Control

### Optimistic Locking Strategy

**Problem:** Multiple users updating the same config simultaneously can cause lost updates.

**Solution:** JPA `@Version` annotation on `ProjectConfig` entity

**Implementation:**
```java
@Entity
@Table(name = "project_configs")
public class ProjectConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Version
    @Column(name = "version")
    private Integer version;  // Auto-incremented by JPA on save
    
    // Other fields...
}
```

**How It Works:**
1. User A reads config (id=123, version=5)
2. User B reads same config (id=123, version=5)
3. User A updates → JPA executes: `UPDATE project_configs SET ..., version=6 WHERE id=123 AND version=5` (Success)
4. User B updates → JPA executes: `UPDATE project_configs SET ..., version=6 WHERE id=123 AND version=5` (Fails - version already 6)
5. JPA throws `OptimisticLockException`

**Error Handling:**
```java
try {
    configRepository.save(config);
} catch (OptimisticLockException e) {
    throw new ConflictException("CONFLICT", 
        "Configuration was modified by another user. Please refresh and retry.");
}
```

**Critical Operations Protected:**
- Update config (PUT /api/project-configs/{id})
- Verify connection (POST /api/project-configs/{id}/verify)
- Soft delete (DELETE /api/project-configs/{id})
- Restore (POST /api/admin/project-configs/{id}/restore)

**Best Practice:** Clients should re-fetch latest config and retry on 409 Conflict response.

---

## Transaction Boundaries

**Transaction Scope:** Each API request = one database transaction

**Spring @Transactional Applied To:**
- All service layer methods in `ProjectConfigService`
- Scheduled jobs (cleanup, audits)

**Consistency Guarantees:**

| Operation | Transaction Scope | Rollback Triggers |
|-----------|------------------|-------------------|
| Create config | Single DB write | Validation error, duplicate key |
| Update config | Single DB write | OptimisticLockException, validation error |
| Verify connection | DB write + external API call | API timeout, invalid credentials |
| Delete config | Single DB write (soft delete) | Authorization failure |
| Restore config | Single DB write | OptimisticLockException |
| Cleanup job | Batch delete (chunks of 100) | DB connection error |

**gRPC Calls (User-Group Service):**
- ✅ **Before transaction:** Authorization checks (`hasAccess`) run before `@Transactional` boundary
- ❌ **Not rolled back:** gRPC call failures do NOT rollback DB transactions
- 🔒 **Idempotency:** All operations are retry-safe (optimistic locking prevents data corruption)

**External API Calls (Jira/GitHub):**
- ⚠️ **Inside transaction:** Verification runs within `@Transactional` boundary
- **Rollback behavior:** API timeout/error rolls back state transition to VERIFIED
- **Timeout:** 6 seconds (configurable)

**Example - Verify Connection:**
```java
@Transactional
public void verifyConnection(UUID id) {
    // 1. Read from DB (starts transaction)
    ProjectConfig config = repository.findById(id);
    
    // 2. Call external API (timeout = 6s)
    boolean valid = jiraClient.testConnection(config.getJiraBaseUrl(), config.getJiraApiToken());
    
    // 3. Update DB (commits if valid, rolls back if API threw exception)
    if (valid) {
        config.setState(VERIFIED);
        config.setLastVerifiedAt(now());
    } else {
        config.setState(INVALID);
        config.setInvalidReason("Invalid credentials");
    }
    repository.save(config);  // Commit happens here
}
```

**Best Practices:**
- Keep transactions short (< 5 seconds)
- Never call external APIs inside long-running transactions
- Use batch processing for bulk operations (chunk size: 100)

---

## Testing

### Unit Tests

**Key Test Cases:**
- Token encryption/decryption
- State machine transitions
- Authorization logic (LEADER check)
- Token masking

### Integration Tests

**Setup:**
- Testcontainers for PostgreSQL
- WireMock for Jira/GitHub APIs
- Mockito for gRPC clients

**Example:**
```java
@SpringBootTest
@Testcontainers
class ProjectConfigIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = 
        new PostgreSQLContainer<>("postgres:15");
    
    @Test
    void createConfig_success() {
        // Test implementation
    }
}
```

---

## Logging Policy

**Framework:** SLF4J + Logback (Spring Boot default)

**Log Levels:**

| Level | Use Case | Example |
|-------|----------|---------|
| ERROR | System failures, data corruption | `Database connection lost`, `Encryption key invalid` |
| WARN | Recoverable issues, degraded performance | `External API timeout (retry attempt 2/3)`, `Optimistic lock conflict` |
| INFO | Business events, API calls | `Config created for projectId=123`, `Connection verified successfully` |
| DEBUG | Detailed flow (dev/staging only) | `Decrypted token: ****last4`, `gRPC call latency: 120ms` |
| TRACE | Full request/response (not used) | - |

**Production Configuration:**
```yaml
logging:
  level:
    root: INFO
    com.safetyauditmanagement.projectconfig: INFO
    org.springframework.web: WARN
    org.hibernate: WARN
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

**Critical Events to Log:**

| Event | Level | Required Fields |
|-------|-------|-----------------|
| Config created | INFO | `projectId`, `userId`, `state` |
| Config updated | INFO | `configId`, `userId`, `version` (before/after) |
| Config deleted | WARN | `configId`, `userId`, `deletedAt` |
| Config restored | WARN | `configId`, `userId` |
| Verify success | INFO | `configId`, `externalSystem` (Jira/GitHub) |
| Verify failed | WARN | `configId`, `reason`, `externalSystem` |
| Optimistic lock conflict | WARN | `configId`, `userId`, `expectedVersion`, `actualVersion` |
| External API timeout | ERROR | `api` (Jira/GitHub), `url`, `timeout`, `attempt` |
| Encryption error | ERROR | `configId`, `operation` (encrypt/decrypt) |
| Unauthorized access | WARN | `userId`, `projectId`, `requiredRole` |

**Security - NEVER Log:**
- ❌ Full API tokens (Jira/GitHub)
- ❌ Encryption keys
- ❌ JWT tokens
- ❌ Database passwords

**Allowed - Log Masked Values:**
- ✅ Token last 4 characters: `****1a2b`
- ✅ User IDs, project IDs, config IDs
- ✅ Error messages (sanitized)

**Example - Safe Logging:**
```java
// ✅ GOOD
log.info("Config created for projectId={}, userId={}, state={}", 
    projectId, userId, state);
log.warn("Verification failed for configId={}, reason={}", 
    configId, sanitize(reason));

// ❌ BAD - Exposes secrets
log.info("Jira token: {}", config.getJiraApiToken());  // NEVER DO THIS
log.debug("Encryption key: {}", encryptionKey);        // NEVER DO THIS
```

**Troubleshooting Support:**
- All exceptions include correlation ID (if using distributed tracing)
- Stack traces logged for ERROR level only
- Use structured logging (JSON format recommended for production)

---

## Monitoring

### Health Checks

**Endpoint:** `/actuator/health`

**Checks:**
- Database connectivity
- gRPC client connectivity (User-Group Service)

### Metrics (Future)

**Proposed:**
- Config creation/update/delete counts
- Verification success/failure rates
- External API latencies
- Encryption/decryption performance

---

## Troubleshooting

### Common Issues

**Issue:** "Invalid encryption key"  
**Solution:** Ensure `ENCRYPTION_KEY` is 32-byte hex string (64 hex chars)

**Issue:** "User-Group Service unavailable"  
**Solution:** Verify `USER_GROUP_SERVICE_GRPC_PORT=9095` (not 9092)

**Issue:** "Jira verification timeout"  
**Solution:** Check firewall rules, increase `JIRA_API_TIMEOUT_SECONDS`

**Issue:** "Cannot decrypt existing tokens after key change"  
**Solution:** Key rotation not supported yet - requires data migration

---

## Deployment

### Docker Compose

```yaml
project-config-service:
  image: project-config-service:1.0
  ports:
    - "8083:8083"
  environment:
    - DB_HOST=postgres
    - JWT_SECRET=${JWT_SECRET}
    - ENCRYPTION_KEY=${ENCRYPTION_KEY}
    - USER_GROUP_SERVICE_GRPC_HOST=user-group-service
  depends_on:
    - postgres
    - user-group-service
```

### Production Checklist

- ✅ Set strong `ENCRYPTION_KEY` (32-byte random hex)
- ✅ Set unique `INTERNAL_SERVICE_KEY` per environment
- ✅ Use same `JWT_SECRET` across all services
- ✅ Enable HTTPS for external API calls
- ✅ Configure database connection pooling
- ✅ Set up monitoring/alerting
- ⏳ Implement rate limiting (future)
- ⏳ Add circuit breaker for external APIs (future)

---

**For API details, see [API_CONTRACT.md](API_CONTRACT.md).**
