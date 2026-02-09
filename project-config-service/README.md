# Project Config Service (gRPC)

Microservice quản lý cấu hình project (Jira và GitHub) cho hệ thống SAMT với gRPC communication.

## Features

### Use Cases Implemented
- **UC30**: Create Project Config - Tạo cấu hình mới cho nhóm
- **UC31**: Get Project Config - Xem cấu hình theo groupId
- **UC32**: Update Project Config - Cập nhật cấu hình
- **UC33**: Delete Project Config - Xóa mềm cấu hình (soft delete)
- **UC34**: Verify Project Config - Kiểm tra kết nối Jira/GitHub
- **UC35**: Restore Project Config - Khôi phục cấu hình đã xóa (Admin only)

### Security Features
- **gRPC Metadata Authentication**: Extract userId và roles từ metadata
- **Service-to-Service Auth**: Internal methods yêu cầu x-service-name và x-service-key headers
- **Token Encryption**: AES-256-GCM encryption cho jira_api_token và github_access_token
- **Token Masking**: Mask tokens dựa trên role (ADMIN/LECTURER xem full, STUDENT xem masked)

### State Machine
```
CREATE → DRAFT
VERIFY success → VERIFIED
VERIFY fail → INVALID
UPDATE critical fields → DRAFT
DELETE → DELETED
RESTORE → DRAFT
```

### Soft Delete Pattern
- `deleted_at TIMESTAMP`: Thời điểm xóa
- `deleted_by BIGINT`: User ID thực hiện xóa
- **90-day Retention**: Scheduled job tự động hard delete sau 90 ngày
- `@SQLRestriction("deleted_at IS NULL")`: Filter deleted records tại JPA layer

## Tech Stack

- **Spring Boot 3.x**
- **Spring Data JPA + Hibernate**
- **gRPC** (io.grpc:grpc-netty-shaded, grpc-protobuf, grpc-stub)
- **PostgreSQL** (UUID primary keys, BIGINT foreign keys)
- **AES-256-GCM Encryption** - Javax Crypto
- **Flyway** - Database migration
- **Lombok** - Boilerplate reduction

## Project Structure

```
src/main/java/com/fpt/projectconfig/
├── entity/
│   └── ProjectConfig.java              # Entity với soft delete + state machine
├── repository/
│   └── ProjectConfigRepository.java    # Custom queries cho soft delete
├── dto/
│   ├── request/
│   │   ├── CreateConfigRequest.java    # Validation rules cho create
│   │   └── UpdateConfigRequest.java    # Partial update support
│   └── response/
│       ├── ApiResponse.java            # Wrapper cho success response
│       ├── ConfigResponse.java         # Config với masked tokens
│       ├── DecryptedTokensResponse.java # Full tokens cho Sync Service
│       ├── ErrorResponse.java          # Error format
│       └── VerificationResponse.java   # Jira/GitHub test results
├── exception/
│   ├── ConfigAlreadyExistsException.java
│   ├── ConfigNotFoundException.java
│   ├── EncryptionException.java
│   ├── ForbiddenException.java
│   ├── VerificationException.java
│   └── GlobalExceptionHandler.java     # Exception handler
├── service/
│   ├── TokenEncryptionService.java     # AES-256-GCM encrypt/decrypt
│   ├── TokenMaskingService.java        # Role-based masking
│   ├── JiraVerificationService.java    # Jira API verification
│   ├── GitHubVerificationService.java  # GitHub API verification
│   └── ProjectConfigService.java       # Main business logic (UC30-35)
├── client/
│   ├── dto/GroupDto.java
│   └── UserGroupServiceClient.java     # REST client cho User-Group Service
├── grpc/
│   ├── interceptor/
│   │   ├── AuthenticationInterceptor.java    # Extract userId/roles từ metadata
│   │   └── ServiceAuthInterceptor.java       # Service-to-service auth
│   ├── mapper/
│   │   └── ProjectConfigMapper.java          # Domain ↔ Protobuf conversion
│   └── service/
│       └── ProjectConfigGrpcService.java     # gRPC service implementation
├── config/
│   ├── RestTemplateConfig.java         # 10s timeout cho external APIs
│   └── GrpcConfig.java                 # gRPC interceptors configuration
├── scheduler/
│   └── ConfigCleanupScheduler.java     # 90-day retention job (2:00 AM daily)
└── ProjectConfigServiceApplication.java

src/main/proto/
└── project_config.proto.template       # Protobuf definition template
```

