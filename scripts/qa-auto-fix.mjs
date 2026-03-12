import fs from 'node:fs';
import path from 'node:path';
import { analyzeHttpyacLog, analyzeSchemathesisLog, summarizeDiagnostics } from './self-heal-analyzer.mjs';
import { applyRepairs } from './self-heal-repair.mjs';
import { parseArgs, safeJsonParse, writeJson, writeText } from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  reportName: 'auto-fix',
  sourceReport: '.self-heal/reports/qa-system-latest.json',
  apply: 'true'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const markdownReport = path.join(reportsDir, `${options.reportName}.md`);

function readTextIfExists(filePath) {
  return fs.existsSync(filePath) ? fs.readFileSync(filePath, 'utf8') : null;
}

function readJsonIfExists(filePath) {
  const raw = readTextIfExists(filePath);
  return raw ? safeJsonParse(raw) : null;
}

function normalizeCategory(diagnostic) {
  const evidence = String(diagnostic.evidence || '').toLowerCase();

  if (/jiraemail|required/i.test(diagnostic.evidence || '')) {
    return 'missing required fields';
  }
  if (/enum|unsupported status|role/i.test(evidence) && diagnostic.category === 'invalid_payload_generated') {
    return 'invalid enum';
  }
  if (/accesstoken|refreshtoken|response\.body\.accessToken/i.test(diagnostic.evidence || '')) {
    return 'incorrect token extraction';
  }
  if (diagnostic.source === 'schemathesis' && diagnostic.category === 'schema_mismatch') {
    return /undocumented http status code|response violates schema/i.test(diagnostic.evidence || '')
      ? 'contract drift'
      : 'schema mismatch';
  }
  if (diagnostic.source === 'httpyac' && diagnostic.category === 'schema_mismatch') {
    return 'incorrect status codes';
  }
  if (diagnostic.category === 'invalid_payload_generated' || diagnostic.category === 'missing_fixture_data') {
    return 'test payload errors';
  }

  return diagnostic.category.replace(/_/g, ' ');
}

function collectDiagnostics(report) {
  const diagnostics = [];
  const stages = Array.isArray(report?.stages) ? report.stages : [];
  const contractLogs = new Set();
  const regressionLogs = new Set();

  for (const stage of stages) {
    if (!stage?.outputFile) {
      continue;
    }

    const logPath = path.join(workspacePath, stage.outputFile);
    if (!fs.existsSync(logPath)) {
      continue;
    }

    if (stage.id.includes('contract') || stage.id === 'fuzz') {
      contractLogs.add(logPath);
    }
    if (stage.id === 'regression' || stage.id === 'smoke') {
      regressionLogs.add(logPath);
    }
  }

  for (const logPath of contractLogs) {
    diagnostics.push(...analyzeSchemathesisLog(readTextIfExists(logPath) || ''));
  }
  for (const logPath of regressionLogs) {
    diagnostics.push(...analyzeHttpyacLog(readTextIfExists(logPath) || ''));
  }

  return diagnostics;
}

function patchFile(filePath, transformer, changes) {
  if (!fs.existsSync(filePath)) {
    return false;
  }

  const before = fs.readFileSync(filePath, 'utf8');
  const after = transformer(before);
  if (after === before) {
    return false;
  }

  fs.writeFileSync(filePath, after, 'utf8');
  changes.push(path.relative(workspacePath, filePath).replace(/\\/g, '/'));
  return true;
}

function replaceAllSafe(input, replacements) {
  let output = input;
  for (const [search, replacement] of replacements) {
    output = output.split(search).join(replacement);
  }
  return output;
}

function applySafePatches() {
  const changedFiles = [];
  const reviewNotes = [];
  let changed = false;

  const tokenTargets = [
    path.join(workspacePath, 'api-smoke.http'),
    path.join(workspacePath, 'api-regression.http'),
    path.join(workspacePath, 'tests', 'bootstrap.http'),
    path.join(workspacePath, 'tests', 'fixtures.http'),
    path.join(workspacePath, 'tests', 'api-auth.http'),
    path.join(workspacePath, 'tests', 'api-users.http'),
    path.join(workspacePath, 'tests', 'api-groups.http'),
    path.join(workspacePath, 'tests', 'api-semesters.http'),
    path.join(workspacePath, 'tests', 'api-project-configs.http'),
    path.join(workspacePath, 'scripts', 'run-schemathesis-docker.mjs')
  ];

  for (const filePath of tokenTargets) {
    changed = patchFile(
      filePath,
      (content) => replaceAllSafe(content, [
        ['response.body.accessToken', 'response.body.data.accessToken'],
        ['response.body.refreshToken', 'response.body.data.refreshToken'],
        ['body?.accessToken', 'body?.data?.accessToken ?? body?.accessToken']
      ]),
      changedFiles
    ) || changed;
  }

  const jiraTargets = [
    path.join(workspacePath, 'api-smoke.http'),
    path.join(workspacePath, 'api-regression.http'),
    path.join(workspacePath, 'tests', 'api-project-configs.http'),
    path.join(workspacePath, 'scripts', 'generate-httpyac-tests.mjs')
  ];

  for (const filePath of jiraTargets) {
    changed = patchFile(
      filePath,
      (content) => content.replace(
        /(jiraHostUrl[^\n]*\n)(\s*)(jiraApiToken)/g,
        (match, hostLine, indent, nextKey) => match.includes('jiraEmail')
          ? match
          : `${hostLine}${indent}jiraEmail: {{ jiraEmail }}\n${indent}${nextKey}`
      ).replace(
        /("jiraHostUrl"\s*:\s*[^\n]*\n)(\s*)("jiraApiToken")/g,
        (match, hostLine, indent, nextKey) => match.includes('jiraEmail')
          ? match
          : `${hostLine}${indent}"jiraEmail": "{{ jiraEmail }}",\n${indent}${nextKey}`
      ).replace(
        /(jiraHostUrl:\s*[^,\n]+,\n)(\s*)(jiraApiToken:)/g,
        (match, hostLine, indent, nextKey) => match.includes('jiraEmail')
          ? match
          : `${hostLine}${indent}jiraEmail: jiraEmail,\n${indent}${nextKey}`
      ),
      changedFiles
    ) || changed;
  }

  if (!changed) {
    reviewNotes.push('No direct file patch matched the current failure evidence; only override-based repairs may have applied.');
  }

  return { changed, changedFiles, reviewNotes };
}

