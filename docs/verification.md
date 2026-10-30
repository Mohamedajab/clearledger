# Evaluation and CV notes

## Run the application

Follow [local development](local-development.md) and sign in at http://localhost:3000 as
operator using the local-only fixture password in the README. Use sample accounts only.

The existing browser suite now checks payment creation, identical replay, conflicting replay,
POSTED journal entries and broker outage/recovery. Run from apps/control-plane:

```sh
npm ci
npx playwright install chromium
npm run test:e2e
```

The recovery test pauses the local broker and always attempts to resume it. It checks a failed
outbox attempt is persisted as PENDING, then becomes PUBLISHED after recovery. Account balances
must remain unchanged during recovery; one payment and one journal must exist. The CI artifact
includes a broker-recovery JSON attachment. No database reset is performed.

## Verification

Local environment: 30 October 2026, Windows, Node 24.16.0.

- Frontend lint, 11 tests, TypeScript checking and production build passed.
- Updated Vite and Undici within existing dependency ranges after npm audit reported two high-severity development dependency findings; the resulting audit reports zero findings.
- Java and a running Docker engine were unavailable locally. Backend tests and Compose recovery require the PR CI result before they can be called verified.

CI provisions Java 21 and real PostgreSQL tests, builds the application images, starts the
Keycloak/PostgreSQL/Redpanda fixture and runs four authenticated browser scenarios.
The database integration suite now fails in CI if no PostgreSQL runtime is available.
No schemas, REST contracts, payment payloads or hosted deployments changed. The Kafka message
adds a stable event-id header. Single-publisher and at-least-once delivery limits remain.

## Suggested CV bullets

- Built a Java/Spring Boot and React ledger application with atomic transfers, balanced journals and tenant-scoped idempotency.
- Added outbox acknowledgement, retry and interruption checks, plus an authenticated broker-recovery scenario that verifies balances and journal state.
