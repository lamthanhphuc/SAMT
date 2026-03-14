import fs from 'node:fs/promises';
import path from 'node:path';
import dotenv from 'dotenv';
import yaml from 'js-yaml';
import { applyOpenapiOverrides, loadOpenapiOverrides } from './self-heal-overrides.mjs';

dotenv.config({ path: path.resolve('.env.test') });

const baseUrl = process.env.BASE_URL || 'http://localhost:9080';
const identityDocsUrl = process.env.IDENTITY_DOCS_URL || `${baseUrl}/identity/v3/api-docs`;
const userGroupDocsUrl = process.env.USER_GROUP_DOCS_URL || `${baseUrl}/user-group/v3/api-docs`;
const projectConfigDocsUrl = process.env.PROJECT_CONFIG_DOCS_URL || `${baseUrl}/project-config/v3/api-docs`;
const syncDocsUrl = process.env.SYNC_DOCS_URL || `${baseUrl}/sync/v3/api-docs`;
const reportDocsUrl = process.env.REPORT_DOCS_URL || `${baseUrl}/report/v3/api-docs`;

const sources = [
  { key: 'identity', url: identityDocsUrl },
  { key: 'userGroup', url: userGroupDocsUrl },
  { key: 'projectConfig', url: projectConfigDocsUrl },
  { key: 'sync', url: syncDocsUrl },
  { key: 'report', url: reportDocsUrl }
];

const componentBuckets = [
  'schemas',
  'responses',
  'parameters',
  'examples',
  'requestBodies',
  'headers',
  'securitySchemes',
  'links',
  'callbacks'
];

function rewriteRefs(obj, keyPrefix, componentRenameMap) {
  if (obj === null || obj === undefined) return obj;
  if (Array.isArray(obj)) return obj.map((it) => rewriteRefs(it, keyPrefix, componentRenameMap));
  if (typeof obj !== 'object') return obj;

  const out = {};
  for (const [k, v] of Object.entries(obj)) {
    if (k === '$ref' && typeof v === 'string' && v.startsWith('#/components/')) {
      const parts = v.split('/');
      const bucket = parts[2];
      const oldName = parts[3];
      const mapped = componentRenameMap[bucket]?.[oldName] || `${keyPrefix}_${oldName}`;
      out[k] = `#/components/${bucket}/${mapped}`;
    } else {
      out[k] = rewriteRefs(v, keyPrefix, componentRenameMap);
    }
  }
  return out;
}

function ensureErrorContract(doc) {
  doc.components = doc.components || {};
  doc.components.schemas = doc.components.schemas || {};
  doc.components.responses = doc.components.responses || {};
  doc.components.schemas.StandardError = {
    type: 'object',
    properties: {
      type: { type: 'string', example: 'https://api.example.com/errors/validation-error' },
      title: { type: 'string', example: 'Validation failed' },
      status: { type: 'integer', format: 'int32', example: 400 },
      detail: { type: 'string', example: 'Invalid UUID' },
      instance: { type: 'string', example: '/api/users/not-a-number' },
      timestamp: { type: 'string', format: 'date-time' }
    }
  };

  const statuses = [400, 401, 403, 404, 405, 409, 415, 500];
  for (const status of statuses) {
    const name = `Error${status}`;
    doc.components.responses[name] = {
      description: `${status} error`,
      content: {
        'application/problem+json': {
          schema: { $ref: '#/components/schemas/StandardError' }
        }
      }
    };
  }
}

function ensureEnvelopeSchema(doc, schemaName, dataSchema) {
  doc.components = doc.components || {};
  doc.components.schemas = doc.components.schemas || {};

  doc.components.schemas[schemaName] = {
    type: 'object',
    properties: {
      timestamp: { type: 'string', format: 'date-time' },
      status: { type: 'integer', format: 'int32' },
      success: { type: 'boolean' },
      data: typeof dataSchema === 'string' ? { $ref: dataSchema } : structuredClone(dataSchema),
      error: { type: 'string' },
      message: { type: 'string' },
      path: { type: 'string' },
      correlationId: { type: 'string' },
      degraded: { type: 'boolean' }
    }
  };
}

