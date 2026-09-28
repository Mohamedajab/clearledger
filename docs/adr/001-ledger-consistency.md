# ADR-001: Keep the ledger consistency boundary in PostgreSQL

- Status: Accepted
- Date: 2026-09-28

## Decision

Use a modular monolith and one PostgreSQL transaction for the payment record, account projections, balanced journal and outbox event. Enforce balanced entries both in the domain and through a deferred database trigger.

## Why

The invariant is more valuable than independent deployment. A service split would require a distributed transaction, saga or reservation protocol and make intermediate monetary state observable. PostgreSQL gives durable atomicity, row-level locks, constraints and mature operations.

## Consequences

Payment write throughput is coupled to the primary database. Package boundaries and events retain an extraction path, but extraction requires a new consistency design. Reporting workloads should not run against the primary indefinitely.

## Alternatives rejected

- Separate account, payment and ledger services: premature distributed consistency.
- Store only mutable balances: insufficient audit trail and reconciliation evidence.
- Event sourcing everything: powerful, but increases projection/versioning complexity beyond current requirements.
