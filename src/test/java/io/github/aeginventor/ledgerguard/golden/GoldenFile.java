package io.github.aeginventor.ledgerguard.golden;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** src/test/resources/golden/*.json 의 구조. */
record GoldenFile(Setup setup, List<GoldenCase> cases) {

    record Setup(LocalDate today, List<String> closedPeriods, List<ExistingVoucher> existingVouchers) {
    }

    record ExistingVoucher(LocalDate voucherDate, String counterpartyName, BigDecimal totalDebit) {
    }

    record GoldenCase(
            String id,
            String title,
            String stage,
            List<String> expectedCodes,
            List<String> draftProblems,
            String wrongAccountCode,
            String correctAccountCode,
            String why,
            GoldenVoucher voucher) {
    }

    record GoldenVoucher(
            LocalDate voucherDate,
            String description,
            String counterpartyName,
            String counterpartyBizNo,
            EvidenceType evidenceType,
            VatType vatType,
            Long supplyAmount,
            List<GoldenLine> lines) {
    }

    record GoldenLine(String accountCode, Side side, BigDecimal amount, String memo) {
    }
}
