package io.github.aeginventor.ledgerguard.report;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "reports", description = "시산표")
public class ReportController {

    private final TrialBalanceService trialBalanceService;

    public ReportController(TrialBalanceService trialBalanceService) {
        this.trialBalanceService = trialBalanceService;
    }

    @GetMapping("/trial-balance")
    @Operation(summary = "월별 합계잔액시산표", description = "month=yyyy-MM. 전기된 원장 항목만 집계하며 balanced 필드로 총차변과 총대변이 같은지 보여준다.")
    public TrialBalanceResponse trialBalance(@RequestParam String month) {
        return trialBalanceService.trialBalance(month);
    }
}
