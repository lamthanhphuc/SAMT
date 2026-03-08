import fs from 'node:fs/promises';
import path from 'node:path';

const supportedAnnotations = [
  'NotBlank',
  'NotNull',
  'Size',
  'Pattern',
  'Email',
  'Min',
  'Max',
  'Positive',
  'PositiveOrZero'
];

async function collectJavaFiles(rootDir) {
  const entries = await fs.readdir(rootDir, { withFileTypes: true });
  const out = [];

  for (const entry of entries) {
    const fullPath = path.join(rootDir, entry.name);
    if (entry.isDirectory()) {
      out.push(...await collectJavaFiles(fullPath));
      continue;
    }

    if (entry.isFile() && fullPath.endsWith('.java')) {
      out.push(fullPath);
    }
  }

  return out;
}

function parseFieldAnnotations(lines) {
  const results = [];
  let pendingAnnotations = [];

  for (const rawLine of lines) {
    const line = rawLine.trim();
    const annotationMatch = line.match(/^@([A-Za-z0-9_]+)(\((.*)\))?$/);
    if (annotationMatch && supportedAnnotations.includes(annotationMatch[1])) {
      pendingAnnotations.push({
        annotation: annotationMatch[1],
        args: annotationMatch[3] || ''
      });
      continue;
    }

    const fieldMatch = line.match(/^(private|protected|public)\s+[^=;]+\s+([A-Za-z0-9_]+);$/);
    if (fieldMatch && pendingAnnotations.length > 0) {
      results.push({
        field: fieldMatch[2],
        annotations: pendingAnnotations
      });
      pendingAnnotations = [];
      continue;
    }

    if (line && !line.startsWith('@')) {
      pendingAnnotations = [];
    }
  }

  return results;
}

export async function scanValidationConstraints(workspacePath) {
  const files = await collectJavaFiles(workspacePath);
  const result = [];

  for (const filePath of files) {
    if (!filePath.includes(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`)) {
      continue;
    }

    const raw = await fs.readFile(filePath, 'utf8');
    const lines = raw.split(/\r?\n/);
    const fields = parseFieldAnnotations(lines);
    if (fields.length === 0) {
      continue;
    }

    result.push({
      file: path.relative(workspacePath, filePath).replace(/\\/g, '/'),
      fields
    });
  }

  return result;
}

if (import.meta.url === `file://${process.argv[1]?.replace(/\\/g, '/')}`) {
  const workspacePath = path.resolve(process.argv[2] || '.');
  const constraints = await scanValidationConstraints(workspacePath);
  process.stdout.write(`${JSON.stringify(constraints, null, 2)}\n`);
}