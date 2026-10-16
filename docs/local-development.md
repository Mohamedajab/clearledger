# Local development and operations runbook

This guide covers the **local portfolio environment** in `compose.yml`. It is
not a production deployment guide. Compose includes a local Keycloak realm,
and the API requires JWT authentication in every profile. Use only sample
payment/account data. See [authentication](authentication.md) for local accounts.

For the design rationale, see [architecture](architecture.md), the
[ledger consistency ADR](adr/001-ledger-consistency.md) and the
[threat model](threat-model.md).

## Prerequisites and first launch

Install Docker with the Compose v2 plugin. Clone the repository and run from
the repository root:

```bash
cp .env.example .env
docker compose config
docker compose up --build -d
docker compose ps
```

On PowerShell, use `Copy-Item .env.example .env` instead of `cp` if preferred.
The sample `.env` contains development passwords: **change them for any
non-isolated experiment** and never commit the populated file.

`docker compose config` expands and checks the Compose model without starting
services. The API waits for healthy PostgreSQL and Redpanda; the UI starts
after the API health check passes. First builds can
take longer due to image and Java/Node dependency downloads.

`POSTGRES_DB` configures both the database and the API connection URL. PostgreSQL
creates that database on a fresh data volume; changing the variable does not
rename a database already stored in a volume.

Broker inspection, Prometheus and Grafana are optional. To start them too:

```bash
docker compose --profile tools up --build -d
```

The core payment workflow does not require these tools. The Prometheus example
still needs a configured scrape credential for the authenticated metrics endpoint.

## Local endpoints

| Service | Address | Notes |
| --- | --- | --- |
| UI | http://localhost:3000 | React operations console backed by the Compose API |
| Swagger UI | http://localhost:8080/docs | API documentation |
| API health | http://localhost:8080/actuator/health | Actuator health endpoint |
| API readiness | http://localhost:8080/actuator/health/readiness | Used by Compose readiness check |
| Redpanda Console | http://localhost:8090 | Optional `tools` profile |
| Prometheus | http://localhost:9090 | Optional `tools` profile; configure scrape authentication |
| Grafana | http://localhost:3001 | Optional `tools` profile; admin / value of `GRAFANA_ADMIN_PASSWORD` |

The database also publishes port 5432 and Redpanda's external Kafka port 19092
on the host. All published ports bind to loopback for local testing.
If any port is in use, change the relevant host mapping in `compose.yml`.

Quick checks:

```bash
docker compose ps
curl -fsS http://localhost:8080/actuator/health/readiness
docker compose logs --tail=100 api
docker compose logs --tail=100 redpanda
```

PowerShell users can open the readiness URL in a browser or run
`Invoke-WebRequest http://localhost:8080/actuator/health/readiness`.

## Exercise the idempotent payment path

Use the example JSON payload in the [main README](../README.md#api-example)
once the demo accounts are present. That example supplies
`Authorization: Bearer YOUR_ACCESS_TOKEN`, `Idempotency-Key` and an amount in **minor currency units**. Tenant identity comes from the token.

1. Send a valid payment request with a new idempotency key and note the
   returned payment identifier.
2. Send the **identical payload with the same key**. It should resolve to the
   original payment rather than moving funds twice.
3. Change the amount, accounts, currency or merchant reference and reuse the key.
   The API returns `409 Conflict`; a replay retains the original journal description.
4. Inspect payment/journal state and the Redpanda Console topic
   `payments.lifecycle.v1` to understand the outbox path.

Do not treat the Kafka topic as an exactly-once delivery channel: the
publisher marks an outbox record after broker acknowledgement, so a crash at
that boundary can lead to duplicate delivery. Production consumers need
their own durable event-ID deduplication. Keep only one active publisher:
the current query does not claim rows across instances.

Account UUIDs in examples are demonstration fixtures, not credentials.
If the local database has been reset, replaced or manually edited, confirm
that those accounts exist and have compatible currency/funds before testing.

## Authenticated local environment

The API uses the same JWT validation and permission checks in all profiles.
Compose imports a Keycloak fixture with operator, reader and other-tenant
accounts. Sign in at the UI with the local-only password from the README.
The fixture is for local testing; a remote deployment needs an HTTPS provider,
administrator-managed tenant attributes and production secrets.

`services/ledger-api/src/main/resources/application-dev.yml` disables the
outbox publisher by default when that profile is launched independently.
The Compose environment overrides the publisher flag to `true`. Therefore
the standalone Maven command and the full Compose stack do **not** have
identical outbox behaviour.

Private Actuator metrics require authentication too. The existing Prometheus
example needs a configured read credential for scraping; it does not bypass API
security. Health/readiness endpoints remain public and minimal.

## Frontend-only demo (no API or database)

This is an explicit in-memory adapter for presenting the interface; it does
not persist real payments:

```bash
cd apps/control-plane
npm ci
VITE_DEMO_MODE=true npm run dev
```

For PowerShell:

```powershell
$env:VITE_DEMO_MODE = 'true'
npm run dev
```

The Vite development server commonly uses http://localhost:5173; use the URL
printed in the terminal. This demo must not be confused with the Compose UI
at port 3000, which uses the API-backed build.

## Tests and diagnosis

```bash
# From the repository root:
cd services/ledger-api
./mvnw verify
cd ../../apps/control-plane
npm ci
npm run lint
npm test
npm run build
```

For Windows without a Unix-style shell, use `mvnw.cmd verify` for the backend.
The database integration tests use Testcontainers when Docker is available.
Alternatively, point them at a dedicated disposable PostgreSQL database:

```powershell
$env:LEDGER_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5432/clearledger_tests'
$env:LEDGER_TEST_DATABASE_USER = 'clearledger'
$env:LEDGER_TEST_DATABASE_PASSWORD = 'your-local-test-password'
./mvnw.cmd verify
```

Create the empty test database first. Tests apply migrations and add isolated
sample tenants; never use a live or shared application database. Without Docker
or an explicit test URL, database tests are skipped; inspect the test output.
CI uses a fresh Testcontainers PostgreSQL instance and runs the concurrency cases.

| Symptom | First checks |
| --- | --- |
| UI is not reachable | `docker compose ps` and `docker compose logs --tail=100 ui api` |
| API unhealthy | Database/Redpanda health, then API logs; read the readiness result |
| Cannot connect to PostgreSQL | Host port 5432, `POSTGRES_DB` vs `DATABASE_URL`, and volume state |
| Payment conflicts | Key reused with a different payload, tenant mismatch, account status/currency/funds |
| No event in broker | Confirm the DB transaction succeeded, worker is enabled, and publisher/broker logs |
| Frontend-only demo seems disconnected | Verify `VITE_DEMO_MODE`; demo mode intentionally bypasses the API |

## Shut down and data retention

```bash
docker compose down
```

This stops the core stack but **retains named volumes** (`postgres-data` and
`grafana-data`). If the optional tools are running, stop those with
`docker compose --profile tools down`. Restarting preserves stored local state.

```bash
docker compose down -v
```

**Destructive:** `-v` removes those local volumes and their stored data.
Only use it when you intend to reset the demonstration environment.

## Known production gaps

This is a portfolio implementation. Among the documented next steps are
journal-to-projection reconciliation, an inbox for consumer deduplication,
OIDC realm provisioning, rate limits, secret rotation and operational
alerting. A successful Compose startup or unit-test run does not establish
PCI compliance, production-grade tenant authentication or end-to-end
exactly-once event processing.

