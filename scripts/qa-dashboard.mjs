import fs from 'node:fs';
import path from 'node:path';
import { htmlEscape, parseArgs, safeJsonParse, writeJson, writeText } from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  reportName: 'qa-dashboard',
  sourceReport: '.self-heal/reports/qa-system-latest.json'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const htmlReport = path.join(reportsDir, `${options.reportName}.html`);

function readJson(relativePath) {
  const filePath = path.join(workspacePath, relativePath);
  if (!fs.existsSync(filePath)) {
    return null;
  }
  return safeJsonParse(fs.readFileSync(filePath, 'utf8'));
}

function stageStatus(value) {
  if (!value) {
    return 'unknown';
  }
  if (value.status) {
    return value.status;
  }
  if (value.finalStatus) {
    return value.finalStatus;
  }
  return 'unknown';
}

function healthScore(sections) {
  let score = 100;
  for (const section of sections) {
    if (section.status === 'failed') {
      score -= section.weight;
    }
    if (section.status === 'skipped' || section.status === 'unknown') {
      score -= Math.ceil(section.weight / 3);
    }
  }
  return Math.max(0, score);
}

function toHtml(report) {
  const cards = report.sections.map((section) => `
      <article class="card status-${htmlEscape(section.status)}">
        <h2>${htmlEscape(section.title)}</h2>
        <p class="status">${htmlEscape(section.status)}</p>
        <p>${htmlEscape(section.summary)}</p>
      </article>`).join('');

  const stageRows = report.pipelineStages.map((stage) => `
      <tr>
        <td>${htmlEscape(stage.id)}</td>
        <td>${htmlEscape(String(stage.status))}</td>
        <td>${htmlEscape(String(stage.attempt ?? 1))}</td>
      </tr>`).join('');

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>QA Dashboard</title>
  <style>
    :root { --bg:#f3efe7; --panel:#fffdfa; --text:#16202a; --muted:#596573; --good:#1c7c54; --warn:#c66b1c; --bad:#b42318; }
    body { margin:0; font-family: Cambria, Georgia, serif; color:var(--text); background:linear-gradient(180deg,#f9f5ed,#f3efe7); }
    main { max-width:1180px; margin:0 auto; padding:28px 20px 40px; }
    .hero,.panel,.card { background:var(--panel); border:1px solid rgba(22,32,42,.08); border-radius:20px; box-shadow:0 12px 28px rgba(22,32,42,.08); }
    .hero,.panel { padding:22px; margin-bottom:18px; }
    .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:14px; }
    .card { padding:18px; }
    .status { text-transform:uppercase; letter-spacing:.08em; font-size:12px; color:var(--muted); }
    .status-passed .status { color:var(--good); }
    .status-failed .status { color:var(--bad); }
    .status-skipped .status,.status-unknown .status { color:var(--warn); }
    table { width:100%; border-collapse:collapse; }
    th,td { padding:10px 8px; border-bottom:1px solid rgba(22,32,42,.08); text-align:left; }
    th { color:var(--muted); font-size:12px; letter-spacing:.06em; text-transform:uppercase; }
  </style>
</head>
<body>
  <main>
    <section class="hero">
      <h1>Reliability Validation Dashboard</h1>
      <p>Generated ${htmlEscape(report.generatedAt)}. Health score: <strong>${report.healthScore}/100</strong>.</p>
      <p>Pipeline status: <strong>${htmlEscape(report.pipelineStatus)}</strong>.</p>
    </section>
    <section class="panel">
      <div class="grid">${cards}
      </div>
    </section>
    <section class="panel">
      <h2>Pipeline Stages</h2>
      <table>
        <thead><tr><th>Stage</th><th>Status</th><th>Attempt</th></tr></thead>
        <tbody>${stageRows}
        </tbody>
      </table>
    </section>
  </main>
</body>
</html>
`;
}

async function main() {
  const pipeline = readJson(options.sourceReport) || {};
  const load = readJson('.self-heal/reports/load-test.json');
  const security = readJson('.self-heal/reports/security-probe.json');
  const integrations = readJson('.self-heal/reports/external-integrations.json');
  const chaos = readJson('.self-heal/reports/chaos.json');
  const communication = readJson('.self-heal/reports/communication.json');
  const hardening = readJson('.self-heal/reports/security-hardening.json');
  const architecture = readJson('.self-heal/reports/architecture-health.json');
  const autofix = readJson('.self-heal/reports/auto-fix.json');
  const endpoints = readJson('qa/endpoints.json');

  const sections = [
    { title: 'API Coverage', status: endpoints ? 'passed' : 'unknown', summary: endpoints ? `${endpoints.summary?.operations || endpoints.operations?.length || 0} operations discovered` : 'No endpoint inventory available', weight: 8 },
    { title: 'Smoke/Regression/Contract', status: pipeline.finalStatus || 'unknown', summary: pipeline.failure ? `Blocked at ${pipeline.failure.stageId}` : 'Pipeline completed', weight: 24 },
    { title: 'Security Probes', status: stageStatus(security), summary: security ? `${security.regressions ?? 0} boundary regressions` : 'No security probe report', weight: 12 },
    { title: 'Security Hardening', status: stageStatus(hardening), summary: hardening ? `${hardening.tests?.length || 0} hardening cases` : 'No hardening report', weight: 12 },
    { title: 'Chaos', status: stageStatus(chaos), summary: chaos ? `${chaos.experiments?.length || 0} experiments` : 'No chaos report', weight: 14 },
    { title: 'Performance', status: stageStatus(load), summary: load?.regressionDetected ? 'Performance regression detected' : load ? `p95 ${load.metrics?.p95DurationMs ?? 'n/a'} ms` : 'No load report', weight: 12 },
    { title: 'Integrations', status: stageStatus(integrations), summary: integrations ? 'Jira and GitHub verification executed' : 'No integrations report', weight: 8 },
    { title: 'Architecture', status: architecture ? (architecture.score < 70 ? 'failed' : 'passed') : 'unknown', summary: architecture ? `Architecture score ${architecture.score}/100` : 'No architecture report', weight: 10 },
    { title: 'Auto Fixer', status: autofix?.changed ? 'passed' : autofix ? 'skipped' : 'unknown', summary: autofix ? `${autofix.appliedChanges?.length || 0} files patched, ${autofix.overrideRepairs?.length || 0} override repairs` : 'No auto-fix report', weight: 6 },
    { title: 'Communication', status: stageStatus(communication), summary: communication ? 'Gateway and inter-service recovery checked' : 'No communication report', weight: 8 }
  ];

  const report = {
    generatedAt: new Date().toISOString(),
    pipelineStatus: pipeline.finalStatus || 'unknown',
    pipelineStages: pipeline.stages || [],
    healthScore: healthScore(sections),
    sections,
    raw: {
      endpoints,
      pipeline,
      security,
      hardening,
      load,
      chaos,
      communication,
      integrations,
      architecture,
      autofix
    }
  };

  await writeJson(jsonReport, report);
  await writeText(htmlReport, toHtml(report));
  process.stdout.write(`Wrote ${path.relative(workspacePath, jsonReport).replace(/\\/g, '/')}\n`);
}

main().catch(async (error) => {
  const failure = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(jsonReport, failure);
  await writeText(htmlReport, `<pre>${htmlEscape(failure.error)}</pre>`);
  process.stderr.write(`${failure.error}\n`);
  process.exit(1);
});