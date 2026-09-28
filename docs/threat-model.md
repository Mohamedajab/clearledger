# Threat model

## Assets and trust boundaries

Assets are account balances, immutable postings, payment identifiers, tenant isolation and signing credentials. Untrusted clients cross the HTTP boundary; the API crosses database, Redis and Kafka boundaries; operators cross observability and administration boundaries.

## Key threats and controls

| Threat | Control | Remaining work |
|---|---|---|
| Cross-tenant access | Tenant-scoped repository queries and uniqueness | Derive tenant from a verified JWT claim; never trust the header in production |
| Duplicate payment | Required tenant-scoped idempotency key and payload hash | Set retention policy and reject expired key reuse explicitly |
| Overspend/race | Pessimistic account locks, stable order, atomic transaction | Load-test hot accounts and alert on lock waits |
| Ledger corruption | Domain balance check, PostgreSQL constraint trigger, immutable posted state | Add scheduled reconciliation and privileged audit logs |
| Token misuse | OAuth2 resource server, narrow read/write scopes, stateless sessions | Provision short-lived tokens, key rotation and audience validation |
| Event loss | Transactional outbox and broker acknowledgement | Add outbox-age SLO and dead-letter operations |
| Event replay | Stable event ID and Kafka key | Consumers need durable inbox deduplication |
| Injection | Prepared JPA queries, validation, JSON serialization | Add DAST and dependency scanning in a real deployment |
| Secret disclosure | Environment injection and ignored `.env` | Use a managed secret store; rotate credentials |
| Browser compromise | CSP, nosniff, restrictive CORS | Remove inline-style allowance by using nonce/hash policy |

## Explicit non-goals

The seeded tenant and development security profile are for local use. The system is not PCI-certified, does not store cardholder data, and has not had an independent security assessment. Docker Compose passwords are intentionally non-production defaults.
