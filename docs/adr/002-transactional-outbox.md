# ADR-002: Publish lifecycle events through a transactional outbox

- Status: Accepted
- Date: 2026-09-28

## Decision

Insert an outbox row in the payment database transaction. A scheduled worker locks unpublished rows, publishes to Kafka with the event ID as key, waits for acknowledgement, then records publication time.

## Why

Writing independently to a database and broker creates a dual-write gap: either side can succeed alone. The outbox makes committed database state the durable source of pending work.

## Delivery contract

Delivery is at-least-once, not globally exactly-once. A crash after broker acknowledgement and before the outbox update can duplicate an event. Consumers must keep an inbox/deduplication record keyed by event ID.

## Consequences

Event publication is asynchronous and introduces measurable lag. Old published rows require retention/archival. High volume may justify change-data-capture rather than polling.
