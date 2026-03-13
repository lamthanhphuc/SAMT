# CI/CD Pipeline Validation Checklist

## Required Jobs

- [ ] Build backend modules (`mvn -q -DskipTests=false verify`).
- [ ] Build frontend (`npm --prefix ../FE-SAMT run build`).
- [ ] Run backend tests (`mvn test`).
- [ ] Run API contract tests (`npm run tests:contract` from SAMT root).
- [ ] Run Playwright QA testkit (`npm run test:e2e` from qa-testing).
- [ ] Run security checks (dependency scan + API security suite).

## Gating Rules

- [ ] Any test failure blocks merge/deploy.
- [ ] Critical vulnerability blocks deploy.
- [ ] OpenAPI contract drift blocks deploy.
- [ ] Docker build failure blocks deploy.

## Artifacts

- [ ] Publish test reports (JUnit/Playwright/contract).
- [ ] Publish code coverage report.
- [ ] Store docker image digest/SBOM.

## Pre-Deploy Approval

- [ ] QA sign-off complete.
- [ ] UAT sign-off complete.
- [ ] Rollback plan validated.
