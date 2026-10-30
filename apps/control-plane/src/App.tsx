import { createContext, useContext, useEffect, useRef, useState } from "react";
import type { FormEvent, ReactNode } from "react";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { NavLink, Navigate, Route, Routes, useLocation } from "react-router-dom";
import { BookOpen, Check, CircleHelp, Copy, CreditCard, Database, Download, ExternalLink, Gauge, Landmark, LogOut, Menu, Search, ShieldCheck, WalletCards, X } from "lucide-react";
import { api, API_URL, type CreatePayment, type LedgerEntry, type Payment } from "./api";
import { DEMO_MODE, identity, initializeAuth, signIn, signOut } from "./auth";
import { downloadCsv, minorUnits } from "./format";
import "./App.css";

const money = (n: number, currency = "GBP") => new Intl.NumberFormat("en-GB", { style: "currency", currency }).format(n / 100);
const date = (value: string) => new Intl.DateTimeFormat("en-GB", { day: "2-digit", month: "short", hour: "2-digit", minute: "2-digit" }).format(new Date(value));
const short = (id: string) => id.slice(0, 8).toUpperCase();
const nav = [["/overview", "Overview", Gauge], ["/payments", "Payments", CreditCard], ["/accounts", "Accounts", WalletCards], ["/ledger", "Ledger", BookOpen], ["/developers", "Developers", Database]] as const;
const SearchContext = createContext("");
const matches = (term: string, ...values: string[]) => values.join(" ").toLowerCase().includes(term.toLowerCase());
const useAccounts = () => useQuery({ queryKey: ["accounts"], queryFn: () => api.accounts() });
const usePayments = () => useQuery({ queryKey: ["payments"], queryFn: () => api.payments() });
const useLedger = () => useQuery({ queryKey: ["ledger"], queryFn: () => api.ledger() });

