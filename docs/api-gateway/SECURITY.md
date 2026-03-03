# Security Design – API Gateway

**Version:** 1.0  
**Last Updated:** February 23, 2026  
**Security Model:** Stateless JWT Validation + Signed Internal Headers + Rate Limiting

---

## Quick Reference

| Aspect | Specification |
|--------|--------------|
| **JWT Algorithm** | RS256 (validated via JWKS) |
| **JWT Validation** | JWKS signature validation + required-claims checks |
| **Session Model** | Stateless (no session storage) |
| **Authentication** | JWT token (validated at Gateway) |
| **Authorization** | Delegated to downstream services |
| **Internal Communication** | HMAC-SHA256 signed headers |
| **Rate Limiting** | Redis-based (100 req/s global, 5 req/min login) |
| **CORS** | Whitelist-based configuration |
| **Request Size Limit** | 10MB max |

---

## 1. Authentication Model

### JWT-Based Authentication

**Responsibility:** API Gateway validates JWT tokens for all protected endpoints.

**Flow:**
1. Client authenticates via Identity Service (`/api/identity/login`)
2. Identity Service issues JWT access token + refresh token
3. Client includes JWT in `Authorization: Bearer <token>` header
4. Gateway validates JWT signature and expiration
5. Gateway extracts user info and injects into headers
6. Gateway forwards request to downstream service

### Public Endpoints

The following endpoints do NOT require JWT authentication:

| Path | Purpose |
|------|---------|
| `POST /api/identity/register` | User registration |
| `POST /api/identity/login` | Authentication |
| `POST /api/identity/refresh-token` | Token refresh |
| `GET /actuator/**` | Health checks |
| `GET /swagger-ui/**` | API documentation |
| `GET /v3/api-docs/**` | OpenAPI specs |

**Implementation:**

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

### Protected Endpoints

All other endpoints require valid JWT token:

**Required Header:**
```
Authorization: Bearer eyJhbGciOiJSUzI1NiIsImtpZCI6ImlkZW50aXR5LTEiLCJ0eXAiOiJKV1QifQ...
```

**Validation Steps:**
1. Extract token from `Authorization` header
2. Verify signature using Identity Service JWKS (configured via `JWT_JWKS_URI`)
3. Check required claims: `sub`, `roles` (non-empty), `jti`, `iat`, `exp`
4. Derive role from `roles[0]` for header injection (`X-User-Role`)

**Failure Response (401 Unauthorized):**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired JWT token"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

---

## 2. Authorization Strategy

### Delegation Model

**Gateway Responsibility:** Authenticate user (validate JWT)

**Downstream Service Responsibility:** Authorize user (check permissions)

**Rationale:**
- Gateway has no business logic knowledge
- Authorization requires context (group membership, resource ownership)
- Downstream services enforce resource-level authorization

### Signed Header Injection

**Security Model:** HMAC-SHA256 signed headers to prevent header spoofing

**Problem Solved:** Prevent attackers from bypassing Gateway and sending forged headers directly to downstream services.

Gateway injects user context with cryptographic signature:

| Header | Source | Example |
|--------|--------|---------|
| `X-User-Id` | JWT claim `sub` | `123` |
| `X-User-Role` | JWT claim `roles[0]` | `ADMIN` |
| `X-Internal-Original-Path` | Request path | `/api/groups/123` |
| `X-Internal-Timestamp` | Unix timestamp (seconds) | `1708704600` |
| `X-Internal-Signature` | HMAC signature (request metadata) | `a7f3b8c2d9e1...` |
| `X-Internal-Key-Id` | Signing key identifier | `gateway-1` |

**Signature Generation (Gateway):**

The gateway computes an internal signature and injects the `X-Internal-*` headers (see `SignedHeaderFilter.java` + `SignedHeaderUtil`). Downstream services MUST verify the internal signature before trusting `X-User-Id` / `X-User-Role`.

**Signature Verification (Downstream Service):**

Downstream services verify `X-Internal-*` headers using their verifier (e.g. `GatewayInternalSignatureVerifier`) and then authorize using `X-User-Role`.

**Security Properties:**

| Property | Implementation | Protection |
|----------|---------------|------------|
| **Integrity** | HMAC-SHA256 signature | Prevents header tampering |
| **Authenticity** | Shared secret (Gateway + Services) | Verifies request from Gateway |
| **Replay Protection** | Timestamp validation (60s window) | Prevents replay attacks |
| **Non-repudiation** | Signature includes all user context | Audit trail integrity |

### Role-Based Access Control

**System Roles:**
- `ADMIN` - Full system access
- `LECTURER` - Read-only access to all groups
- `STUDENT` - Access to own data and groups

