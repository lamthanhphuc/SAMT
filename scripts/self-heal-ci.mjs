import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import { ensureSelfHealFiles, getReportsDir, writeReport } from './self-heal-overrides.mjs';

function parseArgs(argv) {
  const options = {
    workspacePath: process.cwd(),
    seed: process.env.QA_SEED || '20260308',
    maxIterations: Number(process.env.SELF_HEAL_CI_MAX_ITERATIONS || 3)
  };

  for (const arg of argv) {
    if (arg.startsWith('--workspace=')) {
      options.workspacePath = path.resolve(arg.slice('--workspace='.length));
      continue;
    }

    if (arg.startsWith('--seed=')) {
      options.seed = arg.slice('--seed='.length);
      continue;
    }

    if (arg.startsWith('--max-iterations=')) {
      options.maxIterations = Number(arg.slice('--max-iterations='.length));
    }
  }

  return options;
}

function reportStage(stageId, command, result, outputFile) {
  return {
    id: stageId,
    command,
    status: result.status,
    outputFile: outputFile.replace(/\\/g, '/'),
    durationMs: result.durationMs
  };
}

function runCommand(command, cwd, env = {}) {
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
    durationMs: Date.now() - start
  };
}

function runParallel(commands, cwd, env = {}) {
  const tasks = commands.map(({ id, command }) => new Promise((resolve) => {
    const start = Date.now();
    const child = spawn(command, {
      cwd,
      shell: process.platform === 'win32' ? 'powershell.exe' : true,
      env: { ...process.env, ...env }
    });

    let stdout = '';
    let stderr = '';

    child.stdout?.on('data', (chunk) => {
      stdout += chunk.toString();
    });

    child.stderr?.on('data', (chunk) => {
      stderr += chunk.toString();
    });

    child.on('close', (code) => {
      resolve({
        id,
        command,
        status: code ?? 1,
        stdout,
        stderr,
        output: `${stdout}${stderr}`,
        durationMs: Date.now() - start
      });
    });
  }));

  return Promise.all(tasks);
}

