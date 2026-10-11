package io.github.aeginventor.ledgerguard.golden;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.period.PeriodKey;
import io.github.aeginventor.ledgerguard.validation.DuplicateChecker;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Overall;
import io.github.aeginventor.ledgerguard.validation.Stage;
import io.github.aeginventor.ledgerguard.validation.ValidationContext;
import io.github.aeginventor.ledgerguard.validation.ValidationEngine;
import io.github.aeginventor.ledgerguard.validation.ValidationResult;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * 골든셋 평가 하네스. 계정과목표는 Flyway 시드(H2)를 그대로 쓰고, 오늘 날짜와 마감 기간과 기존 전표는 JSON의 setup으로 고정한다.
 * 결과 요약을 build/reports/golden-summary.md 에 쓴다.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GoldenSetEvaluationTest {

    private static final Path SUMMARY = Path.of("build", "reports", "golden-summary.md");

    @Autowired
    private ValidationEngine engine;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Map<String, Account> accounts;
    private List<Evaluated> valid;
    private List<Evaluated> faulty;
    private List<Evaluated> semantic;

    record Evaluated(GoldenFile.GoldenCase goldenCase, ValidationResult result) {

        List<String> codes() {
            return result.codes();
        }

        boolean expectedAllDetected() {
            return new HashSet<>(codes()).containsAll(goldenCase.expectedCodes());
        }

        boolean exactMatch() {
            return new HashSet<>(codes()).equals(new HashSet<>(goldenCase.expectedCodes()));
        }
    }

    @BeforeAll
    void evaluateAll() throws IOException {
        accounts = accountRepository.findAll().stream()
                .collect(Collectors.toMap(Account::getCode, Function.identity()));
        valid = evaluate(load("valid_vouchers.json"));
        faulty = evaluate(load("faulty_vouchers.json"));
        semantic = evaluate(load("semantic_misses.json"));
    }

    @AfterAll
    void writeSummary() throws IOException {
        Files.createDirectories(SUMMARY.getParent());
        Files.writeString(SUMMARY, render(), StandardCharsets.UTF_8);
    }

    @Test
    void validVouchersAllPassWithZeroFalsePositives() {
        assertThat(valid).hasSizeGreaterThanOrEqualTo(20);
        List<String> falsePositives = valid.stream()
                .filter(e -> e.result().overall() != Overall.PASS)
                .map(e -> e.goldenCase().id() + " " + e.goldenCase().title() + " -> " + e.result().findings())
                .toList();
        assertThat(falsePositives).as("거짓 양성 (valid인데 PASS가 아님)").isEmpty();
    }

    @Test
    void faultyVouchersTriggerEveryExpectedCode() {
        assertThat(faulty).hasSizeGreaterThanOrEqualTo(20);
        List<String> missed = faulty.stream()
                .filter(e -> !e.expectedAllDetected() || e.result().overall() == Overall.PASS)
                .map(e -> e.goldenCase().id() + " expected " + e.goldenCase().expectedCodes() + " actual " + e.codes())
                .toList();
        assertThat(missed).as("expectedCodes를 놓친 결함 전표").isEmpty();
        Set<String> coveredCodes = faulty.stream().flatMap(e -> e.goldenCase().expectedCodes().stream())
                .collect(Collectors.toSet());
        assertThat(coveredCodes).containsExactlyInAnyOrder("R01", "R02", "R03", "R04", "R05", "R06", "R07", "R08",
                "R09", "R10", "R11");
    }

    /**
     * 규칙이 못 잡는 결함을 고정한다. 차대가 맞고 계정도 유효하지만 계정 선택이 틀린 전표는 현재 규칙으로 PASS가 나온다.
     * 이 테스트가 통과한다는 것은 한계가 그대로라는 뜻이다. 의미 검증 규칙을 추가하면 이 테스트를 고쳐야 한다.
     */
    @Test
    void semanticMissesStillPassUnderCurrentRules() {
        assertThat(semantic).hasSizeGreaterThanOrEqualTo(3);
        List<String> caught = semantic.stream()
                .filter(e -> e.result().overall() != Overall.PASS)
                .map(e -> e.goldenCase().id() + " -> " + e.codes())
                .toList();
        assertThat(caught).as("규칙이 잡기 시작한 의미 결함 (문서의 한계 절을 갱신해야 함)").isEmpty();
    }

    private GoldenFile load(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/golden/" + name)) {
            if (in == null) {
                throw new IOException("golden/" + name + " 없음");
            }
            return objectMapper.readValue(in, GoldenFile.class);
        }
    }

    private List<Evaluated> evaluate(GoldenFile file) {
        GoldenFile.Setup setup = file.setup();
        Set<YearMonth> closed = setup.closedPeriods() == null ? Set.of()
                : setup.closedPeriods().stream().map(PeriodKey::parse).collect(Collectors.toSet());
        List<GoldenFile.ExistingVoucher> existing = setup.existingVouchers() == null ? List.of()
                : setup.existingVouchers();
        DuplicateChecker duplicates = (date, name, total) -> existing.stream()
                .anyMatch(e -> e.voucherDate().equals(date) && e.counterpartyName().equals(name)
                        && e.totalDebit().compareTo(total) == 0);
        List<Evaluated> evaluated = new ArrayList<>();
        for (GoldenFile.GoldenCase goldenCase : file.cases()) {
            GoldenFile.GoldenVoucher v = goldenCase.voucher();
            List<LineData> lines = new ArrayList<>();
            List<GoldenFile.GoldenLine> rawLines = v.lines() == null ? List.of() : v.lines();
            for (int i = 0; i < rawLines.size(); i++) {
                GoldenFile.GoldenLine line = rawLines.get(i);
                lines.add(new LineData(i + 1, line.accountCode(), line.side(), line.amount(), line.memo()));
            }
            VoucherData data = new VoucherData(v.voucherDate(), v.description(), v.counterpartyName(),
                    v.counterpartyBizNo(), v.evidenceType(), v.vatType(), v.supplyAmount(), lines);
            Stage stage = goldenCase.stage() == null ? Stage.SAVE : Stage.valueOf(goldenCase.stage());
            ValidationContext context = new ValidationContext(data,
                    code -> Optional.ofNullable(accounts.get(code)), closed::contains, duplicates, setup.today(),
                    stage, goldenCase.draftProblems());
            evaluated.add(new Evaluated(goldenCase, engine.evaluate(context)));
        }
        return evaluated;
    }

    private String render() {
        StringBuilder sb = new StringBuilder();
        long validPass = valid.stream().filter(e -> e.result().overall() == Overall.PASS).count();
        long faultyDetected = faulty.stream().filter(e -> e.expectedAllDetected() && e.result().overall() != Overall.PASS).count();
        long faultyExact = faulty.stream().filter(Evaluated::exactMatch).count();
        long semanticPass = semantic.stream().filter(e -> e.result().overall() == Overall.PASS).count();

        sb.append("# 골든셋 평가 결과\n\n");
        sb.append("생성 시각: ").append(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).append('\n');
        sb.append("재현 명령: `./gradlew test --tests 'io.github.aeginventor.ledgerguard.golden.GoldenSetEvaluationTest'`\n\n");
        sb.append("| 세트 | 건수 | 결과 |\n|---|---|---|\n");
        sb.append("| valid_vouchers.json | ").append(valid.size()).append(" | PASS ").append(validPass)
                .append("건, 거짓 양성 ").append(valid.size() - validPass).append("건 |\n");
        sb.append("| faulty_vouchers.json | ").append(faulty.size()).append(" | expectedCodes 전부 검출 ")
                .append(faultyDetected).append("건, 검출 코드가 기대와 정확히 일치 ").append(faultyExact).append("건 |\n");
        sb.append("| semantic_misses.json | ").append(semantic.size()).append(" | 규칙이 못 잡아 PASS ")
                .append(semanticPass).append("건 (알려진 한계) |\n\n");

        sb.append("## valid_vouchers.json\n\n| id | 제목 | 판정 | 사유 코드 |\n|---|---|---|---|\n");
        for (Evaluated e : valid) {
            sb.append("| ").append(e.goldenCase().id()).append(" | ").append(e.goldenCase().title()).append(" | ")
                    .append(e.result().overall()).append(" | ").append(String.join(", ", e.codes())).append(" |\n");
        }
        sb.append("\n## faulty_vouchers.json\n\n| id | 제목 | 기대 코드 | 검출 코드 | 판정 | 전부 검출 |\n|---|---|---|---|---|---|\n");
        for (Evaluated e : faulty) {
            sb.append("| ").append(e.goldenCase().id()).append(" | ").append(e.goldenCase().title()).append(" | ")
                    .append(String.join(", ", e.goldenCase().expectedCodes())).append(" | ")
                    .append(String.join(", ", e.codes())).append(" | ").append(e.result().overall()).append(" | ")
                    .append(e.expectedAllDetected() ? "예" : "아니오").append(" |\n");
        }
        sb.append("\n## semantic_misses.json (규칙이 못 잡는 결함)\n\n| id | 제목 | 쓰인 계정 | 맞는 계정 | 판정 | 왜 못 잡나 |\n|---|---|---|---|---|---|\n");
        for (Evaluated e : semantic) {
            GoldenFile.GoldenCase c = e.goldenCase();
            sb.append("| ").append(c.id()).append(" | ").append(c.title()).append(" | ")
                    .append(c.wrongAccountCode()).append(' ').append(accountName(c.wrongAccountCode())).append(" | ")
                    .append(c.correctAccountCode()).append(' ').append(accountName(c.correctAccountCode())).append(" | ")
                    .append(e.result().overall()).append(" | ").append(c.why()).append(" |\n");
        }
        return sb.toString();
    }

    private String accountName(String code) {
        Account account = accounts.get(code);
        return account == null ? "" : account.getName();
    }
}
