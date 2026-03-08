import fs from 'node:fs/promises';
import path from 'node:path';
import yaml from 'js-yaml';
import { createSchemaPayloadGenerator } from './schema-payload-generator.mjs';
import { loadGeneratorOverrides, operationKey } from './self-heal-overrides.mjs';

const outputDir = path.resolve('tests');
const openapiPath = path.resolve('openapi.yaml');
let generatorOverrides = { operations: {}, globals: {} };
let payloadGenerator = null;

const groups = {
  smoke: ['health', 'auth'],
  auth: ['/api/auth'],
  users: ['/api/users', '/api/admin/users'],
  groups: ['/api/groups'],
  semesters: ['/api/semesters'],
  projectConfigs: ['/api/project-configs'],
  permissions: ['/api/admin', '/api/permissions']
};

const authPathOrder = new Map([
  ['/api/auth/login', 0],
  ['/api/auth/refresh', 1],
  ['/api/auth/logout', 2],
  ['/api/auth/register', 3]
]);

const statefulPathOrder = new Map([
  ['/api/admin/users/{userId}/external-accounts', 0],
  ['/api/admin/users/{userId}/lock', 1],
  ['/api/admin/users/{userId}/unlock', 2],
  ['/api/admin/users/{userId}', 3],
  ['/api/admin/users/{userId}/restore', 4],
  ['/api/groups/{groupId}/members', 0],
  ['/api/groups/{groupId}/members/{userId}/promote', 1],
  ['/api/groups/{groupId}/members/{userId}/demote', 2],
  ['/api/groups/{groupId}/members/{userId}', 3],
  ['/api/project-configs/{id}', 0],
  ['/api/project-configs/{id}/verify', 1],
  ['/api/project-configs/group/{groupId}', 2],
  ['/api/project-configs/admin/{id}/restore', 3]
]);

const headers = `@baseUrl = http://localhost:9080
@identityBaseUrl = http://localhost:8081
@adminEmail = admin@samt.local
@adminPassword = Str0ng@Pass!
@lecturerEmail = qa.lecturer@samt.local
@lecturerPassword = Str0ng@Pass!
@studentEmail = qa.student@samt.local
@studentPassword = Str0ng@Pass!
@invalidUuid = not-a-uuid
@missingToken = invalid.jwt.token

# Prefer running regression through:
#   httpyac tests/bootstrap.http tests/fixtures.http tests/api-*.http --all
# so globals (tokens and fixture IDs) are present for generated cases.

`;

function byGroup(pathKey, groupName) {
  if (groupName === 'smoke') {
    return pathKey.includes('health') || pathKey.startsWith('/api/auth');
  }

  if (groupName === 'auth') {
    return pathKey.startsWith('/api/auth');
  }

  if (groupName === 'permissions') {
    return pathKey.startsWith('/api/permissions')
      || (pathKey.startsWith('/api/admin') && !pathKey.startsWith('/api/admin/users'));
  }

  const needles = groups[groupName];
  return needles.some((needle) => pathKey.startsWith(needle));
}

function getOperationOverride(pathKey, method) {
  return generatorOverrides?.operations?.[operationKey(method, pathKey)] || null;
}

function authHeader(requireAuth, token = 'adminAccessToken') {
  if (!requireAuth) return '';
  return `Authorization: Bearer {{${token}}}\n`;
}

function detectPathParamKind(pathKey, paramName, paramSchema = {}) {
  const lowerName = String(paramName || '').toLowerCase();
  const lowerPath = pathKey.toLowerCase();
  const schemaType = String(paramSchema.type || '').toLowerCase();

  if (lowerName === 'code' && lowerPath.includes('/api/semesters/code/')) return 'semesterCode';
  if (lowerName === 'entitytype' && lowerPath.includes('/api/admin/audit/entity/')) return 'auditEntityType';
  if (lowerName === 'entityid' && lowerPath.includes('/api/admin/audit/entity/')) return 'auditEntityId';
  if (lowerName === 'actorid' && lowerPath.includes('/api/admin/audit/actor/')) return 'actorId';
  if (lowerName === 'userid' || lowerPath.includes('/users/')) return 'userId';
  if (lowerName === 'groupid' || lowerPath.includes('/groups/')) return 'groupId';
  if (lowerName === 'semesterid' || lowerPath.includes('/semesters/')) return 'semesterId';

  if (lowerName === 'id' && lowerPath.includes('/project-configs/')) return 'projectConfigId';
  if (lowerName === 'id' && lowerPath.includes('/semesters/')) return 'semesterId';
  if (lowerName === 'id' && lowerPath.includes('/groups/')) return 'groupId';
  if (lowerName === 'id' && lowerPath.includes('/users/')) return 'userId';

  if (schemaType === 'string') return 'stringId';
  return 'numericId';
}

