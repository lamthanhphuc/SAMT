# Zero-Trust Migration Package (HMAC Removal → Internal JWT + mTLS)

This document is the **authoritative** migration guide for SAMT’s security model.

## Target Architecture (production)

1) Client sends **external JWT** to API Gateway: `Authorization: Bearer <external>`
2) Gateway validates external JWT (issuer/audience/JWKS, etc.)
3) Gateway mints **short-lived internal JWT (RS256)** and forwards downstream as:
   - `Authorization: Bearer <internal>`
4) Downstream services validate internal JWT using the gateway JWKS endpoint:
   - `/.well-known/internal-jwks.json`
5) **Service-to-service traffic is mTLS-only** (HTTP + gRPC) via `mtls` profile.

**Explicitly forbidden (decommissioned model):**
- Any `X-User-*` “identity forwarding” headers
- Any `X-Internal-*` “signature” headers
- Any `INTERNAL_SIGNING_SECRET` / HMAC-based internal signing

## What’s In This Repo Now (high-level)

- Gateway issues internal JWT (RS256) and publishes JWKS.
- Downstreams validate internal JWT via `spring-boot-starter-oauth2-resource-server`.
- `mtls` Spring profile exists across services via `application-mtls.yml`.

## Configuration Contract

### Gateway (issuer)

Internal JWT signing + JWKS:
- `gateway.internal-jwt.issuer` (default `samt-gateway`)
- `gateway.internal-jwt.service-name` (default `api-gateway`)
- `gateway.internal-jwt.ttl-seconds` (default `20`, keep 15–30s)
- `gateway.internal-jwt.key-id` (published as JWS header `kid`)
- `gateway.internal-jwt.private-key-pem-path` (PKCS#8 RSA private key)
- `gateway.internal-jwt.additional-public-jwks-json-path` (optional rotation overlap)

mTLS (gateway → downstream):
- `gateway.mtls.enabled=true` (in `mtls` profile)
- `spring.ssl.bundle.pem.gateway-client.*` (client cert/key + CA)

### Downstreams (validators)

Each service validates internal JWT via JWKS:
- `spring.security.oauth2.resourceserver.jwt.jwk-set-uri=${GATEWAY_INTERNAL_JWKS_URI}`

Internal JWT policy:
- `security.internal-jwt.issuer=${GATEWAY_INTERNAL_JWT_ISSUER}`
- `security.internal-jwt.expected-service=${GATEWAY_INTERNAL_JWT_EXPECTED_SERVICE:api-gateway}`
- `security.internal-jwt.clock-skew-seconds=${INTERNAL_JWT_CLOCK_SKEW_SECONDS:30}` (max 30)

## Migration Checklist (step-by-step)

### Phase 0 — Preparation

- Inventory any consumers still expecting `X-User-*` or `X-Internal-*` headers.
- Decide gateway internal JWT issuer string (recommended stable value like `samt-gateway`).
- Decide key rotation approach:
  - single active key + publish extra public keys for overlap
  - or full JWKS hosting solution (future)

### Phase 1 — Remove HMAC Internal Signing (mandatory)

- Remove all HMAC verifier filters/utilities, and any `INTERNAL_SIGNING_SECRET` wiring.
- Ensure downstream services **do not** accept requests authenticated solely by internal headers.

Status in this repo:
- The old internal HMAC verifier classes were removed from downstream services.
- `INTERNAL_SIGNING_SECRET` was removed from secure compose and service configs.

### Phase 2 — External JWT Verified at Gateway

- Gateway must **not strip inbound** `Authorization` before verifying external JWT.
- Gateway should only replace `Authorization` when proxying downstream.

Status in this repo:
- Gateway filter ordering was fixed so external auth works end-to-end.

### Phase 3 — Issue Internal JWT and Validate Downstream

- Gateway mints RS256 internal JWT with:
  - header: `kid`
  - claims: `iss`, `sub`, `roles`, `service`, `iat`, `exp`, `jti`
- Downstream validates:
  - issuer (`iss`)
  - required `kid` header
  - required `jti` claim
  - timestamps (`iat/exp`) with bounded skew (≤ 30s)
  - expected `service` claim

Status in this repo:
- Implemented in gateway and in downstream security configs.

### Phase 4 — Enable mTLS Between Services (HTTP + gRPC)

- Roll out a CA and issue:
  - a **client certificate** for gateway
  - a **server certificate** for each downstream service
  - (recommended) distinct client certs for each service that makes outbound calls
- Enable profile `mtls` and mount cert material into containers/pods.

Status in this repo:
- Each service has `application-mtls.yml` enabling HTTP server mTLS.
- gRPC server/client TLS/mTLS is configured where gRPC is used.

## Certificate Generation (example)

These are example commands; adapt paths and subject names. You can run them using OpenSSL.

1) Create a local CA:

```bash
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/CN=samt-internal-ca" \
  -out ca.crt
```

2) Issue a certificate for a service (repeat per service):

```bash
SERVICE=identity-service
openssl genrsa -out ${SERVICE}.key 2048
openssl req -new -key ${SERVICE}.key -subj "/CN=${SERVICE}" -out ${SERVICE}.csr
openssl x509 -req -in ${SERVICE}.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out ${SERVICE}.crt -days 365 -sha256
```

3) Issue a client certificate for the gateway:

```bash
openssl genrsa -out api-gateway-client.key 2048
openssl req -new -key api-gateway-client.key -subj "/CN=api-gateway" -out api-gateway-client.csr
openssl x509 -req -in api-gateway-client.csr -CA ca.crt -CAkey ca.key -CAcreateserial \
  -out api-gateway-client.crt -days 365 -sha256
```

## Deployment Wiring (what you must do)

### Spring profiles

- For secure environments, run each service with `SPRING_PROFILES_ACTIVE=prod,mtls,docker` (or your equivalent).
- For local experiments, you can run `docker,mtls` without `prod`.

### Required env vars / mounts

For each downstream service (server-side mTLS):
- `MTLS_SERVER_CERT_PATH` → path to `*.crt`
- `MTLS_SERVER_KEY_PATH` → path to `*.key`
- `MTLS_CA_CERT_PATH` → path to `ca.crt`

For gateway (client-side mTLS to downstream):
- `MTLS_CLIENT_CERT_PATH` → path to `api-gateway-client.crt`
- `MTLS_CLIENT_KEY_PATH` → path to `api-gateway-client.key`
- `MTLS_CA_CERT_PATH` → path to `ca.crt`

For internal JWT verification (downstreams):
- `GATEWAY_INTERNAL_JWKS_URI` → e.g. `https://api-gateway:8080/.well-known/internal-jwks.json` (when mTLS)
- `GATEWAY_INTERNAL_JWT_ISSUER` → must match the gateway issuer

## Key Rotation (RS256)

Recommended safe rotation pattern:

1) Generate new RSA keypair, choose a new `kid`.
2) Configure gateway to sign with new key (new private key + `key-id`).
3) Publish **both** old and new public keys during overlap:
   - Use `gateway.internal-jwt.additional-public-jwks-json-path` to publish the retiring public key.
4) Wait for max internal token TTL + clock skew + caches (recommend 5–10 minutes).
5) Remove the retiring public key from the published JWKS.

## Hardening Requirements (non-negotiable)

- Internal token TTL: 15–30 seconds.
- Clock skew: ≤ 30 seconds.
- Require `kid` and `jti`.
- JWKS endpoint must be reachable **only** over internal network + mTLS.
- Do not log tokens or private keys.
- In `prod`, disable Swagger/OpenAPI and restrict actuator exposure.

## File-Level Change Summary (where to look)

