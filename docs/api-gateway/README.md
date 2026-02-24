# API Gateway

**Version:** 1.0  
**Port:** 8080 (REST)  
**Database:** Redis (rate limiting only)  
**Last Updated:** February 23, 2026

---

## Quick Links

- **[API Contract](API_CONTRACT.md)** - Routing & forwarding behavior
- **[Security Design](SECURITY.md)** - JWT validation, CORS, authorization
- **[Implementation Guide](IMPLEMENTATION.md)** - Setup instructions

---

## Overview

API Gateway is the **single entry point** for all client requests to SAMT system microservices. It handles JWT validation, request routing, and header injection for downstream services.

### Core Responsibilities

✅ **Request Routing:**
- Route HTTP requests to backend microservices
- Path-based routing with prefix stripping
- Service discovery via direct URLs (no load balancer)

✅ **Authentication:**
- JWT validation for protected endpoints
- Public endpoint bypass (login, register, refresh token)
- Extract user info from JWT claims

✅ **Authorization Context Injection:**
- Signed internal headers with HMAC-SHA256
- Inject `X-User-Id`, `X-User-Email`, `X-User-Role`, `X-Timestamp`, `X-Internal-Signature`
- Downstream services verify signature (prevent header spoofing)
- Replay attack prevention with timestamp validation

✅ **Security:**
- CORS whitelist configuration
- Redis-based rate limiting (per-IP and per-endpoint)
- Request size limiting (10MB max)
- Signed internal communication (HMAC-based)
- Global request/response filtering
- Swagger UI aggregation

✅ **Resilience:**
- Circuit breaker pattern (Resilience4j)
- Request timeout configuration (30s max)
- Exponential backoff retry for transient failures
- Connection pooling optimization

### What It Does NOT Do

❌ JWT generation → Identity Service  
❌ Business authorization (group membership, resource ownership) → Downstream services  
❌ Request caching → Not implemented (acceptable tradeoff)  
❌ Response aggregation → Not implemented (simple proxy model)

---

## Architecture

### Service Communication

```
Client (REST/JSON)
      ↓
API Gateway (port 8080)
      ↓ Rate Limiting (Redis-based)
      ↓ JWT Validation
      ↓ Signed Header Injection (X-User-Id, X-User-Email, X-User-Role, X-Internal-Signature)
      ↓ Circuit Breaker + Timeout
      ↓
Downstream Services
      ↓ Signature Verification
  - /api/identity/** → Identity Service (8081)
  - /api/groups/** → User-Group Service (8082)
  - /api/project-configs/** → Project Config Service (8083)
  - /api/sync/** → Sync Service (8084)
  - /api/analysis/** → Analysis Service (planned)
  - /api/reports/** → Report Service (planned)
```

### Package Structure

```
api-gateway/
├── src/main/java/com/example/gateway/
│   ├── config/               # SecurityConfig, GatewayRoutesConfig
│   ├── filter/               # JwtAuthenticationFilter
│   ├── util/                 # JwtUtil
│   ├── validation/           # JwtSecretValidator
│   └── ApiGatewayApplication.java
│
└── src/main/resources/       # application.yml
```

---

## Routing Strategy

### Route Configuration

**Path Mappings:**

| Client Path | Target Service | Target Port | Prefix Stripping |
|-------------|---------------|-------------|------------------|
| `/api/identity/**` | identity-service | 8081 | `/api/identity` → `/` |
| `/api/groups/**` | user-group-service | 8082 | `/api` → `/` |
| `/api/users/**` | user-group-service | 8082 | `/api` → `/` |
| `/api/project-configs/**` | project-config-service | 8083 | `/api` → `/` |
| `/api/sync/**` | sync-service | 8084 | `/api/sync` → `/` |
| `/actuator/**` | gateway (local) | - | No forwarding |

**Example:**
- Client sends: `GET /api/identity/auth/login`
- Gateway strips: `/api/identity`
- Forwards to: `http://identity-service:8081/auth/login`

---

## Authentication Flow

### Protected Endpoint

