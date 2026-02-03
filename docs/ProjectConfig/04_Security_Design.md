# Project Config Service - Security Design

**Service:** Project Config Service  
**Authentication:** JWT (HS256)  
**Authorization:** Role-Based Access Control (RBAC)  
**Encryption:** AES-256-GCM  
**Version:** 1.0  
**Generated:** 2026-02-02

---

## Security Architecture

### Security Layers

```
┌─────────────────────────────────────────────────────────────┐
│                     PUBLIC API LAYER                        │
│  [JWT Validation] → [Role Authorization] → [Token Masking] │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                    BUSINESS LOGIC LAYER                     │
│  [Input Validation] → [State Machine] → [Audit Logging]    │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                   DATA ENCRYPTION LAYER                     │
│  [AES-256-GCM Encryption] ← [Key Management]               │
└─────────────────────────────────────────────────────────────┘
                               ↓
┌─────────────────────────────────────────────────────────────┐
│                    PERSISTENCE LAYER                        │
│  [Encrypted Tokens in PostgreSQL] + [Soft Delete]          │
└─────────────────────────────────────────────────────────────┘
```

---

## Authentication (JWT)

### JWT Structure

**Source:** Identity Service generates JWT tokens  
**Algorithm:** HS256 (HMAC with SHA-256)  
**Secret Key:** Shared between Identity Service and all microservices  
**Token Type:** Access Token (short-lived)

**JWT Header:**
```json
{
  "alg": "HS256",
  "typ": "JWT"
}
```

**JWT Payload:**
```json
{
  "sub": "123",                     // User ID (String representation)
  "roles": ["STUDENT", "LEADER"],   // Array of role strings
  "iat": 1738598400,                // Issued at (Unix timestamp)
  "exp": 1738599300                 // Expiration (Unix timestamp)
}
```

**Token Lifecycle:**
- **Access Token TTL:** 15 minutes
- **Refresh Token:** NOT handled by this service (Identity Service only)

---

### JWT Validation Flow

**Step-by-Step Process:**

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    private final String secretKey;  // From application.yml: jwt.secret
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     FilterChain filterChain) 
            throws ServletException, IOException {
        
        // 1. Extract JWT from Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;  // No JWT → continue to controller (will fail @PreAuthorize)
        }
        
        String jwt = authHeader.substring(7);
        
        try {
            // 2. Validate JWT signature and expiration
            Claims claims = Jwts.parserBuilder()
                .setSigningKey(secretKey.getBytes())
                .build()
                .parseClaimsJws(jwt)
                .getBody();
            
            // 3. Extract user ID and roles
            String userIdStr = claims.getSubject();
            Long userId = Long.parseLong(userIdStr);
            
            @SuppressWarnings("unchecked")
            List<String> roles = claims.get("roles", List.class);
            
            // 4. Create Authentication object
            List<GrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
            
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(userId, null, authorities);
            
            // 5. Set SecurityContext (NO database lookup)
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
        } catch (JwtException e) {
            // Invalid JWT → clear security context
            SecurityContextHolder.clearContext();
        }
        
        filterChain.doFilter(request, response);
    }
}
```

**Key Design Decision:**  
❌ **NO DATABASE USER LOOKUP** - Only validate JWT claims and set SecurityContext  
✅ This service does not call Identity Service gRPC for every request

---

### Security Configuration

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http, 
                                            JwtAuthenticationFilter jwtFilter) 
            throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // JWT-based auth
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/internal/**").permitAll()  // Service-to-service
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

---

## Authorization (RBAC)

### Role Hierarchy

**Roles from Identity Service:**

| Role | Hierarchy Level | Database Value |
|------|----------------|----------------|
| `ADMIN` | 1 (highest) | ADMIN |
| `LECTURER` | 2 | LECTURER |
| `STUDENT` + `LEADER` | 3 | STUDENT (with group leader flag) |
| `STUDENT` + `MEMBER` | 4 (lowest) | STUDENT (regular member) |

**Important:** 
- A user can have `["STUDENT", "LEADER"]` roles if they are a group leader
- A user has only `["STUDENT"]` role if they are a regular group member
- `LEADER` role is NOT stored in Identity Service; it's determined by User-Group Service

---

### Authorization Matrix

**Endpoint Permission Mapping:**

| Endpoint | ADMIN | LECTURER | LEADER | MEMBER | Public |
|----------|-------|----------|--------|--------|--------|
| **POST** /api/project-configs | ✅ | ✅ | ✅ | ❌ | ❌ |
| **GET** /api/project-configs/{id} | ✅ (full) | ✅ (full) | ✅ (masked) | ✅ (masked) | ❌ |
| **PUT** /api/project-configs/{id} | ✅ | ✅ | ✅ | ❌ | ❌ |
| **DELETE** /api/project-configs/{id} | ✅ | ✅ | ✅ | ❌ | ❌ |
| **POST** /api/project-configs/{id}/verify | ✅ | ✅ | ✅ | ❌ | ❌ |
| **POST** /admin/project-configs/{id}/restore | ✅ | ❌ | ❌ | ❌ | ❌ |
| **GET** /internal/project-configs/{id}/tokens | ✅ (service) | ❌ | ❌ | ❌ | ❌ |

**Legend:**
- ✅ (full) = Can see decrypted tokens
- ✅ (masked) = Can see masked tokens (e.g., `ghp_***k4lM`)
- ✅ (service) = Service-to-service authentication required
- ✅ = Allowed with additional business logic checks
- ❌ = Forbidden (403)

---

### Authorization Implementation

#### Method-Level Security

```java
@RestController
@RequestMapping("/api/project-configs")
public class ProjectConfigController {
    
