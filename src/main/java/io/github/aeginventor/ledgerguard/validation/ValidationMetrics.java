package io.github.aeginventor.ledgerguard.validation;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 규칙 판정 결과를 Prometheus 카운터로 남긴다. 운영 중에 어느 규칙이 얼마나 걸리는지, 판정 분포가 어떤지 보기 위한 것이다.
 * ledger_guard_verdicts_total{stage, overall}: 단계별 판정 수
 * ledger_guard_findings_total{stage, rule, severity}: 단계별, 규칙별 사유 수
 */
@Component
public class ValidationMetrics {

    public static final String VERDICTS = "ledger_guard_verdicts_total";
    public static final String FINDINGS = "ledger_guard_findings_total";

    private final MeterRegistry registry;

    public ValidationMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(Stage stage, ValidationResult result) {
        Counter.builder(VERDICTS)
                .description("규칙 엔진 판정 수")
                .tag("stage", stage.name().toLowerCase())
                .tag("overall", result.overall().name().toLowerCase())
                .register(registry)
                .increment();
        for (Finding finding : result.findings()) {
            Counter.builder(FINDINGS)
                    .description("규칙별 사유 수")
                    .tag("stage", stage.name().toLowerCase())
                    .tag("rule", finding.ruleCode())
                    .tag("severity", finding.severity().name().toLowerCase())
                    .register(registry)
                    .increment();
        }
    }
}
