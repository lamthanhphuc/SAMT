import fs from 'node:fs/promises';
import path from 'node:path';
import {
  parseArgs,
  requestJson,
  toPosix,
  writeJson,
  writeText
} from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  baseUrl: process.env.BASE_URL || 'http://localhost:9080',
  inventory: 'qa/endpoints.json',
  reportName: 'security-probe'
});

const workspacePath = path.resolve(options.workspace);
const inventoryPath = path.resolve(workspacePath, options.inventory);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');

function samplePath(pathKey) {
  return pathKey.replace(/\{([^}]+)\}/g, (_match, paramName) => {
    const lower = paramName.toLowerCase();
    if (lower.includes('uuid') || (lower === 'id' && pathKey.includes('/project-configs/'))) {
      return '00000000-0000-0000-0000-000000000001';
    }
    if (lower.includes('service')) {
      return 'identity';
    }
    return '1';
  });
}

function probeBody(endpoint) {
  if (endpoint.method === 'POST' || endpoint.method === 'PUT' || endpoint.method === 'PATCH') {
    return JSON.stringify({});
  }
  return undefined;
}

function isSafeProbeCandidate(endpoint) {
  if (endpoint.path.startsWith('/internal/')) {
    return false;
  }
  if (endpoint.path.startsWith('/api/admin/audit')) {
    return false;
  }
  return true;
}

function toMarkdown(report) {
  const lines = [
    '# Security Probe',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Base URL: ${report.baseUrl}`,
    `- Public checks: ${report.summary.publicChecks}`,
    `- Protected checks: ${report.summary.protectedChecks}`,
    `- Regressions: ${report.summary.regressions}`,
    ''
  ];

  if (report.regressions.length > 0) {
    lines.push('## Regressions');
    lines.push('');
    for (const regression of report.regressions) {
      lines.push(`- ${regression.method} ${regression.path}: expected ${regression.expectation}, got ${regression.status}`);
    }
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function main() {
  const inventory = JSON.parse(await fs.readFile(inventoryPath, 'utf8'));
  const endpoints = inventory.endpoints.filter(isSafeProbeCandidate);
  const publicEndpoints = endpoints.filter((endpoint) => !endpoint.requiresAuth).slice(0, 12);
  const protectedEndpoints = endpoints.filter((endpoint) => endpoint.requiresAuth).slice(0, 20);
  const checks = [];

  for (const endpoint of [...publicEndpoints, ...protectedEndpoints]) {
    const url = `${options.baseUrl}${samplePath(endpoint.path)}`;
    const result = await requestJson(url, {
      method: endpoint.method,
      headers: endpoint.method === 'POST' || endpoint.method === 'PUT' || endpoint.method === 'PATCH'
        ? { 'Content-Type': 'application/json' }
        : {},
      body: probeBody(endpoint)
    });

    const expectation = endpoint.requiresAuth ? '401/403 without credentials' : 'not 401/403 without credentials';
    const passed = endpoint.requiresAuth
      ? [401, 403].includes(result.status)
      : ![401, 403].includes(result.status);

    checks.push({
      method: endpoint.method,
      path: endpoint.path,
      requiresAuth: endpoint.requiresAuth,
      expectation,
      status: result.status,
      passed
    });
  }

  const regressions = checks.filter((check) => !check.passed);
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: options.baseUrl,
    summary: {
      publicChecks: checks.filter((check) => !check.requiresAuth).length,
      protectedChecks: checks.filter((check) => check.requiresAuth).length,
      regressions: regressions.length
    },
    checks,
    regressions
  };

  const jsonPath = path.join(reportsDir, `${options.reportName}.json`);
  const markdownPath = path.join(reportsDir, `${options.reportName}.md`);
  await writeJson(jsonPath, report);
  await writeText(markdownPath, toMarkdown(report));

  process.stdout.write(`Wrote ${toPosix(path.relative(workspacePath, jsonPath))}\n`);
  if (regressions.length > 0) {
    process.exit(1);
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message || String(error)}\n`);
  process.exit(1);
});
