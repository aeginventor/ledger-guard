package io.github.aeginventor.ledgerguard.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.account.AccountType;
import io.github.aeginventor.ledgerguard.account.Side;
import io.github.aeginventor.ledgerguard.period.PeriodKey;
import io.github.aeginventor.ledgerguard.validation.DuplicateChecker;
import io.github.aeginventor.ledgerguard.validation.LedgerAccounts;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Overall;
import io.github.aeginventor.ledgerguard.validation.Stage;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationEngine;
import io.github.aeginventor.ledgerguard.validation.ValidationResult;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.EvidenceType;
import io.github.aeginventor.ledgerguard.voucher.VatType;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * 변이 평가. 사람이 결함 전표를 하나씩 쓰는 대신, 정상 전표 27건에 변이 연산자를 기계적으로 적용해 결함 전표를 만들고
 * 규칙이 그 결함을 잡는지 센다. 손으로 만든 faulty_vouchers.json과 달리 결함의 종류와 위치를 작성자가 고르지 않으므로
 * "같은 AI가 규칙과 결함을 만든" 순환에서 한 발 벗어난 두 번째 측정이다.
 *
 * 결함 변이 14종은 전부 검출되어야 하고, 의미 변이(같은 유형 안에서 계정만 바꿈) 1종은 전부 통과해야 한다.
 * 후자는 형식 규칙의 한계를 수치로 고정한 것이며, 의미 규칙을 추가하면 이 테스트를 고쳐야 한다.
 * 결과는 build/reports/mutation-summary.md 에 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MutationEvaluationTest {

    private static final Path SUMMARY = Path.of("build", "reports", "mutation-summary.md");

    @Autowired
    private ValidationEngine engine;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, Account> accounts;
    private List<Account> catalog;
    private GoldenFile.Setup setup;
    private List<Seed> seeds;
    private List<OperatorResult> results;

    /** 정상 전표 한 건과 그 단계. */
    record Seed(String id, String title, VoucherData voucher, Stage stage) {
    }

    /** 변이 하나의 결과물. expected는 반드시 검출돼야 하는 코드, duplicateOfSelf는 R10용 기존 전표 등록, approver는 R12용. */
    record Mutated(VoucherData voucher, Set<String> expected, Stage stage, boolean duplicateOfSelf, String approver,
            LocalDate today) {

        static Mutated fail(VoucherData voucher, String... codes) {
            return new Mutated(voucher, Set.of(codes), null, false, null, null);
        }

        Mutated stage(Stage s) {
            return new Mutated(voucher, expected, s, duplicateOfSelf, approver, today);
        }

        Mutated approver(String a) {
            return new Mutated(voucher, expected, stage, duplicateOfSelf, a, today);
        }

        Mutated asDuplicateOfSelf() {
            return new Mutated(voucher, expected, stage, true, approver, today);
        }
    }

    record Operator(String name, String description, boolean semantic, Function<Seed, Optional<Mutated>> apply) {
    }

    record Outcome(Seed seed, Mutated mutated, ValidationResult result) {

        boolean detected() {
            return result.overall() != Overall.PASS && new HashSet<>(result.codes()).containsAll(mutated.expected());
        }

        boolean passed() {
            return result.overall() == Overall.PASS;
        }
    }

    record OperatorResult(Operator operator, List<Outcome> outcomes) {

        long applicable() {
            return outcomes.size();
        }

        long detected() {
            return outcomes.stream().filter(Outcome::detected).count();
        }

        long passed() {
            return outcomes.stream().filter(Outcome::passed).count();
        }

        List<Outcome> misses() {
            return operator.semantic()
                    ? outcomes.stream().filter(o -> !o.passed()).toList()
                    : outcomes.stream().filter(o -> !o.detected()).toList();
        }
    }

    @BeforeAll
    void evaluateAll() throws IOException {
        accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getCode, Function.identity()));
        catalog = accountRepository.findAllByOrderByCodeAsc();
        GoldenFile file;
        try (InputStream in = getClass().getResourceAsStream("/golden/valid_vouchers.json")) {
            file = objectMapper.readValue(in, GoldenFile.class);
        }
        setup = file.setup();
        seeds = new ArrayList<>();
        for (GoldenFile.GoldenCase c : file.cases()) {
            GoldenFile.GoldenVoucher v = c.voucher();
            List<LineData> lines = new ArrayList<>();
            for (int i = 0; i < v.lines().size(); i++) {
                GoldenFile.GoldenLine line = v.lines().get(i);
                lines.add(new LineData(i + 1, line.accountCode(), line.side(), line.amount(), line.memo()));
            }
            VoucherData data = new VoucherData(v.voucherDate(), v.description(), v.counterpartyName(),
                    v.counterpartyBizNo(), v.evidenceType(), v.vatType(), v.supplyAmount(), lines, v.preparer(), null);
            seeds.add(new Seed(c.id(), c.title(), data, c.stage() == null ? Stage.SAVE : Stage.valueOf(c.stage())));
        }
        results = new ArrayList<>();
        for (Operator operator : operators()) {
            List<Outcome> outcomes = new ArrayList<>();
            for (Seed seed : seeds) {
                operator.apply().apply(seed).ifPresent(mutated -> outcomes.add(
                        new Outcome(seed, mutated, engine.evaluate(context(seed, mutated)))));
            }
            results.add(new OperatorResult(operator, outcomes));
        }
    }

    @AfterAll
    void writeSummary() throws IOException {
        Files.createDirectories(SUMMARY.getParent());
        Files.writeString(SUMMARY, render(), StandardCharsets.UTF_8);
    }

    @Test
    void seedsAllPassBeforeMutation() {
        List<String> notPass = seeds.stream()
                .filter(s -> engine.evaluate(context(s, new Mutated(s.voucher(), Set.of(), s.stage(), false, null, null)))
                        .overall() != Overall.PASS)
                .map(Seed::id).toList();
        assertThat(notPass).as("변이 전 정상 전표는 전부 PASS여야 한다").isEmpty();
    }

    @Test
    void everyFaultMutationIsDetected() {
        List<String> misses = new ArrayList<>();
        for (OperatorResult r : results) {
            if (r.operator().semantic()) {
                continue;
            }
            assertThat(r.applicable()).as(r.operator().name() + " 적용 가능 건수").isGreaterThan(0);
            for (Outcome o : r.misses()) {
                misses.add(r.operator().name() + " on " + o.seed().id() + " expected " + o.mutated().expected()
                        + " actual " + o.result().codes() + " overall " + o.result().overall());
            }
        }
        assertThat(misses).as("규칙이 놓친 결함 변이").isEmpty();
    }

    @Test
    void semanticMutationsStillPass() {
        for (OperatorResult r : results) {
            if (!r.operator().semantic()) {
                continue;
            }
            assertThat(r.applicable()).as(r.operator().name() + " 적용 가능 건수").isGreaterThan(20);
            List<String> caught = r.misses().stream()
                    .map(o -> o.seed().id() + " -> " + o.result().codes()).toList();
            assertThat(caught).as("규칙이 잡기 시작한 의미 변이 (문서의 한계 절을 갱신해야 함)").isEmpty();
        }
    }

    // ---- 변이 연산자 ----

    private List<Operator> operators() {
        return List.of(
                new Operator("SIDE_FLIP", "1번 라인의 차대를 뒤집는다", false, seed ->
                        Optional.of(Mutated.fail(withLines(seed.voucher(), replace(seed.voucher().lines(), 0,
                                l -> new LineData(l.lineNo(), l.accountCode(), l.side().opposite(), l.amount(), l.memo()))),
                                "R01"))),
                new Operator("VAT_PLUS_ONE", "부가세 라인에 1원을 더하고 반대쪽 라인도 1원 올려 차대는 맞춘다(반올림 실수)", false, seed -> {
                    int vat = firstVatLine(seed.voucher());
                    if (vat < 0) {
                        return Optional.empty();
                    }
                    List<LineData> lines = seed.voucher().lines();
                    Side vatSide = lines.get(vat).side();
                    int counter = firstLine(lines, l -> l.side() != vatSide && !isVat(l));
                    if (counter < 0) {
                        return Optional.empty();
                    }
                    List<LineData> mutated = replace(lines, vat, l -> withAmount(l, l.amount().add(BigDecimal.ONE)));
                    mutated = replace(mutated, counter, l -> withAmount(l, l.amount().add(BigDecimal.ONE)));
                    return Optional.of(Mutated.fail(withLines(seed.voucher(), mutated), "R04"));
                }),
                new Operator("VAT_LINE_REMOVED", "과세 거래에서 부가세 라인을 빼고 반대쪽 라인에서 같은 금액을 뺀다", false, seed -> {
                    VoucherData v = seed.voucher();
                    int vat = firstVatLine(v);
                    if (v.vatType() != VatType.TAXABLE || vat < 0) {
                        return Optional.empty();
                    }
                    LineData vatLine = v.lines().get(vat);
                    int counter = firstLine(v.lines(), l -> l.side() != vatLine.side() && !isVat(l)
                            && l.amount().compareTo(vatLine.amount()) > 0);
                    if (counter < 0) {
                        return Optional.empty();
                    }
                    List<LineData> mutated = new ArrayList<>(v.lines());
                    mutated.set(counter, withAmount(mutated.get(counter), mutated.get(counter).amount().subtract(vatLine.amount())));
                    mutated.remove(vat);
                    Set<String> expected = v.evidenceType() == EvidenceType.TAX_INVOICE ? Set.of("R04", "R06") : Set.of("R04");
                    return Optional.of(new Mutated(withLines(v, renumber(mutated)), expected, null, false, null, null));
                }),
                new Operator("VAT_ON_NON_TAXABLE", "면세, 영세율, 부가세 대상 아님 거래에 부가세대급금 차변을 붙이고 대변 라인을 같이 올린다", false, seed -> {
                    VoucherData v = seed.voucher();
                    if (v.vatType() == VatType.TAXABLE || firstVatLine(v) >= 0) {
                        return Optional.empty();
                    }
                    int credit = firstLine(v.lines(), l -> l.side() == Side.CREDIT);
                    if (credit < 0) {
                        return Optional.empty();
                    }
                    BigDecimal vat = v.sum(Side.DEBIT).divide(BigDecimal.TEN).setScale(0, java.math.RoundingMode.DOWN).max(BigDecimal.ONE);
                    List<LineData> mutated = new ArrayList<>(v.lines());
                    mutated.set(credit, withAmount(mutated.get(credit), mutated.get(credit).amount().add(vat)));
                    mutated.add(new LineData(mutated.size() + 1, LedgerAccounts.VAT_INPUT, Side.DEBIT, vat, "변이"));
                    return Optional.of(Mutated.fail(withLines(v, mutated), "R04"));
                }),
                new Operator("INACTIVE_ACCOUNT", "부가세가 아닌 첫 라인의 계정을 사용 중지된 150 제품으로 바꾼다", false, seed ->
                        swapAccount(seed.voucher(), "150").map(v -> Mutated.fail(v, "R02"))),
                new Operator("UNKNOWN_ACCOUNT", "부가세가 아닌 첫 라인의 계정을 계정과목표에 없는 999로 바꾼다", false, seed ->
                        swapAccount(seed.voucher(), "999").map(v -> Mutated.fail(v, "R02"))),
                new Operator("ZERO_AMOUNT", "1번 라인 금액을 0으로", false, seed ->
                        Optional.of(Mutated.fail(withLines(seed.voucher(), replace(seed.voucher().lines(), 0,
                                l -> withAmount(l, BigDecimal.ZERO))), "R03"))),
                new Operator("NEGATIVE_AMOUNT", "1번 라인 금액의 부호를 뒤집는다", false, seed ->
                        Optional.of(Mutated.fail(withLines(seed.voucher(), replace(seed.voucher().lines(), 0,
                                l -> withAmount(l, l.amount().negate()))), "R03"))),
                new Operator("DECIMAL_AMOUNT", "1번 라인 금액에 0.5원을 더한다", false, seed ->
                        Optional.of(Mutated.fail(withLines(seed.voucher(), replace(seed.voucher().lines(), 0,
                                l -> withAmount(l, l.amount().add(new BigDecimal("0.5"))))), "R03"))),
                new Operator("FUTURE_DATE", "전표 일자를 오늘 다음 날로", false, seed ->
                        Optional.of(Mutated.fail(withDate(seed.voucher(), setup.today().plusDays(1)), "R08"))),
                new Operator("CLOSED_PERIOD_DATE", "전표 일자를 마감된 달의 15일로", false, seed -> {
                    if (setup.closedPeriods() == null || setup.closedPeriods().isEmpty()) {
                        return Optional.empty();
                    }
                    YearMonth closed = PeriodKey.parse(setup.closedPeriods().get(0));
                    return Optional.of(Mutated.fail(withDate(seed.voucher(), closed.atDay(15)), "R07"));
                }),
                new Operator("DUPLICATE_RESUBMIT", "같은 전표를 한 번 더 올린다(기존 전표로 자기 자신을 등록)", false, seed -> {
                    if (seed.voucher().counterpartyName() == null || seed.voucher().counterpartyName().isBlank()) {
                        return Optional.empty();
                    }
                    return Optional.of(Mutated.fail(seed.voucher(), "R10").asDuplicateOfSelf());
                }),
                new Operator("ENTERTAINMENT_EVIDENCE_DROPPED", "기업업무추진비 3만원 초과 전표의 증빙을 없앤다", false, seed -> {
                    VoucherData v = seed.voucher();
                    BigDecimal ent = v.lines().stream()
                            .filter(l -> LedgerAccounts.ENTERTAINMENT.equals(l.accountCode()) && l.side() == Side.DEBIT)
                            .map(LineData::amountOrZero).reduce(BigDecimal.ZERO, BigDecimal::add);
                    if (ent.compareTo(BigDecimal.valueOf(30_000)) <= 0 || v.evidenceType() == EvidenceType.NONE) {
                        return Optional.empty();
                    }
                    return Optional.of(Mutated.fail(withEvidence(v, EvidenceType.NONE), "R09"));
                }),
                new Operator("EVIDENCE_MISMATCH", "면세 거래의 증빙을 세금계산서로, 과세 거래의 증빙을 계산서로 바꾼다", false, seed -> {
                    VoucherData v = seed.voucher();
                    if (v.vatType() == VatType.EXEMPT) {
                        return Optional.of(Mutated.fail(withEvidence(withBizNo(v, "123-45-67891"), EvidenceType.TAX_INVOICE), "R13"));
                    }
                    if (v.vatType() == VatType.TAXABLE) {
                        return Optional.of(Mutated.fail(withEvidence(v, EvidenceType.INVOICE), "R13"));
                    }
                    return Optional.empty();
                }),
                new Operator("SELF_APPROVAL", "작성자가 자기 전표를 승인한다(승인 단계)", false, seed ->
                        Optional.of(Mutated.fail(withPreparer(seed.voucher(), "김작성"), "R12").stage(Stage.APPROVE).approver("김작성"))),
                new Operator("SAME_TYPE_ACCOUNT_SWAP", "부가세가 아닌 첫 라인의 계정을 같은 유형의 다른 활성 계정으로 바꾼다(의미 변이, 형식 규칙은 못 잡음)", true, seed -> {
                    VoucherData v = seed.voucher();
                    int idx = firstLine(v.lines(), l -> !isVat(l));
                    if (idx < 0) {
                        return Optional.empty();
                    }
                    Account current = accounts.get(v.lines().get(idx).accountCode());
                    if (current == null) {
                        return Optional.empty();
                    }
                    Optional<Account> other = sameTypeAlternative(current);
                    return other.map(a -> new Mutated(withLines(v, replace(v.lines(), idx,
                            l -> new LineData(l.lineNo(), a.getCode(), l.side(), l.amount(), l.memo()))), Set.of(), null, false, null, null));
                }));
    }

    // ---- 도우미 ----

    private ValidationContext context(Seed seed, Mutated mutated) {
        Set<YearMonth> closed = setup.closedPeriods() == null ? Set.of()
                : setup.closedPeriods().stream().map(PeriodKey::parse).collect(Collectors.toSet());
        VoucherData v = mutated.voucher();
        DuplicateChecker duplicates = mutated.duplicateOfSelf()
                ? (date, name, total) -> date.equals(v.voucherDate()) && name.equals(v.counterpartyName())
                        && total.compareTo(v.sum(Side.DEBIT)) == 0
                : DuplicateChecker.NONE;
        Stage stage = mutated.stage() != null ? mutated.stage() : seed.stage();
        return new ValidationContext(v, code -> Optional.ofNullable(accounts.get(code)), closed::contains, duplicates,
                mutated.today() != null ? mutated.today() : setup.today(), stage, List.of(), mutated.approver());
    }

    private boolean isVat(LineData line) {
        Account account = accounts.get(line.accountCode());
        return account != null && account.isVatRelated();
    }

    private int firstVatLine(VoucherData v) {
        return firstLine(v.lines(), this::isVat);
    }

    private static int firstLine(List<LineData> lines, java.util.function.Predicate<LineData> test) {
        for (int i = 0; i < lines.size(); i++) {
            if (test.test(lines.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private Optional<VoucherData> swapAccount(VoucherData v, String newCode) {
        int idx = firstLine(v.lines(), l -> !isVat(l));
        if (idx < 0) {
            return Optional.empty();
        }
        return Optional.of(withLines(v, replace(v.lines(), idx,
                l -> new LineData(l.lineNo(), newCode, l.side(), l.amount(), l.memo()))));
    }

    /** 같은 유형, 활성, 부가세 계정이 아닌, 기업업무추진비(R09 대상)도 아닌 다른 계정. 코드 순으로 다음 것을 고른다. */
    private Optional<Account> sameTypeAlternative(Account current) {
        List<Account> candidates = catalog.stream()
                .filter(a -> a.getType() == current.getType() && a.isActive() && !a.isVatRelated()
                        && !a.getCode().equals(current.getCode()) && !LedgerAccounts.ENTERTAINMENT.equals(a.getCode()))
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }
        return candidates.stream().filter(a -> a.getCode().compareTo(current.getCode()) > 0).findFirst()
                .or(() -> Optional.of(candidates.get(0)));
    }

    private static List<LineData> replace(List<LineData> lines, int index, Function<LineData, LineData> change) {
        List<LineData> copy = new ArrayList<>(lines);
        copy.set(index, change.apply(copy.get(index)));
        return copy;
    }

    private static List<LineData> renumber(List<LineData> lines) {
        List<LineData> out = new ArrayList<>();
        for (int i = 0; i < lines.size(); i++) {
            LineData l = lines.get(i);
            out.add(new LineData(i + 1, l.accountCode(), l.side(), l.amount(), l.memo()));
        }
        return out;
    }

    private static LineData withAmount(LineData l, BigDecimal amount) {
        return new LineData(l.lineNo(), l.accountCode(), l.side(), amount, l.memo());
    }

    private static VoucherData withLines(VoucherData v, List<LineData> lines) {
        return new VoucherData(v.voucherDate(), v.description(), v.counterpartyName(), v.counterpartyBizNo(),
                v.evidenceType(), v.vatType(), v.supplyAmount(), lines, v.preparer(), v.reversalOfId());
    }

    private static VoucherData withDate(VoucherData v, LocalDate date) {
        return new VoucherData(date, v.description(), v.counterpartyName(), v.counterpartyBizNo(),
                v.evidenceType(), v.vatType(), v.supplyAmount(), v.lines(), v.preparer(), v.reversalOfId());
    }

    private static VoucherData withEvidence(VoucherData v, EvidenceType evidence) {
        return new VoucherData(v.voucherDate(), v.description(), v.counterpartyName(), v.counterpartyBizNo(),
                evidence, v.vatType(), v.supplyAmount(), v.lines(), v.preparer(), v.reversalOfId());
    }

    private static VoucherData withBizNo(VoucherData v, String bizNo) {
        return new VoucherData(v.voucherDate(), v.description(), v.counterpartyName(), bizNo,
                v.evidenceType(), v.vatType(), v.supplyAmount(), v.lines(), v.preparer(), v.reversalOfId());
    }

    private static VoucherData withPreparer(VoucherData v, String preparer) {
        return new VoucherData(v.voucherDate(), v.description(), v.counterpartyName(), v.counterpartyBizNo(),
                v.evidenceType(), v.vatType(), v.supplyAmount(), v.lines(), preparer, v.reversalOfId());
    }

    private String render() {
        StringBuilder sb = new StringBuilder();
        long faultApplicable = results.stream().filter(r -> !r.operator().semantic()).mapToLong(OperatorResult::applicable).sum();
        long faultDetected = results.stream().filter(r -> !r.operator().semantic()).mapToLong(OperatorResult::detected).sum();
        long semanticApplicable = results.stream().filter(r -> r.operator().semantic()).mapToLong(OperatorResult::applicable).sum();
        long semanticPassed = results.stream().filter(r -> r.operator().semantic()).mapToLong(OperatorResult::passed).sum();
        sb.append("# 변이 평가 결과\n\n");
        sb.append("생성 시각: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("재현 명령: `./gradlew test --tests 'io.github.aeginventor.ledgerguard.golden.MutationEvaluationTest'`\n\n");
        sb.append("정상 전표 ").append(seeds.size()).append("건에 변이 연산자 ").append(results.size()).append("종을 적용했다. ");
        sb.append("결함 변이 ").append(faultApplicable).append("건 중 ").append(faultDetected).append("건 검출, ");
        sb.append("의미 변이 ").append(semanticApplicable).append("건 중 ").append(semanticPassed).append("건 통과(형식 규칙의 한계).\n\n");
        sb.append("| 연산자 | 설명 | 적용 | 검출 | 통과 | 놓침 |\n|---|---|---|---|---|---|\n");
        for (OperatorResult r : results) {
            sb.append("| ").append(r.operator().name()).append(" | ").append(r.operator().description()).append(" | ")
                    .append(r.applicable()).append(" | ").append(r.operator().semantic() ? "-" : String.valueOf(r.detected()))
                    .append(" | ").append(r.passed()).append(" | ")
                    .append(r.misses().stream().map(o -> o.seed().id()).collect(Collectors.joining(", "))).append(" |\n");
        }
        sb.append("\n## 의미 변이 상세\n\n| 정상 전표 | 원래 계정 | 바뀐 계정 | 판정 |\n|---|---|---|---|\n");
        for (OperatorResult r : results) {
            if (!r.operator().semantic()) {
                continue;
            }
            for (Outcome o : r.outcomes()) {
                int idx = firstLine(o.seed().voucher().lines(), l -> !isVat(l));
                String from = o.seed().voucher().lines().get(idx).accountCode();
                String to = o.mutated().voucher().lines().get(idx).accountCode();
                sb.append("| ").append(o.seed().id()).append(' ').append(o.seed().title()).append(" | ")
                        .append(from).append(' ').append(name(from)).append(" | ")
                        .append(to).append(' ').append(name(to)).append(" | ").append(o.result().overall()).append(" |\n");
            }
        }
        return sb.toString();
    }

    private String name(String code) {
        Account a = accounts.get(code);
        return a == null ? "" : a.getName();
    }

    static AccountType typeOf(Account a) {
        return a.getType();
    }
}
