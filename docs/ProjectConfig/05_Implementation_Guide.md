# Project Config Service - Implementation Guide

**Service:** Project Config Service  
**Framework:** Spring Boot 3.2+  
**Java Version:** 17+  
**Build Tool:** Maven  
**Database:** PostgreSQL 15+  
**Version:** 1.0  
**Generated:** 2026-02-02

---

## Project Structure

### Package Organization

```
project-config-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/example/projectconfig/
│   │   │       ├── ProjectConfigServiceApplication.java
│   │   │       ├── controller/
│   │   │       │   ├── ProjectConfigController.java
│   │   │       │   └── InternalConfigController.java
│   │   │       ├── service/
│   │   │       │   ├── ProjectConfigService.java
│   │   │       │   ├── TokenEncryptionService.java
│   │   │       │   ├── TokenMaskingService.java
│   │   │       │   ├── JiraVerificationService.java
│   │   │       │   └── GitHubVerificationService.java
│   │   │       ├── repository/
│   │   │       │   └── ProjectConfigRepository.java
│   │   │       ├── entity/
│   │   │       │   └── ProjectConfig.java
│   │   │       ├── dto/
│   │   │       │   ├── request/
│   │   │       │   │   ├── CreateConfigRequest.java
│   │   │       │   │   └── UpdateConfigRequest.java
│   │   │       │   └── response/
│   │   │       │       ├── ConfigResponse.java
│   │   │       │       ├── DecryptedTokensResponse.java
│   │   │       │       └── ErrorResponse.java
│   │   │       ├── security/
│   │   │       │   ├── JwtAuthenticationFilter.java
│   │   │       │   ├── ServiceToServiceAuthFilter.java
│   │   │       │   ├── SecurityConfig.java
│   │   │       │   └── JwtService.java
│   │   │       ├── client/
│   │   │       │   └── grpc/
│   │   │       │       ├── IdentityServiceGrpcClient.java
│   │   │       │       └── UserGroupServiceGrpcClient.java
│   │   │       ├── exception/
│   │   │       │   ├── ConfigNotFoundException.java
│   │   │       │   ├── GroupNotFoundException.java
│   │   │       │   ├── ForbiddenException.java
│   │   │       │   ├── EncryptionException.java
│   │   │       │   ├── VerificationException.java
│   │   │       │   └── GlobalExceptionHandler.java
│   │   │       ├── config/
│   │   │       │   ├── RestTemplateConfig.java
│   │   │       │   ├── GrpcClientConfig.java
│   │   │       │   └── EncryptionConfig.java
│   │   │       └── scheduler/
│   │   │           └── ConfigCleanupScheduler.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       └── db/migration/
│   │           └── V1__initial_schema.sql
│   └── test/
│       └── java/
│           └── com/fpt/projectconfig/
│               ├── service/
│               │   ├── ProjectConfigServiceTest.java
│               │   ├── TokenEncryptionServiceTest.java
│               │   └── JiraVerificationServiceTest.java
│               ├── controller/
│               │   └── ProjectConfigControllerTest.java
│               └── integration/
│                   └── ProjectConfigIntegrationTest.java
├── pom.xml
└── Dockerfile
```

---

## Maven Dependencies (pom.xml)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 
         https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>
    
    <parent>
        <groupId>com.fpt</groupId>
        <artifactId>samt-parent</artifactId>
        <version>1.0.0</version>
    </parent>
    
    <artifactId>project-config-service</artifactId>
    <version>1.0.0</version>
    <name>Project Config Service</name>
    
    <properties>
        <java.version>17</java.version>
        <spring-boot.version>3.2.0</spring-boot.version>
    </properties>
    
    <dependencies>
        <!-- Spring Boot Starters -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-security</artifactId>
        </dependency>
        
        <!-- Database -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>
        
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
        
        <!-- gRPC -->
        <dependency>
            <groupId>net.devh</groupId>
            <artifactId>grpc-client-spring-boot-starter</artifactId>
            <version>2.15.0.RELEASE</version>
        </dependency>
        
        <!-- gRPC Contracts (from parent project) -->
        <dependency>
            <groupId>com.fpt</groupId>
            <artifactId>grpc-contracts</artifactId>
            <version>1.0.0</version>
        </dependency>
        
        <!-- Lombok -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>
        
        <!-- Testing -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.springframework.security</groupId>
            <artifactId>spring-security-test</artifactId>
            <scope>test</scope>
        </dependency>
        
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>postgresql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>
    
    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

