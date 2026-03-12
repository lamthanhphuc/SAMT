# Automated QA

The repository now includes a top-level QA orchestration layer that sits on top of the existing generated httpyac, Schemathesis, and self-heal tooling.

## Main Entry Points

- `npm run qa:discover` generates `qa/endpoints.json` and `qa/endpoints.md` by combining the unified OpenAPI contract, gateway routes, controller annotations, and public-path rules.
- `npm run qa:security` probes selected public and protected endpoints without credentials and fails on authentication regressions.
- `npm run qa:hardening` sends JWT tampering, role-escalation, mass-assignment, invalid content type, and large payload probes through the live gateway.
- `npm run qa:integration` performs a real end-to-end Jira and GitHub verification flow through `POST /api/project-configs/{id}/verify` using sandbox credentials from `.env`.
- `npm run qa:load` runs the expanded k6 profile for login, profile retrieval, group creation, and project configuration verification, then compares the result with a stored baseline.
- `npm run qa:communication` validates gateway routing, timeout behavior, graceful error propagation, and dependency recovery by stopping a downstream service.
- `npm run qa:chaos` executes stop, restart, latency, and packet-loss chaos experiments against the Docker Compose stack.
- `npm run qa:architecture` scans the codebase for controller business logic, missing service layers, DTO leaks, missing validation, duplicate logic, transaction gaps, and hardcoded secrets.
- `npm run qa:auto-fix` classifies smoke, regression, and Schemathesis failures and applies minimal safe test or override patches where possible.
- `npm run qa:dashboard` aggregates all generated reports into a JSON summary plus an HTML dashboard.
- `npm run qa:system` runs the full iterative reliability loop and emits JSON, Markdown, and HTML reports.
- `.github/workflows/qa-system.yml` runs the same QA system manually or on a nightly schedule in GitHub Actions.

## Reliability Loop

`npm run qa:system` now operates as an iterative validation loop instead of a single pass.

Flow:

- discovery and OpenAPI regeneration
- smoke, regression, contract, and fuzz validation
- security hardening probes
- performance validation with baseline comparison
- inter-service communication validation
- chaos experiments
- architecture scan
- external integration verification
- failure analysis and auto-fix for supported test and schema issues
- re-run until stable or `QA_SYSTEM_MAX_ITERATIONS` is reached

Supported toggles:

- `QA_SYSTEM_MAX_ITERATIONS` controls the loop count. Default: `3`
- `QA_AUTO_FIX` enables or disables the automated fixer. Default: `true`
- `QA_COMPOSE_RECOVERY` enables `docker compose up -d --build` before retrying failed stages. Default: `false`
- `QA_SELF_HEAL_ITERATIONS` controls the existing self-heal retry depth. Default: `2`
- `QA_LOAD_REQUIRED` fails the run if no `k6` runner is available. Default: `false`
- `QA_LOAD_UPDATE_BASELINE` replaces the stored load baseline with the current run. Default: `false`

Example:

```powershell
$env:QA_SYSTEM_MAX_ITERATIONS = '3'
$env:QA_COMPOSE_RECOVERY = 'true'
$env:QA_LOAD_UPDATE_BASELINE = 'true'
npm run qa:system
```

## Environment Mapping

The external integration verifier supports both the requested and current repository naming conventions.

- Jira host: `JIRA_BASE_URL`, fallback `JIRA_HOST`
- Jira email: `JIRA_EMAIL`
- Jira token: `JIRA_API_TOKEN`, fallback `JIRA_TOKEN`
- GitHub repository: `GITHUB_REPO_URL`, fallback `GITHUB_REPO`
- GitHub token: `GITHUB_TOKEN`, fallback `GITHUB_PAT`

Secrets are consumed from `.env` and masked in generated reports.

## Reports

Generated artifacts are written under `.self-heal/reports/`.

- `qa-system-latest.json`
- `qa-system-latest.md`
- `qa-system-latest.html`
- `qa-dashboard.json`
- `qa-dashboard.html`
- `auto-fix.json`
- `chaos.json`
- `communication.json`
- `security-hardening.json`
- `architecture-health.json`
- `security-probe.json`
- `load-test.json`
- `external-integrations.json`

Load testing also stores a historical profile baseline under `.self-heal/baselines/`.

## Recovery Behavior

`npm run qa:system` can optionally rebuild the Docker Compose stack before retrying a failed stage.

Example:

```powershell
$env:QA_COMPOSE_RECOVERY = 'true'
npm run qa:system
```

For smoke, regression, contract, and fuzz failures, the orchestrator invokes the existing self-heal runner before a final retry. If the stage is still failing and `QA_AUTO_FIX=true`, it then runs `qa:auto-fix`, records the proposed or applied patch set, and starts the next loop iteration when a safe change was made.

## CI Notes

The GitHub Actions workflow prepares `.env` from `.env.example`, injects optional Jira and GitHub sandbox secrets, starts Docker Compose, runs `npm run qa:system`, uploads the generated QA artifacts, and tears the stack down.

If no load runner is available in CI, the load stage is reported as skipped unless `QA_LOAD_REQUIRED=true` is set.
