package io.github.aeginventor.ledgerguard.voucher.dto;

import io.github.aeginventor.ledgerguard.voucher.VoucherEvent;
import io.github.aeginventor.ledgerguard.voucher.VoucherStatus;
import java.time.LocalDateTime;

public record VoucherEventResponse(Long id, VoucherEvent.Type type, VoucherStatus fromStatus, VoucherStatus toStatus,
        String actor, String reason, LocalDateTime occurredAt) {

    public static VoucherEventResponse from(VoucherEvent e) {
        return new VoucherEventResponse(e.getId(), e.getType(), e.getFromStatus(), e.getToStatus(), e.getActor(),
                e.getReason(), e.getOccurredAt());
    }
}
