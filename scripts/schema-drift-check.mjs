import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { ensureSelfHealFiles, writeReport } from './self-heal-overrides.mjs';

const watchedFiles = [
  'openapi.yaml',
  'tests/api-auth.http',
  'tests/api-users.http',
  'tests/api-groups.http',
  'tests/api-semesters.http',
  'tests/api-project-configs.http',
  'tests/api-permissions.http',
  '.self-heal/openapi-overrides.json',
  '.self-heal/generator-overrides.json'
];

function parseArgs(argv) {
  const options = {
    workspacePath: process.cwd(),
    ci: false
  };

  for (const arg of argv) {
    if (arg === '--ci') {
      options.ci = true;
      continue;
    }

    if (arg.startsWith('--workspace=')) {
      options.workspacePath = path.resolve(arg.slice('--workspace='.length));
    }
  }

  return options;
}

function runCommand(command, cwd) {
  const result = spawnSync(command, {
    cwd,
    shell: process.platform === 'win32' ? 'powershell.exe' : true,
    encoding: 'utf8'
  });

  return {
    command,
    status: result.status ?? 1,
    stdout: result.stdout || '',
    stderr: result.stderr || '',
    output: `${result.stdout || ''}${result.stderr || ''}`
  };
}

function runGit(args, cwd) {
  const result = spawnSync('git', args, {
    cwd,
    encoding: 'utf8'
  });

  return {
    status: result.status ?? 1,
    stdout: result.stdout || '',
    stderr: result.stderr || ''
  };
}

function toMarkdown(report) {
  const lines = [
    '# Schema Drift Alert',
    '',
    `- Drift detected: ${report.driftDetected ? 'yes' : 'no'}`,
    `- OpenAPI changed: ${report.openapiChanged ? 'yes' : 'no'}`,
    `- Generated tests changed: ${report.generatedTestsChanged ? 'yes' : 'no'}`,
    `- Override state changed: ${report.overrideStateChanged ? 'yes' : 'no'}`,
    ''
  ];

  if (report.changedFiles.length > 0) {
    lines.push('## Changed Files');
    lines.push('');
    for (const filePath of report.changedFiles) {
      lines.push(`- ${filePath}`);
    }
    lines.push('');
  }

  lines.push('## Commands');
  lines.push('');
  for (const command of report.commands) {
    lines.push(`- ${command.command}: ${command.status === 0 ? 'ok' : 'failed'}`);
  }

  return `${lines.join('\n')}\n`;
}

async function main() {
  await ensureSelfHealFiles();
  const options = parseArgs(process.argv.slice(2));

  const commands = [
    runCommand('npm run openapi:generate', options.workspacePath),
    runCommand('npm run tests:generate', options.workspacePath)
  ];

  const failedCommand = commands.find((command) => command.status !== 0);
  if (failedCommand) {
    const report = {
      generatedAt: new Date().toISOString(),
      driftDetected: true,
      openapiChanged: false,
      generatedTestsChanged: false,
      overrideStateChanged: false,
      changedFiles: [],
      commands: commands.map((command) => ({ command: command.command, status: command.status })),
      error: failedCommand.output
    };

    await writeReport('schema-drift-alert.json', `${JSON.stringify(report, null, 2)}\n`);
    await writeReport('schema-drift-alert.md', toMarkdown(report));
    process.stderr.write(failedCommand.output);
    process.exit(1);
  }

  const diffResult = runGit(['status', '--porcelain', '--', ...watchedFiles], options.workspacePath);
  if (diffResult.status !== 0) {
    process.stderr.write(diffResult.stderr || 'Unable to query git status for drift detection.\n');
    process.exit(1);
  }

  const changedFiles = diffResult.stdout
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => line.slice(3).replace(/\\/g, '/'));

  const report = {
    generatedAt: new Date().toISOString(),
    driftDetected: changedFiles.length > 0,
    openapiChanged: changedFiles.includes('openapi.yaml'),
    generatedTestsChanged: changedFiles.some((filePath) => filePath.startsWith('tests/api-')),
    overrideStateChanged: changedFiles.some((filePath) => filePath.startsWith('.self-heal/')),
    changedFiles,
    commands: commands.map((command) => ({ command: command.command, status: command.status }))
  };

  await writeReport('schema-drift-alert.json', `${JSON.stringify(report, null, 2)}\n`);
  await writeReport('schema-drift-alert.md', toMarkdown(report));

  if (report.driftDetected) {
    process.stderr.write('Schema drift detected. Regenerate and commit the synchronized OpenAPI and generated test artifacts.\n');
    process.exit(1);
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message || String(error)}\n`);
  process.exit(1);
});
