import { test, expect } from "@playwright/test";
import type { Page } from "@playwright/test";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";
import type { Account, LedgerEntry } from "../src/api";

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


test("broker recovery preserves one payment, its journal and balances", async ({ page }, testInfo) => {
  test.setTimeout(120_000);
  expect(process.env.E2E_BASE_URL ?? "http://localhost:3000").toBe("http://localhost:3000");
  const root = fileURLToPath(new URL("../../../", import.meta.url));
  const compose = (...args: string[]) => execFileSync("docker", ["compose", ...args], { cwd: root, encoding: "utf8", timeout: 30_000 });
  const sql = (query: string) => compose("exec", "-T", "postgres", "sh", "-c",
    'exec psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -At -c "$1"', "sh", query).trim();
  let authorization = "";
  page.on("request", request => {
    if (request.url().startsWith("http://localhost:3000/api/")) {
      authorization = request.headers()["authorization"] ?? authorization;
    }
  });
  await login(page, "operator");
  await expect.poll(() => authorization.length).toBeGreaterThan(0);
  const headers = { Authorization: authorization };
  const accounts = async () => {
    const response = await page.request.get("/api/v1/accounts", { headers });
    expect(response.ok()).toBe(true);
    return await response.json() as Account[];
  };
  const before = await accounts();
  compose("pause", "redpanda");
  let resumed = false;
  try {
    await page.getByRole("link", { name: "Payments", exact: true }).click();
    await page.getByRole("button", { name: "Create payment" }).click();
    await page.getByLabel("Reference", { exact: true }).fill("RECOVERY-" + Date.now());
    await page.getByLabel("Amount (GBP)").fill("12.50");
    const created = page.waitForResponse(response => response.url().endsWith("/api/v1/payments") && response.request().method() === "POST");
    await page.getByRole("button", { name: "Post payment" }).click();
    const response = await created;
    expect(response.status()).toBe(201);
    const payment = await response.json();
    expect(payment.id).toMatch(/^[0-9a-f-]{36}$/);
    const original = response.request();
    const input = original.postDataJSON();
    const replayHeaders = { ...headers, "Idempotency-Key": original.headers()["idempotency-key"] };
    const replay = await page.request.post("/api/v1/payments", { headers: replayHeaders, data: input });
    expect(replay.status()).toBe(201);
    expect(await replay.json()).toEqual(payment);
    const conflict = await page.request.post("/api/v1/payments", { headers: replayHeaders, data: { ...input, amountMinor: input.amountMinor + 1 } });
    expect(conflict.status()).toBe(409);

    const outbox = () => JSON.parse(sql(`SELECT row_to_json(e) FROM (SELECT id, status, attempts FROM outbox_events WHERE aggregate_id = '${payment.id}') e`));
    await expect.poll(() => outbox().attempts, { timeout: 40_000 }).toBeGreaterThan(0);
    const failed = outbox();
    expect(failed.status).toBe("PENDING");
    const committed = await accounts();
    for (const account of committed) {
      const previous = before.find(row => row.id === account.id)!;
      const change = account.id === input.sourceAccountId ? -1250 : account.id === input.destinationAccountId ? 1250 : 0;
      expect(account.balanceMinor).toBe(previous.balanceMinor + change);
    }
    compose("unpause", "redpanda");
    resumed = true;
    await expect.poll(() => outbox().status, { timeout: 40_000 }).toBe("PUBLISHED");
    const recovered = outbox();
    expect(recovered.id).toBe(failed.id);
    expect(await accounts()).toEqual(committed);
    expect(sql(`SELECT count(*) FROM payment_intents WHERE id = '${payment.id}'`)).toBe("1");
    expect(sql(`SELECT count(*) FROM journal_entries WHERE payment_intent_id = '${payment.id}'`)).toBe("1");
    const journalResponse = await page.request.get("/api/v1/ledger/entries?limit=100", { headers });
    expect(journalResponse.ok()).toBe(true);
    const journals = await journalResponse.json() as LedgerEntry[];
    const journal = journals.find(entry => entry.reference === "PAY-" + payment.id)!;
    expect(journal.status).toBe("POSTED");
    expect(journal.postings).toHaveLength(2);
    expect(journal.postings.filter(p => p.direction === "DEBIT").reduce((sum, p) => sum + p.amountMinor, 0)).toBe(1250);
    expect(journal.postings.filter(p => p.direction === "CREDIT").reduce((sum, p) => sum + p.amountMinor, 0)).toBe(1250);
    await testInfo.attach("broker-recovery", { body: JSON.stringify({ paymentId: payment.id, failed, recovered, balancesUnchangedOnRecovery: true, journalStatus: journal.status }, null, 2), contentType: "application/json" });
  } finally {
    if (!resumed) compose("unpause", "redpanda");
  }
});
