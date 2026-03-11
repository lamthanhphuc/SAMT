# Analysis Service

## Responsibilities

- Provide internal AI-backed analysis for requirement processing and SRS generation.
- Enforce internal role checks before running analysis operations.

## APIs

- Internal API: `POST /internal/ai/generate-srs`.

## Database

- `analysis_schema_marker` bootstrap table is present.
- No additional analysis domain tables are implemented in the current codebase.

## Events

- None.

## Dependencies

- Internal JWT validation against the API Gateway internal JWKS.
- AI provider integration behind `AiService`.
- Report Service is the current downstream caller for SRS generation.