**Authorization Logic:**
- Gateway validates JWT and extracts `roles` claim (list)
- Gateway injects `X-User-Role` header
- Downstream service checks `X-User-Role` for authorization

**Example:**
- User with `STUDENT` role requests `GET /api/groups/1/members`
- Gateway validates JWT, injects `X-User-Role: STUDENT`
- User-Group Service checks if user is member of group 1
- If not member: Return `403 Forbidden`

---

## 3. JWT Validation Rules

### JWKS Model (Authoritative)

**Security Model:** Asymmetric key (RS256)

**Key Material:**
- Identity Service signs JWTs using `JWT_PRIVATE_KEY_PEM` and publishes public keys via `/.well-known/jwks.json`.
- API Gateway validates JWTs using `JWT_JWKS_URI`.

**LEGACY (deprecated):** Earlier documentation referenced `HS256` / `JWT_SECRET` and a shared-secret JWT model. That model is not used by the current codebase and must not be used for production deployments.

### Token Structure

**Access Token Claims:**

```json
{
  "userId": 123,
  "sub": "admin@example.com",
  "role": "ADMIN",
  "token_type": "ACCESS",
  "iat": 1706612400,
  "exp": 1706613300
}
```

**Validation Rules:**

| Rule | Check | Failure Response |
|------|-------|------------------|
| Signature | Verify HMAC-SHA256 signature | `401 Unauthorized` |
| Expiration | `exp > now` | `401 Unauthorized` |
| Required Claims | `userId`, `sub`, `role` present | `401 Unauthorized` |
| Token Type | `token_type == "ACCESS"` (optional) | `401 Unauthorized` |

### Token Expiration

**Access Token TTL:** 15 minutes (configured in Identity Service)

**Refresh Token TTL:** 7 days (stored in Identity Service database)

**Gateway Behavior:**
- Gateway rejects expired access tokens
- Client must refresh access token using `/api/identity/refresh-token` endpoint

**Expiration Check:**

```java
public Claims validateAndParseClaims(String token) {
    try {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    } catch (ExpiredJwtException e) {
        throw new IllegalArgumentException("JWT token expired", e);
    } catch (Exception e) {
        throw new IllegalArgumentException("Invalid JWT token", e);
    }
}
```

---

## 4. Internal Service Communication Security

### Service-to-Service Authentication

**Implementation:** HMAC-SHA256 signed headers

**Shared Secret Model:**
- Gateway has `GATEWAY_INTERNAL_SECRET` environment variable
- All downstream services have same secret
- Secret MUST be 256-bit or longer
- Secret rotation requires coordinated redeployment

**Protection Against:**
✅ Header spoofing (attacker cannot forge valid signature)  
✅ Gateway bypass (requests without signature rejected)  
✅ Replay attacks (timestamp validation)  
✅ Man-in-the-middle (signature integrity check)

**Attack Scenarios Mitigated:**

1. **Direct Service Access:**
   ```bash
   # Attacker tries to bypass Gateway
   curl http://project-config-service:8083/project-configs \
     -H "X-User-Id: 1" \
     -H "X-User-Role: ADMIN"
   
   # Service rejects: Missing or invalid signature
   ```

2. **Header Tampering:**
   ```bash
   # Attacker intercepts request and changes userId
   X-User-Id: 999  # Changed from 123
   X-Internal-Signature: a7f3b8c2...  # Original signature
   
   # Service rejects: Signature mismatch
   ```

3. **Replay Attack:**
   ```bash
   # Attacker captures valid request and replays later
  X-Internal-Timestamp: 1708704600  # 5 minutes old
   
   # Service rejects: Timestamp expired
   ```

### Secret Management

**Gateway Internal Secret:**

```bash
# Production deployment
INTERNAL_SIGNING_SECRET=$(openssl rand -hex 32)  # 256-bit key
```

**Secret Distribution:**
- Store in secret management service (Vault, AWS Secrets Manager)
- Inject into Gateway and all downstream services
- NEVER commit to version control
- NEVER log or expose in error messages

**Secret Rotation Strategy:**

1. **Dual-Secret Model** (zero-downtime rotation):
   ```java
   // Services accept both old and new secrets during rotation
   String oldSecret = env.get("GATEWAY_SECRET_OLD");
   String newSecret = env.get("GATEWAY_SECRET_NEW");
   
   boolean valid = verifySignature(headers, newSecret) || 
                   verifySignature(headers, oldSecret);
   ```

2. **Rotation Steps:**
   - Deploy services with new secret as secondary
   - Deploy Gateway with new secret as primary
   - Remove old secret from services after grace period (24 hours)

