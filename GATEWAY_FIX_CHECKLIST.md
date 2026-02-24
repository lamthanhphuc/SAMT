---------------------------------
# API Gateway – Fix Checklist
---------------------------------

## 1. CRITICAL ISSUES (Block Production)

### 1.1 Routing and Filter Order Not Strictly Enforced
- Title: Routing and Filter Order Not Strictly Enforced
- Affected Component/File: Route configuration, Filter classes, application.yml
- Why it violates the documentation: Documentation requires strict, deterministic filter execution order and per-endpoint rate limiting, which is not enforced.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Enforce filter execution order in code for both WebFilters and GatewayFilters.
  - Implement per-endpoint rate limiting for all documented endpoints, especially /api/identity/**, /login, /register.
  - Ensure route definitions and filter order are strictly deterministic and cannot be altered by config/code changes without explicit review.
- Verification Steps:
  - Review code and config to confirm filter order is enforced and matches documentation.
  - Test rate limiting on all required endpoints.
  - Attempt to change route/filter order and confirm startup fails or logs a warning.
- Status: TODO

### 1.2 CORS Whitelist Enforcement Ambiguity
- Title: CORS Whitelist Enforcement Ambiguity
- Affected Component/File: CORS configuration, application.yml, environment variable handling
- Why it violates the documentation: Documentation requires strict CORS whitelist enforcement; current implementation relies on an environment variable with no fallback or config enforcement.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Enforce CORS whitelist in code and application.yml.
  - If environment variable is missing, fail startup with a clear error.
  - Document the source of truth for CORS configuration.
- Verification Steps:
  - Remove environment variable and confirm startup fails.
  - Confirm CORS whitelist is enforced at runtime and matches documentation.
- Status: TODO

### 1.3 Request Size Limit Not Enforced
- Title: Request Size Limit Not Enforced
- Affected Component/File: application.yml, request handling code
- Why it violates the documentation: 10MB request size limit is required by documentation but not enforced in code or config.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Add explicit 10MB request size limit in application.yml and code.
  - Reject requests exceeding this limit with documented error response.
- Verification Steps:
  - Send requests >10MB and confirm they are rejected with correct error schema.
- Status: TODO

### 1.4 Error Handling Violates JSON Schema & Leaks Exceptions
- Title: Error Handling Violates JSON Schema & Leaks Exceptions
- Affected Component/File: GlobalErrorWebExceptionHandler, error response logic
- Why it violates the documentation: Error responses do not match documented JSON schema and leak exception messages.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Update error handler to match documented JSON error schema (including error object and timestamp).
  - Remove exception messages from responses.
- Verification Steps:
  - Trigger errors and confirm response matches schema and does not leak exception details.
- Status: TODO

### 1.5 Signed Header Injection Not Deterministic or Strict
- Title: Signed Header Injection Not Deterministic or Strict
- Affected Component/File: Header injection logic, secret management
- Why it violates the documentation: Deterministic payload format and UTF-8 encoding are not enforced; no startup failure if secret is missing/weak.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Enforce deterministic payload format and UTF-8 encoding for signed headers.
  - Fail startup if internal secret is missing or weak.
- Verification Steps:
  - Remove/alter secret and confirm startup fails.
  - Inspect headers for deterministic format and encoding.
- Status: TODO

### 1.6 JWT Validation Incomplete
- Title: JWT Validation Incomplete
- Affected Component/File: JWT validation logic
- Why it violates the documentation: No explicit validation of token_type claim; 401 error response does not match documented schema.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Validate token_type claim in JWT.
  - Ensure 401 error response matches documented error object structure and includes timestamp.
- Verification Steps:
  - Send JWTs with missing/invalid token_type and confirm rejection with correct error schema.
- Status: TODO

### 1.7 Default Spring Error Handler Not Disabled
- Title: Default Spring Error Handler Not Disabled
- Affected Component/File: Spring Boot configuration
- Why it violates the documentation: Default error handler must be disabled to prevent non-compliant error responses.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Explicitly disable default Spring error handler in configuration.
- Verification Steps:
  - Trigger errors and confirm only custom handler is used.
- Status: TODO

### 1.8 No Explicit Removal of Client-Supplied X-Internal-* Headers
- Title: No Explicit Removal of Client-Supplied X-Internal-* Headers
- Affected Component/File: Header processing logic
- Why it violates the documentation: Client-supplied internal headers must be removed before injection.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Remove all client-supplied X-Internal-* headers before injecting internal headers.
- Verification Steps:
  - Send requests with spoofed X-Internal-* headers and confirm they are not forwarded.
- Status: TODO

### 1.9 No Explicit Check for Stacktrace Leak
- Title: No Explicit Check for Stacktrace Leak
- Affected Component/File: Error handling, logging configuration
- Why it violates the documentation: Stacktraces must not be leaked in logs or responses.
- Risk Level: CRITICAL
- Exact Fix Required (technical, actionable):
  - Audit error handling and logging to ensure stacktraces are never exposed to clients or logs.
- Verification Steps:
  - Trigger errors and inspect logs/responses for stacktraces.
- Status: TODO

## 2. SECURITY ISSUES

### 2.1 Potential CORS Misconfiguration
- Title: Potential CORS Misconfiguration
- Affected Component/File: CORS configuration, environment variable handling
- Why it violates the documentation: CORS config may be missing or misconfigured if environment variable is absent.
- Risk Level: HIGH
- Exact Fix Required (technical, actionable):
  - Enforce CORS config in code and application.yml; fail startup if missing.
- Verification Steps:
  - Remove/misconfigure env variable and confirm startup fails.
- Status: TODO

### 2.2 No Explicit Removal of Spoofed Internal Headers
- Title: No Explicit Removal of Spoofed Internal Headers
- Affected Component/File: Header processing logic
- Why it violates the documentation: Spoofed internal headers from clients must be removed.
- Risk Level: HIGH
- Exact Fix Required (technical, actionable):
  - Remove all client-supplied X-Internal-* headers before processing.
- Verification Steps:
  - Send spoofed headers and confirm they are not present downstream.
- Status: TODO

### 2.3 No Explicit Check for Hardcoded Secrets
- Title: No Explicit Check for Hardcoded Secrets
- Affected Component/File: Secret management, codebase
- Why it violates the documentation: Hardcoded secrets are forbidden.
- Risk Level: HIGH
- Exact Fix Required (technical, actionable):
  - Audit codebase for hardcoded secrets and remove any found.
- Verification Steps:
  - Search code for secrets and confirm none are present.
- Status: TODO

### 2.4 Error Handler Leaks Exception Messages
- Title: Error Handler Leaks Exception Messages
- Affected Component/File: GlobalErrorWebExceptionHandler
- Why it violates the documentation: Exception messages must not be exposed in responses.
- Risk Level: HIGH
- Exact Fix Required (technical, actionable):
  - Remove exception messages from error responses.
- Verification Steps:
  - Trigger errors and confirm no exception messages are returned.
- Status: TODO

### 2.5 No Explicit Check for Stacktrace Leak
- Title: No Explicit Check for Stacktrace Leak
- Affected Component/File: Error handling, logging configuration
- Why it violates the documentation: Stacktraces must not be leaked in logs or responses.
- Risk Level: HIGH
- Exact Fix Required (technical, actionable):
  - Audit error handling and logging for stacktrace leaks.
- Verification Steps:
  - Trigger errors and inspect logs/responses for stacktraces.
- Status: TODO

## 3. NON-CRITICAL SPEC DEVIATIONS

### 3.1 Minor Differences in Error Response Formatting
- Title: Minor Differences in Error Response Formatting
- Affected Component/File: Error response logic
- Why it violates the documentation: Error response format does not exactly match documentation.
- Risk Level: MEDIUM
- Exact Fix Required (technical, actionable):
  - Update error response formatting to match documentation exactly.
- Verification Steps:
  - Compare error responses to documentation and confirm match.
- Status: TODO

### 3.2 Route URIs and Filter Configurations Inconsistent
- Title: Route URIs and Filter Configurations Inconsistent
- Affected Component/File: Route configuration, filter configuration
- Why it violates the documentation: Some URIs, port numbers, and fallback URIs do not match documentation.
- Risk Level: MEDIUM
- Exact Fix Required (technical, actionable):
  - Update all route URIs, port numbers, and filter configs to match documentation.
- Verification Steps:
  - Compare config to documentation and confirm match.
- Status: TODO

## 4. DETERMINISM RISKS
- Root cause: GatewayFilters (rate limiter, circuit breaker, retry, timeout) rely on route definition order, which is not strictly deterministic or enforced by code.
- Why dangerous: Any code or config change could alter execution order, violating strict requirements and causing unpredictable behavior in production.
- Required enforcement strategy: Enforce filter and route order in code; add startup checks to fail if order is ambiguous or altered; document order explicitly in code and config.
- Status: TODO

## 5. FINAL VALIDATION CHECKLIST
- [ ] Routing matches API_CONTRACT.md
- [ ] Filter order strictly deterministic
- [ ] CORS strict whitelist only
- [ ] Login/register rate limited
- [ ] JWT fully validated
- [ ] Signed headers deterministic & secure
- [ ] No stacktrace leak
- [ ] JSON error schema matches spec
- [ ] Request size limit enforced
- [ ] No wildcard CORS
- [ ] No hardcoded secrets
- [ ] Default Spring error handler disabled

---------------------------------