    // CREATE: LEADER only (verified via User-Group Service)
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<ConfigResponse> createConfig(
            @RequestBody @Valid CreateConfigRequest request,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        
        // Business logic: verify user is LEADER of the group
        service.createConfig(request, userId);
        
        return ResponseEntity.status(201).body(response);
    }
    
    // READ: Any authenticated user (masking based on role)
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConfigResponse> getConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRoles(authentication);
        
        // Business logic: check if user belongs to the group
        ConfigResponse response = service.getConfig(id, userId, roles);
        
        return ResponseEntity.ok(response);
    }
    
    // UPDATE: LEADER only
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<ConfigResponse> updateConfig(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateConfigRequest request,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        service.updateConfig(id, request, userId);
        
        return ResponseEntity.ok(response);
    }
    
    // DELETE: LEADER only
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        service.softDeleteConfig(id, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    // RESTORE: ADMIN only
    @PostMapping("/admin/project-configs/{id}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ConfigResponse> restoreConfig(@PathVariable UUID id) {
        ConfigResponse response = service.restoreConfig(id);
        return ResponseEntity.ok(response);
    }
}
```

---

#### Business Logic Authorization

**Group Leadership Verification:**

```java
@Service
public class ProjectConfigService {
    
    private final UserGroupServiceClient userGroupClient;  // REST client
    
    public void createConfig(CreateConfigRequest request, Long userId) {
        // 1. Validate group exists
        GroupDto group = userGroupClient.getGroup(request.getGroupId());
        if (group == null) {
            throw new GroupNotFoundException(request.getGroupId());
        }
        
        // 2. Verify user is LEADER of the group
        if (!group.getLeaderId().equals(userId)) {
            throw new ForbiddenException("Only group leader can create config");
        }
        
        // 3. Create config...
    }
    
