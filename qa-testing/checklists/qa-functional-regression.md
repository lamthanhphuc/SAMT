# QA Functional + Regression Checklist

## Functional Testing

- [ ] Login with valid credentials routes user by role (ADMIN/LECTURER/STUDENT).
- [ ] Invalid login shows user-friendly error message.
- [ ] Protected routes redirect unauthorized users.
- [ ] Admin can access user management flows.
- [ ] Lecturer can access group/tasks/github/grading flows.
- [ ] Student can access profile/stats/permissions flows.
- [ ] Group listing and group details load correctly.
- [ ] Project config page loads and saves expected values.

## UI Interaction

- [ ] Required fields block form submission.
- [ ] Loading states render during async operations.
- [ ] Error states are visible and recoverable.
- [ ] Navigation menu links route correctly.
- [ ] Tables support expected sorting/filter/search behavior.

## API Correctness

- [ ] Request payloads follow OpenAPI required fields.
- [ ] Response payloads match schema contracts.
- [ ] Status codes are correct for success and failures.
- [ ] Validation errors are consistent in shape and message.

## Regression Critical Flows

- [ ] Authentication lifecycle (login/refresh/logout).
- [ ] Profile retrieval and update.
- [ ] Admin semester and user management flows.
- [ ] Report generation and report download flow.
- [ ] Any previously fixed production bug scenario.

## Edge Cases

- [ ] Empty values for required fields.
- [ ] Very large payloads are rejected gracefully.
- [ ] Invalid formats (email/uuid/date) return 400.
- [ ] Concurrent requests do not corrupt data.