function extractComponentSchemaName(ref) {
  if (typeof ref !== 'string') {
    return null;
  }

  const match = ref.match(/^#\/components\/schemas\/(.+)$/);
  return match ? match[1] : null;
}

function resolveWrappedDataSchema(doc, schema, wrapperPrefix) {
  if (!schema?.$ref) {
    return schema;
  }

  const schemaName = extractComponentSchemaName(schema.$ref);
  if (!schemaName?.startsWith(wrapperPrefix)) {
    return schema.$ref;
  }

  const dataSchema = doc.components?.schemas?.[schemaName]?.properties?.data;
  if (!dataSchema || dataSchema.$ref === `#/components/schemas/${schemaName}`) {
    return schema.$ref;
  }

  return structuredClone(dataSchema);
}

function allowEmptyStringForIntegerQueryParameter(schema, fallback) {
  const numericSchema = {
    ...(schema || {}),
    ...fallback,
    type: 'integer'
  };

  delete numericSchema.nullable;

  return {
    anyOf: [
      numericSchema,
      {
        type: 'string',
        enum: ['']
      }
    ],
    default: numericSchema.default
  };
}

function wrapSuccessResponses(doc, pathPattern, wrapperPrefix, operationNamePrefix, shouldSkip) {
  for (const [pathKey, pathItem] of Object.entries(doc.paths || {})) {
    if (!pathPattern.test(pathKey)) {
      continue;
    }

    for (const [method, operation] of Object.entries(pathItem || {})) {
      if (!operation || typeof operation !== 'object') {
        continue;
      }

      for (const statusCode of ['200', '201']) {
        const response = operation.responses?.[statusCode];
        const mediaType = response?.content?.['application/json'] || response?.content?.['*/*'];
        const schema = mediaType?.schema;

        if (!schema || shouldSkip?.(pathKey, method, operation, response)) {
          continue;
        }

        if (schema.$ref && (schema.$ref.includes('_ApiResponse') || schema.$ref.includes(wrapperPrefix))) {
          continue;
        }

        const wrapperName = `${operationNamePrefix}_${String(operation.operationId || `${method}_${pathKey}`).replace(/[^a-zA-Z0-9]+/g, '_')}`;
        ensureEnvelopeSchema(doc, wrapperName, resolveWrappedDataSchema(doc, schema, wrapperPrefix));

        operation.responses[statusCode] = {
          description: response.description || 'OK',
          content: {
            'application/json': {
              schema: { $ref: `#/components/schemas/${wrapperName}` }
            }
          }
        };
      }
    }
  }
}

function wrapUserGroupSuccessResponses(doc) {
  wrapSuccessResponses(doc, /^\/api\/(groups|users|semesters)(\/|$)/, 'userGroup_ApiEnvelope_', 'userGroup_ApiEnvelope');
}

function wrapReportSuccessResponses(doc) {
  wrapSuccessResponses(
    doc,
    /^\/api\/reports(\/|$)/,
    'report_ApiEnvelope_',
    'report_ApiEnvelope',
    (pathKey, method, operation, response) => pathKey.endsWith('/download') || !response?.content?.['application/json']
  );
}

function ensurePathParameters(doc) {
  for (const [pathKey, pathItem] of Object.entries(doc.paths || {})) {
    const matches = [...pathKey.matchAll(/\{([^}]+)\}/g)].map((m) => m[1]);
    if (matches.length === 0) continue;

    for (const [method, operation] of Object.entries(pathItem || {})) {
      if (!operation || typeof operation !== 'object') continue;
      if (!['get', 'post', 'put', 'patch', 'delete', 'options', 'head'].includes(method)) continue;

      operation.parameters = Array.isArray(operation.parameters) ? operation.parameters : [];
      for (const paramName of matches) {
        const exists = operation.parameters.some((p) => p && p.in === 'path' && p.name === paramName);
        if (!exists) {
          operation.parameters.push({
            name: paramName,
            in: 'path',
            required: true,
            schema: { type: 'string' }
          });
        }
      }
    }
  }
}

