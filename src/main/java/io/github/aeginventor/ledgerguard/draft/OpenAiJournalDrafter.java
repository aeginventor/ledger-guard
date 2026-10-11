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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * OpenAI chat completions 형식을 RestClient로 직접 호출하는 작성기. OPENAI_API_KEY가 있을 때만 빈으로 등록된다.
 * base-url을 바꾸면 같은 형식을 제공하는 로컬 서버(Ollama의 /v1)에도 붙는다. 계정과목표와 거래 설명을 주고 JSON 분개 초안만 받는다.
 * 판정은 하지 않으며 JSON을 못 읽으면 problems로 넘겨 R11이 FAIL로 기록한다. 규칙 판정이 FAIL이면 사유를 되먹여 고친 초안을 받는다(revise).
 */
public class OpenAiJournalDrafter implements JournalDrafter {

    public static final String NAME = "openai";
    static final String COMPLETIONS_PATH = "/v1/chat/completions";

    private final RestClient restClient;
    private final String model;
    private final ObjectMapper objectMapper;
    private final boolean jsonMode;

    public OpenAiJournalDrafter(RestClient restClient, String model, ObjectMapper objectMapper) {
        this(restClient, model, objectMapper, true);
    }

    public OpenAiJournalDrafter(RestClient restClient, String model, ObjectMapper objectMapper, boolean jsonMode) {
        this.restClient = restClient;
        this.model = model;
        this.objectMapper = objectMapper;
        this.jsonMode = jsonMode;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public DraftResult draft(DraftRequest request, List<Account> accounts) {
        return complete(List.of(
                Map.of("role", "system", "content", systemPrompt(accounts)),
                Map.of("role", "user", "content", userPrompt(request))));
    }

    @Override
    public DraftResult revise(DraftRequest request, List<Account> accounts, DraftResult previous, List<String> findings) {
        String previousJson = previous.lines().isEmpty() ? "(초안 없음)" : toJson(previous.lines());
        String feedback = "앞서 낸 초안이 규칙 검증에 실패했다. 사유를 읽고 고친 초안을 같은 JSON 형식으로만 다시 내라.\n"
                + "앞선 초안: " + previousJson + "\n"
                + "실패 사유:\n- " + String.join("\n- ", findings);
        DraftResult result = complete(List.of(
                Map.of("role", "system", "content", systemPrompt(accounts)),
                Map.of("role", "user", "content", userPrompt(request)),
                Map.of("role", "assistant", "content", previousJson),
                Map.of("role", "user", "content", feedback)));
        return new DraftResult(result.lines(), result.problems(),
                (result.note() == null ? "" : result.note() + ". ") + "FAIL 사유를 되먹여 고친 초안");
    }

    private DraftResult complete(List<Map<String, String>> messages) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("temperature", 0);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        body.put("messages", messages);
        JsonNode response;
        try {
            response = restClient.post()
                    .uri(COMPLETIONS_PATH)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientException e) {
            return DraftResult.failed("LLM 호출 실패: " + e.getMessage());
        }
        if (response == null) {
            return DraftResult.failed("LLM 응답이 비어 있다");
        }
        JsonNode content = response.at("/choices/0/message/content");
        if (content.isMissingNode() || content.isNull()) {
            return DraftResult.failed("LLM 응답에 choices[0].message.content가 없다");
        }
        return parseDraft(content.asText());
    }

