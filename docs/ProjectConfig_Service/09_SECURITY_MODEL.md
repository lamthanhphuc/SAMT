# SECURITY MODEL – PROJECT CONFIG SERVICE

## 1. Service-to-Service Authentication

### 1.1 Internal API Access Control

**Internal endpoints** chỉ được truy cập bởi các service nội bộ trong SAMT system. Các endpoint này **KHÔNG** được expose qua API Gateway công khai.

**Protected Endpoints:**
```
GET /internal/project-configs/{configId}/tokens
GET /internal/project-configs/group/{groupId}/tokens
POST /internal/project-configs/verify-service
```

---

### 1.2 Service Authentication Headers

**Tất cả** internal API requests phải bao gồm 2 headers bắt buộc:

```http
X-Service-Name: <service-identifier>
X-Service-Key: <service-secret-key>
```

**Header Specifications:**

| Header | Type | Required | Description |
|--------|------|----------|-------------|
| `X-Service-Name` | String | ✅ YES | Service identifier (enum: `SYNC_SERVICE`, `AI_SERVICE`, `REPORTING_SERVICE`) |
| `X-Service-Key` | String | ✅ YES | Service-specific secret key (SHA-256 hashed) |

**Example Request:**
```http
GET /internal/project-configs/7c9e6679-7425-40de-944b-e07fc1f90ae7/tokens HTTP/1.1
Host: project-config-service:8083
X-Service-Name: SYNC_SERVICE
X-Service-Key: 4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a2b7c4d8e9f3a1b2c7d4e8f9a3b
Content-Type: application/json
```

---

## 2. Service Key Validation Flow

### 2.1 Authentication Filter

```java
@Component
@Order(1)
public class InternalServiceAuthFilter extends OncePerRequestFilter {
    
    @Autowired
    private ServiceKeyValidator serviceKeyValidator;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        
        String requestPath = request.getRequestURI();
        
        // Only apply to internal endpoints
        if (!requestPath.startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract headers
        String serviceName = request.getHeader("X-Service-Name");
        String serviceKey = request.getHeader("X-Service-Key");
        
        // Validate headers presence
        if (serviceName == null || serviceKey == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"PC-401-01\",\"message\":\"Missing service authentication headers\"}");
            return;
        }
        
        // Validate service key
        try {
            ServiceAuthContext authContext = serviceKeyValidator.validate(serviceName, serviceKey);
            
            // Store service context for authorization checks
            ServiceContextHolder.setContext(authContext);
            
            filterChain.doFilter(request, response);
            
        } catch (InvalidServiceKeyException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"PC-401-02\",\"message\":\"Invalid service key\"}");
            
        } catch (UnknownServiceException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"PC-401-03\",\"message\":\"Unknown service name\"}");
            
        } finally {
            ServiceContextHolder.clear();
        }
    }
}
```

---

### 2.2 Service Key Validator

```java
@Service
public class ServiceKeyValidator {
    
    @Value("${internal.service.keys.sync-service}")
    private String syncServiceKey;
    
    @Value("${internal.service.keys.ai-service}")
    private String aiServiceKey;
    
    @Value("${internal.service.keys.reporting-service}")
    private String reportingServiceKey;
    
    private static final Map<String, InternalServicePermission> SERVICE_PERMISSIONS = Map.of(
        "SYNC_SERVICE", InternalServicePermission.READ_DECRYPTED_TOKENS,
        "AI_SERVICE", InternalServicePermission.READ_DECRYPTED_TOKENS,
        "REPORTING_SERVICE", InternalServicePermission.READ_ONLY
    );
    
    public ServiceAuthContext validate(String serviceName, String providedKey) 
            throws InvalidServiceKeyException, UnknownServiceException {
        
        // Get expected key for service
        String expectedKey = getExpectedKey(serviceName);
        
        if (expectedKey == null) {
            throw new UnknownServiceException("Service not registered: " + serviceName);
        }
        
        // Constant-time comparison to prevent timing attacks
        if (!MessageDigest.isEqual(providedKey.getBytes(), expectedKey.getBytes())) {
            throw new InvalidServiceKeyException("Invalid key for service: " + serviceName);
        }
        
        // Get service permissions
        InternalServicePermission permission = SERVICE_PERMISSIONS.get(serviceName);
        
        return new ServiceAuthContext(serviceName, permission);
    }
    
    private String getExpectedKey(String serviceName) {
        return switch (serviceName) {
            case "SYNC_SERVICE" -> syncServiceKey;
            case "AI_SERVICE" -> aiServiceKey;
            case "REPORTING_SERVICE" -> reportingServiceKey;
            default -> null;
        };
    }
}
```

