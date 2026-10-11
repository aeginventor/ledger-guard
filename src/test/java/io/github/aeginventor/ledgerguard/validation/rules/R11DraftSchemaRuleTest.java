package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Stage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class R11DraftSchemaRuleTest {

    private final R11DraftSchemaRule rule = new R11DraftSchemaRule();

    @Test
    void passesWellFormedDraft() {
        assertThat(rule.check(ContextBuilder.voucher().stage(Stage.DRAFT)
                .debit("830", 100_000).credit("101", 100_000).build())).isEmpty();
    }

    @Test
    void failsDraftWithSingleLine() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().stage(Stage.DRAFT)
                .debit("830", 100_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("1개");
    }

    @Test
    void failsWhenLineLacksRequiredFields() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().stage(Stage.DRAFT)
                .line(null, null, null).credit("101", 100_000).build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).message()).contains("accountCode").contains("side").contains("amount");
    }

    @Test
    void reportsDrafterProblemsSuchAsJsonParseFailure() {
        List<Finding> findings = rule.check(ContextBuilder.voucher().stage(Stage.DRAFT)
                .draftProblem("JSON 파싱 실패").build());
        assertThat(findings).extracting(Finding::message).anyMatch(m -> m.contains("JSON 파싱 실패"));
    }

    @Test
    void doesNotApplyAtSaveStage() {
        assertThat(rule.check(ContextBuilder.voucher().stage(Stage.SAVE)
                .line(null, Side.DEBIT, BigDecimal.ONE).build())).isEmpty();
    }
}