export default function App() {
  const client = useQueryClient();
  const [status, setStatus] = useState<"loading" | "signed-in" | "signed-out" | "failed">("loading");
  const [authError, setAuthError] = useState("");
  const [mobile, setMobile] = useState(false);
  const [search, setSearch] = useState("");
  useEffect(() => {
    let active = true;
    initializeAuth().then(authenticated => { if (active) setStatus(authenticated ? "signed-in" : "signed-out"); })
      .catch(() => { if (active) { setAuthError("Could not connect to the sign-in service."); setStatus("failed"); } });
    const expired = () => { client.clear(); setSearch(""); setStatus("signed-out"); };
    window.addEventListener("clearledger:session-expired", expired);
    return () => { active = false; window.removeEventListener("clearledger:session-expired", expired); };
  }, [client]);
  if (status !== "signed-in") return <main className="auth-layout">
    <Landmark size={36}/><p>ClearLedger</p><h1>Your payments workspace</h1>
    {status === "loading" ? <p role="status">Connecting securely…</p> : <>
      <p>{authError || "Sign in to access your organisation's accounts and journal."}</p>
      {status === "failed" ? <button className="primary" onClick={() => window.location.reload()}>Retry connection</button>
        : <button className="primary" onClick={() => void signIn().catch(() => setAuthError("Sign-in could not start. Please try again."))}>Sign in</button>}
      <p className="muted">For the local demo, use operator or reader with the password documented in the setup guide.</p>
    </>}
  </main>;
  const user = identity();
  const logout = async () => {
    client.clear();
    setStatus("signed-out");
    try { await signOut(); } catch { setAuthError("Sign-out could not finish. Close this tab or retry sign-in."); }
  };
  return <SearchContext.Provider value={search}><a className="skip-link" href="#main-content">Skip to content</a><div className="app">
    <aside className={mobile ? "sidebar open" : "sidebar"}>
      <div className="brand"><i><Landmark/></i><div><strong>ClearLedger</strong><small>Payments workspace</small></div><button aria-label="Close navigation" onClick={() => setMobile(false)}><X/></button></div>
      <div className="workspace"><span>Organisation</span><div className="workspace-identity"><strong>{user.tenant}</strong><small>{DEMO_MODE ? "Browser demo" : user.canWrite ? "Operator access" : "Read-only access"}</small></div></div>
      <nav aria-label="Main navigation">{nav.map(([to, label, Icon]) => <NavLink key={to} to={to} onClick={() => setMobile(false)}><Icon/><span>{label}</span></NavLink>)}</nav>
      <div className="sidebar-spacer"/>
      <div className="rail-status"><p>{DEMO_MODE ? "Sample data · resets on refresh" : "Authenticated API connection"}</p></div>
      <nav className="secondary"><a href={DEMO_MODE ? "https://github.com/Mohamedajab/clearledger" : API_URL + "/docs"} target="_blank" rel="noreferrer"><CircleHelp/>{DEMO_MODE ? "Source and API guide" : "API documentation"}<ExternalLink/></a></nav>
      <div className="user"><b>{user.username.slice(0, 2).toUpperCase()}</b><div><strong>{user.username}</strong><small>{user.canWrite ? "Operator" : "Reader"}</small></div>{!DEMO_MODE && <button className="logout-button" aria-label="Sign out" onClick={() => void logout()}><LogOut size={16}/></button>}</div>
    </aside>
    <main id="main-content"><Topbar search={search} setSearch={setSearch} onMenu={() => setMobile(true)}/>
      {DEMO_MODE && <div className="demo-banner"><ShieldCheck/>Interactive browser demo with sample data. No server payments are made.</div>}
      <Routes>
        <Route path="/overview" element={<Overview/>}/><Route path="/payments" element={<Payments/>}/>
        <Route path="/accounts" element={<Accounts/>}/><Route path="/ledger" element={<Ledger/>}/>
        <Route path="/developers" element={<Developers/>}/><Route path="*" element={<Navigate to="/overview" replace/>}/>
      </Routes>
    </main>
  </div></SearchContext.Provider>;
}
function Topbar({ search, setSearch, onMenu }: { search: string; setSearch: (value: string) => void; onMenu: () => void }) {
  const location = useLocation();
  return <header className="topbar"><button className="menu" aria-label="Open navigation" onClick={onMenu}><Menu/></button>
    <div><small>{identity().tenant}</small><strong>{nav.find(([to]) => to === location.pathname)?.[1] ?? "Overview"}</strong></div>
    <label className="search"><Search aria-hidden="true"/><input aria-label="Search records" name="search" autoComplete="off" value={search} onChange={event => setSearch(event.target.value)} placeholder="Search records…"/>{search && <button aria-label="Clear search" onClick={() => setSearch("")}><X size={14}/></button>}</label>
    <span className="env">{DEMO_MODE ? "Demo" : identity().canWrite ? "Operator" : "Read only"}</span>
  </header>;
}
function Page({ title, copy, action, children }: { title: string; copy: string; action?: ReactNode; children: ReactNode }) {
  return <div className="page"><header className="pagehead"><div><h1>{title}</h1><p>{copy}</p></div>{action}</header>{children}</div>;
}
function QueryState({ query, label }: { query: { isPending: boolean; error: Error | null; refetch: () => unknown }; label: string }) {
  if (query.isPending) return <div className="empty-state" role="status">Loading {label}…</div>;
  if (query.error) return <div className="empty-state error" role="alert"><p>{query.error.message}</p><button onClick={() => void query.refetch()}>Retry {label}</button></div>;
  return null;
}
function Empty({ children }: { children: ReactNode }) { return <p className="empty-state" role="status">{children}</p>; }
function Overview() {
  const dashboard = useQuery({ queryKey: ["dashboard"], queryFn: () => api.dashboard() });
  const payments = usePayments();
  const accounts = useAccounts();
  const ledger = useLedger();
  const data = dashboard.data;
  const canWrite = identity().canWrite;
  return <Page title="Overview" copy="Account balances, recent transfers and their journal entries." action={<NavLink className="primary" to="/payments">{canWrite ? "Create payment" : "View payments"}</NavLink>}>
    <QueryState query={dashboard} label="summary"/>
    {data && <section className="metrics">
      <Metric label="Total balance" value={accounts.data && new Set(accounts.data.map(a => a.currency)).size > 1 ? "Multiple currencies" : money(data.totalBalanceMinor, accounts.data?.[0]?.currency ?? "GBP")} sub="Current account projections"/>
      <Metric label="Processed volume" value={money(data.processedVolumeMinor)} sub="All completed GBP transfers"/>
      <Metric label="Completed payments" value={String(data.completedPayments)} sub="All-time count"/>
      <Metric label="Pending events" value={String(data.pendingEvents)} sub="Your organisation's outbox"/>
    </section>}
    {data && <p className="muted">Summary updated {date(data.asOf)}. Payment and journal lists show up to the latest 100 records.</p>}
    <QueryState query={accounts} label="accounts"/><QueryState query={ledger} label="journal"/>
    {ledger.data && <article className="integrity"><ShieldCheck aria-hidden="true"/><div><h2>Journal check</h2><p>{ledger.data.every(entry => entry.postings.reduce((balance, posting) => balance + (posting.direction === "DEBIT" ? posting.amountMinor : -posting.amountMinor), 0) === 0) ? "All loaded entries are balanced." : "An unbalanced entry needs investigation."} <span>{ledger.data.length} {ledger.data.length === 1 ? "entry" : "entries"}, {ledger.data.reduce((sum, entry) => sum + entry.postings.length, 0)} postings.</span></p></div><NavLink to="/ledger">Inspect journal</NavLink></article>}
    <QueryState query={payments} label="payments"/>
    {payments.data && <PaymentTable items={payments.data.slice(0, 8)} accounts={accounts.data ?? []}/>}
  </Page>;
}
function Metric({ label, value, sub }: { label: string; value: string; sub: string }) { return <article className="metric"><h2>{label}</h2><strong>{value}</strong><p>{sub}</p></article>; }
function PaymentTable({ items, accounts }: { items: Payment[]; accounts: { id: string; name: string }[] }) {
  const search = useContext(SearchContext);
  const names = Object.fromEntries(accounts.map(account => [account.id, account.name]));
  const filtered = items.filter(payment => matches(search, payment.merchantReference, payment.id, names[payment.sourceAccountId] ?? "", names[payment.destinationAccountId] ?? ""));
  return <article className="panel tablepanel"><header className="table-heading"><h2>Payments</h2><p className="muted" role="status">{filtered.length} shown</p></header>
    {filtered.length === 0 ? <Empty>{search ? "No payments match your search." : "No payments yet."}</Empty> : <div className="table-scroll" tabIndex={0} role="region" aria-label="Payment records">
      <table aria-label="Payments"><thead><tr><th scope="col">Reference</th><th scope="col">From / to</th><th scope="col" className="amount-cell">Amount</th><th scope="col">Status</th><th scope="col">Created</th></tr></thead><tbody>
      {filtered.map(payment => <tr key={payment.id}>
        <td><b>{payment.merchantReference}</b><small>PAY_{short(payment.id)}</small></td>
        <td>{names[payment.sourceAccountId] ?? short(payment.sourceAccountId)}<small>to {names[payment.destinationAccountId] ?? short(payment.destinationAccountId)}</small></td>
        <td className="amount-cell"><strong>{money(payment.amountMinor, payment.currency)}</strong></td><td><span className="payment-status"><Check aria-hidden="true"/>{payment.status.toLowerCase()}</span></td><td><time>{date(payment.createdAt)}</time></td>
      </tr>)}
      </tbody></table>
    </div>}
  </article>;
}
function Payments() {
  const query = usePayments();
  const accounts = useAccounts();
  const [open, setOpen] = useState(false);
  return <Page title="Payments" copy="Transfers committed with their balance changes and journal postings." action={identity().canWrite ? <button className="primary" onClick={() => setOpen(true)}>Create payment</button> : undefined}>
    {!identity().canWrite && <p className="demo-banner">You have read-only access. An operator can create payments.</p>}
    <QueryState query={query} label="payments"/><QueryState query={accounts} label="accounts"/>
    {query.data && accounts.data && <PaymentTable items={query.data} accounts={accounts.data}/>}
    {open && <PaymentModal close={() => setOpen(false)}/>}
  </Page>;
}
function PaymentModal({ close }: { close: () => void }) {
  const accounts = useAccounts();
  const client = useQueryClient();
  const [validation, setValidation] = useState("");
  const prepared = useRef<{ input: CreatePayment; key: string } | undefined>(undefined);
  const dialog = useRef<HTMLDivElement>(null);
  const previousFocus = useRef(document.activeElement as HTMLElement | null);
  const mutation = useMutation({
    mutationFn: ({ input, key }: { input: CreatePayment; key: string }) => api.createPayment(input, key),
    onSuccess: async () => { await client.invalidateQueries(); close(); },
  });
  useEffect(() => {
    const previous = previousFocus.current;
    dialog.current?.focus();
    return () => previous?.focus();
  }, []);
  const submit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    try {
      const input: CreatePayment = { merchantReference: String(form.get("reference")).trim(), sourceAccountId: String(form.get("source")), destinationAccountId: String(form.get("destination")), amountMinor: minorUnits(String(form.get("amount"))), currency: "GBP", description: String(form.get("description")).trim() };
      if (input.sourceAccountId === input.destinationAccountId) throw new Error("Choose two different accounts.");
      if (!input.merchantReference) throw new Error("Enter a payment reference.");
      setValidation("");
      if (!prepared.current || JSON.stringify(prepared.current.input) !== JSON.stringify(input)) prepared.current = { input, key: crypto.randomUUID() };
      mutation.mutate(prepared.current);
    } catch (error) { setValidation((error as Error).message); }
  };
  const trapFocus = (event: React.KeyboardEvent<HTMLDivElement>) => {
    if (event.key === "Escape" && !mutation.isPending) close();
    if (event.key !== "Tab") return;
    const focusable = [...event.currentTarget.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled)')];
    const first = focusable[0], last = focusable.at(-1);
    if (event.shiftKey && (document.activeElement === first || document.activeElement === event.currentTarget)) { event.preventDefault(); last?.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
  };
  return <div className="overlay"><div className="dialog" role="dialog" aria-modal="true" aria-labelledby="payment-title" tabIndex={-1} ref={dialog} onKeyDown={trapFocus}>
    <header><div><h2 id="payment-title">Create a payment</h2><p>Transfer between two GBP accounts.</p></div><button disabled={mutation.isPending} aria-label="Close payment form" onClick={close}><X/></button></header>
    <QueryState query={accounts} label="accounts"/>
    {accounts.data && <form onSubmit={submit}><fieldset disabled={mutation.isPending}>
      <label>Reference<input name="reference" autoComplete="off" spellCheck={false} required maxLength={96} placeholder="ORD-88422"/></label>
      <div className="twocol"><label>From account<select name="source" required defaultValue={accounts.data[0]?.id}>{accounts.data.filter(a => a.currency === "GBP" && a.status === "ACTIVE").map(a => <option key={a.id} value={a.id}>{a.name}</option>)}</select></label>
      <label>To account<select name="destination" required defaultValue={accounts.data[1]?.id}>{accounts.data.filter(a => a.currency === "GBP" && a.status === "ACTIVE").map(a => <option key={a.id} value={a.id}>{a.name}</option>)}</select></label></div>
      <label>Amount (GBP)<input name="amount" inputMode="decimal" pattern="[0-9]+([.][0-9]{1,2})?" required placeholder="125.00"/></label>
      <label>Description<textarea name="description" maxLength={280} rows={2}/></label>
      <p className="muted">GBP accounts only. Retrying unchanged details uses the same payment key.</p>
      {(validation || mutation.error) && <p role="alert" className="error">{validation || mutation.error?.message}</p>}
      <footer><button type="button" onClick={close}>Cancel</button><button className="primary" type="submit">{mutation.isPending ? "Posting…" : mutation.error ? "Retry payment" : "Post payment"}</button></footer>
    </fieldset></form>}
  </div></div>;
}
function Accounts() {
  const query = useAccounts();
  const search = useContext(SearchContext);
  const items = query.data?.filter(a => matches(search, a.name, a.externalRef, a.id)) ?? [];
  return <Page title="Accounts" copy="Balance projections backed by journal postings.">
    <QueryState query={query} label="accounts"/>
    {query.data && (items.length ? <section className="account-grid">{items.map(a => <article className="account" key={a.id}><header><i><Landmark/></i><em>{a.status.toLowerCase()}</em></header><p>{a.type}</p><h2>{a.name}</h2><strong>{money(a.balanceMinor, a.currency)}</strong><footer><span>{a.externalRef}</span><b>v{a.version}</b></footer></article>)}</section> : <Empty>{search ? "No accounts match your search." : "No accounts are available."}</Empty>)}
  </Page>;
}
function Ledger() {
  const query = useLedger();
  const accounts = useAccounts();
  const search = useContext(SearchContext);
  const names = Object.fromEntries((accounts.data ?? []).map(a => [a.id, a.name]));
  const items = query.data?.filter(entry => matches(search, entry.reference, entry.description, ...entry.postings.map(p => names[p.accountId] ?? p.accountId))) ?? [];
  const exportJournal = () => downloadCsv("clearledger-journal.csv", ["entry_id", "reference", "occurred_at", "account_id", "direction", "amount_minor", "currency"], items.flatMap(entry => entry.postings.map(p => [entry.id, entry.reference, entry.occurredAt, p.accountId, p.direction, p.amountMinor, p.currency])));
  return <Page title="Ledger" copy="Inspect or export the loaded journal records, up to the latest 100 entries." action={<button className="primary" disabled={!items.length} onClick={exportJournal}><Download aria-hidden="true"/>Export journal</button>}>
    <QueryState query={query} label="journal"/><QueryState query={accounts} label="accounts"/>
    {query.data && (items.length ? <section className="entries">{items.map(entry => <LedgerRow key={entry.id} entry={entry} names={names}/>)}</section> : <Empty>{search ? "No journal entries match your search." : "No journal entries yet."}</Empty>)}
  </Page>;
}
function LedgerRow({ entry, names }: { entry: LedgerEntry; names: Record<string, string> }) {
  const [expanded, setExpanded] = useState(false);
  return <article className={expanded ? "entry expanded" : "entry"}><button aria-expanded={expanded} onClick={() => setExpanded(!expanded)}><i><BookOpen/></i><span><b>{entry.description}</b><small>{entry.reference}</small></span><em>{entry.status.toLowerCase()}</em><strong>{money(entry.postings[0]?.amountMinor ?? 0, entry.postings[0]?.currency)}</strong><time>{date(entry.occurredAt)}</time><span>{expanded ? "−" : "+"}</span></button>
    {expanded && <div>{entry.postings.map((posting, index) => <p key={index}><span>{posting.direction}</span><span><strong>{names[posting.accountId] ?? short(posting.accountId)}</strong><small>{posting.accountId}</small></span><b>{money(posting.amountMinor, posting.currency)}</b></p>)}</div>}
  </article>;
}
function Developers() {
  const [message, setMessage] = useState("");
  const endpoints = [["POST", "/api/v1/payments", "Create a transfer (write access)"], ["GET", "/api/v1/payments", "Latest payments"], ["GET", "/api/v1/accounts", "Account balances"], ["GET", "/api/v1/ledger/entries", "Latest journal entries"]];
  const copy = async (text: string) => { try { await navigator.clipboard.writeText(text); setMessage("Copied."); } catch { setMessage("Clipboard is unavailable. Select and copy the text."); } };
  const example = "curl " + (API_URL || (DEMO_MODE ? "http://localhost:8080" : window.location.origin)) + "/api/v1/accounts -H 'Authorization: Bearer YOUR_ACCESS_TOKEN'";
  return <Page title="Developers" copy={DEMO_MODE ? "API reference for the local Java service. These endpoints are not hosted by the browser demo." : "Authenticated REST endpoints scoped to your verified tenant."}>
    <div role="status">{message}</div><section className="dev-grid"><article className="panel endpoints"><h2>REST API</h2>{endpoints.map(([method, path, description]) => <div key={path + method}><b>{method}</b><code>{path}</code><span>{description}</span><button aria-label={"Copy " + method + " " + path} onClick={() => void copy(path)}><Copy/></button></div>)}</article>
      <article className="panel code"><header><span>cURL</span><button onClick={() => void copy(example)}><Copy/>Copy example</button></header><pre>{example}</pre></article></section>
    <section className="contract-grid"><article><ShieldCheck/><h3>Verified identity</h3><p>Issuer, audience and tenant claims are validated. Client-supplied tenant headers do not select your organisation.</p></article><article><Database/><h3>Atomic transfers</h3><p>Balances, postings, payment records and outbox events commit together.</p></article><article><CreditCard/><h3>Safe retries</h3><p>Reuse the same Idempotency-Key for an unchanged transfer.</p></article></section>
  </Page>;
}
