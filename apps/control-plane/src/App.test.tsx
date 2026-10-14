import { beforeEach, expect, it, vi } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { MemoryRouter } from "react-router-dom";

const auth = vi.hoisted(() => ({ canWrite: true, signedIn: true }));
vi.mock("./auth", () => ({ DEMO_MODE: true, initializeAuth: async () => auth.signedIn, identity: () => ({ username: "Operator", tenant: "northstar", canWrite: auth.canWrite }), signIn: vi.fn(), signOut: vi.fn(), accessToken: vi.fn(), expireSession: vi.fn() }));
vi.mock("./api", async importOriginal => {
  const actual = await importOriginal<typeof import("./api")>();
  return { ...actual, api: { accounts: vi.fn(), payments: vi.fn(), dashboard: vi.fn(), ledger: vi.fn(), createPayment: vi.fn() } };
});
import App from "./App";
import { api, DemoClient } from "./api";

beforeEach(() => {
  auth.canWrite = true; auth.signedIn = true;
  const demo = new DemoClient();
  vi.mocked(api.accounts).mockImplementation(() => demo.accounts());
  vi.mocked(api.payments).mockImplementation(() => demo.payments());
  vi.mocked(api.dashboard).mockImplementation(() => demo.dashboard());
  vi.mocked(api.ledger).mockImplementation(() => demo.ledger());
  vi.mocked(api.createPayment).mockImplementation((input, key) => demo.createPayment(input, key));
});
function show(path = "/payments") {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  render(<QueryClientProvider client={client}><MemoryRouter initialEntries={[path]}><App/></MemoryRouter></QueryClientProvider>);
  return userEvent.setup();
}
it("posts a payment and refreshes the displayed records", async () => {
  const user = show();
  await user.click(await screen.findByRole("button", { name: "Create payment" }));
  await user.type(screen.getByLabelText("Reference"), "CV-DEMO-1");
  await user.type(screen.getByLabelText("Amount (GBP)"), "12.50");
  await user.click(screen.getByRole("button", { name: "Post payment" }));
  expect(await screen.findByText("CV-DEMO-1")).toBeVisible();
  expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  expect(api.createPayment).toHaveBeenCalledWith(expect.objectContaining({ amountMinor: 1250 }), expect.any(String));
});
it("retains the idempotency key across an ambiguous network failure and retry", async () => {
  vi.mocked(api.createPayment).mockRejectedValueOnce(new Error("Connection interrupted"));
  const user = show();
  await user.click(await screen.findByRole("button", { name: "Create payment" }));
  await user.type(screen.getByLabelText("Reference"), "RETRY-1");
  await user.type(screen.getByLabelText("Amount (GBP)"), "0.29");
  await user.click(screen.getByRole("button", { name: "Post payment" }));
  expect(await screen.findByRole("alert")).toHaveTextContent("Connection interrupted");
  await user.click(screen.getByRole("button", { name: "Retry payment" }));
  await screen.findByText("RETRY-1");
  expect(vi.mocked(api.createPayment).mock.calls[0][1]).toBe(vi.mocked(api.createPayment).mock.calls[1][1]);
});
it("shows an API error with a working retry instead of a blank table", async () => {
  vi.mocked(api.payments).mockRejectedValueOnce(new Error("Payments unavailable"));
  const user = show();
  expect(await screen.findByRole("alert")).toHaveTextContent("Payments unavailable");
  await user.click(screen.getByRole("button", { name: "Retry payments" }));
  expect(await screen.findByText("ORD-88421")).toBeVisible();
});
it("filters account records and explains empty search results", async () => {
  const user = show("/accounts");
  await screen.findByRole("heading", { name: "Operating wallet" });
  await user.type(screen.getByRole("textbox", { name: "Search records" }), "Acme");
  expect(screen.getByRole("heading", { name: "Acme marketplace" })).toBeVisible();
  expect(screen.queryByRole("heading", { name: "Operating wallet" })).not.toBeInTheDocument();
  await user.clear(screen.getByRole("textbox", { name: "Search records" }));
  await user.type(screen.getByRole("textbox", { name: "Search records" }), "missing");
  expect(screen.getByText("No accounts match your search.")).toBeVisible();
});
it("keeps write controls away from read-only users", async () => {
  auth.canWrite = false;
  show();
  await screen.findByText("ORD-88421");
  expect(screen.queryByRole("button", { name: "Create payment" })).not.toBeInTheDocument();
  expect(screen.getByText(/You have read-only access/)).toBeVisible();
});
it("does not fetch private records before authentication", async () => {
  auth.signedIn = false;
  show();
  await screen.findByRole("button", { name: "Sign in" });
  await waitFor(() => expect(api.accounts).not.toHaveBeenCalled());
  expect(api.payments).not.toHaveBeenCalled();
});
