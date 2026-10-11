package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * 시산표의 month와 ytd 범위. 같은 해의 1월과 2월에 전표를 전기하고, 2월의 ytd 시산표에 1월분이 기초 잔액으로 들어오는지 본다.
 * 다른 테스트가 쓰지 않는 달을 쓰기 위해 오늘이 3월 이전이면 작년의 1월과 2월을 쓴다.
 */
class TrialBalanceScopeIntegrationTest extends ApiTestSupport {

    private static final int YEAR = LocalDate.now().getMonthValue() >= 3 ? LocalDate.now().getYear()
            : LocalDate.now().getYear() - 1;
    private static final LocalDate JAN = LocalDate.of(YEAR, 1, 15);
    private static final LocalDate FEB = LocalDate.of(YEAR, 2, 15);

    private long post(LocalDate date, String counterparty, long amount) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("voucherDate", date.toString());
        request.put("description", "시산표 범위 테스트");
        request.put("preparer", "김작성");
        request.put("counterpartyName", counterparty);
        request.put("evidenceType", "NONE");
        request.put("vatType", "NA");
        request.put("lines", List.of(line("830", "DEBIT", amount), line("101", "CREDIT", amount)));
        long id = postJson("/api/vouchers", request, 201).get("id").asLong();
        postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "박승인"), 200);
        return id;
    }

    private static Map<String, JsonNode> rows(JsonNode trialBalance) {
        Map<String, JsonNode> rows = new LinkedHashMap<>();
        trialBalance.get("rows").forEach(row -> rows.put(row.get("accountCode").asText(), row));
        return rows;
    }

    @Test
    void ytdCarriesPriorMonthsAsOpeningBalanceWhileMonthScopeDoesNot() throws Exception {
        post(JAN, "시산표범위상사 1월", 100_000);
        post(FEB, "시산표범위상사 2월", 50_000);
        String feb = YearMonth.from(FEB).toString();

        JsonNode monthly = getJson("/api/reports/trial-balance?month=" + feb, 200);
        assertThat(monthly.get("scope").asText()).isEqualTo("month");
        assertThat(monthly.get("balanced").asBoolean()).isTrue();
        assertThat(monthly.get("totalDebit").asLong()).isEqualTo(50_000);
        Map<String, JsonNode> monthlyRows = rows(monthly);
        assertThat(monthlyRows.get("830").get("debitTotal").asLong()).isEqualTo(50_000);
        assertThat(monthlyRows.get("830").has("openingBalance")).isFalse();

        JsonNode ytd = getJson("/api/reports/trial-balance?month=" + feb + "&scope=ytd", 200);
        assertThat(ytd.get("scope").asText()).isEqualTo("ytd");
        assertThat(ytd.get("balanced").asBoolean()).isTrue();
        assertThat(ytd.get("totalDebit").asLong()).isEqualTo(150_000);
        assertThat(ytd.get("totalCredit").asLong()).isEqualTo(150_000);
        Map<String, JsonNode> ytdRows = rows(ytd);
        JsonNode expense = ytdRows.get("830");
        assertThat(expense.get("openingBalance").asLong()).isEqualTo(100_000);
        assertThat(expense.get("periodDebit").asLong()).isEqualTo(50_000);
        assertThat(expense.get("periodCredit").asLong()).isZero();
        assertThat(expense.get("debitTotal").asLong()).isEqualTo(150_000);
        assertThat(expense.get("balance").asLong()).isEqualTo(150_000);
        assertThat(expense.get("balanceSide").asText()).isEqualTo("DEBIT");
        JsonNode cash = ytdRows.get("101");
        assertThat(cash.get("openingBalance").asLong()).isEqualTo(-100_000);
        assertThat(cash.get("periodCredit").asLong()).isEqualTo(50_000);
        assertThat(cash.get("balance").asLong()).isEqualTo(-150_000);
        assertThat(cash.get("balanceSide").asText()).isEqualTo("CREDIT");

        JsonNode janYtd = getJson("/api/reports/trial-balance?month=" + YearMonth.from(JAN) + "&scope=ytd", 200);
        assertThat(rows(janYtd).get("830").get("openingBalance").asLong()).isZero();
        assertThat(rows(janYtd).get("830").get("debitTotal").asLong()).isEqualTo(100_000);

        getJson("/api/reports/trial-balance?month=" + feb + "&scope=quarter", 400);
    }
}
