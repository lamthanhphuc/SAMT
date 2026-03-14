import fs from 'node:fs/promises';
import path from 'node:path';
import yaml from 'js-yaml';
import { parseArgs, toPosix, writeJson, writeText } from './qa-helpers.mjs';

const options = parseArgs(process.argv.slice(2), {
  workspace: process.cwd(),
  output: 'qa/endpoints.json'
});

const workspacePath = path.resolve(options.workspace);
const outputPath = path.resolve(workspacePath, options.output);
const markdownPath = outputPath.replace(/\.json$/i, '.md');

const serviceRoots = [
  'identity-service',
  'user-group-service',
  'project-config-service',
  'sync-service',
  'analysis-service',
  'report-service',
  'notification-service',
  'api-gateway'
];

function cleanNormalizedPath(value) {
  const normalized = value.replace(/\/+/g, '/').replace(/\/\//g, '/');
  if (normalized === '/') {
    return normalized;
  }

  return normalized.replace(/\/$/, '') || '/';
}

function joinPath(basePath, subPath) {
  return cleanNormalizedPath(`${basePath || ''}/${subPath || ''}`);
}

async function walkControllers(rootDir) {
  const controllers = [];

  async function visit(currentDir) {
    const entries = await fs.readdir(currentDir, { withFileTypes: true });
    for (const entry of entries) {
      if (entry.name === 'target' || entry.name === 'test' || entry.name === '.git') {
        continue;
      }

      const fullPath = path.join(currentDir, entry.name);
      if (entry.isDirectory()) {
        await visit(fullPath);
        continue;
      }

      if (entry.isFile() && entry.name.endsWith('Controller.java')) {
        controllers.push(fullPath);
      }
    }
  }

  await visit(rootDir);
  return controllers;
}

function parseMappingArguments(rawArgs) {
  if (!rawArgs) {
    return [''];
  }

  const matches = [...rawArgs.matchAll(/"([^"]*)"/g)].map((match) => match[1]);
  if (matches.length > 0) {
    return matches;
  }

  return [''];
}

function parseControllerEndpoints(filePath, source) {
  const service = toPosix(path.relative(workspacePath, filePath)).split('/')[0];
  const lines = source.split(/\r?\n/);
  const endpoints = [];
  let classDeclared = false;
  let classBasePath = '';
  let pendingClassMapping = null;
  let pendingMethodMapping = null;
  let pendingSummary = null;
  let pendingRoles = null;

  for (const line of lines) {
    const trimmed = line.trim();

    const operationMatch = trimmed.match(/^@Operation\(summary\s*=\s*"([^"]+)"/);
    if (operationMatch) {
      pendingSummary = operationMatch[1];
    }

    const preAuthorizeMatch = trimmed.match(/^@PreAuthorize\("([^"]+)"\)/);
    if (preAuthorizeMatch) {
      pendingRoles = preAuthorizeMatch[1];
    }

    const requestMappingMatch = trimmed.match(/^@RequestMapping(?:\((.*)\))?/);
    if (requestMappingMatch) {
      const mappings = parseMappingArguments(requestMappingMatch[1]);
      if (!classDeclared) {
        pendingClassMapping = mappings[0] || '';
      } else {
        pendingMethodMapping = {
          method: 'ANY',
          paths: mappings
        };
      }
    }

    const methodMappingMatch = trimmed.match(/^@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)(?:\((.*)\))?/);
    if (methodMappingMatch) {
      pendingMethodMapping = {
        method: methodMappingMatch[1].replace('Mapping', '').toUpperCase(),
        paths: parseMappingArguments(methodMappingMatch[2])
      };
    }

    if (!classDeclared && /class\s+\w+/.test(trimmed)) {
      classDeclared = true;
      classBasePath = pendingClassMapping || '';
      pendingClassMapping = null;
      continue;
    }

    if (pendingMethodMapping && /(public|private|protected)\s+/.test(trimmed) && /\(/.test(trimmed)) {
      const methodNameMatch = trimmed.match(/\s(\w+)\s*\(/);
      const methodName = methodNameMatch?.[1] || 'unknown';

      for (const subPath of pendingMethodMapping.paths) {
        endpoints.push({
          method: pendingMethodMapping.method,
          path: joinPath(classBasePath, subPath),
          service,
          controllerFile: toPosix(path.relative(workspacePath, filePath)),
          handler: methodName,
          summary: pendingSummary,
          rolesExpression: pendingRoles
        });
      }

      pendingMethodMapping = null;
      pendingSummary = null;
      pendingRoles = null;
    }
  }

  return endpoints;
}

function parseGatewayRoutes(source) {
  const routes = [];
  const routeRegex = /\.route\("([^"]+)",\s*r\s*->\s*r([\s\S]*?)\.uri\(([^)]+)\)\)/g;

  for (const match of source.matchAll(routeRegex)) {
    const id = match[1];
    const body = match[2];
    const uriTarget = match[3].trim();
    const pathMatch = body.match(/\.path\(([^)]+)\)/);
    const pathPatterns = pathMatch ? [...pathMatch[1].matchAll(/"([^"]+)"/g)].map((item) => item[1]) : [];
    const stripPrefixMatch = body.match(/\.stripPrefix\((\d+)\)/);

    routes.push({
      id,
      pathPatterns,
      uriTarget,
      stripPrefix: stripPrefixMatch ? Number(stripPrefixMatch[1]) : 0
    });
  }

  return routes;
}

