import { defineConfig } from "@playwright/test";

const frontendPort = process.env.FE_PORT ?? "5173";

export default defineConfig({
  testDir: ".",
  timeout: 45_000,
  expect: {
    timeout: 10_000,
  },
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    headless: true,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    baseURL: process.env.FE_BASE_URL ?? `http://localhost:${frontendPort}`,
  },
  projects: [
    {
      name: "chromium",
      use: { browserName: "chromium" },
    },
  ],
  webServer: process.env.PW_NO_WEBSERVER
    ? undefined
    : {
        command: "npm --prefix ../../FE-SAMT run dev -- --host --port 5173",
        url: "http://localhost:5173",
        reuseExistingServer: true,
        timeout: 120_000,
      },
});
