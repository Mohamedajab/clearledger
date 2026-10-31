import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  testMatch: "demo.spec.ts",
  use: { baseURL: process.env.DEMO_BASE_URL ?? "http://127.0.0.1:4173" },
  webServer: process.env.DEMO_BASE_URL ? undefined : {
    command: "npm run build:demo && npm exec vite preview -- --host 127.0.0.1 --port 4173",
    url: "http://127.0.0.1:4173",
    timeout: 60_000,
  },
});
