package io.github.aeginventor.ledgerguard.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.aeginventor.ledgerguard.voucher.dto.ValidationResultResponse;
import java.time.LocalDateTime;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        int status,
        String error,
        String message,
        List<String> details,
        ValidationResultResponse validation,
        LocalDateTime timestamp) {
}
