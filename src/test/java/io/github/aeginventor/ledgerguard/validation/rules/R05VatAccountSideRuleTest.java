package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class R05VatAccountSideRuleTest {

    private final R05VatAccountSideRule rule = new R05VatAccountSideRule();

    @Test
    void purchaseVatMustBeInputVatOnDebit() {
        assertThat(rule.check(ContextBuilder.voucher().supply(100_000L)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build())).isEmpty();
    }

    @Test
    void salesVatMustBeOutputVatOnCredit() {
        assertThat(rule.check(ContextBuilder.voucher().supply(100_000L)
                .debit("108", 110_000).credit("401", 100_000).credit("255", 10_000).build())).isEmpty();
    }

    @Test
    void skipsReversalVoucherWhoseSidesAreFlipped() {
        assertThat(rule.check(ContextBuilder.voucher().supply(100_000L).reversalOf(7L)
                .credit("830", 100_000).credit("135", 10_000).debit("101", 110_000).build())).isEmpty();
    }

    @Test
    void failsWhenPurchaseUsesOutputVatAccount() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().supply(100_000L)
                .debit("830", 100_000).debit("255", 10_000).credit("101", 110_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("매입").contains("부가세대급금 차변");
    }

    @Test
    void failsWhenSalesUsesInputVatAccount() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().supply(100_000L)
                .debit("108", 110_000).credit("401", 100_000).credit("135", 10_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("매출").contains("부가세예수금 대변");
    }

    @Test
    void failsWhenVatIsOnTheWrongSide() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().supply(100_000L)
                .debit("830", 110_000).credit("135", 10_000).credit("101", 100_000).build());
        assertThat(findings).hasSize(1);
    }

    @Test
    void ignoresVouchersWithoutVatLines() {
        assertThat(rule.check(ContextBuilder.voucher().debit("801", 1_000).credit("103", 1_000).build())).isEmpty();
    }
}
