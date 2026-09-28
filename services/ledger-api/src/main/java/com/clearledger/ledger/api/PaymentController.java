package com.clearledger.ledger.api;

import com.clearledger.ledger.payment.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/payments")
@Validated
public class PaymentController {
    private final PaymentService service;
    public PaymentController(PaymentService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<PaymentView> create(
        @RequestHeader("X-Tenant-Id") String tenantId,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @Valid @RequestBody CreatePaymentCommand command) {
        PaymentView payment = service.create(tenantId, idempotencyKey, command);
        return ResponseEntity.created(URI.create("/api/v1/payments/" + payment.id())).body(payment);
    }

    @GetMapping
    public List<PaymentView> list(@RequestHeader("X-Tenant-Id") String tenantId,
                                  @RequestParam(defaultValue = "50") @Min(1) @Max(100) int limit) {
        return service.list(tenantId, limit);
    }
}