---

## 3. Service Permission Model

### 3.1 Permission Levels

```java
public enum InternalServicePermission {
    
    /**
     * Full access to decrypted tokens.
     * Granted to: SYNC_SERVICE, AI_SERVICE
     */
    READ_DECRYPTED_TOKENS,
    
    /**
     * Read-only access to masked configs.
     * Granted to: REPORTING_SERVICE
     */
    READ_ONLY,
    
    /**
     * No access to internal endpoints.
     */
    NONE
}
```

---

### 3.2 Service-to-Permission Mapping

**⚠️ CRITICAL: Không có default permission. Mỗi service phải được explicit map.**

| Service Name | Permission | Rationale |
|--------------|------------|-----------|
| `SYNC_SERVICE` | `READ_DECRYPTED_TOKENS` | Cần tokens để đồng bộ dữ liệu từ Jira/GitHub API |
| `AI_SERVICE` | `READ_DECRYPTED_TOKENS` | Cần tokens để phân tích commit history, issues từ Jira/GitHub |
| `REPORTING_SERVICE` | `READ_ONLY` | Chỉ cần thông tin cấu hình (URLs), không cần tokens |

**Implementation:**

```java
@Configuration
public class ServicePermissionConfig {
    
    @Bean
    public ServicePermissionRegistry servicePermissionRegistry() {
        ServicePermissionRegistry registry = new ServicePermissionRegistry();
        
        // Explicit permission mapping - NO DEFAULTS
        registry.register("SYNC_SERVICE", InternalServicePermission.READ_DECRYPTED_TOKENS);
        registry.register("AI_SERVICE", InternalServicePermission.READ_DECRYPTED_TOKENS);
        registry.register("REPORTING_SERVICE", InternalServicePermission.READ_ONLY);
        
        return registry;
    }
}
```

---

### 3.3 Permission Enforcement

```java
@Service
public class ProjectConfigInternalService {
    
    @Autowired
    private ServicePermissionRegistry permissionRegistry;
    
    /**
     * Get decrypted tokens - requires READ_DECRYPTED_TOKENS permission
     */
    @RequiresServicePermission(InternalServicePermission.READ_DECRYPTED_TOKENS)
    public DecryptedTokensDTO getDecryptedTokens(UUID configId) {
        ServiceAuthContext context = ServiceContextHolder.getContext();
        
        // Permission already checked by annotation
        ProjectConfig config = projectConfigRepository.findById(configId)
            .orElseThrow(() -> new ConfigNotFoundException(configId));
        
        // Decrypt tokens
        String decryptedJiraToken = encryptionService.decrypt(config.getEncryptedJiraToken());
        String decryptedGithubToken = encryptionService.decrypt(config.getEncryptedGithubToken());
        
        // Audit log
        auditService.logInternalAccess(
            context.getServiceName(),
            "READ_DECRYPTED_TOKENS",
            configId
        );
        
        return DecryptedTokensDTO.builder()
            .configId(configId)
            .groupId(config.getGroupId())
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(decryptedJiraToken)
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(decryptedGithubToken)
            .build();
    }
    
    /**
     * Get masked config - requires READ_ONLY permission
     */
    @RequiresServicePermission(InternalServicePermission.READ_ONLY)
    public ProjectConfigDTO getConfig(UUID configId) {
        ServiceAuthContext context = ServiceContextHolder.getContext();
        
        ProjectConfig config = projectConfigRepository.findById(configId)
            .orElseThrow(() -> new ConfigNotFoundException(configId));
        
        // Audit log
        auditService.logInternalAccess(
            context.getServiceName(),
            "READ_CONFIG",
            configId
        );
        
        return projectConfigMapper.toDTO(config); // Returns masked tokens
    }
}
```

---

## 4. Service Key Management

### 4.1 Key Generation

**Service keys MUST be:**
- Minimum 64 characters (256-bit entropy)
- Randomly generated using cryptographically secure RNG
- Unique per service
- Different per environment (dev/staging/prod)

**Generation Script:**

```bash
# Generate service key using OpenSSL
openssl rand -hex 32

# Output example:
# 4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a2b7c4d8e9f3a1b2c7d4e8f9a3b
```

---

### 4.2 Key Storage

**Environment Variables (Recommended):**

