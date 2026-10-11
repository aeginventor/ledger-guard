package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Component;

/** R08 전표 일자는 오늘보다 미래일 수 없다. */
@Component
public class R08DateNotFutureRule implements ValidationRule {

    public static final String CODE = "R08";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "DATE_NOT_FUTURE";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        LocalDate date = context.voucher().voucherDate();
        if (date == null) {
            return List.of(Finding.fail(CODE, "전표 일자가 없다", "발생주의 회계: 거래일 기준 기록"));
        }
        if (date.isAfter(context.today())) {
            return List.of(Finding.fail(CODE, "전표 일자 " + date + "이 오늘(" + context.today() + ")보다 미래다",
                    "발생주의 회계: 아직 일어나지 않은 거래는 기록하지 않는다"));
        }
        return List.of();
    }
}
