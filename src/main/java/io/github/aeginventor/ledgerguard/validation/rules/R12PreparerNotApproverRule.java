package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.Stage;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R12 승인 단계에서만 적용. 전표 작성자(preparer)와 승인자(approver)가 같으면 FAIL.
 * 작성과 승인을 다른 사람이 해야 한다는 내부통제의 직무 분리를 코드로 옮긴 것이다.
 * 인증이 없으므로 두 이름은 요청 본문의 문자열이며, 앞뒤 공백과 대소문자는 무시하고 비교한다.
 */
@Component
public class R12PreparerNotApproverRule implements ValidationRule {

    public static final String CODE = "R12";
    private static final String BASIS = "내부통제 직무 분리(전표 작성과 승인의 분리)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "PREPARER_NOT_APPROVER";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        if (context.stage() != Stage.APPROVE) {
            return List.of();
        }
        String preparer = normalize(context.voucher().preparer());
        String approver = normalize(context.approver());
        if (preparer == null || approver == null) {
            return List.of();
        }
        if (preparer.equals(approver)) {
            return List.of(Finding.fail(CODE,
                    "작성자 " + context.voucher().preparer().trim() + "이(가) 자기 전표를 승인할 수 없다", BASIS));
        }
        return List.of();
    }

    private static String normalize(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return name.trim().toLowerCase();
    }
}
