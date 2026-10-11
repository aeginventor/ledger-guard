package io.github.aeginventor.ledgerguard.validation;

import io.github.aeginventor.ledgerguard.account.Account;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 규칙이 판정에 필요한 모든 정보. 외부 조회는 함수로 주입해 DB 없이도 규칙을 테스트할 수 있다.
 * approver는 승인 단계(APPROVE)에서만 값이 있다.
 */
public record ValidationContext(
        VoucherData voucher,
        Function<String, Optional<Account>> accountLookup,
        Predicate<YearMonth> periodClosed,
        DuplicateChecker duplicateChecker,
        LocalDate today,
        Stage stage,
        List<String> draftProblems,
        String approver) {

    public ValidationContext {
        draftProblems = draftProblems == null ? List.of() : List.copyOf(draftProblems);
    }

    /** 승인자가 없는 컨텍스트. 초안과 저장 단계에서 쓴다. */
    public ValidationContext(VoucherData voucher, Function<String, Optional<Account>> accountLookup,
            Predicate<YearMonth> periodClosed, DuplicateChecker duplicateChecker, LocalDate today, Stage stage,
            List<String> draftProblems) {
        this(voucher, accountLookup, periodClosed, duplicateChecker, today, stage, draftProblems, null);
    }

    public Optional<Account> account(String code) {
        return code == null ? Optional.empty() : accountLookup.apply(code);
    }
}
