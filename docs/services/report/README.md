# Report Service

## Responsibilities

- Generate SRS-oriented reports for a project configuration.
- Persist generated report artifacts and supporting issue snapshots.
- Orchestrate calls to Sync Service and Analysis Service during report generation.

## APIs

- Report API: `POST /reports/srs`.

## Database

- `reports`
- `jira_issues`

## Events

- None.

## Dependencies

- PostgreSQL for report persistence.
- Sync Service gRPC for issue retrieval.
- Analysis Service internal HTTP API at `/internal/ai/generate-srs`.