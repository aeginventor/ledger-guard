package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.voucher.VoucherLine;

public record VoucherLineResponse(int lineNo, String accountCode, String accountName, Side side, long amount,
        String memo) {

    public static VoucherLineResponse from(VoucherLine line, String accountName) {
        return new VoucherLineResponse(line.getLineNo(), line.getAccountCode(), accountName, line.getSide(),
                line.getAmount(), line.getMemo());
    }
}
