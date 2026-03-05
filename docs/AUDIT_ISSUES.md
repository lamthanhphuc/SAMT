# Repository Audit Issues (Synchronized)

This file is the authoritative, deduplicated issue register for the current repo state.
Last re-audit: 2026-03-05

---

## AUDIT-0002
Severity: MEDIUM
File: api-gateway/src/main/resources/application.yml
Description: Prior report of swagger URL port mismatch was revalidated.
Why: Incorrect report would create unnecessary remediation churn.
Fix: Keep env-driven Swagger URL defaults aligned with compose ports.
Status: FALSE_POSITIVE

## AUDIT-0016
Severity: HIGH
File: project-config-service/src/main/resources/application.yml
Description: Project Config Service still defaults to verbose logging in base config, including `org.springframework.security: DEBUG` and service logger default DEBUG (`com.samt.projectconfig: ${LOG_LEVEL:DEBUG}`). These defaults are inherited unless explicitly overridden.
Why: Security/debug logs can leak authentication and request context details and increase sensitive data exposure in production-like environments.
Fix: Change defaults to safe levels in base config (e.g., `org.springframework.security: WARN`, service logger `INFO`), and make DEBUG only opt-in via dedicated profile/env toggle.
Status: FIXED

## AUDIT-0020
Severity: HIGH
File: identity-service/Dockerfile, analysis-service/Dockerfile, notification-service/Dockerfile, project-config-service/Dockerfile, report-service/Dockerfile, sync-service/Dockerfile, user-group-service/Dockerfile
Description: Non-gateway service runtime Dockerfiles still run with default root user and lack explicit healthchecks; builder/runtime base images are also not digest-pinned.
Why: Root container runtime increases impact of container escape/app compromise; unpinned images create supply-chain drift.
Fix: Apply the same hardening pattern used by `api-gateway/Dockerfile`: create non-root runtime user, set `USER`, add healthcheck, and pin base images by digest (or tightly pinned immutable references).
Status: FIXED

## AUDIT-0021
Severity: MEDIUM
File: .github/workflows/gitleaks.yml
Description: Secret-scan workflow still uses mutable references (`runs-on: ubuntu-latest`, `actions/checkout@v4`, `gitleaks/gitleaks-action@v2`).
Why: Mutable CI dependencies increase supply-chain risk and reduce pipeline reproducibility.
Fix: Pin runner version (e.g., `ubuntu-24.04`) and pin actions to commit SHAs, matching the hardening approach already used in `ci.yml`.
Status: FIXED

## AUDIT-0022
Severity: MEDIUM
File: docker-compose.yml
Description: Default compose file publishes multiple internal microservice ports to host (`identity-service`, `user-group-service`, `project-config-service`, `sync-service`, `analysis-service`, `report-service`, `notification-service`).
Why: Broad host exposure increases local attack surface and encourages insecure promotion of dev topology into higher environments.
Fix: Keep only edge entrypoints exposed by default (typically API gateway), and move internal-service host port mappings into local-only override files.
Status: FIXED

## AUDIT-0023
Severity: MEDIUM
File: docker-compose.yml
Description: Application service images use mutable `:latest` tags (e.g., `samt/api-gateway:latest`).
Why: Mutable tags break reproducibility and can deploy unreviewed image content.
Fix: Use immutable image tags (versioned or digest-pinned) and update deployment docs to enforce immutable references.
Status: FIXED

## AUDIT-0024
Severity: MEDIUM
File: project-config-service/k8s-deployment-example.yaml
Description: Kubernetes example deploys `image: samt/project-config-service:latest` with `imagePullPolicy: Always`.
Why: Mutable image references make rollouts non-deterministic and weaken change control.
Fix: Use immutable image tags or digests in manifests; reserve mutable tags for local/dev examples only and label them explicitly.
Status: FIXED

