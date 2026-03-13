# External Integration Verification

- Generated at: 2026-03-13T04:33:57.383Z
- Status: passed
- Base URL: http://localhost:9080

## Environment

- Jira host: https://fpt-team-f2dn8ko2.atlassian.net
- Jira email: phucltse184678@fpt.edu.vn
- Jira token: ATAT****************************************************************************************************************************************************************************************B151
- GitHub repo: https://github.com/lamthanhphuc/SAMT
- GitHub token: gith*************************************************************************************ypEc

## Steps

- admin-login: 200
- create-lecturer: 201
- create-semester: 201
- activate-semester: 204
- create-group: 201
- create-project-config: 201
- verify-project-config: 200

## Verification

```json
{
  "configId": "192ac087-21ae-4edf-a0ee-3fb9cf6d999a",
  "state": "VERIFIED",
  "verificationResults": {
    "jira": {
      "status": "SUCCESS",
      "message": "Connected successfully to Jira",
      "error": null,
      "testedAt": "2026-03-13T04:33:57Z",
      "userEmail": "phucltse184678@fpt.edu.vn"
    },
    "github": {
      "status": "SUCCESS",
      "message": "Connected successfully to GitHub",
      "error": null,
      "testedAt": "2026-03-13T04:33:57Z",
      "repoName": "lamthanhphuc/SAMT",
      "hasWriteAccess": true
    }
  },
  "lastVerifiedAt": "2026-03-13T04:33:59Z",
  "invalidReason": null
}
```

