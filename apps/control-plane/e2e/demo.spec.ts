import { test, expect } from "@playwright/test";

test("public demo works without authentication or payment API requests", async ({ page }) => {
  const backendRequests: string[] = [];
  const errors: string[] = [];
  page.on("request", request => {
    if (/^\/(api|auth)(\/|$)/.test(new URL(request.url()).pathname)) backendRequests.push(request.url());
  });
  page.on("pageerror", error => errors.push(error.message));
  await page.goto("/payments");
  await expect(page.getByText("Interactive browser demo with sample data. No server payments are made.")).toBeVisible();
  await page.getByRole("button", { name: "Create payment" }).click();
  await expect(page.locator(".overlay")).toHaveCSS("position", "fixed");
  await expect(page.getByRole("dialog")).toBeFocused();
  await page.keyboard.press("Shift+Tab");
  await expect(page.getByRole("button", { name: "Post payment" })).toBeFocused();
  await page.getByLabel("Reference", { exact: true }).fill("PUBLIC-DEMO-CHECK");
  await page.getByLabel("Amount (GBP)").fill("12.50");
  await page.getByRole("button", { name: "Post payment" }).click();
  await expect(page.getByRole("row", { name: /PUBLIC-DEMO-CHECK/ })).toBeVisible();
  await page.getByRole("link", { name: "Ledger", exact: true }).click();
  await expect(page.locator(".entry")).toHaveCount(2);
  await page.reload();
  await expect(page.locator(".entry")).toHaveCount(1);
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  expect(backendRequests).toEqual([]);
  expect(errors).toEqual([]);
});

test("mobile payment form and records remain usable by keyboard", async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto("/payments");
  const create = page.getByRole("button", { name: "Create payment" });
  await expect(create).toBeVisible();
  await create.focus();
  await page.keyboard.press("Enter");
  await expect(page.getByLabel("Reference", { exact: true })).toBeVisible();
  await expect(page.getByLabel("From account")).toBeVisible();
  await expect(page.getByLabel("To account")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).not.toBeVisible();
  await expect(create).toBeFocused();
  await expect(page.getByRole("columnheader", { name: "From / to" })).toBeAttached();
  await page.getByRole("button", { name: "Open navigation" }).click();
  await page.getByRole("link", { name: "Overview", exact: true }).click();
  await expect(page.getByRole("heading", { name: "Journal check" })).toBeVisible();
  await expect(page.getByText("1 entry, 2 postings.")).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