## gRPC Service Definition

### Protobuf Setup

1. **Copy template**:
```bash
cp src/main/proto/project_config.proto.template src/main/proto/project_config.proto
```

2. **Generate Java classes**:
```bash
mvn clean compile
```

3. **Update implementation**: Uncomment code trong `ProjectConfigGrpcService.java`

### gRPC Methods

#### UC30: CreateProjectConfig
```protobuf
rpc CreateProjectConfig(CreateConfigRequest) returns (ProjectConfigResponse);
```
**Metadata Required**: `user-id`, `roles`

#### UC31: GetProjectConfig
```protobuf
rpc GetProjectConfig(GetConfigRequest) returns (ProjectConfigResponse);
```
**Metadata Required**: `user-id`, `roles`

#### UC32: UpdateProjectConfig
```protobuf
rpc UpdateProjectConfig(UpdateConfigRequest) returns (ProjectConfigResponse);
```
**Metadata Required**: `user-id`, `roles`

#### UC33: DeleteProjectConfig
```protobuf
rpc DeleteProjectConfig(DeleteConfigRequest) returns (google.protobuf.Empty);
```
**Metadata Required**: `user-id`, `roles`

#### UC34: VerifyConnection
```protobuf
rpc VerifyConnection(VerifyConfigRequest) returns (VerificationResult);
```
**Metadata Required**: `user-id`, `roles`

#### UC35: RestoreProjectConfig
```protobuf
rpc RestoreProjectConfig(RestoreConfigRequest) returns (ProjectConfigResponse);
```
**Metadata Required**: `user-id`, `roles` (Admin only)

#### InternalGetDecryptedConfig
```protobuf
rpc InternalGetDecryptedConfig(GetConfigRequest) returns (DecryptedConfigResponse);
```
**Metadata Required**: `x-service-name`, `x-service-key`

### Metadata Format

Clients phải gửi metadata trong mỗi gRPC call:

```java
// Public methods (UC30-35)
Metadata metadata = new Metadata();
metadata.put(Metadata.Key.of("user-id", Metadata.ASCII_STRING_MARSHALLER), "123");
metadata.put(Metadata.Key.of("roles", Metadata.ASCII_STRING_MARSHALLER), "ADMIN,LECTURER");

// Internal methods
Metadata metadata = new Metadata();
metadata.put(Metadata.Key.of("x-service-name", Metadata.ASCII_STRING_MARSHALLER), "sync-service");
metadata.put(Metadata.Key.of("x-service-key", Metadata.ASCII_STRING_MARSHALLER), "secret-key");
```

## Configuration

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/samt_projectconfig
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# JWT
JWT_SECRET=your-256-bit-secret-key

# Encryption (256-bit key as 64 hex chars)
ENCRYPTION_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# Service-to-Service Auth
INTERNAL_SERVICE_KEY=sync-service-secret-key

# User-Group Service URL
USERGROUP_SERVICE_URL=http://localhost:8082
```

### application-projectconfig.yml

## Configuration

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/samt_projectconfig
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres

# Encryption (256-bit key as 64 hex chars)
ENCRYPTION_KEY=0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef

# Service-to-Service Auth
INTERNAL_SERVICE_KEY=sync-service-secret-key

# User-Group Service URL
USERGROUP_SERVICE_URL=http://localhost:8082

# gRPC Server Port
GRPC_SERVER_PORT=9090
```