```bash
# Project Config Service .env
INTERNAL_SERVICE_KEYS_SYNC_SERVICE=4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a2b7c4d8e9f3a1b2c7d4e8f9a3b
INTERNAL_SERVICE_KEYS_AI_SERVICE=7b2c4d8e9f3a1b2c7d4e8f9a3b4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a
INTERNAL_SERVICE_KEYS_REPORTING_SERVICE=2c7d4e8f9a3b4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a2b7c4d8e9f3a1b
```

**application.yml:**

```yaml
internal:
  service:
    keys:
      sync-service: ${INTERNAL_SERVICE_KEYS_SYNC_SERVICE}
      ai-service: ${INTERNAL_SERVICE_KEYS_AI_SERVICE}
      reporting-service: ${INTERNAL_SERVICE_KEYS_REPORTING_SERVICE}
```

**⚠️ NEVER commit service keys to version control.**

---

### 4.3 Key Rotation

**Rotation Schedule:** Every 90 days

**Rotation Process:**

1. Generate new key for service
2. Update environment variable in Project Config Service
3. Deploy Project Config Service with new key
4. Update calling service with new key
5. Deploy calling service
6. Verify connectivity
7. Old key automatically invalidated

**Zero-Downtime Rotation (Advanced):**

```yaml
internal:
  service:
    keys:
      sync-service:
        current: ${SYNC_SERVICE_KEY_CURRENT}
        previous: ${SYNC_SERVICE_KEY_PREVIOUS}  # Valid for 24h grace period
```

---

## 5. Internal API Endpoints

### 5.1 Get Decrypted Tokens by Config ID

**Endpoint:** `GET /internal/project-configs/{configId}/tokens`

**Required Permission:** `READ_DECRYPTED_TOKENS`

**Request Headers:**
```http
X-Service-Name: SYNC_SERVICE
X-Service-Key: 4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a2b7c4d8e9f3a1b2c7d4e8f9a3b
```

**Response (200 OK):**
```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Error Responses:**

| Status | Error Code | Message |
|--------|------------|---------|
| 401 | PC-401-01 | Missing service authentication headers |
| 401 | PC-401-02 | Invalid service key |
| 401 | PC-401-03 | Unknown service name |
| 403 | PC-403-02 | Service does not have READ_DECRYPTED_TOKENS permission |
| 404 | PC-404-01 | Config not found |

---

### 5.2 Get Decrypted Tokens by Group ID

**Endpoint:** `GET /internal/project-configs/group/{groupId}/tokens`

**Required Permission:** `READ_DECRYPTED_TOKENS`

**Request Headers:**
```http
X-Service-Name: AI_SERVICE
X-Service-Key: 7b2c4d8e9f3a1b2c7d4e8f9a3b4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a
```

**Response (200 OK):**
```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3xFfGF0T1234567890abcdefghijklmnop",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_abc123xyz456def789ghi012jkl345mno678"
}
```

**Error Responses:**

| Status | Error Code | Message |
|--------|------------|---------|
| 401 | PC-401-01 | Missing service authentication headers |
| 401 | PC-401-02 | Invalid service key |
| 403 | PC-403-02 | Service does not have READ_DECRYPTED_TOKENS permission |
| 404 | PC-404-02 | Config not found for group |

---

### 5.3 Get Masked Config (Read-Only)

**Endpoint:** `GET /internal/project-configs/{configId}`

**Required Permission:** `READ_ONLY`

**Request Headers:**
```http
X-Service-Name: REPORTING_SERVICE
X-Service-Key: 2c7d4e8f9a3b4a8f3c9d2e1b7f6a8c3d9e2f1a7b4c8d9e3f1a2b7c4d8e9f3a1b
```

**Response (200 OK):**
```json
{
  "configId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "jiraHostUrl": "https://testproject.atlassian.net",
  "jiraApiToken": "ATATT3x***...",
  "githubRepoUrl": "https://github.com/testorg/testrepo",
  "githubToken": "ghp_***...",
  "createdAt": "2026-01-29T10:00:00Z",
  "updatedAt": "2026-01-29T10:00:00Z"
}
```

---

## 6. Audit Logging for Internal Access

### 6.1 Audit Log Entry Structure

**Mỗi internal API call phải được audit log:**

```json
{
  "eventId": "uuid",
  "eventType": "INTERNAL_ACCESS",
  "serviceName": "SYNC_SERVICE",
  "action": "READ_DECRYPTED_TOKENS",
  "resourceType": "PROJECT_CONFIG",
  "resourceId": "7c9e6679-7425-40de-944b-e07fc1f90ae7",
  "timestamp": "2026-01-29T15:30:00Z",
  "sourceIp": "10.0.2.15",
  "outcome": "SUCCESS",
  "metadata": {
    "groupId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "requestPath": "/internal/project-configs/7c9e6679-7425-40de-944b-e07fc1f90ae7/tokens"
  }
}
```

---

### 6.2 Audit Event Types

| Event Type | Description | Retention Period |
|------------|-------------|------------------|
| `INTERNAL_ACCESS_SUCCESS` | Internal service successfully accessed resource | 90 days |
| `INTERNAL_ACCESS_DENIED` | Internal service denied access (permission issue) | 365 days |
| `INTERNAL_AUTH_FAILED` | Service authentication failed (invalid key) | 365 days |
| `INTERNAL_TOKEN_DECRYPTED` | Tokens decrypted for internal service | 90 days |

---

## 7. Security Best Practices

### 7.1 Network-Level Security

**Recommendation:** Internal endpoints should be accessible only from internal network.

**Implementation Options:**

1. **Network Segmentation:**
   ```
   Public API Gateway (port 8080) → External endpoints only
   Internal Service Mesh (port 8083) → Internal endpoints only
   ```

2. **Firewall Rules:**
   ```bash
   # Allow internal endpoints only from service subnet
   iptables -A INPUT -p tcp --dport 8083 -s 10.0.0.0/8 -j ACCEPT
   iptables -A INPUT -p tcp --dport 8083 -j DROP
   ```

3. **Service Mesh with mTLS (Istio/Linkerd):**
   ```yaml
   apiVersion: security.istio.io/v1beta1
   kind: AuthorizationPolicy
   metadata:
     name: internal-api-policy
   spec:
     selector:
       matchLabels:
         app: project-config-service
     rules:
     - from:
       - source:
           namespaces: ["samt-internal"]
       to:
       - operation:
           paths: ["/internal/*"]
   ```

---

### 7.2 Rate Limiting for Internal APIs

**Apply rate limiting even for internal services to prevent accidental DDoS:**

```yaml
resilience4j:
  ratelimiter:
    instances:
      internal-api:
        limitForPeriod: 100
        limitRefreshPeriod: 1s
        timeoutDuration: 0
