# Admin Dashboard Metrics Proposal

## Context
Current FE cards on admin dashboard include:
- Total Users
- Total Groups
- Active Projects
- Pending Requests

After refactor:
- `Pending Requests` already uses backend sync jobs with `status=PENDING`.
- `Active Projects` is still derived from `Total Groups` (metric coupling).

## Existing Backend Capabilities
Available endpoints from current OpenAPI:
- `GET /api/users` -> user totals (user-group-service)
- `GET /api/groups` -> group totals (user-group-service)
- `GET /api/sync/jobs` -> sync job totals by status/jobType (sync-service)
- `GET /api/project-configs/group/{groupId}` -> config by group (project-config-service)

Gap:
- No list/summary endpoint for project-config states across all groups.
- FE cannot compute real "Active Projects" without N+1 calls over groups.

## Proposed Contract (Recommended)

### 1) New endpoint: Admin metrics overview
- Method: `GET`
- Path: `/api/reports/admin/overview`
- Service: report-service (aggregator)
- Auth: `ADMIN`
- Query params:
  - `semesterId` (optional, long)

### Response schema
```json
{
  "success": true,
  "status": 200,
  "path": "/api/reports/admin/overview",
  "data": {
    "semesterId": 2,
    "totalUsers": 124,
    "totalGroups": 36,
    "activeProjects": 28,
    "pendingSyncJobs": 7,
    "jiraApiHealth": "HEALTHY",
    "githubApiHealth": "ISSUE",
    "serverHealth": "ONLINE",
    "lastCalculatedAt": "2026-03-17T11:00:00Z"
  },
  "timestamp": "2026-03-17T11:00:00Z"
}
```

### Field definitions
- `activeProjects`: count of project configs in active states, recommended rule:
  - include `VERIFIED`, optionally include `DRAFT` with successful sync in last X days (business decision)
- `pendingSyncJobs`: count of sync jobs with `status=PENDING`
- health fields (`jiraApiHealth`, `githubApiHealth`): enum `HEALTHY | DEGRADED | ISSUE | NO_DATA`
- `serverHealth`: enum `ONLINE | DEGRADED | OFFLINE`

## Alternative Contract (If keeping domain ownership strict)

### 2) Add summary endpoint in project-config-service
- Method: `GET`
- Path: `/api/project-configs/summary`
- Query: `semesterId` optional

Response:
```json
{
  "success": true,
  "status": 200,
  "data": {
    "totalConfigs": 36,
    "verifiedConfigs": 28,
    "draftConfigs": 6,
    "invalidConfigs": 2
  }
}
```

Then FE computes:
- `activeProjects = verifiedConfigs` (or another agreed rule)

## FE Mapping Plan
- `Total Users` <- `data.totalUsers`
- `Total Groups` <- `data.totalGroups`
- `Active Projects` <- `data.activeProjects`
- `Pending Requests` <- `data.pendingSyncJobs`
- System status pills <- health fields from overview endpoint

## Migration Steps
1. Implement backend endpoint + OpenAPI update.
2. Add FE API client + hook `useAdminOverview`.
3. Replace multi-query card composition in `AdminDashboard` with single overview query.
4. Add unit tests for API adapter and dashboard rendering.

## Notes on Current FE Improvements
In this refactor cycle, FE already moved page-local counts to global backend totals in:
- Reports page cards (status totals by query `totalElements`)
- Sync jobs page cards (status totals by query `totalElements`)
