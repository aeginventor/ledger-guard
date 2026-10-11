package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record DraftRequest(
        @NotBlank @Size(max = 500) String description,
        @NotNull LocalDate voucherDate,
        @Size(max = 200) String counterpartyName,
        @Size(max = 20) String counterpartyBizNo,
        @NotNull EvidenceType evidenceType,
        @NotNull VatType vatType,
        @PositiveOrZero Long supplyAmount) {
}
