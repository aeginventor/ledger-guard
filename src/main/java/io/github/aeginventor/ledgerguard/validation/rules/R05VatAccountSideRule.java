package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LedgerAccounts;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R05 매입 거래의 부가세는 부가세대급금 차변, 매출 거래의 부가세는 부가세예수금 대변이어야 한다.
 * 수익 계정 라인이 하나라도 있으면 매출 거래, 아니면 매입 거래로 본다.
 */
@Component
public class R05VatAccountSideRule implements ValidationRule {

    public static final String CODE = "R05";
    private static final String BASIS = "부가가치세법 제37조 납부세액 계산(매출세액과 매입세액의 구분)(확인 필요)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "VAT_ACCOUNT_SIDE";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        List<LineData> vatLines = RuleSupport.vatLines(context);
        if (vatLines.isEmpty()) {
            return List.of();
        }
        boolean sales = RuleSupport.hasRevenueLine(context);
        String expectedCode = sales ? LedgerAccounts.VAT_OUTPUT : LedgerAccounts.VAT_INPUT;
        Side expectedSide = sales ? Side.CREDIT : Side.DEBIT;
        String expectedName = sales ? "부가세예수금 대변" : "부가세대급금 차변";
        String nature = sales ? "매출" : "매입";

        List<Finding> findings = new ArrayList<>();
        for (LineData line : vatLines) {
            boolean codeOk = expectedCode.equals(line.accountCode());
            boolean sideOk = line.side() == expectedSide;
            if (!codeOk || !sideOk) {
                findings.add(Finding.fail(CODE,
                        nature + " 거래의 부가세는 " + expectedName + "이어야 하는데 " + line.lineNo() + "번 라인은 계정 "
                                + line.accountCode() + " " + line.side() + "이다",
                        BASIS));
            }
        }
        return findings;
    }
}
