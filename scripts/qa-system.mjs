import fs from 'node:fs';
import path from 'node:path';
import {
  ensureDir,
  htmlEscape,
  parseArgs,
  runCommand,
  safeJsonParse,
  toPosix,
  toReportLink,
  writeJson,
  writeText
} from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  baseUrl: process.env.BASE_URL || 'http://localhost:9080',
  reportName: 'qa-system-latest',
  composeRecovery: String(process.env.QA_COMPOSE_RECOVERY || 'false'),
  selfHealIterations: String(process.env.QA_SELF_HEAL_ITERATIONS || '2'),
  maxIterations: String(process.env.QA_SYSTEM_MAX_ITERATIONS || '3'),
  autoFix: String(process.env.QA_AUTO_FIX || 'true')
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const maxIterations = Number(options.maxIterations);

function stageLogPath(iteration, stageId, attempt) {
  return path.join(reportsDir, `${options.reportName}-iter${iteration}-${stageId}-attempt${attempt}.log`);
}

function persistStageLog(iteration, stage, result, attempt, recovery) {
  const outputFile = stageLogPath(iteration, stage.id, attempt);
  fs.writeFileSync(outputFile, result.output, 'utf8');
  return {
    id: stage.id,
    command: stage.command,
    iteration,
    attempt,
    status: result.status,
    durationMs: result.durationMs,
    recovery,
    outputFile: toPosix(path.relative(workspacePath, outputFile))
  };
}

function runStage(command) {
  return runCommand(command, workspacePath, { BASE_URL: options.baseUrl });
}

function runComposeRecovery() {
  return runStage('docker compose up -d --build');
}

function runSelfHealRecovery() {
  return runStage(`node scripts/self-heal-orchestrator.mjs --ci --max-iterations=${options.selfHealIterations}`);
}

function runAutoFixRecovery() {
  return runStage(`node scripts/qa-auto-fix.mjs --workspace=${workspacePath} --sourceReport=${path.join(reportsDir, `${options.reportName}.json`)}`);
}

function artifactCandidates() {
  return [
    'qa/endpoints.json',
    'qa/endpoints.md',
    '.self-heal/reports/security-probe.json',
    '.self-heal/reports/load-test.json',
    '.self-heal/reports/external-integrations.json',
    '.self-heal/reports/contract-metrics.json',
    '.self-heal/reports/fuzz-metrics.json',
    '.self-heal/reports/auto-fix.json',
    '.self-heal/reports/chaos.json',
    '.self-heal/reports/architecture-health.json',
    '.self-heal/reports/communication.json',
    '.self-heal/reports/security-hardening.json',
    '.self-heal/reports/qa-dashboard.json',
    '.self-heal/reports/qa-dashboard.html'
  ];
}

function readJson(relativePath) {
  const filePath = path.isAbsolute(relativePath) ? relativePath : path.join(workspacePath, relativePath);
  if (!fs.existsSync(filePath)) {
    return null;
  }
  return safeJsonParse(fs.readFileSync(filePath, 'utf8'));
}

function collectArtifacts() {
  return artifactCandidates()
    .filter((candidate) => fs.existsSync(path.join(workspacePath, candidate)))
    .map((candidate) => toReportLink(path.join(workspacePath, candidate), workspacePath));
}

async function persistReport(report) {
  report.generatedArtifacts = collectArtifacts();
  await ensureDir(reportsDir);
  await writeJson(path.join(reportsDir, `${options.reportName}.json`), report);
  await writeText(path.join(reportsDir, `${options.reportName}.md`), toMarkdown(report));
  await writeText(path.join(reportsDir, `${options.reportName}.html`), toHtml(report));
}

function autoFixChanged() {
  const report = readJson('.self-heal/reports/auto-fix.json');
  return Boolean(report?.changed);
}

function stageDefinitions() {
  return [
    { id: 'discover', command: 'node scripts/qa-discover.mjs' },
    { id: 'openapi', command: 'npm run openapi:generate' },
    { id: 'generate-tests', command: 'npm run tests:generate' },
    { id: 'security', command: 'node scripts/qa-security-probe.mjs' },
    { id: 'bootstrap', command: 'npm run tests:bootstrap' },
    { id: 'fixtures', command: 'npm run tests:fixtures' },
    { id: 'smoke', command: 'npm run test:smoke', canSelfHeal: true, canAutoFix: true },
    { id: 'regression', command: 'npm run tests:regression', canSelfHeal: true, canAutoFix: true },
    { id: 'contract', command: 'npm run tests:contract:strict', canSelfHeal: true, canAutoFix: true },
    { id: 'fuzz', command: 'npm run tests:fuzz', canSelfHeal: true, canAutoFix: true },
    { id: 'hardening', command: 'npm run qa:hardening' },
    { id: 'load', command: 'npm run qa:load' },
    { id: 'communication', command: 'npm run qa:communication' },
    { id: 'chaos', command: 'npm run qa:chaos' },
    { id: 'architecture', command: 'npm run qa:architecture' },
    { id: 'integrations', command: 'npm run qa:integration' }
  ];
}

