# Sync Service - gRPC Integration Guide

**Document:** Project Config Service Integration  
**Version:** 1.0  
**Date:** February 5, 2026

---

## 1. Overview

Sync Service cần gọi Project Config Service qua **gRPC** để lấy **decrypted API tokens** (Jira và GitHub). Document này mô tả chi tiết cách implement gRPC client và xác thực service-to-service.

---

## 2. gRPC Service Contract

### 2.1 Proto Definition

**File:** `project_config.proto` (from Project Config Service)

```protobuf
syntax = "proto3";

package projectconfig;

option java_package = "com.fpt.swp391.grpc.projectconfig";
option java_multiple_files = true;

// Internal API - Chỉ cho các service nội bộ
service ProjectConfigInternalService {
  // Lấy config với tokens đã decrypt (chỉ dành cho Sync Service)
  rpc InternalGetDecryptedConfig(InternalGetDecryptedConfigRequest) returns (InternalGetDecryptedConfigResponse);
}

message InternalGetDecryptedConfigRequest {
  int64 config_id = 1;
}

message InternalGetDecryptedConfigResponse {
  int64 config_id = 1;
  int64 group_id = 2;
  string jira_host_url = 3;
  string jira_api_token = 4;           // DECRYPTED
  string jira_project_key = 5;
  string github_repo_url = 6;
  string github_access_token = 7;      // DECRYPTED
  string state = 8;                    // DRAFT, VERIFIED, INVALID, DELETED
  string created_at = 9;
  string updated_at = 10;
}
```

**Security Note:** `InternalGetDecryptedConfig` chỉ accessible với service authentication (x-service-name + x-service-key).

---

## 3. Maven Dependencies

### 3.1 pom.xml

```xml
<properties>
    <grpc.version>1.60.0</grpc.version>
    <protobuf.version>3.25.1</protobuf.version>
</properties>

<dependencies>
    <!-- gRPC Dependencies -->
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-netty-shaded</artifactId>
        <version>${grpc.version}</version>
        <scope>runtime</scope>
    </dependency>
    
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-protobuf</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    
    <dependency>
        <groupId>io.grpc</groupId>
        <artifactId>grpc-stub</artifactId>
        <version>${grpc.version}</version>
    </dependency>
    
    <!-- Protobuf -->
    <dependency>
        <groupId>com.google.protobuf</groupId>
        <artifactId>protobuf-java</artifactId>
        <version>${protobuf.version}</version>
    </dependency>
    
    <!-- Annotation for generated code -->
    <dependency>
        <groupId>javax.annotation</groupId>
        <artifactId>javax.annotation-api</artifactId>
        <version>1.3.2</version>
    </dependency>
</dependencies>

<build>
    <extensions>
        <extension>
            <groupId>kr.motd.maven</groupId>
            <artifactId>os-maven-plugin</artifactId>
            <version>1.7.1</version>
        </extension>
    </extensions>
    
    <plugins>
        <!-- Protobuf Compiler -->
        <plugin>
            <groupId>org.xolstice.maven.plugins</groupId>
            <artifactId>protobuf-maven-plugin</artifactId>
            <version>0.6.1</version>
            <configuration>
                <protocArtifact>com.google.protobuf:protoc:${protobuf.version}:exe:${os.detected.classifier}</protocArtifact>
                <pluginId>grpc-java</pluginId>
                <pluginArtifact>io.grpc:protoc-gen-grpc-java:${grpc.version}:exe:${os.detected.classifier}</pluginArtifact>
            </configuration>
            <executions>
                <execution>
                    <goals>
                        <goal>compile</goal>
                        <goal>compile-custom</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## 4. Project Structure

```
sync-service/
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com/fpt/swp391/sync/
│   │   │       ├── client/
│   │   │       │   ├── ProjectConfigGrpcClient.java
│   │   │       │   └── GrpcClientInterceptor.java
│   │   │       ├── config/
│   │   │       │   └── GrpcClientConfig.java
│   │   │       └── dto/
│   │   │           └── DecryptedConfigDto.java
│   │   └── proto/
│   │       └── project_config.proto         # Copy from project-config-service
│   └── test/
└── pom.xml
```

**Step 1:** Copy `project_config.proto` từ project-config-service vào `src/main/proto/`.

**Step 2:** Run `mvn clean compile` để generate gRPC classes:
- `InternalGetDecryptedConfigRequest`
- `InternalGetDecryptedConfigResponse`
- `ProjectConfigInternalServiceGrpc` (stub classes)

---

## 5. Configuration

### 5.1 application.yml

```yaml
# gRPC Client Configuration
grpc:
  client:
    project-config-service:
      address: static://localhost:9092        # Project Config Service gRPC port
      negotiationType: PLAINTEXT              # Use TLS in production
      enableKeepAlive: true
      keepAliveTime: 30s
      keepAliveTimeout: 10s
      maxInboundMessageSize: 4MB

