import { spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import { analyzeHttpyacLog, analyzeSchemathesisLog, summarizeDiagnostics } from './self-heal-analyzer.mjs';
import { applyRepairs } from './self-heal-repair.mjs';
import {
  ensureSelfHealFiles,
  getReportsDir,
  loadPipelineConfig,
  writeReport
} from './self-heal-overrides.mjs';
import { scanValidationConstraints } from './validation-constraint-scanner.mjs';

function parseArgs(argv) {
  const out = {
    maxIterations: null,
    ci: false,
    workspacePath: process.cwd()
  };

  for (const arg of argv) {
    if (arg === '--ci') {
      out.ci = true;
      continue;
    }
    if (arg.startsWith('--max-iterations=')) {
      out.maxIterations = Number(arg.split('=')[1]);
      continue;
    }
    if (arg.startsWith('--workspace=')) {
      out.workspacePath = path.resolve(arg.split('=')[1]);
    }
  }

  return out;
}

function runCommand(command, cwd) {
  const result = spawnSync(command, {
    cwd,
    shell: process.platform === 'win32' ? 'powershell.exe' : true,
    encoding: 'utf8'
  });

  return {
    command,
    cwd,
    status: result.status ?? 1,
    stdout: result.stdout || '',
    stderr: result.stderr || '',
    output: `${result.stdout || ''}${result.stderr || ''}`
  };
}

function detectUnexpected500(stageResult) {
  return /HTTP\/1\.1\s+500\b|"statusCode"\s*:\s*500|500 Internal Server Error/i.test(stageResult.output);
}

function parseCoverageStats(contractOutput) {
  const generatedMatch = contractOutput.match(/Test cases:\s*([\d,]+) generated,\s*([\d,]+) passed,\s*([\d,]+) skipped/i);
  if (!generatedMatch) {
    return null;
  }

  return {
    generated: Number(generatedMatch[1].replace(/,/g, '')),
    passed: Number(generatedMatch[2].replace(/,/g, '')),
    skipped: Number(generatedMatch[3].replace(/,/g, ''))
  };
}

function toMarkdown(report) {
  const lines = [
    '# Self-Healing API Test Report',
    '',
    `- Final status: ${report.finalStatus}`,
    `- Iterations: ${report.iterations.length}`,
    `- Unexpected HTTP 500 detected: ${report.unexpected500 ? 'yes' : 'no'}`,
    ''
  ];

  if (report.coverageStats) {
    lines.push(`- Contract cases: ${report.coverageStats.passed}/${report.coverageStats.generated} passed (${report.coverageStats.skipped} skipped)`);
    lines.push('');
  }

  lines.push('## Iterations');
  lines.push('');

  for (const iteration of report.iterations) {
    lines.push(`### Iteration ${iteration.iteration}`);
    lines.push(`- Result: ${iteration.status}`);
    lines.push(`- Diagnostics: ${iteration.diagnostics.length}`);
    lines.push(`- Repairs applied: ${iteration.repairs.length}`);
    for (const repair of iteration.repairs) {
      lines.push(`- Repair: ${repair}`);
    }
    lines.push('');
  }

  lines.push('## Diagnostic Summary');
  lines.push('');
  for (const [category, count] of Object.entries(report.diagnosticSummary.byCategory || {})) {
    lines.push(`- ${category}: ${count}`);
  }
  lines.push('');
  lines.push('## Validation Constraints');
  lines.push('');
  lines.push(`- DTO files scanned with validation annotations: ${report.validationConstraintFiles}`);

  return `${lines.join('\n')}\n`;
}

async function main() {
  await ensureSelfHealFiles();

  const args = parseArgs(process.argv.slice(2));
  const pipelineConfig = await loadPipelineConfig();
  const maxIterations = args.maxIterations || pipelineConfig.maxIterations || 3;
  const validationConstraints = await scanValidationConstraints(args.workspacePath);

  const report = {
    workspacePath: args.workspacePath.replace(/\\/g, '/'),
    generatedAt: new Date().toISOString(),
    mode: args.ci ? 'ci' : 'local',
    validationConstraintFiles: validationConstraints.length,
    iterations: [],
    coverageStats: null,
    finalStatus: 'failed',
    unexpected500: false,
    diagnosticSummary: { total: 0, byCategory: {} },
    repairHistory: []
  };

  for (let iteration = 1; iteration <= maxIterations; iteration += 1) {
    const iterationReport = {
      iteration,
      status: 'passed',
      stages: [],
      diagnostics: [],
      repairs: []
    };

    for (const stage of pipelineConfig.stages) {
      const stageResult = runCommand(stage.command, args.workspacePath);
      iterationReport.stages.push({
        id: stage.id,
        command: stage.command,
        status: stageResult.status,
        outputFile: null
      });

      if (detectUnexpected500(stageResult)) {
        report.unexpected500 = true;
      }

      const stageLogName = `iteration-${iteration}-${stage.id}.log`;
      const stageLogPath = path.join(getReportsDir(), stageLogName);
      await fs.writeFile(stageLogPath, stageResult.output, 'utf8');
      iterationReport.stages[iterationReport.stages.length - 1].outputFile = stageLogPath.replace(/\\/g, '/');

      if (stageResult.status === 0) {
        if (stage.id === 'contract') {
          report.coverageStats = parseCoverageStats(stageResult.output);
        }
        continue;
      }

      iterationReport.status = 'failed';
      const diagnostics = stage.id === 'contract'
        ? analyzeSchemathesisLog(stageResult.output)
        : analyzeHttpyacLog(stageResult.output);
      iterationReport.diagnostics.push(...diagnostics);
      break;
    }

    if (iterationReport.status === 'passed') {
      report.iterations.push(iterationReport);
      report.finalStatus = report.unexpected500 ? 'passed-with-500-warnings' : 'passed';
      break;
    }

    const repairResult = await applyRepairs(iterationReport.diagnostics);
    iterationReport.repairs.push(...repairResult.repairs);
    if (repairResult.repairs.length > 0) {
      report.repairHistory.push({
        iteration,
        generatedAt: new Date().toISOString(),
        repairs: repairResult.repairs
      });
    }
    report.iterations.push(iterationReport);

    if (!repairResult.changed) {
      break;
    }
  }

  const allDiagnostics = report.iterations.flatMap((iteration) => iteration.diagnostics);
  report.diagnosticSummary = summarizeDiagnostics(allDiagnostics);
  await writeReport('latest.json', `${JSON.stringify(report, null, 2)}\n`);
  await writeReport('latest.md', toMarkdown(report));
  await writeReport('validation-constraints.json', `${JSON.stringify(validationConstraints, null, 2)}\n`);
  await writeReport('repair-history.json', `${JSON.stringify(report.repairHistory, null, 2)}\n`);

  if (!report.finalStatus.startsWith('passed')) {
    process.exit(1);
  }
}

main().catch((error) => {
  console.error(error.stack || error.message || error);
  process.exit(1);
});