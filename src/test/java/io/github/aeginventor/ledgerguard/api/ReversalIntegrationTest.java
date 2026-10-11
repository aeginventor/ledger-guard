package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 역분개. 다른 테스트와 겹치지 않도록 두 달 전 날짜와 고유한 거래처를 쓴다.
 * 시산표 상쇄를 확인하는 테스트는 같은 컨텍스트의 다른 전표가 섞이지 않도록 넉 달 전 달을 혼자 쓴다.
 */
class ReversalIntegrationTest extends ApiTestSupport {

    private static final LocalDate DATE = LocalDate.now().minusMonths(2).withDayOfMonth(5);
    private static final LocalDate NETTING_DATE = LocalDate.now().minusMonths(4).withDayOfMonth(5);
    private static final String NETTING_MONTH = YearMonth.from(NETTING_DATE).toString();

    private Map<String, Object> purchaseVoucher(String counterparty) {
        return purchaseVoucher(counterparty, DATE);
    }

    private Map<String, Object> purchaseVoucher(String counterparty, LocalDate date) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("voucherDate", date.toString());
        request.put("description", "사무용품 카드 구입");
        request.put("preparer", "김작성");
        request.put("counterpartyName", counterparty);
        request.put("evidenceType", "CARD");
        request.put("vatType", "TAXABLE");
        request.put("supplyAmount", 100_000);
        request.put("lines", List.of(line("830", "DEBIT", 100_000), line("135", "DEBIT", 10_000),
                line("253", "CREDIT", 110_000)));
        return request;
    }

    private long savedAndPosted(String counterparty) throws Exception {
        return savedAndPosted(counterparty, DATE);
    }

    private long savedAndPosted(String counterparty, LocalDate date) throws Exception {
        long id = postJson("/api/vouchers", purchaseVoucher(counterparty, date), 201).get("id").asLong();
        postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "박승인"), 200);
        return id;
    }

    @Test
    void reversalFlipsSidesPostsImmediatelyAndNetsToZero() throws Exception {
        long id = savedAndPosted("역분개상사", NETTING_DATE);

        JsonNode reversal = postJson("/api/vouchers/" + id + "/reverse",
                Map.of("preparer", "김작성", "approver", "박승인", "reason", "거래처 착오, 전표 취소",
                        "voucherDate", NETTING_DATE.toString()), 201);
        long reversalId = reversal.get("id").asLong();
        assertThat(reversal.get("status").asText()).isEqualTo("POSTED");
        assertThat(reversal.get("reversalOfId").asLong()).isEqualTo(id);
        assertThat(reversal.get("description").asText()).startsWith("역분개:");
        assertThat(reversal.get("drafter").asText()).isEqualTo("manual");
        assertThat(reversal.get("validation").get("overall").asText()).isEqualTo("PASS");
        JsonNode lines = reversal.get("lines");
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0).get("accountCode").asText()).isEqualTo("830");
        assertThat(lines.get(0).get("side").asText()).isEqualTo("CREDIT");
        assertThat(lines.get(1).get("accountCode").asText()).isEqualTo("135");
        assertThat(lines.get(1).get("side").asText()).isEqualTo("CREDIT");
        assertThat(lines.get(2).get("accountCode").asText()).isEqualTo("253");
        assertThat(lines.get(2).get("side").asText()).isEqualTo("DEBIT");

        JsonNode original = getJson("/api/vouchers/" + id, 200);
        assertThat(original.get("status").asText()).isEqualTo("REVERSED");
        assertThat(original.get("reversedById").asLong()).isEqualTo(reversalId);
        assertThat(original.get("lines")).hasSize(3);

        JsonNode trialBalance = getJson("/api/reports/trial-balance?month=" + NETTING_MONTH, 200);
        assertThat(trialBalance.get("balanced").asBoolean()).isTrue();
        assertThat(trialBalance.get("totalDebit").asLong()).isEqualTo(220_000);
        assertThat(trialBalance.get("rows")).hasSize(3);
        for (JsonNode row : trialBalance.get("rows")) {
            String code = row.get("accountCode").asText();
            assertThat(row.get("debitTotal").asLong()).as(code).isEqualTo(row.get("creditTotal").asLong());
            assertThat(row.get("balance").asLong()).as(code).isZero();
            assertThat(row.get("balanceSide").isNull()).as(code).isTrue();
        }

        postJson("/api/vouchers/" + id + "/reverse",
                Map.of("preparer", "김작성", "approver", "박승인", "reason", "두 번째 시도"), 409);
        postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "박승인"), 409);
        postJson("/api/vouchers/" + reversalId + "/approve", Map.of("approver", "박승인"), 409);
    }

    @Test
    void onlyPostedVouchersCanBeReversed() throws Exception {
        long draftId = postJson("/api/vouchers", purchaseVoucher("역분개미승인상사"), 201).get("id").asLong();
        JsonNode error = postJson("/api/vouchers/" + draftId + "/reverse",
                Map.of("preparer", "김작성", "approver", "박승인", "reason", "미승인 전표 역분개 시도"), 409);
        assertThat(error.get("message").asText()).contains("POSTED");
        assertThat(getJson("/api/vouchers/" + draftId, 200).get("status").asText()).isEqualTo("DRAFT");
    }

    @Test
    void reversalIsBlockedWhenPreparerApprovesItAndLeavesOriginalUntouched() throws Exception {
        long id = savedAndPosted("역분개직무분리상사");
        JsonNode error = postJson("/api/vouchers/" + id + "/reverse",
                Map.of("preparer", "박승인", "approver", "박승인", "reason", "본인 승인 시도"), 409);
        assertThat(error.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R12"));
        JsonNode original = getJson("/api/vouchers/" + id, 200);
        assertThat(original.get("status").asText()).isEqualTo("POSTED");
        assertThat(original.get("reversedById").isNull()).isTrue();
    }

    @Test
    void reversalIntoClosedPeriodIsRejectedByR07() throws Exception {
        long id = savedAndPosted("역분개마감상사");
        LocalDate closedDate = LocalDate.now().minusMonths(5).withDayOfMonth(20);
        String yyyymm = YearMonth.from(closedDate).format(DateTimeFormatter.ofPattern("yyyyMM"));
        postJson("/api/periods/" + yyyymm + "/close", Map.of(), 200);

        JsonNode error = postJson("/api/vouchers/" + id + "/reverse",
                Map.of("preparer", "김작성", "approver", "박승인", "reason", "마감 달로 역분개",
                        "voucherDate", closedDate.toString()), 409);
        assertThat(error.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R07"));
        assertThat(getJson("/api/vouchers/" + id, 200).get("status").asText()).isEqualTo("POSTED");
        assertThat(getJson("/api/vouchers?status=POSTED&month=" + YearMonth.from(closedDate), 200)).isEmpty();
    }

    @Test
    void reverseRequestNeedsPreparerApproverAndReason() throws Exception {
        long id = savedAndPosted("역분개요청검증상사");
        postJson("/api/vouchers/" + id + "/reverse", Map.of("preparer", "김작성", "approver", "박승인"), 400);
        postJson("/api/vouchers/999999/reverse",
                Map.of("preparer", "김작성", "approver", "박승인", "reason", "없는 전표"), 404);
    }

    @Test
    void eventsRecordWhoDidWhatAndFailedApprovalsLeaveNoTrace() throws Exception {
        long id = postJson("/api/vouchers", purchaseVoucher("이력상사"), 201).get("id").asLong();
        postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "김작성"), 409); // R12, 롤백
        postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "박승인"), 200);
        JsonNode reversal = postJson("/api/vouchers/" + id + "/reverse",
                Map.of("preparer", "김작성", "approver", "박승인", "reason", "이력 테스트 역분개"), 201);
        long reversalId = reversal.get("id").asLong();

        JsonNode events = getJson("/api/vouchers/" + id + "/events", 200);
        assertThat(events).hasSize(3);
        assertThat(events.get(0).get("type").asText()).isEqualTo("CREATED");
        assertThat(events.get(0).get("actor").asText()).isEqualTo("김작성");
        assertThat(events.get(0).get("toStatus").asText()).isEqualTo("DRAFT");
        assertThat(events.get(1).get("type").asText()).isEqualTo("APPROVED");
        assertThat(events.get(1).get("fromStatus").asText()).isEqualTo("DRAFT");
        assertThat(events.get(1).get("toStatus").asText()).isEqualTo("POSTED");
        assertThat(events.get(1).get("actor").asText()).isEqualTo("박승인");
        assertThat(events.get(2).get("type").asText()).isEqualTo("REVERSED");
        assertThat(events.get(2).get("reason").asText()).isEqualTo("이력 테스트 역분개");

        JsonNode reversalEvents = getJson("/api/vouchers/" + reversalId + "/events", 200);
        assertThat(reversalEvents).hasSize(2);
        assertThat(reversalEvents.get(0).get("type").asText()).isEqualTo("CREATED");
        assertThat(reversalEvents.get(1).get("type").asText()).isEqualTo("APPROVED");
        getJson("/api/vouchers/999999/events", 404);
    }
}
