package com.clearledger.ledger.payment;

import java.time.Instant;
import java.util.UUID;

public record PaymentView(UUID id, String merchantReference, UUID sourceAccountId, UUID destinationAccountId,
                          long amountMinor, String currency, String status, Instant createdAt, Instant completedAt) {
    public static PaymentView from(PaymentIntent payment) {
        return new PaymentView(payment.getId(), payment.getMerchantReference(), payment.getSourceAccountId(),
            payment.getDestinationAccountId(), payment.getAmountMinor(), payment.getCurrency(),
            payment.getStatus().name(), payment.getCreatedAt(), payment.getCompletedAt());
    }
}
