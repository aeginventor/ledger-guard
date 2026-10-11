package io.github.aeginventor.ledgerguard.validation;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.period.AccountingPeriodRepository;
import io.github.aeginventor.ledgerguard.period.PeriodKey;
import io.github.aeginventor.ledgerguard.voucher.Voucher;
import io.github.aeginventor.ledgerguard.voucher.VoucherRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** DB 조회를 함수로 감싸 규칙 엔진에 넘길 컨텍스트를 만든다. */
@Component
public class ValidationContextFactory {

    private final AccountRepository accountRepository;
    private final AccountingPeriodRepository periodRepository;
    private final VoucherRepository voucherRepository;
    private final Clock clock;

    public ValidationContextFactory(AccountRepository accountRepository, AccountingPeriodRepository periodRepository,
            VoucherRepository voucherRepository, Clock clock) {
        this.accountRepository = accountRepository;
        this.periodRepository = periodRepository;
        this.voucherRepository = voucherRepository;
        this.clock = clock;
    }

    public ValidationContext create(VoucherData voucher, Stage stage, List<String> draftProblems, Long excludeVoucherId) {
        Map<String, Account> accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getCode, Function.identity()));
        Function<String, Optional<Account>> lookup = code -> Optional.ofNullable(accounts.get(code));
        DuplicateChecker duplicateChecker = (date, counterpartyName, total) ->
                voucherRepository.findDuplicateCandidates(date, counterpartyName, excludeVoucherId).stream()
                        .anyMatch(existing -> BigDecimal.valueOf(existing.totalDebit()).compareTo(total) == 0);
        return new ValidationContext(
                voucher,
                lookup,
                month -> periodRepository.findById(PeriodKey.of(month)).map(p -> p.isClosed()).orElse(false),
                duplicateChecker,
                LocalDate.now(clock),
                stage,
                draftProblems);
    }

    public ValidationContext forSave(VoucherData voucher) {
        return create(voucher, Stage.SAVE, List.of(), null);
    }

    public ValidationContext forApproval(VoucherData voucher, Voucher self) {
        return create(voucher, Stage.SAVE, List.of(), self.getId());
    }

    public ValidationContext forDraft(VoucherData voucher, List<String> draftProblems) {
        return create(voucher, Stage.DRAFT, draftProblems, null);
    }
}
