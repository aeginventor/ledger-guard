package io.github.aeginventor.ledgerguard.draft;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountType;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/** 실제 OpenAI를 호출하지 않는다. MockRestServiceServer가 응답을 흉내 낸다. */
class OpenAiJournalDrafterTest {

    private static final String BASE = "https://api.openai.example";

    private MockRestServiceServer server;
    private OpenAiJournalDrafter drafter;

    private final List<Account> accounts = List.of(
            new Account("830", "소모품비", AccountType.EXPENSE, Side.DEBIT, true, false),
            new Account("135", "부가세대급금", AccountType.ASSET, Side.DEBIT, true, true),
            new Account("150", "제품", AccountType.ASSET, Side.DEBIT, false, false));

    private final DraftRequest request = new DraftRequest("사무용품 카드 구입", LocalDate.of(2026, 10, 2), "가상문구",
            null, EvidenceType.CARD, VatType.TAXABLE, 100_000L);

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        drafter = new OpenAiJournalDrafter(builder.build(), "test-model", new ObjectMapper());
    }

    private static String completion(String content) throws Exception {
        return new ObjectMapper().writeValueAsString(java.util.Map.of("choices", List.of(
                java.util.Map.of("message", java.util.Map.of("role", "assistant", "content", content)))));
    }

    @Test
    void parsesJsonDraftFromCompletion() throws Exception {
        server.expect(requestTo(BASE + OpenAiJournalDrafter.COMPLETIONS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"model\":\"test-model\"")))
                .andExpect(content().string(containsString("830 소모품비")))
                .andExpect(content().string(containsString("사무용품 카드 구입")))
                .andRespond(withSuccess(completion("""
                        {"lines":[{"accountCode":"830","side":"DEBIT","amount":100000,"memo":"소모품비"},
                                  {"accountCode":"135","side":"debit","amount":10000},
                                  {"accountCode":"253","side":"CREDIT","amount":110000}]}
                        """), MediaType.APPLICATION_JSON));

        DraftResult result = drafter.draft(request, accounts);

        server.verify();
        assertThat(result.problems()).isEmpty();
        assertThat(result.lines()).extracting(LineData::accountCode).containsExactly("830", "135", "253");
        assertThat(result.lines()).extracting(LineData::side).containsExactly(Side.DEBIT, Side.DEBIT, Side.CREDIT);
        assertThat(result.lines().get(0).amount()).isEqualByComparingTo(new BigDecimal("100000"));
        assertThat(result.lines().get(0).memo()).isEqualTo("소모품비");
        assertThat(drafter.name()).isEqualTo("openai");
    }

    @Test
    void reportsProblemWhenContentIsNotJson() throws Exception {
        server.expect(requestTo(BASE + OpenAiJournalDrafter.COMPLETIONS_PATH))
                .andRespond(withSuccess(completion("분개는 다음과 같습니다: 차변 소모품비 100,000"),
                        MediaType.APPLICATION_JSON));

        DraftResult result = drafter.draft(request, accounts);

        assertThat(result.lines()).isEmpty();
        assertThat(result.problems()).hasSize(1);
        assertThat(result.problems().get(0)).startsWith("JSON 파싱 실패");
    }

    @Test
    void reportsProblemWhenLinesArrayIsMissing() throws Exception {
        server.expect(requestTo(BASE + OpenAiJournalDrafter.COMPLETIONS_PATH))
                .andRespond(withSuccess(completion("{\"entries\":[]}"), MediaType.APPLICATION_JSON));

        DraftResult result = drafter.draft(request, accounts);

        assertThat(result.problems()).containsExactly("초안 JSON에 lines 배열이 없다");
    }

    @Test
    void reportsProblemWhenServerFails() {
        server.expect(requestTo(BASE + OpenAiJournalDrafter.COMPLETIONS_PATH)).andRespond(withServerError());

        DraftResult result = drafter.draft(request, accounts);

        assertThat(result.lines()).isEmpty();
        assertThat(result.problems().get(0)).startsWith("LLM 호출 실패");
    }

    @Test
    void keepsMalformedLinesSoRulesCanReportThem() {
        DraftResult result = drafter.parseDraft(
                "{\"lines\":[{\"accountCode\":\"830\",\"side\":\"LEFT\",\"amount\":\"백만원\"},{\"side\":\"CREDIT\",\"amount\":1000.5}]}");

        assertThat(result.problems()).isEmpty();
        assertThat(result.lines()).hasSize(2);
        assertThat(result.lines().get(0).side()).isNull();
        assertThat(result.lines().get(0).amount()).isNull();
        assertThat(result.lines().get(1).accountCode()).isNull();
        assertThat(result.lines().get(1).amount()).isEqualByComparingTo(new BigDecimal("1000.5"));
    }

    @Test
    void systemPromptListsOnlyActiveAccounts() {
        String prompt = OpenAiJournalDrafter.systemPrompt(accounts);
        assertThat(prompt).contains("830 소모품비").contains("135 부가세대급금").doesNotContain("150 제품");
        assertThat(prompt).contains("원 미만을 버린다");
    }

    @Test
    void extractsJsonObjectFromFencedOrChattyContent() {
        assertThat(OpenAiJournalDrafter.extractJsonObject("```json\n{\"lines\":[]}\n```")).isEqualTo("{\"lines\":[]}");
        assertThat(OpenAiJournalDrafter.extractJsonObject("초안입니다: {\"lines\":[{\"a\":1}]} 끝")).isEqualTo("{\"lines\":[{\"a\":1}]}");
        assertThat(OpenAiJournalDrafter.extractJsonObject("lines 없음")).isEqualTo("lines 없음");
        DraftResult fenced = drafter.parseDraft("```json\n{\"lines\":[{\"accountCode\":\"830\",\"side\":\"debit\",\"amount\":\"100,000\"}]}\n```");
        assertThat(fenced.problems()).isEmpty();
        assertThat(fenced.lines()).hasSize(1);
        assertThat(fenced.lines().get(0).side()).isEqualTo(Side.DEBIT);
        assertThat(fenced.lines().get(0).amount()).isEqualByComparingTo(BigDecimal.valueOf(100_000));
    }

    @Test
    void reviseSendsPreviousDraftAndFindingsBack() throws Exception {
        server.expect(requestTo(BASE + OpenAiJournalDrafter.COMPLETIONS_PATH))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("규칙 검증에 실패했다")))
                .andExpect(content().string(containsString("차변 합계 110000원과 대변 합계 100000원이 다르다")))
                .andExpect(content().string(containsString("\"role\":\"assistant\"")))
                .andRespond(withSuccess(completion("{\"lines\":[{\"accountCode\":\"830\",\"side\":\"DEBIT\",\"amount\":100000},"
                        + "{\"accountCode\":\"135\",\"side\":\"DEBIT\",\"amount\":10000},"
                        + "{\"accountCode\":\"253\",\"side\":\"CREDIT\",\"amount\":110000}]}"), MediaType.APPLICATION_JSON));
        DraftResult previous = new DraftResult(List.of(
                new LineData(1, "830", Side.DEBIT, BigDecimal.valueOf(100_000), null),
                new LineData(2, "135", Side.DEBIT, BigDecimal.valueOf(10_000), null),
                new LineData(3, "253", Side.CREDIT, BigDecimal.valueOf(100_000), null)), List.of(), "첫 시도");
        DraftResult revised = drafter.revise(request, accounts, previous,
                List.of("차변 합계 110000원과 대변 합계 100000원이 다르다"));
        assertThat(revised.problems()).isEmpty();
        assertThat(revised.lines()).hasSize(3);
        assertThat(revised.lines().get(2).amount()).isEqualByComparingTo(BigDecimal.valueOf(110_000));
        assertThat(revised.note()).contains("되먹여");
        server.verify();
    }
}
