# PACKAGE STRUCTURE – PROJECT CONFIG SERVICE

## 1. Overview

Package structure theo best practices Spring Boot, phân tách rõ ràng giữa các layers: Controller, Service, Repository, Entity, DTO, Security.

**Root Package:** `com.samt.projectconfig`

---

## 2. Full Package Tree

```
com.samt.projectconfig
├── ProjectConfigServiceApplication.java
├── controller
│   ├── ProjectConfigController.java
│   └── InternalProjectConfigController.java
├── service
│   ├── ProjectConfigService.java
│   └── impl
│       ├── ProjectConfigServiceImpl.java
│       ├── EncryptionServiceImpl.java
│       ├── JiraVerificationServiceImpl.java
│       └── GitHubVerificationServiceImpl.java
├── repository
│   ├── ProjectConfigRepository.java
│   └── AuditLogRepository.java
├── entity
│   ├── ProjectConfig.java
│   └── AuditLog.java
├── dto
│   ├── request
│   │   ├── CreateConfigRequest.java
│   │   ├── UpdateConfigRequest.java
│   │   └── VerifyCredentialsRequest.java
│   └── response
│       ├── ProjectConfigResponse.java
│       ├── DecryptedTokenResponse.java
│       ├── VerificationResult.java
│       ├── ConnectionStatus.java
│       ├── ErrorResponse.java
│       ├── ValidationErrorResponse.java
│       └── FieldErrorDetail.java
├── exception
│   ├── ProjectConfigException.java
│   ├── ConfigNotFoundException.java
│   ├── ConfigAlreadyExistsException.java
│   ├── InvalidUrlFormatException.java
│   ├── InvalidTokenFormatException.java
│   ├── ConnectionFailedException.java
│   ├── InvalidCredentialsException.java
│   ├── EncryptionException.java
│   ├── GroupNotFoundException.java
│   ├── ForbiddenException.java
│   ├── RateLimitExceededException.java
│   ├── ConcurrentUpdateException.java
│   ├── ConfigExpiredException.java
│   └── GlobalExceptionHandler.java
├── security
│   ├── JwtAuthenticationFilter.java
│   ├── SecurityConfig.java
│   ├── ServiceAuthenticationFilter.java
│   └── RateLimitInterceptor.java
├── config
│   ├── EncryptionConfig.java
│   ├── RestClientConfig.java
│   └── AuditLogConfig.java
├── validator
│   ├── GroupExists.java
│   └── GroupExistsValidator.java
└── util
    ├── TokenMasker.java
    ├── UrlValidator.java
    └── AuditLogger.java
```

---

## 3. Package Details

### 3.1 Root Package

**Package:** `com.samt.projectconfig`

**File:** `ProjectConfigServiceApplication.java`

```java
package com.samt.projectconfig;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableDiscoveryClient
@EnableScheduling
public class ProjectConfigServiceApplication {
    
    public static void main(String[] args) {
        SpringApplication.run(ProjectConfigServiceApplication.class, args);
    }
}
```

---

### 3.2 Controller Package

**Package:** `com.samt.projectconfig.controller`

#### ProjectConfigController.java

**Responsibility:** Handle user-facing REST endpoints

**Dependencies:**
- `ProjectConfigService`
- JWT Security

**Endpoints:**
- `POST /api/project-configs` - Create config
- `GET /api/project-configs/{id}` - Get config by ID
- `GET /api/project-configs/group/{groupId}` - Get config by group
- `PUT /api/project-configs/{id}` - Update config
- `DELETE /api/project-configs/{id}` - Delete config
- `POST /api/project-configs/{id}/verify` - Verify existing config
- `POST /api/project-configs/verify` - Verify credentials (pre-create)
- `GET /api/project-configs/lecturer/me` - List configs by lecturer

```java
package com.samt.projectconfig.controller;

@RestController
@RequestMapping("/api/project-configs")
@RequiredArgsConstructor
@Validated
public class ProjectConfigController {
    private final ProjectConfigService service;
    // Controller methods...
}
```

---

#### InternalProjectConfigController.java

**Responsibility:** Handle internal service-to-service endpoints

**Dependencies:**
- `ProjectConfigService`
- Service Authentication

**Endpoints:**
- `GET /internal/project-configs/{id}/tokens` - Get decrypted tokens

**Security:** Requires `X-Internal-Service-Key` header

