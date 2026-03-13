# DevOps Deployment Checklist

## Docker Build and Runtime

- [ ] `docker compose -f ../docker-compose.yml build` succeeds.
- [ ] All required containers start successfully.
- [ ] Health checks return `healthy` for stateful dependencies and services.
- [ ] Exposed ports map correctly and are reachable.

## Environment & Secrets

- [ ] `.env` is present and required vars are populated.
- [ ] No hardcoded secrets in source/config files.
- [ ] Runtime env values are loaded inside containers.
- [ ] Sensitive values are masked in logs and CI outputs.

## Observability & Logging

- [ ] Correlation/request id propagates across request lifecycle.
- [ ] Error logs include stack traces for server-side diagnostics.
- [ ] Logs do not expose access token/refresh token/password/secret values.

## CI/CD Validation

- [ ] Unit/integration/API/E2E test jobs pass.
- [ ] Contract tests against OpenAPI pass.
- [ ] Security checks pass (SAST/dependency scan/negative API security tests).
- [ ] Docker image scanning passes severity threshold.
- [ ] Deployment gate blocks release when critical checks fail.
