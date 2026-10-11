package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.Stage;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R11 초안 단계에서만 적용. 초안 JSON이 스키마(라인 2개 이상, 라인마다 계정코드와 차대와 금액)를 지키지 못하면 FAIL.
 * 초안 작성기가 JSON 파싱 실패 같은 문제를 draftProblems로 넘기면 그대로 FAIL 사유가 된다.
 */
@Component
public class R11DraftSchemaRule implements ValidationRule {

    public static final String CODE = "R11";
    private static final String BASIS = "초안 스키마: lines >= 2, line.accountCode, line.side, line.amount 필수";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "DRAFT_SCHEMA";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        if (context.stage() != Stage.DRAFT) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        for (String problem : context.draftProblems()) {
            findings.add(Finding.fail(CODE, "초안 생성 실패: " + problem, BASIS));
        }
        VoucherData voucher = context.voucher();
        if (voucher.lines().size() < 2) {
            findings.add(Finding.fail(CODE, "초안 라인이 " + voucher.lines().size() + "개다. 2개 이상이어야 한다", BASIS));
        }
        for (LineData line : voucher.lines()) {
            List<String> missing = new ArrayList<>();
            if (line.accountCode() == null || line.accountCode().isBlank()) {
                missing.add("accountCode");
            }
            if (line.side() == null) {
                missing.add("side");
            }
            if (line.amount() == null) {
                missing.add("amount");
            }
            if (!missing.isEmpty()) {
                findings.add(Finding.fail(CODE, line.lineNo() + "번 라인에 필수 필드 " + String.join(", ", missing) + "이 없다",
                        BASIS));
            }
        }
        return findings;
    }
}
