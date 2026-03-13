import { test, expect } from "@playwright/test";

const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:9080";

test.describe("API Security Negative Tests", () => {
  test("authentication bypass attempt should fail", async ({ request }) => {
    const response = await request.get(`${apiBaseUrl}/profile`);
    expect([401, 403]).toContain(response.status());
  });

  test("authorization role check should block non-admin", async ({ request }) => {
    const response = await request.put(`${apiBaseUrl}/api/admin/users/1/external-accounts`, {
      headers: {
        Authorization: "Bearer invalid-or-student-token",
      },
      data: {
        jiraAccountId: "abc",
        githubUsername: "student-user",
      },
    });

    expect([401, 403]).toContain(response.status());
  });

  test("SQL injection payload should not trigger 5xx", async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/api/auth/login`, {
      data: {
        email: "' OR 1=1 --",
        password: "' OR 1=1 --",
      },
    });

    expect(response.status()).toBeLessThan(500);
  });

  test("XSS payload should be rejected or sanitized", async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/api/auth/register`, {
      data: {
        email: "xss-user@example.com",
        password: "Password@123",
        confirmPassword: "Password@123",
        fullName: "<script>alert(1)</script>",
        role: "STUDENT",
      },
    });

    expect([201, 400, 409]).toContain(response.status());
    const text = await response.text();
    expect(text).not.toContain("<script>alert(1)</script>");
  });

  test("burst requests should respect rate-limit/protection", async ({ request }) => {
    const attempts = 25;
    const responses = await Promise.all(
      Array.from({ length: attempts }).map(() =>
        request.post(`${apiBaseUrl}/api/auth/login`, {
          data: {
            email: "burst-user@example.com",
            password: "wrong-password",
          },
        })
      )
    );

    const statuses = responses.map((r) => r.status());
    expect(statuses.some((s) => [429, 401, 403].includes(s))).toBeTruthy();
  });

  test("error responses must not leak secrets", async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/api/auth/login`, {
      data: {
        email: "",
        password: "",
      },
    });

    const text = await response.text();
    expect(text.toLowerCase()).not.toContain("password=");
    expect(text.toLowerCase()).not.toContain("secret");
    expect(text.toLowerCase()).not.toContain("access_token");
  });
});
