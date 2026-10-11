package io.github.aeginventor.ledgerguard.draft;

import java.util.List;
import java.util.Map;

/** src/main/resources/drafts/*.json 한 파일의 구조. */
public record DraftFixture(
        String id,
        int priority,
        List<String> keywords,
        Settlement settlement,
        List<Line> lines) {

    /** 결제 계정 선택 규칙. 설명에 keywords의 키가 있으면 그 계정을, 없으면 defaultAccount를 쓴다. */
    public record Settlement(String defaultAccount, Map<String, String> keywords) {
    }

    /** amount는 SUPPLY, VAT, TOTAL 가운데 하나. accountCode가 @settlement면 결제 계정으로 바꾼다. */
    public record Line(String accountCode, String side, String amount, String memo) {
    }
}
