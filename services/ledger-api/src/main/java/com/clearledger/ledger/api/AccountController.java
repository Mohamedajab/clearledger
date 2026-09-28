package com.clearledger.ledger.api;

import com.clearledger.ledger.account.Account;
import com.clearledger.ledger.account.AccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {
    private final AccountRepository accounts;
    public AccountController(AccountRepository accounts) { this.accounts = accounts; }

    @GetMapping
    public List<AccountView> list(@RequestHeader("X-Tenant-Id") String tenantId) {
        return accounts.findAllByTenantIdOrderByCreatedAtAsc(tenantId).stream().map(AccountView::from).toList();
    }

    public record AccountView(UUID id, String externalRef, String name, String currency, String type,
                              String status, long balanceMinor, long version, Instant createdAt) {
        static AccountView from(Account account) {
            return new AccountView(account.getId(), account.getExternalRef(), account.getName(), account.getCurrency(),
                account.getType().name(), account.getStatus().name(), account.getBalanceMinor(), account.getVersion(), account.getCreatedAt());
        }
    }
}
