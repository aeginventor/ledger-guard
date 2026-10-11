package io.github.aeginventor.ledgerguard.voucher;

import io.github.aeginventor.ledgerguard.account.Account;
import io.github.aeginventor.ledgerguard.account.AccountRepository;
import io.github.aeginventor.ledgerguard.common.ConflictException;
import io.github.aeginventor.ledgerguard.common.NotFoundException;
import io.github.aeginventor.ledgerguard.ledger.LedgerEntry;
import io.github.aeginventor.ledgerguard.ledger.LedgerEntryRepository;
import io.github.aeginventor.ledgerguard.period.PeriodKey;
import io.github.aeginventor.ledgerguard.validation.Finding;
import io.github.aeginventor.ledgerguard.validation.LineData;
import io.github.aeginventor.ledgerguard.validation.Overall;
import io.github.aeginventor.ledgerguard.validation.ValidationContextFactory;
import io.github.aeginventor.ledgerguard.validation.ValidationEngine;
import io.github.aeginventor.ledgerguard.validation.ValidationResult;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.dto.ApproveRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherLineRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final AccountRepository accountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ValidationContextFactory contextFactory;
    private final ValidationEngine engine;
    private final Clock clock;

    public VoucherService(VoucherRepository voucherRepository, AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository, ValidationContextFactory contextFactory,
            ValidationEngine engine, Clock clock) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.contextFactory = contextFactory;
        this.engine = engine;
        this.clock = clock;
    }

    /** 검증 결과와 관계없이 저장한다. FAIL은 REJECTED 상태로 남겨 감사 추적이 가능하게 한다. */
    @Transactional
    public VoucherResponse create(VoucherRequest request) {
        VoucherData data = request.toVoucherData();
        ValidationResult result = engine.evaluate(contextFactory.forSave(data));

        Voucher voucher = new Voucher(request.voucherDate(), request.description(), request.counterpartyName(),
                request.counterpartyBizNo(), request.evidenceType(), request.vatType(), request.supplyAmount(),
                request.drafterOrManual(), now());
        int lineNo = 1;
        for (VoucherLineRequest line : request.lines()) {
            voucher.addLine(new VoucherLine(lineNo++, line.accountCode(), line.side(), toStoredAmount(line.amount()),
                    line.memo()));
        }
        voucher.replaceFindings(toEntities(result), result.overall());
        voucher.changeStatus(statusFor(result.overall()));
        return toResponse(voucherRepository.save(voucher));
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(Long id) {
        return toResponse(load(id));
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> search(VoucherStatus status, String month) {
        LocalDate from = null;
        LocalDate to = null;
        if (month != null && !month.isBlank()) {
            YearMonth ym = PeriodKey.parse(month);
            from = ym.atDay(1);
            to = ym.plusMonths(1).atDay(1);
        }
        Map<String, String> names = accountNames();
        return voucherRepository.search(status, from, to).stream()
                .map(v -> VoucherResponse.from(v, names::get))
                .toList();
    }

    /**
     * 승인 시점에 규칙을 다시 돌린다. 저장 뒤 기간이 마감됐을 수 있기 때문이다.
     * FAIL이면 409, REVIEW인데 사유가 없으면 409, 이미 전기된 전표도 409.
     * 409로 끝난 승인은 전표를 바꾸지 않는다. 재검증 결과는 응답 본문에만 담긴다.
     */
    @Transactional
    public VoucherResponse approve(Long id, ApproveRequest request) {
        Voucher voucher = load(id);
        if (voucher.getStatus() == VoucherStatus.POSTED) {
            throw new ConflictException("전표 " + id + "은 이미 전기되었다");
        }
        ValidationResult result = engine.evaluate(contextFactory.forApproval(toVoucherData(voucher), voucher));
        if (result.overall() == Overall.FAIL) {
            throw new ConflictException("FAIL 판정 전표는 승인할 수 없다", result);
        }
        if (result.overall() == Overall.REVIEW && (request.reason() == null || request.reason().isBlank())) {
            throw new ConflictException("REVIEW 판정 전표는 승인 사유(reason)를 적어야 승인할 수 있다", result);
        }
        voucher.replaceFindings(toEntities(result), result.overall());
        LocalDateTime now = now();
        voucher.markPosted(request.approver(), request.reason(), now);
        for (VoucherLine line : voucher.getLines()) {
            ledgerEntryRepository.save(new LedgerEntry(voucher.getId(), line.getLineNo(), line.getAccountCode(),
                    line.getSide(), line.getAmount(), voucher.getVoucherDate(), now));
        }
        return toResponse(voucher);
    }

    /** DB와 JVM의 시각 정밀도가 달라 응답이 흔들리지 않도록 밀리초로 자른다. */
    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    static VoucherStatus statusFor(Overall overall) {
        return switch (overall) {
            case PASS -> VoucherStatus.DRAFT;
            case REVIEW -> VoucherStatus.REVIEW;
            case FAIL -> VoucherStatus.REJECTED;
        };
    }

    /** 소수 금액은 R03이 FAIL로 기록하므로 저장 시에는 원 단위로 버린다. 음수는 그대로 남긴다. */
    static long toStoredAmount(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.DOWN).longValueExact();
    }

    static VoucherData toVoucherData(Voucher voucher) {
        List<LineData> lines = voucher.getLines().stream()
                .map(line -> LineData.of(line.getLineNo(), line.getAccountCode(), line.getSide(), line.getAmount()))
                .toList();
        return new VoucherData(voucher.getVoucherDate(), voucher.getDescription(), voucher.getCounterpartyName(),
                voucher.getCounterpartyBizNo(), voucher.getEvidenceType(), voucher.getVatType(),
                voucher.getSupplyAmount(), lines);
    }

    private static List<ValidationFinding> toEntities(ValidationResult result) {
        return result.findings().stream()
                .map((Finding f) -> new ValidationFinding(f.ruleCode(), f.severity(), f.message(), f.basis()))
                .toList();
    }

    private Voucher load(Long id) {
        return voucherRepository.findById(id).orElseThrow(() -> new NotFoundException("전표 " + id + "을 찾을 수 없다"));
    }

    private Map<String, String> accountNames() {
        return accountRepository.findAll().stream().collect(Collectors.toMap(Account::getCode, Account::getName));
    }

    private VoucherResponse toResponse(Voucher voucher) {
        Map<String, String> names = accountNames();
        Function<String, String> nameOf = names::get;
        return VoucherResponse.from(voucher, nameOf);
    }
}
