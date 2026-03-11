# API Gateway

## Responsibilities

- Serve as the single public HTTP ingress for SAMT.
- Route requests to backend services without exposing internal service addresses.
- Validate external JWTs against the Identity Service JWKS.
- Publish an internal JWKS endpoint and forward internal trust context to downstream services.
- Apply fallback behavior for selected upstream failures.

## APIs

- Public routed paths include `/api/auth/**`, `/api/users/**`, `/api/groups/**`, `/api/semesters/**`, `/api/project-configs/**`, `/api/sync/**`, `/api/analysis/**`, `/reports/**`, and notification routes configured in gateway routing.
- Internal JWKS endpoint: `GET /.well-known/internal-jwks.json`.
- Fallback handler: `/__gateway/fallback/{service}`.
- Swagger UI redirect: `GET /swagger-ui/index.html`.

## Database

- None.
- Redis is used for gateway runtime concerns such as rate limiting, not as a domain database.

## Events

- None.

## Dependencies

- Identity Service for external JWKS validation and authentication flows.
- User Group Service, Project Config Service, Sync Service, Analysis Service, Report Service, and Notification Service as routed upstreams.
- Redis for rate limiting and related gateway controls.

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
