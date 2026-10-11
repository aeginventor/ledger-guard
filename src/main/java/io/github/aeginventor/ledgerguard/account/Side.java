package io.github.aeginventor.ledgerguard.account;

public enum Side {
    DEBIT, CREDIT;

    /** 역분개는 모든 라인의 차대를 뒤집는다. */
    public Side opposite() {
        return this == DEBIT ? CREDIT : DEBIT;
    }
}