Gateway (auth + JWKS + mTLS):
- [../../api-gateway/src/main/java/com/example/gateway/security/InternalJwtIssuer.java](../../api-gateway/src/main/java/com/example/gateway/security/InternalJwtIssuer.java)
- [../../api-gateway/src/main/java/com/example/gateway/security/InternalJwtWebFilter.java](../../api-gateway/src/main/java/com/example/gateway/security/InternalJwtWebFilter.java)
- [../../api-gateway/src/main/java/com/example/gateway/security/InternalJwksController.java](../../api-gateway/src/main/java/com/example/gateway/security/InternalJwksController.java)
- [../../api-gateway/src/main/java/com/example/gateway/security/InternalJwkSetProvider.java](../../api-gateway/src/main/java/com/example/gateway/security/InternalJwkSetProvider.java)
- [../../api-gateway/src/main/java/com/example/gateway/security/InternalJwtProperties.java](../../api-gateway/src/main/java/com/example/gateway/security/InternalJwtProperties.java)
- [../../api-gateway/src/main/java/com/example/gateway/mtls/GatewayMtlsHttpClientConfig.java](../../api-gateway/src/main/java/com/example/gateway/mtls/GatewayMtlsHttpClientConfig.java)
- [../../api-gateway/src/main/resources/application-mtls.yml](../../api-gateway/src/main/resources/application-mtls.yml)

Downstreams (resource server validation):
- [../../user-group-service/src/main/java/com/example/user_groupservice/security/SecurityConfig.java](../../user-group-service/src/main/java/com/example/user_groupservice/security/SecurityConfig.java)
- [../../project-config-service/src/main/java/com/samt/projectconfig/security/SecurityConfig.java](../../project-config-service/src/main/java/com/samt/projectconfig/security/SecurityConfig.java)
- [../../sync-service/src/main/java/com/example/syncservice/security/SecurityConfig.java](../../sync-service/src/main/java/com/example/syncservice/security/SecurityConfig.java)
- [../../report-service/src/main/java/com/example/reportservice/config/SecurityConfig.java](../../report-service/src/main/java/com/example/reportservice/config/SecurityConfig.java)

mTLS profile configs:
- [../../identity-service/src/main/resources/application-mtls.yml](../../identity-service/src/main/resources/application-mtls.yml)
- [../../user-group-service/src/main/resources/application-mtls.yml](../../user-group-service/src/main/resources/application-mtls.yml)
- [../../project-config-service/src/main/resources/application-mtls.yml](../../project-config-service/src/main/resources/application-mtls.yml)
- [../../sync-service/src/main/resources/application-mtls.yml](../../sync-service/src/main/resources/application-mtls.yml)
- [../../analysis-service/src/main/resources/application-mtls.yml](../../analysis-service/src/main/resources/application-mtls.yml)
- [../../notification-service/src/main/resources/application-mtls.yml](../../notification-service/src/main/resources/application-mtls.yml)
- [../../report-service/src/main/resources/application-mtls.yml](../../report-service/src/main/resources/application-mtls.yml)

Docs updated for the new model:
- [../contracts/JWT_CLAIMS.md](../contracts/JWT_CLAIMS.md)
- [../audits/security-audit-2026-03-10.md](../audits/security-audit-2026-03-10.md)

## Risks

- **mTLS rollout risk**: if any service is switched to `mtls` while clients still speak plaintext, calls will fail.
- **JWKS reachability risk**: if downstreams can’t reach `GATEWAY_INTERNAL_JWKS_URI`, authentication fails.
- **Clock skew risk**: nodes with skew > 30s will see elevated 401s.
- **Key rotation risk**: dropping old public key too early causes intermittent 401s.
- **Observability leak risk**: logging `Authorization` headers will leak internal JWTs.

## Rollback Plan

Rollback should keep the system safe while restoring availability:

1) **mTLS rollback (transport)**:
   - Remove `mtls` from `SPRING_PROFILES_ACTIVE` for all services.
   - Switch gateway upstream URIs back to `http://...`.
2) **Internal JWT rollback (auth)**:
   - Keep internal JWT validation enabled on services.
   - If gateway key material is broken, restore the previous keypair and `kid` and publish both keys during overlap.
3) **Do not re-enable HMAC headers**.
   - HMAC/header model is explicitly retired; rollback must not re-introduce it.
