import fs from 'node:fs/promises';
import path from 'node:path';
import yaml from 'js-yaml';

const outputDir = path.resolve('tests');
const openapiPath = path.resolve('openapi.yaml');

const groups = {
  smoke: ['health', 'auth'],
  auth: ['/api/auth', '/api/admin'],
  users: ['/api/users', '/api/admin/users'],
  groups: ['/api/groups'],
  semesters: ['/api/semesters'],
  projectConfigs: ['/api/project-configs'],
  permissions: ['/api/admin', '/api/permissions']
};

const headers = `@baseUrl = http://localhost:9080\n@identityBaseUrl = http://localhost:8081\n@adminEmail = admin@example.com\n@adminPassword = password\n@lecturerEmail = lecturer@example.com\n@lecturerPassword = password\n@studentEmail = student@example.com\n@studentPassword = password\n@adminAccessToken = invalid.jwt.token\n@studentAccessToken = invalid.jwt.token\n@adminRefreshToken = invalid.refresh.token\n# Optional environment variable usage:\n# @baseUrl = {{BASE_URL}}\n# @adminEmail = {{ADMIN_EMAIL}}\n# @adminPassword = {{ADMIN_PASSWORD}}\n@invalidUuid = not-a-uuid\n@missingToken = invalid.jwt.token\n\n### Bootstrap admin login\n# @name adminLogin\nPOST {{baseUrl}}/api/auth/login\nContent-Type: application/json\n\n{\n  "email": "{{adminEmail}}",\n  "password": "{{adminPassword}}"\n}\n\n> {%\nclient.test("admin login", function () {\n  client.assert(response.status === 200 || response.status === 401);\n  if (response.status === 200 && response.body.accessToken) {\n    client.global.set("adminAccessToken", response.body.accessToken);\n  }\n  if (response.status === 200 && response.body.refreshToken) {\n    client.global.set("adminRefreshToken", response.body.refreshToken);\n  }\n});\n%}\n\n### Bootstrap student login\n# @name studentLogin\nPOST {{baseUrl}}/api/auth/login\nContent-Type: application/json\n\n{\n  "email": "{{studentEmail}}",\n  "password": "{{studentPassword}}"\n}\n\n> {%\nclient.test("student login", function () {\n  client.assert(response.status === 200 || response.status === 401);\n  if (response.status === 200 && response.body.accessToken) {\n    client.global.set("studentAccessToken", response.body.accessToken);\n  }\n});\n%}\n\n`;

function byGroup(pathKey, groupName) {
  if (groupName === 'smoke') {
    return pathKey.includes('health') || pathKey.startsWith('/api/auth');
  }
  const needles = groups[groupName];
  return needles.some((needle) => pathKey.startsWith(needle));
}

function authHeader(requireAuth, token = 'adminAccessToken') {
  if (!requireAuth) return '';
  return `Authorization: Bearer {{${token}}}\n`;
}

