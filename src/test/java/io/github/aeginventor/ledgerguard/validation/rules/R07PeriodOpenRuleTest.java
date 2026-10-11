package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class R07PeriodOpenRuleTest {

    private final R07PeriodOpenRule rule = new R07PeriodOpenRule();

    @Test
    void passesWhenPeriodIsOpen() {
        assertThat(rule.check(ContextBuilder.voucher().date(LocalDate.of(2026, 10, 2))
                .closedPeriod(YearMonth.of(2026, 9)).build())).isEmpty();
    }

    @Test
    void failsWhenPeriodIsClosed() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().date(LocalDate.of(2026, 9, 30))
                .closedPeriod(YearMonth.of(2026, 9)).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("2026-09").contains("마감");
    }

    @Test
    void skipsWhenDateIsMissing() {
        assertThat(rule.check(ContextBuilder.voucher().date(null).build())).isEmpty();
    }
}
