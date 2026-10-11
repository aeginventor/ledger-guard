package io.github.aeginventor.ledgerguard.validation;

import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** 규칙 테스트용 컨텍스트 빌더. 기본값은 2026-10-11 오늘, 마감 기간 없음, 중복 없음, SAVE 단계. */
public final class ContextBuilder {

    public static final LocalDate TODAY = LocalDate.of(2026, 10, 11);

    private LocalDate voucherDate = LocalDate.of(2026, 10, 2);
    private String description = "테스트 거래";
    private String counterpartyName = "가상상사";
    private String counterpartyBizNo;
    private EvidenceType evidenceType = EvidenceType.CARD;
    private VatType vatType = VatType.TAXABLE;
    private Long supplyAmount;
    private final List<LineData> lines = new ArrayList<>();
    private final Set<YearMonth> closed = new HashSet<>();
    private DuplicateChecker duplicateChecker = DuplicateChecker.NONE;
    private LocalDate today = TODAY;
    private Stage stage = Stage.SAVE;
    private final List<String> draftProblems = new ArrayList<>();

    public static ContextBuilder voucher() {
        return new ContextBuilder();
    }

    public ContextBuilder date(LocalDate date) {
        this.voucherDate = date;
        return this;
    }

    public ContextBuilder description(String description) {
        this.description = description;
        return this;
    }

    public ContextBuilder counterparty(String name) {
        this.counterpartyName = name;
        return this;
    }

    public ContextBuilder bizNo(String bizNo) {
        this.counterpartyBizNo = bizNo;
        return this;
    }

    public ContextBuilder evidence(EvidenceType evidenceType) {
        this.evidenceType = evidenceType;
        return this;
    }

    public ContextBuilder vat(VatType vatType) {
        this.vatType = vatType;
        return this;
    }

    public ContextBuilder supply(Long supplyAmount) {
        this.supplyAmount = supplyAmount;
        return this;
    }

    public ContextBuilder debit(String accountCode, long amount) {
        return line(accountCode, Side.DEBIT, BigDecimal.valueOf(amount));
    }

    public ContextBuilder credit(String accountCode, long amount) {
        return line(accountCode, Side.CREDIT, BigDecimal.valueOf(amount));
    }

    public ContextBuilder line(String accountCode, Side side, BigDecimal amount) {
        lines.add(new LineData(lines.size() + 1, accountCode, side, amount, null));
        return this;
    }

    public ContextBuilder closedPeriod(YearMonth month) {
        closed.add(month);
        return this;
    }

    public ContextBuilder duplicate(DuplicateChecker checker) {
        this.duplicateChecker = checker;
        return this;
    }

    public ContextBuilder today(LocalDate today) {
        this.today = today;
        return this;
    }

    public ContextBuilder stage(Stage stage) {
        this.stage = stage;
        return this;
    }

    public ContextBuilder draftProblem(String problem) {
        draftProblems.add(problem);
        return this;
    }

    public ValidationContext build() {
        VoucherData data = new VoucherData(voucherDate, description, counterpartyName, counterpartyBizNo,
                evidenceType, vatType, supplyAmount, lines);
        return new ValidationContext(data, TestAccounts.LOOKUP, closed::contains, duplicateChecker, today, stage,
                draftProblems);
    }
}
