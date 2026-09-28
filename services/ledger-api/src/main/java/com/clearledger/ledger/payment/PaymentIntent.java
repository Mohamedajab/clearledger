package com.clearledger.ledger.payment;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_intents", uniqueConstraints = {
    @UniqueConstraint(name = "uq_payment_idempotency", columnNames = {"tenant_id", "idempotency_key"}),
    @UniqueConstraint(name = "uq_payment_reference", columnNames = {"tenant_id", "merchant_reference"})
})
public class PaymentIntent {
    public enum Status { PROCESSING, COMPLETED, FAILED }

    @Id private UUID id;
    @Column(name = "tenant_id", nullable = false) private String tenantId;
    @Column(name = "merchant_reference", nullable = false) private String merchantReference;
    @Column(name = "idempotency_key", nullable = false) private String idempotencyKey;
    @Column(name = "source_account_id", nullable = false) private UUID sourceAccountId;
    @Column(name = "destination_account_id", nullable = false) private UUID destinationAccountId;
    @Column(name = "amount_minor", nullable = false) private long amountMinor;
    @Column(nullable = false, length = 3) private String currency;
    @Enumerated(EnumType.STRING) @Column(nullable = false) private Status status;
    @Column(name = "failure_code") private String failureCode;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "completed_at") private Instant completedAt;

    protected PaymentIntent() {}

    public static PaymentIntent completed(UUID id, String tenantId, String reference, String idempotencyKey,
                                          UUID sourceId, UUID destinationId, long amountMinor, String currency) {
        PaymentIntent payment = new PaymentIntent();
        payment.id = id; payment.tenantId = tenantId; payment.merchantReference = reference;
        payment.idempotencyKey = idempotencyKey; payment.sourceAccountId = sourceId;
        payment.destinationAccountId = destinationId; payment.amountMinor = amountMinor;
        payment.currency = currency; payment.status = Status.COMPLETED;
        payment.createdAt = Instant.now(); payment.completedAt = payment.createdAt;
        return payment;
    }

    public UUID getId() { return id; }
    public String getTenantId() { return tenantId; }
    public String getMerchantReference() { return merchantReference; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public UUID getSourceAccountId() { return sourceAccountId; }
    public UUID getDestinationAccountId() { return destinationAccountId; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }
}
