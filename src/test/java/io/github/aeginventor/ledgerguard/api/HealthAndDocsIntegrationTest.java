package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

class HealthAndDocsIntegrationTest extends ApiTestSupport {

    @Test
    void readinessAndLivenessGroupsAreExposed() throws Exception {
        assertThat(getJson("/actuator/health", 200).get("status").asText()).isEqualTo("UP");
        assertThat(getJson("/actuator/health/readiness", 200).get("status").asText()).isEqualTo("UP");
        assertThat(getJson("/actuator/health/liveness", 200).get("status").asText()).isEqualTo("UP");
    }

    @Test
    void accountsAreSeededAndOpenApiIsPublished() throws Exception {
        JsonNode accounts = getJson("/api/accounts", 200);
        assertThat(accounts).hasSize(30);
        assertThat(accounts.get(0).get("code").asText()).isEqualTo("101");
        JsonNode openApi = getJson("/v3/api-docs", 200);
        assertThat(openApi.get("paths").has("/api/vouchers/draft")).isTrue();
    }

    @Test
    void rulesAreListedInCodeOrder() throws Exception {
        JsonNode rules = getJson("/api/rules", 200);
        assertThat(rules).hasSize(13);
        assertThat(rules.get(0).get("code").asText()).isEqualTo("R01");
        assertThat(rules.get(12).get("code").asText()).isEqualTo("R13");
        assertThat(rules.get(8).get("severity").asText()).isEqualTo("REVIEW");
        assertThat(rules.get(11).get("name").asText()).isEqualTo("PREPARER_NOT_APPROVER");
    }

    @Test
    void prometheusCountsVerdictsAndFindingsPerRule() throws Exception {
        postJson("/api/vouchers", Map.of(
                "voucherDate", LocalDate.now().minusDays(1).toString(),
                "description", "지표 테스트 차대 불일치",
                "preparer", "김작성",
                "evidenceType", "NONE",
                "vatType", "NA",
                "lines", List.of(line("830", "DEBIT", 10_000), line("101", "CREDIT", 9_000))), 201);

        var response = mockMvc.perform(MockMvcRequestBuilders.get("/actuator/prometheus")).andReturn().getResponse();
        assertThat(response.getStatus()).as("prometheus endpoint status").isEqualTo(200);
        String body = response.getContentAsString();
        assertThat(body).contains("ledger_guard_verdicts_total{");
        assertThat(body).contains("overall=\"fail\"");
        assertThat(body).contains("stage=\"save\"");
        assertThat(body).contains("ledger_guard_findings_total{");
        assertThat(body).contains("ledger_guard_validation_seconds_count{");
        assertThat(body).contains("ledger_guard_validation_seconds_max{");
        assertThat(body).contains("rule=\"R01\"");
        assertThat(body).contains("severity=\"fail\"");
    }

    @Test
    void staticCheckpointPageIsServedAtRoot() throws Exception {
        var response = mockMvc.perform(MockMvcRequestBuilders.get("/index.html")).andReturn().getResponse();
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("전표 검문소");
    }
}
