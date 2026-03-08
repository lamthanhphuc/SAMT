import fs from 'node:fs/promises';
import path from 'node:path';

const selfHealDir = path.resolve('.self-heal');
const generatorOverridesPath = path.join(selfHealDir, 'generator-overrides.json');
const openapiOverridesPath = path.join(selfHealDir, 'openapi-overrides.json');
const pipelineConfigPath = path.join(selfHealDir, 'pipeline-config.json');
const reportsDir = path.join(selfHealDir, 'reports');

const defaultGeneratorOverrides = {
  globals: {
    authCooldownMs: 15000,
    fixtureAliases: {}
  },
  operations: {}
};

const defaultOpenapiOverrides = {
  schemas: {},
  operations: {}
};

const defaultPipelineConfig = {
  maxIterations: 3,
  stages: [
    { id: 'openapi', command: 'npm run openapi:generate' },
    { id: 'generate', command: 'npm run tests:generate' },
    { id: 'smoke', command: 'npm run test:smoke' },
    { id: 'regression', command: 'npm run tests:regression' },
    { id: 'contract', command: 'npm run tests:contract' }
  ]
};

function deepMerge(target, source) {
  if (!source || typeof source !== 'object' || Array.isArray(source)) {
    return source;
  }

  const out = { ...(target || {}) };
  for (const [key, value] of Object.entries(source)) {
    if (Array.isArray(value)) {
      out[key] = [...value];
      continue;
    }

    if (value && typeof value === 'object') {
      out[key] = deepMerge(out[key], value);
      continue;
    }

    out[key] = value;
  }

  return out;
}

async function readJson(filePath, fallback) {
  try {
    const raw = await fs.readFile(filePath, 'utf8');
    return deepMerge(fallback, JSON.parse(raw));
  } catch (error) {
    if (error.code === 'ENOENT') {
      return structuredClone(fallback);
    }
    throw error;
  }
}

export async function ensureSelfHealFiles() {
  await fs.mkdir(selfHealDir, { recursive: true });
  await fs.mkdir(reportsDir, { recursive: true });

  const seeds = [
    [generatorOverridesPath, defaultGeneratorOverrides],
    [openapiOverridesPath, defaultOpenapiOverrides],
    [pipelineConfigPath, defaultPipelineConfig]
  ];

  for (const [filePath, fallback] of seeds) {
    try {
      await fs.access(filePath);
    } catch (error) {
      if (error.code !== 'ENOENT') {
        throw error;
      }
      await fs.writeFile(filePath, `${JSON.stringify(fallback, null, 2)}\n`, 'utf8');
    }
  }
}

export async function loadGeneratorOverrides() {
  await ensureSelfHealFiles();
  return readJson(generatorOverridesPath, defaultGeneratorOverrides);
}

export async function loadOpenapiOverrides() {
  await ensureSelfHealFiles();
  return readJson(openapiOverridesPath, defaultOpenapiOverrides);
}

export async function loadPipelineConfig() {
  await ensureSelfHealFiles();
  return readJson(pipelineConfigPath, defaultPipelineConfig);
}

export async function writeGeneratorOverrides(data) {
  await ensureSelfHealFiles();
  await fs.writeFile(generatorOverridesPath, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}

export async function writeOpenapiOverrides(data) {
  await ensureSelfHealFiles();
  await fs.writeFile(openapiOverridesPath, `${JSON.stringify(data, null, 2)}\n`, 'utf8');
}

export async function writeReport(reportName, content) {
  await ensureSelfHealFiles();
  const filePath = path.join(reportsDir, reportName);
  await fs.writeFile(filePath, content, 'utf8');
  return filePath;
}

export function operationKey(method, pathKey) {
  return `${String(method).toLowerCase()} ${pathKey}`;
}

function applySchemaPatch(target, patch) {
  if (!patch || typeof patch !== 'object') {
    return;
  }

  for (const [key, value] of Object.entries(patch)) {
    if (value === null) {
      delete target[key];
      continue;
    }

    if (key === 'properties' && value && typeof value === 'object') {
      target.properties = target.properties || {};
      for (const [propertyName, propertyPatch] of Object.entries(value)) {
        target.properties[propertyName] = target.properties[propertyName] || {};
        applySchemaPatch(target.properties[propertyName], propertyPatch);
      }
      continue;
    }

    if (Array.isArray(value)) {
      target[key] = [...value];
      continue;
    }

    if (value && typeof value === 'object' && !('$ref' in value)) {
      target[key] = target[key] || {};
      applySchemaPatch(target[key], value);
      continue;
    }

    target[key] = value;
  }
}

function ensureResponseContent(response, contentType, responsePatch) {
  if (responsePatch.noContent) {
    delete response.content;
    return;
  }

  if (!responsePatch.contentSchemaRef && !responsePatch.contentSchema) {
    return;
  }

  const mediaType = contentType || responsePatch.contentType || '*/*';
  response.content = response.content || {};
  response.content[mediaType] = response.content[mediaType] || {};

  if (responsePatch.contentSchemaRef) {
    response.content[mediaType].schema = { $ref: responsePatch.contentSchemaRef };
  } else if (responsePatch.contentSchema) {
    response.content[mediaType].schema = structuredClone(responsePatch.contentSchema);
  }
}

function applyOperationPatch(operation, patch) {
  if (!patch || typeof patch !== 'object') {
    return;
  }

  if (Array.isArray(patch.removeResponses)) {
    for (const statusCode of patch.removeResponses) {
      delete operation.responses?.[statusCode];
    }
  }

  if (patch.responses && typeof patch.responses === 'object') {
    operation.responses = operation.responses || {};

    for (const [statusCode, responsePatch] of Object.entries(patch.responses)) {
      if (responsePatch?.copyFrom) {
        operation.responses[statusCode] = structuredClone(operation.responses[responsePatch.copyFrom] || {});
      }

      if (
        responsePatch?.noContent ||
        responsePatch?.contentSchemaRef ||
        responsePatch?.contentSchema ||
        responsePatch?.contentType ||
        responsePatch?.description
      ) {
        const existingResponse = operation.responses[statusCode];
        if (existingResponse?.$ref) {
          operation.responses[statusCode] = {};
        }
      }

      operation.responses[statusCode] = operation.responses[statusCode] || {};
      const response = operation.responses[statusCode];

      if (responsePatch.description) {
        response.description = responsePatch.description;
      }

      ensureResponseContent(response, responsePatch.contentType, responsePatch);
    }
  }
}

export function applyOpenapiOverrides(doc, overrides) {
  const schemaOverrides = overrides?.schemas || {};
  const operationOverrides = overrides?.operations || {};

  doc.components = doc.components || {};
  doc.components.schemas = doc.components.schemas || {};

  for (const [schemaName, schemaPatch] of Object.entries(schemaOverrides)) {
    doc.components.schemas[schemaName] = doc.components.schemas[schemaName] || {};
    applySchemaPatch(doc.components.schemas[schemaName], schemaPatch);
  }

  for (const [key, patch] of Object.entries(operationOverrides)) {
    const separatorIndex = key.indexOf(' ');
    if (separatorIndex === -1) {
      continue;
    }

    const method = key.slice(0, separatorIndex).toLowerCase();
    const pathKey = key.slice(separatorIndex + 1);
    const operation = doc.paths?.[pathKey]?.[method];
    if (!operation) {
      continue;
    }

    applyOperationPatch(operation, patch);
  }

  return doc;
}

export function getReportsDir() {
  return reportsDir;
}