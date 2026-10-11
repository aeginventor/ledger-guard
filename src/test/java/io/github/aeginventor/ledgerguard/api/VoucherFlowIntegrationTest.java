package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 초안 → 저장 → 승인 → 시산표 흐름. 다른 테스트와 겹치지 않도록 한 달 전 날짜와 고유한 거래처를 쓴다. */
class VoucherFlowIntegrationTest extends ApiTestSupport {

    private static final LocalDate DATE = LocalDate.now().minusMonths(1).withDayOfMonth(3);
    private static final String MONTH = YearMonth.from(DATE).toString();

    @Test
    void draftThenSaveThenApproveThenTrialBalance() throws Exception {
        Map<String, Object> draftRequest = new LinkedHashMap<>();
        draftRequest.put("description", "사무용품 카드 구입");
        draftRequest.put("voucherDate", DATE.toString());
        draftRequest.put("counterpartyName", "흐름테스트상사");
        draftRequest.put("evidenceType", "CARD");
        draftRequest.put("vatType", "TAXABLE");
        draftRequest.put("supplyAmount", 100_000);

        JsonNode draft = postJson("/api/vouchers/draft", draftRequest, 200);
        assertThat(draft.get("drafter").asText()).isEqualTo("fixture");
        assertThat(draft.get("validation").get("overall").asText()).isEqualTo("PASS");
        JsonNode draftLines = draft.get("draft").get("lines");
        assertThat(draftLines).hasSize(3);
        assertThat(draftLines.get(0).get("accountName").asText()).isEqualTo("소모품비");

        List<Map<String, Object>> lines = new ArrayList<>();
        for (JsonNode node : draftLines) {
            lines.add(line(node.get("accountCode").asText(), node.get("side").asText(), node.get("amount").asLong()));
        }
        Map<String, Object> voucherRequest = new LinkedHashMap<>(draftRequest);
        voucherRequest.put("drafter", "fixture");
        voucherRequest.put("lines", lines);

        JsonNode saved = postJson("/api/vouchers", voucherRequest, 201);
        long id = saved.get("id").asLong();
        assertThat(saved.get("status").asText()).isEqualTo("DRAFT");
        assertThat(saved.get("drafter").asText()).isEqualTo("fixture");
        assertThat(saved.get("validation").get("overall").asText()).isEqualTo("PASS");
        assertThat(saved.get("validation").get("findings")).isEmpty();

        JsonNode fetched = getJson("/api/vouchers/" + id, 200);
        assertThat(fetched.get("lines")).hasSize(3);

        JsonNode approved = postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "검토자"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("POSTED");
        assertThat(approved.get("approver").asText()).isEqualTo("검토자");
        assertThat(approved.get("approvedAt").isNull()).isFalse();

        JsonNode listed = getJson("/api/vouchers?status=POSTED&month=" + MONTH, 200);
        assertThat(listed).anyMatch(node -> node.get("id").asLong() == id);

        JsonNode trialBalance = getJson("/api/reports/trial-balance?month=" + MONTH, 200);
        assertThat(trialBalance.get("balanced").asBoolean()).isTrue();
        assertThat(trialBalance.get("totalDebit").asLong()).isEqualTo(110_000);
        assertThat(trialBalance.get("totalCredit").asLong()).isEqualTo(110_000);
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        trialBalance.get("rows").forEach(row -> rows.put(row.get("accountCode").asText(), row));
        assertThat(rows.keySet()).containsExactly("135", "253", "830");
        assertThat(rows.get("830").get("debitTotal").asLong()).isEqualTo(100_000);
        assertThat(rows.get("830").get("balanceSide").asText()).isEqualTo("DEBIT");
        assertThat(rows.get("135").get("debitTotal").asLong()).isEqualTo(10_000);
        assertThat(rows.get("253").get("creditTotal").asLong()).isEqualTo(110_000);
        assertThat(rows.get("253").get("balance").asLong()).isEqualTo(-110_000);
        assertThat(rows.get("253").get("balanceSide").asText()).isEqualTo("CREDIT");

        postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "검토자"), 409);
    }

    @Test
    void draftWithoutMatchingFixtureFailsWithR11() throws Exception {
        Map<String, Object> draftRequest = Map.of(
                "description", "알 수 없는 거래 qwerty",
                "voucherDate", DATE.toString(),
                "evidenceType", "NONE",
                "vatType", "NA");
        JsonNode draft = postJson("/api/vouchers/draft", draftRequest, 200);
        assertThat(draft.get("drafter").asText()).isEqualTo("fixture");
        assertThat(draft.get("draft").get("lines")).isEmpty();
        assertThat(draft.get("validation").get("overall").asText()).isEqualTo("FAIL");
        assertThat(draft.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R11"));
    }
}
