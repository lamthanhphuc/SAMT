# ADR-002: API Gateway Routing for Project Config Service

**Date:** February 9, 2026  
**Status:** ✅ Accepted  
**Deciders:** Tech Lead, Backend Team, DevOps  

---

## Context

### Current State (Before)

Project Config Service exposes REST API on port 8083, but API Gateway does NOT route to it:

```
✅ Client → Gateway:8080 → Identity Service:8081 (routed)
✅ Client → Gateway:8080 → User-Group Service:8082 (routed)
❌ Client → :8083 → Project Config Service (BYPASSES Gateway!)
```

**Current Gateway Routes:**
```java
// api-gateway/src/main/java/.../config/GatewayRoutesConfig.java

.route("identity-service", r -> r
    .path("/api/identity/**")
    .uri("http://identity-service:8081"))
    
.route("user-group-service", r -> r
    .path("/api/groups/**", "/api/users/**")
    .uri("http://user-group-service:8082"))

// NO ROUTE for Project Config Service REST API
```

### Problems Identified

**🔴 CRITICAL Security & Architecture Issues:**

1. **Authentication Bypass Risk:** 
   - Clients can call Project Config directly on port 8083
   - Bypasses centralized JWT validation at Gateway
   - Each service duplicates JWT validation logic

2. **No Gateway-Level Policies:**
   - ❌ Cannot apply rate limiting
   - ❌ Cannot enforce CORS uniformly
   - ❌ No centralized logging/tracing
   - ❌ No circuit breaker for client calls

3. **Inconsistent Client Experience:**
   - Identity & User-Group: Must go through Gateway
   - Project Config: Direct access (different base URL)
   - Confusing for API consumers

4. **Violates Microservices Pattern:**
   - API Gateway should be SINGLE ENTRY POINT
   - Services should NOT be directly accessible from outside

---

## Decision

**We MANDATE that ALL client REST API access to Project Config Service MUST go through API Gateway.**

### Implementation Plan (PHASE 2)

#### 1. Add Gateway Route

**File:** `api-gateway/src/main/java/com/example/gateway/config/GatewayRoutesConfig.java`

**Add Route:**
```java
@Bean
public RouteLocator customRouteLocator(RouteLocatorBuilder builder) {
    return builder.routes()
        // ... existing routes ...
        
        .route("project-config-service", r -> r
            .path("/api/project-configs/**")
            .filters(f -> f
                .stripPrefix(2)  // Remove /api/project-configs → forward as /
                .addRequestHeader("X-Gateway-Request", "true")
            )
            .uri("http://project-config-service:8083")
        )
        .build();
}
```

**Path Mapping:**
```
Client Request:      http://gateway:8080/api/project-configs/{id}
                           ↓ stripPrefix(2) removes /api/project-configs
Forwarded to:        http://project-config-service:8083/{id}
```

#### 2. Update Project Config REST Controller

**File:** `project-config-service/.../controller/ProjectConfigController.java`

**Current:**
```java
@RestController
@RequestMapping("/api/project-configs")  // Base path includes /api
public class ProjectConfigController { ... }
```

**Update to:**
```java
@RestController
@RequestMapping("/")  // Remove /api prefix (Gateway already strips it)
public class ProjectConfigController { 
    // Endpoints now: GET /, POST /, GET /{id}, etc.
}
```

**Or keep current and adjust stripPrefix:**
```java
// In Gateway: Use stripPrefix(1) instead of stripPrefix(2)
.filters(f -> f.stripPrefix(1))  // Only remove /api
```

#### 3. Update Internal Controller (Optional)

**File:** `InternalConfigController.java` - Keep direct access for internal services

```java
@RestController
@RequestMapping("/internal/project-configs")  // NOT routed through Gateway
public class InternalConfigController {
    // Used by Sync Service via direct service-to-service call
}
```

---

## Authentication Flow (After Implementation)

### REST API (via Gateway)

```
┌─────────────────────────────────────────────────────────────────┐
│ Client (Browser/Mobile/Postman)                                │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             │ POST /api/project-configs
                             │ Authorization: Bearer eyJhbGc...
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ API Gateway (port 8080)                                         │
│  1. JwtAuthenticationFilter validates JWT                       │
│  2. Extract: userId, email, role                                │
│  3. Inject headers: X-User-Id, X-User-Email, X-User-Role        │
│  4. Route to Project Config Service                             │
└────────────────────────────┬────────────────────────────────────┘
                             │ Headers: X-User-Id: 123
                             │          X-User-Email: user@example.com
                             │          X-User-Role: STUDENT
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│ Project Config Service (port 8083)                              │
│  1. Read user info from injected headers                        │
│  2. Verify group leadership (gRPC to User-Group)                │
│  3. Process request                                             │
│  4. Return response                                             │
└─────────────────────────────────────────────────────────────────┘
```

### gRPC (Inter-Service, Unchanged)

```
Sync Service → (gRPC:9093) → Project Config gRPC Server
User-Group Service ← (gRPC:9095) ← Project Config gRPC Client
```

---

## Consequences

### ✅ Positive

1. **Security:**
   - ✅ Single JWT validation point (Gateway)
   - ✅ Consistent authentication across all services
   - ✅ Prevents direct service access bypass

2. **Observability:**
   - ✅ Centralized logging (all requests pass through Gateway)
   - ✅ Distributed tracing via Gateway
   - ✅ Metrics collection at single point

