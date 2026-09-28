package com.clearledger.ledger.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, UUID> {
    @EntityGraph(attributePaths = "postings")
    List<JournalEntry> findByTenantIdOrderByOccurredAtDesc(String tenantId, Pageable pageable);
}