    public ConfigResponse getConfig(UUID id, Long userId, List<String> roles) {
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // 1. Check if user belongs to the group
        GroupDto group = userGroupClient.getGroup(config.getGroupId());
        
        boolean isMember = group.getMembers().stream()
            .anyMatch(member -> member.getUserId().equals(userId));
        
        boolean isAdmin = roles.contains("ADMIN");
        boolean isLecturer = roles.contains("LECTURER");
        
        if (!isMember && !isAdmin && !isLecturer) {
            throw new ForbiddenException("You don't have access to this config");
        }
        
        // 2. Determine token visibility
        boolean canSeeFullTokens = isAdmin || isLecturer;
        
        return ConfigResponse.builder()
            .id(config.getId())
            .groupId(config.getGroupId())
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(maskToken(config.getJiraApiTokenEncrypted(), canSeeFullTokens))
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(maskToken(config.getGithubTokenEncrypted(), canSeeFullTokens))
            .state(config.getState())
            .build();
    }
}
```

---

## Token Masking

### Masking Strategy

**Purpose:** Hide sensitive tokens from students while showing them to admins/lecturers

**Masking Rules:**

| Role | Jira Token Display | GitHub Token Display |
|------|--------------------|---------------------|
| ADMIN | Full decrypted token | Full decrypted token |
| LECTURER | Full decrypted token | Full decrypted token |
| STUDENT (LEADER/MEMBER) | `***` + last 4 chars | `ghp_***` + last 4 chars |

**Examples:**

**Original Tokens:**
- Jira: `ATATT3xFfGF0jk2lM5nB6vC7xZ8aS1dF2gH3jK4lM5nB6vC7xZ8aS1dF2gH3jK4`
- GitHub: `ghp_k3lM5nB6vC7xZ8aS1dF2gH3jK4lM5nB6v`

**Masked for STUDENT:**
- Jira: `***jK4`
- GitHub: `ghp_***B6v`

---

### Masking Implementation

```java
@Service
public class TokenMaskingService {
    
    private final TokenEncryptionService encryptionService;
    
    /**
     * Mask token based on user role
     * @param encryptedToken Encrypted token from database
     * @param canSeeFullToken true if user is ADMIN/LECTURER
     * @return Masked or full token
     */
    public String maskToken(String encryptedToken, boolean canSeeFullToken) {
        if (canSeeFullToken) {
            // Decrypt and return full token for ADMIN/LECTURER
            return encryptionService.decrypt(encryptedToken);
        }
        
        // Decrypt to get original token
        String decryptedToken = encryptionService.decrypt(encryptedToken);
        
        // Mask for STUDENT
        if (decryptedToken.startsWith("ATATT")) {
            // Jira token: show only last 4 characters
            return "***" + decryptedToken.substring(decryptedToken.length() - 4);
        } else if (decryptedToken.startsWith("ghp_")) {
            // GitHub token: show prefix + last 4 characters
            return "ghp_***" + decryptedToken.substring(decryptedToken.length() - 4);
        } else {
            // Unknown token format: full masking
            return "***";
        }
    }
}
```

**Usage in DTO:**

```java
@Getter
@Builder
public class ConfigResponse {
    private UUID id;
    private UUID groupId;
    private String jiraHostUrl;
    private String jiraApiToken;  // Masked or full based on role
    private String githubRepoUrl;
    private String githubToken;   // Masked or full based on role
    private String state;
    private Instant lastVerifiedAt;
    private String invalidReason;
}
```

---

## Data Encryption

### AES-256-GCM Encryption

**Algorithm:** AES-256-GCM (Galois/Counter Mode)  
**Key Size:** 256 bits (32 bytes)  
**IV Size:** 96 bits (12 bytes)  
**Authentication Tag:** 128 bits (16 bytes)

**Why GCM Mode:**
- ✅ **Authenticated Encryption:** Detects tampering (integrity + confidentiality)
- ✅ **Parallelizable:** Faster than CBC mode
- ✅ **NIST Recommended:** FIPS 140-2 compliant
- ❌ **IV Reuse Catastrophic:** Must generate random IV for each encryption

---

### Key Management

**Environment Variable:**
```yaml
# application.yml
encryption:
  secret-key: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2"  # 64 hex chars = 256 bits
```

**Docker Compose:**
```yaml
services:
  project-config-service:
    image: project-config-service:latest
    environment:
      - ENCRYPTION_SECRET_KEY=${ENCRYPTION_SECRET_KEY}  # From .env file
