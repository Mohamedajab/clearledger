# Local development and operations runbook

This guide covers the **local portfolio environment** in `compose.yml`. It is
not a production deployment guide. The Compose API starts with the `dev`
Spring profile, demo credentials and an unauthenticated local development
boundary. Do not expose these services directly to the internet or use real
payment/account data.

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
services. The API waits for healthy PostgreSQL, Redis and Redpanda; the UI and
monitoring services start after the API health check passes. First builds can
take longer due to image and Java/Node dependency downloads.

**Configuration detail:** the Compose API's `DATABASE_URL` explicitly targets
`/clearledger`, whereas PostgreSQL's database name comes from `POSTGRES_DB`.
If you change the latter, update the matching API URL as well. Do not assume
changing just `.env` updates both sides.

## Local endpoints

| Service | Address | Notes |
| --- | --- | --- |
| UI | http://localhost:3000 | React operations console backed by the Compose API |
| Swagger UI | http://localhost:8080/docs | API documentation |
| API health | http://localhost:8080/actuator/health | Actuator health endpoint |
| API readiness | http://localhost:8080/actuator/health/readiness | Used by Compose readiness check |
| Redpanda Console | http://localhost:8090 | Kafka-compatible broker inspection |
| Prometheus | http://localhost:9090 | Metrics |
| Grafana | http://localhost:3001 | Local login: admin / value of `GRAFANA_ADMIN_PASSWORD` |

The database also publishes port 5432, Redis port 6379 and Redpanda's
external Kafka port 19092 on the host. These bindings are for local testing.
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
`X-Tenant-Id`, `Idempotency-Key` and an amount in **minor currency units**.

1. Send a valid payment request with a new idempotency key and note the
   returned payment identifier.
2. Send the **identical payload with the same key**. It should resolve to the
   original payment rather than moving funds twice.
3. Send a **different payload with that same key**. The API documents
   `409 Conflict` for payload-aware key reuse.
4. Inspect payment/journal state and the Redpanda Console topic
   `payments.lifecycle.v1` to understand the outbox path.

Do not treat the Kafka topic as an exactly-once delivery channel: the
publisher marks an outbox record after broker acknowledgement, so a crash at
that boundary can lead to duplicate delivery. Production consumers need
their own durable event-ID deduplication.

Account UUIDs in examples are demonstration fixtures, not credentials.
If the local database has been reset, replaced or manually edited, confirm
that those accounts exist and have compatible currency/funds before testing.

## Two security modes that must not be confused

**Compose/demo path:** `SPRING_PROFILES_ACTIVE=dev` is configured in
`compose.yml`. It allows local UI exploration without provisioning an OIDC
identity provider and starts the outbox worker using
`OUTBOX_PUBLISHER_ENABLED=true`.

**Default application path:** the application config declares OAuth2
resource-server JWT verification and read/write scopes. Running the app
outside the Compose `dev` setup is **not** proof of production readiness;
a real identity provider, tenant-claim binding, key management and deployment
hardening remain separate tasks.

`services/ledger-api/src/main/resources/application-dev.yml` disables the
outbox publisher by default when that profile is launched independently.
The Compose environment overrides the publisher flag to `true`. Therefore
the standalone Maven command and the full Compose stack do **not** have
identical outbox behaviour.

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
npm run build
```

For Windows without a Unix-style shell, use `mvnw.cmd verify` for the backend.
The database integration tests use Testcontainers when Docker is available;
inspect the test output rather than assuming all integration cases executed.

| Symptom | First checks |
| --- | --- |
| UI is not reachable | `docker compose ps` and `docker compose logs --tail=100 ui api` |
| API unhealthy | Database/Redis/Redpanda health, then API logs; read the readiness result |
| Cannot connect to PostgreSQL | Host port 5432, `POSTGRES_DB` vs `DATABASE_URL`, and volume state |
| Payment conflicts | Key reused with a different payload, tenant mismatch, account status/currency/funds |
| No event in broker | Confirm the DB transaction succeeded, worker is enabled, and publisher/broker logs |
| Frontend-only demo seems disconnected | Verify `VITE_DEMO_MODE`; demo mode intentionally bypasses the API |

## Shut down and data retention

```bash
docker compose down
```

This stops the stack but **retains named volumes** (`postgres-data`,
`redis-data` and `grafana-data`), so restarting can preserve local state.

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

