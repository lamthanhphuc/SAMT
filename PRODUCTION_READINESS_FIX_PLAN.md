
# SAMT — Production Readiness Fix Plan

## Executive Summary

**Verdict:** **NOT READY**

This repository demonstrates strong intent (multi-service isolation, JWKS-based JWT validation at the gateway, internal header sanitization, Prometheus exposure in some services, and detailed documentation). However, there are multiple **CRITICAL** blockers that would allow request tampering/replay across service boundaries, expose operational and API documentation endpoints publicly, and make the “production” container/stack artifacts unreliable.

**Readiness score (0–100): 35**

Scoring basis (high-level):
- Security & identity: 10/30
- Service-to-service trust: 5/20
- Deployability (Docker/Compose): 5/20
- Operability (observability, runbooks): 10/20
- Delivery (CI/CD, release): 5/10

## Top 5 Blockers (must-fix before any production launch)

1) **CRITICAL — Service-to-service request integrity is not guaranteed**
	 - Gateway internal signing does **not** bind the request body and uses only timestamp skew checks (replayable within window).
	 - Evidence:
		 - `api-gateway/src/main/java/com/example/gateway/filter/SignedHeaderFilter.java` (uses `generateEmptyBodyHash()`)
		 - Representative verifier: `project-config-service/src/main/java/com/samt/projectconfig/security/GatewayInternalSignatureVerifier.java` (uses SHA-256 of empty body)

2) **CRITICAL — Public exposure of operational endpoints and Swagger/docs**
	 - API gateway permits `/actuator/**`, `/swagger-ui/**`, and `/v3/api-docs/**` with no auth.
	 - Multiple downstream services also `permitAll()` swagger and actuator endpoints.
	 - Evidence:
		 - `api-gateway/src/main/java/com/example/gateway/config/SecurityConfig.java`
		 - `api-gateway/src/main/java/com/example/gateway/filter/JwtAuthenticationFilter.java` (treats `/actuator/**`, swagger, and api-docs as public)
		 - Examples: `identity-service/.../SecurityConfig.java`, `sync-service/.../SecurityConfig.java`, `report-service/.../SecurityConfig.java`, etc.

3) **CRITICAL — Kafka JSON deserialization is configured to trust all packages**
	 - `spring.json.trusted.packages: "*"` (and equivalent Java config) enables unsafe polymorphic deserialization patterns.
	 - Evidence:
		 - `user-group-service/src/main/resources/application.yml`
		 - `user-group-service/src/main/java/.../KafkaConsumerConfig.java`

4) **CRITICAL — Containerization artifacts are inconsistent with runtime ports and may not build reliably**
	 - Multiple service Dockerfiles/compose examples assume `EXPOSE 8080`/healthchecks on `8080` while services are configured to run on `8081+`.
	 - Multi-stage builds exist but are currently not reproducible from a clean checkout:
		 - Builder stage uses `mvn` but the `eclipse-temurin:21-jdk-alpine` image does not include Maven.
		 - Dockerfiles attempt `COPY ../pom.xml` (outside the build context when building per-service).
		 - Runtime stage copies `target/*.jar` from the build context instead of `COPY --from=builder ...`, so the builder output is unused.
	 - Evidence:
		 - Example mismatch: `identity-service/Dockerfile` vs `identity-service/src/main/resources/application.yml` (`server.port: 8081`)
		 - `identity-service/Dockerfile` and `api-gateway/Dockerfile` (builder uses `mvn`, includes `COPY ../pom.xml`, runtime copies from `target/*.jar`)
		 - Similar patterns observed across other service Dockerfiles.

5) **CRITICAL — Production database migration strategy is not defined (DDL auto-update present)**
	 - `.env.example` sets `DDL_AUTO=update` and at least one service default config uses `ddl-auto: update`.
	 - Evidence:
		 - `.env.example`
		 - `user-group-service/src/main/resources/application.yml`

---

## Findings (with fixes + acceptance criteria)

### A) Security & Identity

#### A1 — CRITICAL: Gateway exposes actuator and API docs publicly