    /** 모델이 돌려준 본문을 {"lines":[{accountCode, side, amount, memo}]} 로 읽는다. 코드 펜스나 앞뒤 문장이 있으면 첫 JSON 객체만 뽑는다. */
    DraftResult parseDraft(String content) {
        JsonNode root;
        try {
            root = objectMapper.readTree(extractJsonObject(content));
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
            String accountCode = line.hasNonNull("accountCode") ? line.get("accountCode").asText().trim() : null;
            Side side = null;
            if (line.hasNonNull("side")) {
                try {
                    side = Side.valueOf(line.get("side").asText().trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    side = null;
                }
            }
            BigDecimal amount = null;
            if (line.hasNonNull("amount")) {
                if (line.get("amount").isNumber()) {
                    amount = line.get("amount").decimalValue();
                } else {
                    try {
                        amount = new BigDecimal(line.get("amount").asText().replace(",", "").trim());
                    } catch (NumberFormatException ignored) {
                        amount = null;
                    }
                }
            }
            String memo = line.hasNonNull("memo") ? line.get("memo").asText() : null;
            parsed.add(new LineData(lineNo++, accountCode, side, amount, memo));
        }
        return new DraftResult(parsed, List.of(), NAME + " " + model + " 응답. 판정은 규칙 엔진이 한다");
    }

    /** ```json ... ``` 펜스나 설명 문장 사이에 있는 첫 번째 { ... } 블록을 돌려준다. 없으면 원문 그대로. */
    static String extractJsonObject(String content) {
        if (content == null) {
            return "";
        }
        String text = content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String toJson(List<LineData> lines) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (LineData line : lines) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("accountCode", line.accountCode());
            m.put("side", line.side() == null ? null : line.side().name());
            m.put("amount", line.amount());
            m.put("memo", line.memo());
            out.add(m);
        }
        try {
            return objectMapper.writeValueAsString(Map.of("lines", out));
        } catch (JsonProcessingException e) {
            return "(직렬화 실패)";
        }
    }

    /**
     * 프롬프트는 RICE(역할, 지시, 맥락, 예시) 순서다. 예시 둘은 과세 매입과 부가세 대상이 아닌 급여다.
     * 계정과목표는 활성 계정만 넣는다(30개 기준 약 600자).
     */
    static String systemPrompt(List<Account> accounts) {
        String table = accounts.stream()
                .filter(Account::isActive)
                .map(a -> a.getCode() + " " + a.getName() + " (" + a.getType() + ")")
                .collect(Collectors.joining("\n"));
        return """
                [역할] 너는 한국 중소기업의 회계 담당자다. 거래 설명을 읽고 복식부기 분개 초안을 만든다.

                [지시]
                1. 아래 계정과목표에 있는 코드만 쓴다. 코드는 문자열이다.
                2. 금액은 원 단위 정수다. 소수와 음수를 쓰지 않는다.
                3. 차변 합계와 대변 합계가 같아야 한다.
                4. 과세(TAXABLE) 거래는 공급가액 라인과 부가세 라인을 분리한다. 부가세는 공급가액의 10%에서 원 미만을 버린다.
                5. 매입 부가세는 135 부가세대급금 차변, 매출 부가세는 255 부가세예수금 대변이다.
                6. 면세(EXEMPT), 영세율(ZERO_RATED), 부가세 대상 아님(NA) 거래에는 부가세 라인을 만들지 않는다.
                7. 출력은 JSON 객체 하나만 낸다. 설명 문장, 코드 펜스, 주석을 붙이지 않는다.
                   형식: {"lines":[{"accountCode":"830","side":"DEBIT","amount":100000,"memo":"소모품비"}]}

                [맥락] 계정과목표:
                """ + table + """

                [예시 1] 거래 설명: 사무용품 카드 구입, 증빙 CARD, 부가세 TAXABLE, 공급가액 100000원
                {"lines":[{"accountCode":"830","side":"DEBIT","amount":100000,"memo":"소모품비"},{"accountCode":"135","side":"DEBIT","amount":10000,"memo":"매입세액"},{"accountCode":"253","side":"CREDIT","amount":110000,"memo":"카드 미지급금"}]}

                [예시 2] 거래 설명: 9월 급여 이체, 증빙 NONE, 부가세 NA, 공급가액 (없음), 총액 3000000원
                {"lines":[{"accountCode":"801","side":"DEBIT","amount":3000000,"memo":"급여"},{"accountCode":"103","side":"CREDIT","amount":3000000,"memo":"보통예금 이체"}]}
                """;
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
