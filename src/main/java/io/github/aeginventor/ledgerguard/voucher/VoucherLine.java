package io.github.aeginventor.ledgerguard.voucher;

import io.github.aeginventor.ledgerguard.account.Side;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "voucher_line")
public class VoucherLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "account_code", length = 10, nullable = false)
    private String accountCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "side", length = 10, nullable = false)
    private Side side;

    @Column(name = "amount", nullable = false)
    private long amount;

    @Column(name = "memo", length = 300)
    private String memo;

    protected VoucherLine() {
    }

    public VoucherLine(int lineNo, String accountCode, Side side, long amount, String memo) {
        this.lineNo = lineNo;
        this.accountCode = accountCode;
        this.side = side;
        this.amount = amount;
        this.memo = memo;
    }

    void attachTo(Voucher voucher) {
        this.voucher = voucher;
    }

    public Long getId() {
        return id;
    }

    public Voucher getVoucher() {
        return voucher;
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

    public String getMemo() {
        return memo;
    }
}
