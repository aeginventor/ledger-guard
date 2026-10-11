package io.github.aeginventor.ledgerguard.validation.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.validation.ContextBuilder;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.Stage;
import java.util.List;
import org.junit.jupiter.api.Test;

class R12PreparerNotApproverRuleTest {

    private final R12PreparerNotApproverRule rule = new R12PreparerNotApproverRule();

    private ContextBuilder balanced() {
        return ContextBuilder.voucher().supply(100_000L)
                .debit("830", 100_000).debit("135", 10_000).credit("101", 110_000);
    }

    @Test
    void failsWhenPreparerApprovesOwnVoucher() {
        List<Finding> findings = rule.check(balanced().stage(Stage.APPROVE)
                .preparer("김작성").approver("김작성").build());
        assertThat(findings).hasSize(1);
        assertThat(findings.get(0).severity()).isEqualTo(Severity.FAIL);
        assertThat(findings.get(0).message()).contains("김작성");
    }

    @Test
    void ignoresSurroundingSpacesAndCase() {
        assertThat(rule.check(balanced().stage(Stage.APPROVE)
                .preparer(" Kim ").approver("kim").build())).hasSize(1);
    }

    @Test
    void passesWhenApproverDiffers() {
        assertThat(rule.check(balanced().stage(Stage.APPROVE)
                .preparer("김작성").approver("박승인").build())).isEmpty();
    }

    @Test
    void doesNotApplyAtSaveOrDraftStage() {
        assertThat(rule.check(balanced().stage(Stage.SAVE).preparer("김작성").approver("김작성").build())).isEmpty();
        assertThat(rule.check(balanced().stage(Stage.DRAFT).preparer("김작성").approver("김작성").build())).isEmpty();
    }

    @Test
    void passesWhenPreparerOrApproverIsMissing() {
        assertThat(rule.check(balanced().stage(Stage.APPROVE).approver("김작성").build())).isEmpty();
        assertThat(rule.check(balanced().stage(Stage.APPROVE).preparer("김작성").build())).isEmpty();
        assertThat(rule.check(balanced().stage(Stage.APPROVE).preparer("   ").approver("김작성").build())).isEmpty();
    }
}
