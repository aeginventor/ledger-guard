package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import java.util.List;
import org.junit.jupiter.api.Test;

class R02AccountValidRuleTest {

    private final R02AccountValidRule rule = new R02AccountValidRule();

    @Test
    void passesWhenEveryAccountExistsAndIsActive() {
        assertThat(rule.check(ContextBuilder.voucher().debit("830", 1_000).credit("101", 1_000).build())).isEmpty();
    }

    @Test
    void failsForUnknownAccountCode() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().debit("999", 1_000).credit("101", 1_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("999").contains("계정과목표에 없다");
    }

    @Test
    void failsForInactiveAccount() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().debit("150", 1_000).credit("101", 1_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("사용 중지");
    }

    @Test
    void reportsEachBadLineSeparately() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .debit("998", 1_000).debit("999", 1_000).credit("101", 2_000).build());
        assertThat(findings).hasSize(2);
    }
}
