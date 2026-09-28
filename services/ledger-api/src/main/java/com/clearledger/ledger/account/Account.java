package com.clearledger.ledger.account;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "accounts", uniqueConstraints = @UniqueConstraint(name = "uq_accounts_tenant_external", columnNames = {"tenant_id", "external_ref"}))
public class Account {
    public enum Type { WALLET, MERCHANT, FEE, CLEARING }
    public enum Status { ACTIVE, FROZEN, CLOSED }

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false, length = 64) private String tenantId;
    @Column(name = "external_ref", nullable = false, length = 96) private String externalRef;
    @Column(nullable = false, length = 140) private String name;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(name = "account_type", nullable = false) private Type type;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "balance_minor", nullable = false) private long balanceMinor;
    @Version private long version;
    @Column(name = "created_at", nullable = false) private Instant createdAt;

    protected Account() {}

    public Account(UUID id, String tenantId, String externalRef, String name, String currency, Type type) {
        this.id = id; this.tenantId = tenantId; this.externalRef = externalRef; this.name = name;
        this.currency = currency; this.type = type; this.status = Status.ACTIVE; this.createdAt = Instant.now();
    }

    public void debit(long amountMinor) {
        requireActive();
        if (amountMinor <= 0) throw new IllegalArgumentException("Debit must be positive");
        if (balanceMinor < amountMinor) throw new InsufficientFundsException(id, balanceMinor, amountMinor);
        balanceMinor -= amountMinor;
    }

    public void credit(long amountMinor) {
        requireActive();
        if (amountMinor <= 0) throw new IllegalArgumentException("Credit must be positive");
        balanceMinor = Math.addExact(balanceMinor, amountMinor);
    }

    private void requireActive() {
        if (status != Status.ACTIVE) throw new AccountUnavailableException(id, status.name());
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getExternalRef() { return externalRef; }
    public String getName() { return name; }
    public String getCurrency() { return currency; }
    public Type getType() { return type; }
    public Status getStatus() { return status; }
    public long getBalanceMinor() { return balanceMinor; }
    public long getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
}
