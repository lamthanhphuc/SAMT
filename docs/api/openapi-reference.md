# OpenAPI Reference

## Source Of Truth

- Public HTTP contract: [../../openapi.yaml](../../openapi.yaml)
- HTTP smoke and regression examples: [../../api-smoke.http](../../api-smoke.http) and [../../api-regression.http](../../api-regression.http)

## Usage

- Treat `openapi.yaml` as the canonical public REST contract.
- Service-specific markdown contracts provide implementation detail and operational notes that may not appear in the OpenAPI file.
- When a markdown contract conflicts with `openapi.yaml`, reconcile the conflict and update one authoritative source instead of maintaining two incompatible definitions.