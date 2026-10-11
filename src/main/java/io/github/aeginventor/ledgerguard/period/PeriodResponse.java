package io.github.aeginventor.ledgerguard.period;

import java.time.LocalDateTime;

public record PeriodResponse(String yyyymm, boolean closed, LocalDateTime closedAt) {

    public static PeriodResponse from(AccountingPeriod period) {
        return new PeriodResponse(period.getYyyymm(), period.isClosed(), period.getClosedAt());
    }
}
