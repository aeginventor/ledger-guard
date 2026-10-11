package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.util.List;
import org.junit.jupiter.api.Test;

class R09EntertainmentEvidenceRuleTest {

    private final R09EntertainmentEvidenceRule rule = new R09EntertainmentEvidenceRule();

    @Test
    void reviewWhenOverThirtyThousandWithoutEvidence() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.NONE)
                .debit("813", 50_000).credit("101", 50_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.REVIEW);
        assertThat(findings.get(0).message()).contains("50000원");
    }

    @Test
    void passesAtExactlyThirtyThousandWithoutEvidence() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.NONE)
                .debit("813", 30_000).credit("101", 30_000).build())).isEmpty();
    }

    @Test
    void passesWhenCardEvidenceExists() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.CARD)
                .debit("813", 50_000).credit("101", 50_000).build())).isEmpty();
    }

    @Test
    void passesWhenCashReceiptExists() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.CASH_RECEIPT)
                .debit("813", 50_000).credit("101", 50_000).build())).isEmpty();
    }

    @Test
    void passesWhenPlainInvoiceExists() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.INVOICE)
                .debit("813", 50_000).credit("101", 50_000).build())).isEmpty();
    }

    @Test
    void sumsMultipleEntertainmentLines() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.NONE)
                .debit("813", 20_000).debit("813", 15_000).credit("101", 35_000).build())).hasSize(1);
    }

    @Test
    void ignoresOtherExpenseAccounts() {
        assertThat(rule.check(ContextBuilder.voucher().vat(VatType.NA).evidence(EvidenceType.NONE)
                .debit("830", 50_000).credit("101", 50_000).build())).isEmpty();
    }
}
