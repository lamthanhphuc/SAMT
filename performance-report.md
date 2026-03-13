# Performance Reliability Report

## Scope

- Tool: k6
- Script: `tests/performance/performance-test.js`
- Base URL: `http://localhost:9080` (overridable with `BASE_URL`)

## Load Profile Implemented

1. Warmup: 10 users (`login_warmup`)
2. Concurrency phase: 50 users (`main_api_steady`)
3. Burst phase: 200 requests (`report_burst`)

## Target Scenarios

- Login endpoint: `POST /api/auth/login`
- Main API endpoint: `GET /api/users/me`
- Report/dashboard endpoint: `GET /api/reports/lecturer/overview`

## Validation Thresholds

- Average response time: `http_req_duration avg < 500ms`
- Error rate: `http_req_failed rate < 1%`
- Check pass ratio: `checks rate > 99%`

## Reliability Findings

- Identified potential slow-path in report service: full-table in-memory commit scans via `githubCommitRepository.findAll().stream()`.
- Impact: increased memory pressure and latency risk under load.

## Auto Fix Applied

- Added range-aware aggregate repository queries in:
  - `report-service/src/main/java/com/example/reportservice/repository/GithubCommitRepository.java`
- Removed in-memory counting dependency path by switching to DB-level aggregation in:
  - `report-service/src/main/java/com/example/reportservice/service/impl/DashboardReportingServiceImpl.java`

## Execution Command

```bash
k6 run tests/performance/performance-test.js
```

## Pass Criteria

- Both thresholds must pass for release readiness.
- Any threshold breach must fail the performance gate.
