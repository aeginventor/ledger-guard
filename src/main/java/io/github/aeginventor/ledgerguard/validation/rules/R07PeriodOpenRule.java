package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.springframework.stereotype.Component;

/** R07 전표 일자가 속한 월이 마감되어 있으면 전기할 수 없다. */
@Component
public class R07PeriodOpenRule implements ValidationRule {

    public static final String CODE = "R07";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "PERIOD_OPEN";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        LocalDate date = context.voucher().voucherDate();
        if (date == null) {
            return List.of();
        }
        YearMonth month = YearMonth.from(date);
        if (context.periodClosed().test(month)) {
            return List.of(Finding.fail(CODE, month + " 기간은 이미 마감되었다", "회계기간 마감(accounting_period.closed)"));
        }
        return List.of();
    }
}
