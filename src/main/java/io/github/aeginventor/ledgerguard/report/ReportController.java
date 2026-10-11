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
    @Operation(summary = "합계잔액시산표", description = "month=yyyy-MM. scope=month(기본)는 해당 월에 전기된 원장 항목만, "
            + "scope=ytd는 그 해 1월 1일부터 해당 월 말까지의 누계를 집계하고 행마다 기초 잔액과 당월 발생액을 함께 준다. "
            + "balanced 필드로 총차변과 총대변이 같은지 보여준다.")
    public TrialBalanceResponse trialBalance(@RequestParam String month,
            @RequestParam(required = false, defaultValue = "month") String scope) {
        return trialBalanceService.trialBalance(month, scope);
    }
}
