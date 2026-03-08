import { spawnSync } from 'node:child_process';
import fs from 'node:fs/promises';
import path from 'node:path';
import { ensureSelfHealFiles, writeReport } from './self-heal-overrides.mjs';

const endpointHints = [
  {
    match: (endpoint) => endpoint.startsWith('/api/auth') || endpoint.startsWith('/api/admin/users') || endpoint.startsWith('/api/admin/audit') || endpoint === '/.well-known/jwks.json',
    service: 'identity-service',
    controllerHints: ['AuthController.java', 'AdminController.java', 'JwksController.java'],
    serviceHints: ['AuthService.java', 'UserAdminService.java', 'JwtService.java'],
    exceptionHints: ['GlobalExceptionHandler.java']
  },
  {
    match: (endpoint) => endpoint.startsWith('/api/groups') || endpoint.startsWith('/api/users') || endpoint.startsWith('/api/semesters'),
    service: 'user-group-service',
    controllerHints: ['GroupController.java', 'GroupMemberController.java', 'UserController.java', 'SemesterController.java'],
    serviceHints: ['GroupService.java', 'UserService.java', 'SemesterService.java'],
    exceptionHints: ['GlobalExceptionHandler.java']
  },
  {
    match: (endpoint) => endpoint.startsWith('/api/project-configs'),
    service: 'project-config-service',
    controllerHints: ['ProjectConfigController.java', 'InternalConfigController.java'],
    serviceHints: ['ProjectConfigService.java', 'GitHubVerificationService.java', 'TokenEncryptionService.java'],
    exceptionHints: ['GlobalExceptionHandler.java']
  }
];

function parseArgs(argv) {
  const options = {
    workspacePath: process.cwd(),
    stage: 'unknown',
    logFile: null,
    reportFile: null
  };

  for (const arg of argv) {
    if (arg.startsWith('--workspace=')) {
      options.workspacePath = path.resolve(arg.slice('--workspace='.length));
      continue;
    }

    if (arg.startsWith('--stage=')) {
      options.stage = arg.slice('--stage='.length);
      continue;
    }

    if (arg.startsWith('--log=')) {
      options.logFile = path.resolve(arg.slice('--log='.length));
      continue;
    }

    if (arg.startsWith('--report=')) {
      options.reportFile = path.resolve(arg.slice('--report='.length));
    }
  }

  return options;
}

async function collectFiles(rootDir) {
  const output = [];
  const stack = [rootDir];

  while (stack.length > 0) {
    const current = stack.pop();
    const entries = await fs.readdir(current, { withFileTypes: true });

    for (const entry of entries) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
        continue;
      }

      if (entry.isFile() && entry.name.endsWith('.java')) {
        output.push(fullPath);
      }
    }
  }

  return output;
}

