package io.github.aeginventor.ledgerguard.draft;

import io.github.aeginventor.ledgerguard.validation.LineData;
import java.util.List;

/**
 * 초안 작성기의 출력. problems는 JSON 파싱 실패처럼 초안 자체를 만들지 못한 사유이며 R11이 FAIL로 기록한다.
 */
public record DraftResult(List<LineData> lines, List<String> problems, String note) {

    public static DraftResult failed(String problem) {
        return new DraftResult(List.of(), List.of(problem), null);
    }
}
