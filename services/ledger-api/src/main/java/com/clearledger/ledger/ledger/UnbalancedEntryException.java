package com.clearledger.ledger.ledger;

import java.util.UUID;

public final class UnbalancedEntryException extends RuntimeException {
    public UnbalancedEntryException(UUID entryId, long debits, long credits) {
        super("Entry " + entryId + " is unbalanced: debits=" + debits + ", credits=" + credits);
    }
}