```java
package com.samt.projectconfig.controller;

@RestController
@RequestMapping("/internal/project-configs")
@RequiredArgsConstructor
public class InternalProjectConfigController {
    
    private final ProjectConfigService service;
    
    @GetMapping("/{configId}/tokens")
    @PreAuthorize("hasAuthority('SERVICE')")
    public ResponseEntity<DecryptedTokenResponse> getDecryptedTokens(
        @PathVariable UUID configId,
        @RequestHeader("X-Service-Name") String serviceName
    ) {
        // Implementation...
    }
}
```

---

### 3.3 Service Package

**Package:** `com.samt.projectconfig.service`

#### ProjectConfigService.java (Interface)

```java
package com.samt.projectconfig.service;

import com.samt.projectconfig.dto.request.*;
import com.samt.projectconfig.dto.response.*;
import java.util.UUID;

public interface ProjectConfigService {
    
    ProjectConfigResponse createConfig(CreateConfigRequest request, UUID createdBy);
    
    ProjectConfigResponse getConfigById(UUID configId, UUID requestedBy);
    
    ProjectConfigResponse getConfigByGroupId(UUID groupId, UUID requestedBy);
    
    ProjectConfigResponse updateConfig(UUID configId, UpdateConfigRequest request, UUID updatedBy);
    
    void deleteConfig(UUID configId, UUID deletedBy);
    
    VerificationResult verifyExistingConfig(UUID configId, UUID requestedBy);
    
    VerificationResult verifyCredentials(VerifyCredentialsRequest request);
    
    DecryptedTokenResponse getDecryptedTokens(UUID configId);
}
```

---

**Package:** `com.samt.projectconfig.service.impl`

#### ProjectConfigServiceImpl.java

**Responsibility:** Business logic implementation

**Dependencies:**
- `ProjectConfigRepository`
- `EncryptionService`
- `JiraVerificationService`
- `GitHubVerificationService`
- `AuditLogService`

```java
package com.samt.projectconfig.service.impl;

@Service
@RequiredArgsConstructor
@Transactional
public class ProjectConfigServiceImpl implements ProjectConfigService {
    
    private final ProjectConfigRepository repository;
    private final EncryptionService encryptionService;
    private final JiraVerificationService jiraVerificationService;
    private final GitHubVerificationService githubVerificationService;
    private final AuditLogService auditLogService;
    
    // Service methods implementation...
}
```

---

#### EncryptionServiceImpl.java

**Responsibility:** Token encryption/decryption (AES-256-GCM)

```java
package com.samt.projectconfig.service.impl;

@Service
@RequiredArgsConstructor
public class EncryptionServiceImpl implements EncryptionService {
    
    @Value("${encryption.secret-key}")
    private String secretKey;
    
    public String encrypt(String plainText) {
        // AES-256-GCM encryption
    }
    
    public String decrypt(String encryptedText) {
        // AES-256-GCM decryption
    }
}
```

---

#### JiraVerificationServiceImpl.java

**Responsibility:** Verify Jira connection and credentials

```java
package com.samt.projectconfig.service.impl;

@Service
@RequiredArgsConstructor
public class JiraVerificationServiceImpl implements JiraVerificationService {
    
    private final RestTemplate restTemplate;
    
    public ConnectionStatus verify(String jiraHostUrl, String jiraApiToken) {
        // Call Jira REST API to verify connection
    }
}
```

---

#### GitHubVerificationServiceImpl.java

**Responsibility:** Verify GitHub connection and credentials

```java
package com.samt.projectconfig.service.impl;

@Service
@RequiredArgsConstructor
public class GitHubVerificationServiceImpl implements GitHubVerificationService {
    
    private final RestTemplate restTemplate;
    
    public ConnectionStatus verify(String githubRepoUrl, String githubToken) {
        // Call GitHub REST API to verify connection
    }
}
```

---

### 3.4 Repository Package

**Package:** `com.samt.projectconfig.repository`

#### ProjectConfigRepository.java

