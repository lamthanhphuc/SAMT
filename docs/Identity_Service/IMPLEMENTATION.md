# Implementation Guide – Identity Service

**Version:** 1.0  
**Date:** January 30, 2026  
**Target:** AI Code Generator / Backend Developer

---

## Table of Contents

1. [Configuration](#configuration)
2. [Service Layer Implementation](#service-layer-implementation)
3. [Security Implementation](#security-implementation)
4. [Transaction Management](#transaction-management)
5. [Error Handling](#error-handling)
6. [Testing Requirements](#testing-requirements)

---

## Configuration

### Environment Variables

```bash
# Database
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/identityservice_db
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=postgres123

# Redis (Session & Caching)
SPRING_DATA_REDIS_HOST=localhost
SPRING_DATA_REDIS_PORT=6379

# Security
JWT_SECRET=your-256-bit-secret-key-must-be-same-across-all-services

# gRPC Server
GRPC_SERVER_PORT=9091

# Database Pool
DB_POOL_SIZE=10
DB_POOL_MIN_IDLE=2
```

### application.yml

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      connection-timeout: 30000          # 30 seconds
      maximum-pool-size: ${DB_POOL_SIZE:10}
      minimum-idle: ${DB_POOL_MIN_IDLE:2}
      idle-timeout: 600000               # 10 minutes
      max-lifetime: 1800000              # 30 minutes
      leak-detection-threshold: 60000   # 60 seconds (enables connection leak detection)
      
  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:localhost}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2
          
  jpa:
    hibernate:
      ddl-auto: validate                # Use Flyway for migrations
    show-sql: false
    open-in-view: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        format_sql: true
        jdbc:
          time_zone: UTC
          batch_size: 20
        order_inserts: true
        order_updates: true

grpc:
  server:
    port: ${GRPC_SERVER_PORT:9091}

jwt:
  secret: ${JWT_SECRET}
  access-token-expiration-ms: 900000   # 15 minutes
  refresh-token-expiration-days: 7
```

### Hikari Connection Pool

**Purpose:** High-performance JDBC connection pooling

**Key Settings:**
- `maximum-pool-size`: 10 connections (adjust based on load)
- `minimum-idle`: 2 connections always ready
- `leak-detection-threshold`: 60 seconds - logs warning if connection held too long
- `connection-timeout`: 30 seconds max wait for connection from pool
- `max-lifetime`: 30 minutes - connections recycled to prevent stale connections

**Monitoring:**
- Connection leaks logged automatically after 60s threshold
- Monitor pool saturation via Hikari metrics
- Adjust `maximum-pool-size` based on `(core_count * 2) + effective_spindle_count`

### Redis Configuration

**Purpose:** Session management, token blacklisting (future), caching

**Lettuce Client:** Default Spring Data Redis client (async, thread-safe)

**Key Settings:**
- `max-active`: 8 connections max
- `max-idle`: 8 connections kept idle
- `min-idle`: 2 connections always ready
- `timeout`: 3 seconds for Redis operations

**Usage:**
- ✅ Currently: Available for future session management
- ⚠️ **NOT used for refresh tokens** - tokens stored in PostgreSQL for ACID guarantees
- 🔄 Future: JWT token blacklisting on logout (optional optimization)

---

## Service Layer Implementation

### AuthService.register()

**Pseudo-code:**

```java
@Service
@Transactional
public class AuthService {
    
    /**
     * Register new user account
     * 
     * TRANSACTION: REQUIRED
     * AUDIT: USER_REGISTERED
     */
    public AuthResponse register(RegisterRequest request) {
        // Step 1: Validate input (MUST)
        validateRegisterRequest(request);  // Throws ValidationException
        
        // Step 2: Check email uniqueness (MUST - before password hashing)
        if (userRepository.existsByEmail(request.email())) {
            throw new EmailAlreadyExistsException("Email already registered");
        }
        
        // Step 3: Hash password with BCrypt (MUST - strength 10)
        String passwordHash = passwordEncoder.encode(request.password());
        
        // Step 4: Create user entity
        User user = User.builder()
            .email(request.email())
            .passwordHash(passwordHash)
            .fullName(request.fullName())
            .role(Role.STUDENT)              // MUST: Force STUDENT role
            .status(UserStatus.ACTIVE)       // MUST: Default to ACTIVE
            .build();
        
        // Step 5: Save user to database
        user = userRepository.save(user);    // Transaction commit point
        
        // Step 6: Generate access token (JWT, 15 min)
        String accessToken = jwtService.generateAccessToken(user);
        
        // Step 7: Generate refresh token (UUID, 7 days)
        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
        
        // Step 8: Log audit event (async, non-blocking)
        auditService.logAsync(AuditLog.builder()
            .entityType("User")
            .entityId(user.getId())
            .action("USER_REGISTERED")
            .outcome(Outcome.SUCCESS)
            .metadata(Map.of("email", user.getEmail(), "role", user.getRole()))
            .build());
        
        // Step 9: Return response
        return AuthResponse.builder()
            .user(UserDTO.fromEntity(user))
            .accessToken(accessToken)
            .refreshToken(refreshToken.getToken())
            .tokenType("Bearer")
            .expiresIn(900)  // 15 minutes in seconds
            .build();
    }
    
    /**
     * Validation rules for registration
     */
    private void validateRegisterRequest(RegisterRequest request) {
        // Email validation
        if (!EmailValidator.isValid(request.email())) {
            throw new ValidationException("Invalid email format", "email");
        }
        if (request.email().length() > 255) {
            throw new ValidationException("Email too long (max 255 chars)", "email");
        }
        
        // Password validation
        if (!PasswordValidator.isValid(request.password())) {
            throw new WeakPasswordException(
                "Password must contain at least 8 characters, including uppercase, lowercase, digit, and special character",
                "password"
            );
        }
        
        // Password confirmation
        if (!request.password().equals(request.confirmPassword())) {
            throw new PasswordMismatchException("Passwords do not match", "confirmPassword");
        }
        
        // Full name validation
        if (request.fullName().length() < 2 || request.fullName().length() > 100) {
            throw new ValidationException("Name must be 2-100 characters", "fullName");
        }
        if (!request.fullName().matches("^[\\p{L}\\s\\-]{2,100}$")) {
            throw new ValidationException("Name contains invalid characters", "fullName");
        }
    }
}
```

**Critical Notes:**
- ⚠️ **MUST** check email uniqueness BEFORE hashing password (performance)
- ⚠️ **MUST** force `role = STUDENT` (ignore request.role for security)
- ⚠️ **MUST** wrap in `@Transactional` (rollback on any exception)

---

### AuthService.login()

**Pseudo-code:**

```java
/**
 * Login with email + password
 * 
 * TRANSACTION: REQUIRED
 * AUDIT: USER_LOGIN (success), LOGIN_FAILED (failure)
 * RATE_LIMIT: 5 per 5 minutes per IP
 * SECURITY: Anti-enumeration (generic error messages)
 */
@Transactional
public AuthResponse login(LoginRequest request) {
    // Step 1: Find user by email
    User user = userRepository.findByEmail(request.email())
        .orElseThrow(() -> new InvalidCredentialsException("Invalid credentials"));  // Generic error
    
    // Step 2: Validate password FIRST (CRITICAL for anti-enumeration)
    boolean passwordValid = passwordEncoder.matches(request.password(), user.getPasswordHash());
    if (!passwordValid) {
        // Log failed attempt (async)
        auditService.logAsync(AuditLog.builder()
            .entityType("User")
            .entityId(user.getId())
            .action("LOGIN_FAILED")
            .outcome(Outcome.FAILURE)
            .metadata(Map.of("reason", "incorrect_password", "email", request.email()))
            .build());
        
        throw new InvalidCredentialsException("Invalid credentials");  // Generic error
    }
    
    // Step 3: Check account status AFTER password validation (CRITICAL)
    if (user.getStatus() == UserStatus.LOCKED) {
        auditService.logAsync(AuditLog.builder()
            .entityType("User")
            .entityId(user.getId())
            .action("LOGIN_FAILED")
            .outcome(Outcome.FAILURE)
            .metadata(Map.of("reason", "account_locked", "email", request.email()))
            .build());
        
        throw new AccountLockedException("Account is locked. Contact admin.");  // Specific error (user verified identity)
    }
    
    // Step 4: Generate access token (JWT, 15 min)
    String accessToken = jwtService.generateAccessToken(user);
    
    // Step 5: Generate refresh token (UUID, 7 days)
    RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);
    
    // Step 6: Log successful login (async)
    auditService.logAsync(AuditLog.builder()
        .entityType("User")
        .entityId(user.getId())
        .action("USER_LOGIN")
        .outcome(Outcome.SUCCESS)
        .metadata(Map.of("email", user.getEmail()))
        .build());
    
    // Step 7: Return response
    return AuthResponse.builder()
        .accessToken(accessToken)
        .refreshToken(refreshToken.getToken())
        .tokenType("Bearer")
        .expiresIn(900)
        .build();
}
```

**Critical Security Notes:**
- ⚠️ **MUST** validate password BEFORE checking status (anti-enumeration attack prevention)
- ⚠️ **MUST** use generic error "Invalid credentials" for email not found OR password incorrect
- ⚠️ **MUST** use BCrypt constant-time comparison (timing attack prevention)
- ⚠️ **MUST** return specific error "Account locked" ONLY if password is correct (user verified identity)

**Attack Scenario (WRONG Implementation):**
```java
// ❌ VULNERABLE TO ENUMERATION
if (user.getStatus() == LOCKED) {
    throw new AccountLockedException();  // Attacker knows account exists and is locked!
}
if (!passwordEncoder.matches(password, hash)) {
    throw new InvalidCredentialsException();  // Different error = account exists
}
```

**Correct Implementation:**
```java
// ✅ SECURE (Anti-Enumeration)
if (!passwordEncoder.matches(password, hash)) {
    throw new InvalidCredentialsException();  // Check password FIRST
}
if (user.getStatus() == LOCKED) {
    throw new AccountLockedException();  // Check status AFTER password verified
}
```

---

### RefreshTokenService.refreshToken()

**Pseudo-code:**

```java
/**
 * Refresh access token using refresh token
 * 
 * TRANSACTION: REQUIRED (ATOMIC: revoke old + create new)
 * AUDIT: TOKEN_REFRESHED
 * SECURITY: Token reuse detection, account status check
 */
@Transactional
public AuthResponse refreshToken(String refreshTokenString) {
    // Step 1: Find refresh token in database
    RefreshToken refreshToken = refreshTokenRepository.findByToken(refreshTokenString)
        .orElseThrow(() -> new TokenInvalidException("Token invalid"));  // Generic error
    
    // Step 2: Check if token is expired
    if (refreshToken.isExpired()) {
        throw new TokenExpiredException("Token expired");
    }
    
    // Step 3: Check if token is revoked (TOKEN REUSE DETECTION)
    if (refreshToken.isRevoked()) {
        // SECURITY: Token reuse detected! Revoke ALL tokens and force re-login
        User user = refreshToken.getUser();
        refreshTokenRepository.revokeAllByUser(user);  // Revoke all tokens
        
        auditService.logAsync(AuditLog.builder()
            .entityType("RefreshToken")
            .entityId(refreshToken.getId())
            .action("TOKEN_REUSE_DETECTED")
            .outcome(Outcome.FAILURE)
            .metadata(Map.of("user_id", user.getId(), "token_id", refreshToken.getId()))
            .build());
        
        throw new TokenInvalidException("Token invalid");  // Generic error (don't reveal "reused")
    }
    
    // Step 4: Get user from token
    User user = refreshToken.getUser();
    
    // Step 5: CRITICAL - Check account status BEFORE generating new tokens
    if (user.getStatus() == UserStatus.LOCKED) {
        // Revoke ALL tokens to prevent bypass
        refreshTokenRepository.revokeAllByUser(user);
        
        auditService.logAsync(AuditLog.builder()
            .entityType("RefreshToken")
            .entityId(refreshToken.getId())
            .action("TOKEN_REFRESH_DENIED")
            .outcome(Outcome.FAILURE)
            .metadata(Map.of("reason", "account_locked", "user_id", user.getId()))
            .build());
        
        throw new AccountLockedException("Account is locked. Contact admin.");
    }
    
    // Step 6: Revoke old token (MUST happen BEFORE creating new token)
    refreshToken.setRevoked(true);
    refreshTokenRepository.save(refreshToken);
    
    // Step 7: Generate new refresh token (new UUID, new expiration)
    RefreshToken newRefreshToken = RefreshToken.builder()
        .user(user)
        .token(UUID.randomUUID().toString())
        .expiresAt(LocalDateTime.now().plusDays(7))
        .revoked(false)
        .build();
    newRefreshToken = refreshTokenRepository.save(newRefreshToken);
    
    // Step 8: Generate new access token (new JWT, 15 min)
    String newAccessToken = jwtService.generateAccessToken(user);
    
    // Step 9: Log audit event (async)
    auditService.logAsync(AuditLog.builder()
        .entityType("RefreshToken")
        .entityId(newRefreshToken.getId())
        .action("TOKEN_REFRESHED")
        .outcome(Outcome.SUCCESS)
        .metadata(Map.of(
            "user_id", user.getId(),
            "old_token_id", refreshToken.getId(),
            "new_token_id", newRefreshToken.getId()
        ))
        .build());
    
    // Step 10: Return response
    return AuthResponse.builder()
        .accessToken(newAccessToken)
        .refreshToken(newRefreshToken.getToken())
        .tokenType("Bearer")
        .expiresIn(900)
        .build();
}
```

**Critical Security Notes:**
- ⚠️ **MUST** check `user.status == LOCKED` BEFORE generating new tokens (bypass prevention)
- ⚠️ **MUST** revoke ALL tokens if account is locked (force re-login)
- ⚠️ **MUST** detect token reuse (revoked token usage) → Revoke ALL tokens (security breach)
- ⚠️ **MUST** be atomic transaction (revoke old + create new in same transaction)
- ⚠️ **MUST** revoke old token BEFORE creating new token (avoid orphaned tokens)

**Why Account Status Check is Critical:**

**Attack Scenario (WITHOUT Status Check):**
1. User logs in → Gets refresh token (expires in 7 days)
2. Admin locks user account (status = LOCKED)
3. User calls `/api/auth/refresh` with old token → ✅ Gets new access token ❌
4. User bypasses account lockout for 7 days!

**With Status Check:**
1. User logs in → Gets refresh token (expires in 7 days)
2. Admin locks user account (status = LOCKED)
3. User calls `/api/auth/refresh` → ❌ 403 Account Locked
4. All tokens revoked → User must re-login

---

### AuthService.logout()

**Pseudo-code:**

```java
/**
 * Logout user (revoke refresh token)
 * 
 * TRANSACTION: REQUIRED
 * AUDIT: USER_LOGOUT
 * IDEMPOTENT: Yes (revoking non-existent token returns 204)
 */
@Transactional
public void logout(String refreshTokenString, User currentUser) {
    // Step 1: Find refresh token
    Optional<RefreshToken> tokenOpt = refreshTokenRepository.findByToken(refreshTokenString);
    
    // Step 2: If token not found or already revoked, return success (idempotent)
    if (tokenOpt.isEmpty()) {
        return;  // Silent success (don't reveal token doesn't exist)
    }
    
    RefreshToken refreshToken = tokenOpt.get();
    
    // Step 3: Verify token belongs to current user (security check)
    if (!refreshToken.getUser().getId().equals(currentUser.getId())) {
        throw new ForbiddenException("Cannot revoke token of another user");
    }
    
    // Step 4: If already revoked, return success (idempotent)
    if (refreshToken.isRevoked()) {
        return;  // Silent success
    }
    
    // Step 5: Revoke token
    refreshToken.setRevoked(true);
    refreshTokenRepository.save(refreshToken);
    
    // Step 6: Log audit event (async)
    auditService.logAsync(AuditLog.builder()
        .entityType("RefreshToken")
        .entityId(refreshToken.getId())
        .action("USER_LOGOUT")
        .outcome(Outcome.SUCCESS)
        .metadata(Map.of("user_id", currentUser.getId()))
        .build());
}
```

**Critical Notes:**
- ⚠️ **MUST** be idempotent (calling multiple times has same effect)
- ⚠️ **MUST** return 204 even if token not found (don't leak information)
- ⚠️ **MUST** verify token belongs to current user (prevent revoking others' tokens)

---

## Security Implementation

### Password Hashing

**Configuration:**

```java
@Configuration
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt with strength 10 (MUST)
        return new BCryptPasswordEncoder(10);
    }
}
```

**Usage:**

```java
// Hashing (registration)
String hash = passwordEncoder.encode(plainPassword);

// Verification (login) - CONSTANT TIME comparison
boolean valid = passwordEncoder.matches(plainPassword, hash);
```

**Critical Notes:**
- ⚠️ **MUST** use BCrypt strength 10 (balance security vs performance)
- ⚠️ **MUST** use `matches()` for verification (constant-time, timing attack prevention)
- ⚠️ **NEVER** compare hashes with `equals()` (timing attack vulnerability)

---

### JWT Token Generation

**Configuration:**

```java
@Component
public class JwtService {
    
    @Value("${jwt.secret}")
    private String secret;  // MUST: 256+ bit secret
    
    @Value("${jwt.access-token-ttl}")
    private int accessTokenTtl;  // MUST: 900 seconds (15 minutes)
    
    /**
     * Generate access token
     */
    public String generateAccessToken(User user) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + accessTokenTtl * 1000L);
        
        return Jwts.builder()
            .setSubject(user.getId().toString())  // User ID
            .claim("email", user.getEmail())
            .claim("roles", List.of(user.getRole().name()))  // NO ROLE_ prefix
            .claim("token_type", "ACCESS")
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(SignatureAlgorithm.HS256, secret)
            .compact();
    }
    
    /**
     * Validate and parse JWT token
     */
    public Claims validateToken(String token) {
        try {
            return Jwts.parser()
                .setSigningKey(secret)
                .parseClaimsJws(token)
                .getBody();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException("Token expired");
        } catch (JwtException e) {
            throw new TokenInvalidException("Token invalid");
        }
    }
}
```

**Critical Notes:**
- ⚠️ **MUST** use HS256 algorithm (HMAC-SHA256)
- ⚠️ **MUST** use secret key ≥ 256 bits (32+ characters)
- ⚠️ **MUST** include `roles` claim as array WITHOUT `ROLE_` prefix (e.g., `["STUDENT"]`, `["ADMIN"]`)
- ⚠️ **MUST** set `exp` claim (automatic expiration validation)
- 💡 **NOTE**: Spring Security adds `ROLE_` prefix internally when creating `GrantedAuthority`

---

### Refresh Token Generation

**Implementation:**

```java
@Service
public class RefreshTokenService {
    
