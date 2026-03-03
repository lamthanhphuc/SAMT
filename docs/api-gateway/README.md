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

API Gateway is the **single entry point** for all client requests to SAMT system microservices. It handles external JWT validation, request routing, and forwarding a short-lived internal JWT to downstream services.

### Core Responsibilities

✅ **Request Routing:**
- Route HTTP requests to backend microservices
- Path-based routing with prefix stripping
- Service discovery via direct URLs (no load balancer)

✅ **Authentication:**
- JWT validation for protected endpoints (RS256 via JWKS)
- Public endpoint bypass (login, register, refresh token)
- Extract user info from JWT claims

✅ **Internal JWT Minting & Forwarding:**
- Mint short-lived internal JWT (RS256) after validating the external JWT
- Forward internal JWT via `Authorization: Bearer <internal-jwt>`
- Downstream services validate internal JWT via the gateway internal JWKS
- Replay risk reduction via short TTL + `jti`

✅ **Security:**
- CORS whitelist configuration
- Redis-based rate limiting (per-IP and per-endpoint)
- Request size limiting (10MB max)
- Internal JWT-based communication (RS256/JWKS)
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
  ↓ Internal JWT Minting & Forwarding (Authorization: Bearer <internal-jwt>)
      ↓ Circuit Breaker + Timeout
      ↓
Downstream Services
  ↓ Internal JWT Validation
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
Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6ImlkZW50aXR5LTEifQ...
```

**Gateway Processing:**
1. Check rate limit (Redis)
2. Extract JWT from `Authorization` header
3. Validate signature using JWKS (`JWT_JWKS_URI`)
4. Check token expiration
5. Extract claims: `sub` (userId) + `roles` (array)
6. Mint short-lived internal JWT (RS256)
7. Forward to `http://user-group-service:8082/groups/1` with circuit breaker

**Forwarded Request:**
```http
GET /groups/1 HTTP/1.1
Host: user-group-service:8082
Authorization: Bearer <internal-jwt>
X-Forwarded-Host: gateway
```

**Downstream Service:**
1. Validate internal JWT via gateway internal JWKS (`GATEWAY_INTERNAL_JWKS_URI`)
2. Enforce issuer/service/clock-skew validators
3. Derive identity/roles from JWT claims
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

# JWT validation (RS256 via JWKS)
JWT_JWKS_URI=http://identity-service:8081/.well-known/jwks.json

# Internal JWT signing (Gateway → Service)
GATEWAY_INTERNAL_JWT_PRIVATE_KEY_PEM_PATH=/certs/gateway-private.pkcs8.pem
GATEWAY_INTERNAL_JWT_KID=gateway-internal-2026-01
GATEWAY_INTERNAL_JWT_TTL_SECONDS=20
GATEWAY_INTERNAL_JWT_CLOCK_SKEW_SECONDS=30
GATEWAY_INTERNAL_JWT_ISSUER=samt-gateway

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
  -e JWT_JWKS_URI=http://identity-service:8081/.well-known/jwks.json \
  -e GATEWAY_INTERNAL_JWT_PRIVATE_KEY_PEM_PATH=/certs/gateway-private.pkcs8.pem \
  -e GATEWAY_INTERNAL_JWT_KID=gateway-internal-2026-01 \
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
    - JWT_JWKS_URI=${JWT_JWKS_URI}
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
