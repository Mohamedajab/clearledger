package com.clearledger.ledger.api;

import com.clearledger.ledger.account.AccountRepository;
import com.clearledger.ledger.outbox.OutboxEvent;
import com.clearledger.ledger.outbox.OutboxEventRepository;
import com.clearledger.ledger.payment.PaymentIntent;
import com.clearledger.ledger.payment.PaymentIntentRepository;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    private final AccountRepository accounts;
    private final PaymentIntentRepository payments;
    private final OutboxEventRepository events;

    public DashboardController(AccountRepository accounts, PaymentIntentRepository payments, OutboxEventRepository events) {
        this.accounts = accounts; this.payments = payments; this.events = events;
    }

    @GetMapping
    public DashboardView summary(@RequestHeader("X-Tenant-Id") String tenantId) {
        var tenantAccounts = accounts.findAllByTenantIdOrderByCreatedAtAsc(tenantId);
        return new DashboardView(
            tenantAccounts.stream().mapToLong(account -> account.getBalanceMinor()).sum(),
            payments.completedVolume(tenantId),
            payments.countByTenantIdAndStatus(tenantId, PaymentIntent.Status.COMPLETED),
            events.countByStatus(OutboxEvent.Status.PENDING),
            tenantAccounts.size(),
            Instant.now());
    }

    public record DashboardView(long totalBalanceMinor, long processedVolumeMinor, long completedPayments,
                                long pendingEvents, int accountCount, Instant asOf) {}
}