function detectEndpoint(logContent) {
  const endpointMatch = logContent.match(/(?:curl -X [A-Z]+ .*? )http:\/\/[^/]+(\/[^\s'`]+)/);
  if (endpointMatch) {
    return endpointMatch[1].split('?')[0];
  }

  const httpyacMatch = logContent.match(/[A-Z]+\s+http:\/\/[^/]+(\/[^\s]+)/);
  if (httpyacMatch) {
    return httpyacMatch[1].split('?')[0];
  }

  return null;
}

function detectFailureKind(logContent) {
  if (/NullPointerException/i.test(logContent)) return 'null-check';
  if (/NoSuchElementException|EntityNotFound|not found/i.test(logContent)) return 'missing-repository-check';
  if (/IllegalArgumentException/i.test(logContent) && /enum/i.test(logContent)) return 'incorrect-enum-mapping';
  if (/ConstraintViolationException|MethodArgumentNotValidException|Validation failed/i.test(logContent)) return 'validation-guard';
  if (/HTTP\/1\.1\s+500\b|"statusCode"\s*:\s*500|Internal Server Error/i.test(logContent)) return 'server-error-guard';
  return 'defensive-guard';
}

function extractStackFrames(logContent) {
  const frames = [];
  const pattern = /at\s+([\w.$]+)\(([^:]+):(\d+)\)/g;

  for (const match of logContent.matchAll(pattern)) {
    frames.push({
      className: match[1],
      fileName: match[2],
      line: Number(match[3])
    });
  }

  return frames;
}

function relative(workspacePath, absolutePath) {
  return path.relative(workspacePath, absolutePath).replace(/\\/g, '/');
}

function findCandidatesByHint(javaFiles, workspacePath, endpoint) {
  if (!endpoint) {
    return [];
  }

  const hint = endpointHints.find((entry) => entry.match(endpoint));
  if (!hint) {
    return [];
  }

  const desiredNames = new Set([...hint.controllerHints, ...hint.serviceHints, ...hint.exceptionHints]);
  return javaFiles
    .filter((filePath) => filePath.includes(`${path.sep}${hint.service}${path.sep}`) && desiredNames.has(path.basename(filePath)))
    .map((filePath) => relative(workspacePath, filePath));
}

function findCandidatesByStack(javaFiles, workspacePath, stackFrames) {
  const matches = [];

  for (const frame of stackFrames) {
    const exact = javaFiles.find((filePath) => path.basename(filePath) === frame.fileName);
    if (exact) {
      matches.push(relative(workspacePath, exact));
    }
  }

  return Array.from(new Set(matches));
}

function proposalForKind(kind, endpoint, candidates) {
  const primaryTarget = candidates[0] || 'relevant controller/service file';

  switch (kind) {
    case 'null-check':
      return {
        title: `Add null guard for ${endpoint || 'failing request'}`,
        summary: 'The failure shape is consistent with dereferencing a nullable dependency, DTO field, or repository result.',
        patch: [
          `Target: ${primaryTarget}`,
          'Suggested patch:',
          '```diff',
          '@@',
          '- return downstreamValue.doSomething();',
          '+ if (downstreamValue == null) {',
          '+   throw new IllegalArgumentException("Required value is missing");',
          '+ }',
          '+ return downstreamValue.doSomething();',
          '```'
        ].join('\n')
      };
    case 'missing-repository-check':
      return {
        title: `Add repository existence check for ${endpoint || 'failing request'}`,
        summary: 'The failure suggests an Optional/get or lookup path that is not guarding the missing entity case before service logic continues.',
        patch: [
          `Target: ${primaryTarget}`,
          'Suggested patch:',
          '```diff',
          '@@',
          '- Entity entity = repository.findById(id).get();',
          '+ Entity entity = repository.findById(id)',
          '+   .orElseThrow(() -> new ResourceNotFoundException("Entity not found: " + id));',
          '```'
        ].join('\n')
      };
    case 'incorrect-enum-mapping':
      return {
        title: `Normalize enum parsing for ${endpoint || 'failing request'}`,
        summary: 'The failure indicates input-to-enum conversion is too permissive or throws an unhandled IllegalArgumentException.',
        patch: [
          `Target: ${primaryTarget}`,
          'Suggested patch:',
          '```diff',
          '@@',
          '- Status status = Status.valueOf(request.getStatus());',
          '+ Status status = Arrays.stream(Status.values())',
          '+   .filter(value -> value.name().equalsIgnoreCase(request.getStatus()))',
          '+   .findFirst()',
          '+   .orElseThrow(() -> new IllegalArgumentException("Unsupported status: " + request.getStatus()));',
          '```'
        ].join('\n')
      };
    case 'validation-guard':
      return {
        title: `Tighten request validation for ${endpoint || 'failing request'}`,
        summary: 'The failing path should reject invalid input at the controller boundary instead of letting inconsistent state propagate deeper into the service layer.',
        patch: [
          `Target: ${primaryTarget}`,
          'Suggested patch:',
          '```diff',
          '@@',
          '- public ResponseEntity<?> handle(Request request) {',
          '+ public ResponseEntity<?> handle(@Valid Request request) {',
          '@@',
          '+ if (request.getRequiredField() == null) {',
          '+   throw new IllegalArgumentException("requiredField must be provided");',
          '+ }',
          '```'
        ].join('\n')
      };
    default:
      return {
        title: `Add defensive error handling for ${endpoint || 'failing request'}`,
        summary: 'A backend guard should convert this path from an internal error into a controlled 4xx/5xx domain response with a clear cause.',
        patch: [
          `Target: ${primaryTarget}`,
          'Suggested patch:',
          '```diff',
          '@@',
          '+ try {',
          '+   // existing service logic',
          '+ } catch (IllegalArgumentException ex) {',
          '+   throw ex;',
          '+ } catch (Exception ex) {',
          '+   throw new ServiceUnavailableException("Unexpected backend failure", ex);',
          '+ }',
          '```'
        ].join('\n')
      };
  }
}

function buildPullRequestProposal(stage, endpoint, proposal) {
  return {
    title: `[QA] Investigate ${stage} failure${endpoint ? ` on ${endpoint}` : ''}`,
    body: [
      '## Root Cause Analysis',
      proposal.summary,
      '',
      '## Proposed Patch',
      proposal.patch,
      '',
      '## Policy',
      'This proposal was generated automatically. Backend source code was not modified by the QA platform.'
    ].join('\n')
  };
}

function tryCreateDraftPr(bodyFilePath, title, workspacePath) {
  if (process.env.QA_AGENT_OPEN_PR !== 'true') {
    return { attempted: false, created: false, reason: 'QA_AGENT_OPEN_PR not enabled' };
  }

  const statusResult = spawnSync('gh', ['auth', 'status'], { cwd: workspacePath, encoding: 'utf8' });
  if ((statusResult.status ?? 1) !== 0) {
    return { attempted: true, created: false, reason: 'GitHub CLI auth unavailable' };
  }

  const prResult = spawnSync('gh', ['pr', 'create', '--draft', '--title', title, '--body-file', bodyFilePath], {
    cwd: workspacePath,
    encoding: 'utf8'
  });

  if ((prResult.status ?? 1) !== 0) {
    return {
      attempted: true,
      created: false,
      reason: prResult.stderr || prResult.stdout || 'Failed to create draft PR'
    };
  }

  return {
    attempted: true,
    created: true,
    reason: prResult.stdout.trim()
  };
}

async function main() {
  await ensureSelfHealFiles();
  const options = parseArgs(process.argv.slice(2));
  const logFile = options.logFile || path.join(options.workspacePath, '.self-heal', 'reports', 'latest-failure.log');
  const logContent = await fs.readFile(logFile, 'utf8');
  const javaFiles = await collectFiles(options.workspacePath);
  const endpoint = detectEndpoint(logContent);
  const failureKind = detectFailureKind(logContent);
  const stackFrames = extractStackFrames(logContent);
  const stackCandidates = findCandidatesByStack(javaFiles, options.workspacePath, stackFrames);
  const hintCandidates = findCandidatesByHint(javaFiles, options.workspacePath, endpoint);
  const candidateFiles = Array.from(new Set([...stackCandidates, ...hintCandidates]));
  const proposal = proposalForKind(failureKind, endpoint, candidateFiles);
  const pullRequestProposal = buildPullRequestProposal(options.stage, endpoint, proposal);

  const report = {
    generatedAt: new Date().toISOString(),
    stage: options.stage,
    endpoint,
    failureKind,
    logFile: logFile.replace(/\\/g, '/'),
    stackFrames,
    candidateFiles,
    proposal,
    pullRequestProposal,
    modifiedBackendCode: false
  };

  await writeReport('qa-agent-analysis.json', `${JSON.stringify(report, null, 2)}\n`);

  const markdown = [
    '# Autonomous QA Failure Analysis',
    '',
    `- Stage: ${report.stage}`,
    `- Endpoint: ${report.endpoint || 'unknown'}`,
    `- Failure kind: ${report.failureKind}`,
    `- Backend code modified: no`,
    '',
    '## Candidate Files',
    '',
    ...(candidateFiles.length > 0 ? candidateFiles.map((filePath) => `- ${filePath}`) : ['- No direct code candidate identified from the current log.']),
    '',
    '## Root Cause',
    '',
    proposal.summary,
    '',
    '## Suggested Patch',
    '',
    proposal.patch,
    '',
    '## Pull Request Proposal',
    '',
    `Title: ${pullRequestProposal.title}`,
    '',
    pullRequestProposal.body,
    ''
  ].join('\n');

  await writeReport('qa-agent-analysis.md', `${markdown}\n`);

  const prResult = tryCreateDraftPr(
    path.join(options.workspacePath, '.self-heal', 'reports', 'qa-agent-analysis.md'),
    pullRequestProposal.title,
    options.workspacePath
  );

  if (prResult.attempted) {
    const enrichedReport = { ...report, draftPr: prResult };
    await writeReport('qa-agent-analysis.json', `${JSON.stringify(enrichedReport, null, 2)}\n`);
  }
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message || String(error)}\n`);
  process.exit(1);
});
