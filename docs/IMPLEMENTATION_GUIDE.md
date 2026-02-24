# SAMT System - Implementation Guide

**Version:** 2.0  
**Date:** February 9, 2026  
**Status:** Implementation Complete

---

## 📋 EXECUTIVE SUMMARY

Hệ thống SAMT đã được re-implement theo kiến trúc Microservices với các cải tiến về bảo mật, resilience, và data integrity.

### Thay đổi chính:

✅ **API Gateway với JWT Authentication**
- JWT validation tại Gateway
- Inject user info vào headers cho downstream services
- Centralized authentication point

✅ **Database Independence**
- NO Foreign Key xuyên services
- Logical reference validation qua gRPC
- Service autonomy và fault tolerance

✅ **User-Semester Membership Model**
- Enforce: 1 user = 1 group per semester
- Database-level unique constraint
- PostgreSQL triggers cho business rules

✅ **gRPC Security**
- Metadata-based authentication
- Interceptor cho authorization
- Context propagation

✅ **Business Logic Validation**
- Validate user_id qua Identity Service trước Create/Update
- Role validation (LECTURER, STUDENT)
- Fail-fast strategy

✅ **Event-Driven Architecture**
- Kafka event bus
- UserDeletedEvent cho cascade cleanup
- Async processing với retry

✅ **Resilience Patterns**
- Circuit Breaker cho Identity Service calls
- Exponential backoff retry
- Graceful degradation

---

## 🏗️ KIẾN TRÚC HỆ THỐNG

### 1. Service Communication Flow

```
┌─────────────────────────────────────────────────────────────────┐
│                         CLIENT                                  │
│                      (REST/JSON)                                │
└─────────────────────────┬───────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────────────┐
│                      API GATEWAY                                │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │ 1. JWT Validation (JwtAuthenticationFilter)             │  │
│  │ 2. Extract userId, email, role                          │  │
│  │ 3. Inject to Headers: X-User-Id, X-User-Email, X-User-Role│ │
│  └──────────────────────────────────────────────────────────┘  │
└─────────┬─────────────────┬─────────────────┬─────────────────┘
          │                 │                 │
          │ REST            │ REST            │ gRPC (metadata)
          ▼                 ▼                 ▼
┌─────────────────┐ ┌────────────────┐ ┌──────────────────────┐
│ Identity Service│ │ User-Group     │ │ Project Config       │
│                 │ │ Service        │ │ Service              │
│ - Auth/User     │ │                │ │                      │
│ - JWT issuing   │◄┤ gRPC Client    │◄┤ gRPC Client          │
│ - gRPC Server   │ │ (validate user)│ │ (validate group)     │
│   (user data)   │ │                │ │                      │
│                 │ │ Circuit Breaker│ │ Circuit Breaker      │
│                 │ │ + Retry        │ │ + Retry              │
└────────┬────────┘ └───────┬────────┘ └──────────┬───────────┘
         │                  │                      │
         │ Kafka            │ Kafka                │
         │ (publish)        │ (consume)            │ (consume)
         ▼                  ▼                      ▼
┌─────────────────────────────────────────────────────────────────┐
│                   KAFKA EVENT BUS                               │
│  Topics:                                                        │
│  - user.deleted: Cascade cleanup khi user bị hard delete       │
└─────────────────────────────────────────────────────────────────┘
```

---

## 🔐 1. API GATEWAY - JWT AUTHENTICATION

### Files Created:

1. **SecurityConfig.java**
   - Location: `api-gateway/src/main/java/com/example/gateway/config/SecurityConfig.java`
   - Purpose: Configure Spring Security cho Gateway
   - Features:
     - Public endpoints (register, login, refresh-token)
     - JWT filter at AUTHENTICATION order
     - CORS configuration

2. **JwtAuthenticationFilter.java**
   - Location: `api-gateway/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java`
   - Purpose: Validate JWT và inject user info
   - Flow:
     ```
     1. Extract JWT from Authorization header
     2. Validate signature và expiration
     3. Extract userId, email, role
     4. Inject vào headers: X-User-Id, X-User-Email, X-User-Role
     5. Set Spring Security context
     ```

3. **JwtUtil.java**
   - Location: `api-gateway/src/main/java/com/example/gateway/util/JwtUtil.java`
   - Purpose: JWT validation utility
   - CRITICAL: Secret key MUST match Identity Service

