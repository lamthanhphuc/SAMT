import { readFileSync, existsSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { ensureSelfHealFiles, writeReport } from './self-heal-overrides.mjs';

const cwd = process.cwd();
const dockerEnv = { ...process.env };
delete dockerEnv.DOCKER_API_VERSION;

const dockerNetwork = process.env.SCHEMATHESIS_DOCKER_NETWORK ?? 'samt_samt-network';
const useComposeNetwork = !process.env.SCHEMATHESIS_BASE_URL && dockerNetworkExists(dockerNetwork);
const gatewayUrl = process.env.SCHEMATHESIS_BASE_URL ?? (useComposeNetwork ? 'http://api-gateway:8080' : 'http://host.docker.internal:9080');
const loginUrl = process.env.SCHEMATHESIS_LOGIN_URL ?? 'http://localhost:9080/api/auth/login';
const npxCommand = process.platform === 'win32' ? 'npx.cmd' : 'npx';

function dockerNetworkExists(name) {
  const result = spawnSync('docker', ['network', 'inspect', name], {
    encoding: 'utf8',
    env: dockerEnv
  });

  return !result.error && result.status === 0;
}

function parseArgs(argv) {
  const options = {
    mode: 'contract'
  };

  for (const arg of argv) {
    if (arg.startsWith('--mode=')) {
      options.mode = arg.slice('--mode='.length);
    }
  }

  return options;
}

function parseMetrics(output, mode, seed, junitPath, ndjsonPath) {
  const metrics = {
    mode,
    seed,
    generated: null,
    passed: null,
    skipped: null,
    failures: null,
    warnings: null,
    latencyBudgetMs: Number(process.env.SCHEMATHESIS_MAX_RESPONSE_TIME_MS ?? 800)
  };

  if (ndjsonPath && existsSync(ndjsonPath)) {
    const events = readFileSync(ndjsonPath, 'utf8')
      .split(/\r?\n/)
      .filter(Boolean)
      .map((line) => {
        try {
          return JSON.parse(line);
        } catch {
          return null;
        }
      })
      .filter(Boolean);

    const scenarioEvents = events
      .map((event) => event.ScenarioFinished)
      .filter(Boolean);

    if (scenarioEvents.length > 0) {
      metrics.generated = scenarioEvents.length;
      metrics.skipped = scenarioEvents.filter((event) => event.status === 'skip').length;
      metrics.failures = scenarioEvents.filter((event) => event.status === 'failure' || event.status === 'error').length;
      metrics.passed = scenarioEvents.filter((event) => event.status === 'success').length;
    }
  }

  if (junitPath && existsSync(junitPath)) {
    const junit = readFileSync(junitPath, 'utf8');
    const suiteMatch = junit.match(/<testsuite[^>]*tests="(\d+)"[^>]*failures="(\d+)"[^>]*errors="(\d+)"[^>]*skipped="(\d+)"/i);
    if (suiteMatch) {
      metrics.generated = Number(suiteMatch[1]);
      const failures = Number(suiteMatch[2]);
      const errors = Number(suiteMatch[3]);
      metrics.skipped = Number(suiteMatch[4]);
      metrics.failures = failures + errors;
      metrics.passed = Math.max(metrics.generated - metrics.failures - metrics.skipped, 0);
    }
  }

  const casesMatch = output.match(/Test cases:\s*([\d,]+) generated(?:,\s*([\d,]+) passed)?(?:,\s*([\d,]+) skipped)?/i);
  if (casesMatch && metrics.generated === null) {
    metrics.generated = Number(casesMatch[1].replace(/,/g, ''));
    metrics.passed = casesMatch[2] ? Number(casesMatch[2].replace(/,/g, '')) : null;
    metrics.skipped = casesMatch[3] ? Number(casesMatch[3].replace(/,/g, '')) : null;
  }

  const failureMatch = output.match(/=+\s*([\d,]+) failures?(?:,\s*([\d,]+) warnings?)?/i);
  if (failureMatch && metrics.failures === null) {
    metrics.failures = Number(failureMatch[1].replace(/,/g, ''));
    metrics.warnings = failureMatch[2] ? Number(failureMatch[2].replace(/,/g, '')) : 0;
  }

  const generatedMatch = output.match(/Test cases:\s*([\d,]+)\s+generated/i);
  if (generatedMatch) {
    metrics.generated = Number(generatedMatch[1].replace(/,/g, ''));
  }

  const skippedMatch = output.match(/,\s*([\d,]+)\s+skipped/i);
  if (skippedMatch) {
    metrics.skipped = Number(skippedMatch[1].replace(/,/g, ''));
  }

  const summaryMatch = output.match(/=+\s*([\d,]+) failures?,\s*([\d,]+) errors?/i);
  if (summaryMatch) {
    const failures = Number(summaryMatch[1].replace(/,/g, ''));
    const errors = Number(summaryMatch[2].replace(/,/g, ''));
    metrics.failures = failures + errors;

    if (metrics.generated !== null) {
      const skipped = metrics.skipped ?? 0;
      metrics.passed = Math.max(metrics.generated - metrics.failures - skipped, 0);
    }
  }

  const warningMatch = output.match(/=+\s*[\d,]+ failures?,\s*([\d,]+) warnings?/i);
  if (warningMatch) {
    metrics.warnings = Number(warningMatch[1].replace(/,/g, ''));
  }

  if (metrics.warnings === null) {
    metrics.warnings = 0;
  }

  return metrics;
}

function toSeconds(valueMs) {
  return (Number(valueMs) / 1000).toFixed(3).replace(/0+$/, '').replace(/\.$/, '');
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function resolveAuthToken() {
  if (process.env.SCHEMATHESIS_AUTH_TOKEN) {
    return process.env.SCHEMATHESIS_AUTH_TOKEN;
  }

  const email = process.env.SCHEMATHESIS_LOGIN_EMAIL ?? 'admin@samt.local';
  const password = process.env.SCHEMATHESIS_LOGIN_PASSWORD ?? 'Str0ng@Pass!';
  const maxAttempts = Number(process.env.SCHEMATHESIS_LOGIN_RETRIES ?? 10);
  const retryDelayMs = Number(process.env.SCHEMATHESIS_LOGIN_RETRY_DELAY_MS ?? 3000);

  for (let attempt = 1; attempt <= maxAttempts; attempt += 1) {
    try {
      const response = await fetch(loginUrl, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
      });

      if (!response.ok) {
        if (attempt === maxAttempts) {
          throw new Error(`login returned ${response.status}`);
        }
      } else {
        const body = await response.json();
        if (body?.data?.accessToken ?? body?.accessToken) {
          return body?.data?.accessToken ?? body?.accessToken;
        }

        if (attempt === maxAttempts) {
          throw new Error('login response had no accessToken');
        }
      }
    } catch (error) {
      if (attempt === maxAttempts) {
        throw error;
      }
    }

    await sleep(retryDelayMs);
  }

  return null;
}

let authToken;

await ensureSelfHealFiles();

const options = parseArgs(process.argv.slice(2));
const mode = options.mode;
const strictSeed = process.env.SCHEMATHESIS_SEED ?? '20260308';
const latencyBudgetMs = Number(process.env.SCHEMATHESIS_MAX_RESPONSE_TIME_MS ?? 800);
const latencyBudgetSeconds = toSeconds(latencyBudgetMs);
const reportPrefix = `${cwd}/.self-heal/reports/${mode}`;
const phaseArgs = mode === 'fuzz'
  ? ['--phases', 'examples,coverage,fuzzing']
  : ['--phases', 'examples,coverage'];

const strictArgs = [
  '--checks',
  'all',
  '--warnings',
  'off',
  `--max-response-time=${latencyBudgetSeconds}`,
  '--workers',
  process.env.SCHEMATHESIS_WORKERS ?? '1',
  '--seed',
  strictSeed,
  '--generation-deterministic',
  '--report',
  'junit,ndjson',
  '--report-junit-path',
  `/work/.self-heal/reports/${mode}-junit.xml`,
  '--report-ndjson-path',
  `/work/.self-heal/reports/${mode}-events.ndjson`,
  '--exclude-path-regex=^/(api/auth/(login|refresh|register)|internal/.*|profile|api/admin/users/[^/]+/(lock|unlock|restore)|api/admin/users/[^/]+|api/admin/audit/actor/[^/]+)$'
];

const modeArgs = mode === 'fuzz'
  ? [
      '--max-examples',
      process.env.SCHEMATHESIS_FUZZ_MAX_EXAMPLES ?? '25'
    ]
  : [
      '--max-examples',
      process.env.SCHEMATHESIS_CONTRACT_MAX_EXAMPLES ?? '1'
    ];

const seedResult = spawnSync(npxCommand, ['httpyac', 'tests/bootstrap.http', 'tests/fixtures.http', '--all'], {
  encoding: 'utf8',
  shell: process.platform === 'win32'
});

process.stdout.write(seedResult.stdout || '');
process.stderr.write(seedResult.stderr || '');

if (seedResult.error) {
  console.error(`Schemathesis fixture bootstrap failed: ${seedResult.error.message}`);
  process.exit(1);
}

if ((seedResult.status ?? 1) !== 0) {
  process.exit(seedResult.status ?? 1);
}

try {
  authToken = await resolveAuthToken();
} catch (error) {
  console.error(`Schemathesis auth bootstrap failed: ${error.message}`);
  process.exit(1);
}

if (!authToken) {
  console.error('Schemathesis auth bootstrap failed: no access token available');
  process.exit(1);
}

const args = [
  'run',
  '--rm',
  ...(useComposeNetwork ? ['--network', dockerNetwork] : []),
  '-v',
  `${cwd}:/work`,
  'schemathesis/schemathesis',
  'run',
  '/work/openapi.yaml',
  `--url=${gatewayUrl}`,
  ...phaseArgs,
  ...strictArgs,
  ...modeArgs
];

args.push('-H', `Authorization: Bearer ${authToken}`);

const result = spawnSync('docker', args, {
  encoding: 'utf8',
  env: dockerEnv
});

const output = `${result.stdout || ''}${result.stderr || ''}`;
process.stdout.write(result.stdout || '');
process.stderr.write(result.stderr || '');

const metrics = parseMetrics(
  output,
  mode,
  strictSeed,
  `${reportPrefix}-junit.xml`,
  `${reportPrefix}-events.ndjson`
);
await writeReport(`${mode}-metrics.json`, `${JSON.stringify(metrics, null, 2)}\n`);
await writeReport(
  `${mode}-metrics.md`,
  [
    `# ${mode === 'fuzz' ? 'Fuzzing' : 'Contract'} Metrics`,
    '',
    `- Seed: ${strictSeed}`,
    `- Generated: ${metrics.generated ?? 'unknown'}`,
    `- Passed: ${metrics.passed ?? 'unknown'}`,
    `- Skipped: ${metrics.skipped ?? 'unknown'}`,
    `- Failures: ${metrics.failures ?? 'unknown'}`,
    `- Warnings: ${metrics.warnings ?? 'unknown'}`,
    `- Max response time budget: ${metrics.latencyBudgetMs} ms`,
    ''
  ].join('\n')
);

if (result.error) {
  console.error(result.error.message);
  process.exit(1);
}

process.exit(result.status ?? 1);
