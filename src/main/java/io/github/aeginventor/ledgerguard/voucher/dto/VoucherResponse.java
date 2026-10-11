package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.voucher.DrafterType;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import io.github.aeginventor.ledgerguard.voucher.Voucher;
import io.github.aeginventor.ledgerguard.voucher.VoucherStatus;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Function;

public record VoucherResponse(
        Long id,
        LocalDate voucherDate,
        String description,
        String counterpartyName,
        String counterpartyBizNo,
        EvidenceType evidenceType,
        VatType vatType,
        Long supplyAmount,
        VoucherStatus status,
        DrafterType drafter,
        List<VoucherLineResponse> lines,
        ValidationResultResponse validation,
        String approver,
        String approvalReason,
        LocalDateTime createdAt,
        LocalDateTime approvedAt) {

    public static VoucherResponse from(Voucher voucher, Function<String, String> accountName) {
        return new VoucherResponse(
                voucher.getId(),
                voucher.getVoucherDate(),
                voucher.getDescription(),
                voucher.getCounterpartyName(),
                voucher.getCounterpartyBizNo(),
                voucher.getEvidenceType(),
                voucher.getVatType(),
                voucher.getSupplyAmount(),
                voucher.getStatus(),
                voucher.getDrafter(),
                voucher.getLines().stream()
                        .map(line -> VoucherLineResponse.from(line, accountName.apply(line.getAccountCode())))
                        .toList(),
                ValidationResultResponse.from(voucher),
                voucher.getApprover(),
                voucher.getApprovalReason(),
                voucher.getCreatedAt(),
                voucher.getApprovedAt());
    }
}
