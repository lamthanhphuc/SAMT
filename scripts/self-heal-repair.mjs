import {
  loadGeneratorOverrides,
  loadOpenapiOverrides,
  writeGeneratorOverrides,
  writeOpenapiOverrides
} from './self-heal-overrides.mjs';

function ensureOperationOverride(container, operationKey) {
  container.operations = container.operations || {};
  container.operations[operationKey] = container.operations[operationKey] || {};
  return container.operations[operationKey];
}

function addUniqueStatus(list, status) {
  const out = new Set(Array.isArray(list) ? list : []);
  out.add(Number(status));
  return Array.from(out).sort((left, right) => left - right);
}

function schemaAndPropertyFromEvidence(evidence) {
  const schemaMatch = evidence.match(/Schema at \/components\/schemas\/([^/]+)\/properties\/([^:\s]+)/);
  if (!schemaMatch) {
    return null;
  }
  return {
    schemaName: schemaMatch[1],
    propertyName: schemaMatch[2]
  };
}

function applyHttpyacRepair(generatorOverrides, diagnostic, repairs) {
  if (!diagnostic.operationKey) {
    return false;
  }

  const operationOverride = ensureOperationOverride(generatorOverrides, diagnostic.operationKey);
  let changed = false;

  if (diagnostic.category === 'schema_mismatch' && Number.isFinite(diagnostic.actualStatus)) {
    operationOverride.expectedStatuses = operationOverride.expectedStatuses || {};
    operationOverride.expectedStatuses.happy = addUniqueStatus(operationOverride.expectedStatuses.happy, diagnostic.actualStatus);
    repairs.push(`Accepted learned happy-path status ${diagnostic.actualStatus} for ${diagnostic.operationKey}`);
    changed = true;
  }

  if (diagnostic.category === 'invalid_test_sequencing') {
    operationOverride.requiresIsolatedFixture = true;
    repairs.push(`Marked ${diagnostic.operationKey} as requiring isolated fixture lifecycle`);
    changed = true;
  }

  if (diagnostic.category === 'authorization_failure' && diagnostic.actualStatus === 429) {
    generatorOverrides.globals = generatorOverrides.globals || {};
    generatorOverrides.globals.authCooldownMs = Math.max(Number(generatorOverrides.globals.authCooldownMs || 15000), 15000);
    repairs.push('Enforced auth cooldown window for repeated login traffic');
    changed = true;
  }

  return changed;
}

function applySchemathesisRepair(openapiOverrides, diagnostic, repairs) {
  if (!diagnostic.operationKey) {
    return false;
  }

  let changed = false;

  if (diagnostic.evidence.includes('Undocumented HTTP status code')) {
    const receivedMatch = diagnostic.evidence.match(/Received:\s+(\d+)/);
    if (receivedMatch) {
      const operationOverride = ensureOperationOverride(openapiOverrides, diagnostic.operationKey);
      operationOverride.responses = operationOverride.responses || {};
      const statusCode = receivedMatch[1];
      operationOverride.responses[statusCode] = operationOverride.responses[statusCode] || {
        description: statusCode === '204' ? 'No Content' : `Auto-learned ${statusCode}`
      };

      if (statusCode === '204') {
        operationOverride.responses[statusCode].noContent = true;
      }

      if (statusCode === '503') {
        operationOverride.responses[statusCode].contentType = 'application/json';
        operationOverride.responses[statusCode].contentSchemaRef = '#/components/schemas/StandardError';
      }

      repairs.push(`Documented learned status ${statusCode} for ${diagnostic.operationKey}`);
      changed = true;
    }
  }

  if (diagnostic.evidence.includes('Response violates schema')) {
    const schemaProperty = schemaAndPropertyFromEvidence(diagnostic.evidence);
    if (schemaProperty && /null is not of type "string"/.test(diagnostic.evidence)) {
      openapiOverrides.schemas = openapiOverrides.schemas || {};
      openapiOverrides.schemas[schemaProperty.schemaName] = openapiOverrides.schemas[schemaProperty.schemaName] || { properties: {} };
      openapiOverrides.schemas[schemaProperty.schemaName].properties = openapiOverrides.schemas[schemaProperty.schemaName].properties || {};
      openapiOverrides.schemas[schemaProperty.schemaName].properties[schemaProperty.propertyName] = {
        ...(openapiOverrides.schemas[schemaProperty.schemaName].properties[schemaProperty.propertyName] || {}),
        nullable: true
      };
      repairs.push(`Marked ${schemaProperty.schemaName}.${schemaProperty.propertyName} nullable from observed response`);
      changed = true;
    }

    if (schemaProperty && /is not a "date-time"/.test(diagnostic.evidence)) {
      openapiOverrides.schemas = openapiOverrides.schemas || {};
      openapiOverrides.schemas[schemaProperty.schemaName] = openapiOverrides.schemas[schemaProperty.schemaName] || { properties: {} };
      openapiOverrides.schemas[schemaProperty.schemaName].properties = openapiOverrides.schemas[schemaProperty.schemaName].properties || {};
      openapiOverrides.schemas[schemaProperty.schemaName].properties[schemaProperty.propertyName] = {
        ...(openapiOverrides.schemas[schemaProperty.schemaName].properties[schemaProperty.propertyName] || {}),
        type: 'string',
        format: null
      };
      repairs.push(`Relaxed ${schemaProperty.schemaName}.${schemaProperty.propertyName} format from strict date-time`);
      changed = true;
    }
  }

  if (diagnostic.evidence.includes('JSON deserialization error') || diagnostic.evidence.includes('Missing Content-Type header')) {
    const operationOverride = ensureOperationOverride(openapiOverrides, diagnostic.operationKey);
    operationOverride.responses = operationOverride.responses || {};
    operationOverride.responses['400'] = {
      description: 'Bad Request',
      contentType: 'application/problem+json',
      contentSchemaRef: '#/components/schemas/StandardError'
    };
    repairs.push(`Documented problem-detail 400 response for malformed path input on ${diagnostic.operationKey}`);
    changed = true;
  }

  return changed;
}

export async function applyRepairs(diagnostics) {
  const generatorOverrides = await loadGeneratorOverrides();
  const openapiOverrides = await loadOpenapiOverrides();
  const repairs = [];
  let generatorChanged = false;
  let openapiChanged = false;

  for (const diagnostic of diagnostics) {
    if (diagnostic.source === 'httpyac') {
      generatorChanged = applyHttpyacRepair(generatorOverrides, diagnostic, repairs) || generatorChanged;
      continue;
    }

    if (diagnostic.source === 'schemathesis') {
      openapiChanged = applySchemathesisRepair(openapiOverrides, diagnostic, repairs) || openapiChanged;
    }
  }

  if (generatorChanged) {
    await writeGeneratorOverrides(generatorOverrides);
  }

  if (openapiChanged) {
    await writeOpenapiOverrides(openapiOverrides);
  }

  return {
    changed: generatorChanged || openapiChanged,
    generatorChanged,
    openapiChanged,
    repairs
  };
}