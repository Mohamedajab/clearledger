package com.clearledger.ledger.account;

import java.util.UUID;

public final class AccountUnavailableException extends RuntimeException {
    public AccountUnavailableException(UUID accountId, String status) {
        super("Account " + accountId + " cannot transact while " + status);
    }
}
