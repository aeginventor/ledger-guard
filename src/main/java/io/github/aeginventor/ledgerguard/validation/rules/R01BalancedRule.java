package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** R01 차변 합계와 대변 합계가 같아야 한다. */
@Component
public class R01BalancedRule implements ValidationRule {

    public static final String CODE = "R01";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "BALANCED";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        BigDecimal debit = context.voucher().sum(Side.DEBIT);
        BigDecimal credit = context.voucher().sum(Side.CREDIT);
        if (debit.compareTo(credit) == 0) {
            return List.of();
        }
        return List.of(Finding.fail(CODE,
                "차변 합계 " + RuleSupport.won(debit) + "과 대변 합계 " + RuleSupport.won(credit) + "이 다르다",
                "복식부기 대차평균의 원리"));
    }
}