```java
package com.samt.projectconfig.repository;

import com.samt.projectconfig.entity.ProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.UUID;
import java.util.Optional;

@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
    
    @Query("SELECT c FROM ProjectConfig c WHERE c.configId = :configId AND c.deletedAt IS NULL")
    Optional<ProjectConfig> findById(UUID configId);
    
    @Query("SELECT c FROM ProjectConfig c WHERE c.groupId = :groupId AND c.deletedAt IS NULL")
    Optional<ProjectConfig> findByGroupId(UUID groupId);
    
    @Query("SELECT CASE WHEN COUNT(c) > 0 THEN true ELSE false END FROM ProjectConfig c " +
           "WHERE c.groupId = :groupId AND c.deletedAt IS NULL")
    boolean existsByGroupId(UUID groupId);
}
```

---

#### AuditLogRepository.java

```java
package com.samt.projectconfig.repository;

import com.samt.projectconfig.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, UUID> {
    // Audit log methods
}
```

---

### 3.5 Entity Package

**Package:** `com.samt.projectconfig.entity`

#### ProjectConfig.java

```java
package com.samt.projectconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "project_configs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConfig {
    
    @Id
    @Column(name = "config_id", updatable = false, nullable = false)
    private UUID configId;
    
    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId;
    
    @Column(name = "jira_host_url", length = 255, nullable = false)
    private String jiraHostUrl;
    
    @Column(name = "jira_api_token", columnDefinition = "TEXT", nullable = false)
    private String jiraApiToken; // Encrypted
    
    @Column(name = "github_repo_url", length = 255, nullable = false)
    private String githubRepoUrl;
    
    @Column(name = "github_token", columnDefinition = "TEXT", nullable = false)
    private String githubToken; // Encrypted
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
    
    @Column(name = "deleted_by")
    private UUID deletedBy;
    
    @Version
    @Column(name = "version")
    private Long version; // Optimistic locking
    
    @PrePersist
    protected void onCreate() {
        if (configId == null) {
            configId = UUID.randomUUID();
        }
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
```

---

#### AuditLog.java

```java
package com.samt.projectconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID eventId;
    
    @Column(nullable = false)
    private String eventType;
    
    private UUID configId;
    private UUID userId;
    private String ipAddress;
    private String userAgent;
    
    @Column(columnDefinition = "TEXT")
    private String changes;
    
    private boolean success;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
}
```

---

### 3.6 DTO Package

**Package:** `com.samt.projectconfig.dto.request`

- `CreateConfigRequest.java` - Request for creating config
- `UpdateConfigRequest.java` - Request for updating config
- `VerifyCredentialsRequest.java` - Request for verifying credentials

**Package:** `com.samt.projectconfig.dto.response`

- `ProjectConfigResponse.java` - Config with masked tokens
- `DecryptedTokenResponse.java` - Config with decrypted tokens (internal only)
- `VerificationResult.java` - Result of connection verification
- `ConnectionStatus.java` - Status of individual connection (Jira/GitHub)
- `ErrorResponse.java` - Standard error response
- `ValidationErrorResponse.java` - Validation error with field details
- `FieldErrorDetail.java` - Individual field error

---

### 3.7 Exception Package

**Package:** `com.samt.projectconfig.exception`

All exception classes listed in section 3 of Validation & Exceptions document.

---

### 3.8 Security Package

**Package:** `com.samt.projectconfig.security`

#### JwtAuthenticationFilter.java

**Responsibility:** Validate JWT token from requests

```java
package com.samt.projectconfig.security;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider jwtTokenProvider;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        // Extract and validate JWT
    }
}
```

---

#### SecurityConfig.java

**Responsibility:** Spring Security configuration

```java
package com.samt.projectconfig.security;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ServiceAuthenticationFilter serviceAuthenticationFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/internal/**").hasAuthority("SERVICE")
                .requestMatchers("/api/project-configs/**").authenticated()
                .anyRequest().permitAll()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(serviceAuthenticationFilter, JwtAuthenticationFilter.class);
        
        return http.build();
    }
}
```

---

#### ServiceAuthenticationFilter.java

**Responsibility:** Authenticate service-to-service calls

```java
package com.samt.projectconfig.security;

@Component
public class ServiceAuthenticationFilter extends OncePerRequestFilter {
    
    @Value("${internal.service-key}")
    private String internalServiceKey;
    
    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        
        if (request.getRequestURI().startsWith("/internal/")) {
            String serviceKey = request.getHeader("X-Internal-Service-Key");
            String serviceName = request.getHeader("X-Service-Name");
            
            if (!internalServiceKey.equals(serviceKey)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            
            // Set service authentication
            ServiceAuthentication auth = new ServiceAuthentication(serviceName);
            SecurityContextHolder.getContext().setAuthentication(auth);
        }
        
        filterChain.doFilter(request, response);
    }
}
```

