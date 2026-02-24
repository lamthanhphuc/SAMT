# Implementation & Configuration - User & Group Service

> File này chứa code examples quan trọng và configuration để implement service

## PART 1: CONFIGURATION

### 1. Application Properties

#### application.yml

```yaml
spring:
  application:
    name: usergroup-service
  
  datasource:
    url: jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:usergroup_db}
    username: ${DB_USERNAME:postgres}
    password: ${DB_PASSWORD:postgres}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
  
  flyway:
    enabled: true
    baseline-on-migrate: true
    locations: classpath:db/migration

server:
  port: 8082
  servlet:
    context-path: /api

jwt:
  secret: ${JWT_SECRET:your-secret-key-min-256-bits-long-for-HS256}
  expiration: ${JWT_EXPIRATION:86400000}
```

---

### 2. Security Configuration

#### SecurityConfig.java

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    
    private final JwtAuthenticationFilter jwtAuthFilter;
    
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/**").authenticated()
            )
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
```

#### JwtAuthenticationFilter.java

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtService jwtService;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        final String authHeader = request.getHeader("Authorization");
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        final String jwt = authHeader.substring(7);
        final String userId = jwtService.extractUserId(jwt);
        
        if (userId != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtService.isTokenValid(jwt)) {
                List<String> roles = jwtService.extractRoles(jwt);
                List<SimpleGrantedAuthority> authorities = roles.stream()
                    .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                    .toList();
                
                UsernamePasswordAuthenticationToken authToken = 
                    new UsernamePasswordAuthenticationToken(userId, null, authorities);
                
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }
        
        filterChain.doFilter(request, response);
    }
}
```

---

### 3. Kafka Configuration

**Purpose:** Event-driven communication for user data synchronization (future use)

#### application.yml

```yaml
spring:
  kafka:
    enabled: ${KAFKA_ENABLED:true}
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}
    consumer:
      auto-offset-reset: earliest
      enable-auto-commit: true
      group-id: usergroup-service
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      properties:
        spring.json.trusted.packages: "*"
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
```

#### Environment Variables

```bash
KAFKA_ENABLED=true
KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

#### Kafka Configuration Class

```java
@Configuration
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
public class KafkaConfig {
    
    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;
    
    @Bean
    public ConsumerFactory<String, Object> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "usergroup-service");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        return new DefaultKafkaConsumerFactory<>(config);
    }
    
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Object> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        return factory;
    }
}
```

#### Usage

**Current State:** Infrastructure ready, not actively used  
**Future Use Cases:**
- ✅ Listen to user deletion events from Identity Service
- ✅ Listen to user profile updates (name changes)
- ✅ Publish group membership events to other services

**Example Consumer:**

```java
@Component
@ConditionalOnProperty(name = "spring.kafka.enabled", havingValue = "true")
@Slf4j
public class UserEventConsumer {
    
    @KafkaListener(topics = "user.deleted", groupId = "usergroup-service")
    public void handleUserDeleted(UserDeletedEvent event) {
        log.info("Received user deleted event: {}", event);
        // Future: Handle user deletion cascade (remove from groups, etc.)
    }
    
    @KafkaListener(topics = "user.updated", groupId = "usergroup-service")
    public void handleUserUpdated(UserUpdatedEvent event) {
        log.info("Received user updated event: {}", event);
        // Future: Update cached user data if needed
    }
}
```

---

### 4. Resilience4j Configuration

**Purpose:** Circuit breaker and retry patterns for Identity Service gRPC calls

#### application.yml

```yaml
resilience4j:
  circuitbreaker:
    instances:
      identityService:
        failureRateThreshold: 50          # Open circuit if 50% requests fail
        waitDurationInOpenState: 10s      # Wait 10s before half-open
        slidingWindowSize: 10             # Track last 10 calls
        minimumNumberOfCalls: 5           # Min calls before calculating failure rate

  retry:
    instances:
      identityService:
        maxAttempts: 3                    # Retry up to 3 times
        waitDuration: 500ms               # Wait 500ms between retries
```

#### Usage in gRPC Client

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityServiceGrpcClient {
    
    private final UserServiceGrpc.UserServiceBlockingStub userServiceStub;
    
    @CircuitBreaker(name = "identityService", fallbackMethod = "getUserFallback")
    @Retry(name = "identityService")
    public UserResponse getUser(Long userId) {
        log.debug("Calling Identity Service gRPC: getUser({})", userId);
        
        GetUserRequest request = GetUserRequest.newBuilder()
            .setUserId(String.valueOf(userId))
            .build();
        
        GetUserResponse response = userServiceStub
            .withDeadlineAfter(5, TimeUnit.SECONDS)
            .getUser(request);
        
        return mapToUserResponse(response);
    }
    
    private UserResponse getUserFallback(Long userId, Exception ex) {
        log.error("Circuit breaker fallback: getUser({}) failed", userId, ex);
        throw new ServiceUnavailableException("Identity Service unavailable");
    }
}
```

**Behavior:**
- **Retry:** Automatically retries failed calls up to 3 times with 500ms delay
- **Circuit Breaker:** Opens circuit after 50% failure rate (10 call window)
- **Open State:** Blocks calls for 10 seconds, returns fallback immediately
- **Half-Open:** After 10s, allows test call to check if service recovered

---

### 5. Database Migration

#### V1__initial_schema.sql

⚠️ **CRITICAL:** User-Group Service does NOT have a users table. All user data is in Identity Service.

```sql
-- ============================================
-- TABLE: semesters
-- ============================================
CREATE TABLE semesters (
    id BIGSERIAL PRIMARY KEY,
    semester_code VARCHAR(50) NOT NULL UNIQUE,
    semester_name VARCHAR(100) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_semesters_code ON semesters(semester_code);
CREATE INDEX idx_semesters_active ON semesters(is_active);

COMMENT ON TABLE semesters IS 'Học kỳ (Semester)';
COMMENT ON COLUMN semesters.semester_code IS 'Mã học kỳ (e.g., FALL2024, SPRING2025)';

-- ============================================
-- TABLE: groups
-- ============================================
CREATE TABLE groups (
    id BIGSERIAL PRIMARY KEY,
    group_name VARCHAR(100) NOT NULL,
    semester_id BIGINT NOT NULL,
    lecturer_id BIGINT NOT NULL,  -- Logical reference to Identity Service (NO FK)
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- Logical reference to Identity Service (NO FK)
    version INTEGER NOT NULL DEFAULT 0,
    
    CONSTRAINT fk_groups_semester FOREIGN KEY (semester_id) REFERENCES semesters(id) ON DELETE CASCADE
);

-- Unique constraint: group_name must be unique within a semester (soft delete aware)
CREATE UNIQUE INDEX idx_groups_name_semester_unique 
    ON groups(group_name, semester_id) 
    WHERE deleted_at IS NULL;

CREATE INDEX idx_groups_lecturer_id ON groups(lecturer_id);
CREATE INDEX idx_groups_semester_id ON groups(semester_id);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);

COMMENT ON TABLE groups IS 'Nhóm dự án sinh viên';
COMMENT ON COLUMN groups.lecturer_id IS 'ID Giảng viên phụ trách (logical reference, NO FK)';

-- ============================================
-- TABLE: user_semester_membership
-- ============================================
-- Business Rule: One user can only belong to ONE group per semester
CREATE TABLE user_semester_membership (
    user_id BIGINT NOT NULL,  -- Logical reference to Identity Service (NO FK)
    semester_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    group_role VARCHAR(20) NOT NULL CHECK (group_role IN ('LEADER', 'MEMBER')),
    joined_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP NULL,
    deleted_by BIGINT NULL,  -- Logical reference to Identity Service (NO FK)
    version INTEGER NOT NULL DEFAULT 0,
    
    PRIMARY KEY (user_id, semester_id),
    CONSTRAINT fk_usm_semester FOREIGN KEY (semester_id) REFERENCES semesters(id) ON DELETE CASCADE,
    CONSTRAINT fk_usm_group FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE
);

-- Unique constraint: Only 1 LEADER per group
CREATE UNIQUE INDEX idx_usm_group_leader_unique 
    ON user_semester_membership(group_id) 
    WHERE group_role = 'LEADER' AND deleted_at IS NULL;

CREATE INDEX idx_usm_user_id ON user_semester_membership(user_id);
CREATE INDEX idx_usm_group_id ON user_semester_membership(group_id);
CREATE INDEX idx_usm_semester_id ON user_semester_membership(semester_id);
CREATE INDEX idx_usm_deleted_at ON user_semester_membership(deleted_at);

COMMENT ON TABLE user_semester_membership IS 'User membership in groups by semester';
COMMENT ON COLUMN user_semester_membership.user_id IS 'User ID (logical reference, NO FK)';
COMMENT ON COLUMN user_semester_membership.group_role IS 'Role in group: LEADER or MEMBER';
```

**Key points:**
- NO users table (data in Identity Service)
- `lecturer_id`, `user_id`, `deleted_by` are logical references only
- Composite PK `(user_id, semester_id)` enforces "one group per semester" rule
- Unique index ensures only one leader per group

**Audit Logging:**
- ⚠️ **NO database audit table** - audit logging is handled via application logs (SLF4J) only
- All critical operations are logged using structured JSON format for centralized log aggregation

---

### 4. Docker Configuration

#### Dockerfile

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> **Note:** Updated to Java 21 (from Java 17) per project requirements.

#### docker-compose.yml

```yaml
version: '3.8'

services:
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: usergroup_db
      POSTGRES_USER: postgres
      POSTGRES_PASSWORD: postgres
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data

  usergroup-service:
    build: .
    environment:
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: usergroup_db
      DB_USERNAME: postgres
      DB_PASSWORD: postgres
      JWT_SECRET: your-secret-key
    ports:
      - "8082:8082"
    depends_on:
      - postgres

volumes:
  postgres_data:
```

---

### 5. Maven Dependencies

```xml
<dependencies>
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
        <artifactId>spring-boot-starter-security</artifactId>
    </dependency>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-validation</artifactId>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
    </dependency>
    <dependency>
        <groupId>org.flywaydb</groupId>
        <artifactId>flyway-core</artifactId>
    </dependency>
    <dependency>
        <groupId>io.jsonwebtoken</groupId>
        <artifactId>jjwt-api</artifactId>
        <version>0.11.5</version>
    </dependency>
    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
    </dependency>
    <dependency>
        <groupId>org.mapstruct</groupId>
        <artifactId>mapstruct</artifactId>
        <version>1.5.5.Final</version>
    </dependency>
</dependencies>
```

---

## PART 2: IMPLEMENTATION CODE EXAMPLES

### 1. Entities

⚠️ **IMPORTANT:** No User entity exists in User-Group Service. User data is managed by Identity Service.

#### Semester.java

```java
@Entity
@Table(name = "semesters")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Semester {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "semester_code", nullable = false, unique = true, length = 50)
    private String semesterCode;  // e.g., "SPRING2025", "FALL2024"
    
    @Column(name = "semester_name", nullable = false, length = 100)
    private String semesterName;  // e.g., "Spring Semester 2025"
    
    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;
    
    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
}
```

#### Group.java

```java
@Entity
@Table(name = "groups")
@SQLRestriction("deleted_at IS NULL")  // Hibernate 6.x
// For Hibernate 5.x use: @Where(clause = "deleted_at IS NULL")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "group_name", nullable = false, length = 100)
    private String groupName;
    
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;  // FK to semesters table
    
    @Column(name = "lecturer_id", nullable = false)
    private Long lecturerId;  // Logical reference to Identity Service (NO FK)
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;  // Logical reference to Identity Service (NO FK)
    
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;  // Optimistic locking
    
    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public void softDelete(Long deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }
}
```

#### UserSemesterMembership.java

```java
@Entity
@Table(name = "user_semester_membership")
@SQLRestriction("deleted_at IS NULL")  // Hibernate 6.x
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserSemesterMembership {
    
    @EmbeddedId
    private UserSemesterMembershipId id;  // Composite PK (userId, semesterId)
    
    @Column(name = "group_id", nullable = false)
    private Long groupId;  // FK to groups table
    
    @Column(name = "group_role", nullable = false, length = 20)
    @Enumerated(EnumType.STRING)
    private GroupRole groupRole;  // LEADER or MEMBER
    
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;
    
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    @Column(name = "deleted_by")
    private Long deletedBy;  // Logical reference to Identity Service (NO FK)
    
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;  // Optimistic locking
    
    @PrePersist
    protected void onCreate() {
        joinedAt = Instant.now();
        updatedAt = Instant.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }
    
    public void softDelete(Long deletedByUserId) {
        this.deletedAt = Instant.now();
        this.deletedBy = deletedByUserId;
        this.updatedAt = Instant.now();
    }
}

// Composite PK class
@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserSemesterMembershipId implements Serializable {
    @Column(name = "user_id", nullable = false)
    private Long userId;  // Logical reference to Identity Service (NO FK)
    
    @Column(name = "semester_id", nullable = false)
    private Long semesterId;  // FK to semesters table
}

// Enum
public enum GroupRole {
    LEADER,
    MEMBER
}
```

---

### 2. Repositories

#### SemesterRepository.java

```java
@Repository
public interface SemesterRepository extends JpaRepository<Semester, Long> {
    Optional<Semester> findBySemesterCode(String semesterCode);
    Optional<Semester> findByIsActiveTrue();
    boolean existsBySemesterCode(String semesterCode);
}
```

#### GroupRepository.java

```java
@Repository
public interface GroupRepository extends JpaRepository<Group, Long> {
    Optional<Group> findByIdAndDeletedAtIsNull(Long id);
    
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Group g WHERE g.groupName = :groupName AND g.semesterId = :semesterId " +
           "AND g.deletedAt IS NULL")
    boolean existsByGroupNameAndSemesterId(String groupName, Long semesterId);
    
    @Query("SELECT g FROM Group g WHERE g.semesterId = :semesterId AND g.deletedAt IS NULL")
    List<Group> findAllBySemesterId(Long semesterId);
    
    @Query("SELECT g FROM Group g WHERE g.lecturerId = :lecturerId AND g.deletedAt IS NULL")
    List<Group> findAllByLecturerId(Long lecturerId);
}
```

#### UserSemesterMembershipRepository.java

```java
@Repository
public interface UserSemesterMembershipRepository 
        extends JpaRepository<UserSemesterMembership, UserSemesterMembershipId> {
    
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.id.userId = :userId AND usm.groupId = :groupId " +
           "AND usm.deletedAt IS NULL")
    Optional<UserSemesterMembership> findByUserIdAndGroupId(Long userId, Long groupId);
    
    @Query("SELECT CASE WHEN COUNT(usm) > 0 THEN true ELSE false END " +
           "FROM UserSemesterMembership usm " +
           "WHERE usm.id.userId = :userId AND usm.id.semesterId = :semesterId " +
           "AND usm.deletedAt IS NULL")
    boolean existsByUserIdAndSemesterId(Long userId, Long semesterId);
    
    @Query("SELECT CASE WHEN COUNT(usm) > 0 THEN true ELSE false END " +
           "FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = :role " +
           "AND usm.deletedAt IS NULL")
    boolean existsByGroupIdAndRole(Long groupId, GroupRole role);
    
    /**
     * Find leader with pessimistic lock to prevent race conditions.
     * CRITICAL: Use this for promote/demote role operations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = 'LEADER' " +
           "AND usm.deletedAt IS NULL")
    Optional<UserSemesterMembership> findLeaderByGroupIdWithLock(Long groupId);
    
    @Query("SELECT COUNT(usm) FROM UserSemesterMembership usm " +
           "WHERE usm.groupId = :groupId AND usm.groupRole = 'MEMBER' " +
           "AND usm.deletedAt IS NULL")
    long countMembersByGroupId(Long groupId);
    
    @Query("SELECT usm FROM UserSemesterMembership usm " +
           "WHERE usm.id.userId = :userId AND usm.deletedAt IS NULL")
    List<UserSemesterMembership> findAllByUserId(Long userId);
}
```

> **IMPORTANT:** Use `findLeaderByGroupIdWithLock()` in `assignRole()` method to prevent race conditions when multiple requests try to assign LEADER simultaneously.
```

---

### 3. Service Implementation (Key Methods)

#### GroupMemberServiceImpl.java

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupMemberServiceImpl implements GroupMemberService {
    
    private final UserSemesterMembershipRepository membershipRepository;
    private final GroupRepository groupRepository;
    private final ResilientIdentityServiceClient identityClient;
    
    @Override
    @Transactional
    public MemberResponse addMember(Long groupId, AddMemberRequest request) {
        // Validate user exists and is STUDENT via gRPC
        UserValidationResult userValidation = identityClient.verifyUser(
            request.getUserId(), Role.STUDENT
        );
        
        if (!userValidation.isValid()) {
            throw ResourceNotFoundException.userNotFound(request.getUserId());
        }
        
        if (!userValidation.isActive()) {
            throw ConflictException.userInactive(request.getUserId());
        }
        
        // Validate group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Check user not already in group for this semester
        if (membershipRepository.existsByUserIdAndSemesterId(
                request.getUserId(), group.getSemesterId())) {
            throw ConflictException.userAlreadyInGroupSameSemester(
                request.getUserId(), group.getSemesterId()
            );
        }
        
        // If isLeader=true, check no existing leader
        GroupRole role = request.getIsLeader() ? GroupRole.LEADER : GroupRole.MEMBER;
        if (role == GroupRole.LEADER) {
            if (membershipRepository.existsByGroupIdAndRole(groupId, GroupRole.LEADER)) {
                throw ConflictException.leaderAlreadyExists(groupId);
            }
        }
        
        // Create membership
        UserSemesterMembershipId id = new UserSemesterMembershipId(
            request.getUserId(), 
            group.getSemesterId()
        );
        
        UserSemesterMembership membership = UserSemesterMembership.builder()
            .id(id)
            .groupId(groupId)
            .groupRole(role)
            .build();
        
        membershipRepository.save(membership);
        
        return memberMapper.toResponse(membership, userValidation);
    }
    
    @Override
    @Transactional
    public MemberResponse promoteToLeader(Long groupId, Long userId) {
        // Validate group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Validate membership exists
        UserSemesterMembership membership = membershipRepository
            .findByUserIdAndGroupId(userId, groupId)
            .orElseThrow(() -> ResourceNotFoundException.membershipNotFound(userId));
        
        // Find and demote existing leader with PESSIMISTIC LOCK
        Optional<UserSemesterMembership> existingLeader = 
            membershipRepository.findLeaderByGroupIdWithLock(groupId);
        
        if (existingLeader.isPresent() && 
            !existingLeader.get().getId().getUserId().equals(userId)) {
            UserSemesterMembership oldLeader = existingLeader.get();
            oldLeader.setGroupRole(GroupRole.MEMBER);
            membershipRepository.save(oldLeader);
        }
        
        // Promote to leader
        membership.setGroupRole(GroupRole.LEADER);
        membershipRepository.save(membership);
        
        return memberMapper.toResponse(membership);
    }
    
    @Override
    @Transactional
    public void removeMember(Long groupId, Long userId) {
        // Validate group exists
        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        UserSemesterMembership membership = membershipRepository
            .findByUserIdAndGroupId(userId, groupId)
            .orElseThrow(() -> ResourceNotFoundException.membershipNotFound(userId));
        
        // If removing leader, check no other members
        if (membership.getGroupRole() == GroupRole.LEADER) {
            long memberCount = membershipRepository.countMembersByGroupId(groupId);
            if (memberCount > 0) {
                throw ConflictException.cannotRemoveLeader();
            }
        }
        
        // Get current user ID from SecurityContext for audit
        Long currentUserId = getCurrentUserId();
        membership.softDelete(currentUserId);
        membershipRepository.save(membership);
    }
}
```

---

## Transaction Boundaries & External Calls

**Critical Rule:** All gRPC calls to Identity Service MUST occur **OUTSIDE** `@Transactional` boundaries.

### Why This Matters

- **Database locks:** Transactions hold locks until commit/rollback
- **gRPC timeout:** Identity Service calls have 5-second timeout
- **Connection pool:** Long-running transactions exhaust the pool
- **Deadlock risk:** Distributed calls inside transactions can cause cross-service deadlocks

### Implementation Pattern

✅ **CORRECT: Fetch external data BEFORE transaction**

```java
@Override
public GroupResponse createGroup(CreateGroupRequest request) {
    // 1️⃣ gRPC call OUTSIDE transaction
    UserDto lecturer = identityServiceClient.getUser(request.getLecturerId());
    
    if (lecturer == null || !lecturer.getRole().equals("LECTURER")) {
        throw ValidationException.invalidLecturer();
    }
    
    // 2️⃣ Database writes INSIDE transaction
    return createGroupTransactional(request, lecturer);
}

@Transactional
protected GroupResponse createGroupTransactional(CreateGroupRequest request, UserDto lecturer) {
    // Database operations only - no external calls
    Semester semester = semesterRepository.findByCode(request.getSemesterCode())
        .orElseThrow(() -> ResourceNotFoundException.semesterNotFound());
    
    Group group = Group.builder()
        .groupName(request.getGroupName())
        .semesterId(semester.getId())
        .lecturerId(lecturer.getId())
        .build();
    
    groupRepository.save(group);
    return groupMapper.toResponse(group);
}
```

❌ **WRONG: gRPC call inside transaction**

```java
@Override
@Transactional  // ❌ Transaction starts here
public GroupResponse createGroup(CreateGroupRequest request) {
    // ❌ DANGER: 5-second gRPC timeout while holding DB locks
    UserDto lecturer = identityServiceClient.getUser(request.getLecturerId());
    
    // If Identity Service is slow/down, this transaction holds locks for 5+ seconds
    // Other requests trying to access groups table will be blocked
    
    Semester semester = semesterRepository.findByCode(request.getSemesterCode())
        .orElseThrow(() -> ResourceNotFoundException.semesterNotFound());
    
    // ... rest of code
}
```

### Rollback Behavior

**Question:** What happens if Identity Service returns NOT_FOUND mid-operation?

**Answer:** 
- If gRPC call is **before** `@Transactional`: No database changes made → No rollback needed ✅
- If gRPC call is **inside** `@Transactional`: Exception triggers rollback, but locks were held unnecessarily ❌

### Service Layer Pattern Summary

| Operation | Pattern | Reason |
|-----------|---------|--------|
| `createGroup()` | Fetch user → `@Transactional` write | Validate lecturer before DB lock |
| `updateGroup()` | Fetch user → `@Transactional` write + version check | Concurrent updates need optimistic locking |
| `addMember()` | Fetch user → `@Transactional` write | Validate user exists before group modification |
| `removeMember()` | No external call → `@Transactional` only | All data local to User-Group Service |

**Golden Rule:** External calls = Pre-transaction validation. Database writes = Inside single transaction with version checking.

---

## Concurrent Update Handling & Optimistic Locking

### Purpose

The `version` field enables **optimistic locking** to prevent lost updates when multiple users modify the same group or membership simultaneously.

### How It Works

1. **Read:** JPA loads entity with current `version` value (e.g., `version = 5`)
2. **Modify:** Business logic updates entity fields
3. **Save:** JPA executes:
   ```sql
   UPDATE groups 
   SET group_name = ?, lecturer_id = ?, version = version + 1
   WHERE id = ? AND version = 5
   ```
4. **Conflict Detection:**
   - If another transaction already updated the row → `version` is now 6
   - `WHERE version = 5` matches 0 rows
   - JPA throws `OptimisticLockException`

### Example Scenario

**Timeline:**
```
T1 (Admin A)                    T2 (Admin B)
─────────────────────────────────────────────────────
Read group (version=5)          
                                Read group (version=5)
Modify lecturer_id = 100        
                                Modify lecturer_id = 200
Save → version becomes 6 ✅     
                                Save → CONFLICT ❌
                                (version is now 6, not 5)
```

### REST API Response

When `OptimisticLockException` occurs, return **HTTP 409 CONFLICT**:

```json
{
  "error": {
    "code": "CONFLICT",
    "message": "Group was modified by another user. Please refresh and try again.",
    "field": "version"
  },
  "timestamp": "2026-02-18T12:00:00Z"
}
```

### Exception Handler

```java
@ExceptionHandler(OptimisticLockException.class)
public ResponseEntity<ErrorResponse> handleOptimisticLock(OptimisticLockException ex) {
    ErrorResponse error = ErrorResponse.builder()
        .code("CONFLICT")
        .message("Resource was modified by another user. Please refresh and try again.")
        .field("version")
        .build();
    return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
}
```

### Which Operations Need Optimistic Locking?

| Entity | Operation | Needs Version Check | Reason |
|--------|-----------|---------------------|--------|
| `Group` | Update lecturer | ✅ YES | Multiple admins can change lecturer |
| `Group` | Soft delete | ✅ YES | Admin might delete while another updates |
| `Group` | Restore | ✅ YES | Rare but possible concurrent restore/delete |
| `UserSemesterMembership` | Promote to LEADER | ✅ YES | Multiple admins promoting different members |
| `UserSemesterMembership` | Change group | ✅ YES | Admin moving member while another removes |
| `UserSemesterMembership` | Soft delete | ✅ YES | Concurrent removal/promotion |

### Best Practices

1. **Always include version in UPDATE queries** - JPA does this automatically with `@Version`
2. **Don't manually set version** - Let JPA manage it
3. **Retry logic in clients** - Frontend should catch 409 and allow user to refresh
4. **Log conflicts** - Track frequent conflicts (might indicate workflow issues)

**Result:** Zero risk of lost updates. System is safe under concurrent writes.

---

### 4. Controllers

#### GroupController.java

```java
@RestController
@RequestMapping("/groups")
@RequiredArgsConstructor
@Validated
public class GroupController {
    
    private final GroupService groupService;
    private final GroupMemberService memberService;
    
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<GroupResponse> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(groupService.createGroup(request));
    }
    
    @PostMapping("/{groupId}/members")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> addMember(
            @PathVariable Long groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(memberService.addMember(groupId, request));
    }
    
    @PutMapping("/{groupId}/members/{userId}/promote")
    @PreAuthorize("hasRole('ADMIN') or hasRole('LECTURER')")
    public ResponseEntity<MemberResponse> promoteToLeader(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        return ResponseEntity.ok(memberService.promoteToLeader(groupId, userId));
    }
    
    @DeleteMapping("/{groupId}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable Long groupId,
            @PathVariable Long userId) {
        memberService.removeMember(groupId, userId);
        return ResponseEntity.noContent().build();
    }
}
```

---

### 5. DTOs

#### CreateGroupRequest.java

```java
@Data
public class CreateGroupRequest {
    @NotBlank(message = "Group name is required")
    @Size(min = 3, max = 50)
    @Pattern(regexp = "^[A-Z]{2,4}[0-9]{2,4}-G[0-9]+$")
    private String groupName;
    
    @NotNull(message = "Semester ID is required")
    @Positive(message = "Semester ID must be positive")
    private Long semesterId;
    
    @NotNull(message = "Lecturer ID is required")
    @Positive(message = "Lecturer ID must be positive")
    private Long lecturerId;
}
```

#### AddMemberRequest.java

```java
@Data
public class AddMemberRequest {
    @NotNull(message = "User ID is required")
    @Positive(message = "User ID must be positive")
    private Long userId;
    
    @NotNull(message = "isLeader is required")
    private Boolean isLeader;
}
```

---

## PART 3: CRITICAL IMPLEMENTATION WARNINGS

### 1. Soft Delete & `existsById()` Bug

**PROBLEM:** JPA's `existsById()` method does NOT respect `@SQLRestriction` annotation. It queries directly by primary key without applying the soft delete filter.

**IMPACT:** Code like below will return `true` for soft-deleted records:
```java
// ❌ BUGGY - returns true even if group is soft-deleted
if (!groupRepository.existsById(groupId)) {
    throw ResourceNotFoundException.groupNotFound(groupId);
}
```

**SOLUTION:** Always use `findById().isPresent()` or custom JPQL query:
```java
// ✅ CORRECT - respects @SQLRestriction
if (groupRepository.findById(groupId).isEmpty()) {
    throw ResourceNotFoundException.groupNotFound(groupId);
}

// ✅ ALSO CORRECT - explicit JPQL
@Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
       "FROM Group g WHERE g.id = :id AND g.deletedAt IS NULL")
boolean existsByIdAndNotDeleted(Long id);
```

**AFFECTED REPOSITORIES:** All repositories with soft-delete entities (Group, UserSemesterMembership).

---

### 2. Race Condition in Leader Assignment

**PROBLEM:** When two requests simultaneously try to assign LEADER to different users in the same group, both may succeed if not properly locked.

**SOLUTION:** Use pessimistic locking:
```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT usm FROM UserSemesterMembership usm " +
       "WHERE usm.groupId = :groupId AND usm.groupRole = 'LEADER' AND usm.deletedAt IS NULL")
Optional<UserSemesterMembership> findLeaderByGroupIdWithLock(Long groupId);
```

---

### 3. Authorization Edge Cases

| Actor | UC21 (Get Profile) | UC22 (Update Profile) |
|-------|--------------------|-----------------------|
| ADMIN | ✅ All users | ✅ All users |
| LECTURER | ⚠️ Only STUDENT targets | ❌ Not allowed |
| STUDENT | ✅ Self only | ✅ Self only |

**Note:** LECTURER viewing non-STUDENT target should return `403 FORBIDDEN`. If target role cannot be verified (cross-service), allow with warning log.

#### UC21: LECTURER Authorization - gRPC Fallback Behavior

**Design Decision:** Availability > Strict Security during outages

**Implementation:**

```java
if (isLecturer) {
    try {
        // Attempt to verify target user role
        GetUserRoleResponse roleResponse = identityServiceClient.getUserRole(targetUserId);
        
        if (roleResponse.getRole() == UserRole.STUDENT) {
            return; // ALLOW - target is STUDENT
        } else {
            throw ForbiddenException.lecturerCanOnlyViewStudents(); // 403
        }
        
    } catch (StatusRuntimeException e) {
        // gRPC failure → FALLBACK BEHAVIOR
        log.warn("LECTURER {} viewing user {} - gRPC failed ({}). Allowing per spec (fallback).", 
                actorId, targetUserId, e.getStatus().getCode());
        return; // ALLOW with warning
    }
}
```

**Behavior Matrix:**

| Scenario | gRPC Response | Target Role | Action | HTTP Status |
|----------|---------------|-------------|--------|-------------|
| Normal | SUCCESS | STUDENT | Allow | 200 OK |
| Normal | SUCCESS | LECTURER | Deny | 403 FORBIDDEN |
| Normal | SUCCESS | ADMIN | Deny | 403 FORBIDDEN |
| Outage | UNAVAILABLE | Unknown | Allow + log warning | 200 OK |
| Outage | DEADLINE_EXCEEDED | Unknown | Allow + log warning | 200 OK |

**Rationale:**

1. **Availability Priority:** During Identity Service outage, LECTURER should still access system
2. **Graceful Degradation:** Temporary relaxation of strict security better than complete denial
3. **Audit Trail:** Warning logs track all fallback cases for security review
4. **Risk Mitigation:** LECTURER accessing non-STUDENT during outage is low risk vs. system unavailability

**Trade-offs:**
- ✅ System remains available during Identity Service outage
- ✅ LECTURER can continue work (view students)
- ⚠️ LECTURER might access non-STUDENT user during outage (logged)
- ⚠️ Security slightly relaxed during outage

**Alternatives Considered:**
- **Strict deny on gRPC failure:** Rejected - breaks system during outage
- **Cache user roles:** Rejected - stale data risk, cache invalidation complexity
- **Fallback to secondary Identity Service:** Future enhancement

**Monitoring:**

Watch for warning logs indicating fallback usage:
```
WARN: LECTURER <uuid> viewing user <uuid> - gRPC failed (UNAVAILABLE). Allowing per spec (fallback).
```

High frequency suggests Identity Service stability issue.

---

### 4. Deferred Features

| Feature | Status | Reason |
|---------|--------|--------|
| List Users - role filter | Deferred | Roles stored in identity-service |
| Verify lecturer role on create group | Deferred | Cross-service verification needed |
| Verify target is STUDENT for LECTURER | Partial | Allow with warning if cannot verify |

---

## Summary

Với 5 file này, bạn có đầy đủ thông tin để:

1. ✅ Hiểu requirements và use cases ([01_REQUIREMENTS.md](01_REQUIREMENTS.md))
2. ✅ Thiết kế database và business rules ([02_DESIGN.md](02_DESIGN.md))
3. ✅ Implement APIs theo spec ([03_API_SPECIFICATION.md](03_API_SPECIFICATION.md))
4. ✅ Validate input và handle exceptions ([04_VALIDATION_EXCEPTIONS.md](04_VALIDATION_EXCEPTIONS.md))
5. ✅ Code và configure service (file này - [05_IMPLEMENTATION_CONFIG.md](05_IMPLEMENTATION_CONFIG.md))

**Bắt đầu code từ:**
1. Setup project với Maven dependencies
2. Tạo database migrations (Flyway)
3. Implement Entities
4. Implement Repositories
5. Implement Services với business logic
6. Implement Controllers
7. Configure Security (JWT)
8. Test APIs