### application-projectconfig.yml

```yaml
server:
  port: 8083

spring:
  application:
    name: project-config-service
  datasource:
    url: jdbc:postgresql://localhost:5432/samt_projectconfig
    username: postgres
    password: postgres
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  flyway:
    enabled: true
    locations: classpath:db/migration

# gRPC Configuration (user tự setup sau)
grpc:
  server:
    port: ${GRPC_SERVER_PORT:9090}

encryption:
  key: ${ENCRYPTION_KEY}

security:
  internal-service-key: ${INTERNAL_SERVICE_KEY}

usergroup:
  service:
    url: ${USERGROUP_SERVICE_URL:http://localhost:8082}
```

## Database Schema

### project_configs table

| Column | Type | Constraints | Description |
|--------|------|-------------|-------------|
| id | UUID | PRIMARY KEY | Auto-generated UUID |
| group_id | BIGINT | NOT NULL, UNIQUE | Reference to User-Group Service |
| jira_host_url | VARCHAR(255) | NOT NULL | Jira instance URL |
| jira_api_token_encrypted | TEXT | NOT NULL | Encrypted Jira API token |
| github_repo_url | VARCHAR(512) | NOT NULL | GitHub repository URL |
| github_token_encrypted | TEXT | NOT NULL | Encrypted GitHub token |
| state | VARCHAR(20) | NOT NULL | DRAFT/VERIFIED/INVALID/DELETED |
| invalid_reason | TEXT | | Verification failure details |
| created_at | TIMESTAMP | NOT NULL | Creation timestamp |
| updated_at | TIMESTAMP | NOT NULL | Last update timestamp |
| created_by | BIGINT | NOT NULL | Creator user ID |
| updated_by | BIGINT | NOT NULL | Last updater user ID |
| deleted_at | TIMESTAMP | | Soft delete timestamp |
| deleted_by | BIGINT | | Soft delete user ID |
| version | INTEGER | NOT NULL | Optimistic locking version |

## Business Rules

### Authorization
- **ADMIN**: Full access (create, read, update, delete, verify, restore)
- **LECTURER**: Full access (create, read, update, delete, verify, restore)
- **STUDENT**: 
  - Can create if group leader
  - Can read if group member or leader
  - Can update/delete/verify only if group leader
  - Cannot restore (Admin only)

### Token Masking
- **ADMIN/LECTURER**: See full tokens
- **STUDENT**: 
  - Jira token: `***XXXX` (last 4 chars)
  - GitHub token: `ghp_***XXXX` (prefix + last 4 chars)

### Validation Rules
- **Jira Host URL**: Must contain `atlassian.net` or `jira.com`
- **Jira API Token**: Pattern `^ATATT[A-Za-z0-9+/=_-]{100,500}$`
- **GitHub Repo URL**: Must contain `github.com/owner/repo`
- **GitHub Token**: Pattern `^ghp_[A-Za-z0-9]{36,}$`

### Verification Flow
1. Call Jira API: `GET {jira_host_url}/rest/api/3/myself`
2. If Jira success → Call GitHub API: `GET https://api.github.com/user`
3. Both success → State = VERIFIED
4. Any failure → State = INVALID with reason

### Critical Field Detection
Update transitions to DRAFT if any of these fields change:
- `jiraHostUrl`
- `jiraApiToken`
- `githubRepoUrl`
- `githubAccessToken`

## Running the Service

### Prerequisites
- JDK 21+
- PostgreSQL 14+
- Maven 3.8+

### Setup Steps

1. **Generate Protobuf classes**:
```bash
# Copy proto template
cp src/main/proto/project_config.proto.template src/main/proto/project_config.proto

# Generate Java classes
mvn clean compile
```

2. **Update ProjectConfigGrpcService**:
   - Uncomment code trong `ProjectConfigGrpcService.java`
   - Update extends clause với generated base class
   - Implement protobuf conversions trong `ProjectConfigMapper.java`

