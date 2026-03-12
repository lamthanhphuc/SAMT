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
  reportName: 'chaos',
  baseUrl: process.env.BASE_URL || 'http://localhost:9080',
  timeoutMs: String(process.env.QA_CHAOS_TIMEOUT_MS || 7000)
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const markdownReport = path.join(reportsDir, `${options.reportName}.md`);
const timeoutMs = Number(options.timeoutMs);

loadWorkspaceEnv(workspacePath);

function dockerCompose(command) {
  return runCommand(`docker compose ${command}`, workspacePath);
}

function serviceContainerId(serviceName) {
  const result = dockerCompose(`ps -q ${serviceName}`);
  return result.status === 0 ? String(result.stdout || '').trim() || null : null;
}

function dockerExec(containerId, command) {
  return runCommand(`docker exec ${containerId} sh -lc \"${command}\"`, workspacePath);
}

async function probe(method, url, token, body, headers = {}) {
  const start = Date.now();
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);

  try {
    const result = await requestJson(url, {
      method,
      signal: controller.signal,
      headers: {
        ...headers,
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
  } catch (error) {
    return {
      status: null,
      durationMs: Date.now() - start,
      error: error.message
    };
  } finally {
    clearTimeout(timer);
  }
}

async function waitForRecovery(url, token, acceptedStatuses) {
  const deadline = Date.now() + 45_000;
  while (Date.now() < deadline) {
    const result = await probe('GET', url, token);
    if (acceptedStatuses.includes(result.status)) {
      return result;
    }
    await new Promise((resolve) => setTimeout(resolve, 2_000));
  }
  return null;
}

function supportsTc(containerId) {
  if (!containerId) {
    return false;
  }
  return dockerExec(containerId, 'command -v tc').status === 0;
}

async function runStopExperiment(token) {
  const endpoint = `${options.baseUrl}/api/groups?page=0&size=1`;
  const before = await probe('GET', endpoint, token);
  const stopped = dockerCompose('stop user-group-service');
  const during = await probe('GET', endpoint, token);
  const started = dockerCompose('start user-group-service');
  const recovered = await waitForRecovery(endpoint, token, [200]);

  return {
    id: 'stop-user-group-service',
    method: 'docker stop/start',
    target: 'user-group-service',
    before,
    during,
    recovered,
    actions: [stopped.status, started.status],
    passed: before.status === 200 && Boolean(recovered) && (during.status === null || during.status >= 500 || during.status === 503 || during.status === 502)
  };
}

async function runRestartExperiment(token) {
  const endpoint = `${options.baseUrl}/api/users?page=0&size=1`;
  const before = await probe('GET', endpoint, token);
  const restarting = dockerCompose('restart identity-service');
  const during = await probe('GET', endpoint, token);
  const recovered = await waitForRecovery(endpoint, token, [200]);

  return {
    id: 'restart-identity-service',
    method: 'docker restart',
    target: 'identity-service',
    before,
    during,
    recovered,
    actions: [restarting.status],
    passed: before.status === 200 && Boolean(recovered) && during.durationMs < timeoutMs + 2_000
  };
}

async function runNetemExperiment(token, mode, tcCommand) {
  const serviceName = 'project-config-service';
  const containerId = serviceContainerId(serviceName);
  const endpoint = `${options.baseUrl}/api/project-configs?page=0&size=1`;
  if (!supportsTc(containerId)) {
    return {
      id: `${mode}-${serviceName}`,
      method: 'tc netem',
      target: serviceName,
      skipped: true,
      reason: 'tc is not available in target container'
    };
  }

  const before = await probe('GET', endpoint, token);
  const addResult = dockerExec(containerId, tcCommand);
  const during = await probe('GET', endpoint, token);
  dockerExec(containerId, 'tc qdisc del dev eth0 root >/dev/null 2>&1 || true');
  const recovered = await waitForRecovery(endpoint, token, [200, 404]);

  return {
    id: `${mode}-${serviceName}`,
    method: 'tc netem',
    target: serviceName,
    before,
    during,
    recovered,
    actions: [addResult.status],
    passed: addResult.status === 0 && Boolean(recovered)
  };
}

function toMarkdown(report) {
  const lines = [
    '# Chaos Report',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Status: ${report.status}`,
    ''
  ];

  for (const experiment of report.experiments) {
    lines.push(`## ${experiment.id}`);
    lines.push('');
    lines.push(`- Method: ${experiment.method}`);
    lines.push(`- Target: ${experiment.target}`);
    lines.push(`- Passed: ${experiment.passed ? 'yes' : experiment.skipped ? 'skipped' : 'no'}`);
    if (experiment.reason) {
      lines.push(`- Reason: ${experiment.reason}`);
    }
    if (experiment.before) {
      lines.push(`- Before: ${experiment.before.status ?? 'timeout'} in ${experiment.before.durationMs} ms`);
    }
    if (experiment.during) {
      lines.push(`- During: ${experiment.during.status ?? 'timeout'} in ${experiment.during.durationMs} ms`);
    }
    if (experiment.recovered) {
      lines.push(`- Recovered: ${experiment.recovered.status} in ${experiment.recovered.durationMs} ms`);
    }
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function main() {
  const login = await loginGateway(options.baseUrl, process.env.ADMIN_EMAIL || 'admin@samt.local', process.env.ADMIN_PASSWORD || 'Str0ng@Pass!');
  if (!login.accessToken) {
    throw new Error(`Chaos login failed with status ${login.status}`);
  }

  const experiments = [];
  experiments.push(await runStopExperiment(login.accessToken));
  experiments.push(await runRestartExperiment(login.accessToken));
  experiments.push(await runNetemExperiment(login.accessToken, 'latency', 'tc qdisc add dev eth0 root netem delay 350ms 100ms'));
  experiments.push(await runNetemExperiment(login.accessToken, 'packet-loss', 'tc qdisc add dev eth0 root netem loss 35%'));

  const failures = experiments.filter((experiment) => !experiment.skipped && !experiment.passed);
  const report = {
    generatedAt: new Date().toISOString(),
    status: failures.length > 0 ? 'failed' : 'passed',
    experiments
  };

  await writeJson(jsonReport, report);
  await writeText(markdownReport, toMarkdown(report));
  process.stdout.write(`Wrote ${toPosix(path.relative(workspacePath, jsonReport))}\n`);

  if (failures.length > 0) {
    process.exit(1);
  }
}

main().catch(async (error) => {
  const report = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(jsonReport, report);
  await writeText(markdownReport, `# Chaos Report\n\n\`\`\`text\n${report.error}\n\`\`\`\n`);
  process.stderr.write(`${report.error}\n`);
  process.exit(1);
});