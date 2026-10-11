package io.github.aeginventor.ledgerguard.draft;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/** OPENAI_API_KEY(ledger-guard.openai.api-key)가 비어 있지 않을 때만 OpenAI 작성기를 등록한다. */
@Configuration
@ConditionalOnExpression("!'${ledger-guard.openai.api-key:}'.isBlank()")
public class OpenAiDrafterConfig {

    @Bean
    public OpenAiJournalDrafter openAiJournalDrafter(RestClient.Builder builder,
            @Value("${ledger-guard.openai.api-key}") String apiKey,
            @Value("${ledger-guard.openai.base-url:https://api.openai.com}") String baseUrl,
            @Value("${ledger-guard.openai.model:gpt-4o-mini}") String model,
            @Value("${ledger-guard.openai.json-mode:true}") boolean jsonMode,
            ObjectMapper objectMapper) {
        RestClient restClient = builder
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
        return new OpenAiJournalDrafter(restClient, model, objectMapper, jsonMode);
    }
}
