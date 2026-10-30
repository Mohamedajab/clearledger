# ADR-002: Publish lifecycle events through a transactional outbox

- Status: Accepted
- Date: 2026-09-28

## Decision

Insert an outbox row in the payment database transaction. A single scheduled publisher reads pending rows, publishes to Kafka with the aggregate/payment ID as key, waits for acknowledgement, then records publication time. The payload contains a stable event ID for consumer deduplication; the Kafka key preserves grouping by payment.

## Why

Writing independently to a database and broker creates a dual-write gap: either side can succeed alone. The outbox makes committed database state the durable source of pending work.

## Delivery contract

Delivery may be repeated, not globally exactly-once. A crash after broker acknowledgement and before the outbox update can duplicate an event. Consumers must keep an inbox/deduplication record keyed by event ID. An event is marked FAILED after ten failed attempts; a replay endpoint and automatic recovery of exhausted events are not implemented.

## Consequences

Event publication is asynchronous and introduces measurable lag. The current query does not implement row claims or `SKIP LOCKED`; enable the publisher on only one application instance. Multiple publishers require tested claims or leases first. Old published rows require retention/archival. High volume may justify change-data-capture rather than polling.

## Delivery identifier and interruption

The Kafka `event-id` header is the persisted outbox row UUID, stable across send attempts.
The existing JSON payload and aggregate-based partition key are unchanged. Consumers should
choose and persist a deduplication identifier consistently. A missing acknowledgement does
not establish that the broker rejected the message; retries can produce duplicates.

The publisher preserves interruption, stops its batch and leaves the interrupted event pending.
Other failures increment the existing attempt counter; ten failures exhaust the policy.
Only one publisher is supported: reading a batch does not claim rows across instances.
