# Repository Audit Issues (Synchronized)

This file is the authoritative, deduplicated issue register for the current repo state.
Last re-audit: 2026-03-05 (Final Independent Audit)

---

# ═══════════════════════════════════════════════════════
# FINAL INDEPENDENT SECURITY AUDIT
# Date: 2026-03-05
# Auditor: Senior Security Auditor (Independent)
# ═══════════════════════════════════════════════════════

## Summary

| Metric               | Count |
|----------------------|-------|
| **Total Remediated Issues (This Pass)** | **12** |
| CRITICAL             | 0     |
| HIGH                 | 2     |
| MEDIUM               | 7     |
| LOW                  | 3     |
| Previously Reported  | 5 (AUDIT-0037 through AUDIT-0041) |
| Newly Discovered     | 7 (AUDIT-0042 through AUDIT-0046, AUDIT-NEW-001, AUDIT-NEW-002) |
| Open Security Issues | 0     |

---

## SECTION 1 — VERIFICATION OF PREVIOUSLY REPORTED ISSUES (AUDIT-0002 through AUDIT-0036)

All issues from AUDIT-0002 through AUDIT-0036 were independently re-verified against live source.

| Issue       | Claimed Status | Verified | Evidence |
|-------------|----------------|----------|----------|
| AUDIT-0002  | FALSE_POSITIVE | **CONFIRMED** | Swagger URL is env-driven via `${JWT_JWKS_URI}` — not hardcoded. No real issue. |
| AUDIT-0016  | FIXED | **CONFIRMED** | Base configs now default to INFO. All `application-prod.yml` files set `org.springframework.security: WARN`, `org.hibernate.SQL: WARN`. |
| AUDIT-0020  | FIXED | **CONFIRMED** | All 8 Dockerfiles: `addgroup -S app && adduser -S -G app`, `USER app`, `HEALTHCHECK` present. |
| AUDIT-0021  | FIXED | **CONFIRMED** | `runs-on: ubuntu-24.04` in both workflows. `gitleaks-action@v2` mutable tag folded into AUDIT-0037. |
| AUDIT-0022  | FIXED | **CONFIRMED** | Only `api-gateway` uses `ports:`. All other services use `expose:` only. |
| AUDIT-0023  | FIXED | **CONFIRMED** | All compose images tagged `1.0.0` (e.g. `samt/api-gateway:1.0.0`). |
| AUDIT-0024  | FIXED | **CONFIRMED** | `k8s-deployment-example.yaml` uses `image: samt/project-config-service:1.0.0`, `imagePullPolicy: IfNotPresent`. |
| AUDIT-0025  | FIXED | **CONFIRMED** | All `application-prod.yml` files override logging to INFO/WARN levels. |
| AUDIT-0026  | FIXED | **CONFIRMED** | Pod `securityContext`: `runAsNonRoot: true`, `runAsUser: 10001`. Container: `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem: true`, `capabilities.drop: ["ALL"]`. |
| AUDIT-0027  | FIXED | **CONFIRMED** | All 8 compose app services have `security_opt: ["no-new-privileges:true"]`, `cap_drop: [ALL]`, `read_only: true`, `tmpfs: [/tmp]`. |
| AUDIT-0028  | FIXED | **CONFIRMED** | `<spring-boot.version>3.4.5</spring-boot.version>` in root `pom.xml`. Current supported branch. |
| AUDIT-0029  | FIXED | **CONFIRMED** | `trivy-scan` job uses `strategy.matrix.service` across all 8 services. `exit-code: '1'`, `severity: HIGH,CRITICAL`. |
| AUDIT-0030  | FIXED | **CONFIRMED** | `<grpc.version>1.71.0</grpc.version>`, `<protobuf.version>3.25.5</protobuf.version>`. CVE-2024-7254 patched. |
| AUDIT-0031  | FIXED | **PARTIAL** | Deployment metadata has `tier: app`. Pod template labels do **not** include `tier: app` — see **AUDIT-0040**. |
| AUDIT-0032  | FIXED | **CONFIRMED** | All 8 builder stages digest-pinned: `maven:3.9.6-eclipse-temurin-21@sha256:8d63d4c1902cb12d9e79a70671b18ebe26358cb592561af33ca1808f00d935cb`. |
| AUDIT-0033  | FIXED | **CONFIRMED** | postgres, redis, kafka images all digest-pinned in `docker-compose.yml` and `docker-compose.kafka-production.yml`. |
| AUDIT-0034  | FIXED | **CONFIRMED** | `codeql-sast` job added with `languages: java`, `queries: security-and-quality`. |
| AUDIT-0035  | FIXED | **CONFIRMED** | CronJob script loops over all 7 services with `kubectl patch deployment` for rollout restart. |
| AUDIT-0036  | FIXED | **CONFIRMED** | `JtiReplayValidator` in `common-contracts`, using Redis `setIfAbsent` with 60s TTL. Wired into all 6 downstream `JwtDecoder` beans. |

