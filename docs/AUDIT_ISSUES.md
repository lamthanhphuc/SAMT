# Repository Audit Issues (Synchronized)

This file is the authoritative, deduplicated issue register for the current repo state.

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
