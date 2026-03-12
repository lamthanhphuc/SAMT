import { spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import dotenv from 'dotenv';

export function toPosix(value) {
  return value.replace(/\\/g, '/');
}

export function parseArgs(argv, defaults = {}) {
  const options = { ...defaults };

  for (const arg of argv) {
    if (!arg.startsWith('--')) {
      continue;
    }

    const separatorIndex = arg.indexOf('=');
    if (separatorIndex === -1) {
      options[arg.slice(2)] = true;
      continue;
    }

    const key = arg.slice(2, separatorIndex);
    const rawValue = arg.slice(separatorIndex + 1);
    options[key] = rawValue;
  }

  return options;
}

export function loadWorkspaceEnv(workspacePath = process.cwd()) {
  dotenv.config({ path: path.join(workspacePath, '.env') });
}

export function ensureNumber(value, fallback) {
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : fallback;
}

export function safeJsonParse(text) {
  try {
    return JSON.parse(text);
  } catch {
    return null;
  }
}

export async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

export async function writeJson(filePath, data) {
  await ensureDir(path.dirname(filePath));
  await fs.writeFile(filePath, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}

export async function writeText(filePath, text) {
  await ensureDir(path.dirname(filePath));
  await fs.writeFile(filePath, text, 'utf8');
}

export function runCommand(command, cwd, env = {}) {
  const start = Date.now();
  const result = spawnSync(command, {
    cwd,
    shell: process.platform === 'win32' ? 'powershell.exe' : true,
    encoding: 'utf8',
    env: { ...process.env, ...env }
  });

  return {
    status: result.status ?? 1,
    stdout: result.stdout || '',
    stderr: result.stderr || '',
    output: `${result.stdout || ''}${result.stderr || ''}`,
    durationMs: Date.now() - start,
    error: result.error?.message || null
  };
}

export async function requestJson(url, options = {}) {
  const response = await fetch(url, options);
  const text = await response.text();
  const body = safeJsonParse(text) ?? text;

  return {
    response,
    status: response.status,
    headers: Object.fromEntries(response.headers.entries()),
    text,
    body
  };
}

export function unwrapApiData(body) {
  if (!body || typeof body !== 'object') {
    return body;
  }

  return body.data ?? body;
}

export function extractAccessToken(body) {
  const data = unwrapApiData(body);
  if (!data || typeof data !== 'object') {
    return null;
  }

  return data.accessToken ?? body.accessToken ?? null;
}

export async function loginGateway(baseUrl, email, password) {
  const result = await requestJson(`${baseUrl}/api/auth/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password })
  });

  const accessToken = extractAccessToken(result.body);
  return { ...result, accessToken };
}

export function authHeaders(token) {
  return token
    ? {
        Authorization: `Bearer ${token}`
      }
    : {};
}

export function maskSecret(value, visible = 4) {
  if (!value) {
    return null;
  }

  if (value.length <= visible * 2) {
    return '*'.repeat(value.length);
  }

  return `${value.slice(0, visible)}${'*'.repeat(Math.max(value.length - visible * 2, 4))}${value.slice(-visible)}`;
}

export function findEnv(...keys) {
  for (const key of keys) {
    const value = process.env[key];
    if (value && String(value).trim()) {
      return String(value).trim();
    }
  }

  return null;
}

export function dockerNetworkExists(name) {
  const result = spawnSync('docker', ['network', 'inspect', name], {
    encoding: 'utf8',
    env: { ...process.env }
  });

  return !result.error && result.status === 0;
}

export function resolveDockerGatewayUrl() {
  const override = process.env.QA_DOCKER_BASE_URL || process.env.SCHEMATHESIS_BASE_URL;
  if (override) {
    return override;
  }

  const dockerNetwork = process.env.QA_DOCKER_NETWORK || process.env.SCHEMATHESIS_DOCKER_NETWORK || 'samt_samt-network';
  if (dockerNetworkExists(dockerNetwork)) {
    return 'http://api-gateway:8080';
  }

  return 'http://host.docker.internal:9080';
}

export function nowStamp() {
  return new Date().toISOString().replace(/[:.]/g, '-');
}

export function normalizeGithubRepoUrl(value) {
  if (!value) {
    return null;
  }

  if (/^https?:\/\//i.test(value)) {
    return value.replace(/\.git$/i, '');
  }

  const cleaned = value.replace(/^github\.com\//i, '').replace(/\.git$/i, '');
  if (!cleaned.includes('/')) {
    return null;
  }

  return `https://github.com/${cleaned}`;
}

export function resolveExternalIntegrationConfig() {
  const jiraHostUrl = findEnv('JIRA_BASE_URL', 'JIRA_HOST', 'JIRA_HOST_URL');
  const jiraEmail = findEnv('JIRA_EMAIL');
  const jiraApiToken = findEnv('JIRA_API_TOKEN', 'JIRA_TOKEN');
  const githubRepoUrl = normalizeGithubRepoUrl(findEnv('GITHUB_REPO_URL', 'GITHUB_REPO'));
  const githubToken = findEnv('GITHUB_TOKEN', 'GITHUB_PAT');

  return {
    jiraHostUrl,
    jiraEmail,
    jiraApiToken,
    githubRepoUrl,
    githubToken,
    masked: {
      jiraHostUrl,
      jiraEmail,
      jiraApiToken: maskSecret(jiraApiToken),
      githubRepoUrl,
      githubToken: maskSecret(githubToken)
    },
    complete: Boolean(jiraHostUrl && jiraEmail && jiraApiToken && githubRepoUrl && githubToken)
  };
}

export function htmlEscape(value) {
  return String(value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

export function toReportLink(filePath, workspacePath) {
  return toPosix(path.relative(workspacePath, filePath) || filePath);
}