## AUDIT-0025
Severity: HIGH
File: identity-service/src/main/resources/application.yml, analysis-service/src/main/resources/application.yml, report-service/src/main/resources/application.yml, notification-service/src/main/resources/application.yml, sync-service/src/main/resources/application.yml, user-group-service/src/main/resources/application.yml
Description: Multiple services still default to verbose application/SQL logging in base config (for example `com.example: ${LOG_LEVEL:DEBUG}`, `com.example.user_groupservice: DEBUG`, `org.hibernate.SQL: DEBUG`, `org.hibernate.type.descriptor.sql: TRACE`). Their `application-prod.yml` files do not override these logger levels.
Why: Production inheritance of debug/trace logging can expose query content, identifiers, and request/security context in centralized logs.
Fix: Set safe INFO/WARN defaults in base configs, add explicit hardened logging levels in every `application-prod.yml`, and gate debug/trace behind an explicit opt-in profile.
Status: FIXED

## AUDIT-0026
Severity: MEDIUM
File: project-config-service/k8s-deployment-example.yaml
Description: Kubernetes deployment example does not define `securityContext` at pod or container level (no `runAsNonRoot`, `allowPrivilegeEscalation: false`, dropped capabilities, or read-only root filesystem).
Why: Missing runtime hardening increases blast radius if the application container is compromised.
Fix: Add pod/container security context controls (`runAsNonRoot: true`, explicit non-root UID/GID, `allowPrivilegeEscalation: false`, `capabilities.drop: ["ALL"]`, `readOnlyRootFilesystem: true`) and document required writable mounts.
Status: FIXED

## AUDIT-0027
Severity: MEDIUM
File: docker-compose.yml
Description: Compose services do not set container hardening options such as `security_opt: ["no-new-privileges:true"]`, `cap_drop`, or read-only root filesystems.
Why: Without these controls, successful code execution inside a container has a wider post-exploitation surface.
Fix: Add baseline hardening options per service (at minimum `no-new-privileges`, capability drops, and read-only root filesystem where compatible), then maintain documented exceptions for components that require extra privileges.
Status: FIXED

## AUDIT-0028
Severity: CRITICAL
File: pom.xml
Description: The entire platform is built on Spring Boot 3.2.2 (`<spring-boot.version>3.2.2</spring-boot.version>`). Spring Boot 3.2.x reached End-of-Life on 24 November 2024. As of the audit date (March 2026), this version has been unsupported for over 16 months and will not receive security patches. CVEs discovered in Spring Framework, Spring Security, Netty, Tomcat, and transitive dependencies after that date are unpatched.
Why: Running an EOL framework in production violates OWASP A06 (Vulnerable and Outdated Components). Known unpatched vulnerabilities in transitive dependencies accumulated over 16 months represent unquantified but real attack surface.
Fix: Upgrade to Spring Boot 3.4.x (current supported branch). Run `./mvnw versions:use-latest-releases` or manually set `<spring-boot.version>3.4.x</spring-boot.version>` in the root `pom.xml`, resolve any API-breaking changes, and re-run the full test suite and OWASP dependency check.
Status: OPEN

## AUDIT-0029
Severity: HIGH
File: .github/workflows/ci.yml
Description: The CI pipeline runs Trivy container scanning exclusively against the `api-gateway` image (`docker build -f api-gateway/Dockerfile -t samt/api-gateway:ci .` followed by `aquasecurity/trivy-action`). The remaining seven service images (`identity-service`, `user-group-service`, `project-config-service`, `sync-service`, `analysis-service`, `report-service`, `notification-service`) are never built and scanned for OS-level or library CVEs in CI.
Why: Unscanned container images can ship critical CVEs (base-OS packages, JVM, Alpine libraries) to production without detection. A vulnerability in any backend service container is a lateral movement vector once the cluster is entered through any surface.
Fix: Extend CI with a matrix or sequential Trivy scan step covering all service images. Example: add a `strategy.matrix.service` over all service names, build each image, and run Trivy with `exit-code: 1` and `severity: HIGH,CRITICAL`.
Status: OPEN

