import crypto from 'node:crypto';
import { operationKey } from './self-heal-overrides.mjs';

function stableHash(input) {
  return crypto.createHash('sha256').update(String(input)).digest('hex');
}

function deterministicNumber(seed, min, max) {
  const floorMin = Number.isFinite(min) ? min : 1;
  const floorMax = Number.isFinite(max) ? max : floorMin + 1000;
  const slice = Number.parseInt(seed.slice(0, 8), 16);
  return floorMin + (slice % (floorMax - floorMin + 1));
}

function deterministicToken(prefix, length, seed) {
  const digest = stableHash(`${prefix}:${seed}`).replace(/[^a-zA-Z0-9]/g, 'A').toUpperCase();
  const body = `${prefix}${digest}`.replace(/[^A-Z0-9]/g, 'A');
  return body.slice(0, Math.max(length, prefix.length)).padEnd(length, 'A');
}

function sanitizeSlug(value) {
  return String(value).toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '') || 'sample';
}

function isOptionalProperty(schema, propertyName) {
  return !(Array.isArray(schema?.required) && schema.required.includes(propertyName));
}

export function createSchemaPayloadGenerator({ openapi, overrides = {} }) {
  const components = openapi?.components?.schemas || {};

  function resolveSchema(schema) {
    if (!schema) {
      return null;
    }

    if (schema.$ref) {
      const name = schema.$ref.split('/').pop();
      return components[name] || null;
    }

    if (schema.allOf) {
      return schema.allOf.reduce((merged, entry) => ({
        ...(merged || {}),
        ...(resolveSchema(entry) || {})
      }), {});
    }

    if (schema.oneOf?.length) {
      return resolveSchema(schema.oneOf[0]);
    }

    if (schema.anyOf?.length) {
      return resolveSchema(schema.anyOf[0]);
    }

    return schema;
  }

  function getRequestSchema(operation) {
    const appJson = operation?.requestBody?.content?.['application/json'];
    return resolveSchema(appJson?.schema || null);
  }

  function fixtureReference(pathKey, method, propertyName, schema, seed) {
    const lowerProperty = String(propertyName || '').toLowerCase();
    const opKey = operationKey(method, pathKey);
    const configuredAlias = overrides?.globals?.fixtureAliases?.[`${opKey}:${propertyName}`];
    if (configuredAlias) {
      return configuredAlias;
    }

    if (lowerProperty === 'groupid') return '__RAW__{{fixtureGroupId}}';
    if (lowerProperty === 'semesterid') return '__RAW__{{fixtureSemesterId}}';
    if (lowerProperty === 'userid') {
      if (pathKey === '/api/groups/{groupId}/members') {
        return '__RAW__{{fixtureStatefulMemberUserId}}';
      }
      return '__RAW__{{fixtureUserId}}';
    }

    if (lowerProperty === 'lecturerid') return '__RAW__{{fixtureLecturerId}}';
    if (lowerProperty === 'jiraaccountid') return '{{fixtureJiraAccountId}}';
    if (lowerProperty === 'githubusername') return '{{fixtureGithubUsername}}';
    if (schema?.format === 'uuid' && lowerProperty.endsWith('id')) return '{{fixtureProjectConfigId}}';

    const lowerPath = pathKey.toLowerCase();
    if (lowerProperty === 'email' && lowerPath.includes('/api/admin/users')) {
      return `qa.generated.${sanitizeSlug(pathKey)}.${seed.slice(0, 8)}@samt.local`;
    }

    return null;
  }

  function generateString(schema, context) {
    const resolved = resolveSchema(schema) || {};
    const seed = stableHash(`${context.seed}:${context.pathKey}:${context.propertyName}:${context.caseKind}`);
    const fixture = fixtureReference(context.pathKey, context.method, context.propertyName, resolved, seed);
    if (fixture) {
      return fixture;
    }

    if (Array.isArray(resolved.enum) && resolved.enum.length > 0) {
      return resolved.enum[0];
    }

    const minLength = Math.max(resolved.minLength || 1, 1);
    const maxLength = resolved.maxLength || Math.max(minLength, 32);
    const targetLength = Math.min(Math.max(minLength, 8), maxLength);
    const lowerProperty = String(context.propertyName || '').toLowerCase();

    if (resolved.format === 'email') {
      return `qa.${sanitizeSlug(context.pathKey)}.${seed.slice(0, 10)}@samt.local`;
    }

    if (resolved.format === 'uuid') {
      return '00000000-0000-0000-0000-000000000001';
    }

    if (resolved.format === 'date') {
      return '2026-01-15';
    }

    if (resolved.format === 'date-time') {
      return '2026-01-15T00:00:00Z';
    }

    if (lowerProperty.includes('password')) {
      return 'Str0ng@Pass!';
    }

    if (lowerProperty === 'groupname') {
      return `SE${deterministicNumber(seed, 1000, 9999)}-G${deterministicNumber(seed.slice(8), 1, 9)}`;
    }

    if (lowerProperty === 'semestercode') {
      return `QA-${deterministicNumber(seed, 2026, 2099)}-S${deterministicNumber(seed.slice(8), 1, 3)}`;
    }

    if (lowerProperty === 'semestername') {
      return `QA Semester ${deterministicNumber(seed, 1, 999)}`;
    }

    if (lowerProperty === 'fullname') {
      return `QA ${sanitizeSlug(context.pathKey).replace(/-/g, ' ')}`.slice(0, Math.max(minLength, 10));
    }

    if (lowerProperty === 'jirahosturl') {
      return 'https://example.atlassian.net';
    }

    if (lowerProperty === 'jiraapitoken') {
      return `ATATT${'A'.repeat(95)}`;
    }

    if (lowerProperty === 'githubrepourl') {
      return 'https://github.com/example-org/example-repo';
    }

    if (lowerProperty === 'githubtoken') {
      return `ghp_${'A'.repeat(36)}`;
    }

    if (lowerProperty === 'reason') {
      return 'Automated QA lock verification';
    }

    if (resolved.pattern === '^[a-zA-Z0-9]{20,30}$') {
      return deterministicToken('QAJIRA', Math.max(20, minLength), seed);
    }

    if (resolved.pattern === '^[a-zA-Z0-9-]{1,39}$') {
      return `qaadmin${seed.slice(0, 10)}`.slice(0, Math.min(39, maxLength));
    }

    if (resolved.pattern?.includes('https://github\\.com')) {
      return 'https://github.com/example-org/example-repo';
    }

    if (resolved.pattern?.includes('(atlassian\\.net|jira\\.com)')) {
      return 'https://example.atlassian.net';
    }

    if (resolved.pattern?.startsWith('^ATATT')) {
      return `ATATT${'A'.repeat(Math.max(95, minLength - 5))}`;
    }

    if (resolved.pattern?.startsWith('^ghp_')) {
      return `ghp_${'A'.repeat(Math.max(36, minLength - 4))}`;
    }

    return `${sanitizeSlug(lowerProperty || 'value')}-${seed}`.slice(0, targetLength).padEnd(targetLength, 'a');
  }

  function generateNumber(schema, context) {
    const resolved = resolveSchema(schema) || {};
    const seed = stableHash(`${context.seed}:${context.pathKey}:${context.propertyName}:${context.caseKind}`);
    const fixture = fixtureReference(context.pathKey, context.method, context.propertyName, resolved, seed);
    if (fixture) {
      return fixture;
    }

    const minimum = resolved.minimum ?? resolved.exclusiveMinimum ?? 1;
    const maximum = resolved.maximum ?? minimum + 100;
    return deterministicNumber(seed, minimum, maximum);
  }

  function generateBoolean() {
    return true;
  }

  function generateValue(schema, context) {
    const resolved = resolveSchema(schema) || {};
    const type = resolved.type;

    if (Array.isArray(type)) {
      return generateValue({ ...resolved, type: type.find((entry) => entry !== 'null') || 'string' }, context);
    }

    if (!type && resolved.properties) {
      return generateObject(resolved, context);
    }

    switch (type) {
      case 'object':
        return generateObject(resolved, context);
      case 'array':
        return generateArray(resolved, context);
      case 'integer':
      case 'number':
        return generateNumber(resolved, context);
      case 'boolean':
        return generateBoolean();
      case 'string':
      default:
        return generateString(resolved, context);
    }
  }

  function generateArray(schema, context) {
    const resolved = resolveSchema(schema) || {};
    const minItems = Math.max(resolved.minItems || 1, 1);
    const itemSchema = resolveSchema(resolved.items || { type: 'string' });
    return Array.from({ length: minItems }, (_, index) => generateValue(itemSchema, {
      ...context,
      propertyName: `${context.propertyName || 'item'}_${index}`
    }));
  }

  function generateObject(schema, context) {
    const resolved = resolveSchema(schema) || {};
    const out = {};
    const properties = resolved.properties || {};

    for (const [propertyName, propertySchema] of Object.entries(properties)) {
      if (!context.includeOptional && isOptionalProperty(resolved, propertyName)) {
        continue;
      }
      out[propertyName] = generateValue(propertySchema, {
        ...context,
        propertyName
      });
    }

    return out;
  }

  function invalidateValue(schema, context) {
    const resolved = resolveSchema(schema) || {};
    const type = resolved.type;
    const lowerProperty = String(context.propertyName || '').toLowerCase();

    if (Array.isArray(resolved.enum) && resolved.enum.length > 0) {
      return '__INVALID_ENUM__';
    }

    if (type === 'integer' || type === 'number') {
      if (resolved.minimum !== undefined) {
        return resolved.minimum - 1;
      }
      return 'not-a-number';
    }

    if (type === 'boolean') {
      return 'not-a-boolean';
    }

    if (type === 'array') {
      return 'not-an-array';
    }

    if (type === 'object') {
      return { invalid: true };
    }

    if (resolved.format === 'email') {
      return 'not-an-email';
    }

    if (resolved.format === 'uuid') {
      return 'not-a-uuid';
    }

    if (resolved.format === 'date') {
      return 'invalid-date';
    }

    if (resolved.format === 'date-time') {
      return 'invalid-date-time';
    }

    if (lowerProperty === 'githubusername') {
      return 'bad!';
    }

    if (resolved.pattern === '^[a-zA-Z0-9]{20,30}$') {
      return 'short';
    }

    if (resolved.pattern?.startsWith('^ATATT')) {
      return 'bad-token';
    }

    if (resolved.pattern?.startsWith('^ghp_')) {
      return 'short';
    }

    return '';
  }

  function generateHappyPayload(pathKey, method, operation, options = {}) {
    const schema = getRequestSchema(operation);
    if (!schema) {
      return {};
    }

    return generateValue(schema, {
      seed: options.seed || operationKey(method, pathKey),
      pathKey,
      method,
      caseKind: 'happy',
      propertyName: '',
      includeOptional: options.includeOptional ?? true
    });
  }

  function generateInvalidPayload(pathKey, method, operation, options = {}) {
    const schema = getRequestSchema(operation);
    if (!schema || !schema.properties) {
      return { invalid: 'payload' };
    }

    const out = generateHappyPayload(pathKey, method, operation, options);
    const firstProperty = Object.keys(schema.properties)[0];
    if (!firstProperty) {
      return { invalid: 'payload' };
    }
    out[firstProperty] = invalidateValue(schema.properties[firstProperty], {
      seed: options.seed || operationKey(method, pathKey),
      pathKey,
      method,
      caseKind: 'invalid',
      propertyName: firstProperty
    });
    return out;
  }

  return {
    getRequestSchema,
    generateHappyPayload,
    generateInvalidPayload
  };
}