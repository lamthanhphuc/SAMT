# User-Group Service (code-accurate)

**Ports:** REST `${SERVER_PORT:8082}`, gRPC `${GRPC_SERVER_PORT:9095}`
**Database:** PostgreSQL (schema owned by this service; see Flyway migrations in `user-group-service/src/main/resources/db/migration/`)

Quick links:
- [API Contract](API_CONTRACT.md)
- [Security](SECURITY.md)
- [Database](DATABASE.md)
- [gRPC Contract](GRPC_CONTRACT.md)
- [Implementation Notes](IMPLEMENTATION.md)

---

## 1. Service Responsibility

This service owns and enforces the **group + membership model scoped by semester**.

Implemented responsibilities (from code):
- **Semester CRUD & activation** in the local database (`SemesterController`, `SemesterService`).
- **Group CRUD** (create/update/soft-delete) in the local database (`GroupController`, `GroupService`).
- **Membership management** for students joining groups, plus leader promotion/demotion/removal rules (`GroupMemberController`, `GroupMemberService`).
- **User profile proxy** endpoints that *delegate user data operations to Identity Service via gRPC* (`UserController`, `UserService`).
- **Expose gRPC** for other services to verify group existence and membership/leader role (`UserGroupGrpcServiceImpl`).
- **Consume `user.deleted` Kafka events** (when Kafka is enabled) to clean up memberships (`event/*`).

Non-responsibilities (by absence in code):
- No login/registration/JWT issuance.
- No permission/ACL system beyond **system roles** (JWT `roles`) and **group roles** (LEADER/MEMBER).
- No Redis caching.

---

## 2. Implemented APIs

### REST (JSON)
Base paths are exactly as defined by controllers (no global context-path in `application.yml`). All endpoints require JWT authentication **except** actuator + swagger routes configured in security.

**Groups** ([user-group-service/src/main/java/.../controller/GroupController.java](../../user-group-service/src/main/java/com/example/user_groupservice/controller/GroupController.java))
- `POST /api/groups` — **ADMIN**
- `GET /api/groups/{groupId}` — authenticated
- `GET /api/groups?page&size&semesterId&lecturerId` — authenticated
- `PUT /api/groups/{groupId}` — **ADMIN**
- `DELETE /api/groups/{groupId}` (soft delete) — **ADMIN**
- `PATCH /api/groups/{groupId}/lecturer` — **ADMIN**

**Group members** ([user-group-service/src/main/java/.../controller/GroupMemberController.java](../../user-group-service/src/main/java/com/example/user_groupservice/controller/GroupMemberController.java))
- `POST /api/groups/{groupId}/members` — **ADMIN or LECTURER**
- `GET /api/groups/{groupId}/members` — authenticated
- `PUT /api/groups/{groupId}/members/{userId}/promote` — **ADMIN or LECTURER**
- `PUT /api/groups/{groupId}/members/{userId}/demote` — **ADMIN or LECTURER**
- `DELETE /api/groups/{groupId}/members/{userId}` (soft delete membership) — **ADMIN**

**Users (proxy to Identity via gRPC)** ([user-group-service/src/main/java/.../controller/UserController.java](../../user-group-service/src/main/java/com/example/user_groupservice/controller/UserController.java))
- `GET /api/users/{userId}` — authenticated (service-level authorization applies)
- `PUT /api/users/{userId}` — authenticated (service-level authorization applies)
- `GET /api/users` — **ADMIN** (delegates to Identity `ListUsers`)
- `GET /api/users/{userId}/groups` — authenticated (service-level authorization applies)

**Semesters** ([user-group-service/src/main/java/.../controller/SemesterController.java](../../user-group-service/src/main/java/com/example/user_groupservice/controller/SemesterController.java))
- `POST /api/semesters` — **ADMIN**
- `GET /api/semesters/{id}` — authenticated
- `GET /api/semesters/code/{code}` — authenticated
- `GET /api/semesters/active` — authenticated
- `GET /api/semesters` — authenticated
- `PUT /api/semesters/{id}` — **ADMIN**
- `PATCH /api/semesters/{id}/activate` — **ADMIN**

### gRPC (server)
Defined in `user-group-service/src/main/proto/usergroup_service.proto` and implemented by `UserGroupGrpcServiceImpl`.

- `VerifyGroupExists(group_id)`
- `CheckGroupLeader(group_id, user_id)`
- `CheckGroupMember(group_id, user_id)`
- `GetGroup(group_id)`

See [GRPC_CONTRACT.md](GRPC_CONTRACT.md).

---

## 3. Authorization Model

