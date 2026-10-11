package io.github.aeginventor.ledgerguard.validation;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountType;
import io.github.aeginventor.ledgerguard.account.Side;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/** V2 seed와 같은 값의 계정과목 일부. 규칙 단위 테스트는 DB 없이 이 목록을 쓴다. */
public final class TestAccounts {

    public static final Map<String, Account> CATALOG = Map.ofEntries(
            entry("101", "현금", AccountType.ASSET, Side.DEBIT, true, false),
            entry("103", "보통예금", AccountType.ASSET, Side.DEBIT, true, false),
            entry("108", "외상매출금", AccountType.ASSET, Side.DEBIT, true, false),
            entry("135", "부가세대급금", AccountType.ASSET, Side.DEBIT, true, true),
            entry("146", "상품", AccountType.ASSET, Side.DEBIT, true, false),
            entry("150", "제품", AccountType.ASSET, Side.DEBIT, false, false),
            entry("212", "비품", AccountType.ASSET, Side.DEBIT, true, false),
            entry("251", "외상매입금", AccountType.LIABILITY, Side.CREDIT, true, false),
            entry("253", "미지급금", AccountType.LIABILITY, Side.CREDIT, true, false),
            entry("255", "부가세예수금", AccountType.LIABILITY, Side.CREDIT, true, true),
            entry("331", "자본금", AccountType.EQUITY, Side.CREDIT, true, false),
            entry("401", "매출", AccountType.REVENUE, Side.CREDIT, true, false),
            entry("801", "급여", AccountType.EXPENSE, Side.DEBIT, true, false),
            entry("813", "기업업무추진비", AccountType.EXPENSE, Side.DEBIT, true, false),
            entry("819", "지급임차료", AccountType.EXPENSE, Side.DEBIT, true, false),
            entry("830", "소모품비", AccountType.EXPENSE, Side.DEBIT, true, false),
            entry("930", "잡이익", AccountType.REVENUE, Side.CREDIT, true, false));

    public static final Function<String, Optional<Account>> LOOKUP = code -> Optional.ofNullable(CATALOG.get(code));

    private TestAccounts() {
    }

    private static Map.Entry<String, Account> entry(String code, String name, AccountType type, Side side,
            boolean active, boolean vat) {
        return Map.entry(code, new Account(code, name, type, side, active, vat));
    }
}