4. **GatewayRoutesConfig.java**
   - Location: `api-gateway/src/main/java/com/example/gateway/config/GatewayRoutesConfig.java`
   - Purpose: Define routing rules
   - Routes:
     - `/api/identity/**` → Identity Service (REST)
     - `/api/groups/**`, `/api/users/**` → User-Group Service (REST)
     - `/api/project-configs/**` → Project Config Service (REST)
     - Note: All services also expose gRPC for inter-service communication

### Configuration:

```yaml
# api-gateway/src/main/resources/application.yml

jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-this-in-production}

spring:
  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
```

### Dependencies Added:

```xml
<!-- api-gateway/pom.xml -->

<!-- JWT Library -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.3</version>
</dependency>

<!-- SpringDoc OpenAPI (Swagger Aggregation) -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webflux-ui</artifactId>
    <version>2.3.0</version>
</dependency>

<!-- Redis Reactive -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis-reactive</artifactId>
</dependency>
```

---

## 💾 2. DATABASE - NO FOREIGN KEY XUYÊN SERVICE

### Design Principles:

1. **Logical Reference Instead of FK**
   - `groups.lecturer_id` → Logical reference to Identity Service (NO FK)
   - `user_semester_membership.user_id` → Logical reference (NO FK)
   - `deleted_by` → Logical reference (NO FK)

2. **Service-Layer Validation**
   ```java
   // Before creating group
   VerifyUserResponse lecturer = identityServiceClient.verifyUser(lecturerId);
   if (!lecturer.getExists()) throw NotFoundException("Lecturer not found");
   if (lecturer.getRole() != LECTURER) throw BadRequestException("Not a lecturer");
   ```

3. **Graceful Handling của Deleted Users**
   ```java
   // When rendering group details
   GetUserResponse lecturer = identityServiceClient.getUser(group.getLecturerId());
   if (lecturer.getDeleted()) {
       return GroupResponse.builder()
           .lecturerName("<Deleted User>")
           .build();
   }
   ```

### Flyway Migrations:

**V1__initial_schema.sql**
```sql
-- Semesters
CREATE TABLE semesters (
    id BIGSERIAL PRIMARY KEY,
    semester_code VARCHAR(50) NOT NULL UNIQUE,
    semester_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Groups
CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    semester_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,  -- NO FK CONSTRAINT
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,      -- NO FK CONSTRAINT
    CONSTRAINT fk_groups_semester FOREIGN KEY (semester_id) REFERENCES semesters(id)
);

CREATE UNIQUE INDEX idx_groups_name_semester_unique 
    ON groups(group_name, semester_id) 
    WHERE deleted_at IS NULL;

-- User Semester Membership
-- Business Rule: 1 user = 1 group per semester
CREATE TABLE user_semester_membership (
    user_id BIGINT NOT NULL,     -- NO FK CONSTRAINT
    semester_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    group_role VARCHAR(20) NOT NULL CHECK (group_role IN ('LEADER', 'MEMBER')),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,      -- NO FK CONSTRAINT
    
    PRIMARY KEY (user_id, semester_id),
    CONSTRAINT fk_usm_semester FOREIGN KEY (semester_id) REFERENCES semesters(id),
    CONSTRAINT fk_usm_group FOREIGN KEY (group_id) REFERENCES groups(id)
);

-- Enforce: Only 1 LEADER per group
CREATE UNIQUE INDEX idx_usm_group_leader_unique 
    ON user_semester_membership(group_id) 
    WHERE group_role = 'LEADER' AND deleted_at IS NULL;
```

**V2__add_user_group_constraints.sql**
```sql
-- Trigger: Prevent duplicate membership
CREATE OR REPLACE FUNCTION check_user_semester_uniqueness()
RETURNS TRIGGER AS $$
BEGIN
    IF EXISTS (
        SELECT 1 
        FROM user_semester_membership 
        WHERE user_id = NEW.user_id 
        AND semester_id = NEW.semester_id 
        AND deleted_at IS NULL
    ) THEN
        RAISE EXCEPTION 'User already belongs to a group in this semester';
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_check_user_semester_uniqueness
    BEFORE INSERT OR UPDATE ON user_semester_membership
    FOR EACH ROW
    EXECUTE FUNCTION check_user_semester_uniqueness();
```

