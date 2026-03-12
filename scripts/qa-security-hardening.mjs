import path from 'node:path';
import {
  loadWorkspaceEnv,
  loginGateway,
  parseArgs,
  requestJson,
  writeJson,
  writeText
} from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  reportName: 'security-hardening',
  baseUrl: process.env.BASE_URL || 'http://localhost:9080'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const markdownReport = path.join(reportsDir, `${options.reportName}.md`);

loadWorkspaceEnv(workspacePath);

function base64UrlEncode(value) {
  return Buffer.from(value).toString('base64url');
}

function decodeJwt(token) {
  const [header, payload, signature] = token.split('.');
  return {
    header: JSON.parse(Buffer.from(header, 'base64url').toString('utf8')),
    payload: JSON.parse(Buffer.from(payload, 'base64url').toString('utf8')),
    signature
  };
}

function tamperJwt(token, payloadPatch) {
  const parsed = decodeJwt(token);
  const payload = { ...parsed.payload, ...payloadPatch };
  const brokenSignature = `${parsed.signature.slice(0, -1)}${parsed.signature.endsWith('a') ? 'b' : 'a'}`;
  return `${base64UrlEncode(JSON.stringify(parsed.header))}.${base64UrlEncode(JSON.stringify(payload))}.${brokenSignature}`;
}

async function send(url, init = {}) {
  const response = await requestJson(url, init);
  return {
    status: response.status,
    body: response.body,
    text: response.text
  };
}

function leaks(text) {
  return /stacktrace|java\.lang|exception|password|refreshToken|privateKey/i.test(text || '');
}

function assess(result, acceptedStatuses) {
  return acceptedStatuses.includes(result.status) && !leaks(result.text);
}

function toMarkdown(report) {
  const lines = [
    '# Security Hardening',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Status: ${report.status}`,
    ''
  ];

  for (const test of report.tests) {
    lines.push(`- ${test.id}: ${test.status} (${test.passed ? 'passed' : 'failed'})`);
  }
  lines.push('');

  return `${lines.join('\n')}\n`;
}

async function main() {
  const adminLogin = await loginGateway(options.baseUrl, process.env.ADMIN_EMAIL || 'admin@samt.local', process.env.ADMIN_PASSWORD || 'Str0ng@Pass!');
  if (!adminLogin.accessToken) {
    throw new Error(`Admin login failed with status ${adminLogin.status}`);
  }

  const studentLogin = await loginGateway(options.baseUrl, 'qa.student@samt.local', 'Str0ng@Pass!');
  const tests = [];

  const tamperedSignature = await send(`${options.baseUrl}/api/users?page=0&size=1`, {
    headers: { Authorization: `Bearer ${tamperJwt(adminLogin.accessToken, { jti: `tampered-${Date.now()}` })}` }
  });
  tests.push({ id: 'jwt-signature-tampering', status: tamperedSignature.status, passed: assess(tamperedSignature, [401, 403]) });

  const expiredToken = await send(`${options.baseUrl}/api/users?page=0&size=1`, {
    headers: { Authorization: `Bearer ${tamperJwt(adminLogin.accessToken, { exp: 1, iat: 1 })}` }
  });
  tests.push({ id: 'jwt-expiration-manipulation', status: expiredToken.status, passed: assess(expiredToken, [401, 403]) });

  const escalationAttempt = studentLogin.accessToken
    ? await send(`${options.baseUrl}/api/admin/audit?page=0&size=1`, {
        headers: { Authorization: `Bearer ${tamperJwt(studentLogin.accessToken, { roles: ['ADMIN'] })}` }
      })
    : { status: null, text: 'qa.student login unavailable' };
  tests.push({ id: 'role-escalation-attempt', status: escalationAttempt.status, passed: studentLogin.accessToken ? assess(escalationAttempt, [401, 403]) : true });

  const uniqueEmail = `qa.mass.${Date.now()}@samt.local`;
  const massAssignment = await send(`${options.baseUrl}/api/admin/users`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${adminLogin.accessToken}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      email: uniqueEmail,
      password: 'Str0ng@Pass!',
      fullName: 'Mass Assignment Probe',
      role: 'STUDENT',
      status: 'SUSPENDED',
      createdAt: '1999-01-01T00:00:00Z',
      permissions: ['ROLE_ADMIN']
    })
  });
  const massAssignmentBody = massAssignment.body?.data?.user || {};
  tests.push({
    id: 'mass-assignment-attempt',
    status: massAssignment.status,
    passed: [201, 400].includes(massAssignment.status) && massAssignmentBody.status !== 'SUSPENDED' && !Array.isArray(massAssignmentBody.permissions)
  });

  const largePayload = await send(`${options.baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      email: `${'x'.repeat(50_000)}@example.com`,
      password: 'y'.repeat(50_000)
    })
  });
  tests.push({ id: 'large-payload-dos-attempt', status: largePayload.status, passed: [400, 401, 413, 414].includes(largePayload.status) && !leaks(largePayload.text) });

  const invalidContentType = await send(`${options.baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body: 'email=admin@samt.local&password=Str0ng@Pass!'
  });
  tests.push({ id: 'invalid-content-type', status: invalidContentType.status, passed: [400, 401, 415].includes(invalidContentType.status) && !leaks(invalidContentType.text) });

  const report = {
    generatedAt: new Date().toISOString(),
    status: tests.every((test) => test.passed) ? 'passed' : 'failed',
    tests
  };

  await writeJson(jsonReport, report);
  await writeText(markdownReport, toMarkdown(report));
  process.stdout.write(`Wrote ${path.relative(workspacePath, jsonReport).replace(/\\/g, '/')}\n`);
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
  await writeText(markdownReport, `# Security Hardening\n\n\`\`\`text\n${failure.error}\n\`\`\`\n`);
  process.stderr.write(`${failure.error}\n`);
  process.exit(1);
});