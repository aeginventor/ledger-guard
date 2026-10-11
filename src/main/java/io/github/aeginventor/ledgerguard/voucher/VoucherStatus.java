package io.github.aeginventor.ledgerguard.voucher;

/**
 * DRAFT와 REVIEW는 승인 대기, REJECTED는 FAIL 판정 기록, POSTED는 원장에 전기됨, REVERSED는 전기된 뒤 역분개로 상쇄됨.
 * APPROVED는 쓰지 않는다(결정 16).
 */
public enum VoucherStatus {
    DRAFT, REVIEW, REJECTED, APPROVED, POSTED, REVERSED
}
