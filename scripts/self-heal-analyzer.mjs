function extractOperationFromHttpyacName(name) {
  const match = name.match(/^([A-Z]+)_(\/[^_]+?)(?:_(happy|unauthorized|forbidden_role|invalid_payload|missing_required|invalid_uuid|not_found))?$/);
  if (!match) {
    return null;
  }

  return {
    method: match[1].toLowerCase(),
    pathKey: match[2],
    caseKind: match[3] || 'unknown'
  };
}

function classifyHttpyacFailure({ actualStatus, body, caseKind }) {
  const bodyText = String(body || '').toLowerCase();

  if (bodyText.includes('is not defined')) {
    return 'missing_fixture_data';
  }

  if (actualStatus === 429 || actualStatus === 401 || actualStatus === 403) {
    return 'authorization_failure';
  }

  if (actualStatus === 503 || bodyText.includes('circuit breaker open')) {
    return 'dependency_failure';
  }

  if (actualStatus >= 500) {
    return 'backend_defect';
  }

  if (caseKind === 'happy' && actualStatus === 404) {
    return 'missing_fixture_data';
  }

  if (caseKind === 'happy' && actualStatus === 409) {
    return 'invalid_test_sequencing';
  }

  if (bodyText.includes('validation failed') || bodyText.includes('must ') || bodyText.includes('invalid')) {
    return 'invalid_payload_generated';
  }

  return 'schema_mismatch';
}

export function analyzeHttpyacLog(rawLog) {
  const diagnostics = [];
  const failureRegex = /^\[-\]\s+(?<name>.+?)\s+->\s+\[(?<expected>[^\]]+)\]\s+\(AssertionError \[ERR_ASSERTION\]: Unexpected status (?<status>\d+); expected \[(?<repeatedExpected>[^\]]+)\]; body=(?<body>.*)$/gm;

  for (const match of rawLog.matchAll(failureRegex)) {
    const name = match.groups.name;
    const operation = extractOperationFromHttpyacName(name);
    const actualStatus = Number(match.groups.status);
    const body = match.groups.body;

    diagnostics.push({
      source: 'httpyac',
      operationKey: operation ? `${operation.method} ${operation.pathKey}` : null,
      method: operation?.method || null,
      pathKey: operation?.pathKey || null,
      caseKind: operation?.caseKind || 'unknown',
      category: classifyHttpyacFailure({ actualStatus, body, caseKind: operation?.caseKind }),
      expectedStatuses: match.groups.expected,
      actualStatus,
      evidence: body
    });
  }

  if (/\bis not defined\b/i.test(rawLog) && diagnostics.length === 0) {
    diagnostics.push({
      source: 'httpyac',
      operationKey: null,
      category: 'missing_fixture_data',
      evidence: 'Detected unresolved variable in httpyac output'
    });
  }

  return diagnostics;
}

function classifySchemathesisFailure(block) {
  const lower = block.toLowerCase();

  if (lower.includes('missing content-type header') || lower.includes('json deserialization error')) {
    return 'schema_mismatch';
  }

  if (lower.includes('response violates schema') || lower.includes('undocumented http status code')) {
    return 'schema_mismatch';
  }

  if (lower.includes('service unavailable') || lower.includes('[503]')) {
    return 'dependency_failure';
  }

  if (lower.includes('401') || lower.includes('403') || lower.includes('too many requests')) {
    return 'authorization_failure';
  }

  if (lower.includes('404 not found') || lower.includes('missing test data')) {
    return 'missing_fixture_data';
  }

  if (lower.includes('validation') || lower.includes('rejected generated data')) {
    return 'invalid_payload_generated';
  }

  if (lower.includes('server error')) {
    return 'backend_defect';
  }

  return 'schema_mismatch';
}

export function analyzeSchemathesisLog(rawLog) {
  const diagnostics = [];
  const operationRegex = /^_+\s+([A-Z]+)\s+(\/[^\s]+)\s+_+$/gm;
  const matches = [...rawLog.matchAll(operationRegex)];

  for (let index = 0; index < matches.length; index += 1) {
    const current = matches[index];
    const next = matches[index + 1];
    const start = current.index + current[0].length;
    const statefulIndex = rawLog.indexOf('________________________________ Stateful tests', start);
    const warningsIndex = rawLog.indexOf('=================================== WARNINGS ===================================', start);
    const summaryIndex = rawLog.indexOf('=================================== SUMMARY ====================================', start);
    const candidateEnds = [next?.index, statefulIndex, warningsIndex, summaryIndex, rawLog.length]
      .filter((value) => Number.isInteger(value) && value >= start);
    const end = Math.min(...candidateEnds);
    const block = rawLog.slice(start, end).trim();

    diagnostics.push({
      source: 'schemathesis',
      operationKey: `${current[1].toLowerCase()} ${current[2]}`,
      method: current[1].toLowerCase(),
      pathKey: current[2],
      category: classifySchemathesisFailure(block),
      evidence: block
    });
  }

  const missingDataMatch = rawLog.match(/Missing test data:[\s\S]*?(Schema validation mismatch:|=================================== SUMMARY)/);
  if (missingDataMatch) {
    diagnostics.push({
      source: 'schemathesis',
      operationKey: null,
      category: 'missing_fixture_data',
      evidence: missingDataMatch[0]
    });
  }

  const validationMismatchMatch = rawLog.match(/Schema validation mismatch:[\s\S]*?=================================== SUMMARY/);
  if (validationMismatchMatch) {
    diagnostics.push({
      source: 'schemathesis',
      operationKey: null,
      category: 'invalid_payload_generated',
      evidence: validationMismatchMatch[0]
    });
  }

  return diagnostics;
}

export function summarizeDiagnostics(diagnostics) {
  const summary = {
    total: diagnostics.length,
    byCategory: {}
  };

  for (const diagnostic of diagnostics) {
    summary.byCategory[diagnostic.category] = (summary.byCategory[diagnostic.category] || 0) + 1;
  }

  return summary;
}