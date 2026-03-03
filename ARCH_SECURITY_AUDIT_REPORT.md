# SAMT – Architecture + Security Review Report (Source-Based)

Scope: Current workspace source + deployment artifacts (Docker/Compose/K8s) as provided. Findings are based on static review of repository contents.

## Update (Zero-Trust migration)

This repository has since been migrated away from the legacy **HMAC-signed internal headers** model.

- API Gateway validates the **external JWT** (RS256/JWKS).
- API Gateway mints a **short-lived internal JWT** (RS256) and forwards it as `Authorization: Bearer <internal-jwt>`.
- Downstream services validate the internal JWT using the gateway’s JWKS/public key(s).
- A dedicated `mtls` profile exists to enforce **service-to-service mTLS** for HTTP and gRPC.

Sections below that refer to `X-Internal-*`, `X-User-*`, `SignedHeaderFilter`, `SignedHeaderUtil`, and `GatewayInternalSignatureVerifier` are now **legacy** and kept only for audit traceability.

## Tech Stack & Architecture Snapshot
- Runtime: Java 21
- Frameworks: Spring Boot (multi-module), Spring Security; Spring Cloud Gateway (WebFlux) in `api-gateway`
- Services (observed): `api-gateway`, `identity-service`, `project-config-service`, `user-group-service`, `sync-service`, `analysis-service`, `notification-service`, `report-service`, plus shared `common-events`
- Auth model (current): External JWT (RS256/JWKS) validated at gateway; gateway issues short-lived internal JWT (RS256) to downstream services; optional service-to-service mTLS via `mtls` profile
- Messaging: Kafka (Spring Kafka)
- Data: PostgreSQL; Redis
- Inter-service: gRPC (net.devh starters)
- Ops: Actuator/Prometheus; Springdoc OpenAPI/Swagger in multiple services

## Severity Scale
- **CRITICAL**: Remote compromise/data exfiltration likely OR production deployment broken by default.
- **HIGH**: Exploitable security weakness, major integrity issues, or serious production instability risk.
- **MEDIUM**: Material hardening gap, misconfiguration risk, or reliability/perf issue.
- **LOW**: Code smell, maintainability issue, or minor hardening.

## Executive Summary
Overall, the system shows a solid *conceptual* perimeter pattern (JWT at gateway + internal service trust), but the current implementation has multiple **CRITICAL** weaknesses:
- (Legacy) The previous HMAC internal signing model had body/identity binding and replay gaps; it has been removed in favor of internal JWT + mTLS.
- Internal endpoints return **decrypted secrets/tokens** guarded only by a static header key.
- Swagger/Actuator are **publicly permitted** in `identity-service` and enabled by default in multiple services; most `application-prod.yml` files do not harden these.
- Kafka consumer deserialization trusts **all packages** (`"*"`).
- Dockerfiles/Compose secure deployment contain **build-breaking** / secret-mismatch problems.

## Summary Table (Top Findings)
| ID | Severity | Category | Component | Title |
|---|---|---|---|---|
| SAMT-SEC-001 | CRITICAL | Trust Boundary / Auth | Gateway + All services | Internal signature does not cover body (empty body hash) |
| SAMT-SEC-002 | CRITICAL | AuthZ / Integrity | Gateway + All services | Signature payload does not bind `X-User-Id` / `X-User-Role` |
| SAMT-SEC-003 | HIGH | Replay / Auth | All services | Timestamp-only anti-replay; no nonce/jti cache |
| SAMT-SEC-004 | CRITICAL | Secrets Exposure | project-config-service | Internal API returns decrypted tokens; guarded by static header key |
| SAMT-SEC-005 | CRITICAL | Supply Chain / RCE | user-group-service | Kafka `trusted.packages="*"` enables gadget deserialization risk |
| SAMT-SEC-006 | HIGH | Data Integrity | Multiple services | `ddl-auto` defaults to `update` (and `.env.example` sets it) |
| SAMT-SEC-007 | HIGH | Transport Security | sync-service + project-config-service + user-group-service | gRPC configured as plaintext |
| SAMT-DEP-001 | CRITICAL | Build/Release | All Dockerfiles | Dockerfiles run `mvn` in Temurin Alpine without ensuring Maven |
| SAMT-DEP-002 | CRITICAL | Build/Release | All Dockerfiles | `COPY ../pom.xml` likely outside build context (build fails) |
| SAMT-DEP-003 | HIGH | Deployment Correctness | docker-compose.yml | Application services are commented out (docs drift) |
| SAMT-DEP-004 | CRITICAL | Secure Deploy | docker-compose.secure.yml | Redis secret name mismatch breaks stack deploy |
| SAMT-SEC-008 | CRITICAL | Attack Surface | identity-service | Swagger + api-docs are `permitAll()` in SecurityConfig |
| SAMT-SEC-009 | HIGH | Observability Exposure | project-config-service | Actuator/Prometheus enabled; prod profile doesn’t restrict |
| SAMT-ARCH-001 | HIGH | Architecture | user-group-service | Microservice-to-microservice Maven dependency coupling |

