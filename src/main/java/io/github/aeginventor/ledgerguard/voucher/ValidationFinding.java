package io.github.aeginventor.ledgerguard.voucher;

import io.github.aeginventor.ledgerguard.validation.Severity;
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
@Table(name = "validation_finding")
public class ValidationFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "voucher_id", nullable = false)
    private Voucher voucher;

    @Column(name = "rule_code", length = 10, nullable = false)
    private String ruleCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 10, nullable = false)
    private Severity severity;

    @Column(name = "message", length = 500, nullable = false)
    private String message;

    @Column(name = "basis", length = 300)
    private String basis;

    protected ValidationFinding() {
    }

    public ValidationFinding(String ruleCode, Severity severity, String message, String basis) {
        this.ruleCode = ruleCode;
        this.severity = severity;
        this.message = message;
        this.basis = basis;
    }

    void attachTo(Voucher voucher) {
        this.voucher = voucher;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public Severity getSeverity() {
        return severity;
    }

    public String getMessage() {
        return message;
    }

    public String getBasis() {
        return basis;
    }
}
