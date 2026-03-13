import { test, expect } from "@playwright/test";

const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:9080";

test.describe("E2E Auth Flow", () => {
  test("login form renders and validates required fields", async ({ page }) => {
    await page.goto("/login");

    await expect(page.getByRole("heading", { name: /sign in to samt/i })).toBeVisible();

    await page.getByRole("button", { name: /đăng nhập|sign in/i }).click();

    await expect(page.locator("input[type='email']:invalid")).toHaveCount(1);
    await expect(page.locator("input[type='password']:invalid")).toHaveCount(1);
  });

  test("login sends request to backend API endpoint", async ({ page }) => {
    await page.goto("/login");

    const loginRequestPromise = page.waitForRequest((request) =>
      request.url().includes("/api/auth/login") && request.method() === "POST"
    );

    await page.fill("input[type='email']", process.env.E2E_LOGIN_EMAIL ?? "student@example.com");
    await page.fill("input[type='password']", process.env.E2E_LOGIN_PASSWORD ?? "Password@123");
    await page.getByRole("button", { name: /đăng nhập|sign in/i }).click();

    const loginRequest = await loginRequestPromise;
    expect(loginRequest.url()).toBe(`${apiBaseUrl}/api/auth/login`);

    const body = loginRequest.postDataJSON();
    expect(body).toMatchObject({
      email: expect.any(String),
      password: expect.any(String),
    });
  });
});
