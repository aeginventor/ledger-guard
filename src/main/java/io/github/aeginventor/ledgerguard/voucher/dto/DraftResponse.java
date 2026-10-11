package io.github.aeginventor.ledgerguard.voucher.dto;

import java.util.List;

/**
 * 초안 응답. 저장하지 않는다. drafter 필드로 어떤 작성기가 만들었는지 밝힌다(fixture는 실제 LLM 호출이 아니다).
 * attempts는 작성기를 부른 횟수다. 1이면 수정 없이 나온 초안, 2 이상이면 FAIL 사유를 되먹여 고친 초안이다.
 */
public record DraftResponse(Draft draft, String drafter, String note, int attempts, ValidationResultResponse validation) {

    public record Draft(List<DraftLineResponse> lines) {
    }
}
