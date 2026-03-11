# Security Design – API Gateway

**Version:** 1.0  
**Last Updated:** February 23, 2026  
**Security Model:** External JWT Validation + Internal JWT Minting (RS256) + Rate Limiting

---

## Quick Reference

| Aspect | Specification |
|--------|--------------|
| **JWT Algorithm** | RS256 (validated via JWKS) |
| **JWT Validation** | JWKS signature validation + required-claims checks |
| **Session Model** | Stateless (no session storage) |
| **Authentication** | JWT token (validated at Gateway) |
| **Authorization** | Delegated to downstream services |
| **Internal Communication** | Gateway-issued internal JWT (RS256 via internal JWKS) |
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
5. Gateway mints a short-lived internal JWT (RS256)
6. Gateway forwards request downstream as `Authorization: Bearer <internal-jwt>`

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
4. Enforce bounded clock skew and issuer/audience validators

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

### Internal JWT Minting & Forwarding

**Security Model:** The gateway mints a **short-lived internal JWT** (RS256) after validating the external JWT. Downstream services validate the internal JWT via the gateway internal JWKS endpoint.

**Problem Solved:** Prevent direct-to-service header spoofing by ensuring downstream identity/roles come from a signed token, not from forwarded `X-*` headers.

**Forwarding Contract (Gateway → Service):**
- `Authorization: Bearer <internal-jwt>`
- Internal JWT is RS256, includes `kid` header and `jti` claim, and uses a very short TTL.

### Role-Based Access Control

**System Roles:**
- `ADMIN` - Full system access
- `LECTURER` - Read-only access to all groups
- `STUDENT` - Access to own data and groups

**Authorization Logic:**
- Gateway validates the external JWT
- Gateway mints internal JWT with user identity and roles
- Downstream service authorizes based on internal JWT claims/authorities

**Example:**
- User with `STUDENT` role requests `GET /api/groups/1/members`
- Gateway validates external JWT and mints internal JWT containing role `STUDENT`
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
| Signature | Verify RS256 signature via JWKS | `401 Unauthorized` |
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

**Implementation:** Gateway-issued internal JWT (RS256)

**Model:**
- Gateway validates the **external JWT** (Identity JWKS)
- Gateway mints a **short-lived internal JWT** and forwards it downstream as `Authorization: Bearer <internal-jwt>`
- Downstream services validate the internal JWT using the gateway internal JWKS (e.g., `GATEWAY_INTERNAL_JWKS_URI`)

**Protection Against:**
✅ Direct-to-service header spoofing (identity is token-based)  
✅ Gateway bypass (services require a valid internal JWT)  
✅ Replay risk reduction (short TTL + `jti`)  

**mTLS:** Under profile `mtls`, services additionally require mutual TLS for service-to-service HTTP/gRPC traffic.

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
    configuration.setExposedHeaders(Arrays.asList("Authorization"));
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
| `exposedHeaders` | `Authorization` | Expose response auth header (if used) |
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

### 4. Internal JWT Key Model

**Current Behavior:** Gateway signs **internal JWTs** using an asymmetric keypair and publishes the corresponding public keys via an internal JWKS endpoint.

**Characteristics:**
- Key compromise impact is scoped to the internal JWT signing key
- Key rotation can be handled via JWKS `kid` overlap (services accept multiple public keys during rotation)
- Services only require the JWKS URL (no shared secret distribution)
- mTLS can be enabled (profile `mtls`) for service-to-service transport security

### 5. No Request Logging

**Current Behavior:** Gateway does not log request/response payloads for audit.

**Characteristics:**
- Standard logs capture errors and authentication failures
- No detailed audit trail for security incidents
- Cannot trace malicious request payloads
- Future enhancement: Implement access log filter with centralized logging

### 6. Key Material Handling

**Current Behavior:** The gateway validates external JWTs via **JWKS URIs** (no shared JWT secret). Internal JWT signing keys (private keys) must be treated as sensitive secrets.

**Characteristics:**
- Use a secret manager for private keys (avoid plaintext files checked into the repo)
- Prevent accidental logging of key material
- Prefer rotation with `kid` overlap and short internal JWT TTL

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
- Log private keys or key material
- Log internal JWTs (treat as credentials)

---

## 9. Security Checklist

### Deployment

- [x] External JWT validation uses `JWT_JWKS_URI` (RS256)
- [x] Internal JWT minting is enabled (RS256) and internal JWKS is published
- [ ] Secrets are stored in secret management service (Vault, AWS Secrets Manager)
- [x] CORS is configured with specific origins (whitelist)
- [x] Request body size limit is configured (10MB)
- [x] Rate limiting is implemented (Redis-based)
- [ ] Gateway is deployed behind reverse proxy (TLS termination)
- [x] Downstream services validate internal JWT via gateway internal JWKS
- [ ] mTLS is enabled for service-to-service traffic (profile `mtls`)
- [ ] HTTPS/TLS is enabled for all external traffic
- [ ] Security headers are configured (HSTS, CSP, X-Frame-Options)
- [ ] Request logging is enabled for audit trail

### Runtime

- [ ] Monitor authentication failures
- [ ] Monitor authorization failures
- [ ] Alert on unusual request patterns
- [ ] Rotate JWT signing keys periodically (with `kid` overlap)
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
- [x] Test internal JWT validation (reject invalid signatures)
- [x] Test internal JWT TTL/clock-skew validation
- [ ] Test circuit breaker (fallback on service failure)
- [ ] Test timeout (503 after 30 seconds)