```

**Production Best Practice:**
- Store key in AWS Secrets Manager / Azure Key Vault
- Rotate key annually
- Use different keys per environment (dev/staging/prod)

---

### Encryption Implementation

**Service Class:**

```java
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class TokenEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;      // 96 bits
    private static final int GCM_TAG_LENGTH = 128;    // 128 bits
    
    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;
    
    public TokenEncryptionService(@Value("${encryption.secret-key}") String keyHex) {
        byte[] keyBytes = hexStringToByteArray(keyHex);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 256 bits (64 hex chars)");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }
    
    /**
     * Encrypt plaintext using AES-256-GCM
     * @param plaintext Raw token (e.g., Jira or GitHub token)
     * @return Encrypted format: {iv_base64}:{ciphertext_base64}
     */
    public String encrypt(String plaintext) {
        try {
            // Generate random IV (MUST be unique per encryption)
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, parameterSpec);
            
            // Encrypt (includes authentication tag)
            byte[] plaintextBytes = plaintext.getBytes(StandardCharsets.UTF_8);
            byte[] ciphertextWithTag = cipher.doFinal(plaintextBytes);
            
            // Encode to Base64
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertextWithTag);
            
            // Format: {iv}:{ciphertext+tag}
            return ivBase64 + ":" + ciphertextBase64;
            
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt token", e);
        }
    }
    
    /**
     * Decrypt ciphertext using AES-256-GCM
     * @param encryptedToken Format: {iv_base64}:{ciphertext_base64}
     * @return Decrypted plaintext
     */
    public String decrypt(String encryptedToken) {
        try {
            // Parse encrypted format
            String[] parts = encryptedToken.split(":");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid encrypted token format");
            }
            
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertextWithTag = Base64.getDecoder().decode(parts[1]);
            
            // Initialize cipher
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec parameterSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, parameterSpec);
            
            // Decrypt (automatically verifies authentication tag)
            byte[] plaintextBytes = cipher.doFinal(ciphertextWithTag);
            
            return new String(plaintextBytes, StandardCharsets.UTF_8);
            
        } catch (Exception e) {
            throw new DecryptionException("Failed to decrypt token", e);
        }
    }
    
    private byte[] hexStringToByteArray(String hex) {
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

---

### Encryption Usage

**Before Saving to Database:**

```java
@Service
public class ProjectConfigService {
    
    private final TokenEncryptionService encryptionService;
    private final ProjectConfigRepository repository;
    
    public ConfigResponse createConfig(CreateConfigRequest request, Long userId) {
        // Validate raw tokens
        validateJiraToken(request.getJiraApiToken());
        validateGitHubToken(request.getGithubToken());
        
        // Encrypt tokens
        String encryptedJiraToken = encryptionService.encrypt(request.getJiraApiToken());
        String encryptedGithubToken = encryptionService.encrypt(request.getGithubToken());
        
        // Create entity
        ProjectConfig config = ProjectConfig.builder()
            .groupId(request.getGroupId())
            .jiraHostUrl(request.getJiraHostUrl())
            .jiraApiTokenEncrypted(encryptedJiraToken)  // Store encrypted
            .githubRepoUrl(request.getGithubRepoUrl())
            .githubTokenEncrypted(encryptedGithubToken)  // Store encrypted
            .state("DRAFT")
            .build();
        
        repository.save(config);
        
        return mapToResponse(config, false);  // Don't show full tokens
    }
}
```

**When Calling External APIs:**

```java
@Service
public class JiraVerificationService {
    
    private final TokenEncryptionService encryptionService;
    private final RestTemplate restTemplate;
    
    public boolean verifyJiraConnection(ProjectConfig config) {
        // Decrypt token
        String decryptedToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        
        // Call Jira API
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + decryptedToken);
        
        try {
            ResponseEntity<String> response = restTemplate.exchange(
                config.getJiraHostUrl() + "/rest/api/3/myself",
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            
            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            return false;
        }
    }
}
```

---

## Service-to-Service Authentication

### Internal API Security

**Endpoint:** `GET /internal/project-configs/{id}/tokens`  
**Consumer:** Sync Service (fetches decrypted tokens to sync Jira/GitHub data)

**Authentication Method:** Custom header-based authentication

---

### Header-Based Authentication

**Required Headers:**

| Header | Value | Description |
|--------|-------|-------------|
| `X-Service-Name` | `sync-service` | Service identifier |
| `X-Service-Key` | `<secret_key>` | Shared secret between services |

**Configuration:**

```yaml
# application.yml (Project Config Service)
service-to-service:
  allowed-services:
    - name: sync-service
      key: "s3cr3t_k3y_f0r_sync_s3rv1c3"  # From environment variable
```

---

### Authentication Filter

```java
@Component
public class ServiceToServiceAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${service-to-service.allowed-services[0].name}")
    private String allowedServiceName;
    
    @Value("${service-to-service.allowed-services[0].key}")
    private String allowedServiceKey;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                     HttpServletResponse response, 
                                     FilterChain filterChain) 
            throws ServletException, IOException {
        
        // Only apply to /internal/** endpoints
        if (!request.getRequestURI().startsWith("/internal/")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Extract headers
        String serviceName = request.getHeader("X-Service-Name");
        String serviceKey = request.getHeader("X-Service-Key");
        
        // Validate
        if (!allowedServiceName.equals(serviceName) || 
            !allowedServiceKey.equals(serviceKey)) {
            
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":\"Invalid service credentials\"}");
            return;
        }
        
        // Set authentication
        UsernamePasswordAuthenticationToken authentication = 
            new UsernamePasswordAuthenticationToken(serviceName, null, 
                List.of(new SimpleGrantedAuthority("SERVICE")));
        
        SecurityContextHolder.getContext().setAuthentication(authentication);
        
        filterChain.doFilter(request, response);
    }
}
```

---

### Internal Endpoint Implementation

```java
@RestController
@RequestMapping("/internal/project-configs")
public class InternalConfigController {
    
    private final ProjectConfigService service;
    private final TokenEncryptionService encryptionService;
    
    /**
     * Get decrypted tokens for Sync Service
     * WARNING: Returns sensitive data - only for service-to-service calls
     */
    @GetMapping("/{id}/tokens")
    @PreAuthorize("hasAuthority('SERVICE')")
    public ResponseEntity<DecryptedTokensResponse> getDecryptedTokens(@PathVariable UUID id) {
        ProjectConfig config = service.getConfigById(id);
        
        // Decrypt tokens
        String jiraToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
        String githubToken = encryptionService.decrypt(config.getGithubTokenEncrypted());
        
        DecryptedTokensResponse response = DecryptedTokensResponse.builder()
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(jiraToken)        // DECRYPTED
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(githubToken)       // DECRYPTED
            .build();
        
        return ResponseEntity.ok(response);
    }
}
```

**Sync Service Client:**

```java
@Service
public class ProjectConfigClient {
    
    private final RestTemplate restTemplate;
    
    @Value("${project-config-service.url}")
    private String projectConfigServiceUrl;
    
    @Value("${service-to-service.service-name}")
    private String serviceName;
    
    @Value("${service-to-service.service-key}")
    private String serviceKey;
    
    public DecryptedTokensResponse getDecryptedTokens(UUID configId) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-Service-Name", serviceName);
        headers.set("X-Service-Key", serviceKey);
        
        ResponseEntity<DecryptedTokensResponse> response = restTemplate.exchange(
            projectConfigServiceUrl + "/internal/project-configs/" + configId + "/tokens",
            HttpMethod.GET,
            new HttpEntity<>(headers),
            DecryptedTokensResponse.class
        );
        
        return response.getBody();
    }
}
```

---

## Input Validation

### Request Validation

**Bean Validation Annotations:**

```java
public record CreateConfigRequest(
    @NotNull(message = "Group ID is required")
    UUID groupId,
    
    @NotBlank(message = "Jira host URL is required")
    @Pattern(regexp = "^https://[a-zA-Z0-9.-]+\\.(atlassian\\.net|[a-z]{2,})$", 
             message = "Invalid Jira host URL")
    String jiraHostUrl,
    
    @NotBlank(message = "Jira API token is required")
    @Pattern(regexp = "^ATATT[A-Za-z0-9+/=_-]{100,500}$", 
             message = "Invalid Jira API token format")
    String jiraApiToken,
    
    @NotBlank(message = "GitHub repo URL is required")
    @Pattern(regexp = "^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$", 
             message = "Invalid GitHub repository URL")
    String githubRepoUrl,
    
    @NotBlank(message = "GitHub token is required")
    @Pattern(regexp = "^ghp_[A-Za-z0-9]{36,}$", 
             message = "Invalid GitHub token format")
    String githubToken
) {}
```

**Validation Exception Handler:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationErrors(
            MethodArgumentNotValidException ex) {
        
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.toList());
        
        ErrorResponse errorResponse = ErrorResponse.builder()
            .error("VALIDATION_ERROR")
            .message("Invalid request parameters")
            .details(errors)
            .timestamp(Instant.now())
            .build();
        
        return ResponseEntity.badRequest().body(errorResponse);
    }
}
```

---

## Audit Logging

### Security Events to Log

**Critical Security Events:**

| Event | Log Level | Example |
|-------|-----------|---------|
| JWT validation failure | WARN | "Invalid JWT signature from IP 192.168.1.100" |
| Unauthorized access attempt | WARN | "User 123 attempted to access config 456 without permission" |
| Config creation | INFO | "User 123 created config for group 456" |
| Token decryption | DEBUG | "Decrypted Jira token for config 789" |
| Service-to-service auth failure | ERROR | "Invalid service credentials from IP 10.0.1.5" |
| Soft delete | INFO | "User 123 soft-deleted config 456" |
| Config restore | WARN | "Admin 999 restored config 456" |

---

### Logging Implementation

**Logback Configuration (logback-spring.xml):**

```xml
<configuration>
    <appender name="SECURITY" class="ch.qos.logback.core.rolling.RollingFileAppender">
        <file>logs/security.log</file>
        <encoder>
            <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %msg%n</pattern>
        </encoder>
        <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
            <fileNamePattern>logs/security-%d{yyyy-MM-dd}.log</fileNamePattern>
            <maxHistory>90</maxHistory>
        </rollingPolicy>
    </appender>
    
    <logger name="com.fpt.projectconfig.security" level="INFO" additivity="false">
        <appender-ref ref="SECURITY" />
    </logger>
