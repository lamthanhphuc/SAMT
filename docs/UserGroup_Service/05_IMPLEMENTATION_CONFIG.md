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

### 3. Database Migration

#### V1__create_users_table.sql

```sql
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_deleted_at ON users(deleted_at);
```

#### V2__create_groups_table.sql

```sql
CREATE TABLE groups (
    id UUID PRIMARY KEY,
    group_name VARCHAR(50) NOT NULL,
    semester VARCHAR(20) NOT NULL,
    lecturer_id UUID NOT NULL,
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_groups_lecturer FOREIGN KEY (lecturer_id) REFERENCES users(id),
    CONSTRAINT uq_group_semester UNIQUE (group_name, semester)
);

CREATE INDEX idx_groups_semester ON groups(semester);
CREATE INDEX idx_groups_deleted_at ON groups(deleted_at);
```

#### V3__create_user_groups_table.sql

```sql
CREATE TABLE user_groups (
    user_id UUID NOT NULL,
    group_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL CHECK (role IN ('LEADER', 'MEMBER')),
    deleted_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, group_id),
    CONSTRAINT fk_user_groups_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_groups_group FOREIGN KEY (group_id) REFERENCES groups(id)
);

CREATE UNIQUE INDEX uq_group_leader 
ON user_groups(group_id) 
WHERE role = 'LEADER' AND deleted_at IS NULL;
```

---

### 4. Docker Configuration

#### Dockerfile

```dockerfile
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
```

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

#### User.java

```java
@Entity
@Table(name = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "full_name", nullable = false)
    private String fullName;
    
    @Enumerated(EnumType.STRING)
    private UserStatus status;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
```

#### Group.java

```java
@Entity
@Table(name = "groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Group {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "group_name", nullable = false)
    private String groupName;
    
    @Column(nullable = false)
    private String semester;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private User lecturer;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
    
    public void softDelete() {
        this.deletedAt = Instant.now();
    }
}
```

#### UserGroup.java

```java
@Entity
@Table(name = "user_groups")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@IdClass(UserGroupId.class)
public class UserGroup {
    @Id
    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user;
    
    @Id
    @ManyToOne
    @JoinColumn(name = "group_id")
    private Group group;
    
    @Enumerated(EnumType.STRING)
    private GroupRole role;
    
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
```

---

### 2. Repositories

#### UserRepository.java

```java
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByIdAndDeletedAtIsNull(UUID id);
    
    @Query("SELECT u FROM User u WHERE u.deletedAt IS NULL")
    Page<User> findAllActive(Pageable pageable);
}
```

#### GroupRepository.java

```java
@Repository
public interface GroupRepository extends JpaRepository<Group, UUID> {
    Optional<Group> findByIdAndDeletedAtIsNull(UUID id);
    
    @Query("SELECT CASE WHEN COUNT(g) > 0 THEN true ELSE false END " +
           "FROM Group g WHERE g.groupName = :groupName AND g.semester = :semester " +
           "AND g.deletedAt IS NULL")
    boolean existsByGroupNameAndSemester(String groupName, String semester);
}
```

#### UserGroupRepository.java

```java
@Repository
public interface UserGroupRepository extends JpaRepository<UserGroup, UserGroupId> {
    
    @Query("SELECT ug FROM UserGroup ug " +
           "WHERE ug.user.id = :userId AND ug.group.id = :groupId " +
           "AND ug.deletedAt IS NULL")
    Optional<UserGroup> findByUserIdAndGroupId(UUID userId, UUID groupId);
    
    @Query("SELECT CASE WHEN COUNT(ug) > 0 THEN true ELSE false END " +
           "FROM UserGroup ug JOIN ug.group g " +
           "WHERE ug.user.id = :userId AND g.semester = :semester " +
           "AND ug.deletedAt IS NULL AND g.deletedAt IS NULL")
    boolean existsByUserIdAndSemester(UUID userId, String semester);
    
    @Query("SELECT CASE WHEN COUNT(ug) > 0 THEN true ELSE false END " +
           "FROM UserGroup ug WHERE ug.group.id = :groupId AND ug.role = :role " +
           "AND ug.deletedAt IS NULL")
    boolean existsByGroupIdAndRole(UUID groupId, GroupRole role);
    
    @Query("SELECT COUNT(ug) FROM UserGroup ug " +
           "WHERE ug.group.id = :groupId AND ug.role = 'MEMBER' " +
           "AND ug.deletedAt IS NULL")
    long countMembersByGroupId(UUID groupId);
}
```