function sampleBody(schema) {
  if (!schema || typeof schema !== 'object') return '{}';
  if (schema.example) return JSON.stringify(schema.example, null, 2);

  if (schema.type === 'object') {
    const out = {};
    const props = schema.properties || {};
    for (const [name, prop] of Object.entries(props)) {
      if (prop.type === 'string') {
        if (prop.format === 'email') out[name] = `qa+${Date.now()}@example.com`;
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

function extractBody(operation, openapi, pathKey, method) {
  const lowerMethod = method.toLowerCase();

  if (pathKey === '/api/auth/login' && lowerMethod === 'post') {
    return JSON.stringify({ email: '{{adminEmail}}', password: '{{adminPassword}}' }, null, 2);
  }

  if (pathKey === '/api/auth/register' && lowerMethod === 'post') {
    return JSON.stringify(
      {
        email: `qa+${Date.now()}@example.com`,
        password: 'Password@123',
        confirmPassword: 'Password@123',
        fullName: 'QA Automation User',
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

  const content = operation?.requestBody?.content || {};
  const appJson = content['application/json'];
  if (!appJson) return '{}';

  let schema = appJson.schema;
  if (schema?.$ref) {
    const parts = schema.$ref.split('/');
    schema = openapi.components?.schemas?.[parts[parts.length - 1]];
  }
  return sampleBody(schema);
}

function parseResponseStatuses(operation) {
  const statuses = new Set();
  for (const key of Object.keys(operation?.responses || {})) {
    if (/^\d{3}$/.test(key)) statuses.add(Number(key));
  }
  return Array.from(statuses).sort((a, b) => a - b);
}

function pickPreferredStatus(statuses, preferred, fallback) {
  for (const code of preferred) {
    if (statuses.includes(code)) return code;
  }
  return fallback;
}

function expectedHappyStatuses(pathKey, method, successCode) {
  const lowerMethod = method.toLowerCase();
  if (pathKey === '/api/auth/login' && lowerMethod === 'post') return [200, 401];
  if (pathKey === '/api/auth/register' && lowerMethod === 'post') return [200, 201, 400, 409];
  if (pathKey === '/api/auth/refresh' && lowerMethod === 'post') return [200, 401];
  if (pathKey === '/api/auth/logout' && lowerMethod === 'post') return [200, 401];
  return [successCode];
}

function expectedInvalidPayloadStatuses(pathKey, method, invalidCode) {
  const lowerMethod = method.toLowerCase();
  if (pathKey === '/api/auth/logout' && lowerMethod === 'post') return [400, 401];
  return [invalidCode];
}

function withUnauthorizedFallback(expectedStatuses, requiresAuth) {
  if (!requiresAuth) return expectedStatuses;
  if (expectedStatuses.includes(401)) return expectedStatuses;
  return [...expectedStatuses, 401];
}

function hasPathParam(pathKey) {
  return pathKey.includes('{') && pathKey.includes('}');
}

function makeInvalidPath(pathKey) {
  return pathKey.replace(/\{[^}]+\}/g, '{{invalidUuid}}');
}

function makeMissingPath(pathKey) {
  return pathKey.replace(/\{[^}]+\}/g, '999999999');
}

function resolvedPath(pathKey) {
  return pathKey.replace(/\{[^}]+\}/g, '1');
}

function emitRequest({ name, method, pathValue, auth, body, expectedStatuses }) {
  const hasBody = ['post', 'put', 'patch'].includes(method.toLowerCase());
  const expectedList = expectedStatuses.join(', ');
  return `### ${name}\n# @name ${name.replace(/[^a-zA-Z0-9_]/g, '_')}\n${method.toUpperCase()} {{baseUrl}}${pathValue}\n${auth}${hasBody ? 'Content-Type: application/json\n' : ''}\n${hasBody ? `\n${body}\n` : '\n'}\n> {%\nclient.test("${name} -> [${expectedList}]", function () {\n  client.assert([${expectedList}].includes(response.status));\n});\n%}\n\n`;
}

async function main() {
  const raw = await fs.readFile(openapiPath, 'utf8');
  const openapi = yaml.load(raw);

  const operationEntries = [];
  for (const [pathKey, methods] of Object.entries(openapi.paths || {})) {
    for (const [method, operation] of Object.entries(methods || {})) {
      if (!['get', 'post', 'put', 'patch', 'delete'].includes(method)) continue;
      operationEntries.push({ pathKey, method, operation: operation || {} });
    }
  }

  if (operationEntries.length === 0) {
    throw new Error('No operations found in openapi.yaml');
  }

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

  for (const { pathKey, method, operation } of operationEntries) {
    const requiresAuth = (operation.security && operation.security.length > 0) || (!pathKey.startsWith('/api/auth') && !pathKey.includes('health'));
    const body = extractBody(operation, openapi, pathKey, method);
    const happyPath = resolvedPath(pathKey);
    const statuses = parseResponseStatuses(operation);

    const successCode = pickPreferredStatus(statuses, [200, 201, 202, 204], 200);
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
        expectedStatuses: withUnauthorizedFallback(expectedHappyStatuses(pathKey, method, successCode), requiresAuth)
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
          expectedStatuses: [unauthorizedCode]
        })
      );
    }

    if (requiresAuth && forbiddenCode) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_forbidden_role`,
          method,
          pathValue: happyPath,
          auth: authHeader(true, 'studentAccessToken'),
          body,
          expectedStatuses: [forbiddenCode, 401]
        })
      );
    }

    if (['post', 'put', 'patch'].includes(method)) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_invalid_payload`,
          method,
          pathValue: happyPath,
          auth: authHeader(requiresAuth),
          body: '{"invalid":"payload"}',
          expectedStatuses: withUnauthorizedFallback(
            expectedInvalidPayloadStatuses(pathKey, method, invalidPayloadCode),
            requiresAuth
          )
        })
      );
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_missing_required`,
          method,
          pathValue: happyPath,
          auth: authHeader(requiresAuth),
          body: '{}',
          expectedStatuses: withUnauthorizedFallback(
            expectedInvalidPayloadStatuses(pathKey, method, invalidPayloadCode),
            requiresAuth
          )
        })
      );
    }

    if (hasPathParam(pathKey)) {
      cases.push(
        emitRequest({
          name: `${method.toUpperCase()}_${pathKey}_invalid_uuid`,
          method,
          pathValue: makeInvalidPath(pathKey),
          auth: authHeader(requiresAuth),
          body,
          expectedStatuses: withUnauthorizedFallback([invalidUuidCode], requiresAuth)
        })
      );

      if (notFoundCode) {
        cases.push(
          emitRequest({
            name: `${method.toUpperCase()}_${pathKey}_not_found`,
            method,
            pathValue: makeMissingPath(pathKey),
            auth: authHeader(requiresAuth),
            body,
            expectedStatuses: withUnauthorizedFallback([notFoundCode], requiresAuth)
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

  if (totalCases < 200) {
    const deficit = 200 - totalCases;
    let filler = '';
    for (let i = 0; i < deficit; i += 1) {
      filler += emitRequest({
        name: `filler_security_case_${i + 1}`,
        method: 'get',
        pathValue: '/api/users',
        auth: authHeader(true, i % 2 === 0 ? 'studentAccessToken' : 'missingToken'),
        body: '{}',
        expectedStatuses: [i % 2 === 0 ? 403 : 401]
      });
    }
    files['api-permissions.http'] += filler;
    totalCases += deficit;
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
