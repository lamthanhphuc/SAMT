# API Contract – API Gateway

**Version:** 1.0  
**Base URL:** `http://gateway:8080`  
**Date:** February 23, 2026

---

## Overview

API Gateway is a **reverse proxy** with JWT validation. It does not define business endpoints. Instead, it forwards requests to downstream services after authentication and authorization context injection.

---

## Base URL

**External Clients:**
```
http://gateway:8080
```

**Docker Compose:**
```
http://api-gateway:8080
```

---

## Authentication

### Public Endpoints (No JWT Required)

The following endpoints are accessible without JWT token:

| Path | Target Service | Purpose |
|------|---------------|---------|
| `POST /api/identity/register` | Identity Service | User registration |
| `POST /api/identity/login` | Identity Service | Login & JWT issuance |
| `POST /api/identity/refresh-token` | Identity Service | Refresh access token |
| `GET /actuator/**` | Gateway (local) | Health checks |
| `GET /swagger-ui/**` | Gateway (local) | API documentation |
| `GET /v3/api-docs/**` | Gateway (local) | OpenAPI specs |

### Protected Endpoints (JWT Required)

All other endpoints require `Authorization: Bearer <JWT>` header.

**Request Format:**
```http
GET /api/groups/1 HTTP/1.1
Host: gateway:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**JWT Validation:**
1. Extract token from `Authorization` header
2. Verify signature using shared JWT secret
3. Check expiration time
4. Extract claims: `userId`, `email`, `role`

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

## Route Definitions

### 1. Identity Service Routes

**Incoming Path:** `/api/identity/**`

**Target Service:** `http://identity-service:8081`

**Authorization Requirement:** Public for auth endpoints, JWT required for others

**Request Forwarding Behavior:**

| Client Request | Gateway Action | Forwarded Request | Notes |
|---------------|---------------|-------------------|-------|
| `POST /api/identity/register` | Strip `/api/identity` | `POST /register` | Public |
| `POST /api/identity/login` | Strip `/api/identity` | `POST /login` | Public |
| `POST /api/identity/refresh-token` | Strip `/api/identity` | `POST /refresh-token` | Public |
| `POST /api/identity/logout` | Strip `/api/identity` + Inject headers | `POST /logout` + `X-User-Id`, `X-User-Email`, `X-User-Role` | Protected |
| `GET /api/identity/profile` | Strip `/api/identity` + Inject headers | `GET /profile` + headers | Protected |

**Example:**

**Client Request:**
```http
GET /api/identity/profile HTTP/1.1
Host: gateway:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Forwarded to Identity Service:**
```http
GET /profile HTTP/1.1
Host: identity-service:8081
X-User-Id: 123
X-User-Email: admin@example.com
X-User-Role: ADMIN
X-Timestamp: 1708704600000
X-Internal-Signature: a7f3b8c2d9e1f4a3b8c2d9e1f4a3b8c2
X-Forwarded-Host: gateway
```

---

### 2. User-Group Service Routes

**Incoming Paths:**
- `/api/groups/**`
- `/api/users/**`

**Target Service:** `http://user-group-service:8082`

**Authorization Requirement:** JWT required for all endpoints

**Request Forwarding Behavior:**

| Client Request | Gateway Action | Forwarded Request |
|---------------|---------------|-------------------|
| `GET /api/groups` | Strip `/api` + Inject headers | `GET /groups` + headers |
| `POST /api/groups` | Strip `/api` + Inject headers | `POST /groups` + headers |
| `GET /api/groups/1/members` | Strip `/api` + Inject headers | `GET /groups/1/members` + headers |
| `GET /api/users/1/groups` | Strip `/api` + Inject headers | `GET /users/1/groups` + headers |

**Example:**

**Client Request:**
```http
POST /api/groups HTTP/1.1
Host: gateway:8080
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
Content-Type: application/json

{"name": "Group A", "semesterId": 1}
```

**Forwarded to User-Group Service:**
```http
POST /groups HTTP/1.1
Host: user-group-service:8082
X-User-Id: 123
X-User-Email: admin@example.com
X-User-Role: ADMIN
X-Timestamp: 1708704600000
X-Internal-Signature: a7f3b8c2d9e1f4a3b8c2d9e1f4a3b8c2
X-Forwarded-Host: gateway
Content-Type: application/json

{"name": "Group A", "semesterId": 1}
```

---

### 3. Project Config Service Routes

**Incoming Path:** `/api/project-configs/**`

**Target Service:** `http://project-config-service:8083`

**Authorization Requirement:** JWT required for all endpoints

**Request Forwarding Behavior:**

| Client Request | Gateway Action | Forwarded Request |
|---------------|---------------|-------------------|
| `POST /api/project-configs` | Strip `/api` + Inject headers | `POST /project-configs` + headers |
| `GET /api/project-configs/1` | Strip `/api` + Inject headers | `GET /project-configs/1` + headers |
| `PUT /api/project-configs/1` | Strip `/api` + Inject headers | `PUT /project-configs/1` + headers |
| `DELETE /api/project-configs/1` | Strip `/api` + Inject headers | `DELETE /project-configs/1` + headers |

---

### 4. Sync Service Routes

**Incoming Path:** `/api/sync/**`

**Target Service:** `http://sync-service:8084`

**Authorization Requirement:** JWT required (admin only, enforced by downstream service)

**Request Forwarding Behavior:**

| Client Request | Gateway Action | Forwarded Request |
|---------------|---------------|-------------------|
| `GET /api/sync/status` | Strip `/api/sync` + Inject headers | `GET /status` + headers |
| `POST /api/sync/trigger` | Strip `/api/sync` + Inject headers | `POST /trigger` + headers |

**Note:** Gateway validates JWT but does NOT enforce role-based authorization. Sync Service verifies `X-User-Role: ADMIN` before executing admin operations.

---

### 5. Analysis Service Routes (Planned)

**Incoming Path:** `/api/analysis/**`

**Target Service:** `http://analysis-service:8085`

**Authorization Requirement:** JWT required

**Request Forwarding Behavior:**

| Client Request | Gateway Action | Forwarded Request |
|---------------|---------------|-------------------|
| `GET /api/analysis/groups/1/summary` | Strip `/api/analysis` + Inject headers | `GET /groups/1/summary` + headers |
| `GET /api/analysis/reports/1` | Strip `/api/analysis` + Inject headers | `GET /reports/1` + headers |

---

### 6. Report Service Routes (Planned)

**Incoming Path:** `/api/reports/**`

**Target Service:** `http://report-service:8086`

**Authorization Requirement:** JWT required

**Request Forwarding Behavior:**

| Client Request | Gateway Action | Forwarded Request |
|---------------|---------------|-------------------|
| `GET /api/reports/groups/1` | Strip `/api/reports` + Inject headers | `GET /groups/1` + headers |
| `POST /api/reports/generate` | Strip `/api/reports` + Inject headers | `POST /generate` + headers |

---

## Global Filters

Gateway applies the following filters to all requests:

### 1. JWT Authentication + Signed Header Injection Filter

**Applied to:** All requests except public endpoints

**Behavior:**
1. Extract JWT from `Authorization: Bearer <token>` header
2. Validate signature and expiration
3. Extract claims: `userId`, `email`, `role`
4. Generate timestamp (milliseconds since epoch)
5. Generate HMAC-SHA256 signature: `HMAC(gatewaySecret, userId|email|role|timestamp)`
6. Inject signed headers:
   - `X-User-Id`: User ID from JWT
   - `X-User-Email`: Email from JWT
   - `X-User-Role`: Role from JWT
   - `X-Timestamp`: Request timestamp
   - `X-Internal-Signature`: HMAC-SHA256 signature
7. Set Spring Security context

**Signature Generation:**
```java
String payload = userId + "|" + email + "|" + role + "|" + timestamp;
String signature = HmacUtils.hmacSha256Hex(gatewaySecret, payload);
```

**Downstream Service Verification Requirements:**
1. Verify all 5 headers are present
2. Verify timestamp is within 60-second window
3. Re-compute HMAC signature and compare with `X-Internal-Signature`
4. Reject request if signature invalid (401 Unauthorized)

**Failure:** Return `401 Unauthorized` if JWT validation fails

### 2. Strip Prefix Filter

**Applied to:** All routes

**Behavior:**
- Remove service-specific path prefix before forwarding
- Example: `/api/identity/login` → `/login`

### 3. X-Forwarded-Host Injection

**Applied to:** All routes

**Behavior:**
- Inject `X-Forwarded-Host: gateway` header
- Allows downstream services to identify requests from gateway

### 4. CORS Filter

**Applied to:** All routes

**Behavior:**
- Allow whitelisted origins only (configured in `application.yml`)
  - Production: `https://samt.example.com`, `https://admin.samt.example.com`
- Allow methods: `GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `OPTIONS`
- Allow headers: `Authorization`, `Content-Type`
- Expose headers: `Authorization`, `X-User-Id`, `X-User-Role`
- Credentials: `false`
- Max age: `3600` seconds

### 5. Rate Limiting Filter

**Applied to:** All routes (per-IP rate limiting)

**Limits:**
- Global: 100 requests per second (burst capacity: 200)
- Login endpoint: 5 requests per minute (burst capacity: 10)

**Behavior:**
- Rate limit by client IP address
- Return `429 Too Many Requests` when limit exceeded

### 6. Circuit Breaker Filter

**Applied to:** All downstream service routes

**Behavior:**
- Open circuit when 50% of requests fail within sliding window
- Return `503 Service Unavailable` when circuit open
- Automatically attempt recovery after 30 seconds

### 7. Request Size Limit

**Applied to:** All routes

**Limit:** 10MB max request body size

**Behavior:**
- Reject requests exceeding 10MB with `413 Payload Too Large`

---

## Error Handling

### Gateway Error Responses

**401 Unauthorized:**
```json
{
  "error": {
    "code": "UNAUTHORIZED",
    "message": "Invalid or expired JWT token"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**413 Payload Too Large:**
```json
{
  "error": {
    "code": "PAYLOAD_TOO_LARGE",
    "message": "Request body exceeds 10MB limit"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**429 Too Many Requests:**
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
- `X-RateLimit-Remaining`: Remaining requests in current window
- `X-RateLimit-Burst-Capacity`: Maximum burst capacity
- `X-RateLimit-Replenish-Rate`: Tokens replenished per second

**404 Not Found:**
```json
{
  "error": {
    "code": "NOT_FOUND",
    "message": "No route found for path: /api/invalid"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**503 Service Unavailable:**
```json
{
  "error": {
    "code": "SERVICE_UNAVAILABLE",
    "message": "Downstream service is unavailable"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

### Downstream Service Errors

Gateway **does NOT transform** error responses from downstream services. Errors are forwarded as-is to the client.

**Example:**

If Identity Service returns:
```json
{
  "error": {
    "code": "EMAIL_ALREADY_EXISTS",
    "message": "Email already registered"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

Gateway forwards this response unchanged to the client.

---

## Response Standard Format

Gateway does not define response schema. Response format is determined by downstream services.

**Success Response (from downstream service):**
```json
{
  "data": { ... },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

**Error Response (from downstream service):**
```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Invalid input",
    "field": "email"
  },
  "timestamp": "2026-02-23T10:30:00Z"
}
```

---

## Actuator Endpoints

### 1. GET `/actuator/health`

**Description:** Gateway health status

**Authorization:** None (public)

**Response 200 OK:**
```json
{
  "status": "UP"
}
```

**Response 503 Service Unavailable:**
```json
{
  "status": "DOWN"
}
```

---

### 2. GET `/actuator/gateway/routes`

**Description:** List all configured routes

**Authorization:** None (internal network only)

**Response 200 OK:**
```json
[
  {
    "route_id": "identity-service",
    "uri": "http://identity-service:8081",
    "predicates": ["/api/identity/**"],
    "filters": ["StripPrefix=2", "AddRequestHeader"]
  },
  {
    "route_id": "user-group-service",
    "uri": "http://user-group-service:8082",
    "predicates": ["/api/groups/**", "/api/users/**"],
    "filters": ["StripPrefix=1", "AddRequestHeader"]
  }
]
```

---

## Header Injection Reference

Gateway injects the following headers for all protected endpoints:

| Header | Source | Example Value | Purpose |
|--------|--------|---------------|---------|
| `X-User-Id` | JWT claim `userId` | `123` | User identification |
| `X-User-Email` | JWT claim `subject` | `admin@example.com` | Email verification |
| `X-User-Role` | JWT claim `role` | `ADMIN` | Role-based authorization |
| `X-Timestamp` | Generated by Gateway | `1708704600000` | Request timestamp (milliseconds) |
| `X-Internal-Signature` | HMAC-SHA256 signature | `a7f3b8c2d9e1f4a3...` | Signature for header integrity |
| `X-Forwarded-Host` | Static value | `gateway` | Request origin identification |

### Signature Generation

**Algorithm:** HMAC-SHA256

**Secret:** Gateway internal secret (`gateway.internal.secret`)

**Payload:** `userId|email|role|timestamp`

**Example:**
```java
String payload = "123|admin@example.com|ADMIN|1708704600000";
String signature = HmacUtils.hmacSha256Hex(gatewaySecret, payload);
// Result: "a7f3b8c2d9e1f4a3b8c2d9e1f4a3b8c2"
```

**Downstream Service Usage:**

```java
// Downstream service reads and verifies headers
String userId = request.getHeader("X-User-Id");
String email = request.getHeader("X-User-Email");
String role = request.getHeader("X-User-Role");
String timestamp = request.getHeader("X-Timestamp");
String signature = request.getHeader("X-Internal-Signature");

// Verify headers exist
if (userId == null || signature == null) {
    throw new UnauthorizedException("Missing internal headers");
}

// Verify timestamp (60 second window)
long requestTime = Long.parseLong(timestamp);
long currentTime = System.currentTimeMillis();
if (Math.abs(currentTime - requestTime) > 60000) {
    throw new UnauthorizedException("Request timestamp expired");
}

// Verify signature
String payload = userId + "|" + email + "|" + role + "|" + timestamp;
String expectedSignature = HmacUtils.hmacSha256Hex(gatewaySecret, payload);
if (!expectedSignature.equals(signature)) {
    throw new UnauthorizedException("Invalid internal signature");
}

// Signature valid, use user context
Long userIdLong = Long.parseLong(userId);
if (!"ADMIN".equals(role)) {
    throw new ForbiddenException("Admin access required");
}
```

---

## Known Limitations

### 1. No Response Transformation

Gateway forwards responses from downstream services without modification. Client must handle different response formats from different services.

### 2. No Request Aggregation

Gateway does not support request aggregation (e.g., BFF pattern). Client must make multiple requests for composite data.

### 3. No Protocol Translation

Gateway only supports HTTP/REST. No gRPC-to-REST transcoding. Services must expose REST APIs for client communication.

### 4. No Request Validation

Gateway does not validate request payloads. Validation is delegated to downstream services.
