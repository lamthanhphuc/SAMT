# Repository cleanup report

## What I did (safe, build-preserving cleanup)

- Confirmed baseline build succeeds: `./mvnw -DskipTests package`.
- Built a Maven/module dependency map and identified Spring Boot entry points.
- Deleted *provably* unused artifacts and dead code (only when unreferenced by code/config and not runtime-discovered).
- Rebuilt to verify compilation still succeeds, then ran `./mvnw clean` to remove regenerated build outputs.

## Entrypoints (runtime roots)

Spring Boot applications detected via `@SpringBootApplication` + `main()`:

- analysis-service: `com.example.analysisservice.AnalysisServiceApplication`
- api-gateway: `com.example.gateway.ApiGatewayApplication`
- identity-service: `com.example.identityservice.IdentityServiceApplication`
- notification-service: `com.example.notificationservice.NotificationServiceApplication`
- project-config-service: `com.samt.projectconfig.ProjectConfigServiceApplication`
- report-service: `com.example.reportservice.ReportServiceApplication`
- sync-service: `com.example.syncservice.SyncServiceApplication`
- user-group-service: `com.example.user_groupservice.UserGroupServiceApplication`

## Module dependency map (Maven)

Modules from the root parent POM:

- common-contracts (library)
- common-events (library)
- analysis-service
- api-gateway
- identity-service
- notification-service
- report-service
- sync-service
- user-group-service
- project-config-service

Inter-module edges (direct dependencies):

- common-events -> common-contracts
- identity-service -> common-events
- sync-service -> common-events
- user-group-service -> common-events, common-contracts
- project-config-service -> common-events, common-contracts

(Other services do not declare in-repo module dependencies.)

## Deleted items

### Deleted directories (generated/orphan; not part of the source dependency graph)

- `**/target/` (Maven build output)
- `logs/`
- `common-actuator-security/` (contained only build output; not a Maven module)
- `.copilot-recovery/` (local tooling artifact)

### Deleted tracked files (dead code / scratch artifacts)

- `.tmp/protobuf-compile-help.txt`
- `.tmp/ug-mvn-debug.txt`

Dead Java files removed (unreferenced by other source/config and not discovered via Spring/JPA annotations):

- `common-events/src/main/java/com/example/common/security/kafka/ProdKafkaSecurityEnforcerEnvironmentPostProcessor.java`
- `identity-service/src/main/java/com/example/identityservice/config/FailFastProductionProfileEnforcer.java`
- `project-config-service/src/main/java/com/samt/projectconfig/security/ProdHardeningEnvironmentPostProcessor.java`
- `user-group-service/src/main/java/com/example/user_groupservice/dto/request/AssignRoleRequest.java`
- `user-group-service/src/main/java/com/example/user_groupservice/dto/request/UpdateGroupLecturerRequest.java`
- `user-group-service/src/main/java/com/example/user_groupservice/dto/response/GroupMembersResponse.java`
- `user-group-service/src/main/java/com/example/user_groupservice/entity/SystemRole.java`

Notes on the EnvironmentPostProcessor classes:
- No `spring.factories` / Boot 3 post-processor registration files were found referencing them, so they are not reachable at runtime.

## Build verification

- Build used for verification: `./mvnw -DskipTests package`
- Post-build cleanup: `./mvnw clean`

## Final top-level structure (post-clean)

- analysis-service/
- api-gateway/
- common-contracts/
- common-events/
- docs/
- identity-service/
- notification-service/
- project-config-service/
- report-service/
- sync-service/
- user-group-service/
- plus repository-level config files (compose, k8s template, README, etc.)
