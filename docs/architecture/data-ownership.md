# Data Ownership

## Ownership Matrix

| Domain | Owner | Access by Other Services | Notes |
| --- | --- | --- | --- |
| Users, credentials, refresh tokens | Identity | gRPC or gateway-authenticated REST only | No direct DB access from other services |
| Audit logs | Identity | Read through identity APIs only | Security-sensitive history |
| Semesters, groups, memberships | User Group | gRPC or REST through gateway | Logical user references point to Identity |
| Project integration configs | Project Config | Internal service access only | Sensitive tokens remain encrypted at rest |
| Sync jobs and normalized source data | Sync | Read by analysis and reporting flows | Config input comes from Project Config |
| Analysis outputs | Analysis | Internal consumers | Detailed contract still needs expansion |
| Reports | Report | Internal or gateway-mediated consumers | Detailed contract still needs expansion |
| Notification records | Notification | Internal consumers | Detailed contract still needs expansion |

## Cross-Service Rules

- If service A needs data owned by service B, service A must call service B through a supported API.
- Foreign keys must not cross service-owned databases.
- Derived or cached copies must be treated as secondary views, not source-of-truth records.

## Soft Delete Policy

- Soft delete is the default deletion model for core business entities.
- Retention and cleanup rules are defined in [../contracts/soft-delete-policy.md](../contracts/soft-delete-policy.md).
- Historical documentation mentions hard-delete flows that are not uniformly implemented; treat the contract document as policy and service-level docs as implementation detail.

## Security-Sensitive Data

- JWT signing material is owned by Identity and API Gateway.
- Gateway-issued internal JWTs are the trust boundary for downstream services.
- Jira and GitHub credentials are stored only by Project Config and must remain encrypted at rest.