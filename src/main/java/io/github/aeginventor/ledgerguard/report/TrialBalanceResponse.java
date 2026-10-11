package io.github.aeginventor.ledgerguard.report;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.aeginventor.ledgerguard.account.AccountType;
import io.github.aeginventor.ledgerguard.account.Side;
import java.util.List;

/**
 * 합계잔액시산표. scope가 month이면 해당 월에 전기된 원장 항목만, ytd이면 그 해 1월 1일부터 해당 월 말까지의 누계다.
 * ytd에서는 행마다 기초 잔액(1월 1일부터 전월 말까지)과 당월 발생액이 함께 나온다.
 */
public record TrialBalanceResponse(
        String month,
        String scope,
        List<Row> rows,
        long totalDebit,
        long totalCredit,
        boolean balanced) {

    /**
     * balance는 차변합에서 대변합을 뺀 값. 양수면 차변 잔액, 음수면 대변 잔액이다.
     * openingBalance, periodDebit, periodCredit은 ytd에서만 채워진다.
     */
    public record Row(String accountCode, String accountName, AccountType type,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long openingBalance,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long periodDebit,
            @JsonInclude(JsonInclude.Include.NON_NULL) Long periodCredit,
            long debitTotal, long creditTotal, long balance, Side balanceSide) {
    }
}