function patchSchema(schema, patch) {
  if (!schema) return;
  Object.assign(schema, patch);
}

function patchProperty(schema, propertyName, patch) {
  if (!schema?.properties?.[propertyName]) return;
  Object.assign(schema.properties[propertyName], patch);
}

function applySchemaOverrides(doc) {
  const schemas = doc.components?.schemas || {};

  const register = schemas.identity_RegisterRequest;
  if (register) {
    register.required = ['email', 'password', 'confirmPassword', 'fullName', 'role'];
    patchProperty(register, 'email', { format: 'email', maxLength: 255 });
    patchProperty(register, 'password', {
      minLength: 8,
      maxLength: 128,
      pattern: '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,128}$'
    });
    patchProperty(register, 'confirmPassword', { minLength: 1 });
    patchProperty(register, 'fullName', {
      minLength: 2,
      maxLength: 100,
      pattern: '^[\\p{L}]+(?:[ -][\\p{L}]+)*$'
    });
    patchProperty(register, 'role', { type: 'string', enum: ['STUDENT'] });
  }

  const adminCreate = schemas.identity_AdminCreateUserRequest;
  if (adminCreate) {
    adminCreate.required = ['email', 'password', 'fullName', 'role'];
    patchProperty(adminCreate, 'email', { type: 'string', format: 'email', maxLength: 255 });
    patchProperty(adminCreate, 'password', {
      minLength: 8,
      maxLength: 128,
      pattern: '^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,128}$'
    });
    patchProperty(adminCreate, 'fullName', {
      minLength: 2,
      maxLength: 100,
      pattern: '^[\\p{L}]+(?:[ -][\\p{L}]+)*$'
    });
    patchProperty(adminCreate, 'role', { type: 'string', enum: ['STUDENT', 'LECTURER', 'ADMIN'] });
  }

  const externalAccounts = schemas.identity_ExternalAccountsRequest;
  if (externalAccounts) {
    patchProperty(externalAccounts, 'jiraAccountId', { nullable: true, maxLength: 30 });
    patchProperty(externalAccounts, 'githubUsername', { nullable: true, maxLength: 39 });
  }

  const logout = schemas.identity_LogoutRequest;
  if (logout) {
    logout.required = ['refreshToken'];
    patchProperty(logout, 'refreshToken', { type: 'string', minLength: 1 });
  }

  const createGroup = schemas.userGroup_CreateGroupRequest;
  if (createGroup) {
    createGroup.required = ['groupName', 'semesterId', 'lecturerId'];
    patchProperty(createGroup, 'groupName', {
      minLength: 3,
      maxLength: 50,
      pattern: '^[A-Z]{2,4}[0-9]{2,4}-G[0-9]+$'
    });
    patchProperty(createGroup, 'semesterId', { type: 'integer', format: 'int64', minimum: 1 });
    patchProperty(createGroup, 'lecturerId', { type: 'integer', format: 'int64', minimum: 1 });
  }

  const createSemester = schemas.userGroup_CreateSemesterRequest;
  if (createSemester) {
    createSemester.required = ['semesterCode', 'semesterName', 'startDate', 'endDate'];
    patchProperty(createSemester, 'semesterCode', { minLength: 1 });
    patchProperty(createSemester, 'semesterName', { minLength: 1 });
    patchProperty(createSemester, 'startDate', { type: 'string', format: 'date' });
    patchProperty(createSemester, 'endDate', { type: 'string', format: 'date' });
    delete createSemester.properties?.semesterCode?.pattern;
    delete createSemester.properties?.semesterName?.pattern;
  }

  const createConfig = schemas.projectConfig_CreateConfigRequest;
  if (createConfig) {
    createConfig.required = ['groupId', 'jiraHostUrl', 'jiraEmail', 'jiraApiToken', 'githubRepoUrl', 'githubToken'];
    patchProperty(createConfig, 'groupId', { type: 'integer', format: 'int64', minimum: 1 });
    patchProperty(createConfig, 'jiraHostUrl', {
      maxLength: 255,
      pattern: 'https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com)(/.*)?'
    });
    patchProperty(createConfig, 'jiraEmail', {
      type: 'string',
      format: 'email',
      maxLength: 255
    });
    patchProperty(createConfig, 'jiraApiToken', {
      minLength: 100,
      maxLength: 500,
      pattern: '^ATATT[A-Za-z0-9+/=_-]{95,495}$'
    });
    patchProperty(createConfig, 'githubRepoUrl', {
      maxLength: 512,
      pattern: 'https://github\\.com/[\\w-]+/[\\w-]+'
    });
    patchProperty(createConfig, 'githubToken', {
      minLength: 40,
      maxLength: 255,
      pattern: '^(ghp_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]+)$'
    });
  }

  const updateConfig = schemas.projectConfig_UpdateConfigRequest;
  if (updateConfig) {
    patchProperty(updateConfig, 'jiraHostUrl', {
      maxLength: 255,
      pattern: 'https?://[a-zA-Z0-9.-]+\\.(atlassian\\.net|jira\\.com)(/.*)?'
    });
    patchProperty(updateConfig, 'jiraEmail', {
      type: 'string',
      format: 'email',
      maxLength: 255
    });
    patchProperty(updateConfig, 'jiraApiToken', {
      minLength: 100,
      maxLength: 500,
      pattern: '^ATATT[A-Za-z0-9+/=_-]{95,495}$'
    });
    patchProperty(updateConfig, 'githubRepoUrl', {
      maxLength: 512,
      pattern: 'https://github\\.com/[\\w-]+/[\\w-]+'
    });
    patchProperty(updateConfig, 'githubToken', {
      minLength: 40,
      maxLength: 255,
      pattern: '^(ghp_[A-Za-z0-9]{36,}|github_pat_[A-Za-z0-9_]+)$'
    });
  }

  const updateGroup = schemas.userGroup_UpdateGroupRequest;
  if (updateGroup) {
    updateGroup.required = ['groupName', 'lecturerId'];
    patchProperty(updateGroup, 'lecturerId', { type: 'integer', format: 'int64', minimum: 1 });
  }

  const updateUser = schemas.userGroup_UpdateUserRequest;
  if (updateUser) {
    updateUser.required = ['fullName'];
    patchProperty(updateUser, 'fullName', {
      minLength: 2,
      maxLength: 100,
      pattern: '^[a-zA-ZĂ€-á»¹]+(?: [a-zA-ZĂ€-á»¹]+)*$'
    });
  }

  const updateLecturer = schemas.userGroup_UpdateLecturerRequest;
  if (updateLecturer) {
    patchProperty(updateLecturer, 'lecturerId', { type: 'integer', format: 'int64', minimum: 1 });
  }

  const addMember = schemas.userGroup_AddMemberRequest;
  if (addMember) {
    patchProperty(addMember, 'userId', { type: 'integer', format: 'int64', minimum: 1 });
  }
}