---

#### RateLimitInterceptor.java

**Responsibility:** Rate limiting for verify endpoint

```java
package com.samt.projectconfig.security;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {
    
    private final Map<String, RateLimiter> rateLimiters = new ConcurrentHashMap<>();
    
    @Override
    public boolean preHandle(
        HttpServletRequest request,
        HttpServletResponse response,
        Object handler
    ) throws Exception {
        
        if (request.getRequestURI().contains("/verify")) {
            String userId = extractUserId(request);
            RateLimiter limiter = getRateLimiter(userId);
            
            if (!limiter.tryAcquire()) {
                response.setStatus(429);
                response.getWriter().write("{\"code\":\"RATE_LIMIT_EXCEEDED\"}");
                return false;
            }
        }
        
        return true;
    }
}
```

---

### 3.9 Config Package

**Package:** `com.samt.projectconfig.config`

#### EncryptionConfig.java

```java
package com.samt.projectconfig.config;

@Configuration
public class EncryptionConfig {
    
    @Value("${encryption.secret-key}")
    private String secretKey;
    
    @Bean
    public SecretKey aesSecretKey() throws Exception {
        byte[] decodedKey = Base64.getDecoder().decode(secretKey);
        return new SecretKeySpec(decodedKey, "AES");
    }
}
```

---

#### RestClientConfig.java

```java
package com.samt.projectconfig.config;

@Configuration
public class RestClientConfig {
    
    @Bean
    public RestTemplate restTemplate() {
        RestTemplate restTemplate = new RestTemplate();
        
        // Set timeouts
        HttpComponentsClientHttpRequestFactory factory = 
            new HttpComponentsClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(10000);
        
        restTemplate.setRequestFactory(factory);
        return restTemplate;
    }
}
```

---

### 3.10 Validator Package

**Package:** `com.samt.projectconfig.validator`

#### GroupExists.java (Annotation)

```java
package com.samt.projectconfig.validator;

@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = GroupExistsValidator.class)
public @interface GroupExists {
    String message() default "Group does not exist";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
```

---

#### GroupExistsValidator.java

```java
package com.samt.projectconfig.validator;

@Component
@RequiredArgsConstructor
public class GroupExistsValidator implements ConstraintValidator<GroupExists, UUID> {
    
    private final GroupRepository groupRepository;
    
    @Override
    public boolean isValid(UUID groupId, ConstraintValidatorContext context) {
        if (groupId == null) return true;
        return groupRepository.existsById(groupId);
    }
}
```

---

### 3.11 Util Package

**Package:** `com.samt.projectconfig.util`

#### TokenMasker.java

```java
package com.samt.projectconfig.util;

public class TokenMasker {
    
    public static String maskJiraToken(String token) {
        if (token == null || token.length() < 7) return "***";
        return token.substring(0, 7) + "***...";
    }
    
    public static String maskGitHubToken(String token) {
        if (token == null || token.length() < 4) return "***";
        return token.substring(0, 4) + "***...";
    }
}
```

---

#### UrlValidator.java

```java
package com.samt.projectconfig.util;

import java.util.regex.Pattern;

public class UrlValidator {
    
    private static final Pattern JIRA_URL_PATTERN = 
        Pattern.compile("^https://[a-zA-Z0-9-]+\\.atlassian\\.net$");
    
    private static final Pattern GITHUB_URL_PATTERN = 
        Pattern.compile("^https://github\\.com/[a-zA-Z0-9_-]+/[a-zA-Z0-9_-]+$");
    
    public static boolean isValidJiraUrl(String url) {
        return JIRA_URL_PATTERN.matcher(url).matches();
    }
    
    public static boolean isValidGitHubUrl(String url) {
        return GITHUB_URL_PATTERN.matcher(url).matches();
    }
}
```

---

#### AuditLogger.java

```java
package com.samt.projectconfig.util;

@Component
@RequiredArgsConstructor
public class AuditLogger {
    
    private final AuditLogRepository repository;
    
    public void log(String eventType, UUID configId, UUID userId, String changes) {
        AuditLog log = AuditLog.builder()
            .eventId(UUID.randomUUID())
            .eventType(eventType)
            .configId(configId)
            .userId(userId)
            .changes(changes)
            .success(true)
            .timestamp(LocalDateTime.now())
            .build();
        
        repository.save(log);
    }
}
```

