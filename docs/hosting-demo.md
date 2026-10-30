# Public browser demo

**[Open ClearLedger](https://clearledger-demo.backstop.workers.dev)** — no signup.

This deployment contains the React control plane with its explicit in-memory sample adapter.
Payments, accounts and journal rows are browser-local fixtures. Reloading resets them.
It does not host the Java API, PostgreSQL, authentication or Kafka, and cannot move real money.
Use the [Compose runbook](local-development.md) to evaluate database and broker behaviour.

## Evaluate it

Open Payments, enter a small sample transfer, then inspect its two journal postings.
Use search or export to inspect the records. Reload to restore the initial sample data.
The on-screen banner identifies the browser demo on every page.

## Build and publish

```sh
cd apps/control-plane
npm ci
npm run build:demo
npx playwright install chromium
npm run test:demo
cd ../..
npx wrangler deploy --config wrangler.demo.jsonc
```

The static asset Worker is named `clearledger-demo`. The dedicated `.env.demo` enables
sample mode at build time; ordinary builds keep the existing authenticated application.
The browser policy disallows network connections (`connect-src 'none'`). No deployment
secrets or database service are required. This uses the existing Cloudflare account's
free allowances, which determine availability; it does not provide a spending guarantee.

## Checks actually run

30 October 2026, Windows, Node 24.16.0, Chromium via Playwright:

- `npm run build:demo`: passed TypeScript and Vite production build.
- `npm run test:demo`: passed locally and against the public deployment by setting
  `DEMO_BASE_URL=https://clearledger-demo.backstop.workers.dev`.
- The browser check creates a sample payment, checks journal rows, checks reset on reload
  and a 390px mobile viewport, and asserts no authentication/API requests or page errors.
- Wrangler dry run and deployment passed. Deployment version:
  `d2adc965-6834-4e5d-931f-90c33f242fe7`.

Java/PostgreSQL/Compose verification is separate from this static deployment. The
PR CI run [41139137627](https://github.com/Mohamedajab/clearledger/actions/runs/41139137627)
passed 33 backend tests, 11 frontend tests and four authenticated browser cases, including
broker outage/recovery, plus the new browser-demo test. Backend tests had no skips.
Java and a running Docker engine were unavailable locally.
