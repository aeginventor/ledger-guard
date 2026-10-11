package io.github.aeginventor.ledgerguard.report;

import io.github.aeginventor.ledgerguard.account.AccountType;
import io.github.aeginventor.ledgerguard.account.Side;
import java.util.List;

public record TrialBalanceResponse(
        String month,
        List<Row> rows,
        long totalDebit,
        long totalCredit,
        boolean balanced) {

    /** balance는 차변합에서 대변합을 뺀 값. 양수면 차변 잔액, 음수면 대변 잔액이다. */
    public record Row(String accountCode, String accountName, AccountType type, long debitTotal, long creditTotal,
            long balance, Side balanceSide) {
    }
}