    /**
     * Create new refresh token
     */
    @Transactional
    public RefreshToken createRefreshToken(User user) {
        RefreshToken token = RefreshToken.builder()
            .user(user)
            .token(UUID.randomUUID().toString())  // Random UUID
            .expiresAt(LocalDateTime.now().plusDays(7))  // 7 days TTL
            .revoked(false)
            .build();
        
        return refreshTokenRepository.save(token);
    }
    
    /**
     * Revoke all refresh tokens for user
     */
    @Transactional
    public void revokeAllByUser(User user) {
        List<RefreshToken> tokens = refreshTokenRepository.findAllByUserAndRevokedFalse(user);
        tokens.forEach(token -> token.setRevoked(true));
        refreshTokenRepository.saveAll(tokens);
    }
}
```

**Critical Notes:**
- ⚠️ **MUST** use UUID v4 (cryptographically random)
- ⚠️ **MUST** store in database (not self-contained like JWT)
- ⚠️ **MUST** have expiration (7 days standard)
- ⚠️ **MUST** support revocation (boolean flag)

---

### JWT Authentication Filter

**Implementation:**

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Autowired
    private JwtService jwtService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        
        // Step 1: Extract Authorization header
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // Step 2: Extract token
        String token = authHeader.substring(7);
        
        try {
            // Step 3: Validate token
            Claims claims = jwtService.validateToken(token);
            
            // Step 4: Extract user ID
            String userId = claims.getSubject();
            
            // Step 5: Load user from database
            User user = userRepository.findById(Long.parseLong(userId))
                .orElseThrow(() -> new TokenInvalidException("User not found"));
            
            // Step 6: Check account status (OPTIONAL - can skip if JWT is short-lived)
            if (user.getStatus() == UserStatus.LOCKED) {
                throw new AccountLockedException("Account is locked");
            }
            
            // Step 7: Extract roles
            List<String> roles = claims.get("roles", List.class);
            List<GrantedAuthority> authorities = roles.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
            
            // Step 8: Set authentication context
            UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
        } catch (TokenExpiredException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":{\"code\":\"TOKEN_EXPIRED\",\"message\":\"Token expired\"}}");
            return;
        } catch (TokenInvalidException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.getWriter().write("{\"error\":{\"code\":\"TOKEN_INVALID\",\"message\":\"Token invalid\"}}");
            return;
        } catch (AccountLockedException e) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.getWriter().write("{\"error\":{\"code\":\"ACCOUNT_LOCKED\",\"message\":\"Account is locked\"}}");
            return;
        }
        
        filterChain.doFilter(request, response);
    }
}
```

