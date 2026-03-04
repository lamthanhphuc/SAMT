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
| SAMT-SEC-005 | CRITICAL | Supply Chain / RCE | user-group-service | Kafka `trusted.packages` configured with a wildcard enables gadget deserialization risk |
| SAMT-SEC-006 | HIGH | Data Integrity | Multiple services | `ddl-auto` defaults to `update` (and `.env.example` sets it) |
| SAMT-SEC-007 | HIGH | Transport Security | sync-service + project-config-service + user-group-service | gRPC configured as plaintext |
| SAMT-DEP-001 | CRITICAL | Build/Release | All Dockerfiles | Dockerfiles run `mvn` in Temurin Alpine without ensuring Maven |
| SAMT-DEP-002 | CRITICAL | Build/Release | All Dockerfiles | `COPY ../pom.xml` likely outside build context (build fails) |
| SAMT-DEP-003 | HIGH | Deployment Correctness | docker-compose.yml | Application services are commented out (docs drift) |
| SAMT-DEP-004 | CRITICAL | Secure Deploy | docker-compose.secure.yml | Redis secret canonicalization via Swarm secret (RESOLVED) |
| SAMT-SEC-008 | CRITICAL | Attack Surface | identity-service | Swagger/OpenAPI no longer public (RESOLVED) |
| SAMT-SEC-009 | HIGH | Observability Exposure | project-config-service | Actuator hardened in prod (RESOLVED) |
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
- **Description**: Services default `ddl-auto` to `${DDL_AUTO:validate}` and production enforces `validate`.
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
- **Status**: RESOLVED (TLS by default; prod enforces mTLS + fail-closed; dev-only plaintext allowed)
- **Description**: gRPC client defaults are TLS with CA verification (server identity verification). In `prod`, services require mTLS and fail startup if plaintext negotiation (or disabling gRPC server security) is configured via any property source (env vars, command line args, config maps, etc.). Plaintext is only permitted in `dev` profile.
- **Why this matters**: TLS prevents sniffing/modification; mTLS prevents service impersonation and enables strong service identity.
- **Evidence (current state)**:
  - TLS defaults (no plaintext) for gRPC clients:
    - user-group-service: [user-group-service/src/main/resources/application.yml](user-group-service/src/main/resources/application.yml#L57-L75)
    - sync-service: [sync-service/src/main/resources/application.yml](sync-service/src/main/resources/application.yml#L97-L115)
    - project-config-service: [project-config-service/src/main/resources/application.yml](project-config-service/src/main/resources/application.yml#L89-L104)
  - Prod mTLS required (`clientAuth: REQUIRE` + client cert material):
    - user-group-service: [user-group-service/src/main/resources/application-prod.yml](user-group-service/src/main/resources/application-prod.yml#L28-L42)
    - sync-service: [sync-service/src/main/resources/application-prod.yml](sync-service/src/main/resources/application-prod.yml#L32-L46)
    - project-config-service: [project-config-service/src/main/resources/application-prod.yml](project-config-service/src/main/resources/application-prod.yml#L51-L65)
    - (Upstream server) identity-service mTLS: [identity-service/src/main/resources/application.yml](identity-service/src/main/resources/application.yml#L81-L100)
  - Dev-only plaintext (explicitly scoped to `dev` profile):
    - user-group-service: [user-group-service/src/main/resources/application-dev.yml](user-group-service/src/main/resources/application-dev.yml#L6-L9)
    - sync-service: [sync-service/src/main/resources/application-dev.yml](sync-service/src/main/resources/application-dev.yml#L6-L9)
    - project-config-service: [project-config-service/src/main/resources/application-dev.yml](project-config-service/src/main/resources/application-dev.yml#L6-L9)
  - Prod fail-closed enforcement (startup fails if plaintext / TLS disabled is configured in `prod`):
    - user-group-service enforcement: [user-group-service/src/main/java/com/example/user_groupservice/config/ProdHardeningEnvironmentPostProcessor.java](user-group-service/src/main/java/com/example/user_groupservice/config/ProdHardeningEnvironmentPostProcessor.java#L33-L110)
    - sync-service enforcement: [sync-service/src/main/java/com/example/syncservice/config/ProdHardeningEnvironmentPostProcessor.java](sync-service/src/main/java/com/example/syncservice/config/ProdHardeningEnvironmentPostProcessor.java#L33-L122)
    - project-config-service enforcement: [project-config-service/src/main/java/com/samt/projectconfig/config/ProdHardeningEnvironmentPostProcessor.java](project-config-service/src/main/java/com/samt/projectconfig/config/ProdHardeningEnvironmentPostProcessor.java#L21-L142)
    - Post-processor registrations:
      - user-group-service: [user-group-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor](user-group-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor#L1)
      - sync-service: [sync-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor](sync-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor#L1)
      - project-config-service: [project-config-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor](project-config-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor#L1)
  - Placeholder cert material present (must be replaced for real deployments):
    - Certificates are not bundled; mount at `/certs` at runtime.
- **Fix (implemented)**:
  - Default to TLS with CA verification (`negotiation-type: TLS` + `trustCertCollection`) for service-to-service gRPC.
  - Enforce mTLS in `prod` (`clientAuth: REQUIRE` + client/server cert/key material).
  - Fail closed in `prod` via `EnvironmentPostProcessor`: reject plaintext negotiation (even if introduced via env/args) and prevent disabling server security.
  - Allow plaintext only under `dev` profile.
- **Residual risk**: Certificate issuance/rotation and DNS/SAN alignment must be managed to avoid outages.

## SAMT-SEC-008 – identity-service exposes Swagger and api-docs publicly
- **Severity**: CRITICAL
- **Category**: Attack Surface
- **Components**: identity-service
- **Status**: RESOLVED (Swagger disabled in prod; not publicly permitted)
- **Description**: identity-service no longer permits Swagger/OpenAPI endpoints without authentication, and production disables springdoc entirely.
- **Why this matters**: Identity service is a high-value target. Public docs increase attacker efficiency and may expose internal endpoints/contracts.
- **Evidence**:
  - SecurityConfig no longer permits Swagger/OpenAPI; all non-public routes require auth: [identity-service/src/main/java/com/example/identityservice/config/SecurityConfig.java](identity-service/src/main/java/com/example/identityservice/config/SecurityConfig.java#L68-L79)
  - Production disables springdoc + restricts actuator exposure: [identity-service/src/main/resources/application-prod.yml](identity-service/src/main/resources/application-prod.yml#L23-L36)
  - Prod hardening enforced (cannot be re-enabled via env/args): [identity-service/src/main/java/com/example/identityservice/config/ProdHardeningEnvironmentPostProcessor.java](identity-service/src/main/java/com/example/identityservice/config/ProdHardeningEnvironmentPostProcessor.java#L18-L35)
  - Post-processor registration: [identity-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor](identity-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor#L1)
- **Fix (implemented)**:
  - Removed Swagger/OpenAPI matchers from `permitAll()`.
  - Disabled springdoc in `prod`.
  - Exposed only `health,info` actuator endpoints in `prod` and set health details to `never`.
  - Enforced the above in `prod` via `EnvironmentPostProcessor` to prevent env/CLI overrides.
- **Residual risk**: Ensure production deployments activate the `prod` profile (e.g., `SPRING_PROFILES_ACTIVE=prod`).

## SAMT-SEC-009 – project-config-service actuator exposure not hardened for prod
- **Severity**: HIGH
- **Category**: Observability Exposure
- **Components**: project-config-service
- **Status**: RESOLVED (prod hardened + fail-fast guard)
- **Description**: Default configuration enables actuator metrics/prometheus features and includes non-health endpoints. In production, actuator must be restricted to minimize information disclosure and attack surface.
- **Why this matters**: Metrics and detailed health can leak sensitive operational data (service names, environment, timings, dependency states) and increase attacker efficiency. Production should follow least exposure.
- **Evidence**:
  - Default actuator web exposure includes non-health endpoint(s): [project-config-service/src/main/resources/application.yml](project-config-service/src/main/resources/application.yml#L215-L223)
  - Production restricts exposure to health-only and disables metrics/prometheus: [project-config-service/src/main/resources/application-prod.yml](project-config-service/src/main/resources/application-prod.yml#L37-L63)
  - Prod fail-fast guard blocks unsafe exposure via env vars / CLI: [project-config-service/src/main/java/com/samt/projectconfig/security/ProdHardeningEnvironmentPostProcessor.java](project-config-service/src/main/java/com/samt/projectconfig/security/ProdHardeningEnvironmentPostProcessor.java#L23-L67)
  - Post-processor registration: [project-config-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor](project-config-service/src/main/resources/META-INF/spring/org.springframework.boot.env.EnvironmentPostProcessor#L1-L2)
- **Fix (implemented)**:
  - In `prod`: expose ONLY `health` and set `management.endpoint.health.show-details=never`.
  - Disable Prometheus export and disable actuator `metrics`/`prometheus` endpoints in `prod`.
  - Enforce fail-closed startup in `prod` if `management.endpoints.web.exposure.include` contains `metrics`, `prometheus`, or `*`, or if health details are not `never`.
- **Residual risk**: Ensure production deployments actually activate the `prod` profile (e.g., `SPRING_PROFILES_ACTIVE=prod`) and that the actuator port is not exposed publicly by ingress/firewall rules.

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
- **Status**: RESOLVED (standardized multi-stage builds)
- **Description (previous state)**: Dockerfiles used `eclipse-temurin:21-jdk-alpine` and then ran `mvn ...` without ensuring Maven exists.
- **Why this matters**: Builds fail in CI/CD / clean environments if Maven is not present in the image.
- **Current state**:
  - Builder stage uses `maven:3.9-eclipse-temurin-21` and runs the Maven build.
  - Runtime stage uses `eclipse-temurin:21-jre-alpine` with no Maven present.
  - Builds are intended to run from repository root context (`docker build -f <service>/Dockerfile .`).
- **Evidence** (example service):
  - Builder base image: [api-gateway/Dockerfile](api-gateway/Dockerfile#L6)
  - Maven commands (builder stage): [api-gateway/Dockerfile](api-gateway/Dockerfile#L20), [api-gateway/Dockerfile](api-gateway/Dockerfile#L32)
  - Runtime base image: [api-gateway/Dockerfile](api-gateway/Dockerfile#L34)
  - Runtime entrypoint (no `mvn`): [api-gateway/Dockerfile](api-gateway/Dockerfile#L37)
- **Fix (implemented)**:
  - Refactored all service Dockerfiles to production multi-stage builds with a Maven builder image and a JRE-only runtime image.
- **Residual risk**: Low (primary remaining risk is external dependency availability: Maven Central / artifact repos during build).

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

## SAMT-DEP-004 – docker-compose.secure.yml Redis secret drift (RESOLVED)
- **Severity**: CRITICAL
- **Category**: Secure Deployment
- **Components**: docker-compose.secure.yml
- **Status**: RESOLVED
- **Description**: Redis secret declaration and consumption are now aligned to a single canonical Swarm secret `redis_password`, consumed via file-based env vars.
- **Why this matters**: Prevents `docker stack deploy` failures due to secret name drift and prevents Redis from starting without a non-empty password.
- **Evidence (current state)**:
  - Canonical secret declaration: [docker-compose.secure.yml](docker-compose.secure.yml#L20-L26)
  - Redis fail-fast + requirepass from secret: [docker-compose.secure.yml](docker-compose.secure.yml#L166-L201)
  - Downstream services consume via `SPRING_DATA_REDIS_PASSWORD_FILE`: [docker-compose.secure.yml](docker-compose.secure.yml#L228-L239)
- **Fix (implemented)**:
  - Use one canonical secret name `redis_password` declared once as `external: true`.
  - Attach `redis_password` to Redis + all dependent services.
  - Configure consumers with `SPRING_DATA_REDIS_PASSWORD_FILE=/run/secrets/redis_password`.
  - Fail fast on missing/empty secret (`test -s /run/secrets/redis_password`) and enforce Redis auth via `--requirepass`.
- **Residual risk**: Deployment still requires the external Swarm secret `redis_password` to be created before `docker stack deploy`.

---

# Architecture Findings

## SAMT-ARCH-001 – Microservice-to-microservice Maven dependency coupling
- **Severity**: HIGH
- **Category**: Architecture / Service Boundaries
- **Components**: user-group-service, project-config-service
- **Status**: RESOLVED (extracted shared contracts)
- **Description (previous state)**: `user-group-service` had a compile-scope Maven dependency on `project-config-service`.
- **Current state**: No service module depends on another service module; shared DTOs/events/protos live in `common-contracts`.
- **Why this matters**: This defeats independent versioning and deployability; it tends to create hidden runtime coupling and circular release trains.
- **Evidence (current state)**:
  - New contracts module in reactor build order: [pom.xml](pom.xml#L40-L52)
  - `user-group-service` depends on `common-contracts` (and does not reference `project-config-service`): [user-group-service/pom.xml](user-group-service/pom.xml#L95-L120)
  - `project-config-service` depends on `common-contracts`: [project-config-service/pom.xml](project-config-service/pom.xml#L85-L112)
  - `common-contracts` module definition: [common-contracts/pom.xml](common-contracts/pom.xml#L1)
- **Fix (implemented)**:
  - Created `common-contracts` as a pure model module (no Spring Boot, no service logic).
  - Moved shared event models and shared gRPC proto definitions into `common-contracts`.
  - Removed `project-config-service` dependency from `user-group-service`.
  - Updated `project-config-service` to use gRPC stubs generated from `common-contracts`.
- **Validation (performed)**:
  - `grep -R "<artifactId>project-config-service</artifactId>" .` returns matches only inside the `project-config-service` module.
  - `mvnw clean package -DskipTests` succeeds with no reactor circular dependencies.
- **Residual risk**:
  - Contract evolution must be managed explicitly (semantic versioning and backward compatibility for DTO/proto changes) to avoid breaking dependent services.

---

# Recommended Fix Roadmap

## Phase 0 (0–72 hours) – Stop the bleeding
1) Lock down Swagger/OpenAPI and actuator in **identity-service** and **project-config-service** for prod.
2) Fix `docker-compose.secure.yml` Redis secret mismatch.
3) Remove Kafka `trusted.packages` wildcard configuration and restrict to known DTO packages.

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