```

---

### 7.3 Monitoring & Alerting

**Alert on suspicious activity:**

- Multiple failed authentication attempts from same service
- Service accessing resources outside its permission scope
- Unusually high number of token decryption requests
- Access from unexpected IP addresses

**Metrics to Track:**

```java
@Metrics
public class InternalServiceMetrics {
    
    @Counted(value = "internal.api.requests", extraTags = {"service", "#serviceName"})
    public void recordRequest(String serviceName) { }
    
    @Counted(value = "internal.api.auth.failures", extraTags = {"service", "#serviceName"})
    public void recordAuthFailure(String serviceName) { }
    
    @Timed(value = "internal.api.token.decryption")
    public void recordTokenDecryption() { }
}
```

---

## 8. Service Integration Examples

### 8.1 Sync Service Integration

```java
@Service
public class SyncServiceConfigClient {
    
    @Value("${project-config.service.url}")
    private String projectConfigServiceUrl;
    
    @Value("${sync-service.key}")
    private String syncServiceKey;
    
    @Autowired
    private RestTemplate restTemplate;
    
    public DecryptedTokensDTO getTokensForGroup(UUID groupId) {
        String url = projectConfigServiceUrl + "/internal/project-configs/group/" + groupId + "/tokens";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Name", "SYNC_SERVICE");
        headers.set("X-Service-Key", syncServiceKey);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<DecryptedTokensDTO> response = restTemplate.exchange(
                url, 
                HttpMethod.GET, 
                request, 
                DecryptedTokensDTO.class
            );
            
            return response.getBody();
            
        } catch (HttpClientErrorException.Unauthorized e) {
            throw new ServiceAuthenticationException("Failed to authenticate with Project Config Service");
            
        } catch (HttpClientErrorException.Forbidden e) {
            throw new InsufficientPermissionException("Sync Service lacks READ_DECRYPTED_TOKENS permission");
        }
    }
}
```

---

### 8.2 AI Service Integration

```java
@Service
public class AIServiceConfigClient {
    
    @Value("${project-config.service.url}")
    private String projectConfigServiceUrl;
    
    @Value("${ai-service.key}")
    private String aiServiceKey;
    
    @Autowired
    private WebClient webClient;
    
