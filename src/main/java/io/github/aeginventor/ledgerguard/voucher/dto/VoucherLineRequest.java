package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** 금액은 BigDecimal로 받아 소수나 음수도 규칙 엔진(R03)이 사유와 함께 거부하게 한다. */
public record VoucherLineRequest(
        @NotBlank @Size(max = 10) String accountCode,
        @NotNull Side side,
        @NotNull BigDecimal amount,
        @Size(max = 300) String memo) {

    public LineData toLineData(int lineNo) {
        return new LineData(lineNo, accountCode, side, amount, memo);
    }
}
