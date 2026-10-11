package io.github.aeginventor.ledgerguard;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.aeginventor.ledgerguard.draft.DraftService;
import io.github.aeginventor.ledgerguard.draft.OpenAiJournalDrafter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class LedgerGuardApplicationTests {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private DraftService draftService;

    @Test
    void contextLoadsWithFixtureDrafterAndNoOpenAiBeanByDefault() {
        assertThat(context.getBeansOfType(OpenAiJournalDrafter.class)).isEmpty();
        assertThat(draftService.activeDrafter().name()).isEqualTo("fixture");
    }
}
