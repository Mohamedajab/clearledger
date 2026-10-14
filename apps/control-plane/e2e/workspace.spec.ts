import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";

async function login(page: Page, username: string) {
  await page.goto("/");
  await page.getByRole("button", { name: "Sign in", exact: true }).click();
  await page.getByLabel("Username or email").fill(username);
  await page.getByLabel("Password", { exact: true }).fill("Local-demo-only-2026!");
  await page.getByRole("button", { name: "Sign In", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Overview", exact: true })).toBeVisible();
}

test("operator posts a real payment, finds it in the journal and exports it", async ({ page }) => {
  await login(page, "operator");
  await page.getByRole("link", { name: "Payments", exact: true }).click();
  await page.getByRole("button", { name: "Create payment" }).click();
  const reference = "BROWSER-" + Date.now();
  await page.getByLabel("Reference", { exact: true }).fill(reference);
  await page.getByLabel("Amount (GBP)").fill("12.50");
  const created = page.waitForResponse(response => response.url().endsWith("/api/v1/payments") && response.request().method() === "POST");
  await page.getByRole("button", { name: "Post payment" }).click();
  const payment = await (await created).json() as { id: string };
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(page.getByRole("row", { name: new RegExp(reference) })).toBeVisible();
  await page.getByRole("link", { name: "Accounts", exact: true }).click();
  await page.getByRole("textbox", { name: "Search records" }).fill("Acme");
  await expect(page.getByRole("heading", { name: "Acme marketplace" })).toBeVisible();
  await expect(page.getByRole("heading", { name: "Operating wallet" })).not.toBeVisible();
  await page.getByRole("button", { name: "Clear search" }).click();
  await page.getByRole("link", { name: "Ledger", exact: true }).click();
  await page.getByRole("textbox", { name: "Search records" }).fill("PAY-" + payment.id);
  await expect(page.locator(".entry")).toHaveCount(1);
  await expect(page.locator(".entry em")).toHaveText("posted");
  const download = page.waitForEvent("download");
  await page.getByRole("button", { name: "Export journal" }).click();
  expect((await download).suggestedFilename()).toBe("clearledger-journal.csv");
  await page.getByRole("button", { name: "Clear search" }).click();
  await page.screenshot({ path: "test-results/operator-desktop.png", fullPage: true });
  await page.emulateMedia({ reducedMotion: "reduce" });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.screenshot({ path: "test-results/operator-mobile.png", fullPage: true });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByRole("button", { name: "Sign in", exact: true })).toBeVisible();
});

test("reader cannot access payment write controls", async ({ page }) => {
  await login(page, "reader");
  await page.getByRole("link", { name: "Payments", exact: true }).click();
  await expect(page.getByText(/You have read-only access/)).toBeVisible();
  await expect(page.getByRole("button", { name: "Create payment" })).not.toBeVisible();
});

test("another organisation cannot see Northstar accounts or payments", async ({ page }) => {
  await login(page, "other-tenant");
  await page.getByRole("link", { name: "Accounts", exact: true }).click();
  await expect(page.getByText("No accounts are available.")).toBeVisible();
  await expect(page.getByRole("heading", { name: "Operating wallet" })).not.toBeVisible();
  await page.getByRole("link", { name: "Payments", exact: true }).click();
  await expect(page.getByText("No payments yet.")).toBeVisible();
});