function fixtureVariableForKind(kind) {
  switch (kind) {
    case 'auditEntityType':
      return 'USER';
    case 'auditEntityId':
    case 'actorId':
      return '4';
    case 'semesterCode':
      return 'QA-2026-S1';
    case 'userId':
      return '{{fixtureUserId}}';
    case 'groupId':
      return '{{fixtureGroupId}}';
    case 'semesterId':
      return '{{fixtureSemesterId}}';
    case 'projectConfigId':
      return '{{fixtureProjectConfigId}}';
    case 'stringId':
      return '{{fixtureProjectConfigId}}';
    default:
      return '1';
  }
}

function missingValueForKind(kind) {
  switch (kind) {
    case 'auditEntityType':
      return 'UNKNOWN_ENTITY';
    case 'auditEntityId':
    case 'actorId':
      return '999999999';
    case 'semesterCode':
      return 'MISSING-SEMESTER-CODE';
    case 'projectConfigId':
    case 'stringId':
      return '00000000-0000-0000-0000-000000000999';
    default:
      return '999999999';
  }
}

function invalidValueForKind(kind) {
  if (kind === 'semesterCode') {
    return 'not-a-uuid';
  }

  if (kind === 'projectConfigId' || kind === 'stringId') {
    return '{{invalidUuid}}';
  }
  return '{{invalidUuid}}';
}

function collectPathParams(pathKey, operation, pathItem) {
  const opParams = Array.isArray(operation?.parameters) ? operation.parameters : [];
  const pathParams = Array.isArray(pathItem?.parameters) ? pathItem.parameters : [];
  return [...pathParams, ...opParams].filter((param) => param?.in === 'path' && param?.name);
}

function collectQueryParams(operation, pathItem) {
  const opParams = Array.isArray(operation?.parameters) ? operation.parameters : [];
  const pathParams = Array.isArray(pathItem?.parameters) ? pathItem.parameters : [];
  return [...pathParams, ...opParams].filter((param) => param?.in === 'query' && param?.name);
}

function mapPathParams(pathKey, params, mapper) {
  let output = pathKey;
  for (const param of params) {
    const kind = detectPathParamKind(pathKey, param.name, param.schema || {});
    output = output.replace(new RegExp(`\\{${param.name}\\}`, 'g'), mapper(kind));
  }
  return output;
}

function sampleQueryValue(pathKey, param) {
  const lowerName = String(param?.name || '').toLowerCase();
  const schema = param?.schema || {};

  if (/^arg\d+$/.test(lowerName)) {
    return null;
  }

  if (pathKey === '/api/admin/audit/range' && lowerName === 'startdate') {
    return '2026-01-01T00:00:00';
  }

  if (pathKey === '/api/admin/audit/range' && lowerName === 'enddate') {
    return '2026-12-31T23:59:59';
  }

  if (lowerName === 'page') return '0';
  if (lowerName === 'size') return '20';

  if (schema.default !== undefined && schema.default !== null) {
    return String(schema.default);
  }

  if (schema.type === 'integer' || schema.type === 'number') {
    return String(schema.minimum ?? 1);
  }

  if (schema.type === 'boolean') {
    return 'true';
  }

  if (schema.format === 'date-time') {
    return '2026-01-01T00:00:00';
  }

  if (schema.format === 'date') {
    return '2026-01-01';
  }

  return 'sample';
}

function appendQueryParams(pathValue, pathKey, queryParams) {
  const pairs = [];

  for (const param of queryParams) {
    const value = sampleQueryValue(pathKey, param);
    if (value === null || value === undefined) {
      continue;
    }

    if (param.required || ['page', 'size'].includes(String(param.name || '').toLowerCase())) {
      pairs.push(`${encodeURIComponent(param.name)}=${encodeURIComponent(value)}`);
    }
  }

  if (pairs.length === 0) {
    return pathValue;
  }

  return `${pathValue}${pathValue.includes('?') ? '&' : '?'}${pairs.join('&')}`;
}

