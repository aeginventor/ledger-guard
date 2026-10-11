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
import io.github.aeginventor.ledgerguard.validation.ValidationMetrics;
import io.github.aeginventor.ledgerguard.validation.Stage;
import io.github.aeginventor.ledgerguard.validation.ValidationResult;
import io.github.aeginventor.ledgerguard.validation.VoucherData;
import io.github.aeginventor.ledgerguard.voucher.dto.ApproveRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.ReverseRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherEventResponse;
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
    private final VoucherEventRepository eventRepository;
    private final ValidationContextFactory contextFactory;
    private final ValidationEngine engine;
    private final ValidationMetrics metrics;
    private final Clock clock;

    public VoucherService(VoucherRepository voucherRepository, AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository, VoucherEventRepository eventRepository,
            ValidationContextFactory contextFactory, ValidationEngine engine, ValidationMetrics metrics, Clock clock) {
        this.voucherRepository = voucherRepository;
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.eventRepository = eventRepository;
        this.contextFactory = contextFactory;
        this.engine = engine;
        this.metrics = metrics;
        this.clock = clock;
    }

    /** 검증 결과와 관계없이 저장한다. FAIL은 REJECTED 상태로 남겨 감사 추적이 가능하게 한다. */
    @Transactional
    public VoucherResponse create(VoucherRequest request) {
        VoucherData data = request.toVoucherData();
        ValidationResult result = engine.evaluate(contextFactory.forSave(data));
        metrics.record(Stage.SAVE, result);

        Voucher voucher = new Voucher(request.voucherDate(), request.description(), request.counterpartyName(),
                request.counterpartyBizNo(), request.evidenceType(), request.vatType(), request.supplyAmount(),
                request.drafterOrManual(), request.preparer(), now());
        int lineNo = 1;
        for (VoucherLineRequest line : request.lines()) {
            voucher.addLine(new VoucherLine(lineNo++, line.accountCode(), line.side(), toStoredAmount(line.amount()),
                    line.memo()));
        }
        voucher.replaceFindings(toEntities(result), result.overall());
        voucher.changeStatus(statusFor(result.overall()));
        Voucher saved = voucherRepository.save(voucher);
        eventRepository.save(new VoucherEvent(saved.getId(), VoucherEvent.Type.CREATED, null, saved.getStatus(),
                request.preparer(), null, saved.getCreatedAt()));
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<VoucherEventResponse> events(Long id) {
        load(id);
        return eventRepository.findByVoucherIdOrderByIdAsc(id).stream().map(VoucherEventResponse::from).toList();
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
     * 승인 시점에 규칙을 다시 돌린다. 저장 뒤 기간이 마감됐을 수 있기 때문이다. 승인 단계에서는 R12(작성자와 승인자 분리)가 더해진다.
     * FAIL이면 409, REVIEW인데 사유가 없으면 409, 이미 전기된 전표도 409.
     * 409로 끝난 승인은 전표를 바꾸지 않는다. 재검증 결과는 응답 본문에만 담긴다.
     */
    @Transactional
    public VoucherResponse approve(Long id, ApproveRequest request) {
        Voucher voucher = load(id);
        if (voucher.isPostedToLedger()) {
            throw new ConflictException("전표 " + id + "은 이미 전기되었다");
        }
        post(voucher, request.approver(), request.reason());
        return toResponse(voucher);
    }

    /**
     * 역분개. 전기된 전표는 지우지 않고 차대를 뒤집은 전표를 새로 만들어 같은 날 원장에 전기해 상쇄한다.
     * 역분개 전표도 저장 규칙과 승인 규칙을 전부 거친다(R05와 R10은 역분개에 적용하지 않는다). 원전표는 REVERSED가 된다.
     * 어느 단계든 FAIL이면 409이고 아무것도 저장하지 않는다.
     */
    @Transactional
    public VoucherResponse reverse(Long id, ReverseRequest request) {
        Voucher original = load(id);
        if (original.getStatus() == VoucherStatus.REVERSED) {
            throw new ConflictException("전표 " + id + "은 이미 역분개되었다(역분개 전표 " + original.getReversedById() + ")");
        }
        if (original.getStatus() != VoucherStatus.POSTED) {
            throw new ConflictException("전기된(POSTED) 전표만 역분개할 수 있다. 현재 상태 " + original.getStatus());
        }
        LocalDate date = request.voucherDate() == null ? LocalDate.now(clock) : request.voucherDate();
        Voucher reversal = new Voucher(date, "역분개: " + original.getDescription(), original.getCounterpartyName(),
                original.getCounterpartyBizNo(), original.getEvidenceType(), original.getVatType(),
                original.getSupplyAmount(), DrafterType.MANUAL, request.preparer(), now());
        reversal.markAsReversalOf(original.getId());
        int lineNo = 1;
        for (VoucherLine line : original.getLines()) {
            reversal.addLine(new VoucherLine(lineNo++, line.getAccountCode(), line.getSide().opposite(),
                    line.getAmount(), "역분개 " + original.getId() + "번 전표 " + line.getLineNo() + "번 라인"));
        }
        ValidationResult saveResult = engine.evaluate(contextFactory.forSave(toVoucherData(reversal)));
        metrics.record(Stage.SAVE, saveResult);
        if (saveResult.overall() == Overall.FAIL) {
            throw new ConflictException("역분개 전표가 저장 규칙을 통과하지 못했다", saveResult);
        }
        reversal.replaceFindings(toEntities(saveResult), saveResult.overall());
        reversal.changeStatus(statusFor(saveResult.overall()));
        reversal = voucherRepository.save(reversal);
        eventRepository.save(new VoucherEvent(reversal.getId(), VoucherEvent.Type.CREATED, null, reversal.getStatus(),
                request.preparer(), "역분개 전표 생성, 원전표 " + original.getId(), reversal.getCreatedAt()));
        post(reversal, request.approver(), request.reason());
        VoucherStatus before = original.getStatus();
        original.markReversed(reversal.getId());
        eventRepository.save(new VoucherEvent(original.getId(), VoucherEvent.Type.REVERSED, before,
                VoucherStatus.REVERSED, request.approver(), request.reason(), now()));
        return toResponse(reversal);
    }

    /** 승인 단계 규칙을 다시 돌리고 원장에 전기한다. 승인과 역분개가 함께 쓴다. */
    private void post(Voucher voucher, String approver, String reason) {
        ValidationResult result = engine.evaluate(contextFactory.forApproval(toVoucherData(voucher), voucher, approver));
        metrics.record(Stage.APPROVE, result);
        if (result.overall() == Overall.FAIL) {
            throw new ConflictException("FAIL 판정 전표는 승인할 수 없다", result);
        }
        if (result.overall() == Overall.REVIEW && (reason == null || reason.isBlank())) {
            throw new ConflictException("REVIEW 판정 전표는 승인 사유(reason)를 적어야 승인할 수 있다", result);
        }
        voucher.replaceFindings(toEntities(result), result.overall());
        LocalDateTime now = now();
        VoucherStatus before = voucher.getStatus();
        voucher.markPosted(approver, reason, now);
        for (VoucherLine line : voucher.getLines()) {
            ledgerEntryRepository.save(new LedgerEntry(voucher.getId(), line.getLineNo(), line.getAccountCode(),
                    line.getSide(), line.getAmount(), voucher.getVoucherDate(), now));
        }
        eventRepository.save(new VoucherEvent(voucher.getId(), VoucherEvent.Type.APPROVED, before, VoucherStatus.POSTED,
                approver, reason, now));
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
                voucher.getSupplyAmount(), lines, voucher.getPreparer(), voucher.getReversalOfId());
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
