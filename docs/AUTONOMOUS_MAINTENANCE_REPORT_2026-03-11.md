# Autonomous Maintenance Report - 2026-03-11

## Summary

This cycle resolved the previously open transaction defect in project-config-service, converted the public group-member listing endpoint to pagination, capped one remaining unbounded membership fan-out path, enabled Redis cache metrics for semester caches, and redeployed the affected services with packaged jars.

## 1. Root Cause of UnexpectedRollbackException

The rollback path in project-config-service had two layers:

1. The gRPC credential-read entrypoint in [project-config-service/src/main/java/com/samt/projectconfig/client/grpc/ProjectConfigInternalGrpcService.java](project-config-service/src/main/java/com/samt/projectconfig/client/grpc/ProjectConfigInternalGrpcService.java) was still participating in transactional service-layer flow while invalid configs raised expected business exceptions during decrypted-token reads.
2. Earlier validation attempts were misleading because only `compile` had been run. Docker builds copy the packaged jar from `project-config-service/target/*.jar`, so source changes were not actually deployed until a full packaged build was produced.

The defect was eliminated after:

- removing transactional behavior from the gRPC boundary,
- delegating the gRPC credential-read path to the non-transactional helper in [project-config-service/src/main/java/com/samt/projectconfig/service/ProjectConfigService.java](project-config-service/src/main/java/com/samt/projectconfig/service/ProjectConfigService.java), and
- rebuilding with `./mvnw -pl project-config-service -am clean install -DskipTests` before recreating the container.

Runtime verification after the packaged redeploy showed the gRPC bean was no longer proxied and the sync-triggered credential-read path no longer emitted `UnexpectedRollbackException`.

## 2. Transaction Configuration Fixes Applied

Applied changes:

- [project-config-service/src/main/java/com/samt/projectconfig/client/grpc/ProjectConfigInternalGrpcService.java](project-config-service/src/main/java/com/samt/projectconfig/client/grpc/ProjectConfigInternalGrpcService.java)
  - removed the gRPC method-level transaction wrapper,
  - made the internal gRPC methods final,
  - routed decrypted-token reads through `getDecryptedTokensWithoutTransaction(...)`.
- [project-config-service/src/main/java/com/samt/projectconfig/service/ProjectConfigService.java](project-config-service/src/main/java/com/samt/projectconfig/service/ProjectConfigService.java)
  - added `getDecryptedTokensWithoutTransaction(UUID id)`,
  - retained `getDecryptedTokens(UUID id)` as a delegating entrypoint with `noRollbackFor` on expected business exceptions.

Validation result:

- Packaged rebuild and redeploy completed successfully.
- Fresh sync-driven gRPC reads no longer produced the rollback-only exception.

## 3. Pagination Fixes Implemented

Applied changes in user-group-service:

- [user-group-service/src/main/java/com/example/user_groupservice/repository/UserSemesterMembershipRepository.java](user-group-service/src/main/java/com/example/user_groupservice/repository/UserSemesterMembershipRepository.java)
  - added pageable overloads for `findAllByUserId`, `findAllByGroupId`, and `findAllBySemesterId`.
- [user-group-service/src/main/java/com/example/user_groupservice/controller/GroupMemberController.java](user-group-service/src/main/java/com/example/user_groupservice/controller/GroupMemberController.java)
  - changed `GET /api/groups/{groupId}/members` to accept `page` and `size`.
- [user-group-service/src/main/java/com/example/user_groupservice/service/GroupMemberService.java](user-group-service/src/main/java/com/example/user_groupservice/service/GroupMemberService.java)
  - changed the service contract to return `PageResponse<MemberResponse>`.
- [user-group-service/src/main/java/com/example/user_groupservice/service/impl/GroupMemberServiceImpl.java](user-group-service/src/main/java/com/example/user_groupservice/service/impl/GroupMemberServiceImpl.java)
  - switched the public member listing path to repository pagination.
- [user-group-service/src/main/java/com/example/user_groupservice/service/impl/UserServiceImpl.java](user-group-service/src/main/java/com/example/user_groupservice/service/impl/UserServiceImpl.java)
  - replaced the unbounded user-membership load with a bounded `PageRequest.of(0, 200)` read.

Live validation:

