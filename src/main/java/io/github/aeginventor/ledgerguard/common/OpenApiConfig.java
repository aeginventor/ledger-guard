package io.github.aeginventor.ledgerguard.common;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI ledgerGuardOpenApi() {
        return new OpenAPI().info(new Info()
                .title("ledger-guard API")
                .version("0.1.0")
                .description("AI 분개 초안을 규칙 코드로 판정하는 전표 검증 API. 초안은 저장하지 않고, 승인한 전표만 원장에 전기한다."));
    }
}
