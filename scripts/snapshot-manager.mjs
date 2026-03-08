import crypto from 'node:crypto';
import fs from 'node:fs/promises';
import path from 'node:path';
import { ensureSelfHealFiles, writeReport } from './self-heal-overrides.mjs';

const trackedFiles = [
  'openapi.yaml',
  'tests/api-auth.http',
  'tests/api-users.http',
  'tests/api-groups.http',
  'tests/api-semesters.http',
  'tests/api-project-configs.http',
  'tests/api-permissions.http',
  'tests/bootstrap.http',
  'tests/fixtures.http',
  '.self-heal/openapi-overrides.json',
  '.self-heal/generator-overrides.json',
  'scripts/schema-payload-generator.mjs',
  'scripts/generate-httpyac-tests.mjs'
];

function sha256(content) {
  return crypto.createHash('sha256').update(content).digest('hex');
}

function parseArgs(argv) {
  return {
    action: argv[0] || 'compare',
    workspacePath: process.cwd()
  };
}

async function ensureDir(dirPath) {
  await fs.mkdir(dirPath, { recursive: true });
}

async function fileExists(filePath) {
  try {
    await fs.access(filePath);
    return true;
  } catch {
    return false;
  }
}

async function buildManifest(workspacePath, label) {
  const entries = [];

  for (const relativePath of trackedFiles) {
    const absolutePath = path.join(workspacePath, relativePath);
    const exists = await fileExists(absolutePath);
    if (!exists) {
      entries.push({ path: relativePath, exists: false });
      continue;
    }

    const content = await fs.readFile(absolutePath);
    entries.push({
      path: relativePath,
      exists: true,
      sha256: sha256(content),
      size: content.length
    });
  }

  return {
    label,
    generatedAt: new Date().toISOString(),
    entries
  };
}

function manifestMap(manifest) {
  return new Map((manifest?.entries || []).map((entry) => [entry.path, entry]));
}

function diffManifests(previousManifest, currentManifest) {
  const previous = manifestMap(previousManifest);
  const current = manifestMap(currentManifest);
  const filePaths = new Set([...previous.keys(), ...current.keys()]);

  const diff = {
    added: [],
    removed: [],
    modified: [],
    unchanged: []
  };

  for (const filePath of Array.from(filePaths).sort()) {
    const before = previous.get(filePath);
    const after = current.get(filePath);

    if (!before?.exists && after?.exists) {
      diff.added.push(filePath);
      continue;
    }

    if (before?.exists && !after?.exists) {
      diff.removed.push(filePath);
      continue;
    }

    if ((before?.sha256 || null) !== (after?.sha256 || null)) {
      diff.modified.push(filePath);
      continue;
    }

    diff.unchanged.push(filePath);
  }

  return diff;
}

function toMarkdown(report) {
  const lines = [
    '# Regression Snapshot Report',
    '',
    `- Drift detected: ${report.driftDetected ? 'yes' : 'no'}`,
    `- Baseline manifest: ${report.baselinePath}`,
    `- Latest manifest: ${report.latestPath}`,
    ''
  ];

  for (const key of ['added', 'removed', 'modified']) {
    if (report.diff[key].length === 0) {
      continue;
    }

    lines.push(`## ${key[0].toUpperCase()}${key.slice(1)}`);
    lines.push('');
    for (const filePath of report.diff[key]) {
      lines.push(`- ${filePath}`);
    }
    lines.push('');
  }

  if (!report.driftDetected) {
    lines.push('No snapshot drift detected.');
    lines.push('');
  }

  return `${lines.join('\n')}\n`;
}

async function writeManifest(workspacePath, label, manifest) {
  const snapshotsDir = path.join(workspacePath, '.self-heal', 'snapshots', label);
  await ensureDir(snapshotsDir);
  const manifestPath = path.join(snapshotsDir, 'manifest.json');
  await fs.writeFile(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`, 'utf8');
  return manifestPath;
}

async function readManifest(filePath) {
  const raw = await fs.readFile(filePath, 'utf8');
  return JSON.parse(raw);
}

async function main() {
  await ensureSelfHealFiles();
  const options = parseArgs(process.argv.slice(2));
  const baselineManifestPath = path.join(options.workspacePath, '.self-heal', 'snapshots', 'baseline', 'manifest.json');
  const latestManifest = await buildManifest(options.workspacePath, 'latest');
  const latestManifestPath = await writeManifest(options.workspacePath, 'latest', latestManifest);

  if (options.action === 'update') {
    const baselineManifestPathWritten = await writeManifest(options.workspacePath, 'baseline', {
      ...latestManifest,
      label: 'baseline'
    });

    const report = {
      generatedAt: new Date().toISOString(),
      driftDetected: false,
      baselinePath: baselineManifestPathWritten.replace(/\\/g, '/'),
      latestPath: latestManifestPath.replace(/\\/g, '/'),
      diff: { added: [], removed: [], modified: [], unchanged: trackedFiles.slice() }
    };

    await writeReport('regression-drift-report.json', `${JSON.stringify(report, null, 2)}\n`);
    await writeReport('regression-drift-report.md', toMarkdown(report));
    return;
  }

  if (!(await fileExists(baselineManifestPath))) {
    const report = {
      generatedAt: new Date().toISOString(),
      driftDetected: true,
      baselinePath: baselineManifestPath.replace(/\\/g, '/'),
      latestPath: latestManifestPath.replace(/\\/g, '/'),
      diff: {
        added: trackedFiles.slice(),
        removed: [],
        modified: [],
        unchanged: []
      },
      error: 'Snapshot baseline is missing. Run npm run snapshots:update and commit the baseline manifest.'
    };

    await writeReport('regression-drift-report.json', `${JSON.stringify(report, null, 2)}\n`);
    await writeReport('regression-drift-report.md', toMarkdown(report));
    process.stderr.write(`${report.error}\n`);
    process.exit(1);
  }

  const baselineManifest = await readManifest(baselineManifestPath);
  const diff = diffManifests(baselineManifest, latestManifest);
  const driftDetected = diff.added.length > 0 || diff.removed.length > 0 || diff.modified.length > 0;

  const report = {
    generatedAt: new Date().toISOString(),
    driftDetected,
    baselinePath: baselineManifestPath.replace(/\\/g, '/'),
    latestPath: latestManifestPath.replace(/\\/g, '/'),
    diff
  };

  await writeReport('regression-drift-report.json', `${JSON.stringify(report, null, 2)}\n`);
  await writeReport('regression-drift-report.md', toMarkdown(report));

  if (driftDetected) {
    process.stderr.write('Regression snapshot drift detected. Update the baseline intentionally with npm run snapshots:update and commit the baseline manifest.\n');
    process.exit(1);
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message || String(error)}\n`);
  process.exit(1);
});