### JPA Entities Created:

1. **Semester.java**
   - Location: `user-group-service/src/main/java/com/example/user_groupservice/entity/Semester.java`

2. **UserSemesterMembership.java**
   - Location: `user-group-service/src/main/java/com/example/user_groupservice/entity/UserSemesterMembership.java`
   - Composite Primary Key: `@EmbeddedId UserSemesterMembershipId`

---

## 🔒 3. gRPC SECURITY - INTERCEPTOR & METADATA

### Files Created:

1. **GrpcAuthenticationInterceptor.java**
   - Location: `project-config-service/src/main/java/com/example/projectconfigservice/grpc/interceptor/GrpcAuthenticationInterceptor.java`
   - Purpose: Extract và validate user info từ gRPC metadata
   - Metadata Format:
     ```
     x-user-id: "12345"
     x-user-email: "user@example.com"
     x-user-role: "STUDENT"
     ```
   - Context Keys:
     ```java
     public static final Context.Key<Long> CONTEXT_USER_ID = Context.key("userId");
     public static final Context.Key<String> CONTEXT_USER_EMAIL = Context.key("userEmail");
     public static final Context.Key<String> CONTEXT_USER_ROLE = Context.key("userRole");
     ```

2. **GrpcAuthorizationInterceptor.java**
   - Location: `project-config-service/src/main/java/com/example/projectconfigservice/grpc/interceptor/GrpcAuthorizationInterceptor.java`
   - Purpose: Role-based authorization
   - Rules:
     - ADMIN: Full access
     - LECTURER: Read-only (với token masking)
     - STUDENT (LEADER): Create/Update/Delete
     - STUDENT (MEMBER): Read-only (với token masking)

### Usage in Service Methods:

```java
@Override
public void createProjectConfig(CreateProjectConfigRequest request, 
                                 StreamObserver<ProjectConfigResponse> responseObserver) {
    try {
        // Get authenticated user info from Context
        Long userId = GrpcAuthenticationInterceptor.getCurrentUserId();
        String role = GrpcAuthenticationInterceptor.getCurrentUserRole();
        
        // Validate user is LEADER of the group
        if (!isGroupLeader(userId, request.getGroupId())) {
            responseObserver.onError(
                Status.PERMISSION_DENIED
                    .withDescription("Only group leader can create project config")
                    .asRuntimeException()
            );
            return;
        }
        
        // Proceed with business logic
        // ...
        
    } catch (Exception e) {
        responseObserver.onError(Status.INTERNAL.withCause(e).asRuntimeException());
    }
}
```

---

## ✅ 4. BUSINESS LOGIC VALIDATION

### User Validation Flow:

```
┌────────────────────────────────────────────────────────────────┐
│ UC23: Create Group                                             │
├────────────────────────────────────────────────────────────────┤
│                                                                │
│ 1. Validate semester exists (DB query)                        │
│                                                                │
│ 2. Validate lecturer exists và có role LECTURER               │
│    ┌──────────────────────────────────────────────┐          │
│    │ gRPC Call: IdentityService.verifyUser()      │          │
│    │   - Check exists                             │          │
│    │   - Check NOT deleted                        │          │
│    │   - Check role == LECTURER                   │          │
│    └──────────────────────────────────────────────┘          │
│         ↓                                                      │
│    [Circuit Breaker + Retry]                                  │
│         ↓                                                      │
│    If UNAVAILABLE → Fail-fast (503)                           │
│                                                                │
│ 3. Check duplicate group name (DB unique index)               │
│                                                                │
│ 4. Create group                                                │
│                                                                │
└────────────────────────────────────────────────────────────────┘
```

### Service Implementation (Pseudo-code):

