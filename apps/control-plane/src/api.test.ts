import { beforeEach, expect, it, vi } from "vitest";

vi.mock("./auth", () => ({ DEMO_MODE: false, accessToken: vi.fn(async () => "verified-token"), expireSession: vi.fn() }));
import { DemoClient, HttpClient } from "./api";
import { expireSession } from "./auth";

beforeEach(() => vi.unstubAllGlobals());
it("preserves total funds and replays an unchanged demo transfer exactly once", async () => {
  const client = new DemoClient();
  const accounts = await client.accounts();
  const initial = await client.dashboard();
  const input = { merchantReference: "TEST-1", sourceAccountId: accounts[0].id, destinationAccountId: accounts[1].id, amountMinor: 12500, currency: "GBP", description: "Test" };
  const first = await client.createPayment(input, "stable-key");
  expect((await client.createPayment(input, "stable-key")).id).toBe(first.id);
  const after = await client.accounts();
  expect(after[0].balanceMinor).toBe(accounts[0].balanceMinor - 12500);
  expect(after[1].balanceMinor).toBe(accounts[1].balanceMinor + 12500);
  expect((await client.dashboard()).totalBalanceMinor).toBe(initial.totalBalanceMinor);
  await expect(client.createPayment({ ...input, amountMinor: 12600 }, "stable-key")).rejects.toThrow("Idempotency");
  await expect(client.createPayment({ ...input, amountMinor: Number.MAX_SAFE_INTEGER }, "another-key")).rejects.toThrow("Insufficient");
});
it("sends bearer authentication and the caller's stable retry key without a tenant header", async () => {
  const fetchMock = vi.fn<typeof fetch>(async () => new Response(JSON.stringify({ id: "saved" }), { status: 201 }));
  vi.stubGlobal("fetch", fetchMock);
  const input = { merchantReference: "TEST", sourceAccountId: "a", destinationAccountId: "b", amountMinor: 100, currency: "GBP", description: "" };
  await new HttpClient().createPayment(input, "stable-key");
  expect(fetchMock).toHaveBeenCalledWith("/api/v1/payments", expect.objectContaining({ headers: expect.objectContaining({ Authorization: "Bearer verified-token", "Idempotency-Key": "stable-key" }) }));
  expect(fetchMock.mock.calls[0][1]?.headers).not.toHaveProperty("X-Tenant-Id");
});
it("expires the session after the API rejects a token", async () => {
  vi.stubGlobal("fetch", vi.fn(async () => new Response(null, { status: 401 })));
  await expect(new HttpClient().accounts()).rejects.toThrow("401");
  expect(expireSession).toHaveBeenCalled();
});