3. **Configure gRPC server** (User tự setup):
```xml
<!-- pom.xml -->
<dependency>
    <groupId>net.devh</groupId>
    <artifactId>grpc-server-spring-boot-starter</artifactId>
    <version>2.15.0.RELEASE</version>
</dependency>
```

4. **Annotate service**:
```java
@GrpcService
public class ProjectConfigGrpcService extends ProjectConfigServiceGrpc.ProjectConfigServiceImplBase {
    // ...
}
```

### Build & Run
```bash
cd project-config-service

# Build
mvn clean install

# Run với custom profile
mvn spring-boot:run -Dspring-boot.run.profiles=projectconfig

# Or run JAR
java -jar target/project-config-service-1.0.0.jar
```
java -jar target/project-config-service-1.0.0.jar
```

### Testing gRPC

#### Using grpcurl

```bash
# Install grpcurl
# Windows: choco install grpcurl
# Mac: brew install grpcurl
# Linux: go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest

# List services
grpcurl -plaintext localhost:9090 list

# Create Config (UC30)
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

# Get Config (UC31)
grpcurl -plaintext \
  -H "user-id: 123" \
  -H "roles: STUDENT" \
  -d '{"group_id": 456}' \
  localhost:9090 projectconfig.ProjectConfigService/GetProjectConfig

# Verify Connection (UC34)
grpcurl -plaintext \
  -H "user-id: 123" \
  -H "roles: ADMIN" \
  -d '{"config_id": "uuid-here"}' \
  localhost:9090 projectconfig.ProjectConfigService/VerifyConnection

# Internal API (Sync Service)
grpcurl -plaintext \
  -H "x-service-name: sync-service" \
  -H "x-service-key: secret-key" \
  -d '{"config_id": "uuid-here"}' \
  localhost:9090 projectconfig.ProjectConfigService/InternalGetDecryptedConfig
```

#### Using BloomRPC/Postman

1. Import `project_config.proto` file
2. Configure metadata headers:
   - `user-id`: 123
   - `roles`: ADMIN,LECTURER
3. Send requests với JSON payload

## Logging

```yaml
logging:
  level:
    com.fpt.projectconfig: DEBUG
    io.grpc: INFO
```

## Implementation Notes

### TODO Items (After .proto Generation)

1. **ProjectConfigMapper.java**:
   - Uncomment `toProto()` method
   - Uncomment `toVerificationProto()` method
   - Update với generated Protobuf class names

2. **ProjectConfigGrpcService.java**:
   - Add `extends ProjectConfigServiceGrpc.ProjectConfigServiceImplBase`
   - Uncomment all method bodies
   - Update method signatures với generated Protobuf types
   - Add `@GrpcService` annotation

3. **GrpcConfig.java**:
   - Uncomment `GlobalServerInterceptorConfigurer` bean nếu dùng net.devh starter

### gRPC vs REST Differences

| Aspect | REST (Old) | gRPC (Current) |
|--------|-----------|----------------|
| Protocol | HTTP/1.1 JSON | HTTP/2 Protobuf |
| Authentication | JWT Filter | Metadata Interceptor |
| Authorization | SecurityContext | gRPC Context |
| Error Handling | HTTP Status Codes | gRPC Status Codes |
| Internal API | X-Service-Name header | Metadata with ServiceAuthInterceptor |

### Security Model

- **Authentication**: API Gateway validates JWT, forwards userId/roles qua metadata
- **Authorization**: ProjectConfigService kiểm tra roles và group leadership
- **Service Auth**: Internal methods yêu cầu x-service-name và x-service-key metadata
- **Encryption**: Tokens luôn được encrypt trước khi lưu DB (AES-256-GCM)
- **Masking**: Response tokens được mask dựa trên user role

## Known Issues

See [issues_project_config.md](issues_project_config.md) for documented issues and assumptions.

## License

Internal use only - FPT University SAMT Project