function toMarkdown(report) {
  const lines = [
    '# QA System Report',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Completed at: ${report.completedAt || 'in-progress'}`,
    `- Final status: ${report.finalStatus}`,
    `- Base URL: ${report.baseUrl}`,
    `- Iterations: ${report.iterations.length}/${report.maxIterations}`,
    ''
  ];

  for (const iteration of report.iterations) {
    lines.push(`## Iteration ${iteration.iteration}`);
    lines.push('');
    lines.push(`- Result: ${iteration.status}`);
    if (iteration.failure) {
      lines.push(`- Failed stage: ${iteration.failure.stageId}`);
    }
    if (iteration.autoFix) {
      lines.push(`- Auto-fix attempted: ${iteration.autoFix.status}`);
    }
    lines.push('');
    for (const stage of iteration.stages) {
      lines.push(`- ${stage.id}: ${stage.status === 0 ? 'passed' : 'failed'} (attempt ${stage.attempt})`);
    }
    lines.push('');
  }

  if (report.generatedArtifacts.length > 0) {
    lines.push('## Artifacts');
    lines.push('');
    for (const artifact of report.generatedArtifacts) {
      lines.push(`- ${artifact}`);
    }
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

function toHtml(report) {
  const iterationPanels = report.iterations.map((iteration) => {
    const rows = iteration.stages.map((stage) => `
        <tr>
          <td>${htmlEscape(stage.id)}</td>
          <td>${stage.status === 0 ? 'passed' : 'failed'}</td>
          <td>${stage.attempt}</td>
          <td>${stage.durationMs}</td>
        </tr>`).join('');

    return `
      <section class="panel">
        <h2>Iteration ${iteration.iteration}</h2>
        <p class="meta">Status: ${htmlEscape(iteration.status)}${iteration.failure ? `, failed at ${htmlEscape(iteration.failure.stageId)}` : ''}</p>
        <table>
          <thead><tr><th>Stage</th><th>Status</th><th>Attempt</th><th>Duration ms</th></tr></thead>
          <tbody>${rows}
          </tbody>
        </table>
      </section>`;
  }).join('');

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <title>QA System Report</title>
  <style>
    :root { color-scheme: light; --bg: #f4f1ea; --panel: #fffdf8; --text: #1d2430; --muted: #5b6677; }
    body { margin: 0; font-family: Georgia, 'Times New Roman', serif; background: radial-gradient(circle at top, #fef8ed, #f4f1ea 60%); color: var(--text); }
    main { max-width: 1080px; margin: 0 auto; padding: 32px 24px 48px; }
    .panel { background: var(--panel); border: 1px solid rgba(29,36,48,0.1); border-radius: 18px; padding: 20px 22px; box-shadow: 0 12px 28px rgba(29,36,48,0.08); margin-bottom: 20px; }
    .meta { color: var(--muted); }
    table { width: 100%; border-collapse: collapse; }
    th, td { text-align: left; padding: 10px 8px; border-bottom: 1px solid rgba(29,36,48,0.08); }
    th { color: var(--muted); font-size: 13px; text-transform: uppercase; letter-spacing: 0.06em; }
  </style>
</head>
<body>
  <main>
    <section class="panel">
      <h1>Reliability QA System</h1>
      <p class="meta">Generated ${htmlEscape(report.generatedAt)}. Final status: ${htmlEscape(report.finalStatus)}.</p>
      <p class="meta">Iterations: ${report.iterations.length}/${report.maxIterations}</p>
    </section>
    ${iterationPanels}
  </main>
</body>
</html>
`;
}

async function main() {
  const report = {
    generatedAt: new Date().toISOString(),
    completedAt: null,
    baseUrl: options.baseUrl,
    composeRecoveryEnabled: String(options.composeRecovery).toLowerCase() === 'true',
    autoFixEnabled: String(options.autoFix).toLowerCase() === 'true',
    maxIterations,
    finalStatus: 'failed',
    iterations: [],
    stages: [],
    generatedArtifacts: [],
    failure: null
  };

  const stages = stageDefinitions();
  let stabilized = false;

  for (let iterationNumber = 1; iterationNumber <= maxIterations; iterationNumber += 1) {
    const iteration = {
      iteration: iterationNumber,
      status: 'passed',
      stages: [],
      autoFix: null,
      failure: null
    };
    let continueToNextIteration = false;

    for (const stage of stages) {
      let attempt = 1;
      let result = runStage(stage.command);
      const primary = persistStageLog(iterationNumber, stage, result, attempt, null);
      iteration.stages.push(primary);
      report.stages.push(primary);

      if (result.status === 0) {
        continue;
      }

      if (report.composeRecoveryEnabled) {
        const composeRecovery = runComposeRecovery();
        const recoveryStage = persistStageLog(iterationNumber, { id: `${stage.id}-compose-recovery`, command: 'docker compose up -d --build' }, composeRecovery, 1, null);
        iteration.stages.push(recoveryStage);
        report.stages.push(recoveryStage);
        attempt += 1;
        result = runStage(stage.command);
        const rerun = persistStageLog(iterationNumber, stage, result, attempt, { type: 'docker-compose' });
        iteration.stages.push(rerun);
        report.stages.push(rerun);
        if (result.status === 0) {
          continue;
        }
      }

      if (stage.canSelfHeal) {
        const heal = runSelfHealRecovery();
        const healStage = persistStageLog(iterationNumber, { id: `${stage.id}-self-heal`, command: `node scripts/self-heal-orchestrator.mjs --ci --max-iterations=${options.selfHealIterations}` }, heal, 1, null);
        iteration.stages.push(healStage);
        report.stages.push(healStage);
        attempt += 1;
        result = runStage(stage.command);
        const healedRerun = persistStageLog(iterationNumber, stage, result, attempt, { type: 'self-heal' });
        iteration.stages.push(healedRerun);
        report.stages.push(healedRerun);
        if (result.status === 0) {
          continue;
        }
      }

      iteration.status = 'failed';
      iteration.failure = { stageId: stage.id, outputFile: iteration.stages[iteration.stages.length - 1].outputFile };
      report.failure = iteration.failure;
      await persistReport(report);

      if (report.autoFixEnabled && stage.canAutoFix) {
        const autoFixResult = runAutoFixRecovery();
        const autoFixStage = persistStageLog(iterationNumber, { id: `${stage.id}-auto-fix`, command: 'node scripts/qa-auto-fix.mjs' }, autoFixResult, 1, null);
        iteration.stages.push(autoFixStage);
        report.stages.push(autoFixStage);
        iteration.autoFix = {
          status: autoFixResult.status === 0 && autoFixChanged() ? 'changed' : autoFixResult.status === 0 ? 'no-change' : 'failed',
          outputFile: autoFixStage.outputFile
        };

        if (autoFixResult.status === 0 && autoFixChanged()) {
          continueToNextIteration = true;
        }
      }

      break;
    }

    report.iterations.push(iteration);
    await persistReport(report);

    if (iteration.status === 'passed') {
      stabilized = true;
      break;
    }

    if (!continueToNextIteration) {
      break;
    }
  }

  report.completedAt = new Date().toISOString();
  report.finalStatus = stabilized ? 'passed' : 'failed';
  await persistReport(report);

  const dashboard = runStage(`node scripts/qa-dashboard.mjs --workspace=${workspacePath} --sourceReport=${path.join(reportsDir, `${options.reportName}.json`)}`);
  const dashboardStage = persistStageLog(report.iterations.length || 1, { id: 'dashboard', command: 'node scripts/qa-dashboard.mjs' }, dashboard, 1, null);
  report.stages.push(dashboardStage);
  if (report.iterations.length > 0) {
    report.iterations[report.iterations.length - 1].stages.push(dashboardStage);
  }
  await persistReport(report);

  if (!stabilized) {
    process.exit(1);
  }
}

main().catch(async (error) => {
  const failureReport = {
    generatedAt: new Date().toISOString(),
    completedAt: new Date().toISOString(),
    finalStatus: 'failed',
    error: error.stack || error.message || String(error),
    iterations: [],
    stages: [],
    generatedArtifacts: collectArtifacts()
  };
  await ensureDir(reportsDir);
  await writeJson(path.join(reportsDir, `${options.reportName}.json`), failureReport);
  await writeText(path.join(reportsDir, `${options.reportName}.md`), `# QA System Report\n\n\`\`\`text\n${failureReport.error}\n\`\`\`\n`);
  process.stderr.write(`${failureReport.error}\n`);
  process.exit(1);
});
