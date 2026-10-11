package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/** R10 같은 일자, 같은 거래처, 같은 총액(차변 합계)의 전표가 이미 있으면 중복 의심으로 검토를 요청한다. */
@Component
public class R10DuplicateSuspectRule implements ValidationRule {

    public static final String CODE = "R10";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "DUPLICATE_SUSPECT";
    }

    @Override
    public Severity severity() {
        return Severity.REVIEW;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        VoucherData voucher = context.voucher();
        if (voucher.voucherDate() == null || voucher.counterpartyName() == null || voucher.counterpartyName().isBlank()) {
            return List.of();
        }
        BigDecimal total = voucher.sum(Side.DEBIT);
        if (context.duplicateChecker().exists(voucher.voucherDate(), voucher.counterpartyName(), total)) {
            return List.of(Finding.review(CODE,
                    voucher.voucherDate() + " " + voucher.counterpartyName() + " " + RuleSupport.won(total)
                            + " 전표가 이미 있다. 이중 기표가 아닌지 확인이 필요하다",
                    "같은 일자, 거래처, 총액의 기존 전표"));
        }
        return List.of();
    }
}
