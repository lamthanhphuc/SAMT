# Security Test Checklist

## Authentication / Authorization

- [ ] Protected endpoints reject missing token (`401`/`403`).
- [ ] Invalid/expired token is rejected.
- [ ] Role-based endpoints enforce least privilege.
- [ ] JWT claims validation blocks malformed issuer/audience/token id.

## Input Security

- [ ] SQL injection payloads do not bypass auth or break API.
- [ ] XSS payloads are rejected or safely encoded/sanitized.
- [ ] Command/header injection attempts are rejected.
- [ ] Input size limits prevent abuse.

## Rate Limiting & Abuse

- [ ] Burst requests trigger rate-limit or defensive response.
- [ ] Brute-force login attempts are throttled/blocked.

## Secrets & Sensitive Data

- [ ] Logs never contain passwords/tokens/connection strings.
- [ ] Error payloads do not leak stack traces to external clients.
- [ ] Secret files are excluded from repository/containers.

## Transport & Configuration

- [ ] HTTPS/TLS configuration validated in non-local env.
- [ ] CORS policy follows least privilege.
- [ ] Security headers are present (CSP/X-Content-Type-Options/etc).
