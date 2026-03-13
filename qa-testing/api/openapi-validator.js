import fs from "node:fs";
import path from "node:path";
import yaml from "js-yaml";
import Ajv from "ajv";

const OPENAPI_PATH = path.resolve(process.cwd(), "../openapi.yaml");

function readSpec() {
  const raw = fs.readFileSync(OPENAPI_PATH, "utf8");
  return yaml.load(raw);
}

function resolveRef(spec, ref) {
  if (!ref?.startsWith("#/")) return null;
  return ref
    .replace("#/", "")
    .split("/")
    .reduce((acc, key) => (acc ? acc[key] : undefined), spec);
}

function resolveSchema(spec, schemaNode) {
  if (!schemaNode) return null;
  if (schemaNode.$ref) {
    return resolveSchema(spec, resolveRef(spec, schemaNode.$ref));
  }
  if (schemaNode.allOf) {
    return {
      allOf: schemaNode.allOf.map((part) => resolveSchema(spec, part)),
    };
  }
  if (schemaNode.oneOf) {
    return {
      oneOf: schemaNode.oneOf.map((part) => resolveSchema(spec, part)),
    };
  }
  if (schemaNode.anyOf) {
    return {
      anyOf: schemaNode.anyOf.map((part) => resolveSchema(spec, part)),
    };
  }
  if (schemaNode.type === "array" && schemaNode.items) {
    return { ...schemaNode, items: resolveSchema(spec, schemaNode.items) };
  }
  if (schemaNode.properties) {
    const resolvedProps = Object.fromEntries(
      Object.entries(schemaNode.properties).map(([key, value]) => [
        key,
        resolveSchema(spec, value),
      ])
    );
    return { ...schemaNode, properties: resolvedProps };
  }
  return schemaNode;
}

export function getOperationSchema({ pathName, method, status }) {
  const spec = readSpec();
  const operation = spec?.paths?.[pathName]?.[method.toLowerCase()];
  if (!operation) {
    throw new Error(`OpenAPI operation not found: ${method.toUpperCase()} ${pathName}`);
  }

  const responseNode = operation.responses?.[String(status)] ?? operation.responses?.default;
  if (!responseNode) return null;

  const resolvedResponse = responseNode.$ref ? resolveRef(spec, responseNode.$ref) : responseNode;
  const content = resolvedResponse?.content;
  if (!content) return null;

  const preferredMedia =
    content["application/json"] ||
    content["*/*"] ||
    Object.values(content)[0];

  const schemaNode = preferredMedia?.schema;
  return resolveSchema(spec, schemaNode);
}

export function validateJsonAgainstSchema(payload, schema) {
  if (!schema) {
    return { valid: true, errors: [] };
  }

  const ajv = new Ajv({ allErrors: true, strict: false });
  const validate = ajv.compile(schema);
  const valid = validate(payload);
  return {
    valid,
    errors: validate.errors ?? [],
  };
}
