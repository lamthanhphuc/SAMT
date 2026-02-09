# gRPC Setup Guide - Project Config Service

## Overview

Service đã được implement với **business logic hoàn chỉnh**. User cần setup gRPC server boilerplate để có thể chạy service.

## Quick Start Checklist

- [ ] Copy proto template
- [ ] Configure protobuf-maven-plugin
- [ ] Generate Protobuf classes
- [ ] Uncomment implementation code
- [ ] Add gRPC server dependency
- [ ] Annotate service với @GrpcService
- [ ] Run và test

---

## Step 1: Copy Proto Template

```bash
cp src/main/proto/project_config.proto.template src/main/proto/project_config.proto
```

File `.proto` đã define đầy đủ:
- 7 gRPC methods (UC30-35 + Internal API)
- Request/Response messages
- Enum types (ConfigState)

---

## Step 2: Configure Maven Plugin

Thêm vào `pom.xml` (trong `<build><plugins>`):

```xml
<!-- Protobuf compiler -->
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
```

Thêm extension (trong `<build><extensions>`):

```xml
<extension>
    <groupId>kr.motd.maven</groupId>
    <artifactId>os-maven-plugin</artifactId>
    <version>1.7.1</version>
</extension>
```

**Lưu ý:** Plugin này đã có sẵn trong pom.xml, chỉ cần verify.

---

## Step 3: Generate Protobuf Classes

```bash
mvn clean compile
```

Generated classes sẽ nằm trong `target/generated-sources/protobuf/`:
- `ProjectConfigServiceGrpc.java` - Service base class
- `CreateConfigRequest.java`, `ProjectConfigResponse.java`, etc.
- `ConfigState.java` - Enum

---

## Step 4: Update Implementation Code

### 4.1. ProjectConfigMapper.java

Uncomment và update:

```java
import com.fpt.projectconfig.grpc.proto.ProjectConfigProto;  // Generated class

public static ProjectConfigProto toProto(ConfigResponse response) {
    return ProjectConfigProto.newBuilder()
        .setId(response.getId().toString())
        .setGroupId(response.getGroupId())
        .setJiraHostUrl(response.getJiraHostUrl())
        .setJiraApiToken(response.getJiraApiToken())  // Masked
        .setGithubRepoUrl(response.getGithubRepoUrl())
        .setGithubAccessToken(response.getGithubAccessToken())  // Masked
        .setState(response.getState().name())
        .setInvalidReason(response.getInvalidReason() != null ? response.getInvalidReason() : "")
        .setCreatedAt(toProtoTimestamp(response.getCreatedAt()))
        .setUpdatedAt(toProtoTimestamp(response.getUpdatedAt()))
        .setCreatedBy(response.getCreatedBy())
        .setUpdatedBy(response.getUpdatedBy())
        .build();
}
```

### 4.2. ProjectConfigGrpcService.java

**Update class declaration:**

```java
import com.fpt.projectconfig.grpc.proto.ProjectConfigServiceGrpc;
import net.devh.boot.grpc.server.service.GrpcService;

@GrpcService  // Thêm annotation
public class ProjectConfigGrpcService 
        extends ProjectConfigServiceGrpc.ProjectConfigServiceImplBase {  // Thêm extends
    
    // Keep existing code
}
```

**Uncomment method bodies:**

```java
@Override
public void createProjectConfig(CreateConfigRequestProto request,
                                 StreamObserver<ProjectConfigResponseProto> responseObserver) {
    try {
        Long userId = AuthenticationInterceptor.getUserId();
        List<String> roles = Arrays.asList(AuthenticationInterceptor.getRoles());

        // Extract from protobuf request
        CreateConfigRequest dtoRequest = CreateConfigRequest.builder()
                .groupId(request.getGroupId())
                .jiraHostUrl(request.getJiraHostUrl())
                .jiraApiToken(request.getJiraApiToken())
                .githubRepoUrl(request.getGithubRepoUrl())
                .githubAccessToken(request.getGithubAccessToken())
                .build();

        ConfigResponse configResponse = projectConfigService.createConfig(dtoRequest, userId, roles);

        ProjectConfigResponseProto protoResponse = ProjectConfigMapper.toProto(configResponse);

        responseObserver.onNext(protoResponse);
        responseObserver.onCompleted();

    } catch (Exception e) {
        log.error("Error creating config", e);
        responseObserver.onError(Status.INTERNAL
                .withDescription(e.getMessage())
                .asRuntimeException());
    }
}
```