function applyOperationOverrides(doc) {
  const register = doc.paths?.['/api/auth/register']?.post;
  if (register?.responses?.['200']) {
    register.responses['201'] = register.responses['200'];
    delete register.responses['200'];
  }

  const logout = doc.paths?.['/api/auth/logout']?.post;
  if (logout) {
    logout.security = [{ bearerAuth: [] }];
    if (logout.responses?.['204'] && !logout.responses?.['200']) {
      logout.responses['200'] = logout.responses['204'];
      delete logout.responses['204'];
    } else if (!logout.responses?.['200']) {
      logout.responses = logout.responses || {};
      logout.responses['200'] = { description: 'OK' };
    }
  }

  const createConfig = doc.paths?.['/api/project-configs']?.post;
  if (createConfig?.responses?.['200']) {
    createConfig.responses['201'] = createConfig.responses['200'];
    delete createConfig.responses['200'];
  }

  const configById = doc.paths?.['/api/project-configs/{id}'];
  if (configById) {
    for (const method of ['get', 'put', 'delete']) {
      const operation = configById[method];
      if (!operation?.parameters) continue;
      for (const parameter of operation.parameters) {
        if (parameter.in === 'path' && parameter.name === 'id') {
          parameter.schema = { type: 'string', format: 'uuid' };
        }
      }
    }
  }

  const activeSemester = doc.paths?.['/api/semesters/active'];
  if (activeSemester?.put) {
    delete activeSemester.put;
  }

  for (const [pathKey, pathItem] of Object.entries(doc.paths || {})) {
    for (const [method, operation] of Object.entries(pathItem || {})) {
      if (!operation?.parameters) continue;
      for (const parameter of operation.parameters) {
        if (parameter.in !== 'path') continue;
        if (parameter.name === 'id' && pathKey.includes('/project-configs/')) {
          parameter.schema = { type: 'string', format: 'uuid' };
        }
        if (parameter.name === 'reportId' && pathKey.includes('/api/reports/')) {
          parameter.schema = { type: 'string', format: 'uuid' };
        }
        if (parameter.name === 'syncJobId' && pathKey.includes('/api/sync/jobs/')) {
          parameter.schema = { type: 'integer', format: 'int64', minimum: 1 };
        }
        if (['groupId', 'semesterId', 'userId'].includes(parameter.name)) {
          parameter.schema = { type: 'integer', format: 'int64', minimum: 1 };
        }
        if ((parameter.name === 'page' || parameter.name === 'size') && parameter.schema) {
          parameter.schema.type = 'integer';
        }
      }

      for (const parameter of operation.parameters) {
        if (parameter.in === 'query' && parameter.name === 'page') {
          parameter.allowEmptyValue = true;
          parameter.schema = allowEmptyStringForIntegerQueryParameter(parameter.schema, {
            minimum: 0,
            default: 0
          });
        }
        if (parameter.in === 'query' && parameter.name === 'size') {
          parameter.allowEmptyValue = true;
          parameter.schema = allowEmptyStringForIntegerQueryParameter(parameter.schema, {
            minimum: 1,
            maximum: 100,
            default: 20
          });
        }
        if (parameter.in === 'query' && ['semesterId', 'lecturerId', 'userId', 'actorId'].includes(parameter.name)) {
          parameter.schema = { ...(parameter.schema || {}), type: 'integer', format: 'int64', minimum: 1 };
        }
        if (parameter.in === 'query' && parameter.name === 'projectConfigId' && pathKey.startsWith('/api/sync/jobs')) {
          parameter.schema = { ...(parameter.schema || {}), type: 'string', format: 'uuid' };
        }
      }
    }
  }

}

