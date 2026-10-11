package io.github.aeginventor.ledgerguard.ledger;

import io.github.aeginventor.ledgerguard.account.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "voucher_id", nullable = false)
    private Long voucherId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_code", length = 10, nullable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 10, nullable = false)
    private Side side;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "posted_at", nullable = false)
    private LocalDateTime postedAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long voucherId, int lineNo, String accountCode, Side side, long amount, LocalDate entryDate,
            LocalDateTime postedAt) {
        this.voucherId = voucherId;
        this.lineNo = lineNo;
        this.accountCode = accountCode;
        this.side = side;
        this.amount = amount;
        this.entryDate = entryDate;
        this.postedAt = postedAt;
    }

    public Long getId() {
        return id;
    }

    public Long getVoucherId() {
        return voucherId;
    }

    public int getLineNo() {
        return lineNo;
    }

    public String getAccountCode() {
        return accountCode;
    }

    public Side getSide() {
        return side;
    }

    public long getAmount() {
        return amount;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public LocalDateTime getPostedAt() {
        return postedAt;
    }
}
