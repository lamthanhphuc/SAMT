# External Integration Verification

- Generated at: 2026-03-12T03:40:13.694Z
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
  "configId": "b038a32f-3118-4fa0-acc4-177bc375ea48",
  "state": "VERIFIED",
  "verificationResults": {
    "jira": {
      "status": "SUCCESS",
      "message": "Connected successfully to Jira",
      "error": null,
      "testedAt": "2026-03-12T03:40:15Z",
      "userEmail": "phucltse184678@fpt.edu.vn"
    },
    "github": {
      "status": "SUCCESS",
      "message": "Connected successfully to GitHub",
      "error": null,
      "testedAt": "2026-03-12T03:40:15Z",
      "repoName": "lamthanhphuc/SAMT",
      "hasWriteAccess": true
    }
  },
  "lastVerifiedAt": "2026-03-12T03:40:16Z",
  "invalidReason": null
}
```