</configuration>
```

**Service Layer Logging:**

```java
@Service
@Slf4j
public class ProjectConfigService {
    
    public ConfigResponse createConfig(CreateConfigRequest request, Long userId) {
        log.info("User {} creating config for group {}", userId, request.getGroupId());
        
        // ... business logic ...
        
        log.info("Config {} created successfully by user {}", config.getId(), userId);
        return response;
    }
    
    public void softDeleteConfig(UUID configId, Long userId) {
        log.warn("User {} soft-deleting config {}", userId, configId);
        
        // ... business logic ...
        
        log.info("Config {} soft-deleted by user {}", configId, userId);
    }
}
```

---

## Security Checklist

**Before Deployment:**

- [ ] JWT secret key is loaded from environment variable (NOT hardcoded)
- [ ] Encryption key is 256 bits and stored securely (AWS Secrets Manager)
- [ ] Service-to-service keys are unique per environment (dev/staging/prod)
- [ ] HTTPS enforced in production (TLS 1.2+)
- [ ] CORS configured to allow only trusted origins
- [ ] Rate limiting enabled (e.g., 100 req/min per IP)
- [ ] SQL injection protected (JPA parameterized queries)
- [ ] XSS protected (all inputs validated)
- [ ] Sensitive logs redacted (no tokens in logs)
- [ ] Database credentials rotated quarterly
- [ ] Security headers configured (X-Frame-Options, X-Content-Type-Options)

---

**Next Document:** Implementation Guide
