package com.clearledger.ledger.ledger;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "postings")
public class Posting {
    public enum Direction { DEBIT, CREDIT }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "journal_entry_id") private JournalEntry journalEntry;
    @Column(name = "account_id", nullable = false) private UUID accountId;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Direction direction;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Posting() {}
    Posting(JournalEntry entry, UUID accountId, Direction direction, long amountMinor, String currency) {
        this.journalEntry = entry; this.accountId = accountId; this.direction = direction;
        this.amountMinor = amountMinor; this.currency = currency; this.createdAt = Instant.now();
    }
    public Long getId() { return id; }
    public UUID getAccountId() { return accountId; }
    public Direction getDirection() { return direction; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
}