---

# Detailed Findings

## SAMT-SEC-001 – Internal signature does not cover request body (empty body hash)
- **Severity**: CRITICAL
- **Category**: Trust Boundary / Request Integrity
- **Components**: `api-gateway` → all downstream services
- **Status**: LEGACY / DECOMMISSIONED
- **Description**: This finding applied to the removed HMAC internal signature flow.
- **Why this matters**: Any in-cluster attacker (or misrouted proxy) able to modify request bodies after signing can change semantics while keeping a valid signature. This breaks the core “gateway as policy enforcement point” assumption.
- **Evidence**: Legacy classes referenced by this finding have been removed.
- **Repro (conceptual)**:
  1) Send a signed request with benign JSON body.
  2) Alter the body in-transit (same method/path) while preserving internal headers.
  3) Downstream verifier still accepts signature.
- **Fix**:
  - Compute a canonical hash of the *actual* body bytes at the gateway (and verify it downstream).
  - Alternatively, stop using body signing and move to mTLS with service identity + authorization at destination.
- **Residual risk if unfixed**: In-cluster integrity break → privilege abuse and data corruption.

## SAMT-SEC-002 – Signature payload does not bind user identity/role headers
- **Severity**: CRITICAL
- **Category**: AuthZ / Integrity
- **Components**: Gateway trust model
- **Status**: LEGACY / DECOMMISSIONED
- **Description**: This finding applied to the removed HMAC internal signature flow.
- **Why this matters**: If an attacker can inject/alter internal identity headers (via a compromised in-cluster workload, SSRF, misconfigured proxy, or bypass of sanitization), the signature does not detect it.
- **Evidence**: Legacy classes referenced by this finding have been removed.
- **Repro (conceptual)**:
  - Replay a valid signed request but replace `X-User-Role` to a higher privilege (if any trust boundary leak exists).
- **Fix**:
  - Bind `X-User-Id`, `X-User-Role` (and any authz-relevant headers) into the signed payload.
  - Preferably replace the pattern with mTLS + JWT “token exchange” for internal calls.
- **Residual risk if unfixed**: Authorization spoofing on internal hops.

## SAMT-SEC-003 – Timestamp-only anti-replay protection
- **Severity**: HIGH
- **Category**: Replay
- **Components**: Downstream signature verification
- **Status**: RESOLVED (new approach)
- **Description**: The new internal JWT flow requires `jti` and validates `iat/exp` with clock skew bounded to 30s max. If strict replay detection is required, add a `jti` denylist/cache.
- **Why this matters**: Captured signed requests can be replayed within the skew window. If endpoints are non-idempotent, this is exploitable.
- **Evidence**: Downstream internal JWT validators now enforce bounded skew + required `jti`.
- **Fix**:
  - Include a nonce (`X-Internal-Nonce`) in signature and store it in Redis with TTL to ensure single use.
  - Or use short-lived internal JWT with `jti` and enforce replay detection.
- **Residual risk if unfixed**: Duplicate operations, data corruption, abuse of state-changing endpoints.

