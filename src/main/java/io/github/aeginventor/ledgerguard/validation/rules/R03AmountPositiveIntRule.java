package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/** R03 라인 금액은 1원 이상의 정수여야 한다. 0, 음수, 소수는 거부한다. */
@Component
public class R03AmountPositiveIntRule implements ValidationRule {

    public static final String CODE = "R03";
    private static final String BASIS = "전표 금액은 원 단위 정수";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "AMOUNT_POSITIVE_INT";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        List<Finding> findings = new ArrayList<>();
        for (LineData line : context.voucher().lines()) {
            BigDecimal amount = line.amount();
            if (amount == null) {
                findings.add(Finding.fail(CODE, line.lineNo() + "번 라인에 금액이 없다", BASIS));
            } else if (amount.stripTrailingZeros().scale() > 0) {
                findings.add(Finding.fail(CODE,
                        line.lineNo() + "번 라인 금액 " + amount.toPlainString() + "은 원 단위 정수가 아니다", BASIS));
            } else if (amount.signum() <= 0) {
                findings.add(Finding.fail(CODE,
                        line.lineNo() + "번 라인 금액 " + amount.toPlainString() + "은 1원 이상이어야 한다", BASIS));
            }
        }
        return findings;
    }
}
