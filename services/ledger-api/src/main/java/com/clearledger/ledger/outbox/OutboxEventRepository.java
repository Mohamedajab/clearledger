package com.clearledger.ledger.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxEvent.Status status, Pageable pageable);
    @Query("select count(e) from OutboxEvent e where e.status = :status and e.aggregateId in (select p.id from PaymentIntent p where p.tenantId = :tenantId)")
    long countForTenant(@Param("tenantId") String tenantId, @Param("status") OutboxEvent.Status status);
}
