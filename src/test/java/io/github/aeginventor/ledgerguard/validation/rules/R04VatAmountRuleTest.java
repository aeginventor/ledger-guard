package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.util.List;
import org.junit.jupiter.api.Test;

class R04VatAmountRuleTest {

    private final R04VatAmountRule rule = new R04VatAmountRule();

    @Test
    void expectedVatIsTenPercentFlooredToWon() {
        assertThat(R04VatAmountRule.expectedVat(100_000)).isEqualTo(10_000);
        assertThat(R04VatAmountRule.expectedVat(12_345)).isEqualTo(1_234);
        assertThat(R04VatAmountRule.expectedVat(99_999)).isEqualTo(9_999);
        assertThat(R04VatAmountRule.expectedVat(5)).isEqualTo(0);
    }

    @Test
    void passesWhenVatLineMatchesFlooredTenPercent() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).supply(12_345L)
                .debit("830", 12_345).debit("135", 1_234).credit("101", 13_579).build())).isEmpty();
    }

    @Test
    void failsWhenVatIsRoundedInsteadOfFloored() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).supply(12_345L)
                .debit("830", 12_345).debit("135", 1_235).credit("101", 13_580).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("1235원").contains("1234원");
    }

    @Test
    void failsWhenTaxableVoucherHasNoVatLine() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).supply(100_000L)
                .debit("830", 110_000).credit("101", 110_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("0원");
    }

    @Test
    void skipsTaxableVoucherWithoutSupplyAmount() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).supply(null)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build())).isEmpty();
    }

    @Test
    void failsWhenExemptVoucherHasVatLine() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.EXEMPT).supply(50_000L)
                .debit("830", 50_000).debit("135", 5_000).credit("101", 55_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("면세");
    }

    @Test
    void failsWhenZeroRatedVoucherHasVatLine() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.ZERO_RATED).supply(50_000L)
                .debit("108", 55_000).credit("401", 50_000).credit("255", 5_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("영세율");
    }

    @Test
    void failsWhenNotApplicableVoucherHasVatLine() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.NA)
                .debit("801", 3_000_000).debit("135", 300_000).credit("103", 3_300_000).build());
        assertThat(findings).hasSize(1);
    }

    @Test
    void passesExemptVoucherWithoutVatLine() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.EXEMPT).supply(50_000L)
                .debit("830", 50_000).credit("101", 50_000).build())).isEmpty();
    }
}