## AUDIT-0030
Severity: HIGH
File: pom.xml
Description: Core inter-service communication libraries are significantly outdated. `grpc.version` is pinned to `1.61.0` (released early 2024; current stable is ~1.71.x as of Q1 2026) and `protobuf.version` is `3.25.1` (released November 2023). protobuf-java 3.25.1 is vulnerable to **CVE-2024-7254** — a denial-of-service vulnerability via deeply nested repeated empty fields that causes stack overflow during parsing, fixed in 3.25.5 / 4.27.5+. Multiple other gRPC and protobuf security fixes have been released in intervening versions.
Why: CVE-2024-7254 allows an unauthenticated attacker to craft a malformed protobuf payload and cause stack exhaustion (service crash / DoS) in any service that deserialises incoming gRPC messages. With mTLS disabled in Docker dev environments this is reachable.
Fix: Upgrade `protobuf.version` to `3.25.5` minimum (or `4.29.x` LTS). Upgrade `grpc.version` to the latest stable `1.71.x`. Test for API compatibility in service proto definitions before merging.
Status: OPEN

## AUDIT-0031
Severity: HIGH
File: k8s-secrets.template.yml, project-config-service/k8s-deployment-example.yaml
Description: The Kubernetes NetworkPolicy `app-tier-isolation` in `k8s-secrets.template.yml` defines ingress/egress rules using `podSelector: matchLabels: tier: app`. However, the only concrete deployment in the repository (`k8s-deployment-example.yaml`) labels its pods `tier: backend`, not `tier: app`. The label mismatch means the `app-tier-isolation` NetworkPolicy does not select any pod, rendering it effectively inactive. Any pod using the `backend` label tier has no enforced network isolation from the web or data tiers as intended.
Why: A NetworkPolicy that never applies defeats the zero-trust network segmentation goal of the design. Pods labelled `tier: backend` can be reached by or can reach network segments they should not have access to, increasing lateral movement blast radius after a compromise.
Fix: Standardise on a single tier label. Either change the deployment label to `tier: app` to match the existing NetworkPolicy, or update the NetworkPolicy selector to `tier: backend`. Audit all other deployments (one per microservice) for the same label inconsistency before rolling the Kubernetes manifests to any cluster.
Status: OPEN

## AUDIT-0032
Severity: MEDIUM
File: analysis-service/Dockerfile, api-gateway/Dockerfile, identity-service/Dockerfile, notification-service/Dockerfile, project-config-service/Dockerfile, report-service/Dockerfile, sync-service/Dockerfile, user-group-service/Dockerfile
Description: Every Dockerfile uses `FROM maven:3.9.6-eclipse-temurin-21 AS builder` for the build stage. This tag is not pinned to a SHA256 digest. Docker Hub image tags are mutable — a tag can be silently overwritten with a different image layer at any time. If the `maven:3.9.6-eclipse-temurin-21` image on Docker Hub is ever compromised or inadvertently updated, CI builds will silently incorporate the poisoned image without any integrity alarm. In contrast, the runtime stage (`eclipse-temurin:21.0.6_7-jre-alpine`) uses a version-specific tag that is effectively immutable.
Why: Supply-chain attack via compromised builder image can exfiltrate source code, inject backdoors into compiled artifacts, or leak build-time secrets (credentials, keys injected as build args or env vars). This is a CI/CD supply-chain risk (SLSA level gap).
Fix: Pin the Maven builder image to its digest. Retrieve it with `docker inspect --format='{{index .RepoDigests 0}}' maven:3.9.6-eclipse-temurin-21` and use `FROM maven:3.9.6-eclipse-temurin-21@sha256:<digest> AS builder`. Automate digest refresh via Dependabot or Renovate.
Status: OPEN

## AUDIT-0033
Severity: MEDIUM
File: docker-compose.yml, docker-compose.kafka-production.yml
Description: All three infrastructure service images in Compose are referenced by mutable tags only: `postgres:15-alpine`, `redis:7-alpine`, and `confluentinc/cp-kafka:7.6.0`. While these tags are more specific than `:latest`, they are still mutable — a registry maintainer can repush a different layer set under the same tag. This applies to both the default compose and the production Kafka compose file.
Why: Mutable image tags for infrastructure services create supply-chain drift and can introduce unreviewed database or message-broker changes, including security-relevant configuration changes or vulnerable package upgrades, without triggering a pipeline rebuild.
Fix: Pin each infrastructure image to its digest (e.g., `postgres:15-alpine@sha256:<digest>`). Use Renovate or Dependabot to automate digest update PRs when upstream patches are released.
Status: OPEN