    public Mono<DecryptedTokensDTO> getTokensAsync(UUID configId) {
        return webClient.get()
            .uri(projectConfigServiceUrl + "/internal/project-configs/" + configId + "/tokens")
            .header("X-Service-Name", "AI_SERVICE")
            .header("X-Service-Key", aiServiceKey)
            .retrieve()
            .onStatus(HttpStatus::is4xxClientError, response -> 
                response.bodyToMono(String.class).flatMap(body -> {
                    if (response.statusCode() == HttpStatus.UNAUTHORIZED) {
                        return Mono.error(new ServiceAuthenticationException(body));
                    } else if (response.statusCode() == HttpStatus.FORBIDDEN) {
                        return Mono.error(new InsufficientPermissionException(body));
                    }
                    return Mono.error(new RuntimeException(body));
                })
            )
            .bodyToMono(DecryptedTokensDTO.class);
    }
}
```

---

### 8.3 Reporting Service Integration (Read-Only)

```java
@Service
public class ReportingServiceConfigClient {
    
    @Value("${project-config.service.url}")
    private String projectConfigServiceUrl;
    
    @Value("${reporting-service.key}")
    private String reportingServiceKey;
    
    @Autowired
    private RestTemplate restTemplate;
    
    /**
     * Reporting Service only needs masked config (no decrypted tokens)
     */
    public ProjectConfigDTO getConfig(UUID configId) {
        String url = projectConfigServiceUrl + "/internal/project-configs/" + configId;
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Name", "REPORTING_SERVICE");
        headers.set("X-Service-Key", reportingServiceKey);
        
        HttpEntity<Void> request = new HttpEntity<>(headers);
        
        ResponseEntity<ProjectConfigDTO> response = restTemplate.exchange(
            url, 
            HttpMethod.GET, 
            request, 
            ProjectConfigDTO.class
        );
        
        return response.getBody(); // Returns masked tokens
    }
}
```

---

## 9. Testing Service Authentication

### 9.1 Unit Tests

```java
@SpringBootTest
public class InternalServiceAuthFilterTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void testValidServiceKey_SyncService_Success() throws Exception {
        mockMvc.perform(get("/internal/project-configs/{id}/tokens", TEST_CONFIG_ID)
                .header("X-Service-Name", "SYNC_SERVICE")
                .header("X-Service-Key", VALID_SYNC_SERVICE_KEY))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.jiraApiToken").exists())
            .andExpect(jsonPath("$.githubToken").exists());
    }
    
    @Test
    void testMissingServiceHeaders_Returns401() throws Exception {
        mockMvc.perform(get("/internal/project-configs/{id}/tokens", TEST_CONFIG_ID))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("PC-401-01"));
    }
    
    @Test
    void testInvalidServiceKey_Returns401() throws Exception {
        mockMvc.perform(get("/internal/project-configs/{id}/tokens", TEST_CONFIG_ID)
                .header("X-Service-Name", "SYNC_SERVICE")
                .header("X-Service-Key", "invalid-key"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("PC-401-02"));
    }
    
    @Test
    void testReportingService_AccessDecryptedTokens_Returns403() throws Exception {
        mockMvc.perform(get("/internal/project-configs/{id}/tokens", TEST_CONFIG_ID)
                .header("X-Service-Name", "REPORTING_SERVICE")
                .header("X-Service-Key", VALID_REPORTING_SERVICE_KEY))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.error").value("PC-403-02"));
    }
}
```

---

## 10. Migration & Deployment

### 10.1 Backward Compatibility

**If migrating from an existing system without service authentication:**

1. **Phase 1 (Week 1):** Add authentication filter in "log-only" mode
   ```java
   if (!isValidServiceKey(serviceName, serviceKey)) {
       logger.warn("Invalid service key detected - would be blocked in production");
       // Continue processing
   }
   ```

2. **Phase 2 (Week 2):** Enable enforcement in staging environment
3. **Phase 3 (Week 3):** Enable enforcement in production

---

### 10.2 Deployment Checklist

- [ ] Generate unique service keys for all environments (dev/staging/prod)
- [ ] Store keys in secure environment variable management (AWS Secrets Manager, HashiCorp Vault)
- [ ] Configure service keys in Project Config Service
- [ ] Configure service keys in calling services (Sync, AI, Reporting)
- [ ] Test connectivity between services in staging
- [ ] Deploy Project Config Service with authentication filter enabled
- [ ] Deploy calling services with updated keys
- [ ] Verify audit logs are capturing internal access events
- [ ] Set up monitoring & alerting for authentication failures
- [ ] Document service key rotation procedures

---

**Document Version:** 1.0  
**Last Updated:** 2026-01-29  
**Owner:** Backend Architecture Team  
**Review Schedule:** Quarterly
