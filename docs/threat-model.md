# Threat model

## Assets and trust boundaries

Assets are account balances, immutable postings, payment identifiers, tenant isolation and signing credentials. Untrusted clients cross the HTTP boundary; the API crosses database, Redis and Kafka boundaries; operators cross observability and administration boundaries.

## Key threats and controls

| Threat | Control | Remaining work |
|---|---|---|
| Cross-tenant access | Verified JWT tenant claim, tenant-scoped queries and pending-event counts | Keep tenant attributes administrator-managed; audit account provisioning |
| Duplicate payment | Required tenant-scoped idempotency key and payload hash | Set retention policy and reject expired key reuse explicitly |
| Overspend/race | Pessimistic account locks, stable order, atomic transaction | Load-test hot accounts and alert on lock waits |
| Ledger corruption | Domain balance check, PostgreSQL constraint trigger, immutable posted state | Add scheduled reconciliation and privileged audit logs |
| Token misuse | Signature, issuer, expiry and audience validation, narrow read/write permissions, in-memory browser tokens | Provision short-lived tokens and manage signing-key rotation |
| Event loss | Transactional outbox and broker acknowledgement | Add outbox-age SLO and dead-letter operations |
| Event replay | Stable event ID and Kafka key | Consumers need durable inbox deduplication |
| Injection | Prepared JPA queries, validation, JSON serialization | Add DAST and dependency scanning in a real deployment |
| Secret disclosure | Environment injection and ignored `.env` | Use a managed secret store; rotate credentials |
| Browser compromise | CSP, nosniff, restrictive CORS | Remove inline-style allowance by using nonce/hash policy |

## Explicit non-goals

The seeded tenant and Keycloak development realm are for local use. There is no unauthenticated development API profile. The system is not PCI-certified, does not store cardholder data, and has not had an independent security assessment. Docker Compose passwords are intentionally non-production defaults.