- **Description:** `api-gateway` allows unauthenticated access to `/actuator/**`, `/swagger-ui/**`, `/v3/api-docs/**`.
- **Why this is a problem:** Actuator endpoints can leak environment and operational metadata; swagger/api-docs increase attack surface and ease reconnaissance.
- **Impact:** External attackers can enumerate routes, schemas, and operational state; can accelerate exploit development.
- **Recommended fix:**
	- In the gateway, restrict actuator to **only** `health` (and ideally only from an internal network / mgmt plane).
	- Disable swagger and api-docs in production (`springdoc.*.enabled=false`) or protect behind admin auth.
	- Ensure gateway and per-service SecurityConfig align; avoid duplicate “public endpoint lists” drifting.
- **Acceptance criteria:**
	- External request to `/actuator/env`, `/actuator/metrics`, `/swagger-ui.html`, `/v3/api-docs` returns `401/403` in `prod`.
	- Only `/actuator/health` is accessible from the intended ops network.

#### A2 — HIGH: JWT validation missing issuer/audience/token_type enforcement

- **Description:** Gateway JWT filter verifies `sub`, `roles`, `jti`, `iat`, `exp`, but does not enforce **issuer**, **audience**, or `token_type=ACCESS`.
- **Why this is a problem:** Accepting tokens minted for other services/environments increases the chance of token confusion; missing `token_type` opens up refresh/access misuse.
- **Impact:** Elevated risk of accepting wrong tokens; weaker boundary controls.
- **Recommended fix:**
	- Add issuer/audience claims to identity-issued tokens and enforce with Spring validators.
	- Enforce `token_type == ACCESS` at the gateway.
- **Acceptance criteria:**
	- Tokens with wrong `iss` or missing/incorrect `aud` are rejected.
	- Tokens with `token_type != ACCESS` are rejected.

#### A3 — HIGH: Documentation and configuration drift around JWT algorithm/secret usage

- **Description:** Docs and examples frequently refer to `JWT_SECRET` / HS256 and “same secret across all services”, while the code implements RS256 + JWKS at the gateway.
- **Why this is a problem:** Operators will configure production incorrectly (wrong secrets/algorithms), leading to outages or security regressions.
- **Impact:** High likelihood of misconfiguration in deployment and incident response.
- **Recommended fix:**
	- Standardize: RS256 is authoritative for external JWTs.
	- Remove or clearly label any HS256 content as legacy.
	- Ensure all docs reference `JWT_PRIVATE_KEY_PEM`/`JWKS` and the gateway’s `JWT_JWKS_URI`.
- **Acceptance criteria:**
	- No production deployment instructions require `JWT_SECRET` for JWT signature validation.
	- Docs match actual headers (`X-User-Role` vs `X-User-Roles`) and actual algorithms.

---

### B) Service-to-Service Trust Boundary

#### B1 — CRITICAL: Internal signature does not bind request body

- **Description:** Gateway’s internal request signature uses `generateEmptyBodyHash()` and downstream verification uses an empty-body hash.
- **Why this is a problem:** An attacker who can alter traffic inside the cluster (or compromise a sidecar/service) can modify request bodies without invalidating the signature.
- **Impact:** Unauthorized state changes in downstream services (write endpoints are especially exposed).
- **Recommended fix:**
	- Compute a real body hash at the gateway and forward it (or sign the canonicalized body bytes).
	- In WebFlux, implement a request-body caching filter so the body can be hashed without breaking downstream consumption.
	- Verify on downstream by hashing the received body exactly as signed.
- **Acceptance criteria:**
	- Any mutation of body bytes results in signature verification failure.
	- Integration test demonstrates body tampering is detected.

#### B2 — CRITICAL: Anti-replay relies only on timestamp skew

