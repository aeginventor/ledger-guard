package io.github.aeginventor.ledgerguard.period;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;

/** 회계기간 키. "2026-10"과 "202610"을 모두 받아 "202610"으로 통일한다. */
public final class PeriodKey {

    private static final DateTimeFormatter COMPACT = DateTimeFormatter.ofPattern("yyyyMM");

    private PeriodKey() {
    }

    public static String of(YearMonth month) {
        return month.format(COMPACT);
    }

    public static YearMonth parse(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("기간이 없다");
        }
        String digits = raw.replace("-", "").trim();
        if (!digits.matches("\\d{6}")) {
            throw new IllegalArgumentException("기간 형식은 yyyyMM 또는 yyyy-MM이어야 한다: " + raw);
        }
        return YearMonth.parse(digits, COMPACT);
    }
}
