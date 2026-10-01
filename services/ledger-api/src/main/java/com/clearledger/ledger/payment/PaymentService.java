package com.clearledger.ledger.payment;

import com.clearledger.ledger.account.Account;
import com.clearledger.ledger.account.AccountRepository;
import com.clearledger.ledger.ledger.JournalEntry;
import com.clearledger.ledger.ledger.JournalEntryRepository;
import com.clearledger.ledger.ledger.Posting;
import com.clearledger.ledger.outbox.OutboxEvent;
import com.clearledger.ledger.outbox.OutboxEventRepository;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class PaymentService {
    private final AccountRepository accounts;
    private final PaymentIntentRepository payments;
    private final JournalEntryRepository entries;
    private final OutboxEventRepository outbox;
    private final ObjectMapper json;

    public PaymentService(AccountRepository accounts, PaymentIntentRepository payments,
                          JournalEntryRepository entries, OutboxEventRepository outbox, ObjectMapper json) {
        this.accounts = accounts; this.payments = payments; this.entries = entries; this.outbox = outbox; this.json = json;
    }

    @Transactional
    public PaymentView create(String tenantId, String idempotencyKey, CreatePaymentCommand command) {
        Optional<PaymentIntent> replay = payments.findByTenantIdAndIdempotencyKey(tenantId, idempotencyKey);
        if (replay.isPresent()) {
            PaymentIntent previous = replay.get();
            if (!sameRequest(previous, command)) throw new IdempotencyConflictException();
            return PaymentView.from(previous);
        }
        if (command.sourceAccountId().equals(command.destinationAccountId())) {
            throw new IllegalArgumentException("Source and destination accounts must differ");
        }

        List<UUID> lockOrder = new ArrayList<>(List.of(command.sourceAccountId(), command.destinationAccountId()));
        lockOrder.sort(Comparator.naturalOrder());
        Map<UUID, Account> locked = new HashMap<>();
        for (UUID id : lockOrder) {
            Account account = accounts.lockByIdAndTenantId(id, tenantId)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + id));
            locked.put(id, account);
        }
        Account source = locked.get(command.sourceAccountId());
        Account destination = locked.get(command.destinationAccountId());
        String currency = command.currency().toUpperCase(Locale.ROOT);
        if (!source.getCurrency().equals(currency) || !destination.getCurrency().equals(currency)) {
            throw new IllegalArgumentException("Payment currency must match both accounts");
        }

        source.debit(command.amountMinor());
        destination.credit(command.amountMinor());
        UUID paymentId = UUID.randomUUID();
        PaymentIntent payment = PaymentIntent.completed(paymentId, tenantId, command.merchantReference(),
            idempotencyKey, source.getId(), destination.getId(), command.amountMinor(), currency);
        payments.save(payment);

        JournalEntry entry = new JournalEntry(UUID.randomUUID(), tenantId, paymentId,
            "PAY-" + paymentId, command.description() == null || command.description().isBlank() ? "Account transfer" : command.description());
        entry.addPosting(source.getId(), Posting.Direction.DEBIT, command.amountMinor(), currency);
        entry.addPosting(destination.getId(), Posting.Direction.CREDIT, command.amountMinor(), currency);
        entries.save(entry);
        entry.post();
        entries.flush();

        outbox.save(new OutboxEvent(paymentId, "payment.completed.v1", eventPayload(payment)));
        return PaymentView.from(payment);
    }

    @Transactional(readOnly = true)
    public List<PaymentView> list(String tenantId, int limit) {
        return payments.findByTenantIdOrderByCreatedAtDesc(tenantId, PageRequest.of(0, Math.min(limit, 100)))
            .stream().map(PaymentView::from).toList();
    }

    private boolean sameRequest(PaymentIntent payment, CreatePaymentCommand command) {
        return payment.getMerchantReference().equals(command.merchantReference())
            && payment.getSourceAccountId().equals(command.sourceAccountId())
            && payment.getDestinationAccountId().equals(command.destinationAccountId())
            && payment.getAmountMinor() == command.amountMinor()
            && payment.getCurrency().equalsIgnoreCase(command.currency());
    }

    private String eventPayload(PaymentIntent payment) {
        try {
            return json.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID(), "eventType", "payment.completed.v1",
                "paymentId", payment.getId(), "tenantId", payment.getTenantId(),
                "amountMinor", payment.getAmountMinor(), "currency", payment.getCurrency(),
                "occurredAt", payment.getCompletedAt().toString()));
        } catch (JacksonException error) {
            throw new IllegalStateException("Could not serialize outbox event", error);
        }
    }
}