# Service Authentication
service:
  auth:
    service-name: sync-service
    service-key: ${SERVICE_AUTH_KEY}           # From environment variable: sync-service-secret-key-2026
```

**Environment Variables:**
```bash
export SERVICE_AUTH_KEY=sync-service-secret-key-2026
```

**Security:** Service key phải match với config trong Project Config Service:

```java
// In Project Config Service - ServiceAuthInterceptor.java
private static final Map<String, String> VALID_SERVICES = Map.of(
    "sync-service", "sync-service-secret-key-2026",
    "ai-service", "ai-service-secret-key-2026",
    "reporting-service", "reporting-service-secret-key-2026"
);
```

---

## 6. gRPC Client Implementation

### 6.1 GrpcClientConfig

```java
package com.fpt.swp391.sync.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {
    
    @Value("${grpc.client.project-config-service.address}")
    private String projectConfigServiceAddress;
    
    @Bean
    public ManagedChannel projectConfigChannel() {
        String[] parts = projectConfigServiceAddress.replace("static://", "").split(":");
        String host = parts[0];
        int port = Integer.parseInt(parts[1]);
        
        return ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()  // Use TLS in production: .useTransportSecurity()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .maxInboundMessageSize(4 * 1024 * 1024)  // 4MB
                .build();
    }
}
```

---

### 6.2 GrpcClientInterceptor (Service Authentication)

```java
package com.fpt.swp391.sync.client;

import io.grpc.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class GrpcClientInterceptor implements ClientInterceptor {
    
    @Value("${service.auth.service-name}")
    private String serviceName;
    
    @Value("${service.auth.service-key}")
    private String serviceKey;
    
    @Override
    public <ReqT, RespT> ClientCall<ReqT, RespT> interceptCall(
            MethodDescriptor<ReqT, RespT> method,
            CallOptions callOptions,
            Channel next) {
        
        log.debug("Intercepting gRPC call to method: {}", method.getFullMethodName());
        
        return new ForwardingClientCall.SimpleForwardingClientCall<ReqT, RespT>(
                next.newCall(method, callOptions)) {
            
            @Override
            public void start(Listener<RespT> responseListener, Metadata headers) {
                // Add service authentication metadata
                headers.put(
                    Metadata.Key.of("x-service-name", Metadata.ASCII_STRING_MARSHALLER),
                    serviceName
                );
                headers.put(
                    Metadata.Key.of("x-service-key", Metadata.ASCII_STRING_MARSHALLER),
                    serviceKey
                );
                
                log.debug("Added service authentication headers: service-name={}", serviceName);
                
                super.start(new ForwardingClientCallListener.SimpleForwardingClientCallListener<RespT>(responseListener) {
                    @Override
                    public void onHeaders(Metadata headers) {
                        log.debug("Received response headers from server");
                        super.onHeaders(headers);
                    }
                    
                    @Override
                    public void onClose(Status status, Metadata trailers) {
                        if (status.isOk()) {
                            log.debug("gRPC call completed successfully");
                        } else {
                            log.error("gRPC call failed: {} - {}", status.getCode(), status.getDescription());
                        }
                        super.onClose(status, trailers);
                    }
                }, headers);
            }
        };
    }
}
```

---

### 6.3 ProjectConfigGrpcClient

```java
package com.fpt.swp391.sync.client;

