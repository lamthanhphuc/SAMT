import fs from 'node:fs/promises';
import path from 'node:path';
import { parseArgs, writeJson, writeText } from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  reportName: 'architecture-health'
});

const workspacePath = path.resolve(options.workspace);
const reportsDir = path.join(workspacePath, '.self-heal', 'reports');
const jsonReport = path.join(reportsDir, `${options.reportName}.json`);
const markdownReport = path.join(reportsDir, `${options.reportName}.md`);

async function collectFiles(rootDir, predicate) {
  const output = [];
  const stack = [rootDir];

  while (stack.length > 0) {
    const current = stack.pop();
    const entries = await fs.readdir(current, { withFileTypes: true });
    for (const entry of entries) {
      if (['target', '.git', 'node_modules'].includes(entry.name)) {
        continue;
      }
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
        continue;
      }
      if (predicate(fullPath)) {
        output.push(fullPath);
      }
    }
  }

  return output;
}

function relative(filePath) {
  return path.relative(workspacePath, filePath).replace(/\\/g, '/');
}

function countMatches(text, regex) {
  return [...text.matchAll(regex)].length;
}

function normalizedFingerprint(body) {
  return body
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/\/\/.*$/gm, '')
    .replace(/\s+/g, ' ')
    .replace(/"[^"]*"/g, '"str"')
    .replace(/\b\d+\b/g, '0')
    .trim();
}

async function main() {
  const javaFiles = await collectFiles(workspacePath, (filePath) => filePath.endsWith('.java'));
  const yamlFiles = await collectFiles(workspacePath, (filePath) => /\.(ya?ml|properties)$/i.test(filePath));
  const findings = [];
  const duplicateBodies = new Map();

  for (const filePath of javaFiles) {
    const content = await fs.readFile(filePath, 'utf8');
    const rel = relative(filePath);
    const isController = /[\\/]controller[\\/]/i.test(filePath) && /@RestController|@Controller\b/.test(content);
    const isDto = /class .*Dto\b|record .*Dto\b|package .*dto/i.test(content);
    const isService = /@Service\b/.test(content) || /class .*Service/i.test(path.basename(filePath));

    if (isController) {
      if (/Repository\s+[a-zA-Z_][a-zA-Z0-9_]*;/.test(content)) {
        findings.push({ type: 'direct repository usage in controllers', severity: 'high', file: rel });
      }
      if (!/Service\s+[a-zA-Z_][a-zA-Z0-9_]*;/.test(content)) {
        findings.push({ type: 'missing service layer', severity: 'medium', file: rel });
      }
      if (/for\s*\(|while\s*\(|\.stream\(|if\s*\([^)]{20,}\)|RestTemplate|WebClient|Repository\./.test(content)) {
        findings.push({ type: 'controllers with business logic', severity: 'medium', file: rel });
      }
      if ((/@(?:Post|Put|Patch)Mapping[\s\S]{0,400}@RequestBody(?!\s+@Valid)/.test(content) || /@RequestBody\s+(?!@Valid)/.test(content)) && !/GlobalExceptionHandler|ApiResponseBodyAdvice/.test(rel)) {
        findings.push({ type: 'missing validation', severity: 'medium', file: rel });
      }
      if (!/ApiResponse|ProblemDetail|ResponseEntity/.test(content)) {
        findings.push({ type: 'inconsistent response formats', severity: 'medium', file: rel });
      }
    }

    if (isDto && /import .*entity\./i.test(content)) {
      findings.push({ type: 'DTO exposing entity models', severity: 'high', file: rel });
    }

    if (isService) {
      const mutatingMethods = countMatches(content, /(save\(|delete\(|update\(|create\()/g);
      if (mutatingMethods > 0 && !/@Transactional/.test(content)) {
        findings.push({ type: 'missing transactions', severity: 'medium', file: rel });
      }
    }

    const methodBodies = [...content.matchAll(/(?:public|protected|private)\s+[\w<>\[\], ?]+\s+\w+\([^)]*\)\s*\{([\s\S]*?)\n\s*\}/g)];
    for (const match of methodBodies) {
      const fingerprint = normalizedFingerprint(match[1]);
      if (fingerprint.length < 160) {
        continue;
      }
      const files = duplicateBodies.get(fingerprint) || [];
      files.push(rel);
      duplicateBodies.set(fingerprint, files);
    }
  }

  for (const [fingerprint, files] of duplicateBodies.entries()) {
    const uniqueFiles = Array.from(new Set(files));
    if (uniqueFiles.length > 1) {
      findings.push({
        type: 'duplicated logic across services',
        severity: 'medium',
        file: uniqueFiles.join(', '),
        sample: fingerprint.slice(0, 140)
      });
    }
  }

  for (const filePath of [...javaFiles, ...yamlFiles]) {
    const content = await fs.readFile(filePath, 'utf8');
    const rel = relative(filePath);
    if (/(secret|token|password|api[-_]?key)\s*[:=]\s*["'][^$\n\r]{8,}["']/i.test(content)) {
      findings.push({ type: 'hardcoded secrets', severity: 'high', file: rel });
    }
  }

  const weights = { high: 5, medium: 1, low: 1 };
  const penalty = findings.reduce((total, finding) => total + (weights[finding.severity] || 3), 0);
  const score = Math.max(0, 100 - penalty);

  const report = {
    generatedAt: new Date().toISOString(),
    score,
    totals: {
      javaFiles: javaFiles.length,
      findings: findings.length,
      bySeverity: findings.reduce((acc, finding) => {
        acc[finding.severity] = (acc[finding.severity] || 0) + 1;
        return acc;
      }, {})
    },
    findings
  };

  const lines = [
    '# Architecture Health',
    '',
    `- Score: ${report.score}/100`,
    `- Java files scanned: ${report.totals.javaFiles}`,
    `- Findings: ${report.totals.findings}`,
    ''
  ];

  for (const finding of findings) {
    lines.push(`- [${finding.severity}] ${finding.type}: ${finding.file}`);
  }
  lines.push('');

  await writeJson(jsonReport, report);
  await writeText(markdownReport, `${lines.join('\n')}\n`);
  process.stdout.write(`Wrote ${relative(jsonReport)}\n`);
}

main().catch(async (error) => {
  const failure = {
    generatedAt: new Date().toISOString(),
    status: 'failed',
    error: error.stack || error.message || String(error)
  };
  await writeJson(jsonReport, failure);
  await writeText(markdownReport, `# Architecture Health\n\n\`\`\`text\n${failure.error}\n\`\`\`\n`);
  process.stderr.write(`${failure.error}\n`);
  process.exit(1);
});