package io.github.aeginventor.ledgerguard.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

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
}
