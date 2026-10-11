package io.github.aeginventor.ledgerguard.draft;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/** 키가 있고 drafter=openai 로 고르면 OpenAI 작성기가 활성 작성기가 된다. 실제 호출은 하지 않는다. */
@SpringBootTest(properties = {
        "ledger-guard.openai.api-key=test-key-not-real",
        "ledger-guard.openai.base-url=http://127.0.0.1:9",
        "ledger-guard.drafter=openai"})
@ActiveProfiles("test")
class DrafterSelectionTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DraftService draftService;

    @Test
    void openAiDrafterIsRegisteredAndSelectedWhenKeyIsPresent() {
        assertThat(context.getBeansOfType(OpenAiJournalDrafter.class)).hasSize(1);
        assertThat(context.getBeansOfType(FixtureJournalDrafter.class)).hasSize(1);
        assertThat(draftService.activeDrafter().name()).isEqualTo("openai");
    }
}
