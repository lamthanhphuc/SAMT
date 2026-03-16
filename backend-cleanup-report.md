# Backend Cleanup Report (SAMT)

Date: 2026-03-14
Scope: `SAMT/` only (no change in `FE-SAMT/`)

## 1. Artifacts removed

Cleanup rules applied in backend repo:
- Remove folders: `**/target`, `**/build`, `**/coverage`, `**/dist`
- Remove generated folders: `target/generated-sources`, `target/generated-test-sources`
- Remove files: `*.log`, `*.tmp`, `*-report.json`, `*-report.md`

Total removed entries: **155**

Removed paths:

- E:\Bin\project\SAMT\.github\java-upgrade\20260211035139\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260211035203\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260211035339\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260211035355\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260211035410\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260211035430\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260211035514\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260225044156\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260225044338\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260225044501\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260227150126\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260228160214\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260305054455\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260305054753\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260305055520\logs\0.log
- E:\Bin\project\SAMT\.github\java-upgrade\20260312075549\logs\0.log
- E:\Bin\project\SAMT\.self-heal\reports\regression-drift-report.json
- E:\Bin\project\SAMT\.self-heal\reports\regression-drift-report.md
- E:\Bin\project\SAMT\artifacts\logs\api-regression-ci.log
- E:\Bin\project\SAMT\artifacts\logs\api-smoke.log
- E:\Bin\project\SAMT\artifacts\logs\backend-clean-test.log
- E:\Bin\project\SAMT\artifacts\logs\contract-fuzz.log
- E:\Bin\project\SAMT\artifacts\logs\contract-strict.log
- E:\Bin\project\SAMT\artifacts\logs\docker-down.log
- E:\Bin\project\SAMT\artifacts\logs\docker-up.log
- E:\Bin\project\SAMT\artifacts\logs\frontend-unit.log
- E:\Bin\project\SAMT\artifacts\logs\playwright-e2e.log
- E:\Bin\project\SAMT\artifacts\logs\tests-all-deterministic.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-compose-build.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-compose-config.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-compose-up.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-compose-up-latest.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-mvn-clean-install.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-mvn-latest.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-regression-after-fix.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-regression-run.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-regression-run-2.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-regression-run-3.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-runtime-logs.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-smoke-after-fix.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-smoke-run.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-smoke-run-2.log
- E:\Bin\project\SAMT\artifacts\reports\.tmp-smoke-run-3.log
- E:\Bin\project\SAMT\artifacts\reports\ci-validation-report.md
- E:\Bin\project\SAMT\artifacts\reports\contract-final.log
- E:\Bin\project\SAMT\artifacts\reports\contract-fix-report.json
- E:\Bin\project\SAMT\artifacts\reports\contract-fix-report.md
- E:\Bin\project\SAMT\artifacts\reports\full-auth-users-permissions.log
- E:\Bin\project\SAMT\artifacts\reports\full-regression.log
- E:\Bin\project\SAMT\artifacts\reports\full-tests-all.log
- E:\Bin\project\SAMT\artifacts\reports\performance-report.md
- E:\Bin\project\SAMT\artifacts\reports\permissions-run.log
- E:\Bin\project\SAMT\artifacts\reports\regression-current.log
- E:\Bin\project\SAMT\artifacts\reports\regression-final.log
- E:\Bin\project\SAMT\artifacts\reports\regression-latest.log
- E:\Bin\project\SAMT\artifacts\reports\regression-run.log
- E:\Bin\project\SAMT\artifacts\reports\reliability-report.md
- E:\Bin\project\SAMT\artifacts\reports\tests-all-final.log
- E:\Bin\project\SAMT\node_modules\@cloudamqp\amqp-client\dist
- E:\Bin\project\SAMT\node_modules\@faker-js\faker\dist
- E:\Bin\project\SAMT\node_modules\@grpc\grpc-js\build
- E:\Bin\project\SAMT\node_modules\@grpc\grpc-js\node_modules\@grpc\proto-loader\build
- E:\Bin\project\SAMT\node_modules\@grpc\proto-loader\build
- E:\Bin\project\SAMT\node_modules\@inquirer\ansi\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\checkbox\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\confirm\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\core\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\editor\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\expand\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\external-editor\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\figures\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\input\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\number\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\password\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\prompts\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\rawlist\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\search\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\select\dist
- E:\Bin\project\SAMT\node_modules\@inquirer\type\dist
- E:\Bin\project\SAMT\node_modules\@js-sdsl\ordered-map\dist
- E:\Bin\project\SAMT\node_modules\@sindresorhus\is\dist
- E:\Bin\project\SAMT\node_modules\@szmarczak\http-timer\dist
- E:\Bin\project\SAMT\node_modules\abort-controller\dist
- E:\Bin\project\SAMT\node_modules\agent-base\dist
- E:\Bin\project\SAMT\node_modules\ajv\dist
- E:\Bin\project\SAMT\node_modules\ajv-draft-04\dist
- E:\Bin\project\SAMT\node_modules\ajv-formats\dist
- E:\Bin\project\SAMT\node_modules\async\dist
- E:\Bin\project\SAMT\node_modules\broker-factory\build
- E:\Bin\project\SAMT\node_modules\charset\coverage
- E:\Bin\project\SAMT\node_modules\cliui\build
- E:\Bin\project\SAMT\node_modules\dayjs-plugin-utc\dist
- E:\Bin\project\SAMT\node_modules\defer-to-connect\dist
- E:\Bin\project\SAMT\node_modules\es6-promise\dist
- E:\Bin\project\SAMT\node_modules\escalade\dist
- E:\Bin\project\SAMT\node_modules\event-target-shim\dist
- E:\Bin\project\SAMT\node_modules\fast-unique-numbers\build
- E:\Bin\project\SAMT\node_modules\filesize\dist
- E:\Bin\project\SAMT\node_modules\got\dist
- E:\Bin\project\SAMT\node_modules\graphlib\dist
- E:\Bin\project\SAMT\node_modules\grpc-js-reflection-client\dist
- E:\Bin\project\SAMT\node_modules\hookpoint\dist
- E:\Bin\project\SAMT\node_modules\http-proxy-agent\dist
- E:\Bin\project\SAMT\node_modules\https-proxy-agent\dist
- E:\Bin\project\SAMT\node_modules\httpyac\dist
- E:\Bin\project\SAMT\node_modules\human-signals\build
- E:\Bin\project\SAMT\node_modules\inquirer\dist
- E:\Bin\project\SAMT\node_modules\ip-address\dist
- E:\Bin\project\SAMT\node_modules\json-pointer\coverage
- E:\Bin\project\SAMT\node_modules\js-sdsl\dist
- E:\Bin\project\SAMT\node_modules\js-yaml\dist
- E:\Bin\project\SAMT\node_modules\lru-cache\dist
- E:\Bin\project\SAMT\node_modules\mqtt\build
- E:\Bin\project\SAMT\node_modules\mqtt\dist
- E:\Bin\project\SAMT\node_modules\neotraverse\dist
- E:\Bin\project\SAMT\node_modules\object-hash\dist
- E:\Bin\project\SAMT\node_modules\openapi-to-postmanv2\node_modules\js-yaml\dist
- E:\Bin\project\SAMT\node_modules\postman-collection\node_modules\uuid\dist
- E:\Bin\project\SAMT\node_modules\protobufjs\dist
- E:\Bin\project\SAMT\node_modules\rxjs\dist
- E:\Bin\project\SAMT\node_modules\signal-exit\dist
- E:\Bin\project\SAMT\node_modules\smart-buffer\build
- E:\Bin\project\SAMT\node_modules\socks\build
- E:\Bin\project\SAMT\node_modules\socks-proxy-agent\dist
- E:\Bin\project\SAMT\node_modules\tldts\dist
- E:\Bin\project\SAMT\node_modules\tldts-core\dist
- E:\Bin\project\SAMT\node_modules\tough-cookie\dist
- E:\Bin\project\SAMT\node_modules\uuid\dist
- E:\Bin\project\SAMT\node_modules\worker-factory\build
- E:\Bin\project\SAMT\node_modules\worker-timers\build
- E:\Bin\project\SAMT\node_modules\worker-timers-broker\build
- E:\Bin\project\SAMT\node_modules\worker-timers-worker\build
- E:\Bin\project\SAMT\node_modules\xmldom-format\dist
- E:\Bin\project\SAMT\node_modules\y18n\build
- E:\Bin\project\SAMT\node_modules\yaml\browser\dist
- E:\Bin\project\SAMT\node_modules\yaml\dist
- E:\Bin\project\SAMT\node_modules\yargs\build
- E:\Bin\project\SAMT\node_modules\yargs-parser\build
- E:\Bin\project\SAMT\services\analysis-service\target
- E:\Bin\project\SAMT\services\api-gateway\.github\java-upgrade\20260228160359\logs\0.log
- E:\Bin\project\SAMT\services\api-gateway\.github\java-upgrade\20260228161238\logs\0.log
- E:\Bin\project\SAMT\services\api-gateway\.github\java-upgrade\20260228161400\logs\0.log
- E:\Bin\project\SAMT\services\api-gateway\.github\java-upgrade\20260228161551\logs\0.log
- E:\Bin\project\SAMT\services\api-gateway\.github\java-upgrade\20260228161845\logs\0.log
- E:\Bin\project\SAMT\services\api-gateway\target
- E:\Bin\project\SAMT\services\identity-service\.github\java-upgrade\20260228160730\logs\0.log
- E:\Bin\project\SAMT\services\identity-service\.github\java-upgrade\20260228161058\logs\0.log
- E:\Bin\project\SAMT\services\identity-service\target
- E:\Bin\project\SAMT\services\notification-service\target
- E:\Bin\project\SAMT\services\project-config-service\target
- E:\Bin\project\SAMT\services\report-service\target
- E:\Bin\project\SAMT\services\sync-service\target
- E:\Bin\project\SAMT\services\user-group-service\target
- E:\Bin\project\SAMT\shared\common-contracts\target
- E:\Bin\project\SAMT\shared\common-events\target

