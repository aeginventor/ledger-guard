package io.github.aeginventor.ledgerguard.validation;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 규칙 엔진 입력. JPA 엔티티와 분리해 규칙을 순수 단위 테스트로 검증할 수 있게 한다.
 */
public record VoucherData(
        LocalDate voucherDate,
        String description,
        String counterpartyName,
        String counterpartyBizNo,
        EvidenceType evidenceType,
        VatType vatType,
        Long supplyAmount,
        List<LineData> lines,
        String preparer,
        Long reversalOfId) {

    public VoucherData {
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    /** 작성자와 역분개 참조가 없는 전표. 규칙 단위 테스트와 초안 단계에서 쓴다. */
    public VoucherData(LocalDate voucherDate, String description, String counterpartyName, String counterpartyBizNo,
            EvidenceType evidenceType, VatType vatType, Long supplyAmount, List<LineData> lines) {
        this(voucherDate, description, counterpartyName, counterpartyBizNo, evidenceType, vatType, supplyAmount, lines,
                null, null);
    }

    public boolean isReversal() {
        return reversalOfId != null;
    }

    public BigDecimal sum(Side side) {
        return lines.stream()
                .filter(line -> line.side() == side)
                .map(LineData::amountOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
