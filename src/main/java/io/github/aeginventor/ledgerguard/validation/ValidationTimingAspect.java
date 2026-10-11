package io.github.aeginventor.ledgerguard.validation;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * 규칙 엔진 실행 시간을 AOP로 재서 Timer로 남긴다. 엔진 코드에는 손대지 않는다.
 * Spring AOP는 프록시라 엔진 빈을 밖에서 부를 때만 걸리고, 엔진 안에서 자기 메서드를 부르는 경우(self-invocation)는 재지 않는다.
 * ledger_guard_validation_seconds{stage}: 단계별 판정 소요 시간 분포(count, sum, max).
 */
@Aspect
@Component
public class ValidationTimingAspect {

    public static final String TIMER = "ledger_guard_validation_seconds";

    private final MeterRegistry registry;

    public ValidationTimingAspect(MeterRegistry registry) {
        this.registry = registry;
    }

    @Around("execution(* io.github.aeginventor.ledgerguard.validation.ValidationEngine.evaluate(..)) && args(context)")
    public Object time(ProceedingJoinPoint joinPoint, ValidationContext context) throws Throwable {
        Timer.Sample sample = Timer.start(registry);
        try {
            return joinPoint.proceed();
        } finally {
            sample.stop(Timer.builder(TIMER)
                    .description("규칙 엔진 판정 소요 시간")
                    .tag("stage", context.stage().name().toLowerCase())
                    .register(registry));
        }
    }
}
