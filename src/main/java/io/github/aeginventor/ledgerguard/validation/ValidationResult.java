package io.github.aeginventor.ledgerguard.validation;

import java.util.List;

public record ValidationResult(Overall overall, List<Finding> findings) {

    public static ValidationResult of(List<Finding> findings) {
        boolean anyFail = findings.stream().anyMatch(f -> f.severity() == Severity.FAIL);
        boolean anyReview = findings.stream().anyMatch(f -> f.severity() == Severity.REVIEW);
        Overall overall = anyFail ? Overall.FAIL : anyReview ? Overall.REVIEW : Overall.PASS;
        return new ValidationResult(overall, List.copyOf(findings));
    }

    public List<String> codes() {
        return findings.stream().map(Finding::ruleCode).distinct().toList();
    }
}