3. **Rotation Frequency:** Every 90 days (minimum)

---

## 5. Public Endpoint Protection

### Public Endpoints

Public endpoints are accessible without JWT:
- `POST /api/identity/register`
- `POST /api/identity/login`
- `POST /api/identity/refresh-token`

### Protection Mechanisms

#### 1. Input Validation

**Responsibility:** Downstream services (Identity Service)

**Enforcement:**
- Email format validation
- Password strength validation
- Request rate limiting (NOT implemented at Gateway)

#### 2. Brute Force Protection

**Implementation:** Redis-based rate limiting

**Rate Limits:**

| Endpoint Pattern | Limit | Window | Burst |
|-----------------|-------|--------|-------|
| `/api/identity/login` | 5 requests | 1 minute | 10 |
| `/api/identity/register` | 3 requests | 5 minutes | 5 |
| Global (all endpoints) | 100 requests | 1 second | 200 |

**Rate Limiting Strategy:**

```java
@Bean
KeyResolver ipKeyResolver() {
    return exchange -> {
        String ip = exchange.getRequest().getRemoteAddress().getAddress().getHostAddress();
        return Mono.just(ip);
    };
}

@Bean
RedisRateLimiter redisRateLimiter() {
    return new RedisRateLimiter(
        100,  // replenishRate (requests per second)
        200,  // burstCapacity (max burst)
        1     // requestedTokens (cost per request)
    );
}
```

**Rate Limit Response (429 Too Many Requests):**
```json
{
  "error": {
    "code": "RATE_LIMIT_EXCEEDED",
    "message": "Too many requests. Please try again later.",
    "retryAfter": 60
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**Protection Against:**
✅ Brute force login attacks  
✅ Credential stuffing  
✅ DDoS attacks  
✅ API abuse  
✅ Resource exhaustion

#### 3. CAPTCHA

**Current Implementation:** NOT implemented

**Limitation:**
- No bot protection on registration/login

**Risk:**
- Automated account creation
- Credential stuffing

**Mitigation:**
- Add CAPTCHA to registration/login forms (future work)
- Use infrastructure-level bot protection (Cloudflare, reCAPTCHA)

---

## 6. CORS Policy

### Configuration

**File:** `SecurityConfig.java`

**Policy:**
```java
@Bean
public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();
    configuration.setAllowedOrigins(List.of("*"));
    configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*"));
    configuration.setExposedHeaders(Arrays.asList("Authorization", "X-User-Id", "X-User-Role"));
    configuration.setAllowCredentials(false);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
}
```

### CORS Settings

| Setting | Value | Purpose |
|---------|-------|---------|
| `allowedOrigins` | `*` | Allow requests from any origin |
| `allowedMethods` | `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS` | Allow all HTTP methods |
| `allowedHeaders` | `*` | Allow all request headers |
| `exposedHeaders` | `Authorization`, `X-User-Id`, `X-User-Role` | Expose custom headers to client |
| `allowCredentials` | `false` | Do not allow credentials (cookies, auth headers) |

### Production Configuration

**Implementation:** Whitelist-based CORS

```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "https://samt.example.com"
              - "https://admin.samt.example.com"
              - "https://samt-staging.example.com"  # Staging environment
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
              - X-Request-ID
            exposedHeaders:
              - Authorization
              - X-User-Id
              - X-User-Role
              - X-RateLimit-Remaining
            allowCredentials: false
            maxAge: 3600  # Cache preflight for 1 hour
```

**Security Properties:**
✅ Only whitelisted domains can make requests  
✅ Prevents CSRF from malicious domains  
✅ Prevents data leakage to untrusted sites  
✅ Preflight caching reduces overhead

---

## 7. Security Limitations

### 1. Request Size Limit

**Implementation:** Spring WebFlux codec configuration

```yaml
spring:
  codec:
    max-in-memory-size: 10MB  # 10 megabytes