## AUDIT-0034
Severity: MEDIUM
File: .github/workflows/ci.yml
Description: The CI pipeline includes Software Composition Analysis (OWASP dependency-check) and container scanning (Trivy) but has no Static Application Security Testing (SAST) step. Code-level vulnerabilities — injection flaws, insecure deserialization, path traversal, unsafe reflection, hard-coded credentials — are not caught before merge.
Why: SCA and container scanning find known CVEs in declared dependencies; they do not detect logic-level vulnerabilities or novel misuses of libraries in application code. OWASP A03 (Injection), A08 (Software/Data Integrity Failures), and others are principally detected through SAST.
Fix: Add a SAST job to `ci.yml`. Options: (1) GitHub CodeQL (`github/codeql-action`) for Java — free for public repos, available for private via GHAS; (2) Semgrep OSS (`returntocorp/semgrep-action`) with the `java` ruleset; (3) SpotBugs with the `find-sec-bugs` plugin as a Maven surefire step. At minimum, configure CodeQL with `security-and-quality` query suite and fail the build on `error`-level findings.
Status: OPEN

## AUDIT-0035
Severity: MEDIUM
File: k8s-secrets.template.yml
Description: The `secret-rotator` CronJob rotates both `postgres-credentials` and `redis-credentials` Kubernetes Secrets but then only triggers a rollout restart for `identity-service`. The other six services that consume these same credentials (`user-group-service`, `project-config-service`, `sync-service`, `analysis-service`, `report-service`, `notification-service`) are not restarted. After rotation, those services will continue to use the old (now invalidated) credentials from their environment, causing authentication failures that require manual intervention to resolve.
Why: Incomplete credential rotation defeats the purpose of the rotation procedure. Services left with stale credentials will either fail health checks (causing a cascading outage) or continue authenticating with the old credentials if the database accepts both during a transition window — which undermines the rotation's security goal.
Fix: Add `kubectl rollout restart deployment/<name> -n $NAMESPACE` commands in the CronJob script for every deployment that consumes `postgres-credentials` or `redis-credentials`. Consider using an annotation-driven restart controller (e.g., Reloader by Stakater) to automate this automatically whenever a referenced Secret changes.
Status: OPEN

## AUDIT-0036
Severity: MEDIUM
File: analysis-service/src/main/java/com/example/analysisservice/security/SecurityConfig.java, notification-service/src/main/java/com/example/notificationservice/security/SecurityConfig.java, project-config-service/src/main/java/com/samt/projectconfig/security/SecurityConfig.java, report-service/src/main/java/com/example/reportservice/security/SecurityConfig.java, sync-service/src/main/java/com/example/syncservice/security/SecurityConfig.java, user-group-service/src/main/java/com/example/user_groupservice/security/SecurityConfig.java
Description: All downstream services validate that the internal gateway JWT contains a non-blank `jti` claim (`jtiRequiredValidator`), but none implement a replay-prevention cache. An attacker who intercepts an internal JWT — for example via a compromised sidecar or a logging misconfiguration — can re-present it to any downstream service within the effective validity window (TTL=20 s + clock-skew=30 s = up to 50 s). Refresh tokens issued by the identity service use opaque UUIDs stored in the database and are therefore revocable; internal JWTs are not.
Why: Without a seen-JTI cache, internal bearer tokens are replayable within their validity window. Combined with the risk of a misconfigured log that captures Authorization headers, this creates a privilege escalation / replay-attack vector between internal services.
Fix: Implement a Redis-backed JTI seen-set (TTL = token TTL + clock skew = 60 s) in a shared component (e.g., `common-contracts`). On every validated internal JWT, `SET jti:<jti> 1 EX 60 NX`; if the key already exists, reject with `401`. Wire this check into the `JwtDecoder` bean in each downstream `SecurityConfig` or, preferably, via a shared `OAuth2TokenValidator<Jwt>` implementation.
Status: OPEN
