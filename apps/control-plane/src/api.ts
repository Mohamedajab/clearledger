import { accessToken, DEMO_MODE, expireSession } from "./auth";
export { DEMO_MODE } from "./auth";
export type Dashboard = { totalBalanceMinor: number; processedVolumeMinor: number; completedPayments: number; pendingEvents: number; accountCount: number; asOf: string };
export type Account = { id: string; externalRef: string; name: string; currency: string; type: string; status: string; balanceMinor: number; version: number; createdAt: string };
export type Payment = { id: string; merchantReference: string; sourceAccountId: string; destinationAccountId: string; amountMinor: number; currency: string; status: string; createdAt: string; completedAt: string };
export type Posting = { accountId: string; direction: "DEBIT" | "CREDIT"; amountMinor: number; currency: string };
export type LedgerEntry = { id: string; reference: string; type: string; description: string; status: string; occurredAt: string; postings: Posting[] };
export type CreatePayment = { merchantReference: string; sourceAccountId: string; destinationAccountId: string; amountMinor: number; currency: string; description: string };
export interface ClearLedgerClient {
  dashboard(): Promise<Dashboard>;
  accounts(): Promise<Account[]>;
  payments(): Promise<Payment[]>;
  ledger(): Promise<LedgerEntry[]>;
  createPayment(input: CreatePayment, idempotencyKey: string): Promise<Payment>;
}
export const API_URL = import.meta.env.VITE_API_URL ?? "";
export class HttpClient implements ClearLedgerClient {
  private async request<T>(path: string, init?: RequestInit): Promise<T> {
    const token = await accessToken();
    const response = await fetch(API_URL + path, {
      ...init,
      headers: { "Content-Type": "application/json", ...init?.headers, Authorization: "Bearer " + token },
    });
    if (response.status === 401) expireSession();
    if (!response.ok) {
      const problem = await response.json().catch(() => ({})) as { title?: string; detail?: string };
      throw new Error(problem.detail ?? problem.title ?? (response.status === 403 ? "You do not have permission for this action." : "Request failed (" + response.status + ")"));
    }
    return response.json() as Promise<T>;
  }
  dashboard() { return this.request<Dashboard>("/api/v1/dashboard"); }
  accounts() { return this.request<Account[]>("/api/v1/accounts"); }
  payments() { return this.request<Payment[]>("/api/v1/payments?limit=100"); }
  ledger() { return this.request<LedgerEntry[]>("/api/v1/ledger/entries?limit=100"); }
  createPayment(input: CreatePayment, idempotencyKey: string) {
    return this.request<Payment>("/api/v1/payments", { method: "POST", headers: { "Idempotency-Key": idempotencyKey }, body: JSON.stringify(input) });
  }
}
const seeds: Account[] = [
  { id: "11111111-1111-1111-1111-111111111111", externalRef: "WALLET-GBP-OPERATING", name: "Operating wallet", currency: "GBP", type: "WALLET", status: "ACTIVE", balanceMinor: 184523900, version: 42, createdAt: "2026-03-01T09:00:00Z" },
  { id: "22222222-2222-2222-2222-222222222222", externalRef: "WALLET-GBP-SETTLEMENT", name: "Settlement reserve", currency: "GBP", type: "WALLET", status: "ACTIVE", balanceMinor: 48291000, version: 18, createdAt: "2026-03-01T09:00:00Z" },
  { id: "33333333-3333-3333-3333-333333333333", externalRef: "MERCHANT-ACME", name: "Acme marketplace", currency: "GBP", type: "MERCHANT", status: "ACTIVE", balanceMinor: 8734200, version: 11, createdAt: "2026-04-12T09:00:00Z" },
  { id: "44444444-4444-4444-4444-444444444444", externalRef: "MERCHANT-NOVA", name: "Nova stores", currency: "GBP", type: "MERCHANT", status: "ACTIVE", balanceMinor: 5267800, version: 8, createdAt: "2026-05-22T09:00:00Z" },
];
export class DemoClient implements ClearLedgerClient {
  private balances = structuredClone(seeds);
  private transfers: Payment[] = [
    { id: "00000000-0000-0000-0000-0000000071a9", merchantReference: "ORD-88421", sourceAccountId: seeds[0].id, destinationAccountId: seeds[2].id, amountMinor: 124599, currency: "GBP", status: "COMPLETED", createdAt: "2026-09-28T17:42:18Z", completedAt: "2026-09-28T17:42:18Z" },
  ];
  private replays = new Map<string, { input: CreatePayment; payment: Payment }>();
  async dashboard() {
    return { totalBalanceMinor: this.balances.reduce((sum, account) => sum + account.balanceMinor, 0), processedVolumeMinor: this.transfers.reduce((sum, payment) => sum + payment.amountMinor, 0), completedPayments: this.transfers.length, pendingEvents: 0, accountCount: this.balances.length, asOf: new Date().toISOString() };
  }
  async accounts() { return structuredClone(this.balances); }
  async payments() { return structuredClone(this.transfers); }
  async ledger(): Promise<LedgerEntry[]> {
    return this.transfers.map(payment => ({ id: payment.id, reference: "PAY-" + payment.id, type: "PAYMENT", description: payment.merchantReference, status: "POSTED", occurredAt: payment.createdAt, postings: [{ accountId: payment.sourceAccountId, direction: "DEBIT", amountMinor: payment.amountMinor, currency: payment.currency }, { accountId: payment.destinationAccountId, direction: "CREDIT", amountMinor: payment.amountMinor, currency: payment.currency }] }));
  }
  async createPayment(input: CreatePayment, idempotencyKey: string) {
    const previous = this.replays.get(idempotencyKey);
    if (previous) {
      if (JSON.stringify(previous.input) !== JSON.stringify(input)) throw new Error("Idempotency key already used for another payment.");
      return structuredClone(previous.payment);
    }
    const source = this.balances.find(account => account.id === input.sourceAccountId);
    const destination = this.balances.find(account => account.id === input.destinationAccountId);
    if (!source || !destination || source.id === destination.id) throw new Error("Choose two different accounts.");
    if (!Number.isSafeInteger(input.amountMinor) || input.amountMinor <= 0) throw new Error("Enter a valid amount.");
    if (source.currency !== input.currency || destination.currency !== input.currency) throw new Error("Both accounts must use the payment currency.");
    if (source.balanceMinor < input.amountMinor) throw new Error("Insufficient funds.");
    const payment: Payment = { ...input, id: crypto.randomUUID(), status: "COMPLETED", createdAt: new Date().toISOString(), completedAt: new Date().toISOString() };
    source.balanceMinor -= input.amountMinor;
    destination.balanceMinor += input.amountMinor;
    source.version++;
    destination.version++;
    this.transfers = [payment, ...this.transfers];
    this.replays.set(idempotencyKey, { input: structuredClone(input), payment });
    return structuredClone(payment);
  }
}
export const api: ClearLedgerClient = DEMO_MODE ? new DemoClient() : new HttpClient();
