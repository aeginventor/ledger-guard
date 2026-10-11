package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountType;
import io.github.aeginventor.ledgerguard.validation.LedgerAccounts;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import java.math.BigDecimal;
import java.util.List;

/** 여러 규칙이 함께 쓰는 판별 함수. */
final class RuleSupport {

    private RuleSupport() {
    }

    /** 계정과목표의 vatRelated로 부가세 라인을 가린다. 계정이 없으면 코드로 대신 판단한다. */
    static boolean isVatLine(ValidationContext context, LineData line) {
        return context.account(line.accountCode())
                .map(Account::isVatRelated)
                .orElseGet(() -> LedgerAccounts.VAT_INPUT.equals(line.accountCode())
                        || LedgerAccounts.VAT_OUTPUT.equals(line.accountCode()));
    }

    static List<LineData> vatLines(ValidationContext context) {
        return context.voucher().lines().stream().filter(line -> isVatLine(context, line)).toList();
    }

    static List<LineData> nonVatLines(ValidationContext context) {
        return context.voucher().lines().stream().filter(line -> !isVatLine(context, line)).toList();
    }

    static boolean hasRevenueLine(ValidationContext context) {
        return nonVatLines(context).stream()
                .anyMatch(line -> context.account(line.accountCode())
                        .map(account -> account.getType() == AccountType.REVENUE)
                        .orElse(false));
    }

    static BigDecimal sum(List<LineData> lines) {
        return lines.stream().map(LineData::amountOrZero).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    static String won(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString() + "원";
    }
}
