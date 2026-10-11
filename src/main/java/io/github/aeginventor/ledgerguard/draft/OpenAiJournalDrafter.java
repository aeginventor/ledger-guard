package io.github.aeginventor.ledgerguard.draft;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI chat completions를 RestClient로 직접 호출하는 작성기. OPENAI_API_KEY가 있을 때만 빈으로 등록된다.
 * 계정과목표와 거래 설명을 주고 JSON 분개 초안만 받는다. 판정은 하지 않으며 JSON을 못 읽으면 problems로 넘겨 R11이 FAIL로 기록한다.
 * 이 저장소의 작업에서는 실제 호출을 하지 않았다. 테스트는 MockRestServiceServer로 응답을 흉내 낸다.
 */
public class OpenAiJournalDrafter implements JournalDrafter {

    public static final String NAME = "openai";
    static final String COMPLETIONS_PATH = "/v1/chat/completions";

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;

    public OpenAiJournalDrafter(RestClient restClient, String model, ObjectMapper objectMapper) {
        this.restClient = restClient;
        this.model = model;
        this.objectMapper = objectMapper;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public DraftResult draft(DraftRequest request, List<Account> accounts) {
        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0,
                "response_format", Map.of("type", "json_object"),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt(accounts)),
                        Map.of("role", "user", "content", userPrompt(request))));
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(COMPLETIONS_PATH)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            return DraftResult.failed("OpenAI 호출 실패: " + e.getMessage());
        }
        if (response == null) {
            return DraftResult.failed("OpenAI 응답이 비어 있다");
        }
        JsonNode content = response.at("/choices/0/message/content");
        if (content.isMissingNode() || content.isNull()) {
            return DraftResult.failed("OpenAI 응답에 choices[0].message.content가 없다");
        }
        return parseDraft(content.asText());
    }

    /** 모델이 돌려준 본문을 {"lines":[{accountCode, side, amount, memo}]} 로 읽는다. 실패하면 사유를 돌려준다. */
    DraftResult parseDraft(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (JsonProcessingException e) {
            return DraftResult.failed("JSON 파싱 실패: " + e.getOriginalMessage());
        }
        JsonNode lines = root.get("lines");
        if (lines == null || !lines.isArray()) {
            return DraftResult.failed("초안 JSON에 lines 배열이 없다");
        }
        List<LineData> parsed = new ArrayList<>();
        int lineNo = 1;
        for (JsonNode line : lines) {
            String accountCode = line.hasNonNull("accountCode") ? line.get("accountCode").asText() : null;
            Side side = null;
            if (line.hasNonNull("side")) {
                try {
                    side = Side.valueOf(line.get("side").asText().trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    side = null;
                }
            }
            BigDecimal amount = line.hasNonNull("amount") && line.get("amount").isNumber()
                    ? line.get("amount").decimalValue() : null;
            String memo = line.hasNonNull("memo") ? line.get("memo").asText() : null;
            parsed.add(new LineData(lineNo++, accountCode, side, amount, memo));
        }
        return new DraftResult(parsed, List.of(), "openai " + model + " 응답. 판정은 규칙 엔진이 한다");
    }

    static String systemPrompt(List<Account> accounts) {
        String table = accounts.stream()
                .filter(Account::isActive)
                .map(a -> a.getCode() + " " + a.getName() + " (" + a.getType() + ")")
                .collect(Collectors.joining("\n"));
        return """
                너는 한국 중소기업의 회계 담당자다. 거래 설명을 읽고 복식부기 분개 초안을 만든다.
                아래 계정과목표에 있는 코드만 쓴다. 금액은 원 단위 정수다.
                과세 거래는 공급가액과 부가세 라인을 분리한다. 부가세는 공급가액의 10%에서 원 미만을 버린다.
                매입 부가세는 135 부가세대급금 차변, 매출 부가세는 255 부가세예수금 대변이다.
                면세, 영세율, 부가세 대상이 아닌 거래에는 부가세 라인을 만들지 않는다.
                출력은 JSON 객체 하나만 낸다. 설명 문장을 붙이지 않는다.
                형식: {"lines":[{"accountCode":"830","side":"DEBIT","amount":100000,"memo":"소모품비"}]}

                계정과목표:
                """ + table;
    }

    static String userPrompt(DraftRequest request) {
        return "거래 설명: " + request.description()
                + "\n전표 일자: " + request.voucherDate()
                + "\n거래처: " + (request.counterpartyName() == null ? "(없음)" : request.counterpartyName())
                + "\n증빙 종류: " + request.evidenceType()
                + "\n부가세 종류: " + request.vatType()
                + "\n공급가액: " + (request.supplyAmount() == null ? "(없음)" : request.supplyAmount() + "원");
    }
}
