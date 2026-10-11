package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** 마감 기간 전기 거부. 다른 테스트와 겹치지 않도록 석 달 전 달을 마감한다. */
class ClosedPeriodIntegrationTest extends ApiTestSupport {

    private static final LocalDate DATE = LocalDate.now().minusMonths(3).withDayOfMonth(10);
    private static final String YYYYMM = YearMonth.from(DATE).format(DateTimeFormatter.ofPattern("yyyyMM"));

    private Map<String, Object> salaryVoucher(String counterparty) {
        return Map.of(
                "voucherDate", DATE.toString(),
                "description", "급여 지급",
                "counterpartyName", counterparty,
                "evidenceType", "NONE",
                "vatType", "NA",
                "lines", List.of(line("801", "DEBIT", 3_000_000), line("103", "CREDIT", 3_000_000)));
    }

    @Test
    void closingPeriodBlocksApprovalAndNewVouchers() throws Exception {
        JsonNode saved = postJson("/api/vouchers", salaryVoucher("마감테스트 직원A"), 201);
        assertThat(saved.get("status").asText()).isEqualTo("DRAFT");
        long id = saved.get("id").asLong();

        JsonNode closed = postJson("/api/periods/" + YYYYMM + "/close", Map.of(), 200);
        assertThat(closed.get("yyyymm").asText()).isEqualTo(YYYYMM);
        assertThat(closed.get("closed").asBoolean()).isTrue();
        assertThat(closed.get("closedAt").isNull()).isFalse();

        JsonNode error = postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "검토자"), 409);
        assertThat(error.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R07"));
        // 409로 끝난 승인은 전표를 바꾸지 않는다
        assertThat(getJson("/api/vouchers/" + id, 200).get("status").asText()).isEqualTo("DRAFT");

        JsonNode late = postJson("/api/vouchers", salaryVoucher("마감테스트 직원B"), 201);
        assertThat(late.get("status").asText()).isEqualTo("REJECTED");
        assertThat(late.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R07"));

        JsonNode again = postJson("/api/periods/" + YearMonth.from(DATE) + "/close", Map.of(), 200);
        assertThat(again.get("closedAt").asText()).isEqualTo(closed.get("closedAt").asText());
        assertThat(getJson("/api/periods", 200)).anyMatch(p -> p.get("yyyymm").asText().equals(YYYYMM));
    }
}
