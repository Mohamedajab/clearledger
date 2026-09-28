package com.clearledger.ledger.payment;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.clearledger.ledger.account.*;
import com.clearledger.ledger.ledger.*;
import com.clearledger.ledger.outbox.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {
    @Mock AccountRepository accounts;
    @Mock PaymentIntentRepository payments;
    @Mock JournalEntryRepository entries;
    @Mock OutboxEventRepository outbox;
    PaymentService service;

    UUID sourceId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID destinationId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    Account source;
    Account destination;

    @BeforeEach
    void setUp() {
        service = new PaymentService(accounts, payments, entries, outbox, new ObjectMapper().findAndRegisterModules());
        source = new Account(sourceId, "northstar", "source", "Source", "GBP", Account.Type.WALLET);
        source.credit(10_000);
        destination = new Account(destinationId, "northstar", "destination", "Destination", "GBP", Account.Type.MERCHANT);
        when(accounts.lockByIdAndTenantId(sourceId, "northstar")).thenReturn(Optional.of(source));
        when(accounts.lockByIdAndTenantId(destinationId, "northstar")).thenReturn(Optional.of(destination));
        when(payments.findByTenantIdAndIdempotencyKey("northstar", "key-1")).thenReturn(Optional.empty());
        lenient().when(payments.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(entries.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(outbox.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void transfersFundsPostsBalancedJournalAndWritesOutbox() {
        PaymentView result = service.create("northstar", "key-1",
            new CreatePaymentCommand("ORDER-100", sourceId, destinationId, 2_500, "GBP", "Order settlement"));

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(source.getBalanceMinor()).isEqualTo(7_500);
        assertThat(destination.getBalanceMinor()).isEqualTo(2_500);

        ArgumentCaptor<JournalEntry> journal = ArgumentCaptor.forClass(JournalEntry.class);
        verify(entries).save(journal.capture());
        assertThat(journal.getValue().getStatus()).isEqualTo(JournalEntry.Status.POSTED);
        assertThat(journal.getValue().getPostings()).extracting(Posting::getAmountMinor).containsExactly(2_500L, 2_500L);
        verify(entries).flush();
        verify(outbox).save(any(OutboxEvent.class));
    }

    @Test
    void rejectsCurrencyMismatchBeforeWritingPayment() {
        assertThatThrownBy(() -> service.create("northstar", "key-1",
            new CreatePaymentCommand("ORDER-101", sourceId, destinationId, 500, "USD", "Wrong currency")))
            .isInstanceOf(IllegalArgumentException.class);
        verify(payments, never()).save(any());
    }
}
