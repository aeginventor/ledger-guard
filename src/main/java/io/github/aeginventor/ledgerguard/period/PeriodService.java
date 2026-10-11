package io.github.aeginventor.ledgerguard.period;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PeriodService {

    private final AccountingPeriodRepository repository;
    private final Clock clock;

    public PeriodService(AccountingPeriodRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public PeriodResponse close(String raw) {
        YearMonth month = PeriodKey.parse(raw);
        AccountingPeriod period = repository.findById(PeriodKey.of(month))
                .orElseGet(() -> new AccountingPeriod(PeriodKey.of(month)));
        if (!period.isClosed()) {
            period.close(LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS));
        }
        return PeriodResponse.from(repository.save(period));
    }

    @Transactional(readOnly = true)
    public List<PeriodResponse> list() {
        return repository.findAll().stream()
                .sorted((a, b) -> a.getYyyymm().compareTo(b.getYyyymm()))
                .map(PeriodResponse::from)
                .toList();
    }
}
