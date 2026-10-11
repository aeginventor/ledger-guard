package io.github.aeginventor.ledgerguard.validation.rules;

import io.github.aeginventor.ledgerguard.validation.BusinessNumberValidator;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.Severity;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationRule;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * R06 증빙이 세금계산서이면 거래처 사업자등록번호(10자리, 체크섬 통과)가 있어야 하고,
 * 과세 거래이면 공급가액 라인과 세액 라인이 분리되어 있어야 한다.
 */
@Component
public class R06TaxInvoiceRequisitesRule implements ValidationRule {

    public static final String CODE = "R06";
    private static final String BASIS = "부가가치세법 제32조 제1항 세금계산서 필요적 기재사항(확인 필요)";

    @Override
    public String code() {
        return CODE;
    }

    @Override
    public String name() {
        return "TAX_INVOICE_REQUISITES";
    }

    @Override
    public Severity severity() {
        return Severity.FAIL;
    }

    @Override
    public List<Finding> check(ValidationContext context) {
        VoucherData voucher = context.voucher();
        if (voucher.evidenceType() != EvidenceType.TAX_INVOICE) {
            return List.of();
        }
        List<Finding> findings = new ArrayList<>();
        String bizNo = voucher.counterpartyBizNo();
        if (bizNo == null || bizNo.isBlank()) {
            findings.add(Finding.fail(CODE, "세금계산서 증빙인데 거래처 사업자등록번호가 없다", BASIS));
        } else if (BusinessNumberValidator.normalize(bizNo) == null) {
            findings.add(Finding.fail(CODE, "사업자등록번호 " + bizNo + "은 10자리 숫자 형식이 아니다", BASIS));
        } else if (!BusinessNumberValidator.isValid(bizNo)) {
            findings.add(Finding.fail(CODE, "사업자등록번호 " + bizNo + "은 체크섬 검증을 통과하지 못했다", BASIS));
        }
        if (voucher.vatType() == VatType.TAXABLE) {
            boolean hasVat = !RuleSupport.vatLines(context).isEmpty();
            boolean hasSupply = !RuleSupport.nonVatLines(context).isEmpty();
            if (!hasVat || !hasSupply) {
                findings.add(Finding.fail(CODE, "과세 세금계산서 거래는 공급가액 라인과 세액 라인이 분리되어야 한다", BASIS));
            }
        }
        return findings;
    }
}
