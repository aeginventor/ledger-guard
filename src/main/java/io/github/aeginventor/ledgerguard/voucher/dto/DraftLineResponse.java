package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import java.math.BigDecimal;

public record DraftLineResponse(int lineNo, String accountCode, String accountName, Side side, BigDecimal amount,
        String memo) {

    public static DraftLineResponse from(LineData line, String accountName) {
        return new DraftLineResponse(line.lineNo(), line.accountCode(), accountName, line.side(), line.amount(),
                line.memo());
    }
}
