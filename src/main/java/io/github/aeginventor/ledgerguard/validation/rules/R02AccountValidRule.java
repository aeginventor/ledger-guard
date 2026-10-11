package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/** R02 모든 라인의 계정코드가 계정과목표에 있고 사용 중이어야 한다. */
@Component
public class R02AccountValidRule implements ValidationRule {

    public static final String CODE = "R02";
    private static final String BASIS = "계정과목표(account 테이블)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "ACCOUNT_VALID";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        List<Finding> findings = new ArrayList<>();
        for (LineData line : context.voucher().lines()) {
            if (line.accountCode() == null || line.accountCode().isBlank()) {
                findings.add(Finding.fail(CODE, line.lineNo() + "번 라인에 계정코드가 없다", BASIS));
                continue;
            }
            Optional<Account> account = context.account(line.accountCode());
            if (account.isEmpty()) {
                findings.add(Finding.fail(CODE,
                        line.lineNo() + "번 라인의 계정코드 " + line.accountCode() + "이 계정과목표에 없다", BASIS));
            } else if (!account.get().isActive()) {
                findings.add(Finding.fail(CODE,
                        line.lineNo() + "번 라인의 계정 " + line.accountCode() + " " + account.get().getName()
                                + "은 사용 중지된 계정이다", BASIS));
            }
        }
        return findings;
    }
}
