package io.github.aeginventor.ledgerguard.draft;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import java.util.List;

/**
 * 거래 설명으로 분개 초안을 만드는 구성 요소. 판정은 하지 않는다. 판정은 규칙 엔진이 한다.
 */
public interface JournalDrafter {

    /** 응답 drafter 필드에 그대로 노출되는 이름. fixture 또는 openai. */
    String name();

    DraftResult draft(DraftRequest request, List<Account> accounts);
}
