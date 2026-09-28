package com.clearledger.ledger.ledger;

import static org.assertj.core.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JournalEntryTest {
    @Test
    void postsOnlyWhenDebitsEqualCredits() {
        JournalEntry entry = entry();
        entry.addPosting(UUID.randomUUID(), Posting.Direction.DEBIT, 1_250, "GBP");
        entry.addPosting(UUID.randomUUID(), Posting.Direction.CREDIT, 1_250, "GBP");
        entry.post();
        assertThat(entry.getStatus()).isEqualTo(JournalEntry.Status.POSTED);
        assertThat(entry.getPostings()).hasSize(2);
    }

    @Test
    void refusesAnUnbalancedJournal() {
        JournalEntry entry = entry();
        entry.addPosting(UUID.randomUUID(), Posting.Direction.DEBIT, 1_250, "GBP");
        entry.addPosting(UUID.randomUUID(), Posting.Direction.CREDIT, 1_200, "GBP");
        assertThatThrownBy(entry::post).isInstanceOf(UnbalancedEntryException.class);
        assertThat(entry.getStatus()).isEqualTo(JournalEntry.Status.DRAFT);
    }

    @Test
    void postedEntriesAreImmutable() {
        JournalEntry entry = entry();
        entry.addPosting(UUID.randomUUID(), Posting.Direction.DEBIT, 100, "GBP");
        entry.addPosting(UUID.randomUUID(), Posting.Direction.CREDIT, 100, "GBP");
        entry.post();
        assertThatThrownBy(() -> entry.addPosting(UUID.randomUUID(), Posting.Direction.DEBIT, 1, "GBP"))
            .isInstanceOf(IllegalStateException.class);
    }

    private JournalEntry entry() {
        return new JournalEntry(UUID.randomUUID(), "tenant", UUID.randomUUID(), "PAY-1", "Transfer");
    }
}