---

## Configuration Files

### application.yml

```yaml
spring:
  application:
    name: project-config-service
  
  datasource:
    url: jdbc:postgresql://localhost:5432/projectconfig_db
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate  # Flyway manages schema
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
    show-sql: false
  
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

server:
  port: 8083

# JWT Configuration
jwt:
  secret: ${JWT_SECRET:default_secret_key_change_in_production}

# Encryption Configuration
encryption:
  secret-key: ${ENCRYPTION_SECRET_KEY:a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6q7r8s9t0u1v2w3x4y5z6a7b8c9d0e1f2}

# Service-to-Service Authentication
service-to-service:
  allowed-services:
    - name: sync-service
      key: ${SYNC_SERVICE_KEY:s3cr3t_k3y_f0r_sync_s3rv1c3}

# gRPC Client Configuration
grpc:
  client:
    identity-service:
      address: static://localhost:9090
      negotiation-type: plaintext
      deadline-seconds: 3
    user-group-service:
      address: static://localhost:9091
      negotiation-type: plaintext
      deadline-seconds: 3

# Verification Timeouts
verification:
  jira:
    timeout-seconds: 10
  github:
    timeout-seconds: 10

# Cleanup Job
cleanup:
  retention-days: 90
  cron: "0 0 2 * * *"  # 2 AM daily

# Logging
logging:
  level:
    com.fpt.projectconfig: INFO
    org.springframework.security: DEBUG
  file:
    name: logs/project-config-service.log
  pattern:
    console: "%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
```

---

### application-dev.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/projectconfig_db_dev
  jpa:
    show-sql: true
  
logging:
  level:
    com.fpt.projectconfig: DEBUG
    org.springframework.web: DEBUG

grpc:
  client:
    identity-service:
      address: static://localhost:9090
    user-group-service:
      address: static://localhost:9091
```

---

### application-prod.yml

```yaml
spring:
  datasource:
    url: jdbc:postgresql://postgres-db:5432/projectconfig_db
  jpa:
    show-sql: false

logging:
  level:
    com.fpt.projectconfig: INFO
    org.springframework.security: WARN

grpc:
  client:
    identity-service:
      address: static://identity-service:9090
    user-group-service:
      address: static://user-group-service:9091
