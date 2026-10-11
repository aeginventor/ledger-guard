package io.github.aeginventor.ledgerguard.draft;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.validation.ValidationContextFactory;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Overall;
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
    private final int maxRevisions;

    public DraftService(List<JournalDrafter> drafters,
            @Value("${ledger-guard.drafter:fixture}") String preferred,
            AccountRepository accountRepository,
            ValidationContextFactory contextFactory,
            ValidationEngine engine,
            ValidationMetrics metrics,
            @Value("${ledger-guard.draft.max-revisions:2}") int maxRevisions) {
        this.drafters = drafters;
        this.preferred = preferred;
        this.accountRepository = accountRepository;
        this.contextFactory = contextFactory;
        this.engine = engine;
        this.metrics = metrics;
        this.maxRevisions = maxRevisions;
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

    /**
     * 초안을 만들고 판정한다. 판정이 FAIL이고 작성기가 고칠 수 있으면 사유를 되먹여 다시 쓰게 한다.
     * 되먹임은 최대 maxRevisions회다. 상한이 있어 루프가 반드시 끝나고, 몇 번 불렀는지는 attempts에 적는다.
     * 마지막 초안이 여전히 FAIL이면 그 결과를 그대로 돌려준다. 판정은 어느 시도에서든 같은 규칙 엔진이 한다.
     */
    @Transactional(readOnly = true)
    public DraftResponse draft(DraftRequest request) {
        List<Account> accounts = accountRepository.findAllByOrderByCodeAsc();
        Map<String, String> names = accounts.stream().collect(Collectors.toMap(Account::getCode, Account::getName));
        JournalDrafter drafter = activeDrafter();
        DraftResult result = drafter.draft(request, accounts);
        ValidationResult validation = evaluate(request, result);
        int attempts = 1;
        while (validation.overall() == Overall.FAIL && attempts <= maxRevisions) {
            List<String> findings = validation.findings().stream().map(Finding::message).toList();
            DraftResult revised = drafter.revise(request, accounts, result, findings);
            if (revised == null) {
                break;
            }
            attempts++;
            result = revised;
            validation = evaluate(request, result);
        }
        metrics.record(Stage.DRAFT, validation);

        Function<String, String> nameOf = code -> code == null ? null : names.get(code);
        List<DraftLineResponse> lines = result.lines().stream()
                .map(line -> DraftLineResponse.from(line, nameOf.apply(line.accountCode())))
                .toList();
        return new DraftResponse(new DraftResponse.Draft(lines), drafter.name(), result.note(), attempts,
                ValidationResultResponse.from(validation));
    }

    private ValidationResult evaluate(DraftRequest request, DraftResult result) {
        VoucherData data = new VoucherData(request.voucherDate(), request.description(), request.counterpartyName(),
                request.counterpartyBizNo(), request.evidenceType(), request.vatType(), request.supplyAmount(),
                result.lines());
        return engine.evaluate(contextFactory.forDraft(data, result.problems()));
    }
}
