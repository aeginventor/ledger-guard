package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class R08DateNotFutureRuleTest {

    private final R08DateNotFutureRule rule = new R08DateNotFutureRule();

    @Test
    void passesForTodayAndPast() {
        assertThat(rule.check(ContextBuilder.voucher().date(ContextBuilder.TODAY).build())).isEmpty();
        assertThat(rule.check(ContextBuilder.voucher().date(ContextBuilder.TODAY.minusDays(40)).build())).isEmpty();
    }

    @Test
    void failsForTomorrow() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().date(ContextBuilder.TODAY.plusDays(1)).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("미래");
    }

    @Test
    void failsWhenDateIsMissing() {
        assertThat(rule.check(ContextBuilder.voucher().date(null).build())).hasSize(1);
    }

    @Test
    void usesInjectedToday() {
        assertThat(rule.check(ContextBuilder.voucher().date(LocalDate.of(2026, 10, 11))
                .today(LocalDate.of(2026, 10, 10)).build())).hasSize(1);
    }
}
