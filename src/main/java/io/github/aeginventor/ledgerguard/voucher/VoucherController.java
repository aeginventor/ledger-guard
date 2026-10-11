package io.github.aeginventor.ledgerguard.voucher;

import io.github.aeginventor.ledgerguard.draft.DraftService;
import io.github.aeginventor.ledgerguard.voucher.dto.ApproveRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.DraftResponse;
import io.github.aeginventor.ledgerguard.voucher.dto.ReverseRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherEventResponse;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherRequest;
import io.github.aeginventor.ledgerguard.voucher.dto.VoucherResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vouchers")
@Tag(name = "vouchers", description = "전표 초안, 저장, 승인")
public class VoucherController {

    private final VoucherService voucherService;
    private final DraftService draftService;

    public VoucherController(VoucherService voucherService, DraftService draftService) {
        this.voucherService = voucherService;
        this.draftService = draftService;
    }

    @PostMapping("/draft")
    @Operation(summary = "분개 초안 생성", description = "초안과 규칙 판정을 돌려준다. 저장하지 않는다. drafter 필드가 fixture이면 실제 LLM 호출이 아니다.")
    public DraftResponse draft(@Valid @RequestBody DraftRequest request) {
        return draftService.draft(request);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "전표 저장", description = "규칙 판정 뒤 저장한다. PASS는 DRAFT, REVIEW는 REVIEW, FAIL은 REJECTED 상태가 되며 FAIL도 기록으로 남긴다.")
    public VoucherResponse create(@Valid @RequestBody VoucherRequest request) {
        return voucherService.create(request);
    }

    @GetMapping("/{id}")
    @Operation(summary = "전표 조회")
    public VoucherResponse get(@PathVariable Long id) {
        return voucherService.get(id);
    }

    @GetMapping
    @Operation(summary = "전표 목록", description = "status와 month(yyyy-MM)로 거른다.")
    public List<VoucherResponse> list(@RequestParam(required = false) VoucherStatus status,
            @RequestParam(required = false) String month) {
        return voucherService.search(status, month);
    }

    @GetMapping("/{id}/events")
    @Operation(summary = "전표 상태 이력", description = "작성, 승인과 전기, 역분개로 상쇄된 기록을 시간순으로. 409로 끝난 승인은 롤백되어 남지 않는다.")
    public List<VoucherEventResponse> events(@PathVariable Long id) {
        return voucherService.events(id);
    }

    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "전표 역분개", description = "전기된 전표의 차대를 뒤집은 전표를 만들어 바로 전기하고 원전표를 REVERSED로 바꾼다. "
            + "원전표는 지우지 않는다. 역분개 전표도 규칙을 전부 거치며 FAIL이면 409.")
    public VoucherResponse reverse(@PathVariable Long id, @Valid @RequestBody ReverseRequest request) {
        return voucherService.reverse(id, request);
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "전표 승인과 전기", description = "규칙을 다시 돌린 뒤 PASS 또는 REVIEW(사유 필수)만 POSTED로 바꾸고 원장에 전기한다. FAIL은 409.")
    public VoucherResponse approve(@PathVariable Long id, @Valid @RequestBody ApproveRequest request) {
        return voucherService.approve(id, request);
    }
}
