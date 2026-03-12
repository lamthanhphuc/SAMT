import path from 'node:path';
import {
  authHeaders,
  loadWorkspaceEnv,
  loginGateway,
  maskSecret,
  nowStamp,
  parseArgs,
  requestJson,
  resolveExternalIntegrationConfig,
  unwrapApiData,
  writeJson,
  writeText
} from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  baseUrl: process.env.BASE_URL || 'http://localhost:9080',
  reportName: 'external-integrations'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');

function jsonHeaders(token) {
  return {
    ...authHeaders(token),
    'Content-Type': 'application/json'
  };
}

async function apiCall(baseUrl, token, method, pathKey, payload) {
  return requestJson(`${baseUrl}${pathKey}`, {
    method,
    headers: payload === undefined ? authHeaders(token) : jsonHeaders(token),
    body: payload === undefined ? undefined : JSON.stringify(payload)
  });
}

async function cleanupResource(baseUrl, token, method, pathKey, cleanupActions) {
  const result = await apiCall(baseUrl, token, method, pathKey);
  cleanupActions.push({ method, path: pathKey, status: result.status });
}

function markdown(report) {
  const lines = [
    '# External Integration Verification',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Status: ${report.status}`,
    `- Base URL: ${report.baseUrl}`,
    ''
  ];

  lines.push('## Environment');
  lines.push('');
  lines.push(`- Jira host: ${report.environment.jiraHostUrl || 'missing'}`);
  lines.push(`- Jira email: ${report.environment.jiraEmail || 'missing'}`);
  lines.push(`- Jira token: ${report.environment.jiraApiToken || 'missing'}`);
  lines.push(`- GitHub repo: ${report.environment.githubRepoUrl || 'missing'}`);
  lines.push(`- GitHub token: ${report.environment.githubToken || 'missing'}`);
  lines.push('');

  if (report.steps.length > 0) {
    lines.push('## Steps');
    lines.push('');
    for (const step of report.steps) {
      lines.push(`- ${step.name}: ${step.status}`);
    }
    lines.push('');
  }

  if (report.verificationResponse) {
    lines.push('## Verification');
    lines.push('');
    lines.push('```json');
    lines.push(JSON.stringify(report.verificationResponse, null, 2));
    lines.push('```');
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function main() {
  loadWorkspaceEnv(workspacePath);
  const integrationConfig = resolveExternalIntegrationConfig();
  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: options.baseUrl,
    status: 'skipped',
    environment: {
      jiraHostUrl: integrationConfig.masked.jiraHostUrl,
      jiraEmail: integrationConfig.masked.jiraEmail,
      jiraApiToken: integrationConfig.masked.jiraApiToken,
      githubRepoUrl: integrationConfig.masked.githubRepoUrl,
      githubToken: integrationConfig.masked.githubToken
    },
    steps: [],
    created: {},
    cleanup: [],
    verificationResponse: null
  };

  if (!integrationConfig.complete) {
    const jsonPath = path.join(reportsDir, `${options.reportName}.json`);
    const markdownPath = path.join(reportsDir, `${options.reportName}.md`);
    await writeJson(jsonPath, report);
    await writeText(markdownPath, markdown(report));
    process.stdout.write('External integration verification skipped because sandbox credentials are incomplete.\n');
    return;
  }

  const adminEmail = process.env.ADMIN_EMAIL || 'admin@samt.local';
  const adminPassword = process.env.ADMIN_PASSWORD || 'Str0ng@Pass!';
  const loginResult = await loginGateway(options.baseUrl, adminEmail, adminPassword);
  report.steps.push({ name: 'admin-login', status: loginResult.status });

  if (!loginResult.accessToken) {
    report.status = 'failed';
    const jsonPath = path.join(reportsDir, `${options.reportName}.json`);
    const markdownPath = path.join(reportsDir, `${options.reportName}.md`);
    await writeJson(jsonPath, report);
    await writeText(markdownPath, markdown(report));
    process.exit(1);
  }

  const adminToken = loginResult.accessToken;
  const stamp = nowStamp().replace(/[^0-9]/g, '').slice(-10);
  const lecturerEmail = `qa.integration.${stamp}@samt.local`;
  const lecturerPassword = 'Str0ng@Pass!';

  const createLecturer = await apiCall(options.baseUrl, adminToken, 'POST', '/api/admin/users', {
    email: lecturerEmail,
    password: lecturerPassword,
    fullName: 'Qa Integration Lecturer',
    role: 'LECTURER'
  });
  report.steps.push({ name: 'create-lecturer', status: createLecturer.status });
  if (createLecturer.status !== 201) {
    report.status = 'failed';
  }

  const lecturerId = unwrapApiData(createLecturer.body)?.user?.id ?? unwrapApiData(createLecturer.body)?.userId ?? unwrapApiData(createLecturer.body)?.id ?? null;
  report.created.lecturerId = lecturerId;

  const semesterCode = `QA${stamp.slice(-4)}-S1`;
  const createSemester = await apiCall(options.baseUrl, adminToken, 'POST', '/api/semesters', {
    semesterCode,
    semesterName: `QA Integration ${stamp}`,
    startDate: '2026-01-15',
    endDate: '2026-05-30'
  });
  report.steps.push({ name: 'create-semester', status: createSemester.status });
  if (createSemester.status !== 201) {
    report.status = 'failed';
  }

  const semesterId = unwrapApiData(createSemester.body)?.id ?? null;
  report.created.semesterId = semesterId;

  const activateSemester = semesterId
    ? await apiCall(options.baseUrl, adminToken, 'PATCH', `/api/semesters/${semesterId}/activate`)
    : { status: 0 };
  report.steps.push({ name: 'activate-semester', status: activateSemester.status });
  if (semesterId && ![200, 204].includes(activateSemester.status)) {
    report.status = 'failed';
  }

  const createGroup = await apiCall(options.baseUrl, adminToken, 'POST', '/api/groups', {
    groupName: `SE${stamp.slice(-4)}-G1`,
    semesterId,
    lecturerId
  });
  report.steps.push({ name: 'create-group', status: createGroup.status });
  if (![200, 201].includes(createGroup.status)) {
    report.status = 'failed';
  }

  const groupId = unwrapApiData(createGroup.body)?.id ?? null;
  report.created.groupId = groupId;

  const createConfig = await apiCall(options.baseUrl, adminToken, 'POST', '/api/project-configs', {
    groupId,
    jiraHostUrl: integrationConfig.jiraHostUrl,
    jiraEmail: integrationConfig.jiraEmail,
    jiraApiToken: integrationConfig.jiraApiToken,
    githubRepoUrl: integrationConfig.githubRepoUrl,
    githubToken: integrationConfig.githubToken
  });
  report.steps.push({ name: 'create-project-config', status: createConfig.status });
  if (![200, 201].includes(createConfig.status)) {
    report.status = 'failed';
  }

  const projectConfigId = unwrapApiData(createConfig.body)?.id ?? null;
  report.created.projectConfigId = projectConfigId;

  if (projectConfigId) {
    const verifyConfig = await apiCall(options.baseUrl, adminToken, 'POST', `/api/project-configs/${projectConfigId}/verify`);
    report.steps.push({ name: 'verify-project-config', status: verifyConfig.status });
    report.verificationResponse = unwrapApiData(verifyConfig.body);
    report.status = verifyConfig.status === 200 && report.status !== 'failed' ? 'passed' : 'failed';
  } else {
    report.status = 'failed';
  }

  if (projectConfigId) {
    await cleanupResource(options.baseUrl, adminToken, 'DELETE', `/api/project-configs/${projectConfigId}`, report.cleanup);
  }
  if (groupId) {
    await cleanupResource(options.baseUrl, adminToken, 'DELETE', `/api/groups/${groupId}`, report.cleanup);
  }
  if (lecturerId) {
    await cleanupResource(options.baseUrl, adminToken, 'DELETE', `/api/admin/users/${lecturerId}`, report.cleanup);
  }

  const jsonPath = path.join(reportsDir, `${options.reportName}.json`);
  const markdownPath = path.join(reportsDir, `${options.reportName}.md`);
  await writeJson(jsonPath, report);
  await writeText(markdownPath, markdown(report));

  if (report.status !== 'passed') {
    process.exit(1);
  }
}

main().catch(async (error) => {
  const report = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(path.join(reportsDir, `${options.reportName}.json`), report);
  await writeText(path.join(reportsDir, `${options.reportName}.md`), `# External Integration Verification\n\n\`\`\`text\n${report.error}\n\`\`\`\n`);
  process.stderr.write(`${report.error}\n`);
  process.exit(1);
});
