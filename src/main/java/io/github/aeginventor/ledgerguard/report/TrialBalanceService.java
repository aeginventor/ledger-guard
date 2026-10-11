package io.github.aeginventor.ledgerguard.report;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.ledger.LedgerEntryRepository;
import io.github.aeginventor.ledgerguard.period.PeriodKey;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시산표는 뷰나 캐시 없이 원장을 집계 쿼리로 읽는다.
 * scope=month는 해당 월에 전기된 원장 항목만, scope=ytd는 그 해 1월 1일부터 해당 월 말까지의 누계를 집계한다.
 * 연도를 넘는 이월과 결산 분개는 다루지 않는다.
 */
@Service
public class TrialBalanceService {

    public static final String SCOPE_MONTH = "month";
    public static final String SCOPE_YTD = "ytd";

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    public TrialBalanceService(LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(String month) {
        return trialBalance(month, SCOPE_MONTH);
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(String month, String scope) {
        YearMonth ym = PeriodKey.parse(month);
        String normalizedScope = scope == null || scope.isBlank() ? SCOPE_MONTH : scope.trim().toLowerCase();
        if (!normalizedScope.equals(SCOPE_MONTH) && !normalizedScope.equals(SCOPE_YTD)) {
            throw new IllegalArgumentException("scope는 month 또는 ytd여야 한다: " + scope);
        }
        Map<String, Account> accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getCode, Function.identity()));
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.plusMonths(1).atDay(1);
        Map<String, long[]> period = sums(monthStart, monthEnd);

        if (normalizedScope.equals(SCOPE_MONTH)) {
            return build(ym, SCOPE_MONTH, period, null, accounts);
        }
        LocalDate yearStart = LocalDate.of(ym.getYear(), 1, 1);
        Map<String, long[]> cumulative = sums(yearStart, monthEnd);
        return build(ym, SCOPE_YTD, cumulative, period, accounts);
    }

    /** 계정코드별 [차변합, 대변합]. */
    private Map<String, long[]> sums(LocalDate from, LocalDate to) {
        Map<String, long[]> sums = new TreeMap<>();
        for (Object[] row : ledgerEntryRepository.sumByAccountAndSide(from, to)) {
            String code = (String) row[0];
            Side side = (Side) row[1];
            long amount = ((Number) row[2]).longValue();
            long[] pair = sums.computeIfAbsent(code, k -> new long[2]);
            pair[side == Side.DEBIT ? 0 : 1] += amount;
        }
        return sums;
    }

    private TrialBalanceResponse build(YearMonth ym, String scope, Map<String, long[]> totals,
            Map<String, long[]> period, Map<String, Account> accounts) {
        List<TrialBalanceResponse.Row> rows = new ArrayList<>();
        long totalDebit = 0;
        long totalCredit = 0;
        for (Map.Entry<String, long[]> entry : totals.entrySet()) {
            Account account = accounts.get(entry.getKey());
            long debit = entry.getValue()[0];
            long credit = entry.getValue()[1];
            long balance = debit - credit;
            Side balanceSide = balance > 0 ? Side.DEBIT : balance < 0 ? Side.CREDIT : null;
            Long opening = null;
            Long periodDebit = null;
            Long periodCredit = null;
            if (period != null) {
                long[] p = period.getOrDefault(entry.getKey(), new long[2]);
                periodDebit = p[0];
                periodCredit = p[1];
                opening = balance - (p[0] - p[1]);
            }
            rows.add(new TrialBalanceResponse.Row(entry.getKey(), account == null ? null : account.getName(),
                    account == null ? null : account.getType(), opening, periodDebit, periodCredit, debit, credit,
                    balance, balanceSide));
            totalDebit += debit;
            totalCredit += credit;
        }
        return new TrialBalanceResponse(ym.toString(), scope, rows, totalDebit, totalCredit, totalDebit == totalCredit);
    }
}
