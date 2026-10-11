package io.github.aeginventor.ledgerguard.voucher;

/**
 * 증빙 종류. TAX_INVOICE는 세금계산서(과세와 영세율 공급), INVOICE는 계산서(면세 공급), CARD는 신용카드와 직불카드,
 * CASH_RECEIPT는 현금영수증, NONE은 적격증빙 없음.
 */
public enum EvidenceType {
    TAX_INVOICE, INVOICE, CARD, CASH_RECEIPT, NONE
}
