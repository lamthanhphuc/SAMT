# API Test Cases (OpenAPI-Driven)

## Scope

- Source of truth: `../openapi.yaml`
- Environment: `API_BASE_URL=http://localhost:9080`

## Core Cases

1. **Happy Path**
   - Valid request payload returns expected status code.
   - Response payload matches OpenAPI schema.

2. **Required Fields**
   - Remove each required field one by one.
   - Expect `400` with validation error details.

3. **Invalid Formats**
   - Invalid email, UUID, enum, and date-time formats.
   - Expect `400` and stable error structure.

4. **Authorization / Authentication**
   - Missing token: `401` or `403`.
   - Invalid/expired token: `401`.
   - Wrong role: `403`.

5. **Error Handling**
   - Timeout behavior returns non-2xx and no stack trace leak.
   - Upstream dependency outage returns service unavailable pattern.
   - Server error must include correlation/request id in header/log.

6. **Edge Cases**
   - Empty values in optional and required fields.
   - Very large payload to enforce size limits.
   - Concurrent requests for race-condition-sensitive endpoints.

## Regression Critical Flows

- Auth login/refresh/logout lifecycle.
- Profile read/update.
- Admin user external account mapping.
- Report generation/download endpoints.
