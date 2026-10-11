package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R13 증빙 종류와 부가세 구분이 어긋나면 검토. 면세 공급은 세금계산서 발급 대상이 아니므로 증빙은 계산서여야 하고,
 * 과세와 영세율 공급의 증빙이 계산서이면 매입세액 공제 근거가 되지 않는다.
 * 부가가치세법 제32조 제1항 본문을 읽고 R06의 빈칸으로 찾아 더한 규칙이다(docs/legal-basis.md).
 */
@Component
public class R13EvidenceMatchesVatTypeRule implements ValidationRule {

    public static final String CODE = "R13";
    private static final String BASIS =
            "부가가치세법 제32조 제1항(면세 재화 또는 용역의 공급은 세금계산서 발급 대상에서 제외). 계산서는 법인세법 제121조와 소득세법 제163조(확인 필요)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "EVIDENCE_MATCHES_VAT_TYPE";
    }

    @Override
    public Severity severity() {
        return Severity.REVIEW;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        VoucherData voucher = context.voucher();
        EvidenceType evidence = voucher.evidenceType();
        VatType vat = voucher.vatType();
        if (evidence == null || vat == null) {
            return List.of();
        }
        if (evidence == EvidenceType.TAX_INVOICE && vat == VatType.EXEMPT) {
            return List.of(Finding.review(CODE, "면세 거래의 증빙이 세금계산서다. 면세 공급은 세금계산서 대상이 아니므로 계산서(INVOICE)인지 확인이 필요하다",
                    BASIS));
        }
        if (evidence == EvidenceType.INVOICE && (vat == VatType.TAXABLE || vat == VatType.ZERO_RATED)) {
            return List.of(Finding.review(CODE, (vat == VatType.TAXABLE ? "과세" : "영세율") + " 거래의 증빙이 계산서다. 세금계산서(TAX_INVOICE)인지 확인이 필요하다",
                    BASIS));
        }
        return List.of();
    }
}
