package io.github.aeginventor.ledgerguard.validation;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 등록된 규칙 목록. 판정 사유의 ruleCode가 무엇인지 클라이언트가 바로 찾게 한다. */
@RestController
@RequestMapping("/api/rules")
@Tag(name = "rules", description = "검증 규칙 목록")
public class RuleController {

    public record RuleSummary(String code, String name, Severity severity) {
    }

    private final ValidationEngine engine;

    public RuleController(ValidationEngine engine) {
        this.engine = engine;
    }

    @GetMapping
    @Operation(summary = "규칙 목록", description = "코드 순. 판정 수준(FAIL, REVIEW)과 이름을 돌려준다. 상세는 docs/rules.md")
    public List<RuleSummary> list() {
        return engine.rules().stream()
                .map(rule -> new RuleSummary(rule.code(), rule.name(), rule.severity()))
                .toList();
    }
}
