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

    /**
     * 규칙 판정이 FAIL이었을 때 사유를 되먹여 초안을 고쳐 달라고 한다. 기본은 고치지 못한다는 뜻으로 null을 돌려주고,
     * LLM 작성기만 구현한다. 호출 횟수 상한은 DraftService가 지킨다.
     */
    default DraftResult revise(DraftRequest request, List<Account> accounts, DraftResult previous,
            List<String> findings) {
        return null;
    }
}