function parsePublicPaths(source) {
  const exact = new Set();
  const prefixes = new Set();

  for (const match of source.matchAll(/"([^"]+)"\.equals\(path\)/g)) {
    exact.add(match[1]);
  }

  for (const match of source.matchAll(/path\.startsWith\("([^"]+)"\)/g)) {
    prefixes.add(match[1]);
  }

  for (const match of source.matchAll(/"([^"]*\*\*?)"/g)) {
    prefixes.add(match[1].replace(/\*\*?$/, ''));
  }

  return {
    exact: [...exact],
    prefixes: [...prefixes].filter(Boolean)
  };
}

function isPublicPath(pathKey, publicPaths) {
  if (publicPaths.exact.includes(pathKey)) {
    return true;
  }

  return publicPaths.prefixes.some((prefix) => pathKey.startsWith(prefix));
}

function routeForPath(pathKey, routes) {
  return routes.find((route) => route.pathPatterns.some((pattern) => {
    const prefix = pattern.replace('/**', '');
    return pathKey === prefix || pathKey.startsWith(`${prefix}/`);
  })) || null;
}

function routeServiceName(route) {
  if (!route) {
    return null;
  }

  const routeId = route.id.toLowerCase();
  if (routeId.includes('identity')) {
    return 'identity-service';
  }
  if (routeId.includes('user-group')) {
    return 'user-group-service';
  }
  if (routeId.includes('project-config')) {
    return 'project-config-service';
  }
  if (routeId.includes('sync')) {
    return 'sync-service';
  }
  if (routeId.includes('analysis')) {
    return 'analysis-service';
  }
  if (routeId.includes('report')) {
    return 'report-service';
  }
  if (routeId.includes('notification')) {
    return 'notification-service';
  }

  return route.uriTarget?.replace('ServiceUri', '').replace(/"/g, '') || null;
}

function pathToTestFile(pathKey) {
  if (pathKey.startsWith('/api/auth') || pathKey.startsWith('/api/admin')) {
    return 'tests/api-auth.http';
  }
  if (pathKey.startsWith('/api/users') || pathKey.startsWith('/api/groups')) {
    return 'tests/api-groups.http';
  }
  if (pathKey.startsWith('/api/semesters')) {
    return 'tests/api-semesters.http';
  }
  if (pathKey.startsWith('/api/project-configs')) {
    return 'tests/api-project-configs.http';
  }
  if (pathKey.startsWith('/api/sync')) {
    return 'api-regression.http';
  }
  return null;
}

function endpointMarkdown(report) {
  const lines = [
    '# Endpoint Inventory',
    '',
    `- Generated at: ${report.generatedAt}`,
    `- Operations: ${report.summary.operations}`,
    `- Public endpoints: ${report.summary.publicEndpoints}`,
    `- Protected endpoints: ${report.summary.protectedEndpoints}`,
    ''
  ];

  lines.push('| Method | Path | Service | Auth | Test Coverage |');
  lines.push('| --- | --- | --- | --- | --- |');
  for (const endpoint of report.endpoints) {
    lines.push(`| ${endpoint.method} | ${endpoint.path} | ${endpoint.service || 'unknown'} | ${endpoint.requiresAuth ? 'protected' : 'public'} | ${endpoint.testCoverage.generatedSuite || endpoint.testCoverage.legacySuite || 'none'} |`);
  }
  lines.push('');

  return `${lines.join('\n')}\n`;
}

async function main() {
  const openapiDoc = yaml.load(await fs.readFile(path.join(workspacePath, 'docs/api/openapi.yaml'), 'utf8'));
  const gatewayConfigSource = await fs.readFile(path.join(workspacePath, 'api-gateway', 'src', 'main', 'java', 'com', 'example', 'gateway', 'config', 'GatewayRoutesConfig.java'), 'utf8');
  const publicPathsSource = await fs.readFile(path.join(workspacePath, 'api-gateway', 'src', 'main', 'java', 'com', 'example', 'gateway', 'security', 'PublicEndpointPaths.java'), 'utf8');

  const routes = parseGatewayRoutes(gatewayConfigSource);
  const publicPaths = parsePublicPaths(publicPathsSource);
  const controllerEndpoints = [];

  for (const root of serviceRoots) {
    const controllerRoot = path.join(workspacePath, root, 'src', 'main', 'java');
    try {
      const controllerFiles = await walkControllers(controllerRoot);
      for (const controllerFile of controllerFiles) {
        const source = await fs.readFile(controllerFile, 'utf8');
        controllerEndpoints.push(...parseControllerEndpoints(controllerFile, source));
      }
    } catch {
      // Skip absent service roots.
    }
  }

  const controllerMap = new Map();
  for (const endpoint of controllerEndpoints) {
    controllerMap.set(`${endpoint.method} ${endpoint.path}`, endpoint);
  }

  const endpoints = [];
  for (const [pathKey, pathItem] of Object.entries(openapiDoc.paths || {})) {
    for (const [method, operation] of Object.entries(pathItem)) {
      if (!['get', 'post', 'put', 'delete', 'patch'].includes(method)) {
        continue;
      }

      const controllerEndpoint = controllerMap.get(`${method.toUpperCase()} ${pathKey}`) || null;
      const route = routeForPath(pathKey, routes);
      const requiresAuth = !isPublicPath(pathKey, publicPaths) && !(operation.security === undefined && isPublicPath(pathKey, publicPaths));

      endpoints.push({
        method: method.toUpperCase(),
        path: pathKey,
        summary: operation.summary || controllerEndpoint?.summary || null,
        operationId: operation.operationId || null,
        tags: operation.tags || [],
        service: controllerEndpoint?.service || routeServiceName(route),
        gatewayRouteId: route?.id || null,
        gatewayPatterns: route?.pathPatterns || [],
        controllerFile: controllerEndpoint?.controllerFile || null,
        handler: controllerEndpoint?.handler || null,
        rolesExpression: controllerEndpoint?.rolesExpression || null,
        requestBodySchema: operation.requestBody?.content?.['application/json']?.schema?.$ref || null,
        responseCodes: Object.keys(operation.responses || {}),
        requiresAuth,
        testCoverage: {
          generatedSuite: pathToTestFile(pathKey),
          legacySuite: pathKey.startsWith('/api/') ? ['api-smoke.http', 'api-regression.http'] : []
        }
      });
    }
  }

  const serviceSummaries = Object.entries(
    endpoints.reduce((acc, endpoint) => {
      const key = endpoint.service || 'unknown';
      acc[key] = acc[key] || { operations: 0, publicEndpoints: 0, protectedEndpoints: 0 };
      acc[key].operations += 1;
      if (endpoint.requiresAuth) {
        acc[key].protectedEndpoints += 1;
      } else {
        acc[key].publicEndpoints += 1;
      }
      return acc;
    }, {})
  ).map(([service, summary]) => ({ service, ...summary }));

  const report = {
    generatedAt: new Date().toISOString(),
    baseUrl: openapiDoc.servers?.[0]?.url || 'http://localhost:9080',
    sources: {
      openapi: 'docs/api/openapi.yaml',
      gatewayRoutes: 'services/api-gateway/src/main/java/com/example/gateway/config/GatewayRoutesConfig.java',
      publicPaths: 'services/api-gateway/src/main/java/com/example/gateway/security/PublicEndpointPaths.java',
      controllersScanned: controllerEndpoints.length
    },
    summary: {
      operations: endpoints.length,
      publicEndpoints: endpoints.filter((endpoint) => !endpoint.requiresAuth).length,
      protectedEndpoints: endpoints.filter((endpoint) => endpoint.requiresAuth).length,
      services: serviceSummaries
    },
    gatewayRoutes: routes,
    publicPaths,
    endpoints: endpoints.sort((left, right) => `${left.path}:${left.method}`.localeCompare(`${right.path}:${right.method}`))
  };

  await writeJson(outputPath, report);
  await writeText(markdownPath, endpointMarkdown(report));
  process.stdout.write(`Wrote ${toPosix(path.relative(workspacePath, outputPath))}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message || String(error)}\n`);
  process.exit(1);
});



