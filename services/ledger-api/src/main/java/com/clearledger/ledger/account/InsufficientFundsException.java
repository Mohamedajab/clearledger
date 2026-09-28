package com.clearledger.ledger.account;

import java.util.UUID;

public final class InsufficientFundsException extends RuntimeException {
    private final UUID accountId;
    private final long available;
    private final long requested;

    public InsufficientFundsException(UUID accountId, long available, long requested) {
        super("Account has insufficient funds");
        this.accountId = accountId; this.available = available; this.requested = requested;
    }
    public UUID getAccountId() { return accountId; }
    public long getAvailable() { return available; }
    public long getRequested() { return requested; }
}
