package com.clearledger.ledger.payment;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record CreatePaymentCommand(
    @NotBlank @Size(max = 96) String merchantReference,
    @NotNull UUID sourceAccountId,
    @NotNull UUID destinationAccountId,
    @Positive long amountMinor,
    @NotBlank @Pattern(regexp = "^[A-Z]{3}$") String currency,
    @Size(max = 280) String description
) {}
