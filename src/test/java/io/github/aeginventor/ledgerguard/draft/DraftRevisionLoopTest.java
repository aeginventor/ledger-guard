package io.github.aeginventor.ledgerguard.draft;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/**
 * FAIL 사유를 되먹이는 수정 루프의 상한. 작성기가 끝까지 못 고치면 1 + max-revisions 번 부르고 멈춘다.
 * 한 번에 고치면 2번만 부른다. 실제 LLM은 부르지 않고 가짜 작성기를 쓴다.
 */
@SpringBootTest(properties = {"ledger-guard.drafter=stub", "ledger-guard.draft.max-revisions=2"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DraftRevisionLoopTest {

    /** 설명에 "고쳐짐"이 있으면 첫 수정에서 차대를 맞추고, 아니면 끝까지 차대 불일치 초안만 낸다. */
    static class StubDrafter implements JournalDrafter {

        final AtomicInteger calls = new AtomicInteger();

        @Override
        public String name() {
            return "stub";
        }

        @Override
        public DraftResult draft(DraftRequest request, List<Account> accounts) {
            calls.incrementAndGet();
            return unbalanced();
        }

        @Override
        public DraftResult revise(DraftRequest request, List<Account> accounts, DraftResult previous, List<String> findings) {
            calls.incrementAndGet();
            assertThat(findings).anyMatch(f -> f.contains("차변 합계"));
            return request.description().contains("고쳐짐") ? balanced() : unbalanced();
        }

        private static DraftResult unbalanced() {
            return new DraftResult(List.of(
                    new LineData(1, "830", Side.DEBIT, BigDecimal.valueOf(100_000), null),
                    new LineData(2, "101", Side.CREDIT, BigDecimal.valueOf(90_000), null)), List.of(), "stub");
        }

        private static DraftResult balanced() {
            return new DraftResult(List.of(
                    new LineData(1, "830", Side.DEBIT, BigDecimal.valueOf(100_000), null),
                    new LineData(2, "101", Side.CREDIT, BigDecimal.valueOf(100_000), null)), List.of(), "stub 수정");
        }
    }

    @TestConfiguration
    static class StubConfig {

        @Bean
        StubDrafter stubDrafter() {
            return new StubDrafter();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StubDrafter stub;

    private JsonNode draft(String description) throws Exception {
        Map<String, Object> body = Map.of("description", description, "voucherDate", LocalDate.of(2026, 10, 2).toString(),
                "evidenceType", "NONE", "vatType", "NA");
        String content = mockMvc.perform(MockMvcRequestBuilders.post("/api/vouchers/draft")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(content);
    }

    @Test
    void stopsAfterMaxRevisionsWhenDrafterNeverFixesTheDraft() throws Exception {
        stub.calls.set(0);
        JsonNode response = draft("못 고치는 초안");
        assertThat(response.get("drafter").asText()).isEqualTo("stub");
        assertThat(response.get("attempts").asInt()).isEqualTo(3);
        assertThat(stub.calls.get()).isEqualTo(3);
        assertThat(response.get("validation").get("overall").asText()).isEqualTo("FAIL");
    }

    @Test
    void stopsEarlyWhenRevisionPasses() throws Exception {
        stub.calls.set(0);
        JsonNode response = draft("고쳐짐 초안");
        assertThat(response.get("attempts").asInt()).isEqualTo(2);
        assertThat(stub.calls.get()).isEqualTo(2);
        assertThat(response.get("validation").get("overall").asText()).isEqualTo("PASS");
        assertThat(response.get("note").asText()).contains("수정");
    }
}
