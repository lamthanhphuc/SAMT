# SAMT QA Testing Kit

This folder provides a complete, runnable QA strategy for SAMT:

- Playwright E2E UI tests
- API contract tests validated against `../openapi.yaml`
- Security API checks (authz/authn, injection, rate-limit behavior)
- QA + UAT + DevOps checklists
- Docker health verification script

## Folder Structure

- `e2e/`: UI flows and UI error handling
- `api/`: OpenAPI contract + API security tests
- `checklists/`: QA, DevOps, Security, UAT checklists
- `devops/`: Docker/health validation scripts
- `ci/`: Pipeline validation checklist

## Prerequisites

- Node.js 20+
- Docker + Docker Compose
- Running SAMT backend on `http://localhost:9080`
- Optional frontend auto-start from Playwright config (`../FE-SAMT`)

## Install

```bash
cd SAMT/qa-testing
npm install
npx playwright install chromium
```

## Run

```bash
npm run test:e2e
npm run test:e2e:api
npm run test:e2e:security
```

## Environment Variables

- `API_BASE_URL` (default: `http://localhost:9080`)
- `FE_BASE_URL` (default: `http://localhost:5173`)
- `E2E_LOGIN_EMAIL` (optional for real login test)
- `E2E_LOGIN_PASSWORD` (optional for real login test)
- `PW_NO_WEBSERVER=1` if FE already running and you do not want auto-start

## Notes

- OpenAPI validation uses runtime schema extraction from `../openapi.yaml`.
- Security tests are safe black-box negative tests (no destructive payloads).
