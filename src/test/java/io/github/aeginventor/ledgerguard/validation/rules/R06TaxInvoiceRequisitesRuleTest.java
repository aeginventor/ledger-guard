package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.util.List;
import org.junit.jupiter.api.Test;

class R06TaxInvoiceRequisitesRuleTest {

    private final R06TaxInvoiceRequisitesRule rule = new R06TaxInvoiceRequisitesRule();

    private ContextBuilder taxInvoicePurchase() {
        return ContextBuilder.voucher().evidence(EvidenceType.TAX_INVOICE).vat(VatType.TAXABLE).supply(100_000L)
                .debit("830", 100_000).debit("135", 10_000).credit("253", 110_000);
    }

    @Test
    void passesWithValidBizNoAndSeparatedLines() {
        assertThat(rule.check(taxInvoicePurchase().bizNo("123-45-67891").build())).isEmpty();
    }

    @Test
    void failsWhenBizNoIsMissing() {
        List<Finding> findings = rule.check(taxInvoicePurchase().bizNo(null).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("사업자등록번호가 없다");
    }

    @Test
    void failsWhenBizNoHasWrongLength() {
        List<Finding> findings = rule.check(taxInvoicePurchase().bizNo("123-45-6789").build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("형식");
    }

    @Test
    void failsWhenChecksumDoesNotMatch() {
        List<Finding> findings = rule.check(taxInvoicePurchase().bizNo("123-45-67890").build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("체크섬");
    }

    @Test
    void failsWhenTaxableInvoiceHasNoSeparateVatLine() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().evidence(EvidenceType.TAX_INVOICE)
                .vat(VatType.TAXABLE).supply(100_000L).bizNo("1234567891")
                .debit("830", 110_000).credit("253", 110_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("분리");
    }

    @Test
    void zeroRatedInvoiceDoesNotRequireVatLine() {
        assertThat(rule.check(ContextBuilder.voucher().evidence(EvidenceType.TAX_INVOICE)
                .vat(VatType.ZERO_RATED).supply(100_000L).bizNo("1234567891")
                .debit("108", 100_000).credit("401", 100_000).build())).isEmpty();
    }

    @Test
    void ignoresOtherEvidenceTypes() {
        assertThat(rule.check(ContextBuilder.voucher().evidence(EvidenceType.CARD).bizNo(null)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build())).isEmpty();
    }
}