- **Description:** Downstream verification checks only `abs(now - ts) <= maxSkewSeconds`.
- **Why this is a problem:** Requests can be replayed within the skew window (and potentially outside it if clocks drift or if window is increased).
- **Impact:** Duplicate actions (create/update/delete), payment-like effects, repeated jobs, etc.
- **Recommended fix:**
	- Include a nonce (`X-Internal-Nonce`) or reuse JWT `jti` as a per-request unique value.
	- Store seen nonces in Redis with TTL and reject repeats.
	- Narrow skew window and enforce monotonic timestamp per key-id where feasible.
- **Acceptance criteria:**
	- Replaying the exact same request within the TTL window returns `401/403`.
	- Clock-skew window is documented and monitored.

#### B3 — HIGH: Key rotation model is effectively single-key

- **Description:** Downstream verifiers typically enforce a single expected `X-Internal-Key-Id`.
- **Why this is a problem:** Rotations become a coordinated flag-day; any drift causes outages.
- **Impact:** Operational fragility during secret rotation.
- **Recommended fix:**
	- Support multiple active key IDs in downstream services.
	- Deploy “new key accepted” before “gateway switches signing key”.
- **Acceptance criteria:**
	- Rotation procedure documented and validated in staging.
	- Both old and new key IDs work during the overlap window.

#### B4 — HIGH: gRPC traffic is configured as plaintext (no TLS/mTLS)

- **Description:** gRPC clients are configured with `negotiation-type: plaintext` and use static internal addresses.
- **Why this is a problem:** Without TLS, traffic is readable and modifiable by any actor with network access (or a compromised pod/node). “Encrypted overlay network” (Swarm) does not replace application-layer identity (mTLS) or protect against insider/lateral movement.
- **Impact:** In-transit data exposure and integrity risk; harder to meet common compliance/security baselines.
- **Recommended fix:**
	- Decide the production posture:
		- Prefer mTLS for service-to-service gRPC (either native gRPC TLS or via a mesh like Istio/Linkerd).
		- Keep `plaintext` only for local dev profiles.
	- Add a staging validation that gRPC channels cannot connect without TLS in production.
- **Acceptance criteria:**
	- In `prod`, all gRPC clients negotiate TLS (or mesh-enforced mTLS) and `plaintext` is rejected.
	- Network policy/firewall rules prevent direct cross-namespace/cross-tier access except required paths.

---

### C) Messaging (Kafka)

#### C1 — CRITICAL: `spring.json.trusted.packages: "*"`

- **Description:** Consumer trusts all packages for JSON deserialization.
- **Why this is a problem:** Broad trust enables unsafe deserialization patterns and increases exploitability.
- **Impact:** Potential RCE-class deserialization issues depending on payload and classpath; at minimum, high-risk hardening gap.
- **Recommended fix:**
	- Set `trusted.packages` to the exact event package(s) (e.g., `com.example.common.events`).
	- Consider explicit message schemas (Avro/Protobuf/JSON schema) and stricter deserializers.
- **Acceptance criteria:**
	- Only the allowed event classes deserialize; unknown types are rejected.
	- Unit test verifies a disallowed class cannot be deserialized.

#### C2 — HIGH: No production-grade Kafka security posture demonstrated

- **Description:** Compose uses plaintext listeners; no evidence of TLS/SASL configuration for production.
- **Why this is a problem:** On real networks, plaintext Kafka enables eavesdropping and message injection.
- **Impact:** Data leakage and integrity compromise.
- **Recommended fix:**
	- Enable TLS (mTLS preferred) and SASL mechanisms appropriate to the environment.
	- Lock down Kafka network exposure and ACLs.
- **Acceptance criteria:**
	- Kafka brokers require authenticated clients.
	- No plaintext listeners exposed outside the cluster.

---

### D) Data & Persistence

#### D1 — CRITICAL: Schema migration strategy not production-safe (`ddl-auto: update`)

- **Description:** `.env.example` and service config show `DDL_AUTO=update` / `ddl-auto: update`.
- **Why this is a problem:** Hibernate auto-update is not deterministic or controllable for production migrations, and can cause destructive or locking operations.
- **Impact:** Unplanned schema changes, downtime, and data loss risk.
- **Recommended fix:**
	- Adopt Flyway or Liquibase for all services.
	- Set `ddl-auto=validate` (or `none`) in `prod`.