function sampleBody(schema) {
  if (!schema || typeof schema !== 'object') return '{}';
  if (schema.example) return JSON.stringify(schema.example, null, 2);

  if (schema.type === 'object') {
    const out = {};
    const props = schema.properties || {};
    for (const [name, prop] of Object.entries(props)) {
      if (prop.type === 'string') {
        if (prop.format === 'email') out[name] = 'qa.student2@samt.local';
        else if (prop.format === 'uuid') out[name] = '00000000-0000-0000-0000-000000000000';
        else out[name] = `${name}-sample`;
      } else if (prop.type === 'integer' || prop.type === 'number') {
        out[name] = 1;
      } else if (prop.type === 'boolean') {
        out[name] = true;
      } else if (prop.type === 'array') {
        out[name] = [];
      } else if (prop.type === 'object') {
        out[name] = {};
      }
    }
    return JSON.stringify(out, null, 2);
  }

  return '{}';
}

function stringifyBody(body) {
  return JSON.stringify(body, null, 2).replace(/"__RAW__(\{\{[^}]+\}\})"/g, '$1');
}

function getRequestSchema(operation, openapi) {
  const content = operation?.requestBody?.content || {};
  const appJson = content['application/json'];
  if (!appJson) return null;

  let schema = appJson.schema;
  if (schema?.$ref) {
    const parts = schema.$ref.split('/');
    schema = openapi.components?.schemas?.[parts[parts.length - 1]];
  }

  return schema || null;
}

function hasRequiredRequestFields(operation, openapi) {
  const schema = getRequestSchema(operation, openapi);
  return Array.isArray(schema?.required) && schema.required.length > 0;
}

