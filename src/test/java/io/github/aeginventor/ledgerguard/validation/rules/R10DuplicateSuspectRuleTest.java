package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class R10DuplicateSuspectRuleTest {

    private final R10DuplicateSuspectRule rule = new R10DuplicateSuspectRule();

    @Test
    void reviewWhenSameDateCounterpartyAndTotalExists() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .date(LocalDate.of(2026, 10, 2)).counterparty("가상상사")
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000)
                .duplicate((date, name, total) -> date.equals(LocalDate.of(2026, 10, 2)) && name.equals("가상상사")
                        && total.compareTo(BigDecimal.valueOf(110_000)) == 0)
                .build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.REVIEW);
    }

    @Test
    void skipsReversalVoucher() {
        assertThat(rule.check(ContextBuilder.voucher().reversalOf(7L)
                .debit("830", 100_000).credit("101", 100_000)
                .duplicate((date, name, total) -> true).build())).isEmpty();
    }

    @Test
    void passesWhenNoMatchingVoucherExists() {
        assertThat(rule.check(ContextBuilder.voucher()
                .debit("830", 100_000).credit("101", 100_000)
                .duplicate((date, name, total) -> false).build())).isEmpty();
    }

    @Test
    void skipsWhenCounterpartyIsMissing() {
        assertThat(rule.check(ContextBuilder.voucher().counterparty(null)
                .debit("830", 100_000).credit("101", 100_000)
                .duplicate((date, name, total) -> true).build())).isEmpty();
    }
}