```java
@Service
@RequiredArgsConstructor
public class GroupServiceImpl implements GroupService {

    private final IdentityServiceClient identityServiceClient;
    private final ResilientIdentityServiceClient resilientClient;

    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        // 1. Validate semester
        Semester semester = semesterRepository.findById(request.getSemesterId())
                .orElseThrow(() -> new NotFoundException("Semester not found"));

        // 2. Validate lecturer (with Circuit Breaker + Retry)
        try {
            VerifyUserResponse response = resilientClient.verifyUser(request.getLecturerId());
            
            if (!response.getExists()) {
                throw new NotFoundException("Lecturer not found");
            }
            
            if (response.getDeleted()) {
                throw new BadRequestException("Lecturer has been deleted");
            }
            
            if (!"LECTURER".equals(response.getRole())) {
                throw new BadRequestException("User is not a lecturer");
            }
            
        } catch (CallNotPermittedException e) {
            // Circuit breaker is OPEN
            throw new ServiceUnavailableException("Identity Service unavailable");
        }

        // 3. Check duplicate (handled by unique index)
        // 4. Create group
        Group group = new Group();
        group.setGroupName(request.getGroupName());
        group.setSemesterId(request.getSemesterId());
        group.setLecturerId(request.getLecturerId());
        
        return groupRepository.save(group);
    }
}
```

---

## 📨 5. EVENT-DRIVEN ARCHITECTURE

### Event Flow:

```
┌─────────────────────────────────────────────────────────────────┐
│ STEP 1: Admin hard deletes user in Identity Service            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  IdentityService.deleteUser(userId)                            │
│       ↓                                                         │
│  1. Hard delete user from DB                                   │
│  2. Revoke all refresh tokens                                  │
│  3. Publish UserDeletedEvent to Kafka                          │
│                                                                 │
└─────────────────────────────┬───────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────────┐
                    │   KAFKA TOPIC       │
                    │   user.deleted      │
                    └─────────┬───────────┘
                              │
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
┌─────────────────┐ ┌─────────────────┐ ┌─────────────────┐
│ User-Group      │ │ Project Config  │ │ Sync Service    │
│ Service         │ │ Service         │ │                 │
├─────────────────┤ ├─────────────────┤ ├─────────────────┤
│ Remove user     │ │ Remove/reassign │ │ Clean sync      │
│ from all groups │ │ project configs │ │ history         │
└─────────────────┘ └─────────────────┘ └─────────────────┘
```

### Event Definition:

```java
// common-events/src/main/java/com/example/common/events/UserDeletedEvent.java

@Data
@Builder
public class UserDeletedEvent implements Serializable {
    private Long userId;
    private String email;
    private String role;
    private Instant deletedAt;
    private Long deletedBy;
    private String eventId;  // UUID for deduplication
    private Instant eventTimestamp;
}
```

### Publisher (Identity Service):

```java
// identity-service/src/main/java/com/example/identityservice/event/IdentityEventPublisher.java

@Component
@RequiredArgsConstructor
public class IdentityEventPublisher {

    private final KafkaTemplate<String, UserDeletedEvent> kafkaTemplate;
    private static final String USER_DELETED_TOPIC = "user.deleted";

    public void publishUserDeletedEvent(Long userId, String email, String role, Long deletedBy) {
        UserDeletedEvent event = UserDeletedEvent.builder()
                .userId(userId)
                .email(email)
                .role(role)
                .deletedAt(Instant.now())
                .deletedBy(deletedBy)
                .eventId(UUID.randomUUID().toString())
                .eventTimestamp(Instant.now())
                .build();

        kafkaTemplate.send(USER_DELETED_TOPIC, userId.toString(), event);
    }
}
```

### Consumer (User-Group Service):

```java
// user-group-service/src/main/java/com/example/user_groupservice/event/UserGroupEventConsumer.java

@Component
@RequiredArgsConstructor
public class UserGroupEventConsumer {

    private final UserSemesterMembershipRepository membershipRepository;

    @KafkaListener(
        topics = "user.deleted",
        groupId = "user-group-service"
    )
    @Transactional
    public void handleUserDeletedEvent(UserDeletedEvent event) {
        log.info("Processing UserDeletedEvent: userId={}", event.getUserId());

        // Find all memberships của user (including soft deleted)
        List<UserSemesterMembership> memberships = 
                membershipRepository.findAllByIdUserId(event.getUserId());

        // Hard delete all memberships
        membershipRepository.deleteAll(memberships);
        
        log.info("Removed {} memberships for user {}", memberships.size(), event.getUserId());
    }
}
```

### Kafka Configuration:

```yaml
# user-group-service/src/main/resources/application.yml

spring:
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      group-id: user-group-service
      auto-offset-reset: earliest
      enable-auto-commit: false
```

---

## 🛡️ 6. RESILIENCE - CIRCUIT BREAKER & RETRY

### Configuration:

