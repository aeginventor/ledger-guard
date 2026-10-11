package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LedgerAccounts;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R09 기업업무추진비(구 접대비) 차변 합계가 30,000원을 넘는데 적격증빙(세금계산서, 카드, 현금영수증)이 없으면 검토가 필요하다.
 */
@Component
public class R09EntertainmentEvidenceRule implements ValidationRule {

    public static final String CODE = "R09";
    public static final long THRESHOLD = 30_000L;
    private static final String BASIS =
            "법인세법 제25조 제2항, 같은 법 시행령 제41조(3만원 초과 기업업무추진비의 적격증빙)(확인 필요)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "ENTERTAINMENT_EVIDENCE";
    }

    @Override
    public Severity severity() {
        return Severity.REVIEW;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        BigDecimal total = context.voucher().lines().stream()
                .filter(line -> LedgerAccounts.ENTERTAINMENT.equals(line.accountCode()) && line.side() == Side.DEBIT)
                .map(LineData::amountOrZero)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.compareTo(BigDecimal.valueOf(THRESHOLD)) <= 0) {
            return List.of();
        }
        if (context.voucher().evidenceType() == EvidenceType.NONE) {
            return List.of(Finding.review(CODE,
                    "기업업무추진비 " + RuleSupport.won(total) + "은 3만원을 넘는데 적격증빙이 없다", BASIS));
        }
        return List.of();
    }
}