## 2. Backend build status

Command executed:

```bash
mvn clean verify
```

Result: **BUILD SUCCESS**

Reactor summary:
- SAMT Microservices Parent: SUCCESS
- common-contracts: SUCCESS
- analysis-service: SUCCESS
- api-gateway: SUCCESS
- common-events: SUCCESS
- identity-service: SUCCESS
- notification-service: SUCCESS
- report-service: SUCCESS
- sync-service: SUCCESS
- user-group-service: SUCCESS
- project-config-service: SUCCESS

Build time: `01:51 min`

Notes:
- Build log contains Mockito/JDK dynamic agent warning; not a test/build failure.

## 3. Unused backend code

| File | Module | Reason | Confidence |
|---|---|---|---|
| `services/notification-service/` | notification-service | Service has no active controller endpoint (`@RestController`/route methods) and appears to be config-only scaffold. | Medium |
| `services/api-gateway/src/main/java/com/example/gateway/config/GatewayRoutesConfig.java` | api-gateway | Routes include notification-service targets, but target service currently has no active endpoint surface. | Medium |
| `services/notification-service/src/main/java/com/example/notificationservice/security/SecurityConfig.java` | notification-service | Security config is loaded but currently protects no active API endpoint in this service. | Medium |
| `services/notification-service/src/main/java/com/example/notificationservice/config/ProdSecretsProperties.java` | notification-service | Properties class detected without clear runtime business usage in current code path. | Low |

Classification:
- **SAFE DELETE**: none
- **REVIEW REQUIRED**: all entries above (do not remove business code without owner confirmation)

## 4. Backend repo size reduction

- Before cleanup: **1,667,973,265 bytes** (~1590.70 MiB)
- After cleanup: **687,791,204 bytes** (~655.93 MiB)
- Reduced: **980,182,061 bytes** (~934.77 MiB, ~58.76%)

## Safety checks

- Frontend (`FE-SAMT`) was not modified by this cleanup operation.
- No API contract changes were applied.
- No database schema changes were applied.
- No business logic refactor was applied.