```yaml
# user-group-service/src/main/resources/application.yml

resilience4j:
  circuitbreaker:
    instances:
      identityService:
        failureRateThreshold: 50            # Open after 50% failures
        waitDurationInOpenState: 10s        # Wait 10s before half-open
        permittedNumberOfCallsInHalfOpenState: 3
        slidingWindowSize: 10               # Track last 10 calls
        minimumNumberOfCalls: 5             # Min calls before calculating rate
        
  retry:
    instances:
      identityService:
        maxAttempts: 3
        waitDuration: 500ms
        exponentialBackoffMultiplier: 2     # 500ms, 1000ms, 2000ms
```

### Circuit Breaker States:

```
┌──────────────┐
│   CLOSED     │  ← Normal state, all calls pass through
│ (Healthy)    │
└──────┬───────┘
       │ Failure rate > 50%
       ▼
┌──────────────┐
│     OPEN     │  ← Fail-fast, no calls to Identity Service
│ (Unhealthy)  │
└──────┬───────┘
       │ Wait 10s
       ▼
┌──────────────┐
│  HALF-OPEN   │  ← Test with 3 calls
│  (Testing)   │
└──────┬───────┘
       │ Success → CLOSED
       │ Failure → OPEN
```

### ResilientIdentityServiceClient:

```java
// user-group-service/src/main/java/com/example/user_groupservice/grpc/ResilientIdentityServiceClient.java

@Component
public class ResilientIdentityServiceClient {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    public VerifyUserResponse verifyUser(Long userId) {
        return circuitBreaker.executeSupplier(
                retry.decorateSupplier(() -> 
                    identityServiceClient.verifyUser(userId)
                )
        );
    }

    public boolean isCircuitBreakerOpen() {
        var state = circuitBreaker.getState();
        return state == CircuitBreaker.State.OPEN;
    }
}
```

### Error Handling:

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleCallNotPermitted(
            CallNotPermittedException ex) {
        
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponse.builder()
                        .status(503)
                        .message("Identity Service is temporarily unavailable")
                        .cause("Circuit breaker is OPEN")
                        .build());
    }
}
```

### Monitoring Endpoints:

```
GET /actuator/health
GET /actuator/circuitbreakers
GET /actuator/retries
GET /actuator/metrics/resilience4j.circuitbreaker.calls
```

---

## 📚 7. SWAGGER AT API GATEWAY

### Configuration:

```yaml
# api-gateway/src/main/resources/application.yml

springdoc:
  swagger-ui:
    enabled: true
    path: /swagger-ui.html
    urls:
      - name: Identity Service
        url: http://identity-service:8081/v3/api-docs
      - name: User-Group Service
        url: http://user-group-service:8082/v3/api-docs
  api-docs:
    enabled: true
```

### Access:

```
http://localhost:8080/swagger-ui.html
```

Swagger UI sẽ aggregate API docs từ tất cả services.

---

## 🚀 8. DEPLOYMENT

### Prerequisites:

1. **Java 21**
2. **Docker & Docker Compose**
3. **PostgreSQL 15+**
4. **Kafka 3.x**
5. **Redis 7.x**

### Environment Variables:

```bash
# .env file

# JWT Secret (MUST be same across all services)
JWT_SECRET=your-256-bit-secret-key-change-this-in-production-environment

# Database
POSTGRES_PASSWORD=your-secure-password
SPRING_DATASOURCE_PASSWORD=your-secure-password

# Kafka
KAFKA_BOOTSTRAP_SERVERS=kafka:9092

# Redis
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379

# gRPC Ports
IDENTITY_SERVICE_GRPC_PORT=9091
USER_GROUP_SERVICE_GRPC_PORT=9092
PROJECT_CONFIG_SERVICE_GRPC_PORT=9093
```

### Build & Run:

```bash
# Build all services
mvn clean package -DskipTests

# Run with Docker Compose
docker-compose up -d

# Check logs
docker-compose logs -f api-gateway
docker-compose logs -f identity-service
docker-compose logs -f user-group-service

# Check health
curl http://localhost:8080/actuator/health
```

### Database Migration:

Flyway migrations sẽ tự động chạy khi service start lần đầu.

```bash
# Check migration status
docker exec -it samt-user-group-service sh
flyway info
```

---

## 🧪 9. TESTING

### Unit Tests:

```bash
# Run unit tests
mvn test

