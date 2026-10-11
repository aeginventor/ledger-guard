package io.github.aeginventor.ledgerguard.voucher.dto;

import java.util.List;

/** 초안 응답. 저장하지 않는다. drafter 필드로 어떤 작성기가 만들었는지 밝힌다(fixture는 실제 LLM 호출이 아니다). */
public record DraftResponse(Draft draft, String drafter, String note, ValidationResultResponse validation) {

    public record Draft(List<DraftLineResponse> lines) {
    }
}
