package io.github.aeginventor.ledgerguard.draft;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.validation.ValidationContextFactory;
import io.github.aeginventor.ledgerguard.validation.ValidationEngine;
import io.github.aeginventor.ledgerguard.validation.ValidationMetrics;
import io.github.aeginventor.ledgerguard.validation.Stage;
import io.github.aeginventor.ledgerguard.validation.ValidationResult;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftLineResponse;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftResponse;
import io.github.aeginventor.ledgerguard.voucher.dto.ValidationResultResponse;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 초안 생성과 초안 단계 검증. 저장하지 않는다. */
@Service
public class DraftService {

    private final List<JournalDrafter> drafters;
    private final String preferred;
    private final AccountRepository accountRepository;
    private final ValidationContextFactory contextFactory;
    private final ValidationEngine engine;
    private final ValidationMetrics metrics;

    public DraftService(List<JournalDrafter> drafters,
            @Value("${ledger-guard.drafter:fixture}") String preferred,
            AccountRepository accountRepository,
            ValidationContextFactory contextFactory,
            ValidationEngine engine,
            ValidationMetrics metrics) {
        this.drafters = drafters;
        this.preferred = preferred;
        this.accountRepository = accountRepository;
        this.contextFactory = contextFactory;
        this.engine = engine;
        this.metrics = metrics;
    }

    public JournalDrafter activeDrafter() {
        return drafters.stream()
                .filter(d -> d.name().equalsIgnoreCase(preferred))
                .findFirst()
                .orElseGet(() -> drafters.stream()
                        .filter(d -> FixtureJournalDrafter.NAME.equals(d.name()))
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("초안 작성기가 없다")));
    }

    @Transactional(readOnly = true)
    public DraftResponse draft(DraftRequest request) {
        List<Account> accounts = accountRepository.findAllByOrderByCodeAsc();
        Map<String, String> names = accounts.stream().collect(Collectors.toMap(Account::getCode, Account::getName));
        JournalDrafter drafter = activeDrafter();
        DraftResult result = drafter.draft(request, accounts);

        VoucherData data = new VoucherData(request.voucherDate(), request.description(), request.counterpartyName(),
                request.counterpartyBizNo(), request.evidenceType(), request.vatType(), request.supplyAmount(),
                result.lines());
        ValidationResult validation = engine.evaluate(contextFactory.forDraft(data, result.problems()));
        metrics.record(Stage.DRAFT, validation);

        Function<String, String> nameOf = code -> code == null ? null : names.get(code);
        List<DraftLineResponse> lines = result.lines().stream()
                .map(line -> DraftLineResponse.from(line, nameOf.apply(line.accountCode())))
                .toList();
        return new DraftResponse(new DraftResponse.Draft(lines), drafter.name(), result.note(),
                ValidationResultResponse.from(validation));
    }
}