---

## 4. application.yml

```yaml
server:
  port: 8083

spring:
  application:
    name: project-config-service
  
  datasource:
    url: jdbc:postgresql://localhost:5432/samt_projectconfig
    username: samt_user
    password: ${DB_PASSWORD}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: none
    show-sql: false
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.PostgreSQLDialect
  
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

encryption:
  secret-key: ${ENCRYPTION_SECRET_KEY}

internal:
  service-key: ${INTERNAL_SERVICE_KEY}

jwt:
  secret: ${JWT_SECRET}
  expiration: 86400000

eureka:
  client:
    service-url:
      defaultZone: http://localhost:8761/eureka/
  instance:
    prefer-ip-address: true
```

---

## 5. pom.xml Dependencies

```xml
<dependencies>
    <!-- Spring Boot Starter Web -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-web</artifactId>
    </dependency>
    
    <!-- Spring Boot Starter Data JPA -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>
    
    <!-- Spring Boot Starter Security -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    
    <!-- Spring Boot Starter Validation -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    
    <!-- PostgreSQL Driver -->
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Flyway Migration -->
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    
    <!-- JWT -->
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-impl</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-jackson</artifactId>
        <version>0.11.5</version>
        <scope>runtime</scope>
    </dependency>
    
    <!-- Lombok -->
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
    
    <!-- Eureka Client (Service Discovery) -->
    <dependency>
        <groupId>org.springframework.cloud</groupId>
        <artifactId>spring-cloud-starter-netflix-eureka-client</artifactId>
    </dependency>
    
    <!-- Apache HttpClient (for RestTemplate timeout) -->
    <dependency>
        <groupId>org.apache.httpcomponents.client5</groupId>
        <artifactId>httpclient5</artifactId>
    </dependency>
</dependencies>
```

---

## 6. Package Responsibilities Summary

| Package | Purpose | Key Classes |
|---------|---------|-------------|
| `controller` | REST endpoints | ProjectConfigController, InternalProjectConfigController |
| `service` | Business logic | ProjectConfigService, EncryptionService, VerificationServices |
| `repository` | Database access | ProjectConfigRepository, AuditLogRepository |
| `entity` | JPA entities | ProjectConfig, AuditLog |
| `dto.request` | Request DTOs | CreateConfigRequest, UpdateConfigRequest |
| `dto.response` | Response DTOs | ProjectConfigResponse, VerificationResult |
| `exception` | Exception handling | ProjectConfigException, GlobalExceptionHandler |
| `security` | Authentication & Authorization | JwtFilter, SecurityConfig, ServiceAuthFilter |
| `config` | Spring configuration | EncryptionConfig, RestClientConfig |
| `validator` | Custom validators | GroupExists, GroupExistsValidator |
| `util` | Helper utilities | TokenMasker, UrlValidator, AuditLogger |

---

## 7. Layered Architecture Diagram

```
┌─────────────────────────────────────────────────────────┐
│                    Controller Layer                      │
│  (ProjectConfigController, InternalProjectConfigController) │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                     Service Layer                        │
│  (ProjectConfigService, EncryptionService, VerificationServices) │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                   Repository Layer                       │
│         (ProjectConfigRepository, AuditLogRepository)    │
└────────────────────┬────────────────────────────────────┘
                     │
                     ▼
┌─────────────────────────────────────────────────────────┐
│                    Database Layer                        │
│                  (PostgreSQL - project_configs)          │
└─────────────────────────────────────────────────────────┘

Cross-Cutting Concerns:
├── Security (JWT, Service Auth)
├── Exception Handling (Global Handler)
├── Validation (Jakarta Validation)
└── Audit Logging (AuditLogger)
```

---

## 8. File Count Summary

| Category | Count |
|----------|-------|
| Controllers | 2 |
| Services | 5 (1 interface + 4 implementations) |
| Repositories | 2 |
| Entities | 2 |
| Request DTOs | 3 |
| Response DTOs | 7 |
| Exceptions | 13 |
| Security Classes | 4 |
| Config Classes | 3 |
| Validators | 2 |
| Utils | 3 |
| **Total** | **46 classes** |
