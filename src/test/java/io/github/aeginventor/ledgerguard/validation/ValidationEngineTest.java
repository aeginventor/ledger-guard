package io.github.aeginventor.ledgerguard.validation;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.rules.R01BalancedRule;
import io.github.aeginventor.ledgerguard.validation.rules.R02AccountValidRule;
import io.github.aeginventor.ledgerguard.validation.rules.R03AmountPositiveIntRule;
import io.github.aeginventor.ledgerguard.validation.rules.R04VatAmountRule;
import io.github.aeginventor.ledgerguard.validation.rules.R05VatAccountSideRule;
import io.github.aeginventor.ledgerguard.validation.rules.R06TaxInvoiceRequisitesRule;
import io.github.aeginventor.ledgerguard.validation.rules.R07PeriodOpenRule;
import io.github.aeginventor.ledgerguard.validation.rules.R08DateNotFutureRule;
import io.github.aeginventor.ledgerguard.validation.rules.R09EntertainmentEvidenceRule;
import io.github.aeginventor.ledgerguard.validation.rules.R10DuplicateSuspectRule;
import io.github.aeginventor.ledgerguard.validation.rules.R11DraftSchemaRule;
import io.github.aeginventor.ledgerguard.validation.rules.R12PreparerNotApproverRule;
import io.github.aeginventor.ledgerguard.validation.rules.R13EvidenceMatchesVatTypeRule;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationEngineTest {

    static ValidationEngine engine() {
        return new ValidationEngine(List.of(
                new R11DraftSchemaRule(), new R05VatAccountSideRule(), new R01BalancedRule(),
                new R02AccountValidRule(), new R03AmountPositiveIntRule(), new R04VatAmountRule(),
                new R06TaxInvoiceRequisitesRule(), new R07PeriodOpenRule(), new R08DateNotFutureRule(),
                new R09EntertainmentEvidenceRule(), new R10DuplicateSuspectRule(), new R12PreparerNotApproverRule(),
                new R13EvidenceMatchesVatTypeRule()));
    }

    @Test
    void sortsRulesByCodeRegardlessOfRegistrationOrder() {
        assertThat(engine().rules().stream().map(ValidationRule::code))
                .containsExactly("R01", "R02", "R03", "R04", "R05", "R06", "R07", "R08", "R09", "R10", "R11", "R12", "R13");
    }

    @Test
    void passesWhenNoRuleFires() {
        ValidationResult result = engine().evaluate(ContextBuilder.voucher()
                .supply(100_000L)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000)
                .build());
        assertThat(result.overall()).isEqualTo(Overall.PASS);
        assertThat(result.findings()).isEmpty();
    }

    @Test
    void collectsEveryFindingInsteadOfStoppingAtTheFirstFailure() {
        ValidationResult result = engine().evaluate(ContextBuilder.voucher()
                .date(LocalDate.of(2026, 12, 1))
                .closedPeriod(YearMonth.of(2026, 12))
                .supply(100_000L)
                .debit("830", 100_000).debit("135", 10_001).credit("999", 110_000)
                .build());
        assertThat(result.overall()).isEqualTo(Overall.FAIL);
        assertThat(result.codes()).containsExactly("R01", "R02", "R04", "R07", "R08");
    }

    @Test
    void reviewOnlyWhenThereAreReviewFindingsAndNoFailures() {
        ValidationResult result = engine().evaluate(ContextBuilder.voucher()
                .evidence(EvidenceType.NONE)
                .vat(io.github.aeginventor.ledgerguard.voucher.VatType.NA)
                .debit("813", 50_000).credit("101", 50_000)
                .build());
        assertThat(result.overall()).isEqualTo(Overall.REVIEW);
        assertThat(result.codes()).containsExactly("R09");
    }

    @Test
    void failOutranksReview() {
        ValidationResult result = engine().evaluate(ContextBuilder.voucher()
                .evidence(EvidenceType.NONE)
                .vat(io.github.aeginventor.ledgerguard.voucher.VatType.NA)
                .debit("813", 50_000).credit("101", 40_000)
                .build());
        assertThat(result.overall()).isEqualTo(Overall.FAIL);
        assertThat(result.codes()).containsExactly("R01", "R09");
    }
}