- `GET /api/groups/116/members?page=0&size=1` returned:
  - `content.length = 1`
  - `totalElements = 9`
  - `totalPages = 9`

That confirms the public member-list endpoint now paginates correctly instead of returning the full membership set in one response.

## 4. Cache Metrics Validation

Caching changes are implemented in:

- [user-group-service/src/main/java/com/example/user_groupservice/UserGroupServiceApplication.java](user-group-service/src/main/java/com/example/user_groupservice/UserGroupServiceApplication.java)
- [user-group-service/src/main/java/com/example/user_groupservice/config/CacheConfig.java](user-group-service/src/main/java/com/example/user_groupservice/config/CacheConfig.java)
- [user-group-service/src/main/java/com/example/user_groupservice/service/SemesterService.java](user-group-service/src/main/java/com/example/user_groupservice/service/SemesterService.java)
- [user-group-service/src/main/resources/application.yml](user-group-service/src/main/resources/application.yml)

Final cache configuration details:

- Redis-backed semester caches enabled for `semesterById`, `semesterByCode`, `activeSemester`, and `semesterList`.
- Redis serializer updated to support Java time types so `SemesterResponse` can round-trip through Redis.
- Redis cache statistics enabled so Micrometer reports hit and miss counters.
- actuator metrics exposure expanded to include `metrics`.

Observed live metrics after repeated semester reads:

- `cache.gets = 7`
- `cache.gets{result=hit} = 7`
- `cache.gets{result=miss} = 0`

Interpretation:

- Cache metrics are now exposed and incrementing.
- The validation window observed active hits on warmed semester caches.

## 5. Updated Benchmark Results

Measured through the gateway on the current live stack after the final user-group-service redeploy.

| Endpoint | Iterations | Average ms | Min ms | Max ms | Status |
|---|---:|---:|---:|---:|---|
| /actuator/health | 3 | 3.57 | 2.58 | 4.79 | OK |
| /api/auth/login | 3 | 84.89 | 68.05 | 114.75 | OK |
| /api/semesters | 3 | 13.02 | 11.59 | 15.29 | OK |
| /api/groups/{groupId}/members?page=0&size=1 | 3 | 17.42 | 16.97 | 18.20 | OK |
| /api/sync/all | 1 | n/a | n/a | n/a | HTTP 500 |

Interpretation:

- The fast-path endpoints remain well below the 200 ms target in this local environment.
- The member-list benchmark now reflects the paginated endpoint, not the previous unbounded list behavior.
- `/api/sync/all` remains a functional failure because external integration state is still invalid in the local dataset.

## 6. Generated Artifact Sync

After the API changes, generated artifacts were resynchronized:

- [openapi.yaml](openapi.yaml) regenerated from live service docs.
- HTTP test suites regenerated with [scripts/generate-httpyac-tests.mjs](scripts/generate-httpyac-tests.mjs).

## 7. Remaining Technical Debt

Open items after this cycle:

1. [sync-service/src/main/java/com/example/syncservice/controller/SyncController.java](sync-service/src/main/java/com/example/syncservice/controller/SyncController.java) still fronts a failing `/api/sync/all` path when local Jira or GitHub credentials/config states are invalid.
2. [user-group-service/src/main/java/com/example/user_groupservice/service/impl/GroupServiceImpl.java](user-group-service/src/main/java/com/example/user_groupservice/service/impl/GroupServiceImpl.java) still assembles full group-detail member lists without pagination in `getGroupById(...)`.
3. [user-group-service/src/main/java/com/example/user_groupservice/service/impl/UserServiceImpl.java](user-group-service/src/main/java/com/example/user_groupservice/service/impl/UserServiceImpl.java) now caps memberships but does not yet expose a paginated public contract for `/api/users/{userId}/groups`.
4. Identity audit retention is still unbounded.
5. Kafka consumer lag visibility is still not exposed through Micrometer or external tooling in the local stack.
6. Schema drift reporting still reflects generated artifact differences against git `HEAD` and should be treated separately from runtime correctness.

## 8. Final State

- `UnexpectedRollbackException` on the project-config gRPC credential-read path: resolved in the deployed runtime.
- Public group-member reads: paginated.
- Semester caching: enabled, serializer-fixed, metrics-enabled, and validated with live cache-hit counters.
- Affected services rebuilt from packaged jars and redeployed successfully.