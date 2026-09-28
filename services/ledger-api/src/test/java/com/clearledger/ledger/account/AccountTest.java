package com.clearledger.ledger.account;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AccountTest {
    @Test
    void appliesCreditsAndDebitsInMinorUnits() {
        Account account = account();
        account.credit(10_000);
        account.debit(2_750);
        assertThat(account.getBalanceMinor()).isEqualTo(7_250);
    }

    @Test
    void rejectsOverdraftsWithoutMutatingBalance() {
        Account account = account();
        account.credit(500);
        assertThatThrownBy(() -> account.debit(501))
            .isInstanceOf(InsufficientFundsException.class);
        assertThat(account.getBalanceMinor()).isEqualTo(500);
    }

    @Test
    void rejectsNonPositiveAmounts() {
        assertThatThrownBy(() -> account().credit(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private Account account() {
        return new Account(UUID.randomUUID(), "tenant", "wallet", "Wallet", "GBP", Account.Type.WALLET);
    }
}