## SAMT-SEC-004 – Internal API returns decrypted tokens; guarded only by static header key
- **Severity**: CRITICAL
- **Category**: Secrets Exposure
- **Components**: `project-config-service`
- **Status**: RESOLVED (internal JWT)
- **Description (legacy)**: Previously, `/internal/project-configs/{id}/tokens` returned decrypted Jira/GitHub tokens guarded only by static `X-Service-*` headers.
- **Current state**: `/internal/**` endpoints require `Authorization: Bearer <internal-jwt>` (RS256) validated via the gateway internal JWKS, with strict issuer/service/timestamp + required `kid`/`jti` validators.
- **Evidence**:
  - Endpoint path + behavior: [project-config-service/src/main/java/com/samt/projectconfig/controller/InternalConfigController.java](project-config-service/src/main/java/com/samt/projectconfig/controller/InternalConfigController.java#L1)
  - /internal/** requires authentication: [project-config-service/src/main/java/com/samt/projectconfig/security/SecurityConfig.java](project-config-service/src/main/java/com/samt/projectconfig/security/SecurityConfig.java#L36-L44)
- **Repro (conceptual)**:
  - Call the internal endpoint from any network-reachable pod with correct headers.
- **Fix**:
  - Enforce mTLS (recommended) for service-to-service transport.
  - Keep internal JWT TTL short; consider `jti` replay detection for non-idempotent internal operations.
  - Strongly consider not returning decrypted tokens at all; instead perform actions server-side.
- **Residual risk if unfixed**: Total compromise of 3rd-party integrations.

## SAMT-SEC-005 – Kafka JSON deserialization trusts all packages (`"*"`)
- **Severity**: CRITICAL
- **Category**: Deserialization / RCE
- **Components**: `user-group-service`
- **Description**: Kafka consumer config sets trusted packages to `"*"` both in code and config.
- **Why this matters**: Trusting all packages is a known high-risk setting for JSON-to-object deserialization chains. If an attacker can publish to Kafka (or compromise Kafka ACLs), this can become remote code execution or data tampering.
- **Evidence**:
  - Java config: [user-group-service/src/main/java/com/example/user_groupservice/config/KafkaConsumerConfig.java](user-group-service/src/main/java/com/example/user_groupservice/config/KafkaConsumerConfig.java#L55)
  - YAML config: [user-group-service/src/main/resources/application.yml](user-group-service/src/main/resources/application.yml#L49)
- **Fix**:
  - Set trusted packages to the exact package(s) that contain event DTOs (e.g. `com.example.common.events`).
  - Enforce schema/contract via schema registry or explicit DTO mapping.
- **Residual risk if unfixed**: RCE class of risk if Kafka is reachable/compromised.

## SAMT-SEC-006 – Hibernate `ddl-auto` defaults to `update` (production data integrity risk)
- **Severity**: HIGH
- **Category**: Data Integrity / Migration Safety
- **Components**: identity-service, analysis-service, notification-service, report-service, user-group-service
- **Description**: Several services default to `ddl-auto: update` (or `${DDL_AUTO:update}`), and `.env.example` explicitly sets `DDL_AUTO=update`.
- **Why this matters**: `update` can silently change schemas, create drift, and break reproducibility. It is unsafe for production and complicates rollback.
- **Evidence**:
  - Identity default: [identity-service/src/main/resources/application.yml](identity-service/src/main/resources/application.yml#L49)
  - Other services default: [analysis-service/src/main/resources/application.yml](analysis-service/src/main/resources/application.yml#L45), [notification-service/src/main/resources/application.yml](notification-service/src/main/resources/application.yml#L45), [report-service/src/main/resources/application.yml](report-service/src/main/resources/application.yml#L45)
  - User-group hardcodes update: [user-group-service/src/main/resources/application.yml](user-group-service/src/main/resources/application.yml#L26)
  - .env.example sets update: [.env.example](.env.example#L82)
- **Fix**:
  - Set production to `validate` and require Flyway/Liquibase migrations.
  - Make `DDL_AUTO` default `validate` and only allow `update` in a dev-only profile.
- **Residual risk if unfixed**: Production schema drift, partial deploy failures, data loss.

## SAMT-SEC-007 – gRPC configured as plaintext
- **Severity**: HIGH
- **Category**: Transport Security
- **Components**: user-group-service, sync-service, project-config-service
- **Description**: gRPC clients use plaintext negotiation.
- **Why this matters**: Plaintext gRPC is susceptible to sniffing/modification on any compromised network segment. It also prevents strong service identity.
- **Evidence**:
  - user-group-service: [user-group-service/src/main/resources/application.yml](user-group-service/src/main/resources/application.yml#L72)
  - sync-service: [sync-service/src/main/resources/application.yml](sync-service/src/main/resources/application.yml#L94)
  - project-config-service: [project-config-service/src/main/resources/application.yml](project-config-service/src/main/resources/application.yml#L94)
- **Fix**:
  - Enable TLS for gRPC channels and enforce server identity verification.
  - Prefer mTLS between services.
- **Residual risk if unfixed**: Credential/session leakage on internal network.

## SAMT-SEC-008 – identity-service exposes Swagger and api-docs publicly
- **Severity**: CRITICAL
- **Category**: Attack Surface
- **Components**: identity-service
- **Description**: identity-service SecurityConfig explicitly permits Swagger and OpenAPI endpoints without authentication.
- **Why this matters**: Identity service is a high-value target. Public docs increase attacker efficiency and may expose internal endpoints/contracts.
- **Evidence**:
  - permitAll() for Swagger/api-docs: [identity-service/src/main/java/com/example/identityservice/config/SecurityConfig.java](identity-service/src/main/java/com/example/identityservice/config/SecurityConfig.java#L75-L82)
  - Production profile does not disable springdoc/actuator: [identity-service/src/main/resources/application-prod.yml](identity-service/src/main/resources/application-prod.yml#L1-L19)
- **Fix**:
  - Disable springdoc in prod or protect it behind admin auth + network restrictions.
  - Restrict actuator exposure in prod.
- **Residual risk if unfixed**: Increased likelihood of auth bypass discovery and exploitation.

## SAMT-SEC-009 – project-config-service exposes Prometheus/metrics; prod profile doesn’t restrict
- **Severity**: HIGH
- **Category**: Observability Exposure
- **Components**: project-config-service
- **Description**: Actuator exposes `prometheus` and `metrics`; health details are `always`. `application-prod.yml` does not override these.
- **Why this matters**: Metrics/health can leak sensitive operational data and aid attackers (service names, environment, timings). `show-details: always` is generally unsafe for production.
- **Evidence**:
  - Actuator exposure includes prometheus: [project-config-service/src/main/resources/application.yml](project-config-service/src/main/resources/application.yml#L212)
  - Health show-details always: [project-config-service/src/main/resources/application.yml](project-config-service/src/main/resources/application.yml#L215)
  - No prod override: [project-config-service/src/main/resources/application-prod.yml](project-config-service/src/main/resources/application-prod.yml#L1-L23)
- **Fix**:
  - In prod: expose only `health` and set `show-details: never`.
  - Put Prometheus behind internal network policy / auth.
- **Residual risk if unfixed**: Information disclosure.

## SAMT-SEC-010 – Role truncation: gateway forwards only the first role
- **Severity**: MEDIUM
- **Category**: Authorization Correctness
- **Components**: api-gateway
- **Status**: RESOLVED (internal JWT)
- **Description (legacy)**: This applied to the removed signed-header model where only a single role was forwarded.
- **Current state**: Gateway includes the full `roles` list in the internal JWT and downstream services derive authorities from that claim.
- **Evidence**:
  - Internal JWT roles claim: [api-gateway/src/main/java/com/example/gateway/security/InternalJwtIssuer.java](api-gateway/src/main/java/com/example/gateway/security/InternalJwtIssuer.java)
- **Residual risk**: Keep role semantics consistent across services (e.g., `ROLE_` prefixing) to avoid authorization drift.

---

# Deployment & Operability Findings

## SAMT-DEP-001 – Dockerfiles execute `mvn` without ensuring Maven exists
- **Severity**: CRITICAL
- **Category**: Build/Release Reliability
- **Components**: all services
- **Description**: Dockerfiles use `eclipse-temurin:21-jdk-alpine` and then run `mvn ...`.
- **Why this matters**: Builds will fail in CI/CD or on clean machines unless Maven is installed in-image.
- **Evidence** (example service):
  - Builder base image: [api-gateway/Dockerfile](api-gateway/Dockerfile#L6)
  - Maven commands: [api-gateway/Dockerfile](api-gateway/Dockerfile#L10-L12)
- **Fix**:
  - Use `maven:3.9-eclipse-temurin-21` for builder stage, or `apk add --no-cache maven`.
- **Residual risk if unfixed**: Non-reproducible builds, broken release pipeline.

## SAMT-DEP-002 – Dockerfiles attempt `COPY ../pom.xml` (likely outside build context)
- **Severity**: CRITICAL
- **Category**: Build/Release Reliability
- **Components**: all services
- **Description**: Dockerfiles copy the parent `pom.xml` using a path outside the service directory.
- **Why this matters**: If Docker build context is set to the service directory (typical), Docker forbids copying files outside context → build fails.
- **Evidence** (example service):
  - COPY ../pom.xml ../pom.xml: [api-gateway/Dockerfile](api-gateway/Dockerfile#L9)
- **Fix**:
  - Set build context to repository root and `dockerfile: service/Dockerfile`, or restructure Dockerfiles to not require parent files.
- **Residual risk if unfixed**: Build breakage and inconsistent images.

## SAMT-DEP-003 – docker-compose.yml does not start application services (commented out)
- **Severity**: HIGH
- **Category**: Deployment Correctness / Docs Drift
- **Components**: Local deployment
- **Description**: `docker-compose.yml` includes infra services, but application services are commented out under “APPLICATION SERVICES”.
- **Why this matters**: README/operator expectations can be incorrect; automated bring-up fails silently.
- **Evidence**:
  - Application services section begins: [docker-compose.yml](docker-compose.yml#L240)
- **Fix**:
  - Either uncomment + validate app services, or provide a separate `docker-compose.apps.yml` and update docs.
- **Residual risk if unfixed**: Onboarding friction and broken local/prod parity.

## SAMT-DEP-004 – docker-compose.secure.yml Redis secret mismatch breaks deployment
- **Severity**: CRITICAL
- **Category**: Secure Deployment
- **Components**: docker-compose.secure.yml
- **Description**: Secrets declare `REDIS_PASSWORD`, but the `redis` service expects a secret named `SPRING_DATA_REDIS_PASSWORD`.
- **Why this matters**: `docker stack deploy` will fail or Redis will start without the intended password injection.
- **Evidence**:
  - Declared secret: [docker-compose.secure.yml](docker-compose.secure.yml#L23)
  - Redis service reads different secret: [docker-compose.secure.yml](docker-compose.secure.yml#L158-L169)
- **Fix**:
  - Align on one secret name and consumption path.
  - Add a validation step in Makefile to ensure all referenced secrets exist.
- **Residual risk if unfixed**: Broken secure deployment and possible insecure fallback.

---

# Architecture Findings

## SAMT-ARCH-001 – Microservice-to-microservice Maven dependency coupling
- **Severity**: HIGH
- **Category**: Architecture / Service Boundaries
- **Components**: user-group-service, project-config-service
- **Description**: `user-group-service` has a compile-scope dependency on `project-config-service`.
- **Why this matters**: This defeats independent versioning and deployability; it tends to create hidden runtime coupling and circular release trains.
- **Evidence**:
  - Dependency: [user-group-service/pom.xml](user-group-service/pom.xml#L203-L210)
- **Fix**:
  - Extract shared contracts into a dedicated library module (e.g., `common-contracts`), or use API/schema-based integration.
- **Residual risk if unfixed**: Fragile deployments, cascading changes across services.

---

# Recommended Fix Roadmap

## Phase 0 (0–72 hours) – Stop the bleeding
1) Lock down Swagger/OpenAPI and actuator in **identity-service** and **project-config-service** for prod.
2) Fix `docker-compose.secure.yml` Redis secret mismatch.
3) Remove Kafka `trusted.packages="*"` and restrict to known DTO packages.

## Phase 1 (1–2 weeks) – Restore trust boundaries
1) Replace header/HMAC trust with internal JWT token exchange (gateway-minted RS256) and enforce via mTLS.
2) Add `jti` replay protection where required (non-idempotent internal operations).
3) Remove any static per-service shared keys; use internal JWT + mTLS for service-to-service calls.

## Phase 2 (2–6 weeks) – Production hardening
1) Move all services to `ddl-auto=validate` in prod and enforce Flyway migrations.
2) Enable TLS/mTLS for gRPC.
3) Make Docker builds reproducible (builder image + correct build contexts).
4) Reduce default actuator exposure and health detail leakage.

## Overall Health Score (0–100)
**42/100**

Rationale: Multiple CRITICAL security boundary flaws + several deployment-breaking issues, but the codebase shows intent toward production hardening (profiles, secret validators in gateway, structured configs) that can be built on.