import com.fpt.swp391.grpc.projectconfig.*;
import com.fpt.swp391.sync.dto.DecryptedConfigDto;
import com.fpt.swp391.sync.exception.ConfigNotFoundException;
import com.fpt.swp391.sync.exception.GrpcCallException;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigGrpcClient {
    
    private final ManagedChannel projectConfigChannel;
    private final GrpcClientInterceptor grpcClientInterceptor;
    
    private ProjectConfigInternalServiceGrpc.ProjectConfigInternalServiceBlockingStub stub;
    
    @PostConstruct
    public void init() {
        // Create stub with interceptor for authentication
        this.stub = ProjectConfigInternalServiceGrpc.newBlockingStub(
                ClientInterceptors.intercept(projectConfigChannel, grpcClientInterceptor)
        );
        log.info("ProjectConfigGrpcClient initialized with service authentication");
    }
    
    /**
     * Get decrypted configuration from Project Config Service
     * 
     * @param configId Project config ID
     * @return DecryptedConfigDto with plaintext API tokens
     * @throws ConfigNotFoundException if config not found
     * @throws GrpcCallException if gRPC call fails
     */
    public DecryptedConfigDto getDecryptedConfig(Long configId) {
        log.info("Fetching decrypted config for configId={}", configId);
        
        try {
            InternalGetDecryptedConfigRequest request = InternalGetDecryptedConfigRequest.newBuilder()
                    .setConfigId(configId)
                    .build();
            
            InternalGetDecryptedConfigResponse response = stub.internalGetDecryptedConfig(request);
            
            log.info("Successfully fetched decrypted config for configId={}, state={}", configId, response.getState());
            
            return mapToDto(response);
            
        } catch (StatusRuntimeException e) {
            handleGrpcError(e, configId);
            throw new GrpcCallException("Unexpected error after handling gRPC exception");
        }
    }
    
    private DecryptedConfigDto mapToDto(InternalGetDecryptedConfigResponse response) {
        DecryptedConfigDto dto = new DecryptedConfigDto();
        dto.setConfigId(response.getConfigId());
        dto.setGroupId(response.getGroupId());
        dto.setJiraHostUrl(response.getJiraHostUrl());
        dto.setJiraApiToken(response.getJiraApiToken());  // DECRYPTED
        dto.setJiraProjectKey(response.getJiraProjectKey());
        dto.setGithubRepoUrl(response.getGithubRepoUrl());
        dto.setGithubAccessToken(response.getGithubAccessToken());  // DECRYPTED
        dto.setState(response.getState());
        return dto;
    }
    
    private void handleGrpcError(StatusRuntimeException e, Long configId) {
        Status.Code code = e.getStatus().getCode();
        String description = e.getStatus().getDescription();
        
        log.error("gRPC call failed: code={}, description={}, configId={}", code, description, configId);
        
        switch (code) {
            case NOT_FOUND:
                throw new ConfigNotFoundException("CONFIG_NOT_FOUND", 
                        String.format("Config with ID %d not found", configId));
            
            case PERMISSION_DENIED:
                throw new GrpcCallException("SERVICE_AUTH_FAILED", 
                        "Service authentication failed. Check x-service-name and x-service-key.");
            
            case UNAUTHENTICATED:
                throw new GrpcCallException("SERVICE_NOT_AUTHENTICATED", 
                        "Service not authenticated. Missing authentication metadata.");
            
            case UNAVAILABLE:
                throw new GrpcCallException("SERVICE_UNAVAILABLE", 
                        "Project Config Service is unavailable. Check network and service status.");
            
            case DEADLINE_EXCEEDED:
                throw new GrpcCallException("TIMEOUT", 
                        "gRPC call timeout. Project Config Service did not respond in time.");
            
            default:
                throw new GrpcCallException("GRPC_ERROR", 
                        String.format("gRPC error: %s - %s", code, description));
        }
    }
    
    @PreDestroy
    public void cleanup() {
        if (projectConfigChannel != null && !projectConfigChannel.isShutdown()) {
            log.info("Shutting down gRPC channel to Project Config Service");
            projectConfigChannel.shutdown();
            try {
                if (!projectConfigChannel.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    log.warn("gRPC channel did not terminate gracefully, forcing shutdown");
                    projectConfigChannel.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("Interrupted while waiting for channel shutdown", e);
                projectConfigChannel.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
```

---

### 6.4 DecryptedConfigDto

```java
package com.fpt.swp391.sync.dto;

import lombok.Data;

@Data
public class DecryptedConfigDto {
    private Long configId;
    private Long groupId;
    private String jiraHostUrl;
    private String jiraApiToken;       // DECRYPTED - plaintext
    private String jiraProjectKey;
    private String githubRepoUrl;
    private String githubAccessToken;  // DECRYPTED - plaintext
    private String state;              // DRAFT, VERIFIED, INVALID, DELETED
}
```

---

## 7. Usage in Sync Logic

### 7.1 SyncScheduler Integration

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SyncScheduler {
    
    private final ProjectConfigGrpcClient projectConfigClient;
    private final JiraApiClient jiraApiClient;
    private final GitHubApiClient gitHubApiClient;
    
    @Scheduled(cron = "${sync.scheduler.jira-issues-cron}")
    public void syncJiraIssues() {
        log.info("Starting scheduled Jira issues sync");
        
        List<Long> verifiedConfigIds = getVerifiedConfigIds();
        
        for (Long configId : verifiedConfigIds) {
            try {
                syncJiraIssuesForConfig(configId);
            } catch (Exception e) {
                log.error("Failed to sync Jira issues for config {}: {}", configId, e.getMessage(), e);
            }
        }
        
        log.info("Completed Jira issues sync");
    }
    
    private void syncJiraIssuesForConfig(Long configId) {
        log.info("Syncing Jira issues for config {}", configId);
        
        // Step 1: Get decrypted config via gRPC
        DecryptedConfigDto config = projectConfigClient.getDecryptedConfig(configId);
        
        // Step 2: Validate state
        if (!"VERIFIED".equals(config.getState())) {
            log.warn("Skipping config {} with state {}", configId, config.getState());
            return;
        }
        
        // Step 3: Fetch issues from Jira
        List<JiraIssueDto> issues = jiraApiClient.fetchIssues(
                config.getJiraHostUrl(),
                config.getJiraApiToken(),    // Use decrypted token
                config.getJiraProjectKey()
        );
        
        log.info("Fetched {} issues from Jira for config {}", issues.size(), configId);
        
        // Step 4: Transform and save
        // ... (see SYNC_LOGIC_DESIGN.md)
    }
}
```

---

## 8. Security Considerations

### 8.1 Token Handling Best Practices

**CRITICAL RULES:**

1. **NEVER log decrypted tokens:**
```java
// ❌ BAD - Exposes token in logs
log.info("Fetched config: {}", config);

// ✅ GOOD - Mask tokens
log.info("Fetched config: configId={}, jiraHost={}, state={}", 
    config.getConfigId(), config.getJiraHostUrl(), config.getState());
```

2. **NEVER store decrypted tokens in database:**
```java
// ❌ BAD
syncJob.setJiraToken(config.getJiraApiToken());
syncJobRepository.save(syncJob);

// ✅ GOOD - Only store in memory
String jiraToken = config.getJiraApiToken();
// Use immediately
List<JiraIssueDto> issues = jiraApiClient.fetchIssues(hostUrl, jiraToken, projectKey);
// Token garbage collected after method completes
```

3. **NEVER pass tokens in HTTP headers/query params if logged:**
```java
// ❌ BAD - If using RestTemplate with interceptor logging
restTemplate.exchange(url, HttpMethod.GET, 
    new HttpEntity<>(createHeaders("Authorization", "Bearer " + token)), ...);

// ✅ GOOD - Use WebClient with selective logging
webClient.get().uri(url)
    .header("Authorization", "Bearer " + token)
    .retrieve()
    .bodyToMono(String.class);
```

4. **Discard tokens immediately after use:**
```java
public void syncData(Long configId) {
    DecryptedConfigDto config = projectConfigClient.getDecryptedConfig(configId);
    
    try {
        // Use tokens immediately
        fetchAndSaveData(config);
    } finally {
        // Clear sensitive data (optional, GC will handle)
        config.setJiraApiToken(null);
        config.setGithubAccessToken(null);
    }
}
```

---

### 8.2 Service Authentication Verification

**Testing Service Auth:**

```bash
# Start Project Config Service
cd project-config-service
mvn spring-boot:run

# Test gRPC call with grpcurl
grpcurl -plaintext \
  -d '{"config_id": 123}' \
  -H 'x-service-name: sync-service' \
  -H 'x-service-key: sync-service-secret-key-2026' \
  localhost:9092 \
  projectconfig.ProjectConfigInternalService/InternalGetDecryptedConfig
```

**Expected Response:**
```json
{
  "config_id": "123",
  "group_id": "456",
  "jira_host_url": "https://example.atlassian.net",
  "jira_api_token": "decrypted-plaintext-token",
  "jira_project_key": "SWP391",
  "github_repo_url": "https://github.com/org/repo",
  "github_access_token": "ghp_decrypted_token",
  "state": "VERIFIED"
}
```

**Test Invalid Service Key:**
```bash
grpcurl -plaintext \
  -d '{"config_id": 123}' \
  -H 'x-service-name: sync-service' \
  -H 'x-service-key: wrong-key' \
  localhost:9092 \
  projectconfig.ProjectConfigInternalService/InternalGetDecryptedConfig
```

**Expected Error:**
```
ERROR:
  Code: PermissionDenied
  Message: Invalid service authentication
```

---

## 9. Error Handling & Retry

### 9.1 Retry Configuration (Resilience4j)

```yaml
resilience4j:
  retry:
    instances:
      grpcCall:
        maxAttempts: 3
        waitDuration: 1s
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - io.grpc.StatusRuntimeException
        ignoreExceptions:
          - com.fpt.swp391.sync.exception.ConfigNotFoundException
```

### 9.2 Apply Retry to gRPC Client

```java
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectConfigGrpcClient {
    
    @Retry(name = "grpcCall", fallbackMethod = "getDecryptedConfigFallback")
    public DecryptedConfigDto getDecryptedConfig(Long configId) {
        // ... existing implementation
    }
    
    private DecryptedConfigDto getDecryptedConfigFallback(Long configId, Exception e) {
        log.error("All retry attempts failed for configId={}: {}", configId, e.getMessage());
        
        if (e instanceof ConfigNotFoundException) {
            throw (ConfigNotFoundException) e;  // Don't retry NOT_FOUND
        }
        
        throw new GrpcCallException("GRPC_CALL_FAILED_AFTER_RETRIES", 
                String.format("Failed to get decrypted config after retries: %s", e.getMessage()));
    }
}
```

---

## 10. Testing

### 10.1 Unit Test with Mock gRPC

```java
@ExtendWith(MockitoExtension.class)
public class ProjectConfigGrpcClientTest {
    
    @Mock
    private ManagedChannel mockChannel;
    
    @Mock
    private ProjectConfigInternalServiceGrpc.ProjectConfigInternalServiceBlockingStub mockStub;
    
    @InjectMocks
    private ProjectConfigGrpcClient client;
    
    @Test
    public void testGetDecryptedConfig_success() {
        // Given
        Long configId = 123L;
        InternalGetDecryptedConfigResponse response = InternalGetDecryptedConfigResponse.newBuilder()
                .setConfigId(configId)
                .setJiraHostUrl("https://example.atlassian.net")
                .setJiraApiToken("decrypted-token")
                .setJiraProjectKey("SWP391")
                .setState("VERIFIED")
                .build();
        
        when(mockStub.internalGetDecryptedConfig(any())).thenReturn(response);
        
        // When
        DecryptedConfigDto result = client.getDecryptedConfig(configId);
        
        // Then
        assertThat(result.getConfigId()).isEqualTo(configId);
        assertThat(result.getJiraApiToken()).isEqualTo("decrypted-token");
        assertThat(result.getState()).isEqualTo("VERIFIED");
    }
    
    @Test
    public void testGetDecryptedConfig_notFound() {
        // Given
        Long configId = 999L;
        when(mockStub.internalGetDecryptedConfig(any()))
                .thenThrow(new StatusRuntimeException(Status.NOT_FOUND));
        
        // When & Then
        assertThatThrownBy(() -> client.getDecryptedConfig(configId))
                .isInstanceOf(ConfigNotFoundException.class)
                .hasMessageContaining("CONFIG_NOT_FOUND");
    }
}
```

---

### 10.2 Integration Test with Testcontainers

```java
@SpringBootTest
@Testcontainers
public class SyncSchedulerGrpcIntegrationTest {
    
    @Container
    static GenericContainer<?> projectConfigService = new GenericContainer<>("samt/project-config-service:latest")
            .withExposedPorts(9092)
            .withEnv("SPRING_PROFILES_ACTIVE", "test");
    
    @DynamicPropertySource
    static void configureGrpcClient(DynamicPropertyRegistry registry) {
        String grpcAddress = "static://" + projectConfigService.getHost() + ":" 
                + projectConfigService.getMappedPort(9092);
        registry.add("grpc.client.project-config-service.address", () -> grpcAddress);
    }
    
    @Autowired
    private ProjectConfigGrpcClient grpcClient;
    
    @Test
    public void testGetDecryptedConfig_integration() {
        // Setup test data in Project Config Service database
        // ...
        
        // When
        DecryptedConfigDto config = grpcClient.getDecryptedConfig(123L);
        
        // Then
        assertThat(config).isNotNull();
        assertThat(config.getJiraApiToken()).isNotBlank();
    }
}
```

---

## 11. Monitoring & Observability

### 11.1 Custom Metrics

```java
@Component
@RequiredArgsConstructor
public class ProjectConfigGrpcClient {
    
    private final MeterRegistry meterRegistry;
    
    private Counter grpcCallSuccessCounter;
    private Counter grpcCallFailureCounter;
    private Timer grpcCallDurationTimer;
    
    @PostConstruct
    public void initMetrics() {
        grpcCallSuccessCounter = meterRegistry.counter("grpc.client.calls", 
                "service", "project-config", 
                "status", "success");
        
        grpcCallFailureCounter = meterRegistry.counter("grpc.client.calls", 
                "service", "project-config", 
                "status", "failure");
        
        grpcCallDurationTimer = meterRegistry.timer("grpc.client.duration", 
                "service", "project-config");
    }
    
    public DecryptedConfigDto getDecryptedConfig(Long configId) {
        long startTime = System.currentTimeMillis();
        
        try {
            DecryptedConfigDto result = // ... gRPC call
            
            grpcCallSuccessCounter.increment();
            return result;
            
        } catch (Exception e) {
            grpcCallFailureCounter.increment();
            throw e;
            
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            grpcCallDurationTimer.record(duration, TimeUnit.MILLISECONDS);
        }
    }
}
```

### 11.2 Prometheus Metrics

**Exposed Metrics:**
- `grpc_client_calls_total{service="project-config", status="success|failure"}` - Total gRPC calls
- `grpc_client_duration_seconds{service="project-config"}` - Call duration histogram

**Query Examples:**
```promql
# Success rate
sum(rate(grpc_client_calls_total{status="success"}[5m])) 
/ 
sum(rate(grpc_client_calls_total[5m]))

# Average latency
avg(grpc_client_duration_seconds)

# P99 latency
histogram_quantile(0.99, grpc_client_duration_seconds_bucket)
```

---

## 12. Production Considerations

### 12.1 TLS Configuration

**Enable TLS in production:**

```yaml
grpc:
  client:
    project-config-service:
      address: static://project-config-service.prod.svc.cluster.local:9092
      negotiationType: TLS
      certificatePath: /etc/ssl/certs/project-config-ca.crt
```

```java
@Bean
public ManagedChannel projectConfigChannel() {
    SslContext sslContext = GrpcSslContexts.forClient()
            .trustManager(new File(certificatePath))
            .build();
    
    return NettyChannelBuilder.forAddress(host, port)
            .sslContext(sslContext)
            .build();
}
```

---

### 12.2 Load Balancing

**For multiple Project Config Service instances:**

```yaml
grpc:
  client:
    project-config-service:
      address: dns:///project-config-service.prod.svc.cluster.local:9092
      negotiationType: TLS
      loadBalancerPolicy: round_robin
```

---

### 12.3 Circuit Breaker

```yaml
resilience4j:
  circuitbreaker:
    instances:
      grpcCall:
        slidingWindowSize: 100
        failureRateThreshold: 50
        waitDurationInOpenState: 60s
        permittedNumberOfCallsInHalfOpenState: 10
```

```java
@CircuitBreaker(name = "grpcCall", fallbackMethod = "getDecryptedConfigFallback")
@Retry(name = "grpcCall")
public DecryptedConfigDto getDecryptedConfig(Long configId) {
    // ... existing implementation
}
```

---

## Summary

**Key Points:**

1. **Service Authentication:** Sync Service PHẢI gửi `x-service-name` và `x-service-key` trong gRPC metadata
2. **Token Security:** Decrypted tokens CHỈ lưu trong memory, không log, không lưu DB
3. **Error Handling:** Map gRPC Status codes → domain exceptions (NOT_FOUND, PERMISSION_DENIED, UNAVAILABLE)
4. **Resilience:** Apply Retry + Circuit Breaker patterns cho gRPC calls
5. **Monitoring:** Track success rate, latency, failure rate qua Prometheus metrics
6. **TLS:** Enable TLS encryption trong production environment
7. **Testing:** Unit tests với mock stubs, integration tests với Testcontainers

**Next:** Xem [issues_sync_service.md](issues_sync_service.md) để review các vấn đề cần làm rõ.