```

---

## Core Implementation

### 1. Entity Layer

**ProjectConfig.java**

```java
package com.fpt.projectconfig.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_configs")
@SQLRestriction("deleted_at IS NULL")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectConfig {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "group_id", nullable = false, unique = true)
    private UUID groupId;
    
    @Column(name = "jira_host_url", nullable = false, length = 255)
    private String jiraHostUrl;
    
    @Column(name = "jira_api_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String jiraApiTokenEncrypted;
    
    @Column(name = "github_repo_url", nullable = false, length = 255)
    private String githubRepoUrl;
    
    @Column(name = "github_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String githubTokenEncrypted;
    
    @Column(name = "state", nullable = false, length = 20)
    private String state = "DRAFT";  // DRAFT, VERIFIED, INVALID, DELETED
    
    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;
    
    @Column(name = "invalid_reason", columnDefinition = "TEXT")
    private String invalidReason;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }
    
    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
```

---

### 2. Repository Layer

**ProjectConfigRepository.java**

```java
package com.fpt.projectconfig.repository;

import com.fpt.projectconfig.entity.ProjectConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ProjectConfigRepository extends JpaRepository<ProjectConfig, UUID> {
    
    /**
     * Find config by group ID (auto-filters deleted_at IS NULL via @SQLRestriction)
     */
    Optional<ProjectConfig> findByGroupId(UUID groupId);
    
    /**
     * Find all configs in a specific state (excluding soft-deleted)
     */
    List<ProjectConfig> findByState(String state);
    
    /**
     * Find configs deleted before a specific date (for cleanup job)
     */
    @Query("SELECT c FROM ProjectConfig c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :cutoffDate")
    List<ProjectConfig> findDeletedConfigsOlderThan(Instant cutoffDate);
    
    /**
     * Hard delete configs older than retention period
     */
    @Modifying
    @Query("DELETE FROM ProjectConfig c WHERE c.deletedAt IS NOT NULL AND c.deletedAt < :cutoffDate")
    int hardDeleteExpiredConfigs(Instant cutoffDate);
}
```

---

### 3. Service Layer

**ProjectConfigService.java**

```java
package com.fpt.projectconfig.service;

import com.fpt.projectconfig.client.grpc.IdentityServiceGrpcClient;
import com.fpt.projectconfig.client.rest.UserGroupServiceClient;
import com.fpt.projectconfig.dto.request.CreateConfigRequest;
import com.fpt.projectconfig.dto.request.UpdateConfigRequest;
import com.fpt.projectconfig.dto.response.ConfigResponse;
import com.fpt.projectconfig.entity.ProjectConfig;
import com.fpt.projectconfig.exception.ConfigNotFoundException;
import com.fpt.projectconfig.exception.ForbiddenException;
import com.fpt.projectconfig.exception.GroupNotFoundException;
import com.fpt.projectconfig.repository.ProjectConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigService {
    
    private final ProjectConfigRepository repository;
    private final TokenEncryptionService encryptionService;
    private final TokenMaskingService maskingService;
    private final UserGroupServiceClient userGroupClient;
    private final IdentityServiceGrpcClient identityClient;
    
    /**
     * Create new project configuration
     * Business Rules:
     * 1. User must be LEADER of the group (validated via User-Group Service)
     * 2. Group can have only ONE config (enforced by UNIQUE constraint on group_id)
     * 3. Tokens are encrypted before storage
     * 4. Initial state is DRAFT
     */
    @Transactional
    public ConfigResponse createConfig(CreateConfigRequest request, Long userId) {
        log.info("User {} creating config for group {}", userId, request.groupId());
        
        // 1. Validate group exists
        var group = userGroupClient.getGroup(request.groupId());
        if (group == null) {
            throw new GroupNotFoundException(request.groupId());
        }
        
        // 2. Verify user is LEADER of the group
        if (!group.leaderId().equals(userId)) {
            throw new ForbiddenException("Only group leader can create config");
        }
        
        // 3. Check if config already exists for this group
        repository.findByGroupId(request.groupId()).ifPresent(existing -> {
            throw new IllegalStateException("Config already exists for group " + request.groupId());
        });
        
        // 4. Encrypt tokens
        String encryptedJiraToken = encryptionService.encrypt(request.jiraApiToken());
        String encryptedGithubToken = encryptionService.encrypt(request.githubToken());
        
        // 5. Create entity
        ProjectConfig config = ProjectConfig.builder()
            .groupId(request.groupId())
            .jiraHostUrl(request.jiraHostUrl())
            .jiraApiTokenEncrypted(encryptedJiraToken)
            .githubRepoUrl(request.githubRepoUrl())
            .githubTokenEncrypted(encryptedGithubToken)
            .state("DRAFT")
            .build();
        
        ProjectConfig saved = repository.save(config);
        log.info("Config {} created successfully by user {}", saved.getId(), userId);
        
        return toResponse(saved, List.of("STUDENT"));  // Mask tokens for creator
    }
    
    /**
     * Get config by ID with role-based token masking
     * Authorization:
     * - User must be member of the group OR ADMIN/LECTURER
     * - ADMIN/LECTURER see full tokens
     * - STUDENT see masked tokens
     */
    @Transactional(readOnly = true)
    public ConfigResponse getConfig(UUID id, Long userId, List<String> roles) {
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        boolean isAdmin = roles.contains("ADMIN");
        boolean isLecturer = roles.contains("LECTURER");
        
        if (!isAdmin && !isLecturer) {
            // Verify user is member of the group
            var group = userGroupClient.getGroup(config.getGroupId());
            boolean isMember = group.members().stream()
                .anyMatch(member -> member.userId().equals(userId));
            
            if (!isMember) {
                throw new ForbiddenException("You don't have access to this config");
            }
        }
        
        return toResponse(config, roles);
    }
    
    /**
     * Update config (only LEADER can update)
     * State Transition: If Jira/GitHub tokens changed → state = DRAFT
     */
    @Transactional
    public ConfigResponse updateConfig(UUID id, UpdateConfigRequest request, Long userId) {
        log.info("User {} updating config {}", userId, id);
        
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // Verify user is LEADER
        var group = userGroupClient.getGroup(config.getGroupId());
        if (!group.leaderId().equals(userId)) {
            throw new ForbiddenException("Only group leader can update config");
        }
        
        boolean tokenChanged = false;
        
        // Update Jira config
        if (request.jiraHostUrl() != null) {
            config.setJiraHostUrl(request.jiraHostUrl());
        }
        if (request.jiraApiToken() != null) {
            config.setJiraApiTokenEncrypted(encryptionService.encrypt(request.jiraApiToken()));
            tokenChanged = true;
        }
        
        // Update GitHub config
        if (request.githubRepoUrl() != null) {
            config.setGithubRepoUrl(request.githubRepoUrl());
        }
        if (request.githubToken() != null) {
            config.setGithubTokenEncrypted(encryptionService.encrypt(request.githubToken()));
            tokenChanged = true;
        }
        
        // State transition: token changed → DRAFT
        if (tokenChanged && !"DRAFT".equals(config.getState())) {
            config.setState("DRAFT");
            config.setLastVerifiedAt(null);
            config.setInvalidReason("Configuration updated, verification required");
        }
        
        ProjectConfig updated = repository.save(config);
        log.info("Config {} updated successfully", id);
        
        return toResponse(updated, List.of("STUDENT"));
    }
    
    /**
     * Soft delete config
     */
    @Transactional
    public void softDeleteConfig(UUID id, Long userId) {
        log.warn("User {} soft-deleting config {}", userId, id);
        
        ProjectConfig config = repository.findById(id)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        // Verify user exists in Identity Service
        identityClient.verifyUserExists(userId);
        
        config.setState("DELETED");
        config.setDeletedAt(Instant.now());
        config.setDeletedBy(userId);
        
        repository.save(config);
        log.info("Config {} soft-deleted by user {}", id, userId);
    }
    
    /**
     * Restore soft-deleted config (ADMIN only)
     */
    @Transactional
    public ConfigResponse restoreConfig(UUID id) {
        // Must query without @SQLRestriction filter
        ProjectConfig config = repository.findById(id)
            .filter(c -> c.getDeletedAt() != null)
            .orElseThrow(() -> new ConfigNotFoundException(id));
        
        config.setState("DRAFT");
        config.setDeletedAt(null);
        config.setDeletedBy(null);
        config.setInvalidReason("Config restored, verification required");
        
        ProjectConfig restored = repository.save(config);
        log.warn("Config {} restored by ADMIN", id);
        
        return toResponse(restored, List.of("ADMIN"));
    }
    
    /**
     * Convert entity to response DTO with token masking
     */
    private ConfigResponse toResponse(ProjectConfig config, List<String> roles) {
        boolean canSeeFullTokens = roles.contains("ADMIN") || roles.contains("LECTURER");
        
        return ConfigResponse.builder()
            .id(config.getId())
            .groupId(config.getGroupId())
            .jiraHostUrl(config.getJiraHostUrl())
            .jiraApiToken(maskingService.maskToken(config.getJiraApiTokenEncrypted(), canSeeFullTokens))
            .githubRepoUrl(config.getGithubRepoUrl())
            .githubToken(maskingService.maskToken(config.getGithubTokenEncrypted(), canSeeFullTokens))
            .state(config.getState())
            .lastVerifiedAt(config.getLastVerifiedAt())
            .invalidReason(config.getInvalidReason())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .build();
    }
}
```

---

**TokenEncryptionService.java** (see Security Design for full implementation)

```java
package com.fpt.projectconfig.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

@Service
@Slf4j
public class TokenEncryptionService {
    
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;
    
    private final SecretKeySpec secretKey;
    private final SecureRandom secureRandom;
    
    public TokenEncryptionService(@Value("${encryption.secret-key}") String keyHex) {
        byte[] keyBytes = hexStringToByteArray(keyHex);
        if (keyBytes.length != 32) {
            throw new IllegalArgumentException("Encryption key must be 256 bits");
        }
        this.secretKey = new SecretKeySpec(keyBytes, "AES");
        this.secureRandom = new SecureRandom();
    }
    
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[GCM_IV_LENGTH];
            secureRandom.nextBytes(iv);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec);
            
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            
            String ivBase64 = Base64.getEncoder().encodeToString(iv);
            String ciphertextBase64 = Base64.getEncoder().encodeToString(ciphertext);
            
            return ivBase64 + ":" + ciphertextBase64;
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new RuntimeException("Encryption failed", e);
        }
    }
    
    public String decrypt(String encrypted) {
        try {
            String[] parts = encrypted.split(":");
            byte[] iv = Base64.getDecoder().decode(parts[0]);
            byte[] ciphertext = Base64.getDecoder().decode(parts[1]);
            
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            GCMParameterSpec spec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec);
            
            byte[] plaintext = cipher.doFinal(ciphertext);
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new RuntimeException("Decryption failed", e);
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

**TokenMaskingService.java**

```java
package com.fpt.projectconfig.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TokenMaskingService {
    
    private final TokenEncryptionService encryptionService;
    
    public String maskToken(String encryptedToken, boolean canSeeFullToken) {
        if (canSeeFullToken) {
            return encryptionService.decrypt(encryptedToken);
        }
        
        String decrypted = encryptionService.decrypt(encryptedToken);
        
        if (decrypted.startsWith("ATATT")) {
            // Jira token: show only last 4 characters
            return "***" + decrypted.substring(decrypted.length() - 4);
        } else if (decrypted.startsWith("ghp_")) {
            // GitHub token: show prefix + last 4 characters
            return "ghp_***" + decrypted.substring(decrypted.length() - 4);
        } else {
            return "***";
        }
    }
}
```

---

**JiraVerificationService.java**

```java
package com.fpt.projectconfig.service;

import com.fpt.projectconfig.entity.ProjectConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
@RequiredArgsConstructor
@Slf4j
public class JiraVerificationService {
    
    private final TokenEncryptionService encryptionService;
    private final RestTemplate restTemplate;
    
    @Value("${verification.jira.timeout-seconds}")
    private int timeoutSeconds;
    
    /**
     * Verify Jira connection by calling /rest/api/3/myself
     * Returns error message if verification fails
     */
    public String verifyConnection(ProjectConfig config) {
        try {
            String decryptedToken = encryptionService.decrypt(config.getJiraApiTokenEncrypted());
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + decryptedToken);
            headers.set("Content-Type", "application/json");
            
            String url = config.getJiraHostUrl() + "/rest/api/3/myself";
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Jira verification successful for config {}", config.getId());
                return null;  // Success
            } else {
                return "Jira API returned status " + response.getStatusCode();
            }
            
        } catch (Exception e) {
            log.error("Jira verification failed for config {}", config.getId(), e);
            return "Jira verification failed: " + e.getMessage();
        }
    }
}
```

---

**GitHubVerificationService.java**

```java
package com.fpt.projectconfig.service;

import com.fpt.projectconfig.entity.ProjectConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
@RequiredArgsConstructor
@Slf4j
public class GitHubVerificationService {
    
    private final TokenEncryptionService encryptionService;
    private final RestTemplate restTemplate;
    
    @Value("${verification.github.timeout-seconds}")
    private int timeoutSeconds;
    
    /**
     * Verify GitHub connection by calling /repos/{owner}/{repo}
     * Returns error message if verification fails
     */
    public String verifyConnection(ProjectConfig config) {
        try {
            String decryptedToken = encryptionService.decrypt(config.getGithubTokenEncrypted());
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + decryptedToken);
            headers.set("Accept", "application/vnd.github+json");
            
            // Extract owner/repo from URL: https://github.com/{owner}/{repo}
            String repoPath = config.getGithubRepoUrl().replace("https://github.com/", "");
            String url = "https://api.github.com/repos/" + repoPath;
            
            ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.GET,
                new HttpEntity<>(headers),
                String.class
            );
            
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("GitHub verification successful for config {}", config.getId());
                return null;  // Success
            } else {
                return "GitHub API returned status " + response.getStatusCode();
            }
            
        } catch (Exception e) {
            log.error("GitHub verification failed for config {}", config.getId(), e);
            return "GitHub verification failed: " + e.getMessage();
        }
    }
}
```

---

### 4. Controller Layer

**ProjectConfigController.java**

```java
package com.fpt.projectconfig.controller;

import com.fpt.projectconfig.dto.request.CreateConfigRequest;
import com.fpt.projectconfig.dto.request.UpdateConfigRequest;
import com.fpt.projectconfig.dto.response.ConfigResponse;
import com.fpt.projectconfig.service.ProjectConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/project-configs")
@RequiredArgsConstructor
public class ProjectConfigController {
    
    private final ProjectConfigService service;
    
    @PostMapping
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<ConfigResponse> createConfig(
            @RequestBody @Valid CreateConfigRequest request,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        ConfigResponse response = service.createConfig(request, userId);
        
        return ResponseEntity.status(201).body(response);
    }
    
    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ConfigResponse> getConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        List<String> roles = getRoles(authentication);
        
        ConfigResponse response = service.getConfig(id, userId, roles);
        return ResponseEntity.ok(response);
    }
    
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<ConfigResponse> updateConfig(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateConfigRequest request,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        ConfigResponse response = service.updateConfig(id, request, userId);
        
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<Void> deleteConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        Long userId = (Long) authentication.getPrincipal();
        service.softDeleteConfig(id, userId);
        
        return ResponseEntity.noContent().build();
    }
    
    @PostMapping("/{id}/verify")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'LECTURER', 'LEADER')")
    public ResponseEntity<ConfigResponse> verifyConfig(
            @PathVariable UUID id,
            Authentication authentication) {
        
        ConfigResponse response = service.verifyConfig(id);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/admin/project-configs/{id}/restore")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<ConfigResponse> restoreConfig(@PathVariable UUID id) {
        ConfigResponse response = service.restoreConfig(id);
        return ResponseEntity.ok(response);
    }
    
    private List<String> getRoles(Authentication authentication) {
        return authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toList());
    }
}
```

---

## Testing Strategy

### Unit Tests

**TokenEncryptionServiceTest.java**

```java
@SpringBootTest
class TokenEncryptionServiceTest {
    
    @Autowired
    private TokenEncryptionService encryptionService;
    
    @Test
    void testEncryptDecrypt() {
        String original = "ATATT3xFfGF0jk2lM5nB6vC7xZ8aS1dF2gH3jK4lM5nB6vC7xZ8aS1dF2gH3jK4";
        
        String encrypted = encryptionService.encrypt(original);
        String decrypted = encryptionService.decrypt(encrypted);
        
        assertEquals(original, decrypted);
    }
    
    @Test
    void testEncryptionProducesDifferentCiphertexts() {
        String plaintext = "ghp_k3lM5nB6vC7xZ8aS1dF2gH3jK4lM5nB6v";
        
        String encrypted1 = encryptionService.encrypt(plaintext);
        String encrypted2 = encryptionService.encrypt(plaintext);
        
        assertNotEquals(encrypted1, encrypted2);  // Different IVs
    }
}
```

---

### Integration Tests

**ProjectConfigIntegrationTest.java**

```java
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ProjectConfigIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    @WithMockUser(authorities = {"LEADER"})
    void testCreateConfig() throws Exception {
        String requestBody = """
            {
                "groupId": "550e8400-e29b-41d4-a716-446655440000",
                "jiraHostUrl": "https://test.atlassian.net",
                "jiraApiToken": "ATATT3xFfGF0...",
                "githubRepoUrl": "https://github.com/test/repo",
                "githubToken": "ghp_k3lM5nB6vC7xZ8aS1dF2gH3jK4lM5nB6v"
            }
            """;
        
        mockMvc.perform(post("/api/project-configs")
                .contentType(MediaType.APPLICATION_JSON)
                .content(requestBody))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.state").value("DRAFT"));
    }
}
```

---

## Deployment

### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

COPY target/project-config-service-1.0.0.jar app.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "app.jar"]
```

---

### docker-compose.yml

```yaml
version: '3.8'

services:
  project-config-service:
    build: ./project-config-service
    ports:
      - "8083:8083"
    environment:
      - SPRING_PROFILES_ACTIVE=prod
      - DB_USERNAME=${DB_USERNAME}
      - DB_PASSWORD=${DB_PASSWORD}
      - JWT_SECRET=${JWT_SECRET}
      - ENCRYPTION_SECRET_KEY=${ENCRYPTION_SECRET_KEY}
      - SYNC_SERVICE_KEY=${SYNC_SERVICE_KEY}
    depends_on:
      - postgres
      - identity-service
      - user-group-service
  
  postgres:
    image: postgres:15-alpine
    environment:
      - POSTGRES_DB=projectconfig_db
      - POSTGRES_USER=${DB_USERNAME}
      - POSTGRES_PASSWORD=${DB_PASSWORD}
    volumes:
      - postgres-data:/var/lib/postgresql/data

volumes:
  postgres-data:
```

---

**END OF IMPLEMENTATION GUIDE**

All 5 AI-codable documents are now complete.
