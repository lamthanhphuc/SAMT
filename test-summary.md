# SAMT Combined Test Summary

- Date: 2026-03-13
- System: SAMT
- Combined Command: `npm run tests:all`
- Combined Result: **PASS** (`TESTS_ALL_EXIT:0`)
- Combined Duration: **337.27s**

## Environment Configuration Used

- Backend base URL: `http://localhost:9080` (API Gateway)
- Backend port: `9080`
- Frontend base URL: `http://localhost:5173`
- Frontend port: `5173`
- Playwright web server command: `npm --prefix ../../FE-SAMT run dev -- --host --port 5173`
- FE runtime API URL fallback: `VITE_API_URL=http://localhost:9080`
- API test base URL fallback: `API_BASE_URL=http://localhost:9080`
- Docker status: **UP/healthy** for gateway, microservices, databases, Kafka, and Redis

## Docker Runtime Snapshot

- `api-gateway` — healthy (`0.0.0.0:9080->8080`)
- `identity-service` — healthy (`0.0.0.0:8081->8081`)
- `user-group-service` — healthy (`0.0.0.0:8082->8082`)
- `sync-service` — healthy (`0.0.0.0:8083->8083`)
- `project-config-service` — healthy (`0.0.0.0:8084->8084`)
- `notification-service` — healthy (`0.0.0.0:8085->8085`)
- `analysis-service` — healthy (`0.0.0.0:8087->8087`)
- `report-service` — healthy (`0.0.0.0:8088->8088`)

## Suite Summary

| Suite | Passed | Failed | Duration |
|---|---:|---:|---:|
| E2E (Playwright) | 14 | 0 | 17.2s |
| API Contract (Schemathesis) | 1908 | 0 | 97.39s |
| Smoke | 10 | 0 | 1.4s |
| Regression | 213 | 0 | 224.03s |

## Consolidated Totals

- Total checks executed: **2145**
- Total passed: **2145**
- Total failed: **0**
- Warnings: **0**

## Final Status

All required suites pass in the combined run context:

- E2E: ✅
- API Contract: ✅
- Smoke: ✅
- Regression: ✅