# Run specific service tests
cd user-group-service
mvn test
```

### Integration Tests:

```bash
# Run integration tests với Testcontainers
mvn verify -P integration-tests
```

### API Tests (Postman/cURL):

```bash
# 1. Register user
curl -X POST http://localhost:8080/api/identity/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "student@example.com",
    "password": "password123",
    "fullName": "Test Student"
  }'

# 2. Login
curl -X POST http://localhost:8080/api/identity/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "student@example.com",
    "password": "password123"
  }'

# Response:
# {
#   "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
#   "refreshToken": "uuid-here"
# }

# 3. Create group (ADMIN only)
curl -X POST http://localhost:8080/api/groups \
  -H "Authorization: Bearer <access-token>" \
  -H "Content-Type: application/json" \
  -d '{
    "groupName": "Group A",
    "semesterId": 1,
    "lecturerId": 2
  }'
```

### gRPC Tests (grpcurl):

```bash
# Install grpcurl
go install github.com/fullstorydev/grpcurl/cmd/grpcurl@latest

# List services
grpcurl -plaintext localhost:9092 list

# Call VerifyUser
grpcurl -plaintext \
  -d '{"user_id": 1}' \
  -H 'x-user-id: 1' \
  -H 'x-user-email: admin@example.com' \
  -H 'x-user-role: ADMIN' \
  localhost:9091 IdentityService/VerifyUser
```

---

## 📊 10. MONITORING

### Metrics Endpoints:

```
# API Gateway
http://localhost:8080/actuator/health
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/gateway/routes

# Identity Service
http://localhost:8081/actuator/health
http://localhost:8081/actuator/metrics

# User-Group Service
http://localhost:8082/actuator/health
http://localhost:8082/actuator/circuitbreakers
http://localhost:8082/actuator/retries
```

### Circuit Breaker Metrics:

```bash
# Check Identity Service circuit breaker state
curl http://localhost:8082/actuator/circuitbreakers | jq '.circuitBreakers.identityService'

# Response:
# {
#   "state": "CLOSED",
#   "failureRate": "12.5%",
#   "slowCallRate": "0.0%",
#   "bufferedCalls": 10,
#   "failedCalls": 1,
#   "successfulCalls": 9
# }
```

### Kafka Consumer Lag:

```bash
# Check Kafka topics
docker exec -it kafka kafka-topics --bootstrap-server localhost:9092 --list

# Check consumer group lag
docker exec -it kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group user-group-service \
  --describe
