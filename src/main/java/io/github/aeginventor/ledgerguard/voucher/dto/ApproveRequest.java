package io.github.aeginventor.ledgerguard.voucher.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ApproveRequest(
        @NotBlank @Size(max = 100) String approver,
        @Size(max = 500) String reason) {
}
