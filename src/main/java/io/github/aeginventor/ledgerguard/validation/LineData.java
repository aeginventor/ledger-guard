package io.github.aeginventor.ledgerguard.validation;

import io.github.aeginventor.ledgerguard.account.Side;
import java.math.BigDecimal;

/**
 * 규칙 엔진이 보는 전표 라인. 금액은 소수나 음수가 들어올 수 있으므로 BigDecimal로 받는다(R03이 판정).
 */
public record LineData(int lineNo, String accountCode, Side side, BigDecimal amount, String memo) {

    public static LineData of(int lineNo, String accountCode, Side side, long amount) {
        return new LineData(lineNo, accountCode, side, BigDecimal.valueOf(amount), null);
    }

    public BigDecimal amountOrZero() {
        return amount == null ? BigDecimal.ZERO : amount;
    }
}
