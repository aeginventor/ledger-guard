package io.github.aeginventor.ledgerguard.validation;

public record Finding(String ruleCode, Severity severity, String message, String basis) {

    public static Finding fail(String ruleCode, String message, String basis) {
        return new Finding(ruleCode, Severity.FAIL, message, basis);
    }

    public static Finding review(String ruleCode, String message, String basis) {
        return new Finding(ruleCode, Severity.REVIEW, message, basis);
    }

    public static Finding info(String ruleCode, String message, String basis) {
        return new Finding(ruleCode, Severity.INFO, message, basis);
    }
}