```

---

## 🔧 11. TROUBLESHOOTING

### Problem: JWT Validation Failed at Gateway

**Symptom:** 401 Unauthorized

**Solutions:**
1. Check JWT secret consistency:
   ```bash
   # Identity Service
   echo $JWT_SECRET
   
   # API Gateway
   echo $JWT_SECRET
   ```
   
2. Verify token expiration:
   ```bash
   # Decode JWT (without verification)
   echo "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." | \
     jq -R 'split(".") | .[1] | @base64d | fromjson'
   ```

### Problem: Circuit Breaker OPEN

**Symptom:** 503 Service Unavailable

**Solutions:**
1. Check Identity Service health:
   ```bash
   curl http://localhost:8081/actuator/health
   ```

2. Wait for circuit to transition to HALF-OPEN (10 seconds)

3. Manually reset circuit breaker (if needed):
   ```bash
   curl -X POST http://localhost:8082/actuator/circuitbreakers/identityService/reset
   ```

### Problem: Database Migration Failed

**Symptom:** Flyway validation error

**Solutions:**
1. Check migration files:
   ```bash
   ls -la user-group-service/src/main/resources/db/migration/
   ```

2. Repair Flyway schema history:
   ```bash
   docker exec -it samt-postgres psql -U postgres -d samt_usergroup
   SELECT * FROM flyway_schema_history;
   
   # If needed, repair
   DELETE FROM flyway_schema_history WHERE success = false;
   ```

3. Clean and re-migrate (DEV ONLY):
   ```bash
   docker exec -it samt-postgres psql -U postgres -d samt_usergroup -c "DROP SCHEMA public CASCADE; CREATE SCHEMA public;"
   # Restart service to re-run migrations
   ```

### Problem: Kafka Consumer Not Processing Events

**Symptom:** UserDeletedEvent published but users không bị remove khỏi groups

**Solutions:**
1. Check Kafka topic:
   ```bash
   docker exec -it kafka kafka-console-consumer \
     --bootstrap-server localhost:9092 \
     --topic user.deleted \
     --from-beginning
   ```

2. Check consumer group:
   ```bash
   docker exec -it kafka kafka-consumer-groups \
     --bootstrap-server localhost:9092 \
     --group user-group-service \
     --describe
   ```

3. Check service logs:
   ```bash
   docker logs -f samt-user-group-service | grep -i kafka
   ```

---

## 📖 12. BEST PRACTICES

### Security:

✅ **DO:**
- Rotate JWT secrets regularly
- Use strong passwords (min 12 characters)
- Enable HTTPS in production
- Use mTLS cho gRPC inter-service communication
- Sanitize all user inputs

❌ **DON'T:**
- Commit secrets to Git
- Use default passwords
- Expose internal gRPC endpoints to public
- Trust client-provided user info (always validate at backend)

### Performance:

✅ **DO:**
- Use connection pooling (HikariCP)
- Enable gRPC keepalive
- Cache frequently accessed data (Redis)
- Use database indexes
- Monitor circuit breaker metrics

❌ **DON'T:**
- Make synchronous calls without timeout
- Fetch large result sets without pagination
- Ignore slow query warnings
- Disable connection timeout

### Resilience:

✅ **DO:**
- Implement Circuit Breaker cho external calls
- Use exponential backoff retry
- Handle partial failures gracefully
- Design for failure (fail-fast or fallback)
- Monitor error rates

❌ **DON'T:**
- Infinite retry loops
- Ignore transient errors
- Cascade failures across services
- Block on slow dependencies

---

## 📝 13. CHECKLIST

### API Gateway

- [x] JWT validation filter
- [x] User info injection vào headers
- [x] Route configuration
- [x] CORS configuration
- [x] Swagger UI aggregation
- [x] Health check endpoint

### User-Group Service

- [x] Database schema (Flyway migrations)
- [x] User_Semester_Membership table
- [x] No FK xuyên services
- [x] gRPC client cho Identity Service
- [x] Circuit Breaker & Retry
- [x] Kafka consumer cho UserDeletedEvent
- [x] Business logic validation
- [x] Soft delete support
- [x] REST API endpoints

### Project Config Service

- [x] REST API + gRPC Server
- [x] REST endpoints routed via API Gateway
- [x] gRPC Authentication Interceptor
- [x] gRPC Authorization Interceptor
- [x] Metadata-based authentication for gRPC
- [x] Token encryption (AES-256-GCM)
- [x] Group validation via User-Group Service

### Identity Service

- [x] JWT generation
- [x] gRPC Server (user data provisioning)
- [x] Kafka producer cho UserDeletedEvent
- [x] Hard delete implementation
- [x] Refresh token management

### Event-Driven

- [x] UserDeletedEvent definition
- [x] Kafka producer config
- [x] Kafka consumer config
- [x] Event handler implementation
- [x] Retry & error handling

---

## 🎯 14. SUMMARY

### Implementation Complete ✅

Hệ thống SAMT đã được implement hoàn chỉnh với:

1. ✅ **API Gateway với JWT Authentication**
2. ✅ **Database Independence (No FK xuyên services)**
3. ✅ **User-Semester Membership Model**
4. ✅ **gRPC Security (Interceptor & Metadata)**
5. ✅ **Business Logic Validation**
6. ✅ **Event-Driven Architecture (Kafka)**
7. ✅ **Resilience Patterns (Circuit Breaker & Retry)**
8. ✅ **Flyway Migrations**
9. ✅ **Swagger UI tại Gateway**

### Next Steps:

1. **Testing:** Unit tests, integration tests, E2E tests
2. **Performance Tuning:** Load testing, query optimization
3. **Security Hardening:** Penetration testing, security audit
4. **Documentation:** API docs, deployment guide, runbooks
5. **Monitoring:** Prometheus metrics, Grafana dashboards, alerting

---

## 📞 SUPPORT

For questions or issues, contact:
- Email: dev-team@samt.com
- Slack: #samt-dev
- GitHub Issues: https://github.com/samt/issues

---

**End of Implementation Guide**
