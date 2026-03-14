# Endpoint Inventory

- Generated at: 2026-03-12T05:00:16.680Z
- Operations: 45
- Public endpoints: 4
- Protected endpoints: 41

| Method | Path | Service | Auth | Test Coverage |
| --- | --- | --- | --- | --- |
| GET | /.well-known/jwks.json | identity-service | public |  |
| GET | /api/admin/audit/actor/{actorId} | identity-service | protected | http-tests/suites/api-auth.http |
| GET | /api/admin/audit/entity/{entityType}/{entityId} | identity-service | protected | http-tests/suites/api-auth.http |
| GET | /api/admin/audit/range | identity-service | protected | http-tests/suites/api-auth.http |
| GET | /api/admin/audit/security-events | identity-service | protected | http-tests/suites/api-auth.http |
| POST | /api/admin/users | identity-service | protected | http-tests/suites/api-auth.http |
| DELETE | /api/admin/users/{userId} | identity-service | protected | http-tests/suites/api-auth.http |
| PUT | /api/admin/users/{userId}/external-accounts | identity-service | protected | http-tests/suites/api-auth.http |
| POST | /api/admin/users/{userId}/lock | identity-service | protected | http-tests/suites/api-auth.http |
| POST | /api/admin/users/{userId}/restore | identity-service | protected | http-tests/suites/api-auth.http |
| POST | /api/admin/users/{userId}/unlock | identity-service | protected | http-tests/suites/api-auth.http |
| POST | /api/auth/login | identity-service | public | http-tests/suites/api-auth.http |
| POST | /api/auth/logout | identity-service | protected | http-tests/suites/api-auth.http |
| POST | /api/auth/refresh | identity-service | public | http-tests/suites/api-auth.http |
| POST | /api/auth/register | identity-service | public | http-tests/suites/api-auth.http |
| GET | /api/groups | user-group-service | protected | http-tests/suites/api-groups.http |
| POST | /api/groups | user-group-service | protected | http-tests/suites/api-groups.http |
| DELETE | /api/groups/{groupId} | user-group-service | protected | http-tests/suites/api-groups.http |
| GET | /api/groups/{groupId} | user-group-service | protected | http-tests/suites/api-groups.http |
| PUT | /api/groups/{groupId} | user-group-service | protected | http-tests/suites/api-groups.http |
| PATCH | /api/groups/{groupId}/lecturer | user-group-service | protected | http-tests/suites/api-groups.http |
| GET | /api/groups/{groupId}/members | user-group-service | protected | http-tests/suites/api-groups.http |
| POST | /api/groups/{groupId}/members | user-group-service | protected | http-tests/suites/api-groups.http |
| DELETE | /api/groups/{groupId}/members/{userId} | user-group-service | protected | http-tests/suites/api-groups.http |
| PUT | /api/groups/{groupId}/members/{userId}/demote | user-group-service | protected | http-tests/suites/api-groups.http |
| PUT | /api/groups/{groupId}/members/{userId}/promote | user-group-service | protected | http-tests/suites/api-groups.http |
| POST | /api/project-configs | project-config-service | protected | http-tests/suites/api-project-configs.http |
| DELETE | /api/project-configs/{id} | project-config-service | protected | http-tests/suites/api-project-configs.http |
| GET | /api/project-configs/{id} | project-config-service | protected | http-tests/suites/api-project-configs.http |
| PUT | /api/project-configs/{id} | project-config-service | protected | http-tests/suites/api-project-configs.http |
| POST | /api/project-configs/{id}/verify | project-config-service | protected | http-tests/suites/api-project-configs.http |
| POST | /api/project-configs/admin/{id}/restore | project-config-service | protected | http-tests/suites/api-project-configs.http |
| GET | /api/project-configs/group/{groupId} | project-config-service | protected | http-tests/suites/api-project-configs.http |
| GET | /api/semesters | user-group-service | protected | http-tests/suites/api-semesters.http |
| POST | /api/semesters | user-group-service | protected | http-tests/suites/api-semesters.http |
| GET | /api/semesters/{id} | user-group-service | protected | http-tests/suites/api-semesters.http |
| PUT | /api/semesters/{id} | user-group-service | protected | http-tests/suites/api-semesters.http |
| PATCH | /api/semesters/{id}/activate | user-group-service | protected | http-tests/suites/api-semesters.http |
| GET | /api/semesters/active | user-group-service | protected | http-tests/suites/api-semesters.http |
| GET | /api/semesters/code/{code} | user-group-service | protected | http-tests/suites/api-semesters.http |
| GET | /api/users | user-group-service | protected | http-tests/suites/api-groups.http |
| GET | /api/users/{userId} | user-group-service | protected | http-tests/suites/api-groups.http |
| PUT | /api/users/{userId} | user-group-service | protected | http-tests/suites/api-groups.http |
| GET | /api/users/{userId}/groups | user-group-service | protected | http-tests/suites/api-groups.http |
| GET | /internal/project-configs/{id}/tokens | project-config-service | protected |  |