3. **Resilience:**
   - ✅ Can add Circuit Breaker at Gateway level
   - ✅ Rate limiting per client
   - ✅ Retry logic if Project Config is temporarily down

4. **Consistency:**
   - ✅ All services accessed via same base URL (http://gateway:8080/api/*)
   - ✅ Uniform CORS policies
   - ✅ Same error response format

5. **Flexibility:**
   - ✅ Can swap Project Config implementation without client changes
   - ✅ Can add API versioning at Gateway
   - ✅ Can A/B test with Gateway routing rules

### ⚠️ Considerations

1. **Network Hop:** Extra hop (Gateway → Service) adds ~5-10ms latency
   - **Mitigation:** Acceptable for REST use cases; gRPC remains direct

2. **Gateway as SPOF:** If Gateway goes down, all REST APIs unavailable
   - **Mitigation:** Run multiple Gateway instances (planned in production)

3. **Path Mapping Complexity:** Need to ensure stripPrefix matches controller @RequestMapping
   - **Mitigation:** Document clearly, add integration tests

### ❌ Rejected Alternatives

**Alternative 1: Keep Direct Access**
- Rejected: Violates Gateway pattern, security risk, inconsistent architecture

**Alternative 2: Use Nginx/Traefik Instead**
- Rejected: Spring Cloud Gateway already in use, no benefit to switching

---

## Migration Strategy

### Phase 1: Documentation (✅ COMPLETE)
- [x] Update SRS_REFACTORED.md
- [x] Update 00_SYSTEM_OVERVIEW.md
- [x] Update IMPLEMENTATION_GUIDE.md
- [x] Create ADR-001 (REST+gRPC decision)
- [x] Create ADR-002 (this document)

### Phase 2: Implementation (⏳ PENDING)
- [ ] Add Gateway route in `GatewayRoutesConfig.java`
- [ ] Adjust Project Config Controller paths if needed
- [ ] Update Swagger aggregation in Gateway
- [ ] Update docker-compose port exposure strategy

### Phase 3: Testing (⏳ PENDING)
- [ ] Unit test: Gateway route configuration
- [ ] Integration test: JWT flow through Gateway → Project Config
- [ ] E2E test: Create config, verify Jira, get masked tokens
- [ ] Load test: Ensure Gateway can handle traffic

### Phase 4: Deployment (⏳ PENDING)
- [ ] Deploy to dev environment
- [ ] Verify health checks
- [ ] Update environment documentation
- [ ] Monitor logs for routing errors

### Phase 5: Deprecation (Future)
- [ ] Block direct access to port 8083 from external clients (firewall rule)
- [ ] Keep port 8083 open for internal service health checks

---

## Testing Checklist

```bash
# Before (should fail after firewall rules applied)
curl http://localhost:8083/api/project-configs \
  -H "Authorization: Bearer <token>"  # ❌ Should be blocked

# After (correct way)
curl http://localhost:8080/api/project-configs \
  -H "Authorization: Bearer <token>"  # ✅ Works via Gateway

# Verify JWT injection
curl http://localhost:8080/api/project-configs \
  -H "Authorization: Bearer <token>" -v
# Should see X-User-Id, X-User-Email, X-User-Role in forwarded request

# Test invalid JWT
curl http://localhost:8080/api/project-configs \
  -H "Authorization: Bearer invalid-token"
# Should get 401 Unauthorized from Gateway (not from Project Config)
```

---

## Configuration Updates

### docker-compose.yml (After PHASE 2)

```yaml
# Before: Project Config exposed on 8083
project-config-service:
  ports:
    - "8083:8083"  # ❌ Remove external exposure
    - "9093:9093"  # Keep gRPC

# After: Only expose via Gateway
project-config-service:
  expose:
    - "8083"  # Internal Docker network only
  ports:
    - "9093:9093"  # gRPC still exposed for tools like grpcurl
```

### Swagger UI (After PHASE 2)

**File:** `api-gateway/.../config/SwaggerConfig.java`

```java
@Bean
public GroupedOpenApi projectConfigApi() {
    return GroupedOpenApi.builder()
        .group("project-config")
        .pathsToMatch("/api/project-configs/**")
        .build();
}
```

**Access:** http://localhost:8080/swagger-ui.html → Select "project-config" group

---

## Rollback Plan

If Gateway routing causes issues:

1. **Immediate:** Re-expose Project Config port 8083 in docker-compose
2. **Document:** Add warning in README that direct access is temporary
3. **Debug:** Check Gateway logs for routing errors
4. **Fix:** Adjust stripPrefix or controller paths
5. **Retry:** Test again after fix

---

## References

- [API Gateway Implementation Guide](IMPLEMENTATION_GUIDE.md#1-api-gateway---jwt-authentication)
- [Project Config API Contract](ProjectConfig/02_API_Contract.md)
- [Architecture Compliance Report](../ARCHITECTURE_COMPLIANCE_REPORT.md)
- [Spring Cloud Gateway Docs](https://spring.io/projects/spring-cloud-gateway)

---

## Sign-off

- [x] Tech Lead: Approved - Feb 9, 2026
- [x] Backend Team: Approved - Feb 9, 2026
- [ ] DevOps: Pending review for firewall rules
- [ ] QA Team: Pending routing tests

**Implementation Target:** Sprint 2, Week 1  
**Next Review:** After PHASE 2 code implementation