**Request:**
```http
GET /api/groups/1 HTTP/1.1
Host: gateway:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Gateway Processing:**
1. Check rate limit (Redis)
2. Extract JWT from `Authorization` header
3. Validate signature using shared JWT secret
4. Check token expiration
5. Extract claims: `userId`, `email`, `role`
6. Generate signed headers with HMAC-SHA256
7. Forward to `http://user-group-service:8082/groups/1` with circuit breaker

**Forwarded Request:**
```http
GET /groups/1 HTTP/1.1
Host: user-group-service:8082
X-User-Id: 123
X-User-Email: admin@example.com
X-User-Role: ADMIN
X-Timestamp: 1708704600000
X-Internal-Signature: a7f3b8c2...
X-Forwarded-Host: gateway
```

**Downstream Service:**
1. Verify `X-Internal-Signature` using shared gateway secret
2. Check `X-Timestamp` (reject if older than 60 seconds)
3. Read `X-User-Id`, `X-User-Email`, `X-User-Role` from headers
4. Perform business authorization (group membership, resource ownership)
5. Return response

### Public Endpoint

**Request:**
```http
POST /api/identity/login HTTP/1.1
Host: gateway:8080
Content-Type: application/json

{"email": "admin@example.com", "password": "SecurePass@123"}
```

**Gateway Processing:**
1. Path matches public endpoint list
2. **Skip JWT validation**
3. Forward directly to Identity Service

---

## Configuration

### Environment Variables

```bash
# Server
SERVER_PORT=8080

# JWT (MUST match Identity Service)
JWT_SECRET=your-256-bit-secret-key-change-this-in-production-environment-please

# Internal Gateway Secret (for signed headers)
GATEWAY_INTERNAL_SECRET=gateway-to-service-hmac-secret-256-bit-minimum

# Redis (required for rate limiting)
SPRING_DATA_REDIS_HOST=redis
SPRING_DATA_REDIS_PORT=6379

# Rate Limiting
RATE_LIMIT_GLOBAL_REQUESTS_PER_SECOND=100
RATE_LIMIT_LOGIN_REQUESTS_PER_MINUTE=5

# Resilience
CIRCUIT_BREAKER_FAILURE_RATE_THRESHOLD=50
REQUEST_TIMEOUT_SECONDS=30

# Logging
LOG_LEVEL=INFO
GATEWAY_LOG_LEVEL=DEBUG
```

### Routing Configuration

Routes are defined in `GatewayRoutesConfig.java`:
- Static service discovery (no Eureka/Consul)
- Direct HTTP URLs to downstream services
- Path-based routing with prefix stripping

---

## Deployment Notes

### Local Development

```bash
# Run Gateway
mvn spring-boot:run

# Access Swagger UI
http://localhost:8080/swagger-ui.html
```

### Docker

```bash
# Build
docker build -t api-gateway:1.0 .

# Run
docker run -p 8080:8080 \
  -e JWT_SECRET=your-secret \
  api-gateway:1.0
```

### Docker Compose

Gateway runs on port `8080` and connects to all backend services via internal network.

```yaml
api-gateway:
  build: ./api-gateway
  ports:
    - "8080:8080"
  environment:
    - JWT_SECRET=${JWT_SECRET}
  depends_on:
    - identity-service
    - user-group-service
    - project-config-service
```

---

## Known Limitations

### 1. Static Service Discovery

**Issue:** Service URLs are hardcoded in configuration.

**Current Behavior:**
- Manual configuration required for scaling
- No dynamic discovery of service instances
- Docker Compose uses service names for DNS resolution
- Kubernetes Service provides built-in load balancing

### 2. No Token Revocation

**Issue:** Valid JWT works until expiration (15 minutes).

**Current Behavior:**
- Stolen tokens can be used until expiration
- No immediate logout enforcement
- Short token TTL (15 minutes) limits exposure window

### 3. No Request Caching

**Issue:** API Gateway does not cache responses.

**Current Behavior:**
- Repeated requests always hit downstream services
- No reduction in backend load for read-heavy operations
- Services implement caching at their own level
- CDN can be used for static content

### 4. No Request Transformation

**Issue:** Gateway only forwards requests, does not transform payloads.

**Current Behavior:**
- Client and service must agree on request/response format
- No protocol translation (e.g., REST to gRPC)
- Services expose REST APIs for client communication
- gRPC only for internal service-to-service communication