**Critical Notes:**
- ⚠️ **MUST** extend `OncePerRequestFilter` (execute once per request)
- ⚠️ **MUST** validate token signature and expiration
- ⚠️ **SHOULD** check user status (LOCKED) if token TTL > 5 minutes
- ⚠️ **MUST** set `SecurityContextHolder` with user + authorities

---

## Transaction Management

### Transaction Boundaries

**Rule:** Each API endpoint MUST have ONE transaction boundary at service layer.

**Example:**

```java
// Controller Layer (NO @Transactional)
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);  // Transaction starts here
        return ResponseEntity.status(201).body(response);
    }
}

// Service Layer (@Transactional REQUIRED)
@Service
@Transactional  // Transaction boundary
public class AuthService {
    
    public AuthResponse register(RegisterRequest request) {
        // All database operations in this method are in ONE transaction
        // Rollback on ANY exception
    }
}
```

### Transaction Propagation

**MUST use:**
- `@Transactional(propagation = Propagation.REQUIRED)` for main service methods
- `@Transactional(propagation = Propagation.REQUIRES_NEW)` for audit logging (OPTIONAL - if audit log failure should not rollback main transaction)

**Example:**

```java
@Service
public class AuditService {
    
    // Audit log in SEPARATE transaction (failure doesn't rollback main transaction)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Async  // Non-blocking
    public void logAsync(AuditLog log) {
        auditLogRepository.save(log);
    }
}
```