- **Acceptance criteria:**
	- `prod` profile uses `ddl-auto=validate|none`.
	- All schema changes are delivered as versioned migrations.

#### D2 — MEDIUM: SQL debug logging enabled in non-test config

- **Description:** `user-group-service` enables `show-sql: true` and verbose Hibernate logs.
- **Why this is a problem:** SQL logs can leak sensitive data and increase log volume/cost.
- **Impact:** Potential data exposure in logs and operational noise.
- **Recommended fix:**
	- Disable SQL logging in `prod`.
- **Acceptance criteria:**
	- `prod` profile emits no SQL statements.

---

### E) Build, CI/CD, and Release

#### E1 — HIGH: No CI pipeline found for build/test/security gates

- **Description:** No GitHub Actions workflow/pipeline is present to enforce build, tests, dependency scanning, or container scanning.
- **Why this is a problem:** Production readiness requires repeatable builds and automated checks.
- **Impact:** Regressions reach production; slow/unsafe release cadence.
- **Recommended fix:**
	- Add CI: build all modules, run unit tests, run static analysis, run dependency/CVE scans.
	- Add container build with SBOM output and scanning.
- **Acceptance criteria:**
	- PRs require CI green.
	- Releases produce versioned artifacts and images with provenance.

---

### F) Runtime / Deployment (Docker/Compose/K8s)

#### F1 — CRITICAL: Dockerfile port/healthcheck mismatch across services

- **Description:** Service ports in application config/docs are 8081+ but Dockerfiles/compose examples frequently assume 8080 for `EXPOSE` and healthchecks. Additionally, the current “multi-stage build” Dockerfiles are not buildable/reproducible as written.
- **Why this is a problem:** Healthchecks fail, orchestration restarts pods/containers, and load balancers route incorrectly.
- **Impact:** Immediate deployment instability.
- **Recommended fix:**
	- Align each service Dockerfile `EXPOSE` and healthcheck with the configured port.
	- Fix the build strategy (pick one and make it consistent):
		- Option 1 (recommended): Use builder stage properly and `COPY --from=builder` the built JAR.
		- Option 2: Remove builder stage entirely and require `./mvnw clean package` prior to `docker build` (and document it clearly).
	- Remove invalid `COPY ../pom.xml` patterns; if you need parent POM, build from repo root with an appropriate Dockerfile/build context.
- **Acceptance criteria:**
	- `docker build` works from a clean repo without pre-built jars.
	- Healthchecks pass for each service in docker and in orchestrator.
	- Dockerfiles do not rely on files outside the declared build context.

#### F2 — HIGH: “Secure” stack compose has secret wiring and TLS assumptions issues

- **Description:** `docker-compose.secure.yml` references `SPRING_DATA_REDIS_PASSWORD` secret for Redis, but secrets declare `REDIS_PASSWORD`.
- **Why this is a problem:** Stack deploy will fail or Redis will start without intended auth wiring.
- **Impact:** Failed production deploy or insecure runtime.
- **Recommended fix:**
	- Fix secret naming consistency (either define `SPRING_DATA_REDIS_PASSWORD` secret or reference `REDIS_PASSWORD`).
	- Validate Postgres TLS assumptions: `sslmode=require` will fail unless Postgres is configured for TLS.
- **Acceptance criteria:**
	- `docker stack deploy` succeeds in staging.
	- Redis starts with auth enforced and apps can connect.
	- Database connectivity works with the intended TLS mode.

#### F3 — HIGH: `docker-compose.yml` does not start application services despite README instructions

- **Description:** `README.md` instructs `docker-compose up -d` to “Start toàn bộ hệ thống”, but in `docker-compose.yml` the application services (gateway + microservices) are commented out.
- **Why this is a problem:** The default developer/operator path will not actually run the system, causing confusion and increasing the chance of ad-hoc local changes that later drift further from production.
- **Impact:** Broken onboarding/runbooks; unreliable “how to run” story; higher operational risk.
- **Recommended fix:**
	- Either uncomment and maintain the application services in `docker-compose.yml`, or split into explicit files:
		- `docker-compose.infra.yml` (postgres/redis/kafka)
		- `docker-compose.app.yml` (all services)
	- Update `README.md` to match the chosen approach.
