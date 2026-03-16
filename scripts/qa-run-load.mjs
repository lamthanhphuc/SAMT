import fs from 'node:fs';
import path from 'node:path';
import {
  ensureDir,
  loadWorkspaceEnv,
  parseArgs,
  resolveDockerGatewayUrl,
  runCommand,
  toPosix,
  writeJson,
  writeText
} from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  reportName: 'load-test',
  profile: process.env.K6_PROFILE || 'full'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const baselinesDir = path.join(workspacePath, '.self-heal', 'baselines');
const dockerNetwork = process.env.QA_DOCKER_NETWORK || process.env.SCHEMATHESIS_DOCKER_NETWORK || 'docker_samt-network';
const summaryFile = path.join(reportsDir, `${options.reportName}-summary.json`);
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const markdownReport = path.join(reportsDir, `${options.reportName}.md`);
const baselineFile = path.join(baselinesDir, `${options.profile}.json`);
const loadScriptRelative = 'integration-tests/performance/load-test.js';
const requireLoadRunner = String(process.env.QA_LOAD_REQUIRED || 'false').toLowerCase() === 'true';
const updateBaseline = String(process.env.QA_LOAD_UPDATE_BASELINE || 'false').toLowerCase() === 'true';

loadWorkspaceEnv(workspacePath);

function hasLocalK6() {
  const command = process.platform === 'win32'
    ? 'Get-Command k6 | Select-Object -First 1'
    : 'command -v k6';
  return runCommand(command, workspacePath).status === 0;
}

function shouldSkip(result) {
  const output = result.output || '';
  return output.includes('Unable to find image')
    || output.includes('Client.Timeout exceeded while awaiting headers')
    || output.includes('pull access denied')
    || output.includes('not recognized as the name of a cmdlet');
}

function extractMetric(summary, metricName, field) {
  const metric = summary?.metrics?.[metricName];
  if (!metric) {
    return null;
  }
  if (metric.values && field in metric.values) {
    return metric.values[field];
  }
  if (field in metric) {
    return metric[field];
  }
  if (field === 'count' && 'count' in metric) {
    return metric.count;
  }
  if (field === 'rate' && 'rate' in metric) {
    return metric.rate;
  }
  if (field === 'value' && 'value' in metric) {
    return metric.value;
  }
  return null;
}

function readBaseline() {
  if (!fs.existsSync(baselineFile)) {
    return null;
  }
  const baseline = JSON.parse(fs.readFileSync(baselineFile, 'utf8'));
  return baseline?.p95DurationMs == null && baseline?.failureRate == null
    ? null
    : baseline;
}

function regressionFromBaseline(baseline, metrics) {
  if (!baseline || !metrics) {
    return null;
  }

  const latencyRatio = baseline.p95DurationMs > 0 && metrics.p95DurationMs > 0
    ? (metrics.p95DurationMs - baseline.p95DurationMs) / baseline.p95DurationMs
    : 0;
  const errorRateDelta = (metrics.failureRate ?? 0) - (baseline.failureRate ?? 0);

  return {
    latencyRatio,
    errorRateDelta,
    detected: latencyRatio > 0.3 || errorRateDelta > 0.05
  };
}