function normalizeErrorResponses(doc) {
  const sharedErrorStatuses = ['400', '401', '403', '404', '405', '409', '415', '500'];

  for (const pathItem of Object.values(doc.paths || {})) {
    for (const operation of Object.values(pathItem || {})) {
      if (!operation || typeof operation !== 'object') {
        continue;
      }

      operation.responses = operation.responses || {};
      for (const statusCode of sharedErrorStatuses) {
        const response = operation.responses[statusCode];
        if (!response) {
          operation.responses[statusCode] = { $ref: `#/components/responses/Error${statusCode}` };
          continue;
        }

        if (response.$ref === `#/components/responses/Error${statusCode}`) {
          continue;
        }

        const mediaTypes = Object.keys(response.content || {});
        if (mediaTypes.includes('application/problem+json')) {
          continue;
        }

        operation.responses[statusCode] = { $ref: `#/components/responses/Error${statusCode}` };
      }
    }
  }
}

async function fetchJson(url) {
  const resp = await fetch(url, { headers: { Accept: 'application/json' } });
  if (!resp.ok) {
    throw new Error(`Failed ${url}: ${resp.status} ${resp.statusText}`);
  }
  return resp.json();
}

async function main() {
  const unified = {
    openapi: '3.0.3',
    info: {
      title: 'SAMT Unified Backend API',
      version: '1.0.0',
      description: 'Generated by scripts/generate-openapi.mjs from service OpenAPI docs exposed via API Gateway.'
    },
    servers: [{ url: baseUrl }],
    tags: [],
    paths: {},
    components: {
      schemas: {},
      responses: {},
      parameters: {},
      examples: {},
      requestBodies: {},
      headers: {},
      securitySchemes: {
        bearerAuth: {
          type: 'http',
          scheme: 'bearer',
          bearerFormat: 'JWT'
        }
      },
      links: {},
      callbacks: {}
    }
  };

  const errors = [];

  for (const src of sources) {
    try {
      const raw = await fetchJson(src.url);
      const componentRenameMap = {};
      const converted = rewriteRefs(raw, src.key, componentRenameMap);

      const rawComponents = raw.components || {};
      for (const bucket of componentBuckets) {
        const values = rawComponents[bucket] || {};
        if (!componentRenameMap[bucket]) componentRenameMap[bucket] = {};

        for (const [name, value] of Object.entries(values)) {
          const newName = `${src.key}_${name}`;
          componentRenameMap[bucket][name] = newName;
          unified.components[bucket][newName] = rewriteRefs(value, src.key, componentRenameMap);
        }
      }

      const pathsObj = converted.paths || {};
      for (const [p, methods] of Object.entries(pathsObj)) {
        unified.paths[p] = unified.paths[p] || {};
        for (const [method, operation] of Object.entries(methods)) {
          const op = { ...operation };
          if (!op.tags || op.tags.length === 0) {
            op.tags = [src.key];
          }
          if (!op.responses) op.responses = {};
          for (const status of ['400', '401', '403', '404', '409', '500']) {
            op.responses[status] = { $ref: `#/components/responses/Error${status}` };
          }
          unified.paths[p][method] = op;
        }
      }

      unified.tags.push({ name: src.key, description: `Operations proxied from ${src.key} service` });
      console.log(`Loaded OpenAPI source: ${src.url}`);
    } catch (err) {
      errors.push(String(err.message || err));
      console.warn(`Skipping source ${src.url}: ${err.message}`);
    }
  }

  ensureErrorContract(unified);
  ensurePathParameters(unified);
  applySchemaOverrides(unified);
  applyOperationOverrides(unified);
  const selfHealOverrides = await loadOpenapiOverrides();
  applyOpenapiOverrides(unified, selfHealOverrides);
  wrapUserGroupSuccessResponses(unified);
  wrapReportSuccessResponses(unified);
  normalizeErrorResponses(unified);

  if (Object.keys(unified.paths).length === 0) {
    throw new Error(
      `No OpenAPI sources were reachable from ${baseUrl}. Ensure backend is running and gateway exposes /identity, /user-group, /project-config, /sync and /report docs.`
    );
  }

  const yamlOutput = yaml.dump(unified, {
    lineWidth: 140,
    noRefs: true,
    sortKeys: false
  });

  await fs.writeFile(path.resolve('docs/api/openapi.yaml'), yamlOutput, 'utf8');
  console.log(`docs/api/openapi.yaml generated with ${Object.keys(unified.paths).length} paths.`);

  if (errors.length > 0) {
    console.log('Completed with partial sources:');
    for (const e of errors) console.log(`- ${e}`);
  }
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});


