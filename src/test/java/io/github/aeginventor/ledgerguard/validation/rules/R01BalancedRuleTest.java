package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import java.util.List;
import org.junit.jupiter.api.Test;

class R01BalancedRuleTest {

    private final R01BalancedRule rule = new R01BalancedRule();

    @Test
    void passesWhenDebitEqualsCredit() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000).build());
        assertThat(findings).isEmpty();
    }

    @Test
    void failsWhenDebitDiffersFromCredit() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .debit("830", 100_000).credit("101", 90_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).ruleCode()).isEqualTo("R01");
        assertThat(findings.get(0).severity()).isEqualTo(Severity.FAIL);
        assertThat(findings.get(0).message()).contains("100000원").contains("90000원");
    }

    @Test
    void treatsMissingAmountAsZeroSoOtherRulesCanReportIt() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .line("830", io.github.aeginventor.ledgerguard.account.Side.DEBIT, null)
                .credit("101", 1_000).build());
        assertThat(findings).hasSize(1);
    }
}
