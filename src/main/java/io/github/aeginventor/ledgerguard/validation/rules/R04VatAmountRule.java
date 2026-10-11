package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R04 과세 거래의 부가세 라인 금액은 공급가액의 10%에서 원 미만을 버린 값이어야 한다.
 * 면세, 영세율, 부가세 대상 아님(NA) 거래에는 부가세 라인이 없어야 한다.
 */
@Component
public class R04VatAmountRule implements ValidationRule {

    public static final String CODE = "R04";
    private static final String BASIS =
            "부가가치세법 제30조(세율 10%); 원 미만 버림은 국고금관리법 제47조(확인 필요)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "VAT_AMOUNT";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    /** 공급가액의 10%, 원 미만 버림. */
    public static long expectedVat(long supplyAmount) {
        return Math.floorDiv(supplyAmount * 10, 100);
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        VoucherData voucher = context.voucher();
        List<LineData> vatLines = RuleSupport.vatLines(context);
        VatType vatType = voucher.vatType();
        if (vatType == null) {
            return List.of();
        }
        if (vatType == VatType.TAXABLE) {
            if (voucher.supplyAmount() == null) {
                return List.of();
            }
            BigDecimal expected = BigDecimal.valueOf(expectedVat(voucher.supplyAmount()));
            BigDecimal actual = RuleSupport.sum(vatLines);
            if (actual.compareTo(expected) != 0) {
                return List.of(Finding.fail(CODE,
                        "부가세 라인 합계 " + RuleSupport.won(actual) + "이 공급가액 " + voucher.supplyAmount()
                                + "원의 10%(원 미만 버림) " + RuleSupport.won(expected) + "과 다르다",
                        BASIS));
            }
            return List.of();
        }
        if (!vatLines.isEmpty()) {
            String label = switch (vatType) {
                case EXEMPT -> "면세";
                case ZERO_RATED -> "영세율";
                default -> "부가세 대상이 아닌";
            };
            return List.of(Finding.fail(CODE,
                    label + " 거래인데 부가세 라인이 " + vatLines.size() + "개 있다 (합계 "
                            + RuleSupport.won(RuleSupport.sum(vatLines)) + ")",
                    BASIS));
        }
        return List.of();
    }
}
