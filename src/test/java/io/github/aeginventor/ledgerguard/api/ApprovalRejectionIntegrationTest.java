package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ApprovalRejectionIntegrationTest extends ApiTestSupport {

    private static final LocalDate DATE = LocalDate.now().minusDays(1);

    @Test
    void failVoucherIsStoredAsRejectedAndApprovalReturns409() throws Exception {
        Map<String, Object> request = Map.of(
                "voucherDate", DATE.toString(),
                "description", "차대 불일치 전표",
                "preparer", "김작성",
                "counterpartyName", "거부테스트상사",
                "evidenceType", "CARD",
                "vatType", "TAXABLE",
                "supplyAmount", 100_000,
                "lines", List.of(line("830", "DEBIT", 100_000), line("135", "DEBIT", 10_000),
                        line("101", "CREDIT", 100_000)));

        JsonNode saved = postJson("/api/vouchers", request, 201);
        assertThat(saved.get("status").asText()).isEqualTo("REJECTED");
        assertThat(saved.get("validation").get("overall").asText()).isEqualTo("FAIL");
        assertThat(saved.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R01"));

        long id = saved.get("id").asLong();
        JsonNode error = postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "검토자"), 409);
        assertThat(error.get("status").asInt()).isEqualTo(409);
        assertThat(error.get("validation").get("overall").asText()).isEqualTo("FAIL");

        JsonNode fetched = getJson("/api/vouchers/" + id, 200);
        assertThat(fetched.get("status").asText()).isEqualTo("REJECTED");
    }

    @Test
    void reviewVoucherNeedsReasonToBeApproved() throws Exception {
        Map<String, Object> request = Map.of(
                "voucherDate", DATE.toString(),
                "description", "거래처 식사 접대 현금 5만원",
                "preparer", "김작성",
                "counterpartyName", "검토테스트식당",
                "evidenceType", "NONE",
                "vatType", "NA",
                "lines", List.of(line("813", "DEBIT", 50_000), line("101", "CREDIT", 50_000)));

        JsonNode saved = postJson("/api/vouchers", request, 201);
        assertThat(saved.get("status").asText()).isEqualTo("REVIEW");
        assertThat(saved.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R09"));
        long id = saved.get("id").asLong();

        JsonNode error = postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "검토자"), 409);
        assertThat(error.get("message").asText()).contains("사유");

        JsonNode approved = postJson("/api/vouchers/" + id + "/approve",
                Map.of("approver", "검토자", "reason", "영수증 분실, 참석자 확인서로 대체"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("POSTED");
        assertThat(approved.get("approvalReason").asText()).contains("영수증 분실");
    }

    @Test
    void preparerCannotApproveOwnVoucher() throws Exception {
        Map<String, Object> request = Map.of(
                "voucherDate", DATE.toString(),
                "description", "직무 분리 테스트",
                "preparer", "김작성",
                "counterpartyName", "직무분리상사",
                "evidenceType", "NONE",
                "vatType", "NA",
                "lines", List.of(line("830", "DEBIT", 10_000), line("101", "CREDIT", 10_000)));

        JsonNode saved = postJson("/api/vouchers", request, 201);
        assertThat(saved.get("status").asText()).isEqualTo("DRAFT");
        assertThat(saved.get("preparer").asText()).isEqualTo("김작성");
        long id = saved.get("id").asLong();

        JsonNode error = postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "김작성"), 409);
        assertThat(error.get("validation").get("overall").asText()).isEqualTo("FAIL");
        assertThat(error.get("validation").get("findings"))
                .anyMatch(f -> f.get("ruleCode").asText().equals("R12"));
        assertThat(getJson("/api/vouchers/" + id, 200).get("status").asText()).isEqualTo("DRAFT");

        JsonNode approved = postJson("/api/vouchers/" + id + "/approve", Map.of("approver", "박승인"), 200);
        assertThat(approved.get("status").asText()).isEqualTo("POSTED");
        assertThat(approved.get("approver").asText()).isEqualTo("박승인");
    }

    @Test
    void voucherWithoutPreparerIsRejectedWith400() throws Exception {
        Map<String, Object> request = Map.of(
                "voucherDate", DATE.toString(),
                "description", "작성자 없는 전표",
                "evidenceType", "NONE",
                "vatType", "NA",
                "lines", List.of(line("830", "DEBIT", 10_000), line("101", "CREDIT", 10_000)));
        JsonNode error = postJson("/api/vouchers", request, 400);
        assertThat(error.get("details").toString()).contains("preparer");
    }

    @Test
    void unknownVoucherReturns404AndBadBodyReturns400() throws Exception {
        getJson("/api/vouchers/999999", 404);
        postJson("/api/vouchers", Map.of("description", "필수 필드 누락"), 400);
        postJson("/api/vouchers/draft", Map.of("description", "x", "voucherDate", DATE.toString(),
                "evidenceType", "CARD", "vatType", "TAXABLE", "supplyAmount", -1), 400);
    }
}
