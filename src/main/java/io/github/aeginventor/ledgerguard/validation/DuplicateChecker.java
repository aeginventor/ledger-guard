package io.github.aeginventor.ledgerguard.validation;

import java.math.BigDecimal;
import java.time.LocalDate;

@FunctionalInterface
public interface DuplicateChecker {

    DuplicateChecker NONE = (date, counterpartyName, total) -> false;

    boolean exists(LocalDate voucherDate, String counterpartyName, BigDecimal totalDebit);
}
