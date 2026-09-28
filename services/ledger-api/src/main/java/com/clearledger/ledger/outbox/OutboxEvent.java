package com.clearledger.ledger.outbox;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {
    public enum Status { PENDING, PUBLISHED, FAILED }

    @Id private UUID id;
    @Column(name = "aggregate_type", nullable = false) private String aggregateType;
    @Column(name = "aggregate_id", nullable = false) private UUID aggregateId;
    @Column(name = "event_type", nullable = false) private String eventType;
    @JdbcTypeCode(SqlTypes.JSON) @Column(nullable = false, columnDefinition = "jsonb") private String payload;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(nullable = false) private int attempts;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "published_at") private Instant publishedAt;

    protected OutboxEvent() {}
    public OutboxEvent(UUID aggregateId, String eventType, String payload) {
        this.id = UUID.randomUUID(); this.aggregateType = "payment"; this.aggregateId = aggregateId;
        this.eventType = eventType; this.payload = payload; this.status = Status.PENDING;
        this.createdAt = Instant.now();
    }
    public void published() { status = Status.PUBLISHED; publishedAt = Instant.now(); attempts++; }
    public void failed() { attempts++; if (attempts >= 10) status = Status.FAILED; }
    public UUID getId() { return id; }
    public UUID getAggregateId() { return aggregateId; }
    public String getEventType() { return eventType; }
    public String getPayload() { return payload; }
    public Status getStatus() { return status; }
    public int getAttempts() { return attempts; }
}
