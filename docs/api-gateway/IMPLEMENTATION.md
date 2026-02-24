# Implementation Guide – API Gateway

**Framework:** Spring Boot 3.2+  
**Java Version:** 17+  
**Technology:** Spring Cloud Gateway (Reactive) + Resilience4j  
**Date:** February 23, 2026

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Configuration](#configuration)
3. [Module Structure](#module-structure)
4. [Routing Configuration](#routing-configuration)
5. [Authentication & Authorization Flow](#authentication--authorization-flow)
6. [JWT Validation Strategy](#jwt-validation-strategy)
7. [Request Filtering Model](#request-filtering-model)
8. [Resilience Strategy](#resilience-strategy)
9. [Observability & Logging](#observability--logging)
10. [Deployment Considerations](#deployment-considerations)
11. [Known Limitations](#known-limitations)

---

## Quick Start

### Prerequisites

- Java 17+
- Maven 3.8+
- Redis (optional, for rate limiting)
- Docker (optional)

### Build & Run

```bash
# Build
mvn clean install

# Run locally
mvn spring-boot:run

# Run with Docker
docker build -t api-gateway:1.0 .
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  api-gateway:1.0
```

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

---

## Configuration

### Environment Variables

**Required:**

| Variable | Description | Default | Example |
|----------|-------------|---------|---------|
| `JWT_SECRET` | JWT signing secret (MUST match Identity Service) | - | `your-256-bit-secret-key-here` |
| `GATEWAY_INTERNAL_SECRET` | HMAC secret for signed headers | - | `gateway-service-hmac-secret-256-bit` |

**Optional:**

| Variable | Description | Default |
|----------|-------------|---------|
| `SERVER_PORT` | HTTP server port | `8080` |
| `SPRING_DATA_REDIS_HOST` | Redis host (required for rate limiting) | `redis` |
| `SPRING_DATA_REDIS_PORT` | Redis port | `6379` |
| `LOG_LEVEL` | Root log level | `INFO` |
| `GATEWAY_LOG_LEVEL` | Gateway-specific log level | `DEBUG` |
| `RATE_LIMIT_GLOBAL_RPS` | Global requests per second | `100` |
| `RATE_LIMIT_LOGIN_RPM` | Login requests per minute | `5` |
| `CIRCUIT_BREAKER_FAILURE_THRESHOLD` | Failure rate to open circuit (%) | `50` |
| `REQUEST_TIMEOUT_SECONDS` | Max request timeout | `30` |

### application.yml

```yaml
server:
  port: ${SERVER_PORT:8080}
  shutdown: graceful

spring:
  application:
    name: api-gateway

  lifecycle:
    timeout-per-shutdown-phase: 30s

  data:
    redis:
      host: ${SPRING_DATA_REDIS_HOST:redis}
      port: ${SPRING_DATA_REDIS_PORT:6379}
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 2

  cloud:
    gateway:
      default-filters:
        - DedupeResponseHeader=Access-Control-Allow-Credentials Access-Control-Allow-Origin
      httpclient:
        connect-timeout: 3000       # 3 seconds
        response-timeout: 30s       # 30 seconds max
        pool:
          max-connections: 500
          max-idle-time: 20s
          acquire-timeout: 45s
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "https://samt.example.com"
              - "https://admin.samt.example.com"
            allowedMethods:
              - GET
              - POST
              - PUT
              - PATCH
              - DELETE
              - OPTIONS
            allowedHeaders:
              - Authorization
              - Content-Type
            exposedHeaders:
              - Authorization
              - X-User-Id
              - X-User-Role
            allowCredentials: false
            maxAge: 3600

  codec:
    max-in-memory-size: 10MB  # Request body size limit

# JWT Configuration
jwt:
  secret: ${JWT_SECRET:your-256-bit-secret-key-change-this-in-production-environment-please}

# Internal Gateway Secret
gateway:
  internal:
    secret: ${GATEWAY_INTERNAL_SECRET:gateway-service-hmac-secret-change-in-production}

# Rate Limiting
rate:
  limit:
    global:
      requests-per-second: ${RATE_LIMIT_GLOBAL_RPS:100}
      burst-capacity: 200
    login:
      requests-per-minute: ${RATE_LIMIT_LOGIN_RPM:5}
      burst-capacity: 10

# Circuit Breaker
resilience4j:
  circuitbreaker:
    instances:
      defaultCircuitBreaker:
        failure-rate-threshold: ${CIRCUIT_BREAKER_FAILURE_THRESHOLD:50}  # 50%
        slow-call-rate-threshold: 50
        slow-call-duration-threshold: 10s
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10
        minimum-number-of-calls: 5
        automatic-transition-from-open-to-half-open-enabled: true
  timelimiter:
    instances:
      defaultTimeLimiter:
        timeout-duration: ${REQUEST_TIMEOUT_SECONDS:30}s

# Swagger Configuration
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

# Management & Monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,gateway
  endpoint:
    health:
      show-details: when-authorized

logging:
  level:
    root: ${LOG_LEVEL:INFO}
    com.example.gateway: ${LOG_LEVEL:DEBUG}
    org.springframework.cloud.gateway: ${GATEWAY_LOG_LEVEL:INFO}
```

---

## Module Structure

### Package Organization

```
api-gateway/
├── src/main/java/com/example/gateway/
│   ├── ApiGatewayApplication.java      # Main entry point
│   │
│   ├── config/
│   │   ├── SecurityConfig.java         # Spring Security configuration
│   │   ├── GatewayRoutesConfig.java    # Route definitions with circuit breaker
│   │   ├── RateLimitConfig.java        # Rate limiting configuration
│   │   └── Resilience4jConfig.java     # Circuit breaker, retry, timeout
│   │
│   ├── filter/
│   │   ├── JwtAuthenticationFilter.java # JWT validation filter
│   │   └── SignedHeaderFilter.java      # HMAC signature injection
│   │
│   ├── util/
│   │   ├── JwtUtil.java                 # JWT parsing and validation
│   │   └── SignatureUtil.java           # HMAC signature generation
│   │
│   ├── resolver/
│   │   └── IpKeyResolver.java           # Rate limiting key resolver
│   │
│   └── validation/
│       ├── JwtSecretValidator.java      # JWT secret validation on startup
│       └── GatewaySecretValidator.java  # Gateway internal secret validation
│
└── src/main/resources/
    └── application.yml                   # Configuration
```

### Dependency Management

**Key Dependencies:**

| Dependency | Purpose | Version |
|------------|---------|---------|
| `spring-cloud-starter-gateway` | Reactive gateway framework | 2023.0.0 |
| `spring-boot-starter-security` | Security & authentication | 3.2+ |
| `jjwt-api` | JWT parsing and validation | 0.12.3 |
| `spring-boot-starter-data-redis-reactive` | Redis integration (rate limiting) | 3.2+ |
| `resilience4j-spring-boot3` | Circuit breaker, retry, rate limiter | 2.1.0 |
| `resilience4j-reactor` | Reactive resilience patterns | 2.1.0 |
| `commons-codec` | HMAC signature generation | 1.16.0 |
| `springdoc-openapi-starter-webflux-ui` | Swagger UI | 2.3.0 |
| `spring-boot-starter-actuator` | Health checks & metrics | 3.2+ |

---

## Routing Configuration

### Static Route Definition

**File:** `GatewayRoutesConfig.java`

**Strategy:** Direct URL-based routing (no service discovery)

**Example:**

```java
@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
        return builder.routes()
                
                // Identity Service with circuit breaker
                .route("identity-service", r -> r
                        .path("/api/identity/**")
                        .filters(f -> f
                                .stripPrefix(2)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .circuitBreaker(config -> config
                                        .setName("identityServiceCB")
                                        .setFallbackUri("forward:/fallback/identity")
                                )
                                .retry(retryConfig -> retryConfig
                                        .setRetries(3)
                                        .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)
                                        .setMethods(HttpMethod.GET)
                                        .setBackoff(Duration.ofMillis(100), Duration.ofMillis(1000), 2, true)
                                )
                        )
                        .uri("http://identity-service:8081")
                )

                // User-Group Service with rate limiting
                .route("user-group-service", r -> r
                        .path("/api/groups/**", "/api/users/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .addRequestHeader("X-Forwarded-Host", "gateway")
                                .requestRateLimiter(config -> config
                                        .setRateLimiter(redisRateLimiter())
                                        .setKeyResolver(ipKeyResolver())
                                )
                                .circuitBreaker(config -> config
                                        .setName("userGroupServiceCB")
                                )
                        )
                        .uri("http://user-group-service:8082")
                )

                .build();
    }

    @Bean
    public RedisRateLimiter redisRateLimiter() {
        return new RedisRateLimiter(
            100,  // replenishRate (requests per second)
            200,  // burstCapacity
            1     // requestedTokens
        );
    }

    @Bean
    KeyResolver ipKeyResolver() {
        return exchange -> Mono.just(
            exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
        );
    }
}
```

### Route Matching Priority

Routes are evaluated in **order of definition**. First matching route wins.

**Example:**
1. `/api/identity/login` matches `identity-service` route (most specific)
2. `/api/groups/1` matches `user-group-service` route
3. `/api/unknown` → No match → 404 Not Found

### Service Discovery

**Current Implementation:** Static URLs

**Future Enhancement:** Dynamic service discovery with Eureka/Consul

**Docker Compose:** Uses service names as DNS (e.g., `http://identity-service:8081`)

---

## Authentication & Authorization Flow

> ⚠️ IMPORTANT – CONCEPTUAL VIEW ONLY
>
> This section describes a simplified authentication-focused flow for conceptual understanding.
>
> It DOES NOT represent the complete filter execution chain inside Spring Cloud Gateway.
>
> It MUST NOT be used as a reference for implementing filter execution order.
>
> The authoritative and complete execution sequence is defined in the
> "Definitive Filter Execution Order" section.
>
> Any implementation of filters, WebFilters, or GatewayFilters MUST follow
> the 11-step execution order defined in that section.

### 1. Request Arrival

Client sends request with JWT token:
```http
GET /api/groups/1 HTTP/1.1
Host: gateway:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 2. Public Endpoint Check

**File:** `JwtAuthenticationFilter.java`

**Logic:**
```java
private boolean isPublicEndpoint(String path) {
    return path.startsWith("/api/identity/register") ||
           path.startsWith("/api/identity/login") ||
           path.startsWith("/api/identity/refresh-token") ||
           path.startsWith("/actuator") ||
           path.startsWith("/swagger-ui") ||
           path.startsWith("/v3/api-docs");
}
```

**If public:** Skip JWT validation, forward immediately

**If protected:** Proceed to JWT validation

### 3. JWT Validation

**File:** `JwtUtil.java`

**Steps:**
1. Extract token from `Authorization: Bearer <token>` header
2. Verify signature using shared JWT secret
3. Check expiration time (`exp` claim)
4. Extract claims: `userId`, `email`, `role`

**Validation Logic:**
```java
public Claims validateAndParseClaims(String token) {
    try {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    } catch (Exception e) {
        throw new IllegalArgumentException("Invalid or expired JWT token", e);
    }
}
```

**Failure:** Return `401 Unauthorized`

### 4. Header Injection (Signed)

**File:** `SignedHeaderFilter.java`

**Process:**
```java
// Generate signed headers
Long userId = claims.get("userId", Long.class);
String email = claims.getSubject();
String role = claims.get("role", String.class);
long timestamp = System.currentTimeMillis();

// Generate HMAC-SHA256 signature
String payload = userId + "|" + email + "|" + role + "|" + timestamp;
String signature = HmacUtils.hmacSha256Hex(gatewaySecret, payload);

// Inject headers
ServerWebExchange mutatedExchange = exchange.mutate()
        .request(builder -> builder
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Email", email)
                .header("X-User-Role", role)
                .header("X-Timestamp", String.valueOf(timestamp))
                .header("X-Internal-Signature", signature)
        )
        .build();
```

### 5. Spring Security Context

**Purpose:** Enable Spring Security annotations in Gateway (if needed)

**Code:**
```java
UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
        email,
        null,
        List.of(new SimpleGrantedAuthority("ROLE_" + role))
);

return chain.filter(mutatedExchange)
        .contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication));
```

**Note:** Spring Security adds `ROLE_` prefix automatically. JWT should NOT include `ROLE_` prefix.

### 6. Request Forwarding with Resilience

Gateway forwards request to downstream service with:
- Signed headers
- Circuit breaker protection
- Timeout enforcement (30 seconds)
- Retry logic (transient failures only)

**Example:**
```http
GET /groups/1 HTTP/1.1
Host: user-group-service:8082
X-User-Id: 123
X-User-Email: admin@example.com
X-User-Role: ADMIN
X-Timestamp: 1708704600000
X-Internal-Signature: a7f3b8c2d9e1f4a3b8c2d9e1f4a3b8c2
X-Forwarded-Host: gateway
```

---

## JWT Validation Strategy

### Shared Secret Model

**Security Model:** Symmetric key (HS256)

**Key Requirement:** JWT secret MUST be identical across all services

**Configuration:**
- Identity Service: `jwt.secret` environment variable
- API Gateway: `jwt.secret` environment variable (MUST match)

**Secret Validation on Startup:**

**File:** `JwtSecretValidator.java`

```java
@Component
public class JwtSecretValidator implements InitializingBean {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Override
    public void afterPropertiesSet() throws Exception {
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            throw new IllegalStateException("JWT secret is not configured");
        }
        if (jwtSecret.length() < 32) {
            throw new IllegalStateException("JWT secret must be at least 256 bits (32 characters)");
        }
        log.info("JWT secret validated successfully (length: {} bytes)", jwtSecret.getBytes().length);
    }
}
```

### Token Expiration Handling

**Access Token TTL:** 15 minutes (configured in Identity Service)

**Refresh Token TTL:** 7 days (configured in Identity Service)

**Gateway Responsibility:** Reject expired access tokens with `401 Unauthorized`

**Client Responsibility:** Refresh access token using `/api/identity/refresh-token` endpoint

---

## Request Filtering Model

### Definitive Filter Execution Order

> ✅ AUTHORITATIVE EXECUTION MODEL
>
> This 11-step sequence is the official and complete filter execution model of the API Gateway.
>
> ALL implementations MUST strictly follow this order.
>
> The order is security-critical and MUST NOT be altered.
>
> Non-negotiable guarantees:
>
> - CORS executes FIRST (before authentication).
> - JWT Authentication executes at @Order(-100).
> - Signed Header Injection executes at @Order(-50).
> - Public endpoints bypass JWT validation and signed header injection,
>   but STILL pass through CORS.
> - Rate Limiting executes at GatewayFilter stage AFTER routing decision.
> - Circuit Breaker wraps the entire downstream call.
> - Retry executes INSIDE Circuit Breaker.
> - Retry MUST NOT execute when circuit state is OPEN.
> - HTTP client timeout applies across all retry attempts.
>
> No alternative execution interpretation is allowed.

**Execution Flow (in strict order):**

```
1. CORS Handling (WebFilter, implicit order)
   ↓
2. JWT Authentication (WebFilter, @Order(-100))
   ↓
3. Signed Header Injection (WebFilter, @Order(-50))
   ↓
4. Gateway Routing Decision
   ↓
5. Rate Limiting (GatewayFilter, per-route if configured)
   ↓
6. StripPrefix (GatewayFilter, per-route)
   ↓
7. AddRequestHeader (GatewayFilter, per-route)
   ↓
8. Circuit Breaker (GatewayFilter, per-route if configured)
   ↓
9. Retry Logic (GatewayFilter, per-route if configured)
   ↓
10. HTTP Client Execution (connect-timeout: 3s, response-timeout: 30s)
    ↓
11. Forward to Downstream Service
```

**Filter Classification:**

| Filter | Type | Scope | Order | Implementation |
|--------|------|-------|-------|----------------|
| CORS | WebFilter | Global | Implicit (first) | Spring Cloud Gateway built-in |
| JWT Authentication | WebFilter | Global | @Order(-100) | `JwtAuthenticationFilter.java` |
| Signed Header Injection | WebFilter | Global | @Order(-50) | `SignedHeaderFilter.java` |
| Rate Limiting | GatewayFilter | Per-route | Route definition order | `RequestRateLimiter` (conditionally applied) |
| StripPrefix | GatewayFilter | Per-route | Route definition order | Built-in |
| AddRequestHeader | GatewayFilter | Per-route | Route definition order | Built-in |
| Circuit Breaker | GatewayFilter | Per-route | Route definition order | Resilience4j |
| Retry | GatewayFilter | Per-route | Route definition order | Built-in |

**Critical Clarifications:**

1. **WebFilter vs GatewayFilter:**
   - WebFilters execute BEFORE gateway routing for all requests
   - GatewayFilters execute AFTER routing for matched routes only

2. **@Order Values:**
   - JWT Authentication: @Order(-100) to execute before signed header injection
   - Signed Header Injection: @Order(-50) to execute after JWT validation
   - CORS: No explicit @Order, handled by Spring Cloud Gateway framework first

3. **Rate Limiting Scope:**
   - Applied at GatewayFilter level per route configuration
   - NOT applied globally to all routes by default
   - See "Rate Limiting Configuration" section for route-specific application

4. **Circuit Breaker vs Retry:**
   - Circuit Breaker wraps the entire downstream call
   - Retry logic executes INSIDE the circuit breaker
   - If circuit is OPEN, retry does NOT execute (fail fast)

5. **Timeout Hierarchy:**
   - **Connect timeout (3s):** TCP connection establishment
   - **Response timeout (30s):** Total request-response duration
   - **Circuit breaker slow call (10s):** Triggers slow call threshold
   - Response timeout is enforced at HTTP client level, applies to all retries combined

6. **Public Endpoint Bypass:**
   - JWT Authentication filter checks path and skips validation for public endpoints
   - Signed Header Injection does NOT execute for public endpoints
   - All other filters execute normally

---

## Signature Generation Implementation

### SignatureUtil.java

```java
package com.example.gateway.util;

import org.apache.commons.codec.digest.HmacUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SignatureUtil {

    private final String gatewaySecret;

    public SignatureUtil(@Value("${gateway.internal.secret}") String gatewaySecret) {
        this.gatewaySecret = gatewaySecret;
    }

    public String generateSignature(Long userId, String email, String role, long timestamp) {
        String payload = userId + "|" + email + "|" + role + "|" + timestamp;
        return HmacUtils.hmacSha256Hex(gatewaySecret, payload);
    }

    public boolean verifySignature(
            Long userId,
            String email,
            String role,
            long timestamp,
            String receivedSignature
    ) {
        String expectedSignature = generateSignature(userId, email, role, timestamp);
        return expectedSignature.equals(receivedSignature);
    }
}
```

### Downstream Service Verification

```java
@Component
public class SignatureVerificationFilter implements Filter {

    @Value("${gateway.internal.secret}")
    private String gatewaySecret;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        
        // Read headers
        String userId = httpRequest.getHeader("X-User-Id");
        String email = httpRequest.getHeader("X-User-Email");
        String role = httpRequest.getHeader("X-User-Role");
        String timestamp = httpRequest.getHeader("X-Timestamp");
        String signature = httpRequest.getHeader("X-Internal-Signature");
        
        // Validate headers exist
        if (userId == null || signature == null) {
            sendError(response, 401, "Missing internal headers");
            return;
        }
        
        // Validate timestamp (60 second window)
        long requestTime = Long.parseLong(timestamp);
        long currentTime = System.currentTimeMillis();
        if (Math.abs(currentTime - requestTime) > 60000) {
            sendError(response, 401, "Request timestamp expired");
            return;
        }
        
        // Verify signature
        String payload = userId + "|" + email + "|" + role + "|" + timestamp;
        String expectedSignature = HmacUtils.hmacSha256Hex(gatewaySecret, payload);
        if (!expectedSignature.equals(signature)) {
            sendError(response, 401, "Invalid internal signature");
            return;
        }
        
        // Signature valid, proceed
        chain.doFilter(request, response);
    }
    
    private void sendError(ServletResponse response, int status, String message) throws IOException {
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        httpResponse.setStatus(status);
        httpResponse.setContentType("application/json");
        httpResponse.getWriter().write(
            "{\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}}"
        );
    }
}
```

---

## Resilience Strategy

### Implementation Overview

**Components:**
1. Circuit Breaker (Resilience4j)
2. Request Timeout (Spring Cloud Gateway)
3. Retry Logic (Exponential Backoff)
4. Connection Pooling
5. Rate Limiting (Redis)

### 1. Circuit Breaker

**Purpose:** Prevent cascading failures when downstream service fails

**Scope:** Per-downstream-service (each service has its own independent circuit breaker)

**Configuration:**
```yaml
resilience4j:
  circuitbreaker:
    instances:
      defaultCircuitBreaker:
        failure-rate-threshold: 50          # Open circuit at 50% failure rate
        slow-call-rate-threshold: 50        # Open circuit at 50% slow calls
        slow-call-duration-threshold: 10s   # Slow call = >10 seconds
        wait-duration-in-open-state: 30s    # Wait 30s before half-open
        permitted-number-of-calls-in-half-open-state: 5
        sliding-window-type: COUNT_BASED
        sliding-window-size: 10             # Last 10 calls
        minimum-number-of-calls: 5          # Need 5 calls before calculating
```

**States:**
- **CLOSED:** Normal operation, requests forwarded
- **OPEN:** Circuit open, requests fail fast with 503
- **HALF_OPEN:** Testing if service recovered (5 test calls)

**Per-Service Circuit Breaker Configuration:**

| Route | Circuit Breaker Name | Configuration Source | Failure Calculation Scope |
|-------|---------------------|---------------------|---------------------------|
| `/api/identity/**` | `identityServiceCB` | Inherits from `defaultCircuitBreaker` | Per identity-service route only |
| `/api/groups/**`, `/api/users/**` | `userGroupServiceCB` | Inherits from `defaultCircuitBreaker` | Per user-group-service route only |
| `/api/project-configs/**` | `projectConfigServiceCB` | Inherits from `defaultCircuitBreaker` | Per project-config-service route only |
| `/api/sync/**` | `syncServiceCB` | Inherits from `defaultCircuitBreaker` | Per sync-service route only |

**Route Configuration Example:**
```java
.circuitBreaker(config -> config
    .setName("identityServiceCB")
    .setFallbackUri("forward:/fallback/identity")  // Optional fallback
)
```

**Critical Clarifications:**

1. **Circuit Breaker Isolation:**
   - Each downstream service has its own circuit breaker instance
   - Failure in Identity Service does NOT affect User-Group Service circuit breaker
   - Failure rates are calculated independently per service

2. **Circuit Breaker vs Retry Relationship:**
   - Circuit breaker executes FIRST (wraps the entire call)
   - If circuit is CLOSED: Retry logic executes inside circuit breaker
   - If circuit is OPEN: Request fails immediately with 503, retry does NOT execute
   - Retry attempts count toward circuit breaker failure statistics

3. **Timeout Hierarchy (execution order):**
   ```
   Circuit Breaker (wraps everything)
   └─> Retry Logic (if circuit is closed)
       └─> HTTP Client Call
           ├─> Connect Timeout (3s) - TCP connection establishment
           └─> Response Timeout (30s) - Total request duration across ALL retry attempts
   ```
   - **Connect timeout (3s):** Per connection attempt
   - **Response timeout (30s):** Total duration including all retries
   - **Slow call threshold (10s):** Single call duration triggers slow call statistics

4. **Failure Rate Calculation:**
   - Failure rate is per circuit breaker (per downstream service)
   - Calculated over sliding window of last 10 calls
   - Requires minimum 5 calls before opening circuit
   - Failures include: HTTP 5xx, timeouts, connection errors, exceptions

5. **Fallback Behavior:**
   - If `fallbackUri` is specified: Routes to fallback endpoint (e.g., `/fallback/identity`)
   - If `fallbackUri` is NOT specified: Returns 503 Service Unavailable
   - Fallback is NOT implemented by default (returns generic error)

**Fallback Response (503 Service Unavailable):**
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "Identity Service is temporarily unavailable. Please try again later."
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

### 2. Request Timeout

**Purpose:** Prevent slow requests from exhausting Gateway resources

**Configuration:**
```yaml
spring:
  cloud:
    gateway:
      httpclient:
        connect-timeout: 3000       # 3 seconds to establish connection
        response-timeout: 30s       # 30 seconds max for response
```

**Behavior:**
- Connect timeout: 3 seconds (network connection)
- Response timeout: 30 seconds (total request duration)
- Timeout triggers circuit breaker failure count

**Timeout Response (504 Gateway Timeout):**
```json
{
  "error": {
    "code": "GATEWAY_TIMEOUT",
    "message": "Downstream service did not respond within 30 seconds"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

### 3. Retry Logic

**Purpose:** Recover from transient failures

**Configuration:**
```java
.retry(retryConfig -> retryConfig
    .setRetries(3)  // Max 3 retries
    .setStatuses(HttpStatus.BAD_GATEWAY, HttpStatus.SERVICE_UNAVAILABLE)  // Only these statuses
    .setMethods(HttpMethod.GET)  // Only idempotent methods
    .setBackoff(
        Duration.ofMillis(100),  // Initial backoff
        Duration.ofMillis(1000), // Max backoff
        2,                       // Multiplier
        true                     // Jitter enabled
    )
)
```

**Backoff Strategy:**
- Attempt 1: Immediate
- Attempt 2: 100ms + jitter
- Attempt 3: 200ms + jitter
- Attempt 4: 400ms + jitter

**Safety:**
- **Only retry idempotent operations** (GET, DELETE)
- **Never retry POST/PUT** without idempotency keys
- **Retry only transient errors** (502, 503)

### 4. Connection Pooling

**Purpose:** Reuse HTTP connections for performance

**Configuration:**
```yaml
spring:
  cloud:
    gateway:
      httpclient:
        pool:
          max-connections: 500      # Max total connections
          max-idle-time: 20s        # Idle connection TTL
          acquire-timeout: 45s      # Wait time for connection from pool
```

**Tuning:**
- `max-connections`: `(downstream services) * (expected RPS) / (avg response time)`
- Example: 6 services * 100 RPS / 0.05s = 12,000 / 0.05 = ~240 connections
- Set to 500 for headroom

### 5. Rate Limiting

**Purpose:** Protect Gateway and downstream services from overload

**Scope:** Per-route configuration (NOT globally applied)

**Redis Dependency:** Required for rate limiting. If Redis is unavailable, requests proceed without rate limiting (fail-open strategy).

#### Rate Limiting Configuration Matrix

| Route Pattern | Rate Limiter Bean | Replenish Rate | Burst Capacity | Key Resolver | Window |
|---------------|-------------------|----------------|----------------|--------------|--------|
| `/api/identity/**` | None | N/A | N/A | N/A | No rate limiting |
| `/api/groups/**` | `redisRateLimiter()` | 100 req/s | 200 | IP-based | 1 second |
| `/api/users/**` | `redisRateLimiter()` | 100 req/s | 200 | IP-based | 1 second |
| `/api/project-configs/**` | `redisRateLimiter()` | 100 req/s | 200 | IP-based | 1 second |
| `/api/sync/**` | `redisRateLimiter()` | 100 req/s | 200 | IP-based | 1 second |

**Configuration Details:**

1. **Identity Service Route:**
   - Rate limiting is NOT applied
   - Login endpoint `/api/identity/login` has NO per-route rate limit
   - Public endpoints (login, register) depend on infrastructure-level protection

2. **Other Service Routes:**
   - All apply the same `redisRateLimiter()` bean
   - 100 requests per second per IP address
   - Burst capacity: 200 requests (allows temporary spikes)

3. **Key Resolver:**
   ```java
   @Bean
   KeyResolver ipKeyResolver() {
       return exchange -> Mono.just(
           exchange.getRequest()
               .getRemoteAddress()
               .getAddress()
               .getHostAddress()
       );
   }
   ```
   - Rate limiting is per-IP address
   - NOT per-user (userId is not extracted at rate limiting stage)
   - Client IP extracted from `ServerHttpRequest.getRemoteAddress()`

4. **Redis Fail-Open Behavior:**
   - If Redis is unavailable: Rate limiting is bypassed, requests proceed normally
   - No fallback to in-memory rate limiting
   - Monitoring should alert on Redis unavailability

**Rate Limiter Bean Definitions:**

```java
@Bean
public RedisRateLimiter redisRateLimiter() {
    return new RedisRateLimiter(
        100,  // replenishRate: 100 tokens per second
        200,  // burstCapacity: Allow burst up to 200 requests
        1     // requestedTokens: 1 token per request
    );
}

// Alternative bean for login-specific rate limiting (NOT CURRENTLY APPLIED)
@Bean
public RedisRateLimiter loginRateLimiter() {
    return new RedisRateLimiter(
        5,    // replenishRate: 5 per minute = 5/60 per second
        10,   // burstCapacity: Allow burst up to 10
        1
    );
}
```

**Note:** The `loginRateLimiter()` bean is defined but NOT currently applied to any route. To apply stricter rate limiting to login endpoints, the identity-service route configuration must be updated.

**Rate Limit Response (429 Too Many Requests):**
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again later."
  },
  "timestamp": "2026-02-23T10:30:00Z",
  "retryAfter": 60
}
```

**Response Headers:**
- `X-RateLimit-Remaining`: Tokens remaining for this IP
- `X-RateLimit-Burst-Capacity`: Max burst capacity
- `X-RateLimit-Replenish-Rate`: Tokens per second

---

## Global Error Handling Strategy

### Error Response Standardization

**Policy:** ALL errors (4xx, 5xx) returned by API Gateway use a standardized JSON format. No default Spring Boot error responses are exposed to clients.

**Standard Error Format:**

```json
{
  "error": {
    "code": "ERROR_CODE",
    "message": "Human-readable error description"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**Implementation:** `GlobalErrorWebExceptionHandler` or custom `WebExceptionHandler`

### Error Response Catalog

**Comprehensive error coverage (all HTTP error codes use standard format):**

| HTTP Status | Error Code | Trigger Condition | Source |
|-------------|-----------|-------------------|--------|
| 400 Bad Request | `BAD_REQUEST` | Malformed request, validation failure | Gateway or downstream |
| 401 Unauthorized | `UNAUTHORIZED` | Missing, invalid, or expired JWT | JWT Authentication Filter |
| 403 Forbidden | `FORBIDDEN` | Valid JWT but insufficient permissions | Downstream service |
| 404 Not Found | `NOT_FOUND` | No route matches request path | Gateway routing |
| 413 Payload Too Large | `PAYLOAD_TOO_LARGE` | Request body exceeds 10MB limit | Spring WebFlux codec |
| 429 Too Many Requests | `RATE_LIMIT_EXCEEDED` | Rate limit threshold exceeded | RequestRateLimiter filter |
| 500 Internal Server Error | `INTERNAL_SERVER_ERROR` | Unexpected error in Gateway | Gateway exception handler |
| 502 Bad Gateway | `BAD_GATEWAY` | Invalid response from downstream | HTTP client |
| 503 Service Unavailable | `SERVICE_UNAVAILABLE` | Circuit breaker open | Circuit breaker filter |
| 504 Gateway Timeout | `GATEWAY_TIMEOUT` | Response timeout (30s) exceeded | HTTP client timeout |

### Error Response Examples

#### 400 Bad Request
```json
{
  "error": {
    "code": "BAD_REQUEST",
    "message": "Request validation failed"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 401 Unauthorized
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired JWT token"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 403 Forbidden
```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "Insufficient permissions to access this resource"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 404 Not Found
```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "No route found for request path"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 413 Payload Too Large
```json
{
  "error": {
    "code": "PAYLOAD_TOO_LARGE",
    "message": "Request body exceeds 10MB limit"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 429 Too Many Requests
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again later."
  },
  "timestamp": "2026-02-23T10:30:00Z",
  "retryAfter": 60
}
```

#### 500 Internal Server Error
```json
{
  "error": {
    "code": "INTERNAL_SERVER_ERROR",
    "message": "An unexpected error occurred"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 502 Bad Gateway
```json
{
  "error": {
    "code": "BAD_GATEWAY",
    "message": "Invalid response from downstream service"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 503 Service Unavailable
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "Identity Service is temporarily unavailable. Please try again later."
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

#### 504 Gateway Timeout
```json
{
  "error": {
    "code": "GATEWAY_TIMEOUT",
    "message": "Downstream service did not respond within 30 seconds"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

### Error Handling Implementation

**File:** `GlobalErrorWebExceptionHandler.java`

**Type:** Implements `ErrorWebExceptionHandler` (Spring WebFlux)

**Responsibilities:**

1. Catch all exceptions thrown by WebFilters and GatewayFilters
2. Map exceptions to standardized error responses
3. Set appropriate HTTP status codes
4. Add `timestamp` field with ISO-8601 format
5. Log errors with correlation IDs for tracing

**Exception Mapping Strategy:**

```java
@Component
@Order(-2)  // Higher priority than DefaultErrorWebExceptionHandler
public class GlobalErrorWebExceptionHandler implements ErrorWebExceptionHandler {

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        
        ErrorResponse errorResponse = mapExceptionToErrorResponse(ex);
        response.setStatusCode(errorResponse.getStatus());
        
        byte[] bytes = serializeErrorResponse(errorResponse);
        DataBuffer buffer = response.bufferFactory().wrap(bytes);
        
        return response.writeWith(Mono.just(buffer));
    }
    
    private ErrorResponse mapExceptionToErrorResponse(Throwable ex) {
        if (ex instanceof JwtException) {
            return new ErrorResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", 
                "Invalid or expired JWT token");
        } else if (ex instanceof RateLimitExceededException) {
            return new ErrorResponse(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please try again later.");
        } else if (ex instanceof TimeoutException) {
            return new ErrorResponse(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
                "Downstream service did not respond within 30 seconds");
        } else if (ex instanceof CircuitBreakerOpenException) {
            return new ErrorResponse(HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE",
                "Service is temporarily unavailable. Please try again later.");
        } else {
            return new ErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred");
        }
    }
}
```

### Downstream Error Propagation

**Behavior:**

- If downstream service returns error response in standard format: Propagate as-is
- If downstream service returns non-standard error: Wrap in standard format
- HTTP status code is preserved from downstream service
- Gateway adds `X-Gateway-Error: false` header to indicate error originated from downstream

**Example:**

Downstream service returns:
```json
{
  "error": {
    "code": "FORBIDDEN",
    "message": "User is not a member of this group"
  },
  "timestamp": "2026-02-23T10:30:15Z"
}
```

Gateway propagates to client unchanged with `403 Forbidden` status.

### Error Logging

**All errors are logged with:**
- Request ID (correlation ID)
- HTTP method and path
- Client IP address
- User ID (if authenticated)
- Exception stack trace (for 5xx errors)
- No sensitive data (passwords, tokens, secrets)

---

## Observability & Logging

### Actuator Endpoints

**Health Check:**
```bash
curl http://localhost:8080/actuator/health
```

**Gateway Routes:**
```bash
curl http://localhost:8080/actuator/gateway/routes
```

**Metrics:**
```bash
curl http://localhost:8080/actuator/metrics
```

### Logging Configuration

**Log Levels:**

| Logger | Level | Purpose |
|--------|-------|---------|
| `com.example.gateway` | `DEBUG` | Gateway-specific logs |
| `org.springframework.cloud.gateway` | `INFO` | Gateway framework logs |
| `root` | `INFO` | General application logs |

**Log Format:**

```
[timestamp] [level] [thread] [logger] - [message]
```

**Example:**
```
2026-02-23 10:30:00 INFO  [reactor-http-nio-2] c.e.g.f.JwtAuthenticationFilter - JWT validated for user: admin@example.com (userId=123, role=ADMIN)
```

### Structured Logging

**Current Implementation:** Plain text logs

**Future Enhancement:** JSON-structured logs for log aggregation (ELK, Splunk)

**Example:**
```json
{
  "timestamp": "2026-02-23T10:30:00Z",
  "level": "INFO",
  "logger": "com.example.gateway.filter.JwtAuthenticationFilter",
  "message": "JWT validated",
  "userId": 123,
  "email": "admin@example.com",
  "role": "ADMIN"
}
```

---

## Deployment Considerations

### Docker

**Dockerfile:**
```dockerfile
FROM openjdk:17-jdk-slim
WORKDIR /app
COPY target/api-gateway-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Build:**
```bash
docker build -t api-gateway:1.0 .
```

**Run:**
```bash
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  api-gateway:1.0
```

### Docker Compose

**Configuration:**
```yaml
services:
  api-gateway:
    build: ./api-gateway
    ports:
      - "8080:8080"
    environment:
      - JWT_SECRET=${JWT_SECRET}
      - SPRING_DATA_REDIS_HOST=redis
    depends_on:
      - identity-service
      - user-group-service
      - project-config-service
      - redis
    networks:
      - samt-network
```

### Environment-Specific Configuration

**Local Development:**
- Use `application-local.yml` profile
- Service URLs: `http://localhost:8081`, `http://localhost:8082`, etc.

**Docker Compose:**
- Use `application-docker.yml` profile
- Service URLs: `http://identity-service:8081`, `http://user-group-service:8082`, etc.

**Kubernetes:**
- Use ConfigMaps for configuration
- Service URLs: `http://identity-service.samt.svc.cluster.local:8081`, etc.

---

## Known Limitations

### 1. Static Service Discovery

**Issue:** Service URLs are hardcoded in configuration.

**Current Behavior:**
- Manual configuration required for scaling
- No automatic failover to healthy instances
- Docker Compose uses service names for DNS resolution
- Kubernetes Service provides built-in load balancing

### 2. No Token Revocation

**Issue:** Valid JWT works until expiration (15 minutes).

**Current Behavior:**
- Stolen tokens can be used until expiration
- No immediate logout enforcement
- Short token TTL (15 minutes) limits exposure window

---

## Testing

### Unit Tests

**Test JWT Validation:**
```bash
mvn test -Dtest=JwtUtilTest
```

**Test Routing Configuration:**
```bash
mvn test -Dtest=GatewayRoutesConfigTest
```

### Integration Tests

**Test Public Endpoints:**
```bash
curl -X POST http://localhost:8080/api/identity/login \
  -H "Content-Type: application/json" \
  -d '{"email": "admin@example.com", "password": "SecurePass@123"}'
```

**Test Protected Endpoints:**
```bash
curl -X GET http://localhost:8080/api/groups \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**Test JWT Validation Failure:**
```bash
curl -X GET http://localhost:8080/api/groups \
  -H "Authorization: Bearer invalid-token"
```

**Expected Response:**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired JWT token"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

### Load Testing

**Tool:** Apache JMeter, Gatling, k6

**Scenario:**
1. Authenticate to get JWT token
2. Send 1000 requests to protected endpoint
3. Measure latency and throughput

**Expected Performance:**
- Throughput: 1000+ requests/second
- Latency (P95): < 50ms
- Error rate: < 0.1%
