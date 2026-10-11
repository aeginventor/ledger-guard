package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.DrafterType;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.IntStream;

public record VoucherRequest(
        @NotNull LocalDate voucherDate,
        @NotBlank @Size(max = 500) String description,
        @Size(max = 200) String counterpartyName,
        @Size(max = 20) String counterpartyBizNo,
        @NotNull EvidenceType evidenceType,
        @NotNull VatType vatType,
        @PositiveOrZero Long supplyAmount,
        DrafterType drafter,
        @NotEmpty @Valid List<VoucherLineRequest> lines) {

    public DrafterType drafterOrManual() {
        return drafter == null ? DrafterType.MANUAL : drafter;
    }

    public VoucherData toVoucherData() {
        List<LineData> lineData = IntStream.range(0, lines.size())
                .mapToObj(i -> lines.get(i).toLineData(i + 1))
                .toList();
        return new VoucherData(voucherDate, description, counterpartyName, counterpartyBizNo, evidenceType, vatType,
                supplyAmount, lineData);
    }
}
