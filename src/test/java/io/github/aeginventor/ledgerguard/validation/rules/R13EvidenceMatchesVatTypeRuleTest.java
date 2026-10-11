package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.util.List;
import org.junit.jupiter.api.Test;

class R13EvidenceMatchesVatTypeRuleTest {

    private final R13EvidenceMatchesVatTypeRule rule = new R13EvidenceMatchesVatTypeRule();

    @Test
    void reviewWhenExemptTransactionHasTaxInvoice() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.EXEMPT)
                .evidence(EvidenceType.TAX_INVOICE).bizNo("123-45-67891")
                .debit("830", 45_000).credit("101", 45_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.REVIEW);
        assertThat(findings.get(0).message()).contains("면세");
    }

    @Test
    void reviewWhenTaxableOrZeroRatedTransactionHasPlainInvoice() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).evidence(EvidenceType.INVOICE).supply(100_000L)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build())).hasSize(1);
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.ZERO_RATED).evidence(EvidenceType.INVOICE)
                .debit("108", 100_000).credit("401", 100_000).build())).hasSize(1);
    }

    @Test
    void passesWhenEvidenceMatchesVatType() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.EXEMPT).evidence(EvidenceType.INVOICE)
                .debit("830", 45_000).credit("101", 45_000).build())).isEmpty();
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).evidence(EvidenceType.TAX_INVOICE).supply(100_000L)
                .bizNo("123-45-67891").debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build())).isEmpty();
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.ZERO_RATED).evidence(EvidenceType.TAX_INVOICE)
                .bizNo("123-45-67891").debit("108", 100_000).credit("401", 100_000).build())).isEmpty();
    }

    @Test
    void ignoresCardCashReceiptNoneAndNotApplicable() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.EXEMPT).evidence(EvidenceType.CARD)
                .debit("830", 45_000).credit("101", 45_000).build())).isEmpty();
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.INVOICE)
                .debit("801", 100_000).credit("103", 100_000).build())).isEmpty();
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.TAXABLE).evidence(EvidenceType.NONE).supply(100_000L)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build())).isEmpty();
    }
}
