package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.voucher.ValidationFinding;

public record FindingResponse(String ruleCode, Severity severity, String message, String basis) {

    public static FindingResponse from(Finding finding) {
        return new FindingResponse(finding.ruleCode(), finding.severity(), finding.message(), finding.basis());
    }

    public static FindingResponse from(ValidationFinding finding) {
        return new FindingResponse(finding.getRuleCode(), finding.getSeverity(), finding.getMessage(),
                finding.getBasis());
    }
}
