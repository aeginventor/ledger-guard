package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class R03AmountPositiveIntRuleTest {

    private final R03AmountPositiveIntRule rule = new R03AmountPositiveIntRule();

    @Test
    void passesForPositiveIntegers() {
        assertThat(rule.check(ContextBuilder.voucher().debit("830", 1).credit("101", 1).build())).isEmpty();
    }

    @Test
    void failsForZero() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().debit("830", 0).credit("101", 0).build());
        assertThat(findings).hasSize(2);
        assertThat(findings.get(0).message()).contains("1원 이상");
    }

    @Test
    void failsForNegative() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().debit("830", -500).credit("101", 500).build());
        assertThat(findings).hasSize(1);
    }

    @Test
    void failsForDecimalAmount() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .line("830", Side.DEBIT, new BigDecimal("1000.5")).credit("101", 1_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("정수가 아니다");
    }

    @Test
    void acceptsDecimalWithOnlyTrailingZeros() {
        assertThat(rule.check(ContextBuilder.voucher()
                .line("830", Side.DEBIT, new BigDecimal("1000.00")).credit("101", 1_000).build())).isEmpty();
    }

    @Test
    void failsForMissingAmount() {
        List<Finding> findings = rule.check(ContextBuilder.voucher()
                .line("830", Side.DEBIT, null).credit("101", 1_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("금액이 없다");
    }
}
