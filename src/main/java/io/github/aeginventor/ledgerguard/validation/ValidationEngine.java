package io.github.aeginventor.ledgerguard.validation;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 등록된 모든 규칙을 코드 순으로 끝까지 실행하고 사유를 전부 모은다. 중간에 멈추지 않는다.
 */
@Component
public class ValidationEngine {

    private final List<ValidationRule> rules;

    public ValidationEngine(List<ValidationRule> rules) {
        this.rules = rules.stream().sorted(Comparator.comparing(ValidationRule::code)).toList();
    }

    public ValidationResult evaluate(ValidationContext context) {
        List<Finding> findings = new ArrayList<>();
        for (ValidationRule rule : rules) {
            findings.addAll(rule.check(context));
        }
        return ValidationResult.of(findings);
    }

    public List<ValidationRule> rules() {
        return rules;
    }
}
