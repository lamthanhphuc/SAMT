import path from 'node:path';
import {
  loadWorkspaceEnv,
  loginGateway,
  parseArgs,
  requestJson,
  runCommand,
  toPosix,
  writeJson,
  writeText
} from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  reportName: 'communication',
  baseUrl: process.env.BASE_URL || 'http://localhost:9080'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const markdownReport = path.join(reportsDir, `${options.reportName}.md`);

loadWorkspaceEnv(workspacePath);

function dockerCompose(command) {
  return runCommand(`docker compose ${command}`, workspacePath);
}

async function timedRequest(url, token, method = 'GET', body = null) {
  const start = Date.now();
  const result = await requestJson(url, {
    method,
    headers: {
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(body ? { 'Content-Type': 'application/json' } : {})
    },
    body: body ? JSON.stringify(body) : undefined
  });
  return {
    status: result.status,
    durationMs: Date.now() - start,
    body: result.body
  };
}

function leaksSensitiveData(body) {
  const text = typeof body === 'string' ? body : JSON.stringify(body || {});
  return /exception|stacktrace|java\.lang|org\.springframework/i.test(text);
}

async function waitForStatus(url, token, acceptedStatuses) {
  const deadline = Date.now() + 45_000;
  while (Date.now() < deadline) {
    const result = await timedRequest(url, token);
    if (acceptedStatuses.includes(result.status)) {
      return result;
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  return null;
}

function routeExpectations(baseUrl) {
  return [
    { name: 'gateway-public-auth', url: `${baseUrl}/api/auth/login`, method: 'POST', public: true, expected: [200, 401], body: { email: 'admin@samt.local', password: 'Str0ng@Pass!' } },
    { name: 'identity-user-list', url: `${baseUrl}/api/users?page=0&size=1`, expected: [200] },
    { name: 'user-group-list', url: `${baseUrl}/api/groups?page=0&size=1`, expected: [200] },
    { name: 'project-config-by-group', url: `${baseUrl}/api/project-configs/group/1`, expected: [200, 404] }
  ];
}

function toMarkdown(report) {
  const lines = [
    '# Communication Report',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Status: ${report.status}`,
    ''
  ];

  lines.push('## Routing');
  lines.push('');
  for (const route of report.routingChecks) {
    lines.push(`- ${route.name}: ${route.status} in ${route.durationMs} ms`);
  }
  lines.push('');

  lines.push('## Failure Recovery');
  lines.push('');
  lines.push(`- During outage: ${report.failureExperiment.during.status ?? 'timeout'} in ${report.failureExperiment.during.durationMs} ms`);
  lines.push(`- Recovered: ${report.failureExperiment.recovered?.status ?? 'no'}${report.failureExperiment.recovered ? ` in ${report.failureExperiment.recovered.durationMs} ms` : ''}`);
  lines.push('');

  return `${lines.join('\n')}\n`;
}

async function main() {
  const login = await loginGateway(options.baseUrl, process.env.ADMIN_EMAIL || 'admin@samt.local', process.env.ADMIN_PASSWORD || 'Str0ng@Pass!');
  if (!login.accessToken) {
    throw new Error(`Communication login failed with status ${login.status}`);
  }

  const routingChecks = [];
  for (const route of routeExpectations(options.baseUrl)) {
    const result = route.public
      ? await timedRequest(route.url, null, route.method, route.body)
      : await timedRequest(route.url, login.accessToken, route.method, route.body);
    routingChecks.push({
      name: route.name,
      ...result,
      passed: route.expected.includes(result.status)
    });
  }

  const failingService = 'project-config-service';
  const failingUrl = `${options.baseUrl}/api/project-configs/group/1`;
  dockerCompose(`stop ${failingService}`);
  const during = await timedRequest(failingUrl, login.accessToken);
  dockerCompose(`start ${failingService}`);
  const recovered = await waitForStatus(failingUrl, login.accessToken, [200, 404]);

  const report = {
    generatedAt: new Date().toISOString(),
    status: 'passed',
    routingChecks,
    staticEvidence: {
      resilience4jConfigured: runCommand('rg -n -i "resilience4j|CircuitBreaker|retry|fallback" api-gateway project-config-service analysis-service', workspacePath).status === 0,
      internalJwtConfigured: runCommand('rg -n -i "internal-jwks|InternalJwt|GrpcJwt|jwk|jwt" api-gateway identity-service user-group-service project-config-service analysis-service', workspacePath).status === 0
    },
    failureExperiment: {
      target: failingService,
      during,
      recovered,
      timeoutHandled: during.durationMs < 8_000,
      gracefulError: (during.status === null || during.status >= 500 || during.status === 503 || during.status === 502) && !leaksSensitiveData(during.body),
      recoveredCleanly: Boolean(recovered)
    }
  };

  if (routingChecks.some((item) => !item.passed) || !report.failureExperiment.timeoutHandled || !report.failureExperiment.gracefulError || !report.failureExperiment.recoveredCleanly) {
    report.status = 'failed';
  }

  await writeJson(jsonReport, report);
  await writeText(markdownReport, toMarkdown(report));
  process.stdout.write(`Wrote ${toPosix(path.relative(workspacePath, jsonReport))}\n`);
  if (report.status !== 'passed') {
    process.exit(1);
  }
}

main().catch(async (error) => {
  const failure = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(jsonReport, failure);
  await writeText(markdownReport, `# Communication Report\n\n\`\`\`text\n${failure.error}\n\`\`\`\n`);
  process.stderr.write(`${failure.error}\n`);
  process.exit(1);
});