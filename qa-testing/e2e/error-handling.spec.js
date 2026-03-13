import { test, expect } from "@playwright/test";

test.describe("UI Error Handling", () => {
  test("shows a user-friendly error when server returns 500", async ({ page }) => {
    await page.route("**/api/auth/login", async (route) => {
      await route.fulfill({
        status: 500,
        contentType: "application/json",
        body: JSON.stringify({ message: "internal-server-error" }),
      });
    });

    await page.goto("/login");
    await page.fill("input[type='email']", "student@example.com");
    await page.fill("input[type='password']", "Password@123");
    await page.getByRole("button", { name: /đăng nhập|sign in/i }).click();

    await expect(page.getByText(/đăng nhập thất bại/i)).toBeVisible();
  });

  test("shows error when network fails", async ({ page }) => {
    await page.route("**/api/auth/login", async (route) => {
      await route.abort("failed");
    });

    await page.goto("/login");
    await page.fill("input[type='email']", "student@example.com");
    await page.fill("input[type='password']", "Password@123");
    await page.getByRole("button", { name: /đăng nhập|sign in/i }).click();

    await expect(page.getByText(/đăng nhập thất bại/i)).toBeVisible();
  });

  test("timeout simulation keeps app responsive", async ({ page }) => {
    await page.route("**/api/auth/login", async (route) => {
      await new Promise((resolve) => setTimeout(resolve, 12_000));
      await route.fulfill({
        status: 504,
        contentType: "application/json",
        body: JSON.stringify({ message: "gateway-timeout" }),
      });
    });

    await page.goto("/login");
    await page.fill("input[type='email']", "student@example.com");
    await page.fill("input[type='password']", "Password@123");
    await page.getByRole("button", { name: /đăng nhập|sign in/i }).click();

    await expect(page.getByRole("button", { name: /đang đăng nhập/i })).toBeVisible();
    await expect(page.getByText(/đăng nhập thất bại/i)).toBeVisible();
  });
});
