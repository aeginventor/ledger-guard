package io.github.aeginventor.ledgerguard.period;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "accounting_period")
public class AccountingPeriod {

    @Id
    @Column(name = "yyyymm", length = 6, nullable = false)
    private String yyyymm;

    @Column(name = "closed", nullable = false)
    private boolean closed;

    @Column(name = "closed_at")
    private LocalDateTime closedAt;

    protected AccountingPeriod() {
    }

    public AccountingPeriod(String yyyymm) {
        this.yyyymm = yyyymm;
        this.closed = false;
    }

    public void close(LocalDateTime at) {
        this.closed = true;
        this.closedAt = at;
    }

    public String getYyyymm() {
        return yyyymm;
    }

    public boolean isClosed() {
        return closed;
    }

    public LocalDateTime getClosedAt() {
        return closedAt;
    }
}
