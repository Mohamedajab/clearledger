import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 60_000,
  workers: 1,
  use: { baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3000", channel: process.env.BROWSER_CHANNEL, launchOptions: { executablePath: process.env.BROWSER_EXECUTABLE }, trace: "retain-on-failure", screenshot: "only-on-failure" },
});
