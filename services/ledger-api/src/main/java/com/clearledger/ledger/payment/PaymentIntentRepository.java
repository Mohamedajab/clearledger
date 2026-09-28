package com.clearledger.ledger.payment;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    Optional<PaymentIntent> findByTenantIdAndIdempotencyKey(String tenantId, String idempotencyKey);
    List<PaymentIntent> findByTenantIdOrderByCreatedAtDesc(String tenantId, Pageable pageable);
    long countByTenantIdAndStatus(String tenantId, PaymentIntent.Status status);
    @Query("select coalesce(sum(p.amountMinor), 0) from PaymentIntent p where p.tenantId = :tenantId and p.status = com.clearledger.ledger.payment.PaymentIntent.Status.COMPLETED")
    long completedVolume(@Param("tenantId") String tenantId);
}