```

**Protection Against:**
✅ Large payload attacks  
✅ Memory exhaustion  
✅ Slowloris-style attacks  
✅ Malicious file uploads

**Error Response (413 Payload Too Large):**
```json
{
  "error": {
    "code": "PAYLOAD_TOO_LARGE",
    "message": "Request body exceeds 10MB limit"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

### 2. No Token Revocation

**Current Behavior:** Valid JWT works until expiration (15 minutes).

**Characteristics:**
- Stolen tokens can be used until expiration
- No immediate logout enforcement
- Short access token TTL (15 minutes) limits exposure window
- Refresh token rotation implemented at Identity Service level

### 3. No IP Whitelisting

**Current Behavior:** Gateway accepts requests from any IP address.

**Characteristics:**
- No network-level access control
- Can be deployed behind reverse proxy for IP filtering
- Infrastructure-level whitelisting available (Nginx, HAProxy)

### 4. Shared Secret Model

**Current Behavior:** Gateway and all services share same HMAC secret.

**Characteristics:**
- Single secret compromise affects entire system
- Secret rotation requires coordinated deployment across all services
- HMAC secret stored in environment variables
- Future enhancement: Migrate to mTLS (certificate-based, per-service keys)

### 5. No Request Logging

**Current Behavior:** Gateway does not log request/response payloads for audit.

**Characteristics:**
- Standard logs capture errors and authentication failures
- No detailed audit trail for security incidents
- Cannot trace malicious request payloads
- Future enhancement: Implement access log filter with centralized logging

### 6. JWT Secret in Environment Variable

**Current Behavior:** JWT secret stored in environment variable.

**Characteristics:**
- Secret visible in process list
- Secret may be leaked in logs or error messages
- Shared with Identity Service for JWT validation
- Future enhancement: Use secret management service (HashiCorp Vault, AWS Secrets Manager)

### 7. Redis Dependency

**Current Behavior:** Rate limiting depends on Redis availability.

**Characteristics:**
- Redis down = rate limiting disabled (fail-open strategy)
- No rate limiting during Redis outage
- Redis deployed in HA mode mitigates this
- Future enhancement: Fallback to in-memory rate limiting

### 8. Static Secret Distribution

**Current Behavior:** Secrets stored in environment variables or Kubernetes Secrets.

**Characteristics:**
- Kubernetes Secrets stored base64 encoded (not encrypted by default)
- Secrets require manual rotation and redeployment
- Future enhancement: Implement secret rotation automation

---

## 8. Best Practices

### JWT Secret Management

**DO:**
- Use 256-bit or longer secret
- Store secret in secret management service
- Rotate secret periodically
- Use different secrets for different environments

**DON'T:**
- Hardcode secret in code
- Commit secret to version control
- Share secret across multiple applications
- Use weak or predictable secrets

### Request Validation

**Gateway Responsibility:**
- Validate JWT signature and expiration
- Validate request headers

**Downstream Service Responsibility:**
- Validate request body
- Validate business logic
- Enforce resource-level authorization

### Error Handling

**DO:**
- Return generic error messages (`401 Unauthorized`, `403 Forbidden`)
- Log detailed errors internally
- Include request ID for tracing

**DON'T:**
- Expose stack traces to client
- Leak sensitive information in error messages
- Return different errors for valid vs invalid users (timing attacks)

### Logging

**DO:**
- Log authentication failures
- Log authorization failures
- Log unusual request patterns
- Include user ID, IP, timestamp

**DON'T:**
- Log JWT tokens (risk of token leakage)
- Log passwords or sensitive data
- Log credit card numbers or PII
- Log gateway internal secret
- Log internal signatures (HMAC values)

---

## 9. Security Checklist

### Deployment

- [x] JWT secret is 256-bit or longer
- [x] Gateway internal secret is 256-bit or longer
- [ ] Secrets are stored in secret management service (Vault, AWS Secrets Manager)
- [x] CORS is configured with specific origins (whitelist)
- [x] Request body size limit is configured (10MB)
- [x] Rate limiting is implemented (Redis-based)
- [x] Signed internal headers are implemented (HMAC-SHA256)
- [ ] Gateway is deployed behind reverse proxy (TLS termination)
- [x] Downstream services verify internal signatures
- [ ] HTTPS/TLS is enabled for all external traffic
- [ ] Security headers are configured (HSTS, CSP, X-Frame-Options)
- [ ] Request logging is enabled for audit trail

### Runtime

- [ ] Monitor authentication failures
- [ ] Monitor authorization failures
- [ ] Alert on unusual request patterns
- [ ] Rotate JWT secret periodically
- [ ] Update dependencies regularly (security patches)
- [ ] Review security logs regularly

### Testing

- [x] Test JWT validation with valid tokens
- [x] Test JWT validation with expired tokens
- [x] Test JWT validation with invalid signatures
- [x] Test public endpoints without JWT
- [x] Test protected endpoints without JWT (expect 401)
- [x] Test CORS with different origins (reject unauthorized)
- [x] Test rate limiting (429 after limit exceeded)
- [x] Test request size limits (413 for large payloads)
- [x] Test signed header verification (reject invalid signatures)
- [x] Test timestamp validation (reject old requests)
- [ ] Test circuit breaker (fallback on service failure)
- [ ] Test timeout (503 after 30 seconds)
