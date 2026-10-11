package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.validation.Overall;
import io.github.aeginventor.ledgerguard.validation.ValidationResult;
import io.github.aeginventor.ledgerguard.voucher.Voucher;
import java.util.List;

public record ValidationResultResponse(Overall overall, List<FindingResponse> findings) {

    public static ValidationResultResponse from(ValidationResult result) {
        return new ValidationResultResponse(result.overall(),
                result.findings().stream().map(FindingResponse::from).toList());
    }

    public static ValidationResultResponse from(Voucher voucher) {
        return new ValidationResultResponse(voucher.getValidationOverall(),
                voucher.getFindings().stream().map(FindingResponse::from).toList());
    }
}