function extractBody(operation, openapi, pathKey, method) {
  const lowerMethod = method.toLowerCase();
  const override = getOperationOverride(pathKey, method);

  if (override?.body?.happy) {
    return stringifyBody(override.body.happy);
  }

  if (pathKey === '/api/auth/login' && lowerMethod === 'post') {
    return JSON.stringify({ email: '{{adminEmail}}', password: '{{adminPassword}}' }, null, 2);
  }

  if (pathKey === '/api/auth/register' && lowerMethod === 'post') {
    return JSON.stringify(
      {
        email: 'qa.student2@samt.local',
        password: 'Str0ng@Pass!',
        confirmPassword: 'Str0ng@Pass!',
        fullName: 'QA Student Two',
        role: 'STUDENT'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/auth/refresh' && lowerMethod === 'post') {
    return JSON.stringify({ refreshToken: '{{adminRefreshToken}}' }, null, 2);
  }

  if (pathKey === '/api/auth/logout' && lowerMethod === 'post') {
    return JSON.stringify({ refreshToken: '{{adminRefreshToken}}' }, null, 2);
  }

  if (pathKey === '/api/groups' && lowerMethod === 'post') {
    return JSON.stringify(
      {
        groupName: 'SE2601-G1',
        semesterId: '{{fixtureSemesterId}}',
        lecturerId: '{{fixtureLecturerId}}'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/groups/{groupId}' && lowerMethod === 'put') {
    return JSON.stringify(
      {
        groupName: 'SE2601-G1',
        semesterId: '{{fixtureSemesterId}}',
        lecturerId: '{{fixtureLecturerId}}'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/groups/{groupId}/members' && lowerMethod === 'post') {
    return JSON.stringify({ userId: '{{fixtureUserId}}' }, null, 2);
  }

  if (pathKey === '/api/groups/{groupId}/lecturer' && lowerMethod === 'patch') {
    return JSON.stringify({ lecturerId: '{{fixtureLecturerId}}' }, null, 2);
  }

  if (pathKey === '/api/semesters' && lowerMethod === 'post') {
    return JSON.stringify(
      {
        semesterCode: 'QA-2026-S1',
        semesterName: 'QA Semester 2026 S1',
        startDate: '2026-01-15',
        endDate: '2026-05-30'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/semesters/{id}' && lowerMethod === 'put') {
    return JSON.stringify(
      {
        semesterName: 'Updated Generated Semester',
        startDate: '2026-01-20',
        endDate: '2026-06-05'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/project-configs' && lowerMethod === 'post') {
    return JSON.stringify(
      {
        groupId: '{{fixtureGroupId}}',
        jiraHostUrl: 'https://example.atlassian.net',
        jiraApiToken: 'ATATTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
        githubRepoUrl: 'https://github.com/example-org/example-repo',
        githubToken: 'ghp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/admin/users' && lowerMethod === 'post') {
    return JSON.stringify(
      {
        email: 'qa.generated.user@samt.local',
        password: 'Str0ng@Pass!',
        fullName: 'QA Generated User',
        role: 'STUDENT'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/admin/users/{userId}/external-accounts' && lowerMethod === 'put') {
    return JSON.stringify(
      {
        jiraAccountId: '{{fixtureJiraAccountId}}',
        githubUsername: '{{fixtureGithubUsername}}'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/users/{userId}' && lowerMethod === 'put') {
    return JSON.stringify({ fullName: 'QA Student Updated' }, null, 2);
  }

  if (pathKey === '/api/project-configs/{id}' && lowerMethod === 'put') {
    return JSON.stringify(
      {
        jiraHostUrl: 'https://example.atlassian.net',
        jiraApiToken: 'ATATTAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA',
        githubRepoUrl: 'https://github.com/example-org/example-repo',
        githubToken: 'ghp_AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA'
      },
      null,
      2
    );
  }

  const schema = getRequestSchema(operation, openapi);
  if (!schema) return '{}';
  return stringifyBody(payloadGenerator.generateHappyPayload(pathKey, method, operation, {
    includeOptional: true,
    seed: operationKey(method, pathKey)
  }));
}

function extractInvalidBody(operation, openapi, pathKey, method) {
  const lowerMethod = method.toLowerCase();
  const override = getOperationOverride(pathKey, method);

  if (override?.body?.invalid_payload) {
    return stringifyBody(override.body.invalid_payload);
  }

  if (pathKey === '/api/groups/{groupId}/members' && lowerMethod === 'post') {
    return JSON.stringify({ userId: 'not-a-number' }, null, 2);
  }

  if (pathKey === '/api/semesters/{id}' && lowerMethod === 'put') {
    return JSON.stringify(
      {
        semesterName: 'Updated Generated Semester',
        startDate: '2026-06-05',
        endDate: '2026-01-20'
      },
      null,
      2
    );
  }

  if (pathKey === '/api/admin/users/{userId}/external-accounts' && lowerMethod === 'put') {
    return JSON.stringify({ jiraAccountId: 'short', githubUsername: 'bad!' }, null, 2);
  }

  if (pathKey === '/api/project-configs/{id}' && lowerMethod === 'put') {
    return JSON.stringify(
      {
        jiraHostUrl: 'invalid-url',
        jiraApiToken: 'bad-token',
        githubRepoUrl: 'invalid-repo',
        githubToken: 'short'
      },
      null,
      2
    );
  }

  return stringifyBody(payloadGenerator.generateInvalidPayload(pathKey, method, operation, {
    seed: `${operationKey(method, pathKey)}:invalid`
  }));
}

function shouldEmitMissingRequiredCase(operation, openapi) {
  return hasRequiredRequestFields(operation, openapi);
}

function resolvePathValue(pathKey, method, caseKind, basePathValue) {
  const lowerMethod = method.toLowerCase();
  const override = getOperationOverride(pathKey, method);

  if (override?.pathTemplateByCase?.[caseKind]) {
    return override.pathTemplateByCase[caseKind];
  }

  if (pathKey.startsWith('/api/admin/users/{userId}')) {
    const lifecyclePath = basePathValue
      .replace('{{fixtureUserId}}', '{{fixtureAdminLifecycleUserId}}')
      .replace('/1/', '/{{fixtureAdminLifecycleUserId}}/');
    return lifecyclePath;
  }

  if (pathKey === '/api/groups/{groupId}' && lowerMethod === 'delete') {
    return basePathValue.replace('{{fixtureGroupId}}', '{{fixtureEmptyGroupId}}');
  }

  if (pathKey === '/api/groups/{groupId}/members') {
    return basePathValue.replace('{{fixtureGroupId}}', '{{fixtureEmptyGroupId}}');
  }

  if (pathKey.includes('/api/groups/{groupId}/members/{userId}')) {
    return basePathValue
      .replace('{{fixtureUserId}}', '{{fixtureStatefulMemberUserId}}')
      .replace('{{fixtureGroupId}}', '{{fixtureEmptyGroupId}}');
  }

  if (pathKey === '/api/project-configs/{id}' && lowerMethod === 'delete') {
    return basePathValue.replace('{{fixtureProjectConfigId}}', '{{fixtureDeletableProjectConfigId}}');
  }

  if (pathKey === '/api/project-configs/admin/{id}/restore') {
    return basePathValue.replace('{{fixtureProjectConfigId}}', '{{fixtureDeletableProjectConfigId}}');
  }

  return basePathValue;
}

function resolveBody(pathKey, method, caseKind, baseBody) {
  const override = getOperationOverride(pathKey, method);
  if (override?.body?.[caseKind]) {
    return stringifyBody(override.body[caseKind]);
  }

  if (
    pathKey === '/api/groups/{groupId}/members' &&
    method.toLowerCase() === 'post' &&
    caseKind !== 'invalid_payload' &&
    caseKind !== 'missing_required'
  ) {
    return JSON.stringify({ userId: '{{fixtureStatefulMemberUserId}}' }, null, 2);
  }

  return baseBody;
}

function parseResponseStatuses(operation) {
  const statuses = new Set();
  for (const key of Object.keys(operation?.responses || {})) {
    if (/^\d{3}$/.test(key)) statuses.add(Number(key));
  }
  return Array.from(statuses).sort((a, b) => a - b);
}

function normalizeExpectedStatuses(method, statuses) {
  const out = new Set(statuses);
  const lowerMethod = method.toLowerCase();

  if (lowerMethod === 'post' && out.has(200) && !out.has(201)) {
    out.add(201);
  }
  if (lowerMethod === 'delete' && out.has(200) && !out.has(204)) {
    out.add(204);
  }
  if ((lowerMethod === 'put' || lowerMethod === 'patch') && out.has(200) && !out.has(204)) {
    out.add(204);
  }
  if ((lowerMethod === 'put' || lowerMethod === 'patch') && out.has(204) && !out.has(200)) {
    out.add(200);
  }

  return Array.from(out).sort((a, b) => a - b);
}

function addRouteSpecificExpectedStatuses(pathKey, method, statuses) {
  const out = new Set(statuses);

  if (pathKey === '/api/auth/login' && method.toLowerCase() === 'post') {
    out.add(429);
  }

  return Array.from(out).sort((a, b) => a - b);
}

function pickPreferredStatus(statuses, preferred, fallback) {
  for (const code of preferred) {
    if (statuses.includes(code)) return code;
  }
  return fallback;
}

function expectedHappyStatuses(pathKey, method, successCode) {
  const override = getOperationOverride(pathKey, method);
  if (override?.expectedStatuses?.happy?.length) {
    return [...override.expectedStatuses.happy].sort((a, b) => a - b);
  }

  if (method.toLowerCase() === 'delete') {
    return Array.from(new Set([successCode, 204])).sort((a, b) => a - b);
  }

  if (pathKey === '/api/groups/{groupId}/members' && method.toLowerCase() === 'post') {
    return [200, 201];
  }

  if (method.toLowerCase() === 'post' && ['/api/admin/users', '/api/groups', '/api/semesters', '/api/project-configs'].includes(pathKey)) {
    return Array.from(new Set([successCode, 201, 409])).sort((a, b) => a - b);
  }

  if (pathKey === '/api/semesters/{id}/activate' && method.toLowerCase() === 'patch') {
    return [200, 204];
  }

  return [successCode];
}

function expectedInvalidPayloadStatuses(pathKey, method, invalidCode) {
  void pathKey;
  void method;
  return [invalidCode];
}

function expectedInvalidUuidStatuses(pathKey, requiresAuth, invalidUuidCode, notFoundCode) {
  if (pathKey === '/api/semesters/code/{code}') {
    return withUnauthorizedFallback([notFoundCode || 404], requiresAuth);
  }

  return withUnauthorizedFallback([invalidUuidCode], requiresAuth);
}

function expectedForbiddenRoleStatuses(forbiddenCode) {
  return forbiddenCode ? [forbiddenCode] : [403];
}

function expectedNotFoundStatuses(pathKey, requiresAuth, notFoundCode) {
  if (
    pathKey === '/api/admin/audit/actor/{actorId}' ||
    pathKey === '/api/admin/audit/entity/{entityType}/{entityId}'
  ) {
    return withUnauthorizedFallback([200], requiresAuth);
  }

  return withUnauthorizedFallback([notFoundCode], requiresAuth);
}

function shouldEmitForbiddenRoleCase(pathKey, method) {
  const normalizedMethod = method.toLowerCase();

  if (pathKey.startsWith('/api/admin/')) {
    return true;
  }

  const exactMatches = new Set([
    'post /api/groups',
    'put /api/groups/{groupId}',
    'delete /api/groups/{groupId}',
    'patch /api/groups/{groupId}/lecturer',
    'post /api/semesters',
    'put /api/semesters/{id}',
    'patch /api/semesters/{id}/activate',
    'post /api/groups/{groupId}/members',
    'put /api/groups/{groupId}/members/{userId}/promote',
    'put /api/groups/{groupId}/members/{userId}/demote',
    'delete /api/groups/{groupId}/members/{userId}'
  ]);

  return exactMatches.has(`${normalizedMethod} ${pathKey}`);
}

function withUnauthorizedFallback(expectedStatuses, requiresAuth) {
  if (!requiresAuth) return expectedStatuses;
  if (expectedStatuses.includes(401)) return expectedStatuses;
  return [...expectedStatuses, 401];
}

function hasPathParam(pathKey) {
  return pathKey.includes('{') && pathKey.includes('}');
}

function preRequestScriptForCase(pathKey, caseKind) {
  if (!pathKey.startsWith('/api/project-configs')) {
    return '';
  }

  void caseKind;
  return 'await new Promise((resolve) => setTimeout(resolve, 7000));';
}

function expectedRegisterStatuses(pathKey, method, successCode) {
  if (pathKey === '/api/auth/register' && method.toLowerCase() === 'post') {
    return [201, 409];
  }
  return expectedHappyStatuses(pathKey, method, successCode);
}

function requiresAuthentication(pathKey, operation) {
  if (pathKey === '/api/auth/logout') {
    return true;
  }

  return (operation.security && operation.security.length > 0) ||
    (!pathKey.startsWith('/api/auth') && !pathKey.includes('health'));
}

function postResponseScriptForCase(pathKey, method, caseKind) {
  if (pathKey === '/api/auth/login' && method.toLowerCase() === 'post' && caseKind === 'happy') {
    return [
      'if (response.status === 200 && response.body && response.body.accessToken) {',
      "  client.global.set('adminAccessToken', response.body.accessToken);",
      '}',
      'if (response.status === 200 && response.body && response.body.refreshToken) {',
      "  client.global.set('adminRefreshToken', response.body.refreshToken);",
      '}'
    ].join('\n');
  }

  return '';
}

function emitRequest({ name, method, pathValue, auth, body, expectedStatuses, preRequestScript = '', postResponseScript = '' }) {
  const hasBody = ['post', 'put', 'patch'].includes(method.toLowerCase());
  const expectedList = expectedStatuses.join(', ');
  const beforeRequest = preRequestScript ? `< {% ${preRequestScript} %}\n` : '';
  const afterResponse = postResponseScript ? `${postResponseScript}\n` : '';
  return `### ${name}\n# @name ${name.replace(/[^a-zA-Z0-9_]/g, '_')}\n${beforeRequest}${method.toUpperCase()} {{baseUrl}}${pathValue}\n${auth}${hasBody ? 'Content-Type: application/json\n' : ''}\n${hasBody ? `\n${body}\n` : '\n'}\n> {%\n${afterResponse}client.test("${name} -> [${expectedList}]", function () {\n  const expectedStatuses = [${expectedList}];\n  const responseBody = typeof response.body === 'string' ? response.body : JSON.stringify(response.body);\n  client.assert(\n    expectedStatuses.includes(response.status),\n    'Unexpected status ' + response.status + '; expected [${expectedList}]; body=' + (responseBody || '<empty>')\n  );\n});\n%}\n\n`;
}

async function main() {
  const raw = await fs.readFile(openapiPath, 'utf8');
  const openapi = yaml.load(raw);
  generatorOverrides = await loadGeneratorOverrides();
  payloadGenerator = createSchemaPayloadGenerator({ openapi, overrides: generatorOverrides });

  const operationEntries = [];
  for (const [pathKey, methods] of Object.entries(openapi.paths || {})) {
    for (const [method, operation] of Object.entries(methods || {})) {
      if (!['get', 'post', 'put', 'patch', 'delete'].includes(method)) continue;
      if (method === 'parameters') continue;
      operationEntries.push({ pathKey, method, operation: operation || {}, pathItem: methods || {} });
    }
  }

  if (operationEntries.length === 0) {
    throw new Error('No operations found in openapi.yaml');
  }

  const methodOrder = { post: 0, get: 1, put: 2, patch: 3, delete: 4 };
  operationEntries.sort((left, right) => {
    const leftAuthOrder = authPathOrder.get(left.pathKey);
    const rightAuthOrder = authPathOrder.get(right.pathKey);
    if (leftAuthOrder !== undefined || rightAuthOrder !== undefined) {
      return (leftAuthOrder ?? Number.MAX_SAFE_INTEGER) - (rightAuthOrder ?? Number.MAX_SAFE_INTEGER);
    }

    const leftStatefulOrder = statefulPathOrder.get(left.pathKey);
    const rightStatefulOrder = statefulPathOrder.get(right.pathKey);
    if (leftStatefulOrder !== undefined || rightStatefulOrder !== undefined) {
      return (leftStatefulOrder ?? Number.MAX_SAFE_INTEGER) - (rightStatefulOrder ?? Number.MAX_SAFE_INTEGER);
    }

    const leftParamCount = (left.pathKey.match(/\{/g) || []).length;
    const rightParamCount = (right.pathKey.match(/\{/g) || []).length;
    if (leftParamCount !== rightParamCount) {
      return leftParamCount - rightParamCount;
    }

    const pathCompare = left.pathKey.localeCompare(right.pathKey);
    if (pathCompare !== 0) {
      return pathCompare;
    }

    return (methodOrder[left.method] ?? 99) - (methodOrder[right.method] ?? 99);
  });

  const files = {
    'api-smoke.http': '',
    'api-auth.http': '',
    'api-users.http': '',
    'api-groups.http': '',
    'api-semesters.http': '',
    'api-project-configs.http': '',
    'api-permissions.http': ''
  };

  for (const fileName of Object.keys(files)) {
    files[fileName] = headers;
  }

  let totalCases = 0;

  for (const { pathKey, method, operation, pathItem } of operationEntries) {
    const requiresAuth = requiresAuthentication(pathKey, operation);

    const pathParams = collectPathParams(pathKey, operation, pathItem);
    const queryParams = collectQueryParams(operation, pathItem);

    const happyPath = resolvePathValue(
      pathKey,
      method,
      'happy',
      appendQueryParams(mapPathParams(pathKey, pathParams, fixtureVariableForKind), pathKey, queryParams)
    );
    const invalidPath = resolvePathValue(
      pathKey,
      method,
      'invalid_uuid',
      appendQueryParams(mapPathParams(pathKey, pathParams, invalidValueForKind), pathKey, queryParams)
    );
    const missingPath = resolvePathValue(
      pathKey,
      method,
      'not_found',
      appendQueryParams(mapPathParams(pathKey, pathParams, missingValueForKind), pathKey, queryParams)
    );

    const body = resolveBody(pathKey, method, 'happy', extractBody(operation, openapi, pathKey, method));
    const statuses = parseResponseStatuses(operation);

    const successCode = pickPreferredStatus(statuses, [200, 201, 202, 204], 200);
    const documentedStatuses = addRouteSpecificExpectedStatuses(
      pathKey,
      method,
      normalizeExpectedStatuses(method, statuses.length > 0 ? statuses : [successCode])
    );
    const unauthorizedCode = statuses.includes(401) ? 401 : null;
    const forbiddenCode = statuses.includes(403) ? 403 : null;
    const invalidPayloadCode = pickPreferredStatus(statuses, [400, 422], 400);
    const invalidUuidCode = statuses.includes(400) ? 400 : invalidPayloadCode;
    const notFoundCode = statuses.includes(404) ? 404 : null;

    const cases = [];

    cases.push(
      emitRequest({
        name: `${method.toUpperCase()}_${pathKey}_happy`,
        method,
        pathValue: happyPath,
        auth: authHeader(requiresAuth),
        body,
        expectedStatuses: expectedRegisterStatuses(pathKey, method, successCode),
          preRequestScript: preRequestScriptForCase(pathKey, 'happy'),
          postResponseScript: postResponseScriptForCase(pathKey, method, 'happy')
      })
    );

    if (requiresAuth && unauthorizedCode) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_unauthorized`,
          method,
          pathValue: happyPath,
          auth: authHeader(true, 'missingToken'),
          body,
          expectedStatuses: [unauthorizedCode],
          preRequestScript: preRequestScriptForCase(pathKey, 'unauthorized')
        })
      );
    }

    if (requiresAuth && forbiddenCode && shouldEmitForbiddenRoleCase(pathKey, method)) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_forbidden_role`,
          method,
          pathValue: happyPath,
          auth: authHeader(true, 'studentAccessToken'),
          body,
          expectedStatuses: expectedForbiddenRoleStatuses(forbiddenCode),
          preRequestScript: preRequestScriptForCase(pathKey, 'forbidden_role')
        })
      );
    }

    const hasJsonRequestBody = Boolean(operation?.requestBody?.content?.['application/json']);

    if (['post', 'put', 'patch'].includes(method) && hasJsonRequestBody) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_invalid_payload`,
          method,
          pathValue: happyPath,
          auth: authHeader(requiresAuth),
          body: resolveBody(pathKey, method, 'invalid_payload', extractInvalidBody(operation, openapi, pathKey, method)),
          expectedStatuses: withUnauthorizedFallback(expectedInvalidPayloadStatuses(pathKey, method, invalidPayloadCode), requiresAuth),
          preRequestScript: preRequestScriptForCase(pathKey, 'invalid_payload')
        })
      );

      if (shouldEmitMissingRequiredCase(operation, openapi)) {
        cases.push(
          emitRequest({
            name: `${method.toUpperCase()}_${pathKey}_missing_required`,
            method,
            pathValue: happyPath,
            auth: authHeader(requiresAuth),
            body: '{}',
            expectedStatuses: withUnauthorizedFallback(expectedInvalidPayloadStatuses(pathKey, method, invalidPayloadCode), requiresAuth),
            preRequestScript: preRequestScriptForCase(pathKey, 'missing_required')
          })
        );
      }
    }

    if (hasPathParam(pathKey)) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_invalid_uuid`,
          method,
          pathValue: invalidPath,
          auth: authHeader(requiresAuth),
          body,
          expectedStatuses: expectedInvalidUuidStatuses(pathKey, requiresAuth, invalidUuidCode, notFoundCode),
          preRequestScript: preRequestScriptForCase(pathKey, 'invalid_uuid')
        })
      );

      if (notFoundCode) {
        cases.push(
          emitRequest({
            name: `${method.toUpperCase()}_${pathKey}_not_found`,
            method,
            pathValue: missingPath,
            auth: authHeader(requiresAuth),
            body,
            expectedStatuses: expectedNotFoundStatuses(pathKey, requiresAuth, notFoundCode),
            preRequestScript: preRequestScriptForCase(pathKey, 'not_found')
          })
        );
      }
    }

    const targetFiles = Object.keys(files).filter((fileName) => {
      const key = fileName.replace('api-', '').replace('.http', '').replace(/-/g, '');
      if (fileName === 'api-smoke.http') return byGroup(pathKey, 'smoke');
      if (key === 'auth') return byGroup(pathKey, 'auth');
      if (key === 'users') return byGroup(pathKey, 'users');
      if (key === 'groups') return byGroup(pathKey, 'groups');
      if (key === 'semesters') return byGroup(pathKey, 'semesters');
      if (key === 'projectconfigs') return byGroup(pathKey, 'projectConfigs');
      if (key === 'permissions') return byGroup(pathKey, 'permissions');
      return false;
    });

    for (const fileName of targetFiles) {
      for (const c of cases) {
        files[fileName] += c;
        totalCases += 1;
      }
    }
  }

  await fs.mkdir(outputDir, { recursive: true });
  for (const [fileName, content] of Object.entries(files)) {
    await fs.writeFile(path.join(outputDir, fileName), content, 'utf8');
  }

  console.log(`Generated ${Object.keys(files).length} files with ${totalCases} test cases.`);
}

main().catch((err) => {
  console.error(err.message || err);
  process.exit(1);
});