function toMarkdown(report) {
  const lines = [
    '# Autonomous QA CI Report',
    '',
    `- Final status: ${report.finalStatus}`,
    `- Seed: ${report.seed}`,
    `- Parallel stages executed: ${report.parallelStages.join(', ') || 'none'}`,
    ''
  ];

  lines.push('## Stages');
  lines.push('');
  for (const stage of report.stages) {
    lines.push(`- ${stage.id}: ${stage.status === 0 ? 'passed' : 'failed'} (${stage.durationMs} ms)`);
  }
  lines.push('');

  if (report.failures.length > 0) {
    lines.push('## Failures');
    lines.push('');
    for (const failure of report.failures) {
      lines.push(`- ${failure.id}: ${failure.outputFile}`);
    }
    lines.push('');
  }

  if (report.generatedReports.length > 0) {
    lines.push('## Generated Reports');
    lines.push('');
    for (const generatedReport of report.generatedReports) {
      lines.push(`- ${generatedReport}`);
    }
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function writeStageLog(stageId, output) {
  const logPath = path.join(getReportsDir(), `ci-${stageId}.log`);
  await fs.writeFile(logPath, output, 'utf8');
  return logPath;
}

async function invokeQaAgent(workspacePath, stage, logFile) {
  const result = runCommand(
    `node scripts/autonomous-qa-agent.mjs --stage=${stage} --log="${logFile}"`,
    workspacePath
  );

  return result.status === 0;
}

async function main() {
  await ensureSelfHealFiles();
  const options = parseArgs(process.argv.slice(2));
  const strictEnv = {
    CI: 'true',
    QA_SEED: options.seed,
    SCHEMATHESIS_SEED: options.seed,
    SCHEMATHESIS_WORKERS: 'auto',
    SCHEMATHESIS_MAX_RESPONSE_TIME_MS: '800',
    SCHEMATHESIS_STRICT: 'true',
    SCHEMATHESIS_VALIDATE_SCHEMA: 'true'
  };

  const report = {
    generatedAt: new Date().toISOString(),
    workspacePath: options.workspacePath.replace(/\\/g, '/'),
    seed: options.seed,
    finalStatus: 'failed',
    stages: [],
    failures: [],
    parallelStages: ['contract', 'fuzz'],
    generatedReports: []
  };

  const sequentialStages = [
    { id: 'openapi', command: 'npm run openapi:generate' },
    { id: 'self-heal', command: `node scripts/self-heal-orchestrator.mjs --ci --max-iterations=${options.maxIterations}` },
    { id: 'schema-drift', command: 'npm run tests:schema-drift' },
    { id: 'bootstrap', command: 'npm run tests:bootstrap' },
    { id: 'fixtures', command: 'npm run tests:fixtures' },
    { id: 'smoke', command: 'npm run test:smoke' },
    { id: 'regression', command: 'npm run tests:regression:ci' }
  ];

  for (const stage of sequentialStages) {
    const result = runCommand(stage.command, options.workspacePath, strictEnv);
    const outputFile = await writeStageLog(stage.id, result.output);
    report.stages.push(reportStage(stage.id, stage.command, result, outputFile));

    if (result.status !== 0) {
      report.failures.push({ id: stage.id, outputFile: outputFile.replace(/\\/g, '/') });
      await invokeQaAgent(options.workspacePath, stage.id, outputFile);
      await writeReport('ci-latest.json', `${JSON.stringify(report, null, 2)}\n`);
      await writeReport('ci-latest.md', toMarkdown(report));
      process.exit(1);
    }
  }

  const parallelResults = await runParallel([
    { id: 'contract', command: 'npm run tests:contract:strict' },
    { id: 'fuzz', command: 'npm run tests:fuzz' }
  ], options.workspacePath, strictEnv);

  for (const result of parallelResults) {
    const outputFile = await writeStageLog(result.id, result.output);
    report.stages.push(reportStage(result.id, result.command, result, outputFile));
    if (result.status !== 0) {
      report.failures.push({ id: result.id, outputFile: outputFile.replace(/\\/g, '/') });
    }
  }

  const snapshotResult = runCommand('npm run snapshots:check', options.workspacePath, strictEnv);
  const snapshotLogFile = await writeStageLog('snapshots', snapshotResult.output);
  report.stages.push(reportStage('snapshots', 'npm run snapshots:check', snapshotResult, snapshotLogFile));
  if (snapshotResult.status !== 0) {
    report.failures.push({ id: 'snapshots', outputFile: snapshotLogFile.replace(/\\/g, '/') });
  }

  if (report.failures.length > 0) {
    const lastFailure = report.failures[report.failures.length - 1];
    await invokeQaAgent(options.workspacePath, lastFailure.id, lastFailure.outputFile);
    await writeReport('ci-latest.json', `${JSON.stringify(report, null, 2)}\n`);
    await writeReport('ci-latest.md', toMarkdown(report));
    process.exit(1);
  }

  report.finalStatus = 'passed';
  report.generatedReports = [
    '.self-heal/reports/latest.json',
    '.self-heal/reports/schema-drift-alert.json',
    '.self-heal/reports/regression-drift-report.json',
    '.self-heal/reports/contract-metrics.json',
    '.self-heal/reports/fuzz-metrics.json'
  ];

  await writeReport('ci-latest.json', `${JSON.stringify(report, null, 2)}\n`);
  await writeReport('ci-latest.md', toMarkdown(report));
}

main().catch(async (error) => {
  await ensureSelfHealFiles();
  await writeReport('ci-latest.md', `# Autonomous QA CI Report\n\n- Final status: failed\n\n\`\`\`text\n${error.stack || error.message || String(error)}\n\`\`\`\n`);
  process.stderr.write(`${error.stack || error.message || String(error)}\n`);
  process.exit(1);
});
