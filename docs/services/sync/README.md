# Sync Service

## Responsibilities

- Run manual and scheduled synchronization jobs for Jira and GitHub data.
- Persist sync execution status and normalized activity records.
- Maintain raw ingestion tables used during synchronization.
- Expose synchronized issue data for downstream reporting.

## APIs

- Manual trigger API: `/api/sync/jira/issues`, `/api/sync/github/commits`, `/api/sync/all`.
- gRPC endpoint: `SyncGrpcService.getIssuesByProjectConfig`.
- Scheduled jobs in `SyncScheduler` for Jira and GitHub execution windows.

## Database

- `sync_jobs`
- `unified_activities`
- `jira_issues`
- `github_commits`
- `github_pull_requests`

## Events

- None.

## Dependencies

- PostgreSQL for sync state and normalized activity persistence.
- Jira and GitHub external APIs.
- Project Config Service for integration configuration lookup.
- ShedLock and scheduler infrastructure for coordinated background execution.