Repeat cho tất cả methods (GetProjectConfig, UpdateProjectConfig, DeleteProjectConfig, VerifyConnection, RestoreProjectConfig, InternalGetDecryptedConfig).

---

## Step 5: Add gRPC Server Dependency

**Option A: Spring Boot Starter (Recommended)**

Thêm vào `pom.xml`:

```xml
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-server-spring-boot-starter</artifactId>
    <version>2.15.0.RELEASE</version>
</dependency>
```

Configure trong `application.yml`:

```yaml
grpc:
  server:
    port: 9090
```

**Option B: Manual Setup**

Tự tạo GrpcServerRunner với `io.grpc:grpc-netty-shaded` (đã có trong pom.xml).

---

## Step 6: Verify Configuration

### GrpcConfig.java

File đã được tạo sẵn tại `config/GrpcConfig.java`. Nếu dùng net.devh starter, uncomment:

```java
@Bean
public GlobalServerInterceptorConfigurer globalInterceptorConfigurer() {
    return registry -> {
        registry.addServerInterceptors(authenticationInterceptor());
        registry.addServerInterceptors(serviceToServiceInterceptor());
    };
}
```

---

## Step 7: Run Service

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=projectconfig
```

**Expected logs:**

```
gRPC Server started, listening on port 9090
Hibernate: ...
Started ProjectConfigServiceApplication in X.XXX seconds
```

---

## Step 8: Test with grpcurl

```bash
# List services
grpcurl -plaintext localhost:9090 list

# Expected output:
# projectconfig.ProjectConfigService
# grpc.health.v1.Health
# grpc.reflection.v1alpha.ServerReflection

# Create config
grpcurl -plaintext \
  -H "user-id: 123" \
  -H "roles: ADMIN" \
  -d '{
    "group_id": 456,
    "jira_host_url": "https://example.atlassian.net",
    "jira_api_token": "ATATT3xFfGF0...",
    "github_repo_url": "https://github.com/user/repo",
    "github_access_token": "ghp_..."
  }' \
  localhost:9090 projectconfig.ProjectConfigService/CreateProjectConfig
```

---

## Troubleshooting

### Issue: "Method not found"

**Solution:** 
- Verify `.proto` file được copy đúng
- Run `mvn clean compile` lại
- Check generated classes trong `target/generated-sources/protobuf/`

### Issue: "No service registered"

**Solution:**
- Verify `@GrpcService` annotation
- Check service extends `ProjectConfigServiceGrpc.ProjectConfigServiceImplBase`
- Verify gRPC server dependency

### Issue: "UNAUTHENTICATED"

**Solution:**
- Thêm metadata headers: `user-id`, `roles`
- Verify AuthenticationInterceptor đang hoạt động

### Issue: "PERMISSION_DENIED"

**Solution:**
- Internal methods cần: `x-service-name`, `x-service-key`
- Verify ServiceAuthInterceptor configuration

---

## Architecture Diagram

```
Client
  │
  ├─ Metadata: user-id, roles
  │
  ▼
┌─────────────────────────────┐
│  gRPC Server (Port 9090)    │
│  ┌─────────────────────┐    │
│  │ Interceptors        │    │
│  │ - Authentication    │    │
│  │ - ServiceAuth       │    │
│  └─────────────────────┘    │
│           │                 │
│  ┌────────▼────────────┐    │
│  │ ProjectConfig       │    │
│  │ GrpcService         │    │
│  └────────┬────────────┘    │
└───────────┼─────────────────┘
            │
   ┌────────▼────────┐
   │ ProjectConfig   │
   │ Service         │
   │ (Business Logic)│
   └────────┬────────┘
            │
   ┌────────▼────────┐
   │ Repository      │
   │ (PostgreSQL)    │
   └─────────────────┘
```

---

## Summary

✅ **Business Logic**: Hoàn chỉnh (UC30-35 + Internal API)  
✅ **Security**: Authentication + Service Auth interceptors  
✅ **Encryption**: AES-256-GCM  
✅ **Masking**: Role-based token masking  
✅ **Soft Delete**: 90-day retention với scheduled cleanup  

🔨 **User Setup Required**:
1. Generate Protobuf classes
2. Uncomment implementation code
3. Add gRPC server dependency
4. Test với grpcurl

**Estimated Setup Time:** 15-30 minutes