### 3.1 Authentication (Internal JWT)
- The service expects requests to come from the API Gateway.
- External JWTs are validated at the gateway (RS256 via JWKS).
- The gateway forwards a short-lived **internal JWT** as `Authorization: Bearer <internal-jwt>`.
- This service validates the internal JWT (RS256 via gateway JWKS) and derives:
  - user id from claim `sub`
  - roles from claim `roles`

Token validation failure behavior (as implemented):
- If the internal JWT is missing or invalid, authentication is not set.
- Spring Security returns `401` for protected endpoints via `JwtAuthenticationEntryPoint`.

### 3.2 System roles vs group roles
- **System roles** are taken from JWT `roles`: `ADMIN`, `LECTURER`, `STUDENT`.
- **Group roles** are stored in DB per membership: `LEADER`, `MEMBER`.

### 3.3 Enforcement points
- **Controller-level RBAC**: enforced by `@PreAuthorize` on endpoints (see Section 2).
- **Service-level authorization**: implemented for user-profile access rules in `UserServiceImpl`:
  - `ADMIN`: can view anyone.
  - Self: can view self.
  - `LECTURER`: can view only `STUDENT` users and only supervised students via `GroupRepository.existsByLecturerAndStudent(actorId, targetUserId)`.
  - If the gRPC role check fails unexpectedly, the code falls back to **allow** and logs a warning.

Role/permission handling implemented in this codebase is limited to the above; there is no separate permission matrix or resource-scoped ACL system.

---

## 4. Data Model

### 4.1 Tables (PostgreSQL)
Owned tables (created/changed via Flyway):
- `semesters`
- `groups`
- `user_semester_membership`

### 4.2 Relationship model (what is enforced)
- A **group** belongs to exactly one **semester** (`groups.semester_id` FK).
- A **membership** is keyed by `(user_id, semester_id)` and points to a `group_id`.

This yields the enforced constraints:
- **One group per user per semester**: `PRIMARY KEY (user_id, semester_id)` + trigger guard.
- **One leader per group**: partial unique index on `user_semester_membership(group_id)` where `group_role='LEADER' AND deleted_at IS NULL`.
- **Soft delete**:
  - `groups.deleted_at/deleted_by`
  - `user_semester_membership.deleted_at/deleted_by`
  - JPA entities use `@SQLRestriction("deleted_at IS NULL")` to filter soft-deleted rows in most ORM queries.
- **Optimistic locking**:
  - `groups.version`
  - `user_semester_membership.version`

### 4.3 Cross-service references
Fields `groups.lecturer_id`, `user_semester_membership.user_id`, and `deleted_by` are **logical references** to Identity Service users (no DB FK to Identity DB). Lecturer/student validity is enforced via gRPC calls to Identity in service logic.

See [DATABASE.md](DATABASE.md) for the exact constraints/triggers.

---

## 5. Inter-service Communication

### 5.1 Identity Service (gRPC client)
This service calls Identity Service via gRPC for:
- verifying a user exists (`verifyUserExists`)
- retrieving a user’s system role (`getUserRole`)
- fetching user details (`getUser`, `getUsers`)
- updating a user profile (`updateUser`)
- listing users (`listUsers`)

Error mapping for many calls is centralized through `GrpcExceptionHandler`, translating gRPC statuses into HTTP-facing business exceptions.

Resilience:
- `ResilientIdentityServiceClient` wraps **some** identity calls (not all) with Resilience4j circuit breaker + retry.

### 5.2 gRPC server (this service)
The exposed gRPC API is used for **group existence + membership/leader checks**.

### 5.3 Kafka (user deletion)
When `spring.kafka.enabled=true` (see `KafkaConsumerConfig`):
- `UserGroupEventConsumer` listens to `user.deleted` and **hard-deletes** all memberships for the user.
- `UserDeletedEventConsumer` also listens to `user.deleted` but uses a different container factory name and performs **soft delete** and **auto-promotes a new leader** when a leader is deleted.

Docs reflect the code as-is; choose/standardize one consumer in code if you want a single, deterministic behavior.

### 5.4 Redis
No Redis client/config/dependency exists in this service module.

---

## 6. Removed Content

The following claims were removed from previous docs because they are not implemented (or contradict code):
- LECTURER being allowed to create/update/delete groups via REST (`GroupController` is ADMIN-only for those).
- Group LEADER being allowed to add/remove members via REST (REST endpoints do not check group-role; only system role via `@PreAuthorize`).
- “User-Group service does not validate JWT signatures” (the service validates the gateway-issued internal JWT via JWKS; external JWT is validated at the gateway).
- “Leader auto-promotion not implemented on user deletion” (there is an implementation in `UserDeletedEventConsumer`, though there is also a competing hard-delete consumer).
- “Planned/future consumers” such as `user.updated` handling and generic event-driven caching.
- Any Redis caching references.
- Any “future use” architecture theory that is not present in code paths.