### Rollback Rules

**Default:** Rollback on ANY unchecked exception (RuntimeException)

**Custom rollback:**

```java
@Transactional(rollbackFor = {Exception.class})  // Rollback on checked exceptions too
public void someMethod() throws Exception {
    // ...
}
```

---

## Error Handling

### Global Exception Handler

**Implementation:**

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    /**
     * Validation errors (400 Bad Request)
     */
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(ValidationException e) {
        ErrorResponse error = ErrorResponse.builder()
            .error(ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message(e.getMessage())
                .field(e.getField())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(400).body(error);
    }
    
    /**
     * Email already exists (409 Conflict)
     */
    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEmailExists(EmailAlreadyExistsException e) {
        ErrorResponse error = ErrorResponse.builder()
            .error(ErrorDetail.builder()
                .code("EMAIL_ALREADY_EXISTS")
                .message(e.getMessage())
                .field("email")
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(409).body(error);
    }
    
    /**
     * Invalid credentials (401 Unauthorized)
     */
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException e) {
        ErrorResponse error = ErrorResponse.builder()
            .error(ErrorDetail.builder()
                .code("INVALID_CREDENTIALS")
                .message("Invalid credentials")  // MUST be generic
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(401).body(error);
    }
    
    /**
     * Account locked (403 Forbidden)
     */
    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(AccountLockedException e) {
        ErrorResponse error = ErrorResponse.builder()
            .error(ErrorDetail.builder()
                .code("ACCOUNT_LOCKED")
                .message(e.getMessage())
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(403).body(error);
    }
    
    /**
     * Token expired (401 Unauthorized)
     */
    @ExceptionHandler(TokenExpiredException.class)
    public ResponseEntity<ErrorResponse> handleTokenExpired(TokenExpiredException e) {
        ErrorResponse error = ErrorResponse.builder()
            .error(ErrorDetail.builder()
                .code("TOKEN_EXPIRED")
                .message("Token expired")
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(401).body(error);
    }
    
    /**
     * Generic server error (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception e) {
        // Log full stack trace (MUST)
        log.error("Unhandled exception", e);
        
        ErrorResponse error = ErrorResponse.builder()
            .error(ErrorDetail.builder()
                .code("INTERNAL_SERVER_ERROR")
                .message("An unexpected error occurred")  // Don't leak internal details
                .build())
            .timestamp(LocalDateTime.now())
            .build();
        return ResponseEntity.status(500).body(error);
    }
}
```

### Exception Hierarchy

**MUST define custom exceptions:**

```java
// Base exception
public class IdentityServiceException extends RuntimeException {
    public IdentityServiceException(String message) {
        super(message);
    }
}

// Validation errors (400)
public class ValidationException extends IdentityServiceException {
    private final String field;
    
    public ValidationException(String message, String field) {
        super(message);
        this.field = field;
    }
}

// Authentication errors (401)
public class InvalidCredentialsException extends IdentityServiceException {
    public InvalidCredentialsException(String message) {
        super(message);
    }
}

public class TokenExpiredException extends IdentityServiceException {
    public TokenExpiredException(String message) {
        super(message);
    }
}

public class TokenInvalidException extends IdentityServiceException {
    public TokenInvalidException(String message) {
        super(message);
    }
}

// Authorization errors (403)
public class AccountLockedException extends IdentityServiceException {
    public AccountLockedException(String message) {
        super(message);
    }
}

public class ForbiddenException extends IdentityServiceException {
    public ForbiddenException(String message) {
        super(message);
    }
}

// Conflict errors (409)
public class EmailAlreadyExistsException extends IdentityServiceException {
    public EmailAlreadyExistsException(String message) {
        super(message);
    }
}

public class ConflictException extends IdentityServiceException {
    public ConflictException(String message) {
        super(message);
    }
}

// Not found errors (404)
public class UserNotFoundException extends IdentityServiceException {
    public UserNotFoundException(String message) {
        super(message);
    }
}
```

---

## Testing Requirements

### Unit Tests (MUST)

**Test cases for AuthService.login():**

```java
@SpringBootTest
@Transactional
class AuthServiceTest {
    
    @Autowired
    private AuthService authService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;
    
    @Test
    void login_WithValidCredentials_ReturnsTokens() {
        // Given
        User user = createUser("test@example.com", "SecurePass@123", UserStatus.ACTIVE);
        LoginRequest request = new LoginRequest("test@example.com", "SecurePass@123");
        
        // When
        AuthResponse response = authService.login(request);
        
        // Then
        assertThat(response.getAccessToken()).isNotNull();
        assertThat(response.getRefreshToken()).isNotNull();
        assertThat(response.getTokenType()).isEqualTo("Bearer");
        assertThat(response.getExpiresIn()).isEqualTo(900);
    }
    
    @Test
    void login_WithWrongPassword_ThrowsInvalidCredentials() {
        // Given
        User user = createUser("test@example.com", "SecurePass@123", UserStatus.ACTIVE);
        LoginRequest request = new LoginRequest("test@example.com", "WrongPassword");
        
        // When & Then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)
            .hasMessage("Invalid credentials");
    }
    
    @Test
    void login_WithLockedAccount_ThrowsAccountLocked() {
        // Given
        User user = createUser("test@example.com", "SecurePass@123", UserStatus.LOCKED);
        LoginRequest request = new LoginRequest("test@example.com", "SecurePass@123");
        
        // When & Then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(AccountLockedException.class)
            .hasMessageContaining("locked");
    }
    
    @Test
    void login_WithWrongPasswordAndLockedAccount_ThrowsInvalidCredentials() {
        // CRITICAL TEST: Verify anti-enumeration (password checked FIRST)
        
        // Given
        User user = createUser("test@example.com", "SecurePass@123", UserStatus.LOCKED);
        LoginRequest request = new LoginRequest("test@example.com", "WrongPassword");
        
        // When & Then
        assertThatThrownBy(() -> authService.login(request))
            .isInstanceOf(InvalidCredentialsException.class)  // NOT AccountLockedException!
            .hasMessage("Invalid credentials");
    }
    
    private User createUser(String email, String password, UserStatus status) {
        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(password))
            .fullName("Test User")
            .role(Role.STUDENT)
            .status(status)
            .build();
        return userRepository.save(user);
    }
}
```

### Integration Tests (SHOULD)

**Test case for refresh token reuse detection:**

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Test
    void refreshToken_WhenReused_RevokesAllTokens() throws Exception {
        // Step 1: Login
        String loginJson = "{\"email\":\"test@example.com\",\"password\":\"SecurePass@123\"}";
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginJson))
            .andExpect(status().isOk())
            .andReturn();
        
        String refreshToken = extractRefreshToken(loginResult);
        
        // Step 2: Refresh token (FIRST TIME - should succeed)
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isOk());
        
        // Step 3: Reuse old token (SECOND TIME - should revoke all tokens)
        mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refreshToken + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error.code").value("TOKEN_INVALID"));
        
        // Step 4: Verify all tokens revoked (any refresh should fail)
        // This proves that token reuse detection works
    }
}
```

### Security Tests (MUST)

**Test anti-enumeration:**

```java
@Test
void login_WithNonExistentEmail_ReturnsGenericError() {
    // Given
    LoginRequest request = new LoginRequest("nonexistent@example.com", "password");
    
    // When
    Exception exception = assertThrows(InvalidCredentialsException.class, 
        () -> authService.login(request));
    
    // Then
    assertThat(exception.getMessage()).isEqualTo("Invalid credentials");  // MUST be generic
    assertThat(exception.getMessage()).doesNotContain("email");  // MUST NOT reveal "email not found"
}
```

---

## Status

✅ **COMPLETE** - Ready for implementation

**Next Steps:**
1. Implement services following pseudo-code
2. Implement global exception handler
3. Implement JWT filter
4. Write unit tests (100% coverage for critical security logic)
5. Write integration tests
6. Test anti-enumeration manually (Postman/curl)