---

### 3. Service Implementation (Key Methods)

#### GroupMemberServiceImpl.java

```java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupMemberServiceImpl implements GroupMemberService {
    
    private final UserGroupRepository userGroupRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    
    @Override
    @Transactional
    public MemberResponse addMember(UUID groupId, AddMemberRequest request) {
        // Validate user exists and is active
        User user = userRepository.findByIdAndDeletedAtIsNull(request.getUserId())
            .orElseThrow(() -> ResourceNotFoundException.userNotFound(request.getUserId()));
        
        if (user.getStatus() == UserStatus.INACTIVE) {
            throw ConflictException.userInactive(user.getId());
        }
        
        // Validate group exists
        Group group = groupRepository.findByIdAndDeletedAtIsNull(groupId)
            .orElseThrow(() -> ResourceNotFoundException.groupNotFound(groupId));
        
        // Check user not already in group for this semester
        if (userGroupRepository.existsByUserIdAndSemester(
                request.getUserId(), group.getSemester())) {
            throw ConflictException.userAlreadyInGroupSameSemester(
                request.getUserId(), group.getSemester()
            );
        }
        
        // If isLeader=true, check no existing leader
        GroupRole role = request.getIsLeader() ? GroupRole.LEADER : GroupRole.MEMBER;
        if (role == GroupRole.LEADER) {
            if (userGroupRepository.existsByGroupIdAndRole(groupId, GroupRole.LEADER)) {
                throw ConflictException.leaderAlreadyExists(groupId);
            }
        }
        
        // Create membership
        UserGroup membership = UserGroup.builder()
            .user(user)
            .group(group)
            .role(role)
            .build();
        
        userGroupRepository.save(membership);
        
        return memberMapper.toResponse(membership);
    }
    
    @Override
    @Transactional
    public MemberResponse assignRole(UUID groupId, UUID userId, AssignRoleRequest request) {
        // Validate membership exists
        UserGroup membership = userGroupRepository.findByUserIdAndGroupId(userId, groupId)
            .orElseThrow(() -> ResourceNotFoundException.userNotFound(userId));
        
        GroupRole newRole = GroupRole.valueOf(request.getRole());
        
        // If assigning LEADER, demote old leader
        if (newRole == GroupRole.LEADER) {
            Optional<UserGroup> existingLeader = 
                userGroupRepository.findLeaderByGroupId(groupId);
            
            if (existingLeader.isPresent() && 
                !existingLeader.get().getUser().getId().equals(userId)) {
                UserGroup oldLeader = existingLeader.get();
                oldLeader.setRole(GroupRole.MEMBER);
                userGroupRepository.save(oldLeader);
            }
        }
        
        // Assign new role
        membership.setRole(newRole);
        userGroupRepository.save(membership);
        
        return memberMapper.toResponse(membership);
    }
    
    @Override
    @Transactional
    public void removeMember(UUID groupId, UUID userId) {
        UserGroup membership = userGroupRepository.findByUserIdAndGroupId(userId, groupId)
            .orElseThrow(() -> ResourceNotFoundException.userNotFound(userId));
        
        // If removing leader, check no other members
        if (membership.getRole() == GroupRole.LEADER) {
            long memberCount = userGroupRepository.countMembersByGroupId(groupId);
            if (memberCount > 0) {
                throw ConflictException.cannotRemoveLeader();
            }
        }
        
        membership.softDelete();
        userGroupRepository.save(membership);
    }
}
```

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
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(memberService.addMember(groupId, request));
    }
    
    @PutMapping("/{groupId}/members/{userId}/role")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<MemberResponse> assignRole(
            @PathVariable UUID groupId,
            @PathVariable UUID userId,
            @Valid @RequestBody AssignRoleRequest request) {
        return ResponseEntity.ok(memberService.assignRole(groupId, userId, request));
    }
    
    @DeleteMapping("/{groupId}/members/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> removeMember(
            @PathVariable UUID groupId,
            @PathVariable UUID userId) {
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
    
    @NotBlank(message = "Semester is required")
    @Pattern(regexp = "^(Spring|Summer|Fall|Winter)[0-9]{4}$")
    private String semester;
    
    @NotNull(message = "Lecturer ID is required")
    private UUID lecturerId;
}
```

#### AddMemberRequest.java

```java
@Data
public class AddMemberRequest {
    @NotNull(message = "User ID is required")
    private UUID userId;
    
    @NotNull(message = "isLeader is required")
    private Boolean isLeader;
}
```

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