function toMarkdown(report) {
  const lines = [
    '# Auto Fix Report',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Source report: ${report.sourceReport}`,
    `- Diagnostics: ${report.summary.total}`,
    `- Applied changes: ${report.appliedChanges.length}`,
    `- Override repairs: ${report.overrideRepairs.length}`,
    ''
  ];

  lines.push('## Classification');
  lines.push('');
  for (const item of report.classification) {
    lines.push(`- ${item.category}: ${item.count}`);
  }
  lines.push('');

  if (report.appliedChanges.length > 0) {
    lines.push('## Applied File Changes');
    lines.push('');
    for (const file of report.appliedChanges) {
      lines.push(`- ${file}`);
    }
    lines.push('');
  }

  if (report.overrideRepairs.length > 0) {
    lines.push('## Override Repairs');
    lines.push('');
    for (const repair of report.overrideRepairs) {
      lines.push(`- ${repair}`);
    }
    lines.push('');
  }

  if (report.reviewNotes.length > 0) {
    lines.push('## Review Notes');
    lines.push('');
    for (const note of report.reviewNotes) {
      lines.push(`- ${note}`);
    }
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function main() {
  const sourceReportPath = path.resolve(workspacePath, options.sourceReport);
  const report = readJsonIfExists(sourceReportPath) || { stages: [] };
  const diagnostics = collectDiagnostics(report);
  const classified = diagnostics.map((diagnostic) => ({
    ...diagnostic,
    requestedCategory: normalizeCategory(diagnostic)
  }));

  const classificationMap = new Map();
  for (const diagnostic of classified) {
    classificationMap.set(
      diagnostic.requestedCategory,
      (classificationMap.get(diagnostic.requestedCategory) || 0) + 1
    );
  }

  const applyEnabled = String(options.apply).toLowerCase() === 'true' && classified.length > 0;
  const safePatchResult = applyEnabled
    ? applySafePatches()
    : { changed: false, changedFiles: [], reviewNotes: classified.length === 0 ? ['No diagnostics available, so no safe patches were attempted.'] : ['File patching disabled by --apply=false'] };
  const overrideRepairResult = applyEnabled
    ? await applyRepairs(classified)
    : { changed: false, repairs: [] };

  const output = {
    generatedAt: new Date().toISOString(),
    sourceReport: path.relative(workspacePath, sourceReportPath).replace(/\\/g, '/'),
    summary: summarizeDiagnostics(classified),
    classification: Array.from(classificationMap.entries())
      .map(([category, count]) => ({ category, count }))
      .sort((left, right) => right.count - left.count || left.category.localeCompare(right.category)),
    findings: classified.map((diagnostic) => ({
      source: diagnostic.source,
      operationKey: diagnostic.operationKey,
      category: diagnostic.requestedCategory,
      rootCause: diagnostic.evidence,
      minimalSafeChange: diagnostic.requestedCategory === 'incorrect token extraction'
        ? 'Patch token extraction to use response.body.data.* before falling back to legacy fields.'
        : diagnostic.requestedCategory === 'missing required fields'
          ? 'Patch test payload templates to include the missing field while preserving existing request shape.'
          : diagnostic.requestedCategory === 'contract drift'
            ? 'Patch OpenAPI overrides with learned response/status compatibility instead of changing backend code blindly.'
            : 'Patch only the failing test/schema surface and keep the backend implementation unchanged until evidence points to runtime defects.'
    })),
    appliedChanges: safePatchResult.changedFiles,
    overrideRepairs: overrideRepairResult.repairs || [],
    reviewNotes: safePatchResult.reviewNotes,
    changed: safePatchResult.changed || Boolean(overrideRepairResult.changed)
  };

  await writeJson(jsonReport, output);
  await writeText(markdownReport, toMarkdown(output));
  process.stdout.write(`Wrote ${path.relative(workspacePath, jsonReport).replace(/\\/g, '/')}\n`);
}

main().catch(async (error) => {
  const failure = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(jsonReport, failure);
  await writeText(markdownReport, `# Auto Fix Report\n\n\`\`\`text\n${failure.error}\n\`\`\`\n`);
  process.stderr.write(`${failure.error}\n`);
  process.exit(1);
});