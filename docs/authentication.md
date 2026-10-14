# Authentication and tenant access

The React application signs in through Keycloak using authorization code flow with PKCE S256. The public browser client has no client secret. Access and refresh tokens stay in the adapter's memory; they are not saved to localStorage or sessionStorage. Requests refresh a nearly expired token before sending a Bearer header. A rejected/expired session clears the query cache and returns to sign-in. Logout clears cached organisation data.

The API verifies the JWT signature using the configured JWKS, then validates issuer, expiry, audience `clearledger-api` and a string `tenant_id`. Every private controller derives the tenant from that verified claim. A forged `X-Tenant-Id` does not change the selected tenant. Pending-event counts join through the tenant's payment records.

Read requests require `ledger:read` or `ledger:write`; mutations require `ledger:write`. The fixture uses those exact Keycloak realm roles, which the API maps to its scope authorities. Equivalent OAuth scope claims are supported. Unknown realm roles confer no API permission. UI controls reflect write permission; the API remains authoritative.

## Local fixture

`docker compose up --build` imports `infra/keycloak/clearledger-realm.json`. Open http://localhost:3000. Accounts `operator`, `reader`, and `other-tenant` use **Local-demo-only-2026!**, exclusively for this loopback-bound local fixture. The third account belongs to tenant `other`, which has no seeded accounts.

The browser uses `/auth` on the same origin. Nginx proxies it to Keycloak; the API fetches public signing keys through the internal service URL. Tokens use the public issuer `http://localhost:3000/auth/realms/clearledger`. Vite proxies auth and API paths too, so http://localhost:5173 works against the Compose services. Registration, direct password grants and password resets are disabled for this fixture.

## Production configuration

Provision an HTTPS identity provider and assign tenant attributes and permissions through trusted administration. Do not allow users to edit their tenant attribute or permission roles. The imported accounts and Keycloak `start-dev` are for local verification only.

Set `OIDC_ISSUER_URI`, `OIDC_JWK_SET_URI`, `OIDC_AUDIENCE`, database secrets and `ALLOWED_ORIGINS` on the API. Configure the browser client with exact HTTPS redirect/logout URIs and PKCE. By default the UI uses same-origin `/auth`; a different provider uses build-time `VITE_OIDC_URL`, `VITE_OIDC_REALM` and `VITE_OIDC_CLIENT_ID`. Update Nginx proxy/CSP policy for that deployment. An external API uses `VITE_API_URL`; add matching Docker build arguments if building a customized image.

The application has no authentication bypass in a development or test profile. `VITE_DEMO_MODE=true` selects an explicitly labelled browser-only sample adapter and makes no API payments.

## Verification

Token-policy tests cover issuer, audience, expiry, missing/malformed tenants and permission-role mapping. HTTP tests reject anonymous requests, prevent reader writes and prove that forged tenant headers cannot select another tenant. PostgreSQL integration asserts persisted POSTED journals, two postings, balance changes and one outbox event after an idempotent replay.

With the local stack running:

```bash
cd apps/control-plane
npm ci
npx playwright install chromium
npm run test:e2e
```

The three browser tests perform actual OIDC login and cover database payment creation, journal export, logout, reader controls and another organisation's isolation. They create sample transfers and should run only against the local fixture. CI starts that fixture and runs the same checks.

## Scope

The current payment form supports seeded GBP accounts. Lists and exports contain up to the latest 100 entries; they are not complete audit exports. Account provisioning, API-key creation and operational telemetry screens are not implemented, so the UI exposes no inactive controls or fabricated measurements. This is a portfolio system, not a payment provider or audited production service.
