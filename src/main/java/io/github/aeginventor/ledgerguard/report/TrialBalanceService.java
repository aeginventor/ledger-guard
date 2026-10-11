package io.github.aeginventor.ledgerguard.report;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.ledger.LedgerEntryRepository;
import io.github.aeginventor.ledgerguard.period.PeriodKey;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 시산표는 뷰나 캐시 없이 원장을 집계 쿼리로 읽는다. 해당 월에 전기된 원장 항목만 집계한다. */
@Service
public class TrialBalanceService {

    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    public TrialBalanceService(LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository) {
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public TrialBalanceResponse trialBalance(String month) {
        YearMonth ym = PeriodKey.parse(month);
        Map<String, Account> accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getCode, Function.identity()));
        Map<String, long[]> sums = new TreeMap<>();
        for (Object[] row : ledgerEntryRepository.sumByAccountAndSide(ym.atDay(1), ym.plusMonths(1).atDay(1))) {
            String code = (String) row[0];
            Side side = (Side) row[1];
            long amount = ((Number) row[2]).longValue();
            long[] pair = sums.computeIfAbsent(code, k -> new long[2]);
            pair[side == Side.DEBIT ? 0 : 1] += amount;
        }
        List<TrialBalanceResponse.Row> rows = new ArrayList<>();
        long totalDebit = 0;
        long totalCredit = 0;
        for (Map.Entry<String, long[]> entry : sums.entrySet()) {
            Account account = accounts.get(entry.getKey());
            long debit = entry.getValue()[0];
            long credit = entry.getValue()[1];
            long balance = debit - credit;
            Side balanceSide = balance > 0 ? Side.DEBIT : balance < 0 ? Side.CREDIT : null;
            rows.add(new TrialBalanceResponse.Row(entry.getKey(), account == null ? null : account.getName(),
                    account == null ? null : account.getType(), debit, credit, balance, balanceSide));
            totalDebit += debit;
            totalCredit += credit;
        }
        return new TrialBalanceResponse(ym.toString(), rows, totalDebit, totalCredit, totalDebit == totalCredit);
    }
}