- **Acceptance criteria:**
	- Following the README commands on a clean machine results in a running gateway + all required services.
	- The documented health checks and ports match the actual compose configuration.

---

### G) Observability & Operations

#### G1 — HIGH: Metrics/Prometheus exposure inconsistent across services

- **Description:** Some services expose `prometheus` and others only `metrics`, and gateway exposes `gateway` but not `prometheus`.
- **Why this is a problem:** Production monitoring requires consistent scrape targets and dashboards.
- **Impact:** Blind spots; delayed incident detection.
- **Recommended fix:**
	- Standardize Actuator + Micrometer Prometheus endpoint across all services (or adopt OpenTelemetry collector pattern).
	- Ensure metrics are not publicly exposed.
- **Acceptance criteria:**
	- Every service exposes consistent metrics on a protected management interface.
	- Dashboards and alerts cover latency, errors, saturation, and dependency health.

#### G2 — MEDIUM: No end-to-end distributed tracing implementation

- **Description:** Correlation IDs exist in at least one service, but no cross-service tracing (W3C traceparent/B3) is implemented system-wide.
- **Why this is a problem:** Incident triage is slow in microservices without traces.
- **Impact:** Higher MTTR.
- **Recommended fix:**
	- Adopt OpenTelemetry + trace propagation across gateway and all services.
- **Acceptance criteria:**
	- A single request can be traced from gateway → downstream service(s) in the tracing backend.

---

## Roadmap (prioritized)

### Phase 0 (0–7 days) — Blockers

1. Lock down gateway `/actuator/**`, swagger, and api-docs in `prod`.
2. Fix internal request signing:
	 - sign real body hash
	 - add nonce + replay protection store (Redis)
3. Fix Kafka `trusted.packages` and validate production Kafka security plan.
4. Make Dockerfiles build reliably from clean source; align ports/healthchecks.
5. Set `prod` DB strategy: Flyway/Liquibase and disable `ddl-auto:update`.

### Phase 1 (1–3 weeks) — Hardening

1. JWT hardening: issuer/audience/token_type enforcement and explicit clock skew.
2. Secret rotation: multi-key-id acceptance on downstream; documented rotation runbook.
3. Standardize management endpoints (health/metrics) and isolate them from public traffic.

### Phase 2 (1–2 months) — Operational excellence

1. CI/CD with security gates (SAST, dependency/CVE, container scan, SBOM).
2. Distributed tracing roll-out and SLOs/alerts.
3. Chaos/DR exercises: backup/restore drills and failure-mode testing.

---

## Go-Live Acceptance Checklists

### Security
- [ ] Gateway blocks public access to swagger and non-health actuator endpoints.
- [ ] JWT validation enforces `iss`, `aud`, and `token_type=ACCESS`.
- [ ] Internal gateway-to-service signing binds body + has replay protection.
- [ ] Kafka deserialization is constrained to known packages/classes.
- [ ] Secrets are injected via a proper secret store (Docker secrets/K8s secrets/Vault) with rotation tested.

### Data
- [ ] All services use migrations; `ddl-auto` is not `update` in production.
- [ ] Backup/restore procedures tested; RPO/RTO documented.

### Deployability
- [ ] All Dockerfiles build from clean source without prebuilt artifacts.
- [ ] Container ports + readiness/liveness probes match actual service ports.
- [ ] “Secure” compose/stack configuration validated in staging.

### Observability
- [ ] Consistent metrics endpoint across services; dashboards + alerts exist.
- [ ] Correlation IDs or tracing propagate gateway → downstream.
- [ ] Centralized logging and log retention policies defined.

### Delivery
- [ ] CI pipeline runs build + tests + scans on every PR.
- [ ] Release produces versioned, signed images/artifacts.

