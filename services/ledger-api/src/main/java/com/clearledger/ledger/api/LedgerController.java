package com.clearledger.ledger.api;

import com.clearledger.ledger.ledger.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ledger")
public class LedgerController {
    private final JournalEntryRepository entries;
    public LedgerController(JournalEntryRepository entries) { this.entries = entries; }

    @GetMapping("/entries")
    public List<EntryView> entries(@RequestHeader("X-Tenant-Id") String tenantId,
                                   @RequestParam(defaultValue = "50") int limit) {
        return entries.findByTenantIdOrderByOccurredAtDesc(tenantId, PageRequest.of(0, Math.min(Math.max(limit, 1), 100)))
            .stream().map(EntryView::from).toList();
    }

    public record PostingView(UUID accountId, String direction, long amountMinor, String currency) {}
    public record EntryView(UUID id, String reference, String type, String description, String status,
                            Instant occurredAt, List<PostingView> postings) {
        static EntryView from(JournalEntry entry) {
            return new EntryView(entry.getId(), entry.getReference(), entry.getEntryType(), entry.getDescription(),
                entry.getStatus().name(), entry.getOccurredAt(), entry.getPostings().stream()
                .map(p -> new PostingView(p.getAccountId(), p.getDirection().name(), p.getAmountMinor(), p.getCurrency())).toList());
        }
    }
}
