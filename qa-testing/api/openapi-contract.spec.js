import { test, expect } from "@playwright/test";
import { getOperationSchema, validateJsonAgainstSchema } from "./openapi-validator.js";

const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:9080";

async function assertContract(response, { pathName, method, expectedStatuses }) {
  expect(expectedStatuses).toContain(response.status());

  const contentType = response.headers()["content-type"] ?? "";
  if (!contentType.includes("application/json")) {
    return;
  }

  const schema = getOperationSchema({
    pathName,
    method,
    status: response.status(),
  });

  const payload = await response.json();
  const result = validateJsonAgainstSchema(payload, schema);
  expect(result.valid, JSON.stringify(result.errors, null, 2)).toBe(true);
}

test.describe("API OpenAPI Contract", () => {
  test("POST /api/auth/login - valid request returns contract-compliant response", async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/api/auth/login`, {
      data: {
        email: process.env.E2E_LOGIN_EMAIL ?? "student@example.com",
        password: process.env.E2E_LOGIN_PASSWORD ?? "Password@123",
      },
    });

    await assertContract(response, {
      pathName: "/api/auth/login",
      method: "post",
      expectedStatuses: [200, 401, 403],
    });
  });

  test("POST /api/auth/login - invalid payload returns validation error contract", async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/api/auth/login`, {
      data: {
        email: "not-an-email",
        password: "",
      },
    });

    await assertContract(response, {
      pathName: "/api/auth/login",
      method: "post",
      expectedStatuses: [400],
    });
  });

  test("POST /api/auth/register - required field validation", async ({ request }) => {
    const response = await request.post(`${apiBaseUrl}/api/auth/register`, {
      data: {
        email: "",
        password: "short",
      },
    });

    await assertContract(response, {
      pathName: "/api/auth/register",
      method: "post",
      expectedStatuses: [400],
    });
  });
});