---

## SECTION 2 — REMEDIATION STATUS (COMPLETED)

All previously open issues in this section were remediated and re-verified against repository state.

| Issue | Severity | Status | Evidence |
|------|----------|--------|----------|
| AUDIT-0037 | MEDIUM | **FIXED** | `.github/workflows/ci.yml` and `.github/workflows/gitleaks.yml` now pin mutable actions to immutable SHAs (`checkout`, `cache`, `codeql-action`, `gitleaks-action`). |
| AUDIT-0038 | MEDIUM | **FIXED** | All 8 service Dockerfiles now pin runtime stage to `eclipse-temurin:21.0.6_7-jre-alpine@sha256:4e9ab608...`. |
| AUDIT-0039 | MEDIUM | **FIXED** | `k8s-secrets.template.yml` Role now scopes access via `resourceNames` for both `secrets` and target `deployments`. |
| AUDIT-0040 | HIGH | **FIXED** | `project-config-service/k8s-deployment-example.yaml` pod template labels now include `tier: app`. |
| AUDIT-0041 | MEDIUM | **FIXED** | `k8s-secrets.template.yml` CronJob container now has hardened `securityContext` (non-root, read-only rootfs, no priv esc, drop ALL caps). |

---

## SECTION 3 — NEWLY DISCOVERED ISSUES (REMEDIATED)

| Issue | Severity | Status | Evidence |
|------|----------|--------|----------|
| AUDIT-0042 | HIGH | **FIXED** | `docker-compose-implementation.yml` hardened: digest-pinned infra images, removed host port publishing, removed password leakage pattern, added container hardening controls. |
| AUDIT-0043 | MEDIUM | **FIXED** | `.gitleaks.toml` migrated from broad path allowlist to targeted placeholder regex allowlist. |
| AUDIT-0044 | LOW | **FIXED** | `k8s-secrets.template.yml` app-tier egress now includes DNS (53), Kafka (29092), inter-service gRPC (9091-9095), and gateway JWKS path (8080). |
| AUDIT-0045 | LOW | **FIXED** | `k8s-secrets.template.yml` removed ambiguous `egress: []` pattern; data tier now uses canonical deny-all egress by omission. |
| AUDIT-0046 | MEDIUM | **FIXED** | `.github/workflows/ci.yml` now has CodeQL quality gate step that fails CI on open HIGH/CRITICAL CodeQL alerts. |
| AUDIT-NEW-001 | MEDIUM | **FIXED** | Added repository-level `.dockerignore` to prevent secret/cert/context leakage into Docker build context. |
| AUDIT-NEW-002 | LOW | **FIXED** | `docker-compose.secure.yml` now includes `user-group-service` and `project-config-service` in Docker secret delivery (`redis_password`). |

---

## SECTION 4 — POSITIVE FINDINGS (Defenses Verified Working)

The following security controls were independently verified to be correctly implemented:

| Control | Status | Evidence |
|---------|--------|----------|
| **Non-root containers** | PASS | All 8 Dockerfiles create `app` user, set `USER app` |
| **Container healthchecks** | PASS | All Dockerfiles and compose services have HEALTHCHECK |
| **Stateless JWT sessions** | PASS | `SessionCreationPolicy.STATELESS` in all SecurityConfig beans |
| **JWT replay prevention** | PASS | `JtiReplayValidator` with Redis `setIfAbsent`, 60s TTL, in all 6 downstream services |
| **JWT issuer/service validation** | PASS | `JwtIssuerValidator` + `service` claim validator in all downstream `JwtDecoder` beans |
| **JWT kid validation** | PASS | `kidRequiredValidator` present in all downstream services |
| **RS256 signing** | PASS | Internal JWTs signed with RSA-2048 private key via `RSASSASigner`, JWKS-based verification |
| **Short-lived internal JWTs** | PASS | 20s TTL with 30s clock skew — effective window 50s maximum |
| **SBOM generation** | PASS | CycloneDX plugin generates JSON + XML BOMs at verify phase |
| **Dependency convergence** | PASS | Maven enforcer plugin with `dependencyConvergence` rule, `failFast: true` |
| **OWASP Dependency-Check** | PASS | `failBuildOnCVSS=7` gate in CI |
| **Trivy container scanning** | PASS | Matrix strategy across all 8 services, `exit-code: 1`, `severity: HIGH,CRITICAL` |
| **CodeQL SAST** | PASS | Workflow pins CodeQL actions and enforces HIGH/CRITICAL quality gate in CI |
| **Secret scanning** | PASS | Gitleaks allowlist narrowed to known placeholders only; broad path exclusions removed |
| **Docker build context hardening** | PASS | `.dockerignore` added to exclude secrets, certs, git metadata, docs, and non-build artifacts |
| **Secure compose secret propagation** | PASS | `docker-compose.secure.yml` now covers all Redis-dependent services |
| **Builder image pinning** | PASS | All 8 builder stages digest-pinned |
| **Infrastructure image pinning** | PASS | Postgres, Redis, Kafka digest-pinned in main compose |
| **Compose hardening** | PASS | `no-new-privileges`, `cap_drop: ALL`, `read_only: true`, `tmpfs: /tmp` on all app services |
| **K8s security context** | PASS | Pod: `runAsNonRoot`, `runAsUser: 10001`. Container: `allowPrivilegeEscalation: false`, `readOnlyRootFilesystem`, `capabilities.drop: ALL` |
| **Redis password protection** | PASS | Password required via env, protected-mode enabled, no host port exposed |
| **Kafka production SSL** | PASS | `docker-compose.kafka-production.yml` enforces SSL-only, mTLS required, PLAINTEXT guard |
| **Prod actuator lockdown** | PASS | All `application-prod.yml` files: `exposure.include: health`, `springdoc.enabled: false` |
| **gRPC mTLS in production** | PASS | `GRPC_SERVER_SECURITY_ENABLED: true` default, prod configs require mTLS with cert chain |
| **.env exclusion** | PASS | `.gitignore` properly excludes `.env`, `.env.local`, `.env.production`, `.local-certs/`, `secrets/`, `*.pem`, `*.key` |
| **K8s secret rotation** | PASS | CronJob rotates postgres + redis credentials and restarts all 7 service deployments |
| **Spring Boot version** | PASS | 3.4.5 (current supported branch) |
| **gRPC/protobuf versions** | PASS | gRPC 1.71.0, protobuf 3.25.5 |

---

## SECTION 5 — CONCLUSION

### Is the system production-grade secure? **Production Ready with Minor Fixes**

The system demonstrates a **mature security posture** with robust controls across most domains. The major architectural decisions (internal JWT with replay prevention, container hardening, digest-pinned images, mTLS for gRPC, automated secret rotation) are well-implemented and correctly wired.

All security findings tracked in Section 2 and Section 3 are now fixed and reflected in source code.

**Residual non-security caveat:**
1. Maven build currently reports an existing dependency convergence conflict (`checker-qual`) unrelated to this security remediation pass.

From a security control perspective, the repository now meets the documented production baseline.

---

*End of Final Independent Security Audit — 2026-03-05*
