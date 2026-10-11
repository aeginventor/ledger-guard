package io.github.aeginventor.ledgerguard.validation;

/**
 * 규칙이 참조하는 특수 계정 코드. 계정과목표(V2 seed)와 함께 바꿔야 한다.
 */
public final class LedgerAccounts {

    public static final String VAT_INPUT = "135";      // 부가세대급금(매입세액)
    public static final String VAT_OUTPUT = "255";     // 부가세예수금(매출세액)
    public static final String ENTERTAINMENT = "813";  // 기업업무추진비(구 접대비)

    private LedgerAccounts() {
    }
}