function toMarkdown(report) {
  const lines = [
    '# Load Test',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Status: ${report.status}`,
    `- Profile: ${report.profile}`,
    `- Base URL: ${report.baseUrl}`,
    ''
  ];

  if (report.metrics) {
    lines.push('## Metrics');
    lines.push('');
    lines.push(`- Requests: ${report.metrics.httpReqs ?? 'unknown'}`);
    lines.push(`- Failure rate: ${report.metrics.failureRate ?? 'unknown'}`);
    lines.push(`- Throughput: ${report.metrics.throughputPerSecond ?? 'unknown'} req/s`);
    lines.push(`- P50 duration: ${report.metrics.p50DurationMs ?? 'unknown'} ms`);
    lines.push(`- P95 duration: ${report.metrics.p95DurationMs ?? 'unknown'} ms`);
    lines.push(`- P99 duration: ${report.metrics.p99DurationMs ?? 'unknown'} ms`);
    lines.push('');
  }

  if (report.baseline) {
    lines.push('## Baseline');
    lines.push('');
    lines.push(`- Baseline P95: ${report.baseline.p95DurationMs ?? 'unknown'} ms`);
    lines.push(`- Baseline failure rate: ${report.baseline.failureRate ?? 'unknown'}`);
    lines.push(`- Regression detected: ${report.regressionDetected ? 'yes' : 'no'}`);
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function main() {
  await ensureDir(reportsDir);
  await ensureDir(baselinesDir);
  const baseUrl = resolveDockerGatewayUrl();
  const localK6 = hasLocalK6();
  const command = localK6
    ? `k6 run ${loadScriptRelative} --summary-export ${toPosix(path.relative(workspacePath, summaryFile))}`
    : [
        'docker run --rm',
        ...(baseUrl === 'http://api-gateway:8080' ? [`--network ${dockerNetwork}`] : []),
        `-v "${workspacePath}:/work"`,
        `-e BASE_URL=${baseUrl}`,
        `-e K6_PROFILE=${options.profile}`,
        `grafana/k6 run /work/${toPosix(loadScriptRelative)} --summary-export /work/${toPosix(path.relative(workspacePath, summaryFile))}`
      ].join(' ');

  const result = runCommand(command, workspacePath, {
    BASE_URL: baseUrl,
    K6_PROFILE: options.profile
  });
  const summary = fs.existsSync(summaryFile)
    ? JSON.parse(fs.readFileSync(summaryFile, 'utf8'))
    : null;
  const skipped = result.status !== 0 && !requireLoadRunner && shouldSkip(result);
  const metrics = summary
    ? {
        httpReqs: extractMetric(summary, 'http_reqs', 'count'),
        throughputPerSecond: extractMetric(summary, 'http_reqs', 'rate'),
        failureRate: extractMetric(summary, 'http_req_failed', 'rate') ?? extractMetric(summary, 'http_req_failed', 'value'),
        p50DurationMs: extractMetric(summary, 'http_req_duration', 'p(50)') ?? extractMetric(summary, 'http_req_duration', 'med'),
        p95DurationMs: extractMetric(summary, 'http_req_duration', 'p(95)'),
        p99DurationMs: extractMetric(summary, 'http_req_duration', 'p(99)') ?? extractMetric(summary, 'http_req_duration', 'max')
      }
    : null;
  const baseline = readBaseline();
  const regression = regressionFromBaseline(baseline, metrics);

  const report = {
    generatedAt: new Date().toISOString(),
    status: skipped ? 'skipped' : result.status === 0 ? regression?.detected ? 'failed' : 'passed' : 'failed',
    profile: options.profile,
    baseUrl,
    command,
    outputFile: toPosix(path.relative(workspacePath, summaryFile)),
    metrics,
    baseline,
    regressionDetected: Boolean(regression?.detected),
    regression,
    error: result.error,
    exitCode: result.status
  };

  if (metrics && result.status === 0 && (!baseline || updateBaseline)) {
    fs.writeFileSync(baselineFile, `${JSON.stringify({
      capturedAt: new Date().toISOString(),
      profile: options.profile,
      ...metrics
    }, null, 2)}\n`, 'utf8');
    report.baselineUpdated = true;
  }

  await writeJson(jsonReport, report);
  await writeText(markdownReport, toMarkdown(report));

  process.stdout.write(`Wrote ${toPosix(path.relative(workspacePath, jsonReport))}\n`);
  if ((result.status !== 0 || report.regressionDetected) && !skipped) {
    process.stderr.write(result.output);
    if (report.regressionDetected) {
      process.stderr.write('Performance regression detected against stored baseline.\n');
    }
    process.exit(result.status || 1);
  }
}

main().catch(async (error) => {
  const report = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(jsonReport, report);
  await writeText(markdownReport, `# Load Test\n\n\`\`\`text\n${report.error}\n\`\`\`\n`);
  process.stderr.write(`${report.error}\n`);
  process.exit(1);
});
