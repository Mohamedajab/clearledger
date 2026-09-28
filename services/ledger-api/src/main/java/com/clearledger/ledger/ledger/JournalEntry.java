package com.clearledger.ledger.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "journal_entries")
public class JournalEntry {
    public enum Status { DRAFT, POSTED, REVERSED }

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "payment_intent_id") private UUID paymentIntentId;
    @Column(nullable = false) private String reference;
    @Column(name = "entry_type", nullable = false) private String entryType;
    @Column(nullable = false) private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "occurred_at", nullable = false) private Instant occurredAt;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @OneToMany(mappedBy = "journalEntry", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Posting> postings = new ArrayList<>();

    protected JournalEntry() {}

    public JournalEntry(UUID id, String tenantId, UUID paymentIntentId, String reference, String description) {
        this.id = id; this.tenantId = tenantId; this.paymentIntentId = paymentIntentId;
        this.reference = reference; this.description = description; this.entryType = "PAYMENT";
        this.status = Status.DRAFT; this.occurredAt = Instant.now(); this.createdAt = this.occurredAt;
    }

    public void addPosting(UUID accountId, Posting.Direction direction, long amountMinor, String currency) {
        if (status != Status.DRAFT) throw new IllegalStateException("Cannot change a posted entry");
        postings.add(new Posting(this, accountId, direction, amountMinor, currency));
    }

    public void post() {
        long debits = postings.stream().filter(p -> p.getDirection() == Posting.Direction.DEBIT).mapToLong(Posting::getAmountMinor).sum();
        long credits = postings.stream().filter(p -> p.getDirection() == Posting.Direction.CREDIT).mapToLong(Posting::getAmountMinor).sum();
        if (debits == 0 || debits != credits) throw new UnbalancedEntryException(id, debits, credits);
        status = Status.POSTED;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public UUID getPaymentIntentId() { return paymentIntentId; }
    public String getReference() { return reference; }
    public String getEntryType() { return entryType; }
    public String getDescription() { return description; }
    public Status getStatus() { return status; }
    public Instant getOccurredAt() { return occurredAt; }
    public List<Posting> getPostings() { return List.copyOf(postings); }
}
