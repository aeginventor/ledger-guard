package io.github.aeginventor.ledgerguard.period;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/periods")
@Tag(name = "periods", description = "회계기간 마감")
public class PeriodController {

    private final PeriodService periodService;

    public PeriodController(PeriodService periodService) {
        this.periodService = periodService;
    }

    @GetMapping
    @Operation(summary = "기간 목록", description = "마감 이력이 있는 기간만 돌려준다.")
    public List<PeriodResponse> list() {
        return periodService.list();
    }

    @PostMapping("/{yyyymm}/close")
    @Operation(summary = "기간 마감", description = "yyyyMM 또는 yyyy-MM. 마감된 기간의 전표는 저장과 승인 모두 R07로 거부된다. 다시 호출해도 결과는 같다.")
    public PeriodResponse close(@PathVariable String yyyymm) {
        return periodService.close(yyyymm);
    }
}
