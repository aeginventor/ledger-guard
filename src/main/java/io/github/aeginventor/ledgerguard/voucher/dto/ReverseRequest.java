package io.github.aeginventor.ledgerguard.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 역분개 요청. 역분개 전표의 작성자(preparer)와 승인자(approver)는 달라야 하고(R12), 사유는 필수다.
 * voucherDate가 없으면 오늘 날짜로 역분개한다.
 */
public record ReverseRequest(
        @NotBlank @Size(max = 100) String preparer,
        @NotBlank @Size(max = 100) String approver,
        @NotBlank @Size(max = 500) String reason,
        LocalDate voucherDate) {
}
